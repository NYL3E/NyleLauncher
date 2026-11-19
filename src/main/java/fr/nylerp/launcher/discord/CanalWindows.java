package fr.nylerp.launcher.discord;

import com.sun.jna.Native;
import com.sun.jna.platform.win32.Kernel32;
import com.sun.jna.platform.win32.WinBase;
import com.sun.jna.platform.win32.WinNT;
import com.sun.jna.ptr.IntByReference;
import com.sun.jna.win32.StdCallLibrary;
import com.sun.jna.win32.W32APIOptions;

import java.io.EOFException;
import java.io.IOException;
import java.nio.file.NoSuchFileException;
import java.util.Arrays;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

/**
 * Tube nommé de Discord sous Windows ({@code \\?\pipe\discord-ipc-N}), par JNA (déjà embarqué pour la
 * barre système et le démarrage automatique ; Minecraft 1.21.1 l'embarque aussi, même version).
 *
 * <p><b>Pourquoi pas un {@code RandomAccessFile}</b> : sur un handle synchrone, Windows sérialise les
 * entrées-sorties ; une lecture bloquée en attente d'un message empêcherait d'écrire (activité, PONG),
 * et la fermeture pendant cette lecture peut pendre. Ici on ne lit jamais à l'aveugle : {@code PeekNamedPipe}
 * dit combien d'octets sont arrivés, et {@code ReadFile} ne lit que ceux-là. Un seul fil touche au handle,
 * qu'il ferme lui-même.
 *
 * <p><b>Chaque appel au tube est borné à une seconde</b> (essai du 19/11 au portable : un serveur de tube dont les
 * tampons sont nuls et qui ne lit plus fait attendre {@code WriteFile} sans fin). Une {@link Garde} ouvre le handle
 * du fil APPELANT ; si l'appel dure plus d'une seconde, la sentinelle appelle {@code CancelSynchronousIo} sur CE
 * fil seulement : l'appel revient en échec, la connexion est abandonnée et réessayée plus tard. La garde est
 * refermée sous verrou dès le retour de l'appel : la sentinelle ne peut jamais annuler une autre opération.
 */
final class CanalWindows implements Canal {

    private static final int ERREUR_FICHIER_INTROUVABLE = 2;
    private static final int ERREUR_PLUS_DE_DONNEES = 234;
    private static final int THREAD_TERMINATE = 0x0001; // droit exigé par CancelSynchronousIo
    static final long DELAI_MAX_MS = 1000;

    /** {@code CancelSynchronousIo} n'est pas dans jna-platform 5.14 : correspondance minimale. */
    private interface Annulation extends StdCallLibrary {
        Annulation INSTANCE = Native.load("kernel32", Annulation.class, W32APIOptions.DEFAULT_OPTIONS);
        boolean CancelSynchronousIo(WinNT.HANDLE fil);
    }

    private static final Kernel32 K = Kernel32.INSTANCE;
    private static final ScheduledExecutorService SENTINELLE = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread t = new Thread(r, "nyle-discord-sentinelle");
        t.setDaemon(true);
        return t;
    });

    private final WinNT.HANDLE handle;
    private boolean ferme;

    private CanalWindows(WinNT.HANDLE handle) {
        this.handle = handle;
    }

    /** Borne UN appel synchrone du fil courant ; à fermer dès le retour de l'appel (try-with-resources). */
    private static final class Garde implements AutoCloseable {
        private final WinNT.HANDLE fil;
        private final ScheduledFuture<?> tache;
        private boolean fini;               // sous verrou (this)
        private volatile boolean annulee;

        Garde() {
            WinNT.HANDLE h = null;
            try { h = K.OpenThread(THREAD_TERMINATE, false, K.GetCurrentThreadId()); } catch (Throwable ignore) { }
            fil = h;
            tache = h == null ? null : SENTINELLE.schedule(this::annuler, DELAI_MAX_MS, TimeUnit.MILLISECONDS);
        }

        private synchronized void annuler() {
            if (fini) return; // l'appel est déjà revenu : rien à annuler, et surtout pas l'opération suivante
            annulee = true;
            try { Annulation.INSTANCE.CancelSynchronousIo(fil); } catch (Throwable ignore) { }
        }

        boolean annulee() { return annulee; }

        @Override
        public void close() {
            synchronized (this) { fini = true; }
            if (tache != null) tache.cancel(false);
            if (fil != null) {
                try { K.CloseHandle(fil); } catch (Throwable ignore) { }
            }
        }
    }

    static CanalWindows ouvrir(String chemin) throws IOException {
        WinNT.HANDLE h;
        int erreur;
        boolean annulee;
        try (Garde g = new Garde()) {
            h = K.CreateFile(chemin, WinNT.GENERIC_READ | WinNT.GENERIC_WRITE, 0, null, WinNT.OPEN_EXISTING, 0, null);
            erreur = Native.getLastError();
            annulee = g.annulee();
        }
        if (h == null || WinBase.INVALID_HANDLE_VALUE.equals(h)) {
            if (erreur == ERREUR_FICHIER_INTROUVABLE) throw new NoSuchFileException(chemin);
            throw new IOException("tube " + chemin + (annulee ? " : ouverture annulée après 1 s" : " : erreur Windows " + erreur));
        }
        if (annulee) { // ouvert à la dernière milliseconde : on ne garde pas un tube douteux
            try { K.CloseHandle(h); } catch (Throwable ignore) { }
            throw new IOException("tube " + chemin + " : ouverture trop lente");
        }
        return new CanalWindows(h);
    }

    @Override
    public int lire(byte[] b, int debut, int longueur) throws IOException {
        IntByReference disponibles = new IntByReference();
        try (Garde g = new Garde()) {
            if (!K.PeekNamedPipe(handle, null, 0, null, disponibles, null)) {
                int e = Native.getLastError();
                throw new EOFException(g.annulee() ? "tube muet (lecture annulée après 1 s)" : "tube fermé par Discord (erreur Windows " + e + ")");
            }
        }
        int n = Math.min(longueur, disponibles.getValue());
        if (n <= 0) return 0;
        byte[] cible = debut == 0 ? b : new byte[n];
        IntByReference lus = new IntByReference();
        try (Garde g = new Garde()) {
            if (!K.ReadFile(handle, cible, n, lus, null)) {
                int e = Native.getLastError();
                if (e != ERREUR_PLUS_DE_DONNEES || g.annulee()) {
                    throw new EOFException(g.annulee() ? "tube muet (lecture annulée après 1 s)" : "lecture du tube impossible (erreur Windows " + e + ")");
                }
            }
        }
        if (cible != b) System.arraycopy(cible, 0, b, debut, lus.getValue());
        return lus.getValue();
    }

    @Override
    public void ecrire(byte[] b) throws IOException {
        byte[] reste = b;
        while (reste.length > 0) {
            IntByReference ecrits = new IntByReference();
            try (Garde g = new Garde()) {
                if (!K.WriteFile(handle, reste, reste.length, ecrits, null)) {
                    int e = Native.getLastError();
                    throw new IOException(g.annulee() ? "Discord ne lit plus le tube (écriture annulée après 1 s)"
                            : "écriture dans le tube impossible (erreur Windows " + e + ")");
                }
            }
            int n = ecrits.getValue();
            if (n <= 0) throw new IOException("le tube n'accepte plus rien");
            reste = Arrays.copyOfRange(reste, n, reste.length);
        }
    }

    @Override
    public void close() {
        if (ferme) return;
        ferme = true;
        try { K.CloseHandle(handle); } catch (Throwable ignore) { }
    }
}

package fr.nylerp.launcher.discord;

import com.sun.jna.platform.win32.Kernel32;
import com.sun.jna.platform.win32.WinBase;
import com.sun.jna.platform.win32.WinNT;
import com.sun.jna.ptr.IntByReference;

import java.io.EOFException;
import java.io.IOException;
import java.nio.file.NoSuchFileException;

/**
 * Tube nommé de Discord sous Windows ({@code \\?\pipe\discord-ipc-N}), par JNA (déjà embarqué pour la
 * barre système et le démarrage automatique).
 *
 * <p><b>Pourquoi pas un {@code RandomAccessFile}</b> : sur un handle synchrone, Windows sérialise les
 * entrées-sorties ; une lecture bloquée en attente d'un message empêcherait d'écrire (activité, PONG),
 * et la fermeture pendant cette lecture peut pendre. Ici on ne lit jamais à l'aveugle : {@code PeekNamedPipe}
 * dit combien d'octets sont arrivés, et {@code ReadFile} ne lit que ceux-là, donc il revient tout de suite.
 * Un seul fil touche au handle, qu'il ferme lui-même.
 */
final class CanalWindows implements Canal {

    private static final int ERREUR_FICHIER_INTROUVABLE = 2;
    private static final int ERREUR_PLUS_DE_DONNEES = 234;

    private final Kernel32 k = Kernel32.INSTANCE;
    private final WinNT.HANDLE handle;
    private boolean ferme;

    private CanalWindows(WinNT.HANDLE handle) {
        this.handle = handle;
    }

    static CanalWindows ouvrir(String chemin) throws IOException {
        Kernel32 k = Kernel32.INSTANCE;
        WinNT.HANDLE h = k.CreateFile(chemin, WinNT.GENERIC_READ | WinNT.GENERIC_WRITE, 0, null,
                WinNT.OPEN_EXISTING, 0, null);
        if (h == null || WinBase.INVALID_HANDLE_VALUE.equals(h)) {
            int e = k.GetLastError();
            if (e == ERREUR_FICHIER_INTROUVABLE) throw new NoSuchFileException(chemin);
            throw new IOException("tube " + chemin + " : erreur Windows " + e);
        }
        return new CanalWindows(h);
    }

    @Override
    public int lire(byte[] b, int debut, int longueur) throws IOException {
        IntByReference disponibles = new IntByReference();
        if (!k.PeekNamedPipe(handle, null, 0, null, disponibles, null)) {
            throw new EOFException("tube fermé par Discord (erreur Windows " + k.GetLastError() + ")");
        }
        int n = Math.min(longueur, disponibles.getValue());
        if (n <= 0) return 0;
        byte[] cible = debut == 0 ? b : new byte[n];
        IntByReference lus = new IntByReference();
        if (!k.ReadFile(handle, cible, n, lus, null) && k.GetLastError() != ERREUR_PLUS_DE_DONNEES) {
            throw new EOFException("lecture du tube impossible (erreur Windows " + k.GetLastError() + ")");
        }
        if (cible != b) System.arraycopy(cible, 0, b, debut, lus.getValue());
        return lus.getValue();
    }

    @Override
    public void ecrire(byte[] b) throws IOException {
        byte[] reste = b;
        while (reste.length > 0) {
            IntByReference ecrits = new IntByReference();
            if (!k.WriteFile(handle, reste, reste.length, ecrits, null)) {
                throw new IOException("écriture dans le tube impossible (erreur Windows " + k.GetLastError() + ")");
            }
            int n = ecrits.getValue();
            if (n <= 0) throw new IOException("le tube n'accepte plus rien");
            reste = java.util.Arrays.copyOfRange(reste, n, reste.length);
        }
    }

    @Override
    public void close() {
        if (ferme) return;
        ferme = true;
        try { k.CloseHandle(handle); } catch (Throwable ignore) { }
    }
}

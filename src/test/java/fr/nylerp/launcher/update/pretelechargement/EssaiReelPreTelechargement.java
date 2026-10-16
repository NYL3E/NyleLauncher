package fr.nylerp.launcher.update.pretelechargement;

import fr.nylerp.launcher.config.AppPaths;
import fr.nylerp.launcher.config.ModeDeJeu;
import fr.nylerp.launcher.univers.PreTelechargeur.Etat;
import fr.nylerp.launcher.update.ModpackUpdater;

import java.nio.file.Files;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Essai RÉEL, lancé à la main (pas dans la suite de tests) : pré-télécharge un univers depuis le
 * vrai manifeste de production (lecture seule), puis lance la synchronisation normale et vérifie
 * qu'elle ne télécharge plus rien. À lancer avec un {@code user.home} jetable :
 *
 * <pre>java -Duser.home=/tmp/essai -cp &lt;jar&gt;:build/classes/java/test \
 *   fr.nylerp.launcher.update.pretelechargement.EssaiReelPreTelechargement nylerp</pre>
 */
public final class EssaiReelPreTelechargement {

    public static void main(String[] args) throws Exception {
        String univers = args.length > 0 ? args[0] : "nylerp";
        if (!System.getProperty("user.home").contains("essai")) {
            throw new IllegalStateException("user.home doit être un dossier jetable (contenant « essai »)");
        }
        if (ModeDeJeu.courant() != PlanPack.mode(univers)) {
            throw new IllegalStateException("la synchro de référence tourne sur le mode courant (" + ModeDeJeu.courant() + ")");
        }
        PreTelechargeurPack pack = new PreTelechargeurPack(Conditions.systeme(), 0, () -> true);
        long t0 = System.nanoTime();
        pack.demarrer(univers);
        while (pack.etat(univers) == Etat.EN_COURS || pack.etat(univers) == Etat.AUCUN && pack.octetsTotal(univers) < 0) {
            Thread.sleep(2000);
            System.out.printf("  %5.1f %%  de %d Mo%n", pack.progression(univers) * 100, pack.octetsTotal(univers) / 1_000_000);
        }
        double tPre = (System.nanoTime() - t0) / 1e9;
        System.out.printf("PRÉ-TÉLÉCHARGEMENT : état %s, %d Mo en %.0f s, erreur=%s%n", pack.etat(univers),
                pack.octetsTotal(univers) / 1_000_000, tPre, pack.erreur(univers));
        long enReserve;
        try (var l = Files.list(Reserve.dossier())) {
            enReserve = l.filter(p -> !p.getFileName().toString().endsWith(".part")).count();
        }
        System.out.println("fichiers en réserve : " + enReserve);
        if (pack.etat(univers) != Etat.PRET) System.exit(2);

        long adoptionsAvant = Reserve.ADOPTIONS.get();
        List<String> aTelecharger = new CopyOnWriteArrayList<>();
        long t1 = System.nanoTime();
        new ModpackUpdater(new ModpackUpdater.Listener() {
            @Override public void onStatus(String line) {
                if (line.startsWith("↓ ")) aTelecharger.add(line);
                if (line.startsWith("Modpack") || line.startsWith("Serveur")) System.out.println("synchro : " + line);
            }
            @Override public void onProgress(int d, int t, long b, long bt) { }
        }).sync();
        double tSync = (System.nanoTime() - t1) / 1e9;
        long adoptes = Reserve.ADOPTIONS.get() - adoptionsAvant;
        long resteReserve;
        try (var l = Files.list(Reserve.dossier())) {
            resteReserve = l.count();
        }
        System.out.printf("SYNCHRO NORMALE : %.1f s, %d fichiers à poser, %d pris de la réserve, %d téléchargés, réserve restante %d%n",
                tSync, aTelecharger.size(), adoptes, aTelecharger.size() - adoptes, resteReserve);
        PlanPack.Resultat apres = new PlanPack(univers).evaluer();
        System.out.println("plan après coup : " + apres.aTelecharger().size() + " fichier(s) à télécharger, dossier "
                + AppPaths.gameDir());
        pack.fermer();
        System.exit(aTelecharger.size() == adoptes && apres.rienATelecharger() ? 0 : 3);
    }
}

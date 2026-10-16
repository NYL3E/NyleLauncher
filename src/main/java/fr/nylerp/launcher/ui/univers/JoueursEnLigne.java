package fr.nylerp.launcher.ui.univers;

import fr.nylerp.launcher.net.ServerStatus;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.IntConsumer;

/**
 * Le compteur de joueurs de NyleRP, sondé UNE seule fois pour tout le launcher : {@link ServerStatus#start}
 * crée un sondeur à chaque appel, et la nouvelle interface l'affiche à plusieurs endroits (choix
 * d'univers, univers NyleRP, à chaque changement de compte). Les abonnés reçoivent la dernière valeur
 * tout de suite, puis chaque relevé (toutes les 30 s), depuis un fil de fond.
 */
public final class JoueursEnLigne {

    private static final List<IntConsumer> abonnes = new CopyOnWriteArrayList<>();
    private static volatile Integer dernier;
    private static boolean demarre;

    private JoueursEnLigne() { }

    public static synchronized void abonner(IntConsumer abonne) {
        abonnes.add(abonne);
        Integer d = dernier;
        if (d != null) abonne.accept(d);
        if (!demarre) {
            demarre = true;
            ServerStatus.start(n -> {
                dernier = n;
                for (IntConsumer a : abonnes) {
                    try { a.accept(n); } catch (Throwable ignored) { }
                }
            });
        }
    }

    public static void desabonner(IntConsumer abonne) { abonnes.remove(abonne); }
}

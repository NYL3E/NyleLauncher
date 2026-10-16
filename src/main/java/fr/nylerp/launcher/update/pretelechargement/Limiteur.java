package fr.nylerp.launcher.update.pretelechargement;

import java.util.concurrent.TimeUnit;

/**
 * Plafond de débit partagé par tous les flux d'un pré-téléchargement (seau à jetons).
 *
 * <p>Chaque flux « paie » les octets qu'il vient de lire ; quand le seau est vide, il dort le temps
 * que le débit autorisé rembourse la dette. Plusieurs flux qui empruntent ensemble se partagent
 * donc le même plafond : quatre flux à 1 Mo/s plafonnés à 1 Mo/s font 1 Mo/s au total, pas 4.
 * Un débit de 0 veut dire « pas de plafond ».
 */
final class Limiteur {

    private long debit;           // octets par seconde, 0 = illimité
    private double jetons;        // peut devenir négatif : c'est la dette à rembourser en dormant
    private long dernier = System.nanoTime();

    Limiteur(long octetsParSeconde) {
        this.debit = Math.max(0, octetsParSeconde);
    }

    synchronized void setDebit(long octetsParSeconde) {
        recharger();
        debit = Math.max(0, octetsParSeconde);
        jetons = Math.min(jetons, capacite());
    }

    synchronized long debit() {
        return debit;
    }

    /** Rafale tolérée : un quart de seconde de débit (au moins 16 Ko), pour lisser sans à-coups. */
    private double capacite() {
        return Math.max(debit / 4.0, 16 * 1024);
    }

    private void recharger() {
        long maintenant = System.nanoTime();
        if (debit > 0) {
            jetons = Math.min(capacite(), jetons + (maintenant - dernier) * debit / 1e9);
        }
        dernier = maintenant;
    }

    /** Paie {@code octets} ; dort si le plafond est dépassé. Interruptible. */
    void prendre(int octets) throws InterruptedException {
        long attenteNanos;
        synchronized (this) {
            if (debit <= 0) return;
            recharger();
            jetons -= octets;
            if (jetons >= 0) return;
            attenteNanos = (long) (-jetons * 1e9 / debit);
        }
        TimeUnit.NANOSECONDS.sleep(attenteNanos);
    }
}

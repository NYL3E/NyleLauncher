package fr.nylerp.launcher.annonces;

import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.function.LongSupplier;

/**
 * L'heure du serveur, tenue à jour sans jamais faire confiance à l'horloge du PC.
 *
 * <p>Chaque réponse des points d'accès (200 comme 304) porte l'heure du serveur : en-tête HTTP
 * {@code Date}, et champ {@code maintenant} dans le corps. On la retient avec l'instant de réception
 * mesuré par une horloge MONOTONE ({@link System#nanoTime()}), qui ne saute ni au changement d'heure
 * ni quand le joueur règle son horloge. « Maintenant » = heure du serveur + temps écoulé depuis.
 *
 * <p>Sans aucune réponse du serveur depuis le démarrage, {@link #maintenant()} est vide : on ne
 * décide alors RIEN d'après l'horloge locale (contrat L7, règle 3).
 */
public final class HorlogeServeur {

    private final LongSupplier nanos;
    private volatile OffsetDateTime reference;
    private volatile long nanosReference;

    public HorlogeServeur() { this(System::nanoTime); }

    HorlogeServeur(LongSupplier nanos) { this.nanos = nanos; }

    /** Recale sur l'heure annoncée par le serveur à l'instant présent. */
    public void recaler(OffsetDateTime heureServeur) {
        if (heureServeur == null) return;
        long n = nanos.getAsLong();
        reference = heureServeur;
        nanosReference = n;
    }

    /** Heure du serveur maintenant, ou vide si le serveur n'a jamais répondu depuis le démarrage. */
    public Optional<OffsetDateTime> maintenant() {
        OffsetDateTime ref = reference;
        if (ref == null) return Optional.empty();
        return Optional.of(ref.plusNanos(nanos.getAsLong() - nanosReference));
    }
}

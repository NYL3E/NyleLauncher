package fr.nylerp.launcher.univers;

import java.time.ZonedDateTime;
import java.util.Optional;

/**
 * Contrat partagé de la refonte 2026-10 : état de Pokényle piloté à distance (config du Studio,
 * {@code /api/launcher/config}). Implémenté par le lot L7 (client), lu par l'interface (lot L5).
 * Tant que rien n'est branché, {@link #PAR_DEFAUT} décrit un Pokényle pas encore ouvert.
 */
public interface SourceEtatPokenyle {

    /** Vrai si ce joueur peut lancer Pokényle maintenant (ouverture générale ou testeur). */
    boolean ouvertPour(String pseudo);

    /** Date et heure exactes de l'ouverture, si elles sont annoncées. */
    Optional<ZonedDateTime> ouverture();

    /** Libellé affiché quand la date exacte n'est pas connue, par exemple « mi-décembre ». */
    String libelleOuverture();

    /**
     * Gardé dans le contrat pour ne rien casser, mais l'interface ne le lit plus : le serveur décide, pas le launcher.
     * Un compte crack voit les mêmes écrans qu'un premium (décision de l'owner, 16/10/2026).
     */
    boolean crackAutorise();

    /** Idem : plus lu par l'interface (voir {@link #crackAutorise()}). */
    String messageCrack();

    boolean discordActif();

    String discordUrl();

    /** Rappel appelé (sur n'importe quel fil) quand la config distante change. */
    void surChangement(Runnable rappel);

    SourceEtatPokenyle PAR_DEFAUT = new SourceEtatPokenyle() {
        @Override public boolean ouvertPour(String pseudo) { return false; }
        @Override public Optional<ZonedDateTime> ouverture() { return Optional.empty(); }
        @Override public String libelleOuverture() { return "mi-décembre"; }
        @Override public boolean crackAutorise() { return false; }
        @Override public String messageCrack() {
            return "Pokényle demande pour l'instant un compte Minecraft acheté.";
        }
        @Override public boolean discordActif() { return false; }
        @Override public String discordUrl() { return "https://discord.gg/nyle"; }
        @Override public void surChangement(Runnable rappel) { }
    };
}

package fr.nylerp.launcher.jeu;

import fr.nylerp.launcher.auth.Account;
import fr.nylerp.launcher.config.Settings;
import fr.nylerp.launcher.launch.MinecraftLauncher;
import fr.nylerp.launcher.ui.CubeSpinner;
import fr.nylerp.launcher.ui.Fonts;
import fr.nylerp.launcher.ui.SyncFailedDialog;
import fr.nylerp.launcher.update.ModpackUpdater;
import fr.nylerp.launcher.update.OptionalMods;
import fr.nylerp.launcher.update.SelfUpdater;
import fr.nylerp.launcher.update.ServerListSanitizer;
import javafx.application.Platform;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.ReadOnlyBooleanProperty;
import javafx.beans.property.ReadOnlyObjectProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressBar;
import javafx.scene.shape.SVGPath;

import java.awt.Desktop;
import java.net.URI;
import java.util.concurrent.CompletableFuture;

/**
 * LE LANCEMENT DU JEU — sorti tel quel de l'ancien {@code MainView} (refonte 2026-10, lot L5).
 *
 * <p>Les méthodes {@link #startPlay}, {@link #runModpackUpdate}, {@link #performSelfUpdate},
 * {@link #performLauncherUpdateOnPlay}, {@link #refreshPlayButton}, {@link #setPlayBusy},
 * {@link #auChangementDeMode}, {@link #messageLisible} et {@link #readFabricVersionFromManifest}
 * sont DÉPLACÉES sans que leur logique change. Seuls écarts, voulus et listés :
 * <ul>
 *   <li>le bouton passé aux méthodes est un {@link Declencheur} (le gros bouton du kit n'est pas un
 *       {@code Button} JavaFX) : il n'offre que {@code setDisable}, seule méthode qu'elles appellent ;</li>
 *   <li>« fermer au lancement » appelle {@code Fond.fermerApresLancement()} au lieu de
 *       {@code Platform.exit()} : la fenêtre se range dans la barre système ;</li>
 *   <li>{@link #setPlayBusy} publie aussi l'état dans {@link #occupeProperty()}.</li>
 * </ul>
 *
 * <p>L'état affiché (ligne d'état, barre, libellé du bouton) vit ici, dans des nœuds-modèles que les
 * écrans des univers observent par liaison : les deux univers montrent ainsi le MÊME lancement.
 */
public final class LanceurJeu {

    /** Le bouton tel que le voient les méthodes déplacées : seulement {@code setDisable}. */
    public static final class Declencheur {
        private final BooleanProperty desactive = new SimpleBooleanProperty(false);
        public void setDisable(boolean v) { desactive.set(v); }
        public ReadOnlyBooleanProperty disableProperty() { return desactive; }
    }

    // ── Modèle d'affichage (ex-champs de MainView, mêmes noms pour que les corps restent identiques) ──
    private final Label status = new Label("Prêt à jouer");
    private final ProgressBar progress = new ProgressBar(0);
    /** Toujours nul : la vue « terminal » du canal DEV n'existe plus dans la nouvelle interface. */
    private fr.nylerp.launcher.ui.terminal.TerminalProgress termProgress;
    private final Label playLabel = new Label("JOUER");
    private final SVGPath playIcon = new SVGPath();
    /** Toujours nul : le bouton du kit montre l'attente lui-même (cf. {@link #occupeProperty()}). */
    private CubeSpinner playSpinner;
    private volatile boolean modpackUpdatePending = false;
    private volatile String launcherUpdateUrl = null;
    private volatile String launcherUpdateTag = null;

    private final Declencheur play = new Declencheur();
    private final BooleanProperty occupe = new SimpleBooleanProperty(false);
    private final ObjectProperty<SelfUpdater.Info> miseAJour = new SimpleObjectProperty<>();

    public LanceurJeu() {
        playIcon.setContent("M 3 1 L 3 15 L 15 8 Z");
        playLabel.setFont(Fonts.bold(22));
    }

    /**
     * Les deux vérifications de fond que faisait le constructeur de MainView : mise à jour du
     * launcher (bannière + bouton « METTRE À JOUR ») et mise à jour du pack du mode courant.
     */
    public void demarrerVerifications() {
        SelfUpdater.check().thenAccept(info -> Platform.runLater(() -> {
            if (info.hasUpdate()) {
                miseAJour.set(info);
                launcherUpdateUrl = info.releaseUrl();
                launcherUpdateTag = info.latestTag();
                refreshPlayButton();
            }
        }));

        // Check modpack in background — if the remote manifest is newer than
        // the cached one, flip the main button to "METTRE À JOUR" so the
        // player explicitly triggers the sync instead of it silently
        // happening on every launch.
        CompletableFuture.runAsync(() -> {
            boolean pending = ModpackUpdater.hasUpdate();
            Platform.runLater(() -> {
                modpackUpdatePending = pending;
                refreshPlayButton();
            });
        });
    }

    /** Le clic sur le gros bouton : exactement l'aiguillage de l'ancien bouton JOUER. */
    public void jouer() {
        fr.nylerp.launcher.annonces.Annonces.universOuvert(fr.nylerp.launcher.config.ModeDeJeu.courant().id);
        if (launcherUpdateUrl != null) {
            // Same auto-update flow as the small banner button — never open the browser
            performLauncherUpdateOnPlay(play);
        } else if (modpackUpdatePending) {
            runModpackUpdate(play);
        } else {
            startPlay(play);
        }
    }

    /** Bouton « Télécharger » de la bannière de mise à jour du launcher. */
    public void telechargerMiseAJour(Button dl) {
        SelfUpdater.Info info = miseAJour.get();
        if (info != null) performSelfUpdate(info.latestTag(), info.releaseUrl(), dl);
    }

    /** À appeler après {@code ModeDeJeu.definir} : repart d'un bouton neutre et réinterroge le pack. */
    public void apresChangementUnivers() { auChangementDeMode(); }

    public ReadOnlyBooleanProperty occupeProperty() { return occupe; }
    public ReadOnlyBooleanProperty desactiveProperty() { return play.disableProperty(); }
    public ReadOnlyObjectProperty<SelfUpdater.Info> miseAJourProperty() { return miseAJour; }
    /** Ligne d'état (lecture seule pour l'interface : à lier, jamais à écrire). */
    public Label etat() { return status; }
    /** Avancement (lecture seule pour l'interface). */
    public ProgressBar avancement() { return progress; }
    /** Libellé brut du bouton (« JOUER », « METTRE À JOUR », « 42% »…). */
    public Label libelle() { return playLabel; }
    /** Vrai si le bouton propose une mise à jour (pack ou launcher) plutôt que de lancer. */
    public boolean misesAJourEnAttente() { return modpackUpdatePending || launcherUpdateUrl != null; }

    /**
     * Same as {@link #performSelfUpdate} but drives the giant Play button at the
     * bottom-right (which uses {@code playLabel} instead of its own text).
     */
    private void performLauncherUpdateOnPlay(Declencheur play) {
        if (!SelfUpdater.canAutoUpdate()) {
            openBrowser(launcherUpdateUrl);
            return;
        }
        play.setDisable(true);
        playLabel.setText("0%");
        setPlayBusy(true);          // 8-cube ring while we DL the installer
        SelfUpdater.downloadUpdate(launcherUpdateTag, (done, total) -> {
            if (total > 0) {
                int pct = (int) Math.min(100L, done * 100L / total);
                Platform.runLater(() -> playLabel.setText(pct + "%"));
            }
        }).whenComplete((file, err) -> Platform.runLater(() -> {
            if (err != null) {
                playLabel.setText("RÉESSAYER");
                play.setDisable(false);
                setPlayBusy(false);
                return;
            }
            try {
                playLabel.setText("INSTALLATION");
                // Keep the spinner running — the installer is about to run
                // and the process will exit on its own (System.exit below).
                SelfUpdater.runInstaller(file);
                new Thread(() -> {
                    try { Thread.sleep(1500); } catch (InterruptedException ignored) {}
                    Platform.runLater(() -> { Platform.exit(); System.exit(0); });
                }).start();
            } catch (Exception ex) {
                playLabel.setText("RÉESSAYER");
                play.setDisable(false);
                setPlayBusy(false);
            }
        }));
    }

    /** Toggle the Play button between "idle arrow" and "8-cube spinner".
     *  Centralised so all three call sites (launcher update, modpack
     *  update, game launch) flip both bits the same way — previously
     *  only {@link #startPlay} wired the spinner, so clicking
     *  {@code METTRE À JOUR} just disabled the button with no motion. */
    private void setPlayBusy(boolean busy) {
        occupe.set(busy);            // refonte 2026-10 : l'interface (bouton, segment) s'y abonne
        // Canal DEV : la barre terminal n'anime son curseur d'attente QUE pendant un travail réel.
        // Au repos elle reste strictement figée — pas d'animation permanente sur l'écran d'accueil.
        if (termProgress != null) termProgress.setBusy(busy);
        if (playSpinner == null || playIcon == null) return;
        if (busy) {
            playSpinner.start();
            playIcon.setVisible(false);
            playIcon.setManaged(false);
        } else {
            playSpinner.stop();
            playIcon.setVisible(true);
            playIcon.setManaged(true);
        }
    }

    /**
     * Downloads the new launcher installer for the current OS and runs it.
     * Falls back to opening the GitHub release page only if the OS isn't
     * supported by the auto-installer (currently only macOS).
     */
    private void performSelfUpdate(String tag, String fallbackUrl, Button dl) {
        if (!SelfUpdater.canAutoUpdate()) {
            // No installer for this OS yet — open the release page so the user sees the assets
            openBrowser(fallbackUrl);
            return;
        }
        dl.setDisable(true);
        dl.setText("0%");
        SelfUpdater.downloadUpdate(tag, (done, total) -> {
            if (total > 0) {
                int pct = (int) Math.min(100L, done * 100L / total);
                Platform.runLater(() -> dl.setText(pct + "%"));
            }
        }).whenComplete((file, err) -> Platform.runLater(() -> {
            if (err != null) {
                dl.setText("Réessayer");
                dl.setDisable(false);
                return;
            }
            try {
                dl.setText("Installation...");
                SelfUpdater.runInstaller(file);
                // Give the installer a moment to start before we release file locks
                new Thread(() -> {
                    try { Thread.sleep(1500); } catch (InterruptedException ignored) {}
                    Platform.runLater(() -> { Platform.exit(); System.exit(0); });
                }).start();
            } catch (Exception ex) {
                dl.setText("Réessayer");
                dl.setDisable(false);
            }
        }));
    }

    /**
     * L'owner vient de changer d'univers.
     *
     * <p>Le mode est déjà enregistré quand on arrive ici ; il reste à faire dire la vérité à
     * l'écran. Et la vérité, après une bascule, c'est qu'on ne sait RIEN de l'instance qui
     * vient d'être désignée : elle a son propre dossier, son propre manifeste, et peut-être
     * n'a-t-elle jamais été téléchargée. On repart donc d'un bouton neutre, puis on
     * réinterroge le dépôt du NOUVEAU pack en tâche de fond — exactement comme au démarrage.
     *
     * <p>On ne lance surtout pas la synchronisation d'autorité : basculer sur pokényle peut
     * représenter un gigaoctet à télécharger. C'est au joueur d'appuyer sur « Mettre à jour »
     * quand il l'a décidé, pas au sélecteur d'engager sa connexion à sa place.
     */
    private void auChangementDeMode() {
        modpackUpdatePending = false;
        if (status != null) {
            status.setText("Mode " + fr.nylerp.launcher.config.ModeDeJeu.courant().titre
                    + " — vérification du pack…");
        }
        refreshPlayButton();
        CompletableFuture.runAsync(() -> {
            boolean pending = ModpackUpdater.hasUpdate();
            Platform.runLater(() -> {
                modpackUpdatePending = pending;
                if (status != null) {
                    status.setText(pending ? "Mise à jour du pack disponible" : "Prêt à jouer");
                }
                refreshPlayButton();
            });
        });
    }

    private void refreshPlayButton() {
        if (playLabel == null || playIcon == null) return;
        boolean dev = fr.nylerp.launcher.config.Constants.DEV;
        if (modpackUpdatePending || launcherUpdateUrl != null) {
            playLabel.setText("METTRE À JOUR");
            playLabel.setFont(dev ? fr.nylerp.launcher.ui.terminal.TerminalTheme.mono(15) : Fonts.bold(15));
            playIcon.setVisible(false);
            playIcon.setManaged(false);
        } else {
            playLabel.setText("JOUER");
            playLabel.setFont(dev ? fr.nylerp.launcher.ui.terminal.TerminalTheme.mono(20) : Fonts.bold(22));
            playIcon.setVisible(true);
            playIcon.setManaged(true);
        }
    }

    /**
     * Traduit une exception de lancement en phrase que le JOUEUR peut suivre.
     *
     * <p>Le libellé était {@code "Erreur: " + ex.getMessage()}. Sur les exceptions de fichier, ce
     * message vaut le CHEMIN et rien d'autre : un joueur a vu
     * {@code « Erreur: C:\…\jdk-21.0.12+8\bin/ucrtbase.dll »} — barre presque pleine, aucune idée de
     * quoi faire, et le problème revenait à chaque essai. La classe de l'exception, elle, dit
     * précisément ce qui s'est passé ; on s'en sert pour donner la marche à suivre.
     */
    private static String messageLisible(Throwable ex) {
        Throwable cause = ex;
        while (cause.getCause() != null && cause.getCause() != cause) cause = cause.getCause();
        String detail = cause.getMessage() == null ? "" : cause.getMessage();

        if (cause instanceof java.nio.file.AccessDeniedException
                || cause instanceof java.nio.file.FileSystemException) {
            return "Fichier bloqué par l'antivirus ou déjà utilisé — relance le launcher. "
                    + "Si ça persiste, autorise le dossier NyleLauncher dans ton antivirus.";
        }
        if (cause instanceof java.net.UnknownHostException) {
            return "Pas de connexion Internet (DNS injoignable) — vérifie ta connexion.";
        }
        if (cause instanceof java.net.SocketTimeoutException
                || cause instanceof java.net.ConnectException
                || cause instanceof java.net.http.HttpTimeoutException) {
            return "Connexion au serveur de mise à jour impossible — réessaie dans un instant.";
        }
        if (cause instanceof java.io.EOFException || detail.toLowerCase().contains("zip")) {
            return "Téléchargement incomplet — relance le launcher, il reprendra le fichier.";
        }
        // Un fichier dans un encodage inattendu ne doit plus JAMAIS être ce que voit un joueur :
        // « Input length = 3 » ne veut rien dire pour personne (une joueuse l'a eu le 29/08, son
        // jeu plantait et le launcher se cassait en lisant le journal qui l'expliquait).
        // Les lectures passent désormais par LectureTexte ; ce filet reste pour ce qu'on aurait
        // oublié, et il dit au moins quoi faire.
        if (cause instanceof java.nio.charset.CharacterCodingException) {
            return "Un fichier du jeu est illisible (encodage inattendu) — relance le launcher, "
                    + "il le remplacera. Si ça persiste, envoie un rapport au staff.";
        }
        // Le diagnostic de plantage a déjà rédigé une phrase pour le joueur : on la garde telle
        // quelle plutôt que de la préfixer d'un « Erreur : » qui la ferait passer pour un code.
        if (cause instanceof java.io.IOException && detail.length() > 40
                && (detail.startsWith("Le jeu") || detail.startsWith("Pas assez")
                    || detail.startsWith("Ta carte") || detail.startsWith("Un fichier")
                    || detail.startsWith("Un mod") || detail.startsWith("L'installation")
                    || detail.startsWith("Le moteur"))) {
            return detail;
        }
        return "Erreur : " + (detail.isBlank() ? cause.getClass().getSimpleName() : detail);
    }

    private void openBrowser(String url) {
        try {
            if (Desktop.isDesktopSupported() && Desktop.getDesktop().isSupported(Desktop.Action.BROWSE)) {
                Desktop.getDesktop().browse(new URI(url));
            }
        } catch (Exception ignored) {}
    }

    private void runModpackUpdate(Declencheur play) {
        play.setDisable(true);
        playLabel.setText("MISE À JOUR…");
        progress.setProgress(0);
        status.setText("Téléchargement des mises à jour…");
        setPlayBusy(true);          // 8-cube ring while the modpack downloads

        new Thread(() -> {
            try {
                // Self-heal stale servers.dat before the sync so the absent file
                // gets re-pulled clean (firstInstallOnly only re-pulls if absent).
                ServerListSanitizer.sweep();
                new ModpackUpdater(new ModpackUpdater.Listener() {
                    @Override public void onStatus(String line) {
                        Platform.runLater(() -> status.setText(line));
                    }
                    @Override public void onProgress(int d, int t, long bd, long bt) {
                        Platform.runLater(() -> progress.setProgress(t == 0 ? 0 : (double) d / t));
                    }
                }).sync();
                Platform.runLater(() -> {
                    modpackUpdatePending = false;
                    status.setText("Prêt à jouer");
                    progress.setProgress(0);
                    play.setDisable(false);
                    setPlayBusy(false);
                    refreshPlayButton();
                });
            } catch (Exception ex) {
                Platform.runLater(() -> {
                    status.setText(messageLisible(ex));
                    play.setDisable(false);
                    setPlayBusy(false);
                    refreshPlayButton();
                });
            }
        }, "Modpack-Update").start();
    }

    private void startPlay(Declencheur play) {
        play.setDisable(true);
        progress.setProgress(0);
        status.setText("Synchronisation du modpack…");
        // Swap the static play arrow for the animated 8-cube ring so the
        // user has a continuous "something is happening" signal — covers
        // every phase from modpack sync through Fabric install to the MC
        // process being alive. Reset on MC exit + on error below.
        setPlayBusy(true);

        new Thread(() -> {
            try {
                // Self-heal stale servers.dat BEFORE the modpack sync. The clean
                // server list is shipped firstInstallOnly, so it's only re-pulled
                // if the local copy is ABSENT — the sanitizer deletes it when it
                // still carries a deprecated direct-backend entry, letting the
                // sync below restore the proxy-only list.
                ServerListSanitizer.sweep();
                // Migrations one-shot de keybinds (ex: zoom monocle C&B → non assignée).
                fr.nylerp.launcher.update.KeybindDefaults.applyOnce();
                // Migration one-shot : retire l'ancien NYLERP-PACK.zip (désormais embarqué en mod).
                fr.nylerp.launcher.util.LightTextures.cleanupLegacyPack();

                // Sync du modpack. En cas d'échec on ne lance PLUS silencieusement les
                // vieux mods (ce qui laissait le joueur bloqué sur une version périmée sans
                // le savoir) : on affiche un dialog clair et on laisse le joueur choisir —
                // Réessayer / Lancer quand même (cas hors-ligne assumé) / Annuler.
                ModpackUpdater updater = new ModpackUpdater(new ModpackUpdater.Listener() {
                    @Override public void onStatus(String line) {
                        Platform.runLater(() -> status.setText(line));
                    }
                    @Override public void onProgress(int d, int t, long bd, long bt) {
                        Platform.runLater(() -> progress.setProgress(t == 0 ? 0 : (double) d / t));
                    }
                });
                boolean syncCancelled = false;
                while (true) {
                    try {
                        updater.sync();
                        break;                       // sync réussi → on continue le lancement
                    } catch (Exception syncErr) {
                        org.slf4j.LoggerFactory.getLogger("ModpackSync")
                                .warn("Modpack sync failed: {}", syncErr.toString(), syncErr);
                        SyncFailedDialog.Choice choice = SyncFailedDialog.ask(syncErr.getMessage());
                        if (choice == SyncFailedDialog.Choice.RETRY) {
                            Platform.runLater(() -> status.setText("Nouvelle tentative de mise à jour…"));
                            continue;                // reboucle → relance sync()
                        }
                        if (choice == SyncFailedDialog.Choice.LAUNCH_ANYWAY) {
                            Platform.runLater(() ->
                                    status.setText("Lancement local (version possiblement périmée)…"));
                            break;                   // le joueur assume la version locale (hors-ligne/GitHub down)
                        }
                        syncCancelled = true;        // CANCEL → abandon propre du lancement
                        break;
                    }
                }
                if (syncCancelled) {
                    Platform.runLater(() -> {
                        status.setText("Lancement annulé");
                        progress.setProgress(0);
                        play.setDisable(false);
                        setPlayBusy(false);
                        refreshPlayButton();
                    });
                    return;
                }

                // Reconcile optional mods (Bobby, Litematica) AFTER the
                // manifest sync but BEFORE launching MC, so the mods/ folder
                // matches the user's settings before Fabric scans it.
                Platform.runLater(() -> status.setText("Mods optionnels…"));
                OptionalMods.applyAll();

                Account account = fr.nylerp.launcher.auth.AccountStore.active();
                // ── Session TOUJOURS fraîche au lancement (fix « session invalide ») ─────────────
                // Le refresh silencieux du démarrage ne suffit pas : un launcher laissé ouvert (ou un
                // PC sorti de veille) lance le jeu avec un token Minecraft périmé (validité ~24 h) →
                // « session invalide » à l'entrée du serveur. Ici on régénère la chaîne complète
                // (refresh token MS → XBL → XSTS → token MC neuf) juste avant CHAQUE lancement :
                // le jeu part systématiquement avec un token vieux de quelques secondes.
                //  • échec réseau transitoire → on lance avec le token en cache (aucune régression) ;
                //  • refresh token révoqué/expiré (~90 j) → on N'ENVOIE PAS le joueur dans un échec
                //    de session garanti : message clair + retour à l'écran de connexion.
                if (account != null && account.type() == Account.Type.MICROSOFT
                        && account.refreshToken() != null) {
                    Platform.runLater(() -> status.setText("Vérification de ta session Microsoft…"));
                    try {
                        Account fresh = fr.nylerp.launcher.auth.MicrosoftAuth
                                .refresh(account.refreshToken())
                                .get(20, java.util.concurrent.TimeUnit.SECONDS);
                        fr.nylerp.launcher.auth.AccountStore.updateActive(fresh);
                        account = fresh;
                        org.slf4j.LoggerFactory.getLogger("Auth")
                                .info("Session MC rafraîchie avant lancement pour {}", fresh.username());
                    } catch (Exception refreshErr) {
                        String m = String.valueOf(refreshErr);
                        boolean hardReject = m.contains("invalid_grant") || m.contains("revoked")
                                || m.contains("expired_token");
                        if (hardReject) {
                            org.slf4j.LoggerFactory.getLogger("Auth")
                                    .warn("Refresh token rejeté au lancement: {}", m);
                            Platform.runLater(() -> {
                                status.setText("Ta session Microsoft a expiré — reconnecte ton compte.");
                                progress.setProgress(0);
                                play.setDisable(false);
                                setPlayBusy(false);
                                refreshPlayButton();
                            });
                            return;   // pas de lancement voué à la session invalide
                        }
                        org.slf4j.LoggerFactory.getLogger("Auth")
                                .warn("Refresh avant lancement échoué (token en cache conservé): {}", m);
                    }
                }
                // Mémoire « Auto » (selon la RAM du PC) ou valeur du curseur ; le plafond physique
                // est appliqué par MinecraftLauncher.
                int ramMb = Settings.get().requestedRamMb(fr.nylerp.launcher.util.SystemRam.totalMb());
                // Read Fabric version from the cached manifest so the launcher
                // always installs the loader specified by the modpack publisher,
                // not a hardcoded one that drifts out of sync.
                String fabricVer = readFabricVersionFromManifest();
                Process proc = MinecraftLauncher.launch(account, ramMb, true, fabricVer, new MinecraftLauncher.Listener() {
                    @Override public void onStatus(String s) {
                        Platform.runLater(() -> status.setText(s));
                    }
                    @Override public void onProgress(long d, long t) {
                        Platform.runLater(() -> progress.setProgress(t <= 0 ? -1 : (double) d / t));
                    }
                });

                Platform.runLater(() -> {
                    status.setText("Jeu en cours de lancement…");
                    progress.setProgress(1);
                    playLabel.setText("EN COURS");
                    playLabel.setFont(Fonts.bold(18));
                    playIcon.setVisible(false);
                    playIcon.setManaged(false);
                    play.setDisable(true);
                });

                // Watch the MC process and reset UI when it exits
                if (proc != null) {
                    new Thread(() -> {
                        try { proc.waitFor(); } catch (InterruptedException ignored) {}
                        Platform.runLater(() -> {
                            status.setText("Prêt à jouer");
                            progress.setProgress(0);
                            play.setDisable(false);
                            setPlayBusy(false);
                            refreshPlayButton();
                        });
                    }, "MC-Watch").start();
                }

                // Fermer le launcher au lancement (réglage « closeOnLaunch », activé par défaut).
                // ~4 s de délai pour laisser la fenêtre Minecraft apparaître avant de fermer,
                // sinon l'utilisateur voit un court instant sans aucune fenêtre.
                // Refonte 2026-10 : avec l'icône de barre système, la fenêtre se RANGE (le launcher
                // reste là pour les annonces) ; sans elle, Fond quitte exactement comme avant.
                if (proc != null && Settings.get().closeOnLaunch) {
                    new Thread(() -> {
                        try { Thread.sleep(4000); } catch (InterruptedException ignored) {}
                        Platform.runLater(fr.nylerp.launcher.fond.Fond::fermerApresLancement);
                    }, "CloseOnLaunch").start();
                }
            } catch (Exception ex) {
                Platform.runLater(() -> {
                    status.setText(messageLisible(ex));
                    play.setDisable(false);
                    setPlayBusy(false);
                    refreshPlayButton();
                });
            }
        }, "Launch-Thread").start();
    }

    /**
     * Reads the cached manifest.json and returns loader.version (Fabric version),
     * or null if the file doesn't exist or the field is missing. The launcher
     * falls back to a hardcoded default in that case.
     */
    private static String readFabricVersionFromManifest() {
        try {
            java.nio.file.Path f = fr.nylerp.launcher.config.AppPaths.manifestCache();
            if (!java.nio.file.Files.exists(f)) return null;
            String json = fr.nylerp.launcher.util.LectureTexte.lire(f);
            com.google.gson.JsonObject obj = com.google.gson.JsonParser
                    .parseString(json).getAsJsonObject();
            if (!obj.has("loader")) return null;
            com.google.gson.JsonObject loader = obj.getAsJsonObject("loader");
            return loader.has("version") ? loader.get("version").getAsString() : null;
        } catch (Exception e) {
            return null;
        }
    }
}

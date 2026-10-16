package fr.nylerp.launcher.ui.univers;

import fr.nylerp.launcher.auth.Account;
import fr.nylerp.launcher.config.Settings;
import fr.nylerp.launcher.jeu.LanceurJeu;
import fr.nylerp.launcher.ui.kit.CarteVerre;
import fr.nylerp.launcher.ui.kit.Curseurs;
import fr.nylerp.launcher.ui.kit.Jetons;
import fr.nylerp.launcher.ui.kit.Polices;
import fr.nylerp.launcher.update.CheminsMedias;
import javafx.animation.Interpolator;
import javafx.animation.KeyFrame;
import javafx.animation.KeyValue;
import javafx.animation.Timeline;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.Tooltip;
import javafx.scene.effect.BlurType;
import javafx.scene.effect.DropShadow;
import javafx.scene.image.ImageView;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.media.Media;
import javafx.scene.media.MediaPlayer;
import javafx.scene.media.MediaView;
import javafx.scene.paint.Color;
import javafx.scene.shape.Circle;
import javafx.scene.shape.Rectangle;
import javafx.scene.shape.SVGPath;
import javafx.scene.text.Text;
import javafx.util.Duration;

/**
 * L'UNIVERS NYLERP — la vidéo et l'ambiance de l'ancien accueil, restylées avec le kit.
 *
 * <p>Repris de l'ancien {@code MainView} : les deux vidéos de fond (boucle principale + clip secret à
 * 2 %, et l'œuf de Pâques « agent »), le bouton qui coupe/relance la vidéo (choix retenu), la
 * musique et l'ambiance (lecteurs statiques, volumes réglés en direct depuis les paramètres, son
 * coupé retenu), le compteur de joueurs en ligne, l'actualité et la carte du partenaire Hosterfy,
 * la version du payload. Nouveau : la vidéo couvre toute la fenêtre (recadrage centré), et
 * {@link #suspendre()} / {@link #reprendre()} coupent tout quand la fenêtre est rangée ou que
 * l'autre univers est affiché.
 */
public final class UniversNylerp extends StackPane {

    // ── Lecteurs (ex-MainView) ───────────────────────────────────────────────────────────────────
    private MediaPlayer player1;
    private MediaPlayer player2;
    private MediaView view1;
    private MediaView view2;
    private MediaPlayer currentPlayer;
    private static final java.util.Random RNG = new java.util.Random();
    private static volatile boolean queueVideo2NextLoop = false;
    private static MediaPlayer ambientPlayer;
    private static MediaPlayer musicPlayer;
    private static volatile UniversNylerp instanceForLiveUpdate;
    private static boolean audioMuted = Settings.get().launcherAudioMuted;
    private static volatile UniversNylerp accueilAudio;
    private static final java.util.Set<String> audioEnAttente = java.util.concurrent.ConcurrentHashMap.newKeySet();

    private boolean videoCoupee = Settings.get().launcherVideoDisabled;
    private SVGPath videoIcon;
    private final StackPane corpsFond = new StackPane();
    private Node ancreAudio;
    private boolean suspendu;
    private java.util.function.IntConsumer compteur;
    private final double largeur, hauteur;

    public UniversNylerp(double echelle, Account compte, LanceurJeu lanceur) {
        largeur = 1180 * echelle;
        hauteur = 700 * echelle;
        setPrefSize(largeur, hauteur);
        setMinSize(largeur, hauteur);
        setMaxSize(largeur, hauteur);
        setStyle("-fx-background-color: #08080B;");
        instanceForLiveUpdate = this;
        Rectangle clip = new Rectangle(largeur, hauteur);
        setClip(clip);

        corpsFond.setPrefSize(largeur, hauteur);
        corpsFond.setMaxSize(largeur, hauteur);
        installBackground(corpsFond);
        ancreAudio = this;
        startAmbientAudio();

        // voile pour la lecture : sombre en bas et à gauche, sur la vidéo
        Region voile = new Region();
        voile.setMouseTransparent(true);
        voile.setStyle("-fx-background-color: linear-gradient(to top, rgba(6,6,12,0.72) 0%, rgba(6,6,12,0.0) 48%),"
                + "linear-gradient(to right, rgba(6,6,12,0.55) 0%, rgba(6,6,12,0.0) 45%),"
                + "linear-gradient(to bottom, rgba(6,6,12,0.45) 0%, rgba(6,6,12,0.0) 18%);");

        // ── bloc de gauche : logo, sous-titre, joueurs en ligne ──
        ImageView logo = new ImageView(ImagesDistantes.classpath("/images/logo-mark.png", 0, 0));
        logo.setPreserveRatio(true);
        logo.setSmooth(true);
        logo.setFitHeight(Jetons.rem(4.3));
        ImagesDistantes.charger(CheminsMedias.LOGO_NYLERP_GRAND_2X, null, 0, 0, img -> logo.setImage(img));
        Circle pastille = new Circle(Jetons.rem(0.22), Color.web("#22C55E"));
        Timeline souffle = new Timeline(
                new KeyFrame(Duration.ZERO, new KeyValue(pastille.opacityProperty(), 1.0)),
                new KeyFrame(Duration.seconds(0.9), new KeyValue(pastille.opacityProperty(), 0.45)),
                new KeyFrame(Duration.seconds(1.8), new KeyValue(pastille.opacityProperty(), 1.0)));
        souffle.setCycleCount(Timeline.INDEFINITE);
        souffle.play();
        Text enLigne = new Text("Joueurs en ligne…");
        enLigne.setFont(Polices.extra(Jetons.rem(0.62)));
        enLigne.setFill(Jetons.ENCRE);
        HBox capsule = new HBox(Jetons.rem(0.35), pastille, enLigne);
        capsule.setAlignment(Pos.CENTER_LEFT);
        capsule.getStyleClass().add("kit-capsule");
        capsule.setMaxWidth(Region.USE_PREF_SIZE);
        compteur = count -> Platform.runLater(() -> {
            if (count >= 0) enLigne.setText(count + (count > 1 ? " joueurs en ligne" : " joueur en ligne"));
            else enLigne.setText("Serveur hors ligne");
        });
        JoueursEnLigne.abonner(compteur);
        VBox gauche = new VBox(Jetons.rem(0.6), logo, capsule);
        gauche.setAlignment(Pos.TOP_LEFT);
        gauche.setMaxSize(Region.USE_PREF_SIZE, Region.USE_PREF_SIZE);
        gauche.setPickOnBounds(false);
        StackPane.setAlignment(gauche, Pos.TOP_LEFT);
        StackPane.setMargin(gauche, new Insets(Jetons.rem(4.2), 0, 0, Jetons.rem(2.2)));

        // ── lancement, en bas à gauche ──
        BlocLancement bloc = new BlocLancement(lanceur, false, "NYLERP", Pos.TOP_LEFT);
        StackPane.setAlignment(bloc, Pos.BOTTOM_LEFT);
        StackPane.setMargin(bloc, new Insets(0, 0, Jetons.rem(2.3), Jetons.rem(1.6)));

        // ── actualité, à droite ──
        Region actualite = construireActualite();
        StackPane.setAlignment(actualite, Pos.TOP_RIGHT);
        StackPane.setMargin(actualite, new Insets(Jetons.rem(3.6), Jetons.rem(1.4), 0, 0));

        // ── vidéo (bas droite) et version (bas droite, discrète) ──
        Region videoBtn = buildVideoButton();
        Label version = new Label("v" + fr.nylerp.launcher.config.Constants.runningPayloadVersion());
        version.setFont(Polices.gras(Jetons.rem(0.5)));
        version.setTextFill(Color.rgb(255, 255, 255, 0.55));
        HBox basDroite = new HBox(Jetons.rem(0.6), version, videoBtn);
        basDroite.setAlignment(Pos.CENTER_RIGHT);
        basDroite.setMaxSize(Region.USE_PREF_SIZE, Region.USE_PREF_SIZE);
        basDroite.setPickOnBounds(false);
        StackPane.setAlignment(basDroite, Pos.BOTTOM_RIGHT);
        StackPane.setMargin(basDroite, new Insets(0, Jetons.rem(1.4), Jetons.rem(1.2), 0));

        getChildren().addAll(corpsFond, voile, gauche, actualite, bloc, basDroite);
    }

    // ── Pause / reprise (fenêtre rangée, autre univers) ──────────────────────────────────────────

    /** Coupe vidéos et son (fenêtre rangée, univers Pokényle affiché). */
    public void suspendre() {
        if (suspendu) return;
        suspendu = true;
        for (MediaPlayer p : new MediaPlayer[]{player1, currentPlayer == player2 ? player2 : null, ambientPlayer, musicPlayer}) {
            if (p == null) continue;
            try { p.pause(); } catch (Throwable ignored) { }
        }
    }

    /** Reprend vidéos et son là où ils étaient (le son coupé reste coupé : c'est le drapeau mute). */
    public void reprendre() {
        if (!suspendu) return;
        suspendu = false;
        for (MediaPlayer p : new MediaPlayer[]{player1, currentPlayer == player2 ? player2 : null, ambientPlayer, musicPlayer}) {
            if (p == null) continue;
            try { p.play(); } catch (Throwable ignored) { }
        }
    }

    /** Cet univers est abandonné (autre compte) : vidéos rendues, compteur désabonné. La musique
     *  (lecteurs statiques) reste, comme avec l'ancien accueil. */
    public void liberer() {
        suspendre();
        arreterVideos();
        if (compteur != null) JoueursEnLigne.desabonner(compteur);
        if (instanceForLiveUpdate == this) instanceForLiveUpdate = null;
    }

    // ── Son global (bouton de l'en-tête) ─────────────────────────────────────────────────────────

    public static boolean sonCoupe() { return audioMuted; }

    /** Coupe / réactive musique et ambiance, et retient le choix (ex-toggleMute de MainView). */
    public static void basculerSon() {
        audioMuted = !audioMuted;
        for (MediaPlayer p : new MediaPlayer[] { ambientPlayer, musicPlayer }) {
            if (p == null) continue;
            try { p.setMute(audioMuted); }
            catch (Throwable t) {
                System.err.println("[UniversNylerp] setMute failed (player likely halted): " + t);
            }
        }
        UniversNylerp v = instanceForLiveUpdate;
        if (v != null && v.player2 != null && v.currentPlayer == v.player2) {
            try { v.player2.setMute(audioMuted); } catch (Throwable ignored) {}
        }
        Settings.get().launcherAudioMuted = audioMuted;
        Settings.get().save();
    }

    /** Réglage en direct du volume d'ambiance (curseur des paramètres) — aussi l'audio du clip secret. */
    public static void setLiveAmbientVolume(double v) {
        double clamped = Math.max(0, Math.min(1, v));
        if (ambientPlayer != null) {
            try { ambientPlayer.setVolume(clamped); } catch (Throwable ignored) {}
        }
        if (instanceForLiveUpdate != null) {
            try { instanceForLiveUpdate.applyAmbientVolumeToVideo2(clamped); } catch (Throwable ignored) {}
        }
    }

    /** Réglage en direct du volume de la musique. */
    public static void setLiveMusicVolume(double v) {
        if (musicPlayer != null) {
            try { musicPlayer.setVolume(Math.max(0, Math.min(1, v))); } catch (Throwable ignored) {}
        }
    }

    // ── Vidéos de fond (repris de MainView ; seule la mise à l'échelle change : « cover ») ────────

    private void installBackground(StackPane body) {
        if (videoCoupee) {
            installAgentEasterEgg(body);
            return;
        }
        construireVideos(body);
        installAgentEasterEgg(body);
    }

    private void construireVideos(StackPane body) {
        if (view1 != null || player1 != null) return;
        view1 = newBgMediaView();
        view2 = newBgMediaView();
        view2.setVisible(false);
        body.getChildren().add(0, view1);
        body.getChildren().add(1, view2);
        try {
            player1 = newBgPlayer("/media/videolauncher1.mp4", view1);
            player2 = newBgPlayer("/media/videolauncher2.mp4", view2);
            if (player1 == null || player2 == null) {
                System.err.println("[UniversNylerp] one of the background videos failed to load");
                if (view1 != null) view1.setVisible(false);
                if (view2 != null) view2.setVisible(false);
                return;
            }
            player1.setOnRepeat(this::onPlayer1Repeat);
            player2.setOnRepeat(this::onPlayer2Repeat);
            currentPlayer = player1;
            player1.setMute(true);
            player2.setMute(true);
            applyAmbientVolumeToVideo2(Settings.get().ambientVolume);
            if (!suspendu) player1.play();
            player2.setOnReady(() -> {
                try {
                    player2.seek(javafx.util.Duration.ZERO);
                    player2.pause();
                } catch (Throwable ignored) {}
            });
            player2.play();
        } catch (Throwable t) {
            System.err.println("[UniversNylerp] background videos unavailable: " + t);
            if (view1 != null) view1.setVisible(false);
            if (view2 != null) view2.setVisible(false);
        }
    }

    private void arreterVideos() {
        for (MediaView v : new MediaView[] { view1, view2 }) {
            if (v == null) continue;
            v.setVisible(false);
            try { v.setMediaPlayer(null); } catch (Throwable ignored) {}
            corpsFond.getChildren().remove(v);
        }
        for (MediaPlayer p : new MediaPlayer[] { player1, player2 }) {
            if (p == null) continue;
            try { p.stop(); }    catch (Throwable ignored) {}
            try { p.dispose(); } catch (Throwable ignored) {}
        }
        view1 = null; view2 = null;
        player1 = null; player2 = null;
        currentPlayer = null;
    }

    /** La vidéo (1942 × 1080) COUVRE la fenêtre : hauteur fixée, largeur déduite, recadrage centré.
     *  Hauteur numérique et non liée : une liaison qui démarre à 0 fait retomber JavaFX sur la taille
     *  intrinsèque (le « zoom » vu en 2026-05). */
    private MediaView newBgMediaView() {
        MediaView v = new MediaView();
        v.setPreserveRatio(true);
        v.setSmooth(true);
        double h = Math.max(hauteur, largeur * 1080.0 / 1942.0);
        v.setFitHeight(h);
        StackPane.setAlignment(v, Pos.CENTER);
        return v;
    }

    private MediaPlayer newBgPlayer(String resourcePath, MediaView attachedView) {
        try {
            String url = fr.nylerp.launcher.update.Medias.urlOuClasspath(
                    "nylerp/" + resourcePath.substring(resourcePath.lastIndexOf('/') + 1), resourcePath);
            if (url == null) throw new IllegalStateException("vidéo introuvable : " + resourcePath);
            Media media = new Media(url);
            media.setOnError(() -> {
                System.err.println("[UniversNylerp] media decode error (" + resourcePath + "): " + media.getError());
                Platform.runLater(() -> attachedView.setVisible(false));
            });
            MediaPlayer p = new MediaPlayer(media);
            p.setOnError(() -> {
                System.err.println("[UniversNylerp] mediaplayer error (" + resourcePath + "): " + p.getError());
                Platform.runLater(() -> attachedView.setVisible(false));
            });
            p.setMute(true);
            p.setCycleCount(MediaPlayer.INDEFINITE);
            attachedView.setMediaPlayer(p);
            return p;
        } catch (Throwable t) {
            System.err.println("[UniversNylerp] failed to build player for " + resourcePath + ": " + t);
            return null;
        }
    }

    private void onPlayer1Repeat() {
        if (currentPlayer != player1) return;
        boolean queued    = queueVideo2NextLoop;
        boolean pickRand2 = RNG.nextDouble() < 0.02;
        if (!queued && !pickRand2) return;
        queueVideo2NextLoop = false;
        view2.setVisible(true);
        view1.setVisible(false);
        try { player2.setMute(audioMuted); } catch (Throwable ignored) {}
        try { player2.play(); } catch (Throwable ignored) {}
        currentPlayer = player2;
    }

    private void onPlayer2Repeat() {
        if (currentPlayer != player2) return;
        view1.setVisible(true);
        view2.setVisible(false);
        try {
            player2.setMute(true);
            player2.pause();
            player2.seek(javafx.util.Duration.ZERO);
        } catch (Throwable ignored) {}
        currentPlayer = player1;
    }

    private void applyAmbientVolumeToVideo2(double v) {
        if (player2 == null) return;
        double boosted = Math.min(1.0, Math.max(0.0, v) * 2.0);
        try { player2.setVolume(boosted); } catch (Throwable ignored) {}
    }

    private Scene agentFilterScene;
    private javafx.event.EventHandler<javafx.scene.input.KeyEvent> agentFilter;

    /** Taper « agent » : le clip secret remplace la PROCHAINE boucle naturelle (sans couper). */
    private void installAgentEasterEgg(StackPane body) {
        final StringBuilder buf = new StringBuilder();
        agentFilter = e -> {
            String ch = e.getCharacter();
            if (ch == null || ch.isEmpty()) return;
            for (int i = 0; i < ch.length(); i++) {
                char c = Character.toLowerCase(ch.charAt(i));
                if (c < 0x20 || c > 0x7E) continue;
                buf.append(c);
            }
            if (buf.length() > 8) buf.delete(0, buf.length() - 8);
            if (buf.toString().endsWith("agent")) {
                buf.setLength(0);
                queueVideo2NextLoop = true;
            }
        };
        Runnable sync = () -> {
            Scene now = body.getScene();
            if (now == agentFilterScene) return;
            if (agentFilterScene != null) {
                try { agentFilterScene.removeEventFilter(javafx.scene.input.KeyEvent.KEY_TYPED, agentFilter); } catch (Throwable ignored) {}
            }
            if (now != null) {
                try { now.addEventFilter(javafx.scene.input.KeyEvent.KEY_TYPED, agentFilter); } catch (Throwable ignored) {}
            }
            agentFilterScene = now;
        };
        sync.run();
        body.sceneProperty().addListener((obs, oldS, newS) -> sync.run());
    }

    // ── Musique et ambiance (repris de MainView) ─────────────────────────────────────────────────

    private void startAmbientAudio() {
        String urlAmbiance = fr.nylerp.launcher.update.Medias.urlOuClasspath(CheminsMedias.NYLERP_AMBIANCE, "/media/ambient.mp3");
        String urlMusique = fr.nylerp.launcher.update.Medias.urlOuClasspath(CheminsMedias.NYLERP_MUSIQUE, "/media/music.mp3");
        if (ambientPlayer == null && urlAmbiance == null) relancerAudioQuandPret(CheminsMedias.NYLERP_AMBIANCE);
        if (musicPlayer == null && urlMusique == null) relancerAudioQuandPret(CheminsMedias.NYLERP_MUSIQUE);
        if (ambientPlayer == null && urlAmbiance != null) {
            try {
                Media media = new Media(urlAmbiance);
                media.setOnError(() -> System.err.println("[UniversNylerp] ambient media error: " + media.getError()));
                ambientPlayer = new MediaPlayer(media);
                ambientPlayer.setOnError(() -> System.err.println("[UniversNylerp] ambient player error: " + ambientPlayer.getError()));
                ambientPlayer.setVolume(Settings.get().ambientVolume);
                ambientPlayer.setCycleCount(MediaPlayer.INDEFINITE);
                ambientPlayer.setMute(audioMuted);
                ambientPlayer.setAutoPlay(!suspendu);
            } catch (Throwable t) {
                System.err.println("[UniversNylerp] ambient audio unavailable: " + t);
            }
        }
        if (musicPlayer == null && urlMusique != null) {
            try {
                Media media = new Media(urlMusique);
                media.setOnError(() -> System.err.println("[UniversNylerp] music media error: " + media.getError()));
                musicPlayer = new MediaPlayer(media);
                musicPlayer.setOnError(() -> System.err.println("[UniversNylerp] music player error: " + musicPlayer.getError()));
                musicPlayer.setVolume(Settings.get().musicVolume);
                musicPlayer.setCycleCount(MediaPlayer.INDEFINITE);
                musicPlayer.setMute(audioMuted);
                musicPlayer.setAutoPlay(!suspendu);
            } catch (Throwable t) {
                System.err.println("[UniversNylerp] music unavailable: " + t);
            }
        }
    }

    /** Rejoue {@link #startAmbientAudio} à l'arrivée du média — seulement si cet univers est à l'écran. */
    private void relancerAudioQuandPret(String chemin) {
        accueilAudio = this;
        if (!audioEnAttente.add(chemin)) return;
        fr.nylerp.launcher.update.Medias.quandDisponible(chemin, p -> Platform.runLater(() -> {
            audioEnAttente.remove(chemin);
            UniversNylerp v = accueilAudio;
            Node a = v == null ? null : v.ancreAudio;
            if (a != null && !v.suspendu && a.getScene() != null && a.getScene().getWindow() != null
                    && a.getScene().getWindow().isShowing()) {
                System.out.println("[UniversNylerp] média audio arrivé (" + chemin + ") : lecture");
                v.startAmbientAudio();
            }
        }));
    }

    // ── Bouton vidéo (repris de MainView, au style du kit) ───────────────────────────────────────

    private Region buildVideoButton() {
        double c = Jetons.rem(1.9);
        StackPane btn = new StackPane();
        btn.setPrefSize(c, c);
        btn.setMinSize(c, c);
        btn.setMaxSize(c, c);
        btn.setCursor(Curseurs.main());
        String repos = "-fx-background-color: rgba(255,255,255,0.78); -fx-background-radius: 99; -fx-border-color: rgba(255,255,255,0.95); -fx-border-radius: 99; -fx-border-width: 1.2;";
        String survol = repos.replace("0.78", "1.0");
        btn.setStyle(repos);
        btn.setOnMouseEntered(e -> btn.setStyle(survol));
        btn.setOnMouseExited(e -> btn.setStyle(repos));
        Tooltip.install(btn, new Tooltip("Couper / relancer la vidéo de fond\nCoupée, elle ne se relancera plus au démarrage"));
        videoIcon = new SVGPath();
        videoIcon.setFill(Jetons.ICONE);
        applyVideoIconShape();
        btn.getChildren().add(videoIcon);
        btn.setOnMouseClicked(e -> toggleVideo());
        return btn;
    }

    private void toggleVideo() {
        videoCoupee = !videoCoupee;
        try {
            if (videoCoupee) arreterVideos();
            else construireVideos(corpsFond);
        } catch (Throwable t) {
            System.err.println("[UniversNylerp] bascule du fond vidéo impossible : " + t);
        }
        applyVideoIconShape();
        Settings.get().launcherVideoDisabled = videoCoupee;
        Settings.get().save();
    }

    private void applyVideoIconShape() {
        if (videoIcon == null) return;
        videoIcon.setContent(videoCoupee ? "M8 5v14l11-7z" : "M6 19h4V5H6v14zm8-14v14h4V5h-4z");
        videoIcon.setScaleX(0.75);
        videoIcon.setScaleY(0.75);
    }

    // ── Actualité (mêmes cartes que l'ancien panneau, en verre clair) ───────────────────────────

    private Region construireActualite() {
        double lp = Jetons.rem(14.2);
        Text titre = new Text("Actualité");
        titre.setFont(Polices.titre(Jetons.rem(0.95)));
        titre.setFill(Jetons.ENCRE);
        SVGPath fleche = new SVGPath();
        fleche.setContent("M 0 4 L 6 -2 L 12 4");
        fleche.setStroke(Jetons.ICONE);
        fleche.setStrokeWidth(2);
        fleche.setFill(Color.TRANSPARENT);
        Region espace = new Region();
        HBox.setHgrow(espace, Priority.ALWAYS);
        HBox entete = new HBox(Jetons.rem(0.5), titre, espace, fleche);
        entete.setAlignment(Pos.CENTER_LEFT);
        entete.setCursor(Curseurs.main());

        VBox liste = new VBox(Jetons.rem(0.6), carteOuverture(lp - Jetons.rem(2.0)), carteHosterfy(lp - Jetons.rem(2.0)));
        ScrollPane defil = new ScrollPane(liste);
        defil.setFitToWidth(true);
        defil.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        defil.setVbarPolicy(ScrollPane.ScrollBarPolicy.AS_NEEDED);
        defil.getStyleClass().add("kit-defilement");
        defil.setPrefViewportHeight(Jetons.rem(12.5));
        defil.setMaxHeight(Jetons.rem(12.5));

        VBox contenu = new VBox(Jetons.rem(0.6), entete, defil);
        CarteVerre carte = new CarteVerre(CarteVerre.Variante.CLAIRE, contenu);
        carte.setPadding(new Insets(Jetons.rem(0.8)));
        carte.setPrefWidth(lp);
        carte.setMaxWidth(lp);
        carte.setMaxHeight(Region.USE_PREF_SIZE);
        // fermée par défaut : une petite carte « Actualité », qui se déplie au clic
        boolean[] ouvert = {false};
        defil.setVisible(false);
        defil.setManaged(false);
        fleche.setRotate(180);
        entete.setOnMouseClicked(e -> {
            ouvert[0] = !ouvert[0];
            defil.setVisible(ouvert[0]);
            defil.setManaged(ouvert[0]);
            new Timeline(new KeyFrame(Duration.millis(260),
                    new KeyValue(fleche.rotateProperty(), ouvert[0] ? 0 : 180, Interpolator.EASE_BOTH))).play();
        });
        return carte;
    }

    private Region carteOuverture(double l) {
        ImageView cover = new ImageView(ImagesDistantes.classpath("/assets/news/ouverture.jpg", 0, 0));
        cover.setFitWidth(l);
        cover.setPreserveRatio(true);
        cover.setSmooth(true);
        Label tag = etiquette("OUVERTURE");
        Text t = new Text("Ouverture du serveur");
        t.setFont(Polices.noir(Jetons.rem(0.72)));
        t.setFill(Jetons.ENCRE);
        Text d = new Text("Rendez-vous le 8 juillet à 14h00 pour l'ouverture du serveur !");
        d.setFont(Polices.gras(Jetons.rem(0.56)));
        d.setFill(Jetons.ENCRE_2);
        d.setWrappingWidth(l - Jetons.rem(1.2));
        VBox texte = new VBox(Jetons.rem(0.3), tag, t, d);
        texte.setPadding(new Insets(Jetons.rem(0.55)));
        return carteNouvelle(new VBox(cover, texte), l, null);
    }

    private Region carteHosterfy(double l) {
        ImageView logo = new ImageView(ImagesDistantes.classpath("/assets/partenaires/hosterfy.png", 32, 32));
        logo.setFitWidth(32);
        logo.setFitHeight(32);
        logo.setSmooth(false);
        Label tag = etiquette("PARTENAIRE");
        Text nom = new Text("Hosterfy");
        nom.setFont(Polices.noir(Jetons.rem(0.72)));
        nom.setFill(Jetons.ENCRE);
        Text quoi = new Text("L'hébergeur qui fait tourner nos serveurs.");
        quoi.setFont(Polices.gras(Jetons.rem(0.56)));
        quoi.setFill(Jetons.ENCRE_2);
        quoi.setWrappingWidth(l - Jetons.rem(4));
        Text lien = new Text("hosterfy.com");
        lien.setFont(Polices.extra(Jetons.rem(0.56)));
        lien.setFill(Jetons.ACTIF);
        HBox ligne = new HBox(Jetons.rem(0.55), logo, new VBox(Jetons.rem(0.15), nom, quoi, lien));
        ligne.setAlignment(Pos.CENTER_LEFT);
        VBox bloc = new VBox(Jetons.rem(0.35), tag, ligne);
        bloc.setPadding(new Insets(Jetons.rem(0.55)));
        return carteNouvelle(bloc, l, "https://hosterfy.com");
    }

    private static Label etiquette(String s) {
        Label l = new Label(s);
        l.setFont(Polices.noir(Jetons.rem(0.42)));
        l.setTextFill(Color.WHITE);
        l.setStyle("-fx-background-color: linear-gradient(to right, #73dbff, #10a7e6); -fx-background-radius: 99; -fx-padding: 2 8 2 8;");
        return l;
    }

    private Region carteNouvelle(VBox contenu, double l, String lien) {
        contenu.setStyle("-fx-background-color: rgba(255,255,255,0.85); -fx-background-radius: 12;");
        contenu.setPrefWidth(l);
        contenu.setMaxWidth(l);
        Rectangle clip = new Rectangle();
        clip.setArcWidth(24);
        clip.setArcHeight(24);
        clip.widthProperty().bind(contenu.widthProperty());
        clip.heightProperty().bind(contenu.heightProperty());
        contenu.setClip(clip);
        contenu.setOnMouseEntered(e -> contenu.setTranslateY(-2));
        contenu.setOnMouseExited(e -> contenu.setTranslateY(0));
        if (lien != null) {
            contenu.setCursor(Curseurs.main());
            contenu.setOnMouseClicked(e -> ouvrirNavigateur(lien));
        }
        return contenu;
    }

    public static void ouvrirNavigateur(String url) {
        try {
            if (java.awt.Desktop.isDesktopSupported() && java.awt.Desktop.getDesktop().isSupported(java.awt.Desktop.Action.BROWSE)) {
                java.awt.Desktop.getDesktop().browse(new java.net.URI(url));
            }
        } catch (Exception ignored) { }
    }
}

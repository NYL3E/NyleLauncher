# NyleLauncher

Launcher Minecraft cross-platform pour **NyleRP** — `play.nylerp.fr`.

## Features

- Connexion **Microsoft** (compte officiel Minecraft Java Edition)
- Connexion **offline/crack** (mode non-premium)
- **Mise à jour automatique** du modpack à chaque démarrage via `manifest.json`
- Lancement direct dans le serveur `play.nylerp.fr`
- UI orange/gris foncé, police Montserrat

## Stack

- Java 21, JavaFX 21
- OpenLauncherLib + FlowUpdater (framework lancement MC type Paladium)
- OpenAuth pour Microsoft OAuth
- Build : Gradle, Shadow Plugin

## Développement

```bash
./gradlew run            # lance le launcher
./gradlew shadowJar      # builds build/libs/nylelauncher-<version>.jar
```

## Structure

- `src/main/java/fr/nylerp/launcher/` — code Java
  - `Main.java` — point d'entrée
  - `LauncherApp.java` — classe JavaFX principale, gère les écrans
  - `ui/` — vues (login, main, progress)
  - `auth/` — Microsoft + offline
  - `update/` — manifest + téléchargement des mods
  - `launch/` — lancement Minecraft (OpenLauncherLib)
- `src/main/resources/` — CSS, fonts, images, app.properties
- `pack/` — mods et configs à distribuer (montés dans le manifest)
- `scripts/generate-manifest.py` — scan `pack/` et produit `manifest.json`
- `.github/workflows/` — CI cross-platform + publication

## Polices

Les fichiers `Montserrat-*.ttf` doivent être placés dans `src/main/resources/fonts/` (OFL license).
Download depuis https://fonts.google.com/specimen/Montserrat → "Get font" → dézippe et copie les fichiers static (Regular, Medium, SemiBold, Bold) dans `fonts/`.

## Déploiement

1. Push un tag `vX.Y.Z` → GitHub Actions build Windows `.msi`, macOS `.dmg`, Linux `.AppImage` + attache à la Release.
2. Pour une mise à jour de modpack : push dans `pack/`, le workflow `publish-modpack.yml` regenère `manifest.json` et upload les fichiers.

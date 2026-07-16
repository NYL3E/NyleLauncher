# WinGet manifest — NyleLauncher

## Comment soumettre

1. **Fork** : `microsoft/winget-pkgs` → fork sur le compte NYL3E
2. **Clone** :
   ```bash
   git clone git@github.com:NYL3E/winget-pkgs.git
   cd winget-pkgs
   ```
3. **Copier les manifests** : copie tout le dossier `manifests/n/NYL3E/NyleLauncher/0.3.18/`
   depuis ici vers le repo forké au même path
4. **Valider en local** (optionnel mais recommandé) :
   ```powershell
   # Si tu as Visual Studio installé sur Windows, sinon skip cette étape
   winget validate --manifest manifests/n/NYL3E/NyleLauncher/0.3.18
   ```
5. **Commit + push** :
   ```bash
   git checkout -b add-NYL3E.NyleLauncher-0.3.18
   git add manifests/n/NYL3E/NyleLauncher/
   git commit -m "New package: NYL3E.NyleLauncher version 0.3.18"
   git push -u origin add-NYL3E.NyleLauncher-0.3.18
   ```
6. **Pull Request** vers `microsoft/winget-pkgs` :
   - Titre : `New package: NYL3E.NyleLauncher version 0.3.18`
   - Body : cocher la checklist standard (lire CONTRIBUTING.md de winget-pkgs)
   - Le bot CI va lancer ~10 jobs de validation automatique (MSI download, schema check,
     install/uninstall sandbox)
   - Si tous verts → review humain en 1-7 jours → merge → ton package est dans WinGet

## Une fois mergé

Les users peuvent installer via :
```powershell
winget install NYL3E.NyleLauncher
```

→ **Aucun SmartScreen, aucun warning Defender** pour ce path d'install.

## Pour chaque nouvelle release

1. Copier le dossier `0.3.18/` → `0.X.Y/` (la nouvelle version)
2. Mettre à jour dans chaque YAML :
   - `PackageVersion: 0.X.Y`
   - `InstallerUrl` (nouveau tag)
   - `InstallerSha256` (recalculer : `Get-FileHash` sur Windows, `shasum -a 256` sur Mac/Linux,
     puis convertir en upper-case)
   - `ProductCode` (récupérer depuis le MSI : `msiexec /a path.msi /qb` ou `dark.exe -x` pour
     extraire le ProductCode du Property table)
   - `ReleaseNotesUrl` (tag du nouveau release)
3. Commit + push + PR → merge → user peut faire `winget upgrade NYL3E.NyleLauncher`

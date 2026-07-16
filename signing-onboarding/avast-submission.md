# Avast / AVG — False Positive Submission

**URL** : https://www.avast.com/false-positive-file-form.php
(Le même formulaire couvre AVG, qui partage le moteur Avast depuis 2016.)

## Étapes

1. Sélectionner **Incorrect detection on a clean file**
2. Upload le MSI : `NyleLauncher-windows-v{X.Y.Z}.msi`
3. Remplir les champs ci-dessous

---

## Réponses

### Your name
Lenny TRIDAT

### Your email
lennytridat@gmail.com

### Detection name
*Laisser vide si non détecté localement — sinon copier le nom Avast/AVG affiché
(exemple : `Win32:Evo-gen [Susp]`, `IDP.Generic`, `FileRepMetagen`)*

### File source URL
https://github.com/NYL3E/NyleLauncher/releases/latest

### Your comments
```
NyleLauncher is the official open-source launcher for NyleRP, a French
Minecraft roleplay server (play.nylerp.fr). The MSI is built by jpackage
(part of OpenJDK 21) on GitHub Actions from public source code under the
MIT license.

The binary is a Java application that:
- Authenticates the user against Microsoft Live OAuth (Xbox Live API)
- Downloads a curated Minecraft modpack from our GitHub Releases CDN
- Launches Minecraft Java Edition

It does not modify the registry outside its own keys, does not install any
service, does not collect telemetry, does not phone home.

Source : https://github.com/NYL3E/NyleLauncher
License : MIT
Build CI : https://github.com/NYL3E/NyleLauncher/actions
Maintainer : Lenny TRIDAT, lennytridat@gmail.com
SHA256 : c08119b8258c1efb2c8df9f13e18e663d8ab0541486c747323270a3a0ddaa5ec

Please whitelist this binary and the publisher signature chain (once we
have one via SignPath Foundation, application in progress).
```

---

**Délai réponse** : 1-3 jours business
**Re-submit** : à chaque release majeure (chaque nouveau hash de MSI)

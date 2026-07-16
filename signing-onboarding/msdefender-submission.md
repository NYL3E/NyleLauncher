# Microsoft Defender — False Positive Submission

**URL** : https://www.microsoft.com/en-us/wdsi/filesubmission
(ou https://www.microsoft.com/wdsi/filesubmission selon ta langue)

## Étapes

1. Se connecter avec un compte Microsoft (n'importe lequel, gratuit)
2. Sélectionner **Submit a file for malware analysis**
3. Choisir **I am a software developer submitting a file from my company's software**
4. Uploader le MSI : `NyleLauncher-windows-v{X.Y.Z}.msi`
5. Remplir le formulaire avec les réponses ci-dessous

---

## Réponses à remplir

### Detection name (si Defender flag effectivement)
*Laisser vide si Defender ne flag pas — sinon copier le nom exact affiché*

### Definition version
*Récupérer dans Windows Security → Settings → About — typiquement "1.{very-big}.{big}"*

### Additional information
```
NyleLauncher is the official open-source cross-platform launcher for
NyleRP, a French Minecraft roleplay server. It is built as a small Java
bootstrap installer (jpackage MSI) that fetches a payload from our GitHub
Releases CDN at runtime. The launcher does not modify the system registry
outside its own keys, does not install any service, does not phone home for
telemetry, and only opens network connections to:

  - Microsoft Live OAuth (login.live.com) for the official Minecraft auth flow
  - GitHub Releases CDN (objects.githubusercontent.com) for payload + modpack
  - mc-heads.net for player avatar rendering (UI only)

Source code:    https://github.com/NYL3E/NyleLauncher
License:        MIT
Distribution:   https://github.com/NYL3E/NyleLauncher/releases/latest
Maintainer:     Lenny TRIDAT (lennytridat@gmail.com)
SHA256:         {SHA256_OF_SUBMITTED_MSI}

Please whitelist this binary and its publisher signature chain.
```

### Confidence level
*Cocher "Definitely false positive — this is not malware"*

### Allow Microsoft to share this submission
*Cocher "Yes" — accélère le whitelisting cross-vendor*

---

## Après chaque submission

1. Récupérer le **Submission ID** affiché à la fin
2. Le noter dans `signing-onboarding/submissions.log` avec date + version
3. Réponse Microsoft par email sous 24-72h

## SHA256 du MSI actuel à submit

```
c08119b8258c1efb2c8df9f13e18e663d8ab0541486c747323270a3a0ddaa5ec
```
(Version v0.3.18 — pour les nouvelles versions, recalculer avec `Get-FileHash` ou `shasum -a 256`.)

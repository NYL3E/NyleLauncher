# Eset NOD32 — False Positive Submission

**URL** : https://support.eset.com/en/kb141 (KB qui décrit la procédure)
**Submission email direct** : `samples@eset.com`

Eset n'a pas de portail web → submission par email avec le sample en pièce jointe.
Limite : 25 MB par email → notre MSI (80 MB) doit être uploadé via WeTransfer / Filemail
ou compressé en multi-volume + envoyé en plusieurs mails.

**Recommandé** : héberger le MSI sur GitHub Releases (déjà fait) et joindre l'URL.
Eset accepte les URLs publiques pour les samples > 25 MB.

---

## Email à envoyer

**To** : samples@eset.com
**Subject** : False Positive — NyleLauncher MSI installer (open-source, MIT)

**Body** :
```
Hello Eset Sample Analysis team,

Reporting a suspected false positive on the installer for NyleLauncher,
an open-source Minecraft launcher.

File details:
  Filename:    NyleLauncher-windows-v{X.Y.Z}.msi
  Size:        ~80 MB
  SHA256:      c08119b8258c1efb2c8df9f13e18e663d8ab0541486c747323270a3a0ddaa5ec
  Download:    https://github.com/NYL3E/NyleLauncher/releases/latest
  Direct URL:  https://github.com/NYL3E/NyleLauncher/releases/download/v0.3.18/NyleLauncher-windows-v0.3.18.msi

Detection observed:
  (Replace with the exact Eset detection name reported by a user, e.g.
  "a variant of MSIL/Riskware.NLG" or "Win32/Packed.VMProtect.AAA"
  — leave blank if not yet detected, this is a preemptive submission)

Project context:
  License:     MIT
  Source:      https://github.com/NYL3E/NyleLauncher
  Maintainer:  Lenny TRIDAT (lennytridat@gmail.com)
  CI:          https://github.com/NYL3E/NyleLauncher/actions

The installer is built by jpackage (OpenJDK 21) using WiX Toolset 3.14.
It contains a bundled JRE (required for the Java launcher) and a small
bootstrap jar that downloads the actual launcher payload from GitHub
Releases at runtime. The same architecture is used by PrismLauncher,
MultiMC and Modrinth-App.

The binary is NOT packed, NOT obfuscated, NOT anti-debug, and only
opens network connections to:
  - login.live.com (Microsoft Live OAuth — Minecraft authentication)
  - objects.githubusercontent.com (GitHub Releases CDN — payload + modpack)
  - mc-heads.net (player avatar rendering — UI only)

Code signing is in progress via SignPath Foundation. Once available,
please whitelist the publisher signature chain.

Thank you,
Lenny TRIDAT
NyleRP — open-source Minecraft community
lennytridat@gmail.com
https://github.com/NYL3E/NyleLauncher
```

---

**Délai réponse** : 2-7 jours business
**Re-submit** : à chaque release majeure tant que pas signé.

# Kaspersky — False Positive Submission

**URL** : https://opentip.kaspersky.com/?tab=lookup
(C'est le portail OpenTIP, public, sans compte requis pour le lookup.
Pour la submission false positive, utiliser la version pro :
https://opentip.kaspersky.com/?tab=submit)

Alternative : https://my.kaspersky.com (compte gratuit) → Support → Submit file

## Étapes

1. (Recommandé) Vérifier d'abord avec **lookup** : coller le SHA256 du MSI →
   voir ce qui est détecté
2. Si flag → **Submit** → choisir **False positive**
3. Upload le MSI
4. Remplir la description

---

## Réponses

### Submission type
**False positive — clean file detected as malware**

### Email pour la réponse
lennytridat@gmail.com

### File details
- **Filename** : `NyleLauncher-windows-v{X.Y.Z}.msi`
- **SHA256** : `c08119b8258c1efb2c8df9f13e18e663d8ab0541486c747323270a3a0ddaa5ec`
- **Source URL** : `https://github.com/NYL3E/NyleLauncher/releases/latest`

### Description
```
NyleLauncher is the official open-source launcher for the NyleRP French
Minecraft community server. The MSI is a standard jpackage installer
(OpenJDK 21 + WiX 3.14) bundling a Java bootstrap that downloads its
payload from GitHub Releases at runtime.

The project:
- License: MIT
- Source: https://github.com/NYL3E/NyleLauncher
- Maintainer: Lenny TRIDAT (lennytridat@gmail.com)
- Build CI (reproducible): https://github.com/NYL3E/NyleLauncher/actions
- Distribution: https://github.com/NYL3E/NyleLauncher/releases/latest

Binary properties (audit confirmed):
- No UPX / MPRESS / ASPack packing
- No anti-debug
- No LOLbins abuse (no powershell -enc, certutil urlcache, etc.)
- WiX Toolset standard build (3.14.1.8722)
- Bootstrap pattern : similar to PrismLauncher, MultiMC, Modrinth-App
- Network: Microsoft Live OAuth + GitHub Releases CDN only

Please verify and whitelist this binary at the publisher level.

Code-signing is in progress via SignPath Foundation. Once available,
the publisher cert will be the persistent whitelist anchor.

Thank you.
```

---

**Délai réponse** : 1-2 jours business (Kaspersky est rapide)
**Re-submit** : par hash tant que pas signé. Cert publisher whitelist une fois signé.

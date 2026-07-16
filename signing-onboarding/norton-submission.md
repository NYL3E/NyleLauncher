# Norton / Symantec — False Positive Submission

**URL** : https://submit.symantec.com/false_positive/

## Étapes

1. Aucun compte requis pour la submission file
2. Catégorie : **Norton Consumer Products** (sauf si tu utilises un produit Symantec entreprise)
3. Type : **Dispute the detection**
4. Upload le MSI

---

## Réponses

### Submitter information
- **Name** : Lenny TRIDAT
- **Email** : lennytridat@gmail.com
- **Company** : NyleRP (Independent open-source project)
- **Country** : France

### File details
- **Filename** : `NyleLauncher-windows-v{X.Y.Z}.msi`
- **Detection name** : *celui affiché par Norton, sinon laisser vide*
- **File source** : `https://github.com/NYL3E/NyleLauncher/releases/latest`

### Comments
```
Hello Norton team,

NyleLauncher is the official open-source launcher for NyleRP, a French
Minecraft roleplay community server. It is published under the MIT license
with all source code public at https://github.com/NYL3E/NyleLauncher.

The MSI is a standard jpackage installer (OpenJDK 21 + WiX Toolset 3.14)
that installs a bootstrap Java application. At runtime, the bootstrap
downloads its payload (the actual launcher logic) from our GitHub Releases
CDN, verifying integrity via SHA256 against a public manifest.

Network activity is limited to:
- login.live.com (Microsoft Xbox Live OAuth — required for Minecraft auth)
- objects.githubusercontent.com (GitHub Releases asset CDN)
- mc-heads.net (player avatar rendering, UI only)

No registry modifications outside the installer's own keys, no service
installation, no scheduled tasks, no telemetry, no DNS hijack, no
anti-debug, no obfuscation.

Build CI logs : https://github.com/NYL3E/NyleLauncher/actions
SHA256 of submitted file : c08119b8258c1efb2c8df9f13e18e663d8ab0541486c747323270a3a0ddaa5ec

We are in the process of obtaining code-signing via SignPath Foundation
(open-source program). Once signed, we will resubmit so the publisher
signature is whitelisted at the cert level rather than per-hash.

Thank you,
Lenny TRIDAT
```

---

**Délai réponse** : 3-7 jours business
**Re-submit** : à chaque release majeure tant qu'on n'a pas le cert signé

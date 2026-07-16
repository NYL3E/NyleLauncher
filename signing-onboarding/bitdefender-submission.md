# BitDefender — False Positive Submission

**URL** : https://www.bitdefender.com/consumer/support/answer/29358/
(Ouvrir le ticket via le lien "submit a sample" en bas de la page — section consumer)

Alternative directe : https://www.bitdefender.com/business/support/en/77209-127255-false-positive-submission.html

## Étapes

1. Aucun compte requis
2. Sélectionner **Submit suspected false positive** → upload file
3. Limite : fichier ≤ 100 MB (OK, notre MSI = 80 MB)
4. Si le fichier dépasse, zipper et joindre le ZIP avec mot de passe `infected`

---

## Réponses

### Contact
- **Name** : Lenny TRIDAT
- **Email** : lennytridat@gmail.com
- **Company / Organization** : NyleRP (open-source Minecraft community)
- **Country** : France

### Product
- **Affected BitDefender product** : Total Security / Internet Security / Antivirus Free (cocher selon retour de l'utilisateur qui a vu le warning, sinon laisser sur "all consumer products")
- **Detection name** : *si reporté par un user, le copier — sinon "Gen:Variant.Heur.{...}" est le plus commun*

### File details
- **Filename** : `NyleLauncher-windows-v{X.Y.Z}.msi`
- **SHA256** : `c08119b8258c1efb2c8df9f13e18e663d8ab0541486c747323270a3a0ddaa5ec`
- **File source** : `https://github.com/NYL3E/NyleLauncher/releases/latest`

### Description
```
Hello BitDefender FP team,

The submitted MSI is the installer for NyleLauncher, an open-source
Minecraft launcher for the NyleRP server community. The project is
published under the MIT license at https://github.com/NYL3E/NyleLauncher.

Technical profile of the binary:
- Standard jpackage MSI (OpenJDK 21 + WiX Toolset 3.14.1.8722)
- Bundles a private JRE — required to run a Java application
- Bootstrap pattern: small Java jar that downloads the actual payload
  from GitHub Releases at runtime (verified SHA256)
- No registry writes outside HKCU/HKLM\Software\NyleLauncher
- No services, scheduled tasks, autostart entries
- No anti-debug, no UPX/MPRESS/ASPack packing
- Network: Microsoft Live OAuth + GitHub Releases CDN + mc-heads.net only

We suspect the detection is heuristic on the "downloader + bundled JRE"
pattern, which is common for legitimate Java launchers (Modrinth-App,
PrismLauncher, MultiMC, ATLauncher all share the same shape).

Build pipeline (public CI): https://github.com/NYL3E/NyleLauncher/actions
Maintainer:                  Lenny TRIDAT, lennytridat@gmail.com
Code signing:                In progress via SignPath Foundation

Please whitelist this binary and the future publisher signature once we
have one.

Thank you,
Lenny
```

---

**Délai réponse** : 2-5 jours
**Re-submit** : à chaque release tant que pas signé. Une fois signé, BitDefender
whitelist par cert chain et tu n'as plus à re-submit.

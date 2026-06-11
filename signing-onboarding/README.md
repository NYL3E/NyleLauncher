# Signing & Notarization Onboarding — NyleLauncher

Ce dossier contient les éléments prêts-à-l'envoi pour faire signer / notariser /
whitelister NyleLauncher sur Windows et macOS. Voir `tasks/lessons.md` pour les
raisons de chaque démarche.

## Contenu

| Fichier | Quoi | Quand le lancer | Délai |
|---|---|---|---|
| `signpath-foundation-application.md` | Texte à coller dans le formulaire SignPath | Maintenant | ~1-2 semaines |
| `msdefender-submission.md` | Texte + binding pour submit.microsoft.com | Maintenant | 24-72h |
| `avast-submission.md` | Avast/AVG false positive | Après MSI re-uploaded à chaque grosse release | 1-3 jours |
| `norton-submission.md` | Norton/Symantec false positive | idem | 3-7 jours |
| `bitdefender-submission.md` | BitDefender false positive | idem | 2-5 jours |
| `kaspersky-submission.md` | Kaspersky OpenTIP false positive | idem | 1-2 jours |
| `eset-submission.md` | Eset NOD32 false positive | idem | 2-7 jours |
| `winget-manifest/` | YAML manifest prêt pour `microsoft/winget-pkgs` PR | Après une release signée stable | 3-7 jours |
| `apple-developer-setup.md` | Étapes Apple Developer + secrets GitHub pour macOS notarization | Quand tu paies les 99 € | 1-2 jours |

## État actuel (audit MSI v0.3.18)

- ✅ Pas de packer (UPX/MPRESS/ASPack)
- ✅ Métadonnées EXE complètes (Subject, Author, WiX Toolset 3.14.1)
- ✅ Pas de LOLbin abuse dans les strings
- ✅ Bootstrap architecture déjà en place (bootstrap 0.3MB / payload 80MB séparés)
- ❌ Pas signé — cause principale des warnings SmartScreen / Defender / AV tiers

**SHA256 du MSI actuel** : `c08119b8258c1efb2c8df9f13e18e663d8ab0541486c747323270a3a0ddaa5ec`

## ✅ NOUVEAU (2026-06-11) — le CI sait signer dès que les certificats existent

Le workflow `bootstrap.yml` (tags `v*`) contient désormais des étapes de signature
**conditionnelles** : elles ne font rien tant que les secrets n'existent pas, et
s'activent automatiquement dès qu'ils sont ajoutés (Settings → Secrets → Actions).

### Windows (tue l'avertissement SmartScreen)
Secrets à créer une fois le certificat obtenu :
- `WINDOWS_CERT_PFX_BASE64` — le .pfx encodé : `base64 -i cert.pfx | pbcopy`
- `WINDOWS_CERT_PFX_PASSWORD` — le mot de passe du .pfx

Options de certificat, par ordre de préférence :
1. **SignPath Foundation** (gratuit, OSS) — candidature soumise le 2026-05-19, relancer si pas de réponse.
   NB : SignPath signe via leur infra (pas un .pfx) — si retenu, adapter l'étape CI avec leur action officielle `signpath/github-action-submit-signing-request`.
2. **Azure Trusted Signing** (~10 $/mois) — le plus rapide à obtenir ; signe via `signtool /dlib` (pas de .pfx non plus → adapter l'étape, doc Azure « Trusted Signing in GitHub Actions »).
3. **Certificat OV classique** (Certum ~250 €/an, .pfx direct) — compatible tel quel avec l'étape CI ; la réputation SmartScreen se construit en 2-6 semaines de téléchargements.
4. **Certificat EV** (~400 €/an) — réputation SmartScreen immédiate.

### macOS (tue l'avertissement Gatekeeper)
Prérequis : Apple Developer Program (99 €/an) → certificat « Developer ID Application » → export .p12.
Secrets à créer (cf. `apple-developer-setup.md` pour le pas-à-pas) :
- `APPLE_CERT_P12_BASE64`, `APPLE_CERT_P12_PASSWORD`
- `APPLE_DEVELOPER_ID` (email du compte), `APPLE_APP_PASSWORD` (mot de passe d'app), `APPLE_TEAM_ID`

Le CI fait alors : import keychain → `jpackage --mac-sign` (hardened runtime) →
`notarytool submit --wait` → `stapler staple`. Résultat : DMG ouvrable sans aucun
avertissement, même fraîchement téléchargé.

### En attendant les certificats
Les soumissions antivirus/Defender de ce dossier restent le meilleur palliatif,
et la FAQ du site (`/telecharger/`) documente la procédure SmartScreen pour les joueurs.

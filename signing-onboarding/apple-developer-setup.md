# Apple Developer Setup — macOS Notarization

Procédure complète une fois que tu as payé le **Apple Developer Program (99 €/an)**.

## Étape 1 : créer le compte (1-2 jours)

1. https://developer.apple.com/programs/enroll/ → Enroll as **Individual**
2. Identité : ID national + photo + carte bancaire pour les 99 €
3. Apple vérifie en 24-48h, tu reçois un email "Welcome to the Apple Developer Program"

## Étape 2 : créer le certificat Developer ID Application (10 min)

1. Sur ton Mac, ouvrir **Keychain Access** → menu **Certificate Assistant** →
   **Request a Certificate From a Certificate Authority**
2. Email : ton email Apple Dev
3. Common Name : `NyleRP`
4. Sélectionner **Saved to disk** → sauvegarder le `CertificateSigningRequest.certSigningRequest`
5. Aller sur https://developer.apple.com/account/resources/certificates/list
6. Cliquer **+** → choisir **Developer ID Application** → upload le CSR
7. Télécharger le .cer généré → double-clic pour l'installer dans Keychain
8. **Exporter le cert + clé privée** depuis Keychain Access en format `.p12`
   (clic droit sur le cert "Developer ID Application: NyleRP" → Export → format .p12 →
   mot de passe fort)

## Étape 3 : créer l'App-Specific Password (5 min)

1. https://appleid.apple.com → Sign-In and Security → App-Specific Passwords
2. **Generate password** → label `NyleLauncher CI Notarization`
3. Copier la valeur affichée (format `xxxx-xxxx-xxxx-xxxx`)

## Étape 4 : récupérer le Team ID (1 min)

1. https://developer.apple.com/account/#!/membership/
2. Copier le **Team ID** (format 10 caractères alphanumeric)

## Étape 5 : ajouter les secrets GitHub

Dans `https://github.com/NYL3E/NyleLauncher/settings/secrets/actions` ajouter :

| Nom du secret | Valeur |
|---|---|
| `APPLE_DEVELOPER_ID` | Email du compte Apple Developer |
| `APPLE_APP_PASSWORD` | Le password app-specific généré étape 3 |
| `APPLE_TEAM_ID` | Le Team ID étape 4 |
| `APPLE_CERT_P12_BASE64` | Contenu du .p12 encodé base64 : `base64 -i cert.p12 \| pbcopy` puis paste |
| `APPLE_CERT_P12_PASSWORD` | Mot de passe du .p12 étape 2 |

## Étape 6 : intégrer dans le workflow

Une fois les secrets en place, **envoie-moi un signal** et je modifie
`.github/workflows/bootstrap.yml` pour ajouter les steps macOS suivants
juste avant l'upload du `.dmg` :

```yaml
- name: Import Apple cert
  if: matrix.platform == 'mac'
  run: |
    echo "${{ secrets.APPLE_CERT_P12_BASE64 }}" | base64 -d > /tmp/cert.p12
    security create-keychain -p "" build.keychain
    security default-keychain -s build.keychain
    security unlock-keychain -p "" build.keychain
    security import /tmp/cert.p12 -k build.keychain \
      -P "${{ secrets.APPLE_CERT_P12_PASSWORD }}" -T /usr/bin/codesign
    security set-key-partition-list -S apple-tool:,apple: -s -k "" build.keychain
    rm /tmp/cert.p12

- name: Codesign the .app inside .dmg
  if: matrix.platform == 'mac'
  run: |
    # Mount the dmg, copy out the .app, sign it, repackage
    hdiutil attach build/dist/NyleLauncher-mac-v${{ env.VERSION }}.dmg -mountpoint /tmp/dmg
    cp -R /tmp/dmg/NyleLauncher.app /tmp/NyleLauncher.app
    hdiutil detach /tmp/dmg
    codesign --deep --force --options runtime \
      --sign "Developer ID Application: NyleRP (${{ secrets.APPLE_TEAM_ID }})" \
      /tmp/NyleLauncher.app
    # Re-create the dmg with the signed .app
    rm build/dist/NyleLauncher-mac-v${{ env.VERSION }}.dmg
    hdiutil create -volname NyleLauncher -srcfolder /tmp/NyleLauncher.app \
      -ov -format UDZO build/dist/NyleLauncher-mac-v${{ env.VERSION }}.dmg

- name: Notarize the .dmg
  if: matrix.platform == 'mac'
  run: |
    xcrun notarytool submit build/dist/NyleLauncher-mac-v${{ env.VERSION }}.dmg \
      --apple-id "${{ secrets.APPLE_DEVELOPER_ID }}" \
      --password "${{ secrets.APPLE_APP_PASSWORD }}" \
      --team-id "${{ secrets.APPLE_TEAM_ID }}" \
      --wait
    xcrun stapler staple build/dist/NyleLauncher-mac-v${{ env.VERSION }}.dmg
```

(Ne pas coller ça maintenant — je l'adapterai au layout réel du workflow
existant avec le bon nom de fichier, etc.)

## Étape 7 : pousser un release tag

`git tag v0.3.19 && git push origin v0.3.19`

→ Le CI build, signe, notarise, attach le .dmg au release. Premier user qui
télécharge : **zéro warning Gatekeeper**.

## Vérification que ça marche

Après download du .dmg signé + notarisé :
```bash
spctl -a -t exec -vvv /Volumes/NyleLauncher/NyleLauncher.app
# Doit afficher : "source=Notarized Developer ID"
```

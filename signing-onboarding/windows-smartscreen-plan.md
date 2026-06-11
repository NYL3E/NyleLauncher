# Windows sans avertissement — le plan réaliste (2026)

## La vérité d'abord

- **SignPath** : refusé → terminé.
- **Azure Trusted Signing (~10 $/mois)** : pour les particuliers, c'est **USA et Canada
  uniquement** — pas éligible depuis la France (pour une société, il faut 3+ ans
  d'historique fiscal vérifiable). Donc non, ce n'est pas une option pour nous.
- **Aucune option payante ne supprime l'avertissement instantanément.** SmartScreen =
  signature **+ réputation** (volume de téléchargements sur une identité signée).
  Avec un certificat standard, l'avertissement disparaît après quelques jours à
  quelques semaines de téléchargements, puis ne revient plus (la réputation est
  attachée à ton identité, pas au fichier).

## 🥇 LA méthode garantie : le Microsoft Store (recommandée)

C'est la seule option qui supprime l'avertissement **à 100 %, immédiatement, sans
certificat et sans attente de réputation** : les apps installées depuis le Store ne
passent JAMAIS par SmartScreen, car **Microsoft re-signe lui-même le paquet** avec
son propre certificat après certification. Coût : **~19 $ une seule fois** (compte
développeur individuel), pas d'abonnement.

Pourquoi ça nous va parfaitement :
- Notre architecture bootstrap + payload est idéale : le Store distribue le
  bootstrap (qui ne change presque jamais), et les vraies mises à jour (payload)
  continuent d'arriver automatiquement comme aujourd'hui, sans repasser par le Store.
- Pas de pièce d'identité ni de validation d'entreprise : un compte Microsoft + CB.

### Tuto
1. **Toi (15 min)** : crée un compte développeur sur partner.microsoft.com/dashboard
   → « Compte individuel » (~19 $ une fois). Réserve le nom d'app **NyleLauncher**.
   Récupère dans Partner Center → Product identity : `Identity/Name`,
   `Identity/Publisher` (CN=…) et `PublisherDisplayName`.
2. **Moi (dès que tu me donnes ces 3 valeurs)** : je prépare le paquet **MSIX** en CI
   (app-image jpackage + AppxManifest `runFullTrust`, non signé — c'est le Store qui
   signe) avec le self-updater MSI désactivé dans cette variante (politique Store :
   les mises à jour de l'app passent par le Store ; nos payloads, eux, restent
   automatiques).
3. **Toi (10 min)** : upload du .msix dans Partner Center → soumission. Certification
   Microsoft : 24-72 h. À l'approbation, l'app est en ligne.
4. **Site** : on remplace/complète le bouton Windows par « Installer depuis le
   Microsoft Store » (lien `https://apps.microsoft.com/...` + deep link
   `ms-windows-store://`). Zéro avertissement, zéro clic « Exécuter quand même ».

On garde le MSI en téléchargement direct pour ceux qui préfèrent (avec, à terme,
la signature Certum ci-dessous pour ce canal-là).

## 🥈 Le canal direct : certificat Certum « Open Source Code Signing »

C'est LE certificat des devs indés européens : ~**89-129 €/an**, ouvert aux
particuliers, validation d'identité simple.

### Étape 1 — Acheter (1 jour)
1. Va sur shop.certum.eu → « Open Source Code Signing » → option **« in the Cloud »**
   (SimplySign, pas de carte physique à recevoir).
2. Crée le compte au nom « NYLE » / ton nom légal (c'est ce nom qui s'affichera
   dans la fenêtre « Éditeur vérifié » de Windows).

### Étape 2 — Validation d'identité (2-7 jours)
Certum demande une pièce d'identité (CNI/passeport) + une preuve du projet
open-source : donne simplement https://github.com/NYL3E/NyleLauncher (licence MIT,
repo public — on coche toutes les cases).

### Étape 3 — Signer (à chaque release `v*` du bootstrap, ~2 min)
Depuis 2023, les clés privées de code signing vivent obligatoirement sur HSM/cloud —
pas de fichier .pfx exportable, donc pas de signature 100 % automatique en CI avec
ce certificat. Mais le bootstrap ne sort qu'une fois toutes les quelques semaines,
et le payload (les vraies mises à jour) n'a pas besoin de signature. Procédure :

1. Sur un PC Windows : installe **SimplySign Desktop** (Certum) + le **Windows SDK**
   (pour signtool).
2. Télécharge le MSI fraîchement buildé par la CI depuis la release GitHub.
3. Connecte SimplySign (code de l'app mobile), puis :
   ```bat
   signtool sign /n "NYLE" /fd SHA256 /tr http://time.certum.pl /td SHA256 NyleLauncher-windows-vX.Y.Z.msi
   signtool verify /pa NyleLauncher-windows-vX.Y.Z.msi
   ```
4. Remplace l'asset MSI de la release GitHub par le fichier signé
   (`gh release upload vX.Y.Z NyleLauncher-windows-vX.Y.Z.msi --clobber`).
5. Fais pareil pour le zip portable si tu veux (dézippe, signe `NyleLauncher.exe`,
   rezippe) — optionnel, le MSI est le canal principal.

### Étape 4 — Accélérer la réputation (le jour même de la 1re release signée)
1. Soumets le MSI signé sur https://www.microsoft.com/wdsi/filesubmission
   (texte prêt dans `msdefender-submission.md`).
2. Fais télécharger + installer la release par la commu Discord (chaque install
   « Exécuter quand même » nourrit la réputation).
3. En général : avertissement disparu en **quelques jours à 2-3 semaines**, définitivement.

## Récap

| Option | Prix | Particulier FR | Avertissement supprimé |
|---|---|---|---|
| **Microsoft Store** | **~19 $ une fois** | ✅ | ✅ **garanti, immédiat** (Microsoft signe le paquet) |
| Certum Open Source (canal direct) | ~99 €/an | ✅ | ✅ après quelques jours/semaines de téléchargements |
| Certificat EV classique | 350-500 €/an | ✅ (lourd) | rapide mais plus garanti « instantané » depuis 2024 |
| Azure Trusted Signing | 10 $/mois | ❌ USA/Canada only | — |
| SignPath | gratuit | ~~refusé~~ | — |

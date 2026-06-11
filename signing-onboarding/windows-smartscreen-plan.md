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

## ❌ Microsoft Store : écarté (et c'est important de comprendre pourquoi)

Le Store supprime bien SmartScreen à 100 %… mais notre launcher permet de jouer
en crack à un jeu qui appartient à Microsoft. Soumettre ça à la certification du
Store de Microsoft = refus quasi certain, risque de bannissement du compte
développeur, et surtout ça pointe un projecteur juridique sur le projet. À ne pas
faire. (Même logique pour winget, qui est aussi un dépôt Microsoft modéré.)

## 🥇 LA méthode adaptée à notre situation : certificat Certum « Open Source »

C'est le playbook éprouvé de notre catégorie de logiciel :
- Les **CA de code signing valident TON identité, pas la conformité de l'app à
  l'EULA de Mojang**. Elles ne révoquent que pour du malware. Des launchers
  crack-compatibles tournent signés depuis des années sans problème (TLauncher) ;
  SKLauncher a choisi de rester non signé et assume l'avertissement — ça ne marche
  pour eux que parce que leurs millions de téléchargements blanchissent chaque
  fichier par volume, ce qu'on n'a pas.
- Notre architecture joue pour nous : l'avertissement ne concerne QUE l'installeur,
  téléchargé UNE fois. Les mises à jour (payload) sont invisibles pour SmartScreen.
  Une fois l'identité signée réputée, plus aucun avertissement, définitivement,
  même sur les futures versions.

⚠️ Affichage du nom : un certificat individuel affiche ton **nom légal** comme
« Éditeur vérifié ». Pour afficher « NYLE » à la place, il faut une entité (une
micro-entreprise française suffit, création gratuite en ligne) + un certificat
« company » (~250-350 €/an au lieu de ~99 €). À toi de choisir ce que tu préfères
montrer.

## Le tuto Certum, étape par étape

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

| Option | Prix | Compatible crack-launcher | Avertissement supprimé |
|---|---|---|---|
| **Certum Open Source** | **~99 €/an** | ✅ (la CA ne juge pas l'app) | ✅ après quelques jours/semaines de téléchargements, puis définitif |
| Certificat company OV (affiche « NYLE ») | ~250-350 €/an + micro-entreprise | ✅ | ✅ idem |
| Certificat EV | 350-500 €/an | ✅ (lourd) | plus rapide, plus garanti « instantané » depuis 2024 |
| Microsoft Store / winget | ~19 $ | ❌ refus + risque de ban (crack) | — |
| Azure Trusted Signing | 10 $/mois | ❌ USA/Canada only | — |
| SignPath | gratuit | ~~refusé~~ | — |
| Rester non signé (choix SKLauncher) | 0 € | ✅ | ❌ sauf très gros volume par release |

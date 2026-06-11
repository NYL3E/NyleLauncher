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

## Le plan RÉEL, étape par étape (vérifié sur retours d'expérience 2025-2026)

### Point préalable — ta micro-entreprise ne sert à rien ici (et c'est tant mieux)
- NYLE CORP est une **entreprise individuelle** : le registre SIRENE ne connaît que
  « LENNY TRIDAT » comme dénomination — et ton SIREN est **non-diffusible**, donc
  invérifiable publiquement par une CA. Un certificat « company » afficherait de
  toute façon ton nom légal, pas « NYLE CORP », après avoir levé la
  non-diffusibilité et payé ~3× plus cher. À oublier.
- Le bon produit : **Certum Open Source Code Signing « in the Cloud »** —
  certificat INDIVIDUEL (pas besoin de l'entreprise), éligible car NyleLauncher
  est un projet open source public sous MIT.
- L'éditeur affiché par Windows sera : **« Open Source Developer, Lenny Tridat »**.

### Étape 1 — Achat (aujourd'hui, ~15 min, ~60 € TTC)
shop.certum.eu → « Open Source Code Signing in the Cloud » : **49 € HT/an**
(≈ 59-60 € TTC). PAS la version carte physique (69 € + port + galères de
drivers/lecteur documentées) : la version cloud SimplySign se pilote depuis
l'app, sans matériel.

### Étape 2 — Validation d'identité (2 à 5 jours ouvrés)
Documents demandés (constatés en conditions réelles) :
1. Pièce d'identité recto/verso (CNI ou permis) + **vérification vidéo IDNow**
   (mouvements de tête, 5 min depuis le téléphone).
2. Un justificatif type **facture (eau/élec/téléphone)** à ton nom.
3. **L'URL du projet open source** : https://github.com/NYL3E/NyleLauncher
   (public, licence MIT — coche toutes les cases).
Délai constaté après envoi : **~2 jours ouvrés** (compter 5 max).

### Étape 3 — Activation + première signature (1 h la première fois, 2 min ensuite)
1. Sur un **PC ou une VM Windows** : installe SimplySign Desktop + Windows SDK.
2. Active le certificat depuis ton compte Certum (paire de clés générée côté cloud).
3. Signe le MSI de la release courante :
   ```bat
   signtool sign /n "Open Source Developer, Lenny Tridat" /fd SHA256 /td SHA256 /tr http://time.certum.pl NyleLauncher-windows-vX.Y.Z.msi
   signtool verify /pa NyleLauncher-windows-vX.Y.Z.msi
   ```
4. Ré-upload sur la release GitHub :
   `gh release upload vX.Y.Z NyleLauncher-windows-vX.Y.Z.msi --clobber`
À refaire à chaque release `v*` du bootstrap (rare) — jamais pour les payloads.

### Étape 4 — Réputation SmartScreen : les critères RÉELS
Microsoft ne publie **aucun seuil chiffré** — voici ce qui est observé en pratique :
- La réputation est attachée au **couple identité signataire + fichier** ; une fois
  l'identité réputée, les versions suivantes signées héritent de la confiance.
- Ordres de grandeur constatés chez les éditeurs signés OV : l'avertissement
  disparaît après **quelques dizaines à quelques centaines d'installations**,
  généralement en **quelques jours à 3 semaines**.
- Accélérateurs concrets, le jour de la 1re release signée :
  1. Soumission du MSI signé sur microsoft.com/wdsi/filesubmission
     (texte prêt dans `msdefender-submission.md`).
  2. Vague d'installs via le Discord (3 700+ membres → 50-100 installs en
     quelques jours suffisent largement).
  3. Toujours signer TOUTES les futures releases avec le même certificat.
- Garantie : aucune méthode n'est « instantanée » pour nous (cf. plus haut) ;
  celle-ci est la seule qui converge à coup sûr vers zéro avertissement permanent.

### Budget total réel
- ~60 € TTC/an, c'est tout. Renouvellement annuel au même prix (re-validation allégée).

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

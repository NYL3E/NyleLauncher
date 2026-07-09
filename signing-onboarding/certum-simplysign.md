# Signature de code Certum (SimplySign) — flow NyleLauncher

Statut : **certificat ÉMIS le 2026-07-09** — « Open Source Developer Lenny TRIDAT »,
Certum Code Signing 2021 CA, RSA 4096, valable jusqu'au **2027-07-09**.
Remplace le plan SignPath (abandonné). La clé privée vit dans le **cloud SimplySign**
(non exportable — pas de PFX, c'est voulu et plus sûr).

## Fichiers du repo

- `signing/nylelauncher-codesign-2026.pem` — certificat public (leaf).
- `signing/certum-ccsca2021.pem` — CA intermédiaire Certum.
- `signing/chain.pem` — chaîne complète (leaf + intermédiaire), vérifiée `openssl verify` OK.
- `scripts/sign-release.sh` — signature d'une release publiée (MSI + EXE portable).
- `tools/jsign-6.0.jar` — auto-téléchargé par le script (SHA-256 épinglé).

## Ce qu'il reste à faire UNE FOIS (actions humaines — compte Certum requis)

1. **Installer SimplySign Desktop** (macOS) : certum.eu → SimplySign → téléchargement.
2. **Activer l'app mobile SimplySign** avec le code d'activation reçu par mail Certum
   (c'est elle qui génère les OTP de connexion).
3. Ouvrir SimplySign Desktop → se connecter (identifiant Certum + OTP mobile) →
   l'icône passe au vert : la carte virtuelle est montée, la clé est accessible.
4. `bash scripts/sign-release.sh --list` → vérifier que l'alias du certificat apparaît
   (demande le PIN SimplySign choisi à l'activation).

## À chaque release (2 minutes)

```bash
# 1. tag + CI comme d'habitude (le CI publie MSI/DMG/DEB non signés)
# 2. SimplySign Desktop connecté (OTP), puis :
bash scripts/sign-release.sh v0.3.22
```

Le script télécharge le MSI + le zip portable de la release, signe (SHA-256,
horodatage RFC3161 `time.certum.pl` — la signature reste valide après expiration
du certificat), re-zippe le portable, **remplace les assets en place** (mêmes URLs)
et vérifie le 200 public.

## Pourquoi on n'est pas « détecté comme un virus » du jour au lendemain

- La signature supprime l'« Éditeur inconnu » et fait démarrer la **réputation
  SmartScreen** du certificat : les alertes bleues disparaissent au fil des
  téléchargements (jours → semaines). Un binaire signé + horodaté n'est plus
  re-flaggé à chaque nouvelle version : la réputation suit le certificat.
- Les faux positifs antivirus tiers (Avast, BitDefender…) se traitent en parallèle
  avec les dossiers de soumission déjà prêts dans ce dossier — joindre désormais
  le hash du binaire **signé** dans chaque formulaire.
- Le CI garde son step PFX legacy (inactif sans secrets) — sans objet pour
  SimplySign, on signe post-release via le script.

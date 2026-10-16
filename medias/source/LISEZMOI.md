# Paquet de médias du launcher (refonte 2026-10, lot L3)

Ce dossier est la SOURCE du paquet `medias-<version>` publié en release GitHub par
`scripts/publier-medias.py`. Le chemin d'un fichier ici est son **chemin logique** dans le launcher
(`Medias.fichier("nylerp/music.mp3")`) ; constantes dans `CheminsMedias.java`. Ce LISEZMOI n'est pas
publié.

Rien de ce dossier n'entre dans le jar du payload : chaque joueur télécharge le paquet UNE fois, en
fond, puis le garde en cache (`<racine du launcher>/medias/<sha256>.<ext>`). Sans paquet (hors ligne,
premier démarrage), l'interface s'affiche sans ces médias.

Formats lisibles par JavaFX : PNG, JPEG, GIF, BMP (ni WebP ni AVIF) ; MP3/AAC/MP4 H.264 pour le son
et la vidéo.

## Présent

| Chemin logique | Contenu |
|---|---|
| `nylerp/music.mp3` | musique de l'accueil NyleRP (sortie du jar) |
| `nylerp/ambient.mp3` | ambiance feu de camp (sortie du jar) |

Les vidéos `nylerp/videolauncher1.mp4` et `nylerp/videolauncher2.mp4` restent DANS le jar (1,6 Mo,
vitales) ; les déposer ici les remplacerait à distance.

## Réservé : médias Pokényle à venir de l'agent du site

Fenêtre logique 1180×700, tout en 2x.

| Chemin logique | Format |
|---|---|
| `pokenyle/fond-pokenyle@2x.jpg` | 2360×1400, JPEG q90 sRGB, fond du site SANS oiseaux |
| `pokenyle/fond-pokenyle.jpg` | 1180×700, JPEG q90 |
| `pokenyle/nuages@2x.png` | 2360×1400 RGBA, si une couche de nuages se sépare |
| `pokenyle/atlas/pidgeot-net.png` + `.json` | images 340×480, 24, grille 12×2 (4080×960) |
| `pokenyle/atlas/pidgeot-flou.png` + `.json` | images 192×272, 24, grille 12×2 |
| `pokenyle/atlas/pidgeotto-net.png` + `.json` | images 260×416, 36, grille 12×3 (3120×1248) |
| `pokenyle/atlas/pidgeotto-flou.png` + `.json` | images 168×270, 36, grille 12×3 |
| `pokenyle/atlas/pidgey-net.png` + `.json` | images 200×254, 48, grille 16×3 (3200×762) |
| `pokenyle/atlas/ho_oh-net.png` + `.json` | images 600×440, 24, grille 6×4 (3600×1760) |
| `pokenyle/trajets.json` | keyframes du site (horde, loin, Ho-Oh) + position et taille de chaque oiseau |
| `pokenyle/particules/plume@2x.png` | 64×192 RGBA |
| `pokenyle/particules/eclat@2x.png` | 64×64 RGBA |
| `univers/carte-pokenyle@2x.jpg` | 1040×1240, JPEG q90 |
| `univers/carte-nylerp@2x.jpg` | 1040×1240, JPEG q90 |
| `logos/pokenyle-entete@2x.png`, `logos/nylerp-entete@2x.png` | RGBA, hauteur 88 px |
| `logos/pokenyle-grand@2x.png`, `logos/nylerp-grand@2x.png` | RGBA, hauteur 176 px |

Atlas : PNG RGBA non prémultiplié, 4096 px au plus par côté. JSON d'atlas :
`{"n": 24, "colonnes": 12, "lignes": 2, "w": 340, "h": 480, "duree_s": 0.8, "pivot": [0.5, 0.5]}`.

## À NE PAS mettre ici (vital hors ligne : dans le jar)

- **Icônes de barre système** (`barre/…`) : il faut l'icône à l'ouverture de session, avant tout
  réseau. Le lot 1 les embarque dans le jar ; les chemins `barre/barre-mac-Template.png`,
  `barre/barre-mac-Template@2x.png`, `barre/barre.ico`, `barre/barre-24.png`, `barre/barre-48.png`
  ne servent qu'à les REMPLACER à distance.
- **Polices** (Nunito, Lilita One, Swei Gothic) : sans elles, toute l'interface change de rendu au
  premier démarrage. Le lot 2 les embarque dans le jar (TTF statiques, ~1 Mo).

## Publier

```
python3 -I scripts/publier-medias.py --source medias/source --version 2                # essai à blanc
python3 -I scripts/publier-medias.py --source medias/source --version 2 --appliquer    # publication + vérification
```
puis `Medias.VERSION = "2"` dans le payload suivant. `medias/regles.json` liste les médias
« différés » (téléchargés à la première demande au lieu d'être préchargés).

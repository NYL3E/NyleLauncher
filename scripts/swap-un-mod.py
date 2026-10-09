#!/usr/bin/env python3
"""Remplace UN SEUL mod dans le pack déjà publié, sans toucher au reste.

Publier le pack complet suppose que le dossier `pack/` local soit à jour. Il ne l'est pas
toujours — il peut dater de plusieurs semaines — et republier depuis lui renverrait à tous les
joueurs un modpack périmé. Ce script part donc du manifeste RÉELLEMENT EN LIGNE, n'y change que
l'entrée du mod visé, et téléverse le nouveau jar à côté des autres.

Ce qu'il fait, dans l'ordre :
  1. lit le manifeste publié sur la release `pack-latest` ;
  2. y remplace l'entrée du mod (chemin, taille, empreinte, adresse) ;
  3. change `version` — le launcher ne compare QUE ce champ pour décider d'une mise à jour ;
  4. téléverse le jar, puis le manifeste ;
  5. relit le tout depuis GitHub pour vérifier que ce qui est en ligne est bien ce qu'on voulait.

Le manifeste part EN DERNIER : tant qu'il n'a pas changé, aucun launcher ne bouge. Si le
téléversement du jar échoue, les joueurs continuent sur l'ancienne version au lieu de courir
après un fichier qui n'existe pas.

    python3 swap-un-mod.py <chemin/vers/le.jar> <préfixe> <nouvelle-version-manifeste>
    python3 swap-un-mod.py .../nyleessentials-2.120.5.jar mods/nyleessentials prod-solofix-0821
"""

import hashlib
import json
import os
import pathlib
import sys
import urllib.error
import urllib.request

REPO = "NYL3E/NyleLauncher"
# Canal visé : pack-latest (NYLERP) par défaut ; NYLE_PACK_TAG=pack-pokenyle pour POKÉNYLE.
TAG = os.environ.get("NYLE_PACK_TAG", "pack-latest")
TOKEN_FILE = pathlib.Path.home() / ".config" / "nyle-launcher" / "gh-token"


def jeton():
    return TOKEN_FILE.read_text().strip()


def api(methode, url, tk, corps=None, brut=False, type_contenu=None):
    req = urllib.request.Request(url, data=corps, method=methode)
    req.add_header("Authorization", f"token {tk}")
    req.add_header("Accept", "application/octet-stream" if brut else "application/vnd.github+json")
    if type_contenu:
        req.add_header("Content-Type", type_contenu)
    with urllib.request.urlopen(req) as r:
        donnees = r.read()
    if brut:
        return donnees
    return json.loads(donnees) if donnees else {}


def main():
    if len(sys.argv) != 4:
        print(__doc__)
        return 2
    jar = pathlib.Path(sys.argv[1])
    prefixe = sys.argv[2]
    version = sys.argv[3]
    if not jar.is_file():
        print(f"jar introuvable : {jar}")
        return 1

    tk = jeton()
    rel = api("GET", f"https://api.github.com/repos/{REPO}/releases/tags/{TAG}", tk)
    rid = rel["id"]
    assets = {a["name"]: a for a in rel["assets"]}

    # ── 1. Le manifeste en ligne, seule source de vérité ─────────────────────────────────────
    if "manifest.json" not in assets:
        print("pas de manifest.json sur la release")
        return 1
    manifeste = json.loads(api("GET", assets["manifest.json"]["url"], tk, brut=True))
    fichiers = manifeste["files"]

    vises = [x for x in fichiers if x["path"].startswith(prefixe)]
    if len(vises) != 1:
        print(f"attendu 1 entrée pour « {prefixe} », trouvé {len(vises)} :")
        for x in vises:
            print("   ", x["path"])
        return 1
    ancienne = vises[0]
    print(f"ancienne entrée : {ancienne['path']}")

    # ── 2. La nouvelle entrée ────────────────────────────────────────────────────────────────
    octets = jar.read_bytes()
    empreinte = hashlib.sha256(octets).hexdigest()
    nom_asset = "mods__" + jar.name
    nouvelle = {
        "path": f"mods/{jar.name}",
        # Le manifeste publié écrit les tailles en TEXTE : on garde son format, sans quoi le
        # launcher pourrait ne pas relire ce qu'il attend.
        "size": str(len(octets)) if isinstance(ancienne.get("size"), str) else len(octets),
        "sha256": empreinte,
        "url": f"https://github.com/{REPO}/releases/download/{TAG}/{nom_asset}",
    }
    fichiers[fichiers.index(ancienne)] = nouvelle
    ancienne_version = manifeste.get("version")
    manifeste["version"] = version
    print(f"nouvelle entrée : {nouvelle['path']} · {len(octets)} o")
    print(f"version         : {ancienne_version} → {version}")

    # ── 3. Le jar d'abord ────────────────────────────────────────────────────────────────────
    if nom_asset in assets:
        print(f"asset déjà présent, on le remplace : {nom_asset}")
        api("DELETE", f"https://api.github.com/repos/{REPO}/releases/assets/{assets[nom_asset]['id']}", tk)
    print(f"téléversement de {nom_asset} ({len(octets)/1048576:.1f} Mo)…")
    api("POST", f"https://uploads.github.com/repos/{REPO}/releases/{rid}/assets?name={nom_asset}",
        tk, corps=octets, type_contenu="application/java-archive")

    # ── 4. Le manifeste ENSUITE : c'est lui qui déclenche les mises à jour ───────────────────
    brut = json.dumps(manifeste, ensure_ascii=False, indent=1).encode("utf-8")
    api("DELETE", f"https://api.github.com/repos/{REPO}/releases/assets/{assets['manifest.json']['id']}", tk)
    print("téléversement du manifeste…")
    api("POST", f"https://uploads.github.com/repos/{REPO}/releases/{rid}/assets?name=manifest.json",
        tk, corps=brut, type_contenu="application/json")

    # ── 5. Relecture ─────────────────────────────────────────────────────────────────────────
    rel = api("GET", f"https://api.github.com/repos/{REPO}/releases/tags/{TAG}", tk)
    assets = {a["name"]: a for a in rel["assets"]}
    verif = json.loads(api("GET", assets["manifest.json"]["url"], tk, brut=True))
    ok_version = verif.get("version") == version
    entree = [x for x in verif["files"] if x["path"] == nouvelle["path"]]
    ok_jar = nom_asset in assets and assets[nom_asset]["size"] == len(octets)
    print()
    print(f"  version en ligne : {verif.get('version')}  {'OK' if ok_version else 'ÉCHEC'}")
    print(f"  entrée du mod    : {'OK' if entree else 'ABSENTE'}")
    print(f"  jar en ligne     : {'OK' if ok_jar else 'ÉCHEC'}")
    print(f"  empreinte        : {empreinte[:16]}…")
    return 0 if (ok_version and entree and ok_jar) else 1


if __name__ == "__main__":
    raise SystemExit(main())

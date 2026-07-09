#!/usr/bin/env python3
"""Vérifie que CHAQUE URL du manifest publié répond HTTP 200 — sans token,
exactement comme le launcher d'un joueur.

Né de l'incident du 2026-07-09 : un run upload-pack.py interrompu (500/401)
avait laissé la release pack-emotes avec 751 assets alors que le manifest en
référençait 926 → 175 fichiers en 404 → « Mise à jour échouée » pour tout
nouveau joueur / réinstallation, puis « Network Protocol Error » en jeu (mods
périmés via « Lancer quand même »). La vérification manuelle ne couvrait que
les jars de mods, pas les subtree releases (pack-emotes).

À lancer APRÈS tout upload-pack.py, réparation manuelle d'assets, ou doute :

    python3 scripts/verify-pack.py            # sweep complet
    python3 scripts/verify-pack.py --sha      # + télécharge tout et vérifie les SHA-256 (long)

Sort avec le code 1 si au moins une URL est cassée.
"""
import argparse
import concurrent.futures
import hashlib
import json
import sys
import time
import urllib.error
import urllib.request

MANIFEST_URL = ("https://github.com/NYL3E/NyleLauncher/releases/download/"
                "pack-latest/manifest.json?t=%d" % time.time())


def fetch(url, full=False):
    """GET sans authentification (= vue joueur). 3 tentatives sur erreur réseau."""
    for attempt in range(3):
        try:
            req = urllib.request.Request(url, headers={"User-Agent": "nyle-verify-pack"})
            with urllib.request.urlopen(req, timeout=60) as r:
                return r.status, (r.read() if full else r.read(32))
        except urllib.error.HTTPError as e:
            return e.code, b""
        except Exception:
            if attempt == 2:
                return "ERR", b""
            time.sleep(2)


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--sha", action="store_true",
                    help="Télécharger chaque fichier en entier et vérifier son SHA-256")
    args = ap.parse_args()

    st, _ = fetch(MANIFEST_URL)
    if st != 200:
        print(f"FATAL: manifest.json lui-même en HTTP {st}", file=sys.stderr)
        sys.exit(1)
    with urllib.request.urlopen(
            urllib.request.Request(MANIFEST_URL, headers={"User-Agent": "nyle-verify-pack"}),
            timeout=60) as r:
        manifest = json.loads(r.read().decode())
    files = manifest["files"]
    print(f"manifest version={manifest.get('version')} — {len(files)} fichiers")

    def check(entry):
        st, data = fetch(entry["url"], full=args.sha)
        if st != 200:
            return (entry["path"], f"HTTP {st}")
        if args.sha:
            sha = hashlib.sha256(data).hexdigest()
            if entry.get("sha256") and sha.lower() != entry["sha256"].lower():
                return (entry["path"], "SHA-256 KO")
        return None

    bad = []
    with concurrent.futures.ThreadPoolExecutor(max_workers=20) as ex:
        for i, res in enumerate(ex.map(check, files), 1):
            if res:
                bad.append(res)
            if i % 200 == 0 or i == len(files):
                print(f"  {i}/{len(files)} — cassés: {len(bad)}")

    if bad:
        print(f"\n{len(bad)} URL(s) CASSÉE(S) :", file=sys.stderr)
        for path, why in bad:
            print(f"  {why}  {path}", file=sys.stderr)
        sys.exit(1)
    print("\nOK — toutes les URLs du manifest répondent 200"
          + (" et tous les SHA matchent" if args.sha else ""))


if __name__ == "__main__":
    main()

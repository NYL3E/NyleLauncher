#!/usr/bin/env python3
"""publier-medias.py — publie le paquet de médias du launcher (refonte 2026-10, lot L3).

Le payload ne transporte plus la musique ni les grandes images : il les télécharge UNE fois depuis
la release GitHub `medias-<version>` et les garde en cache (voir Medias.java / MediasCache.java).
Ce script construit le manifeste `medias.json` depuis un dossier source et publie la release.

    # 1. Essai à blanc (défaut) : construit et affiche le manifeste, n'envoie RIEN
    python3 -I scripts/publier-medias.py --source medias/source --version 1

    # 2. Essai réel sur une release BROUILLON (invisible des joueurs), puis suppression
    python3 -I scripts/publier-medias.py --source medias/source --version essai-l3 --brouillon --appliquer
    python3 -I scripts/publier-medias.py --version essai-l3 --supprimer-brouillon

    # 3. Publication : release `medias-<version>` publiée (préversion), puis vérification de chaque URL
    python3 -I scripts/publier-medias.py --source medias/source --version 1 --appliquer

Puis monter `Medias.VERSION` à la même valeur dans le payload suivant : un payload épingle la
version de médias qu'il attend.

Règles :
- chemin logique = chemin relatif dans le dossier source (`nylerp/music.mp3`) ;
- nom d'asset = `<12 premiers caractères du sha>-<nom assaini>` : adressé par contenu, et assaini
  comme GitHub le ferait (tout caractère hors [A-Za-z0-9._-] devient `.`, cf. mémoire
  reference_github_release_sanitization) — sinon l'URL du manifeste ne correspondrait pas ;
- `medias/regles.json` (facultatif) : {"differe": ["univers/*", ...]} = téléchargés à la première
  demande seulement (`prechargement: false`) ; le reste est préchargé en fond ;
- ignorés : fichiers cachés, LISEZMOI*/README*, fichiers vides (GitHub refuse un asset de 0 octet) ;
- la release est retrouvée en énumérant /releases (un BROUILLON n'apparaît pas dans
  /releases/tags/<tag> — mémoire reference_github_release_draft_orphan) ;
- le jeton (~/.config/nyle-launcher/gh-token) n'est jamais affiché.
"""
import argparse
import fnmatch
import hashlib
import json
import pathlib
import re
import sys
import time
import urllib.error
import urllib.request

DEPOT_DEFAUT = "NYL3E/NyleLauncher"
JETON = pathlib.Path.home() / ".config" / "nyle-launcher" / "gh-token"
NOM_MANIFESTE = "medias.json"
FORMAT = 1


# ── Manifeste ────────────────────────────────────────────────────────────────────────────────

def sha256(p: pathlib.Path) -> str:
    h = hashlib.sha256()
    with p.open("rb") as f:
        for bloc in iter(lambda: f.read(1 << 20), b""):
            h.update(bloc)
    return h.hexdigest()


def nom_asset(sha: str, chemin: str) -> str:
    base = chemin.rsplit("/", 1)[-1]
    sur = re.sub(r"[^A-Za-z0-9._-]+", ".", base)
    sur = re.sub(r"\.{2,}", ".", sur).strip(".")
    return f"{sha[:12]}-{sur}"


def construire(source: pathlib.Path, version: str, regles: dict) -> tuple[dict, dict]:
    """Renvoie (manifeste, {nom_asset: chemin_disque})."""
    differe = regles.get("differe", [])
    fichiers, disque = [], {}
    for p in sorted(source.rglob("*")):
        if not p.is_file():
            continue
        rel = p.relative_to(source).as_posix()
        nom = p.name
        if any(part.startswith(".") for part in rel.split("/")) or nom.upper().startswith(("LISEZMOI", "README")):
            continue
        taille = p.stat().st_size
        if taille == 0:
            print(f"  ignoré (vide) : {rel}", file=sys.stderr)
            continue
        if not re.fullmatch(r"[A-Za-z0-9._@/-]{1,200}", rel) or ".." in rel:
            sys.exit(f"chemin logique refusé par le launcher : {rel!r} (caractères permis : A-Z a-z 0-9 . _ @ / -)")
        sha = sha256(p)
        asset = nom_asset(sha, rel)
        e = {"chemin": rel, "sha256": sha, "taille": taille, "asset": asset}
        if any(fnmatch.fnmatch(rel, motif) for motif in differe):
            e["prechargement"] = False
        fichiers.append(e)
        disque[asset] = p
    return {"format": FORMAT, "version": version, "fichiers": fichiers}, disque


# ── API GitHub ───────────────────────────────────────────────────────────────────────────────

class SansRedirection(urllib.request.HTTPRedirectHandler):
    def redirect_request(self, *a, **k):
        return None


def jeton() -> str:
    if not JETON.is_file():
        sys.exit(f"jeton GitHub absent : {JETON}")
    return JETON.read_text().strip()


def api(methode, url, tk, donnees=None, type_contenu="application/json", accept="application/vnd.github+json"):
    req = urllib.request.Request(url, data=donnees, method=methode)
    req.add_header("Authorization", f"token {tk}")
    req.add_header("Accept", accept)
    req.add_header("User-Agent", "nyle-publier-medias")
    if donnees is not None:
        req.add_header("Content-Type", type_contenu)
    with urllib.request.urlopen(req, timeout=300) as r:
        corps = r.read()
        return json.loads(corps) if corps else {}


def trouver_release(tk, depot, tag):
    page = 1
    while True:
        lot = api("GET", f"https://api.github.com/repos/{depot}/releases?per_page=100&page={page}", tk)
        for r in lot:
            if r.get("tag_name") == tag:
                return r
        if len(lot) < 100:
            return None
        page += 1


def televerser(tk, release, nom, octets):
    url = release["upload_url"].split("{", 1)[0] + "?name=" + urllib.request.quote(nom)
    return api("POST", url, tk, octets, "application/octet-stream")


def telecharger_asset(tk, depot, release, asset) -> bytes:
    """Contenu d'un asset. Brouillon : par l'API (pas d'URL publique), redirection suivie SANS le
    jeton (le stockage signé refuse un second mécanisme d'authentification)."""
    if release.get("draft"):
        req = urllib.request.Request(f"https://api.github.com/repos/{depot}/releases/assets/{asset['id']}")
        req.add_header("Authorization", f"token {tk}")
        req.add_header("Accept", "application/octet-stream")
        req.add_header("User-Agent", "nyle-publier-medias")
        ouvreur = urllib.request.build_opener(SansRedirection)
        try:
            with ouvreur.open(req, timeout=120) as r:
                return r.read()
        except urllib.error.HTTPError as e:
            if e.code not in (301, 302, 303, 307, 308):
                raise
            lieu = e.headers["Location"]
        with urllib.request.urlopen(urllib.request.Request(lieu, headers={"User-Agent": "nyle-publier-medias"}), timeout=300) as r:
            return r.read()
    url = f"https://github.com/{depot}/releases/download/{release['tag_name']}/{asset['name']}?t={int(time.time() * 1000)}"
    with urllib.request.urlopen(urllib.request.Request(url, headers={"User-Agent": "nyle-publier-medias"}), timeout=300) as r:
        return r.read()


# ── Commandes ────────────────────────────────────────────────────────────────────────────────

def publier(args, manifeste, disque):
    tk = jeton()
    tag = f"medias-{args.version}"
    rel = trouver_release(tk, args.depot, tag)
    if rel and rel.get("draft") is False and args.brouillon:
        sys.exit(f"{tag} existe déjà et est PUBLIÉE : --brouillon refusé (on ne repasse pas une release publique en brouillon)")
    if rel is None:
        rel = api("POST", f"https://api.github.com/repos/{args.depot}/releases", tk, json.dumps({
            "tag_name": tag,
            "name": f"Médias du launcher — {args.version}",
            "body": "Paquet de médias du launcher (musique, images, ciel Pokényle). Généré par scripts/publier-medias.py ; "
                    "le payload qui épingle cette version les télécharge une fois puis les garde en cache. "
                    "Voir medias.json pour la liste.",
            "draft": bool(args.brouillon),
            "prerelease": True,   # /releases/latest doit rester la dernière version du launcher
        }).encode())
        print(f"release créée : {tag} (id {rel['id']}, brouillon={rel['draft']})")
    else:
        print(f"release existante : {tag} (id {rel['id']}, brouillon={rel['draft']})")

    existants = {a["name"]: a for a in rel.get("assets", [])}
    voulus = set(disque) | {NOM_MANIFESTE}
    envoyes = 0
    for nom, chemin in disque.items():
        a = existants.get(nom)
        if a and a["size"] == chemin.stat().st_size:
            continue                                        # adressé par contenu : même nom + même taille = même fichier
        if a:
            api("DELETE", f"https://api.github.com/repos/{args.depot}/releases/assets/{a['id']}", tk)
        televerser(tk, rel, nom, chemin.read_bytes())
        envoyes += 1
        print(f"  envoyé {nom} ({chemin.stat().st_size} o)")
    # Le manifeste en DERNIER : tant qu'il n'est pas là (ou qu'il est l'ancien), aucun launcher ne
    # demande un asset pas encore envoyé.
    if NOM_MANIFESTE in existants:
        api("DELETE", f"https://api.github.com/repos/{args.depot}/releases/assets/{existants[NOM_MANIFESTE]['id']}", tk)
    televerser(tk, rel, NOM_MANIFESTE, json.dumps(manifeste, ensure_ascii=False, indent=1).encode("utf-8"))
    for nom, a in existants.items():
        if nom not in voulus:
            api("DELETE", f"https://api.github.com/repos/{args.depot}/releases/assets/{a['id']}", tk)
            print(f"  retiré (n'est plus dans le manifeste) : {nom}")
    if rel.get("draft") and not args.brouillon:
        rel = api("PATCH", f"https://api.github.com/repos/{args.depot}/releases/{rel['id']}", tk, json.dumps({"draft": False}).encode())
        print(f"brouillon publié : {tag}")
    print(f"{envoyes} média(s) envoyé(s), manifeste publié")
    return verifier(tk, args.depot, tag, manifeste)


def verifier(tk, depot, tag, manifeste) -> int:
    rel = trouver_release(tk, depot, tag)
    if rel is None:
        print(f"ÉCHEC : {tag} introuvable")
        return 1
    assets = {a["name"]: a for a in rel.get("assets", [])}
    ko = 0
    distant = assets.get(NOM_MANIFESTE)
    if distant is None:
        print("ÉCHEC : medias.json absent de la release"); return 1
    lu = json.loads(telecharger_asset(tk, depot, rel, distant))
    if lu != manifeste:
        print("ÉCHEC : medias.json publié différent du manifeste construit"); ko += 1
    for e in manifeste["fichiers"]:
        a = assets.get(e["asset"])
        if a is None:
            print(f"  ÉCHEC absent : {e['asset']}"); ko += 1; continue
        b = telecharger_asset(tk, depot, rel, a)
        if len(b) != e["taille"] or hashlib.sha256(b).hexdigest() != e["sha256"]:
            print(f"  ÉCHEC contenu : {e['asset']}"); ko += 1
        else:
            print(f"  ok {e['chemin']}")
    mode = "API (brouillon)" if rel.get("draft") else "URL publique"
    print(f"vérification par {mode} : {len(manifeste['fichiers']) - ko}/{len(manifeste['fichiers'])} médias conformes")
    return 1 if ko else 0


def supprimer_brouillon(args) -> int:
    tk = jeton()
    tag = f"medias-{args.version}"
    rel = trouver_release(tk, args.depot, tag)
    if rel is None:
        print(f"{tag} : aucune release"); return 0
    if not rel.get("draft"):
        sys.exit(f"{tag} est PUBLIÉE : suppression refusée (seuls les brouillons d'essai se suppriment ici)")
    api("DELETE", f"https://api.github.com/repos/{args.depot}/releases/{rel['id']}", tk)
    # Un brouillon n'a pas de tag ; on vérifie quand même qu'aucun ref orphelin ne traîne.
    try:
        api("GET", f"https://api.github.com/repos/{args.depot}/git/refs/tags/{tag}", tk)
        print(f"ATTENTION : un tag {tag} existe encore sur le dépôt (non supprimé automatiquement)")
    except urllib.error.HTTPError as e:
        if e.code != 404:
            raise
    print(f"brouillon {tag} supprimé (id {rel['id']})")
    return 0


def main():
    ap = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    ap.add_argument("--source", type=pathlib.Path, help="dossier des médias (chemins logiques = chemins relatifs)")
    ap.add_argument("--version", required=True, help="version du paquet → release medias-<version>")
    ap.add_argument("--depot", default=DEPOT_DEFAUT)
    ap.add_argument("--regles", type=pathlib.Path, help="règles (défaut : <source>/../regles.json si présent)")
    ap.add_argument("--sortie", type=pathlib.Path, default=pathlib.Path("build/medias/medias.json"))
    ap.add_argument("--brouillon", action="store_true", help="créer/mettre à jour une release BROUILLON (essai)")
    ap.add_argument("--appliquer", action="store_true", help="publier pour de vrai (sinon : essai à blanc)")
    ap.add_argument("--supprimer-brouillon", action="store_true", help="supprimer la release brouillon medias-<version>")
    args = ap.parse_args()

    if not re.fullmatch(r"[A-Za-z0-9._-]{1,40}", args.version):
        sys.exit("version invalide (A-Z a-z 0-9 . _ -)")
    if args.supprimer_brouillon:
        sys.exit(supprimer_brouillon(args))
    if not args.source or not args.source.is_dir():
        sys.exit("--source : dossier introuvable")

    regles_f = args.regles or args.source.parent / "regles.json"
    regles = json.loads(regles_f.read_text()) if regles_f.is_file() else {}
    manifeste, disque = construire(args.source, args.version, regles)
    total = sum(e["taille"] for e in manifeste["fichiers"])
    args.sortie.parent.mkdir(parents=True, exist_ok=True)
    args.sortie.write_text(json.dumps(manifeste, ensure_ascii=False, indent=1), encoding="utf-8")
    print(f"manifeste medias-{args.version} : {len(manifeste['fichiers'])} médias, {total / 1e6:.1f} Mo → {args.sortie}")
    for e in manifeste["fichiers"]:
        drapeau = " (différé)" if e.get("prechargement") is False else ""
        print(f"  {e['chemin']:<42} {e['taille']:>10} o  {e['asset']}{drapeau}")
    if not args.appliquer:
        print("(essai à blanc : rien envoyé — ajouter --appliquer, et --brouillon pour un essai invisible des joueurs)")
        return 0
    return publier(args, manifeste, disque)


if __name__ == "__main__":
    sys.exit(main())

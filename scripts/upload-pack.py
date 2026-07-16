#!/usr/bin/env python3
"""
Upload the current pack/ + manifest.json directly to the GitHub release
`pack-latest`. Replaces the CI workflow for when we need to publish a new
modpack revision without pushing (e.g., when pack/ is gitignored because
the binary jars shouldn't live in git history).

Reads the GitHub PAT from ~/.config/nyle-launcher/gh-token.

Run after sync-instance.py to publish what the developer is playing with:

    python3 scripts/sync-instance.py
    python3 scripts/upload-pack.py

Concurrent uploads keep the total wall time reasonable even with ~1000
assets. GitHub asset filenames are the `__`-joined relative path with
special chars (notably `+`) replaced by `.` to match GH's own sanitization.
"""
import argparse
import concurrent.futures as cf
import json
import os
import pathlib
import re
import shutil
import sys
import time
import urllib.request

ROOT  = pathlib.Path(__file__).resolve().parents[1]
PACK  = ROOT / "pack"
DIST  = ROOT / "pack-dist"
MANIFEST = ROOT / "manifest.json"
REPO  = "NYL3E/NyleLauncher"
TAG   = "pack-latest"
TOKEN_FILE = pathlib.Path.home() / ".config" / "nyle-launcher" / "gh-token"

# GitHub hard-caps a release at 1000 assets (HTTP 422 "file_count limited to
# 1000 assets per release"). Large self-contained subtrees therefore ship on
# their OWN release tag; MUST stay in sync with generate-manifest.py, which
# writes the per-file URLs the launcher follows.
SUBTREE_RELEASES = {
    "emotes/": "pack-emotes",
}


def release_tag_for(rel_str: str) -> str:
    for prefix, tag in SUBTREE_RELEASES.items():
        if rel_str.startswith(prefix):
            return tag
    return TAG


def token():
    if not TOKEN_FILE.is_file():
        print(f"ERROR: no token at {TOKEN_FILE}", file=sys.stderr)
        sys.exit(1)
    return TOKEN_FILE.read_text().strip()


def api(method, url, tk, data=None, content_type="application/json"):
    req = urllib.request.Request(url, data=data, method=method)
    req.add_header("Authorization", f"token {tk}")
    req.add_header("Accept", "application/vnd.github+json")
    if data is not None:
        req.add_header("Content-Type", content_type)
        req.add_header("Content-Length", str(len(data)))
    with urllib.request.urlopen(req) as r:
        body = r.read()
        return json.loads(body) if body else {}


def flatten_and_sanitize(pack_root: pathlib.Path, out: pathlib.Path):
    """Stage pack files into one flat dir PER release tag (out/<tag>/…).

    Returns {tag: staged_count}."""
    if out.exists():
        shutil.rmtree(out)
    out.mkdir(parents=True)
    # Collapse runs of special chars into a single dot and strip leading/
    # trailing dots — GH rejects consecutive/trailing dots with HTTP 422.
    sanitize = re.compile(r"[^A-Za-z0-9._\-]+")
    collapse = re.compile(r"\.{2,}")
    counts = {}
    for p in pack_root.rglob("*"):
        if not p.is_file():
            continue
        name = p.name.lower()
        if name.startswith(".") or name == "readme.md":
            continue
        # GitHub rejects 0-byte release assets with HTTP 422 (size >= 1)
        if p.stat().st_size == 0:
            continue
        rel = p.relative_to(pack_root)
        rel_str = str(rel).replace("\\", "/")
        tag = release_tag_for(rel_str)
        flat = rel_str.replace("/", "__")
        safe = sanitize.sub(".", flat)
        safe = collapse.sub(".", safe).strip(".")
        tag_dir = out / tag
        tag_dir.mkdir(exist_ok=True)
        shutil.copy2(p, tag_dir / safe)
        counts[tag] = counts.get(tag, 0) + 1
    return counts


def delete_release_if_exists(tk, tag):
    # Enumerate ALL releases — the by-tag endpoint (/releases/tags/<tag>) does
    # NOT return DRAFTS, so an orphaned draft (GitHub auto-converts a release to
    # a draft when its tag ref is deleted) would be invisible here, never get
    # cleaned up, and keep the public download URL 404ing while the real assets
    # sit in an unpublished draft. List every release and delete each one on tag.
    try:
        releases = api("GET", f"https://api.github.com/repos/{REPO}/releases?per_page=100", tk)
        for r in releases:
            if r.get("tag_name") == tag:
                api("DELETE", f"https://api.github.com/repos/{REPO}/releases/{r['id']}", tk)
                print(f"  Deleted release id={r['id']} (draft={r.get('draft')})")
    except urllib.error.HTTPError as e:
        if e.code != 404:
            raise
    # Also try to delete the tag ref so the next create can target a fresh sha
    try:
        api("DELETE", f"https://api.github.com/repos/{REPO}/git/refs/tags/{tag}", tk)
        print(f"  Deleted tag ref {tag}")
    except urllib.error.HTTPError as e:
        if e.code != 422 and e.code != 404:  # 422 = not found
            raise


def create_release(tk, tag):
    body = json.dumps({
        "tag_name":    tag,
        "name":        "Modpack — latest" if tag == TAG else f"Modpack — {tag}",
        "body":        f"Generated {time.strftime('%Y-%m-%dT%H:%M:%SZ', time.gmtime())} — automatic release, see manifest.json for file list.",
        "draft":       False,
        # Mark as prerelease so GH's /releases/latest returns actual launcher
        # builds (tagged vX.Y.Z), not this modpack asset bundle.
        "prerelease":  True,
    }).encode()
    r = api("POST", f"https://api.github.com/repos/{REPO}/releases", tk, body)
    print(f"  Created release id={r['id']}")
    return r["id"]


def upload_asset(tk, release_id: int, file_path: pathlib.Path):
    data = file_path.read_bytes()
    name = file_path.name
    url = f"https://uploads.github.com/repos/{REPO}/releases/{release_id}/assets?name={name}"
    req = urllib.request.Request(url, data=data, method="POST")
    req.add_header("Authorization", f"token {tk}")
    req.add_header("Content-Type", "application/octet-stream")
    req.add_header("Content-Length", str(len(data)))
    with urllib.request.urlopen(req) as r:
        return name, r.status


def publish_release(tk, tag, dist_dir: pathlib.Path):
    """Delete+recreate the release for `tag` and upload every file in dist_dir,
    with verify+retry. Exits the process on persistent failure."""
    assets = sorted(dist_dir.iterdir())
    total = len(assets)
    if total > 1000:
        print(f"ERROR: {tag}: {total} assets > limite GitHub de 1000 par release. "
              f"Découper via SUBTREE_RELEASES.", file=sys.stderr)
        sys.exit(1)

    print(f"── Release {tag} — {total} assets")
    print("  Deleting existing release (if any)")
    delete_release_if_exists(tk, tag)
    # Small delay — GH sometimes needs a beat to release the tag
    time.sleep(3)

    print("  Creating new release")
    rid = create_release(tk, tag)

    print(f"  Uploading {total} assets (parallel)")
    done = [0]
    errors = []

    def task(f):
        try:
            return upload_asset(tk, rid, f)
        except Exception as e:
            errors.append((f.name, str(e)))
            return None

    with cf.ThreadPoolExecutor(max_workers=8) as ex:
        for fut in cf.as_completed([ex.submit(task, f) for f in assets]):
            done[0] += 1
            if done[0] % 25 == 0 or done[0] == total:
                print(f"   {done[0]}/{total}")

    # VERIFY + RETRY — GitHub renvoie parfois un succès apparent mais ne persiste
    # PAS l'asset (échec silencieux sous charge) => upload_asset ne lève rien et le
    # fichier manque dans la release. On liste les assets RÉELLEMENT présents
    # (pagination, >100 assets) et on re-tente les manquants jusqu'à ce que tout soit là.
    #
    # NOTE: GitHub may assign a different release ID in the tag lookup than what
    # create_release() returned (race/reuse). Always re-resolve by tag before
    # verifying so we query the correct release.
    def resolve_rid():
        r = api("GET", f"https://api.github.com/repos/{REPO}/releases/tags/{tag}", tk)
        return r["id"]

    def present_names(verify_rid):
        names, page = set(), 1
        while True:
            batch = api("GET", f"https://api.github.com/repos/{REPO}/releases/{verify_rid}/assets?per_page=100&page={page}", tk)
            if not batch:
                break
            names |= {a["name"] for a in batch}
            if len(batch) < 100:
                break
            page += 1
        return names

    print("  Verifying all assets landed (+ retry missing)")
    # Wait briefly for GitHub to settle after parallel uploads
    time.sleep(2)
    verify_rid = resolve_rid()
    if verify_rid != rid:
        print(f"   NOTE: tag resolves to release id={verify_rid} (created as {rid}) — using tag-resolved id")
    for attempt in range(5):
        have = present_names(verify_rid)
        missing = [f for f in assets if f.name not in have]
        if not missing:
            break
        print(f"   {len(missing)} manquants -> re-upload (tentative {attempt + 1}/5)")
        for f in missing:
            try:
                upload_asset(tk, verify_rid, f)
            except Exception as e:
                errors.append((f.name, str(e)))
        time.sleep(3)
        verify_rid = resolve_rid()

    # NOTE: resolve present_names ONCE — calling it inside the listcomp condition
    # re-listed every page of assets PER FILE (~10k API calls for 926 assets) and
    # burned the 5000/h GitHub core rate limit → HTTP 403 mid-publish (2026-07-03).
    have = present_names(verify_rid)
    missing = [f.name for f in assets if f.name not in have]
    if missing:
        print(f"\n{tag}: {len(missing)} assets TOUJOURS absents après 5 tentatives:", file=sys.stderr)
        for n in missing[:25]:
            print(f"  {n}", file=sys.stderr)
        sys.exit(1)

    print(f"  OK — {total} assets vérifiés présents sur {tag}")
    return total


def _require_smoke_ok():
    """GARDE SMOKE-TEST (owner 2026-07-11 : « le launcher doit TOUJOURS être opérationnel ») —
    refuse de pousser un pack qui n'a pas passé scripts/smoke-test-pack.sh récemment (<30 min).
    L'incident du 2026-07-11 03:27 (collision de mixins → jeu mort pour TOUS les joueurs via le
    pack) aurait été bloqué ici. Bypass d'urgence : NYLE_SKIP_SMOKE=1 (à justifier)."""
    if os.environ.get("NYLE_SKIP_SMOKE") == "1":
        print("⚠ smoke-test BYPASSÉ (NYLE_SKIP_SMOKE=1)")
        return
    import time as _t
    marker = pathlib.Path(__file__).resolve().parent.parent / ".smoke-ok"
    try:
        age = _t.time() - float(marker.read_text().strip())
    except Exception:
        sys.exit("✖ REFUS: aucun smoke-test récent. Lance d'abord: bash scripts/smoke-test-pack.sh")
    if age > 1800:
        sys.exit(f"✖ REFUS: smoke-test trop vieux ({age/60:.0f} min). Relance: bash scripts/smoke-test-pack.sh")
    print(f"✔ smoke-test OK (il y a {age/60:.0f} min)")


def main():
    _require_smoke_ok()
    ap = argparse.ArgumentParser()
    ap.add_argument("--only", action="append", default=None, metavar="TAG",
                    help="Ne publier QUE ce(s) tag(s) (ex: --only pack-latest). "
                         "Utile pour reprendre après un rate-limit sans re-publier "
                         "une release subtree déjà complète.")
    args = ap.parse_args()

    if not MANIFEST.is_file():
        print("ERROR: manifest.json missing. Run sync-instance.py first.",
              file=sys.stderr)
        sys.exit(1)

    tk = token()

    print("1) Flattening pack/ → pack-dist/<tag>/")
    counts = flatten_and_sanitize(PACK, DIST)
    main_dir = DIST / TAG
    main_dir.mkdir(exist_ok=True)
    shutil.copy2(MANIFEST, main_dir / "manifest.json")
    counts[TAG] = counts.get(TAG, 0) + 1
    for tag, n in sorted(counts.items()):
        print(f"   {tag}: {n} assets staged")

    todo = {t: c for t, c in counts.items() if args.only is None or t in args.only}
    if not todo:
        print(f"ERROR: --only {args.only} ne correspond à aucun tag staged "
              f"({sorted(counts)})", file=sys.stderr)
        sys.exit(1)

    # Publish the SUBTREE releases first: their URLs only go live for players
    # once the new manifest.json lands on the main release. Publishing the
    # main release last keeps the visible outage window to its own recreate.
    grand_total = 0
    for tag in sorted(todo, key=lambda t: (t == TAG, t)):
        grand_total += publish_release(tk, tag, DIST / tag)

    print(f"\nDone — {grand_total} assets vérifiés présents sur {len(todo)} release(s). "
          f"https://github.com/{REPO}/releases/tag/{TAG}")


if __name__ == "__main__":
    main()

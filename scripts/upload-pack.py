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
    if out.exists():
        shutil.rmtree(out)
    out.mkdir(parents=True)
    # Collapse runs of special chars into a single dot and strip leading/
    # trailing dots — GH rejects consecutive/trailing dots with HTTP 422.
    sanitize = re.compile(r"[^A-Za-z0-9._\-]+")
    collapse = re.compile(r"\.{2,}")
    count = 0
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
        flat = str(rel).replace("/", "__").replace("\\", "__")
        safe = sanitize.sub(".", flat)
        safe = collapse.sub(".", safe).strip(".")
        shutil.copy2(p, out / safe)
        count += 1
    return count


def delete_release_if_exists(tk):
    try:
        r = api("GET", f"https://api.github.com/repos/{REPO}/releases/tags/{TAG}", tk)
        api("DELETE", f"https://api.github.com/repos/{REPO}/releases/{r['id']}", tk)
        print(f"  Deleted release id={r['id']}")
    except urllib.error.HTTPError as e:
        if e.code != 404:
            raise
    # Also try to delete the tag ref so the next create can target a fresh sha
    try:
        api("DELETE", f"https://api.github.com/repos/{REPO}/git/refs/tags/{TAG}", tk)
        print(f"  Deleted tag ref {TAG}")
    except urllib.error.HTTPError as e:
        if e.code != 422 and e.code != 404:  # 422 = not found
            raise


def create_release(tk):
    body = json.dumps({
        "tag_name":    TAG,
        "name":        f"Modpack — latest",
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


def main():
    if not MANIFEST.is_file():
        print("ERROR: manifest.json missing. Run sync-instance.py first.",
              file=sys.stderr)
        sys.exit(1)

    tk = token()

    print("1) Flattening pack/ → pack-dist/")
    count = flatten_and_sanitize(PACK, DIST)
    shutil.copy2(MANIFEST, DIST / "manifest.json")
    count += 1
    print(f"   {count} assets staged")

    print("2) Deleting existing release (if any)")
    delete_release_if_exists(tk)
    # Small delay — GH sometimes needs a beat to release the tag
    time.sleep(3)

    print("3) Creating new release")
    rid = create_release(tk)

    print(f"4) Uploading {count} assets (parallel)")
    assets = sorted(DIST.iterdir())
    total = len(assets)
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

    if errors:
        print(f"\n{len(errors)} uploads FAILED:", file=sys.stderr)
        for n, err in errors[:10]:
            print(f"  {n}: {err}", file=sys.stderr)
        sys.exit(1)

    print(f"\nDone. Release: https://github.com/{REPO}/releases/tag/{TAG}")


if __name__ == "__main__":
    main()

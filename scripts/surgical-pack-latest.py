#!/usr/bin/env python3
"""Surgically replace ONLY specific assets on the pack-latest release,
without deleting/recreating it (avoids re-uploading the 745MB of mod jars).

Used to ship a config-only pack change (e.g. config/emotecraft.json) plus the
regenerated manifest.json. For each target, delete the existing asset by name
then upload the fresh file.
"""
import json, pathlib, sys, time, urllib.request, urllib.error

ROOT = pathlib.Path(__file__).resolve().parents[1]
REPO = "NYL3E/NyleLauncher"
TAG  = "pack-latest"
TOKEN_FILE = pathlib.Path.home() / ".config" / "nyle-launcher" / "gh-token"

# (local file, asset name on the release)
TARGETS = [
    (ROOT / "pack" / "config" / "emotecraft.json", "config__emotecraft.json"),
    (ROOT / "manifest.json",                        "manifest.json"),
]

def token():
    return TOKEN_FILE.read_text().strip()

def api(method, url, tk, data=None):
    req = urllib.request.Request(url, data=data, method=method)
    req.add_header("Authorization", f"token {tk}")
    req.add_header("Accept", "application/vnd.github+json")
    if data is not None:
        req.add_header("Content-Type", "application/json")
    with urllib.request.urlopen(req) as r:
        body = r.read()
        return json.loads(body) if body else {}

def present_assets(tk, rid):
    out, page = [], 1
    while True:
        batch = api("GET", f"https://api.github.com/repos/{REPO}/releases/{rid}/assets?per_page=100&page={page}", tk)
        if not batch:
            break
        out += batch
        if len(batch) < 100:
            break
        page += 1
    return out

def upload_asset(tk, rid, path, name):
    data = path.read_bytes()
    url = f"https://uploads.github.com/repos/{REPO}/releases/{rid}/assets?name={name}"
    req = urllib.request.Request(url, data=data, method="POST")
    req.add_header("Authorization", f"token {tk}")
    req.add_header("Content-Type", "application/octet-stream")
    with urllib.request.urlopen(req) as r:
        return r.status

def main():
    tk = token()
    rel = api("GET", f"https://api.github.com/repos/{REPO}/releases/tags/{TAG}", tk)
    rid = rel["id"]
    print(f"pack-latest release id={rid}")
    assets = {a["name"]: a["id"] for a in present_assets(tk, rid)}
    for path, name in TARGETS:
        if not path.is_file():
            print(f"ERROR: missing local file {path}", file=sys.stderr); sys.exit(1)
        if name in assets:
            api("DELETE", f"https://api.github.com/repos/{REPO}/releases/assets/{assets[name]}", tk)
            print(f"  deleted old {name}")
            time.sleep(1)
        upload_asset(tk, rid, path, name)
        print(f"  uploaded {name} ({path.stat().st_size} bytes)")
    # verify
    time.sleep(2)
    have = {a["name"] for a in present_assets(tk, rid)}
    for _, name in TARGETS:
        print(f"  verify {name}: {'PRESENT' if name in have else 'MISSING!!'}")

if __name__ == "__main__":
    main()

#!/usr/bin/env python3
"""
Generate manifest.json from the files under pack/.

The manifest describes every file the launcher must ensure is present on the
player's side, with its SHA-256 and download URL.

Usage:
    python3 scripts/generate-manifest.py [--release TAG]

The --release argument sets the GitHub Release tag from which files will be
served (default: pack-latest). The URL template is:
    https://github.com/NYL3E/NyleLauncher/releases/download/<TAG>/<flatname>

Since GitHub Releases flattens filenames (no subdirs in asset names), the
script encodes subdirectory paths into the asset name by replacing '/' with
'__'. The launcher reverses this mapping.
"""

import argparse
import datetime as _dt
import hashlib
import json
import pathlib
import re
import sys

ROOT   = pathlib.Path(__file__).resolve().parents[1]
PACK   = ROOT / "pack"
OUT    = ROOT / "manifest.json"

DEFAULT_REPO = "NYL3E/NyleLauncher"


def sha256(path: pathlib.Path) -> str:
    h = hashlib.sha256()
    with path.open("rb") as f:
        for chunk in iter(lambda: f.read(1024 * 64), b""):
            h.update(chunk)
    return h.hexdigest()


def flat_asset_name(rel: pathlib.Path) -> str:
    return str(rel).replace("/", "__").replace("\\", "__")


def gh_safe_asset_name(name: str) -> str:
    """Mirror GitHub Releases asset-name sanitization. GH replaces anything
    outside [A-Za-z0-9._-] (notably '+') with '.' on upload, and rejects
    consecutive dots / trailing dots with 422. Collapse runs and trim."""
    safe = re.sub(r"[^A-Za-z0-9._\-]+", ".", name)
    safe = re.sub(r"\.{2,}", ".", safe)
    return safe.strip(".")


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--release", default="pack-latest", help="GitHub Release tag")
    ap.add_argument("--repo", default=DEFAULT_REPO, help="GitHub repo (owner/name)")
    ap.add_argument("--mc", default="1.21.1")
    ap.add_argument("--loader-type", default="fabric")
    ap.add_argument("--loader-version", default="0.18.4")
    args = ap.parse_args()

    if not PACK.is_dir():
        print(f"ERROR: {PACK} does not exist", file=sys.stderr)
        sys.exit(1)

    files = []
    for p in sorted(PACK.rglob("*")):
        if p.is_file():
            rel = p.relative_to(PACK)
            # Skip README / dotfiles / empty files (GitHub releases reject 0-byte assets)
            if rel.name.startswith(".") or rel.name.lower() == "readme.md":
                continue
            if p.stat().st_size == 0:
                continue
            asset = gh_safe_asset_name(flat_asset_name(rel))
            url = f"https://github.com/{args.repo}/releases/download/{args.release}/{asset}"
            files.append({
                "path":   str(rel).replace("\\", "/"),
                "sha256": sha256(p),
                "size":   p.stat().st_size,
                "url":    url,
            })

    manifest = {
        "version":   _dt.datetime.now(_dt.timezone.utc).strftime("%Y.%m.%d-%H%M%S"),
        "mcVersion": args.mc,
        "loader":    {"type": args.loader_type, "version": args.loader_version},
        "files":     files,
    }

    OUT.write_text(json.dumps(manifest, indent=2, ensure_ascii=False) + "\n")
    print(f"Wrote {OUT} — {len(files)} files")


if __name__ == "__main__":
    main()

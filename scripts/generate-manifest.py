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

    # Files the user is expected to customise locally — pulled exactly once
    # on first install, never overwritten by subsequent manifest updates.
    # Render distance, GUI scale, key binds etc. live in options.txt;
    # last-played / favourites in servers.dat. Without this guard, every
    # publisher push that bumped the SHA would silently reset the player's
    # personal settings — see ModpackUpdater.firstInstallOnly handling.
    FIRST_INSTALL_ONLY = {"options.txt", "optionsof.txt", "servers.dat"}

    # Path-PREFIX based first-install protection. Use this for entire config
    # subtrees that the user reconfigures in-game (mic device, volumes,
    # hotkeys, onboarding state). Without this, the file matches by name
    # outside the set above and gets overwritten on every pack sync — which
    # is exactly the "voicechat keeps resetting" bug 2026-05-11.
    FIRST_INSTALL_PREFIXES = (
        # Simple Voice Chat — mic, speaker, gain, PTT, onboarding, per-player
        # & per-category volumes, username cache. Every one of these is set
        # by the user via the in-game SVC menu and must persist across syncs.
        "config/voicechat/",
        # Just Zoom keybind + zoom slope preferences live in this subdir.
        "config/justzoom/",
        # Sophisticated Storage user-side toggles (visuals, sort modes, etc.).
        "config/sophisticatedcore-client.toml",
        "config/sophisticatedstorage-client.toml",
        # Watut item-arm render adjustments (user-tweakable per-item arm fit).
        "config/watut-item-arm-adjustments.json",
        # NOTE: config/watut-client.toml is DELIBERATELY NOT protected — 2026-06-29
        # we force showGuisForYourOwnPlayerIn3rdPerson=false so Watut stops animating
        # the player's OWN model in 3rd person (head-look + right-arm point). That
        # animation flung the head + right arm off-view (extreme/NaN pivot) when the
        # window lost focus with no menu open. Must reach every existing player, so it
        # stays under publisher control (same rationale as DistantHorizons/ModMenu below).
        # Exposure camera mod client prefs.
        "config/exposure-client.toml",
        "config/exposure_polaroid-client.toml",
        # JEI bookmarks + filter settings.
        "config/jei/",
        # Iris shader pack selection + per-pack settings.
        "config/iris.properties",
        "config/iris/",
        # Backpacked client-only toggles.
        "config/backpacked.client.toml",
        # ConfiguredAPI client tweaks.
        "config/configured-client.properties",
        # Farmer's Delight client toggles.
        "config/farmersdelight-client.json",
        # Immersive Armor HUD client placement.
        "config/immersivearmorhud-client.toml",
        # Brewin & Chewin client visuals.
        "config/brewinandchewin-client.toml",
        # Exposure_polaroid client-side prefs.
        # NOTE: we DELIBERATELY do NOT protect:
        #   - sodium-options.json (we tune chunk_builder_threads)
        #   - DistantHorizons.toml (we tune lodRadius/SSAO/transparency,
        #     and 2026-05-16 we force enableAutoUpdater=false so the in-game
        #     update prompt is killed for every existing player on next sync)
        #   - modmenu.json (we force update_checker=false /
        #     button_update_badge=false so the ModMenu mod page never shows
        #     an "update available" badge — must propagate to every player)
        #   - pointblank-common.toml (we tune pipScopeRefreshRate/iris flag)
        # Those configs stay under publisher control so future tuning
        # reaches existing players.
    )

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
            entry = {
                "path":   str(rel).replace("\\", "/"),
                "sha256": sha256(p),
                "size":   p.stat().st_size,
                "url":    url,
            }
            rel_str = str(rel).replace("\\", "/")
            if (rel.name in FIRST_INSTALL_ONLY
                    or any(rel_str.startswith(p) for p in FIRST_INSTALL_PREFIXES)):
                entry["firstInstallOnly"] = True
            files.append(entry)

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

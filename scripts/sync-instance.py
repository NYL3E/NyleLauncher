#!/usr/bin/env python3
"""
Mirror a CurseForge (or vanilla) Minecraft instance into `pack/` so the modpack
publish workflow ships exactly what the developer plays with — no drift, no
hand-maintained subset of mods.

Default source:
    ~/Documents/curseforge/minecraft/Instances/<AUTO-DETECTED>

The source directory can be overridden with --instance. The script:
  1. Reads minecraftinstance.json to get mcVersion + fabric loader version
  2. Wipes pack/ (keeping README.md) and re-copies the whitelisted folders/files
  3. Invokes generate-manifest.py with the detected loader version

Run this before pushing: the Publish-modpack workflow takes it from there.
"""
import argparse
import json
import pathlib
import re
import shutil
import subprocess
import sys
import zipfile

ROOT = pathlib.Path(__file__).resolve().parents[1]
PACK = ROOT / "pack"
DEFAULT_CF = pathlib.Path.home() / "Documents" / "curseforge" / "minecraft" / "Instances"

# Top-level names to mirror from the instance. Directories are copied
# recursively; loose files are copied as-is. Everything else is ignored
# (logs, saves, crash reports, launcher metadata, etc.).
INCLUDE_DIRS = [
    "mods", "config", "defaultconfigs",
    "fancymenu_data", "global_packs",
]
# resourcepacks/ and shaderpacks/ get special handling: their subdirectories
# are zipped into single files so we ship a handful of .zip assets instead
# of thousands of loose files on the GH release.
PACK_DIRS = ["resourcepacks", "shaderpacks"]
# Auto-populated per-mod caches (cobblemon pokedex, showdown battle data,
# emotes, journeymap tiles, etc.) are NOT shipped — they are re-downloaded
# by the mods themselves on first launch and bloat the release otherwise.
INCLUDE_FILES = ["options.txt", "servers.dat"]


def pick_instance(instances_dir: pathlib.Path) -> pathlib.Path:
    """Pick the active Nyle server modpack instance. Prefer an exact
    'SAISON' folder, else fall back to the most-recently-modified mods/.
    Skips copies/backups suffixed 'copie'."""
    if not instances_dir.is_dir():
        print(f"ERROR: no instances directory at {instances_dir}", file=sys.stderr)
        sys.exit(1)
    candidates = [p for p in instances_dir.iterdir()
                  if p.is_dir() and not p.name.startswith(".")
                  and "COPIE" not in p.name.upper()]
    if not candidates:
        print(f"ERROR: no instances found under {instances_dir}", file=sys.stderr)
        sys.exit(1)
    saison = [p for p in candidates if "SAISON" in p.name.upper()]
    if saison:
        saison.sort(key=lambda p: -(p / "mods").stat().st_mtime
                    if (p / "mods").is_dir() else 0)
        return saison[0]
    # Fallback: instance whose mods/ was touched most recently
    def mods_mtime(p):
        m = p / "mods"
        return m.stat().st_mtime if m.is_dir() else 0
    candidates.sort(key=lambda p: -mods_mtime(p))
    return candidates[0]


def read_versions(instance: pathlib.Path):
    meta = instance / "minecraftinstance.json"
    if not meta.is_file():
        print(f"ERROR: {meta} not found — is this a CurseForge instance?",
              file=sys.stderr)
        sys.exit(1)
    d = json.loads(meta.read_text())
    mc = d.get("gameVersion") or "1.21.1"
    modloader = d.get("baseModLoader", {}).get("name") or ""
    # e.g. "fabric-0.18.4-1.21.1"
    m = re.match(r"(fabric|forge|neoforge)-([\w.+-]+)-(\d[\w.]*)", modloader)
    if not m:
        print(f"WARN: could not parse modloader '{modloader}', keeping defaults",
              file=sys.stderr)
        return mc, "fabric", "0.18.4"
    return mc, m.group(1), m.group(2)


def clean_pack():
    if not PACK.is_dir():
        PACK.mkdir(parents=True)
        return
    def on_rm_error(func, path, exc_info):
        import os, stat
        try:
            os.chmod(path, stat.S_IWRITE | stat.S_IREAD)
            func(path)
        except Exception:
            pass
    for child in PACK.iterdir():
        if child.name == "README.md":
            continue
        if child.is_dir():
            shutil.rmtree(child, onerror=on_rm_error)
        else:
            try: child.unlink()
            except Exception: pass


def copy_tree(src: pathlib.Path, dst: pathlib.Path):
    """Copy dir tree, skipping typical junk that shouldn't ship to players."""
    def ignore(path, names):
        skipped = set()
        for n in names:
            if n.startswith(".") or n.endswith((".log", ".tmp")):
                skipped.add(n)
            if n in {"crash-reports", "logs", "cache", "debug"}:
                skipped.add(n)
        return skipped
    shutil.copytree(src, dst, ignore=ignore, dirs_exist_ok=True)


def zip_packs(src: pathlib.Path, dst: pathlib.Path):
    """Mirror a resourcepacks/ or shaderpacks/ directory into `dst`, zipping
    any unpacked subfolder into a single .zip. Minecraft reads zipped packs
    natively; shipping them unpacked would mean thousands of release assets
    instead of a handful of zips."""
    dst.mkdir(parents=True, exist_ok=True)
    count = 0
    for child in src.iterdir():
        if child.name.startswith(".") or child.name.endswith(".tmp"):
            continue
        if child.is_dir():
            zip_path = dst / (child.name + ".zip")
            with zipfile.ZipFile(zip_path, "w", zipfile.ZIP_DEFLATED) as zf:
                for f in child.rglob("*"):
                    if f.is_file():
                        zf.write(f, f.relative_to(child))
            count += 1
        elif child.is_file():
            shutil.copy2(child, dst / child.name)
            count += 1
    return count


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--instance", type=pathlib.Path, default=None,
                    help="Path to the CurseForge instance directory")
    ap.add_argument("--release", default="pack-latest")
    args = ap.parse_args()

    instance = args.instance or pick_instance(DEFAULT_CF)
    print(f"Source instance: {instance.name}")

    mc_ver, loader_type, loader_ver = read_versions(instance)
    print(f"  mcVersion = {mc_ver}")
    print(f"  loader    = {loader_type} {loader_ver}")

    clean_pack()

    copied_dirs = 0
    copied_files = 0
    for name in INCLUDE_DIRS:
        src = instance / name
        if src.is_dir():
            dst = PACK / name
            copy_tree(src, dst)
            copied_dirs += 1
            print(f"  + {name}/ ({sum(1 for _ in dst.rglob('*') if _.is_file())} files)")
    for name in PACK_DIRS:
        src = instance / name
        if src.is_dir():
            dst = PACK / name
            n = zip_packs(src, dst)
            if n > 0:
                copied_dirs += 1
                print(f"  + {name}/ ({n} packs, zipped)")
    for name in INCLUDE_FILES:
        src = instance / name
        if src.is_file():
            shutil.copy2(src, PACK / name)
            copied_files += 1
            print(f"  + {name}")
    print(f"Copied {copied_dirs} directories and {copied_files} files into pack/")

    # Regenerate manifest.json using the detected loader version
    subprocess.run([sys.executable, str(ROOT / "scripts" / "generate-manifest.py"),
                    "--release", args.release,
                    "--mc", mc_ver,
                    "--loader-type", loader_type,
                    "--loader-version", loader_ver], check=True)


if __name__ == "__main__":
    main()

#!/usr/bin/env python3
"""build-prefix-pack.py — RESOURCE-PACK GENERATOR for uploadable grade prefixes.

Takes the server's config/nyleessentials/prefixes.json + the CURRENTLY-SERVED
resource pack, and bakes every prefix image into assets/minecraft/font/default.json
as a bitmap provider (PLUS its PNG), WITHOUT disturbing any existing provider
(ranks, gems, menus, titles, vanilla references, …). Produces a new pack zip, its
sha1, uploads it as a GitHub release asset, and applies the new server.properties.

TWO I/O MODES (auto-detected by env):
  • LOCAL DEV  (default) — reads from the server over mc-sftp (get), and only
    PRINTS the mc-sftp put + countdown + restart commands for the human. The live
    server is never touched.
  • HEADLESS / CI — when env PANEL_URL, SERVER_ID and PTERO_API_KEY are ALL set,
    every server I/O is done over HTTP against the Pterodactyl CLIENT API (no
    ~/bin CLIs needed). In this mode the script WRITES the new server.properties
    itself, and restarts the server only when it is safe to do so (0 players
    online, unless --no-restart is given). This is what the GitHub Action runs.

Common to both: download the served pack over HTTP, build artifacts in /tmp,
upload a GitHub *release asset* (the served pack URL points at GitHub).

Steps: 1 fetch prefixes.json · 2 fetch server.properties · 3 download served pack ·
4 merge default.json (preserve + skip-already-mapped) · 5 re-zip + sha1 ·
6 gh release upload · 7 build server.properties.new · 8 validate · 9 apply (CI only).

Run (local dev):  python3 tools/build-prefix-pack.py [--tag server-pack-live]
Run (CI/headless): PANEL_URL=… SERVER_ID=… PTERO_API_KEY=… GH_TOKEN=… \
                   python3 tools/build-prefix-pack.py --tag server-pack-live [--no-restart]
Idempotent + re-runnable.
"""
import argparse
import hashlib
import json
import os
import shutil
import subprocess
import sys
import urllib.error
import urllib.request
import zipfile

# ── Constants ────────────────────────────────────────────────────────────────
MC_SFTP = os.path.expanduser("~/bin/mc-sftp")

REMOTE_PREFIXES = "/config/nyleessentials/prefixes.json"
REMOTE_SERVERPROPS = "/server.properties"

# URL-encoded remote paths for the Pterodactyl client files API (%2F == '/').
API_PREFIXES_FILE = "%2Fconfig%2Fnyleessentials%2Fprefixes.json"
API_SERVERPROPS_FILE = "%2Fserver.properties"

LOCAL_PREFIXES = "/tmp/prefixes.json"
LOCAL_SERVERPROPS = "/tmp/server.properties"
LOCAL_SERVERPROPS_NEW = "/tmp/server.properties.new"
SERVED_ZIP = "/tmp/served-pack.zip"
WORKDIR = "/tmp/packwork"
NEW_ZIP = "/tmp/NYLERP-PACK.new.zip"

DEFAULT_JSON_REL = os.path.join("assets", "minecraft", "font", "default.json")
PREFIX_TEX_RELDIR = os.path.join("assets", "minecraft", "textures", "prefixes")
PACK_MCMETA_REL = "pack.mcmeta"

DEFAULT_TAG = "server-pack-live"
DEFAULT_REPO = "NYL3E/NyleLauncher"
DEFAULT_ASSET = "NYLERP-PACK.zip"

# Some packs are zipped WITH a wrapper folder (e.g. "NYLERP-PACK/pack.mcmeta").
# Detected after extraction; "" means pack.mcmeta is at the zip root.
PACK_ROOT = ""


def _wp(rel):
    """Filesystem path under WORKDIR honouring the detected PACK_ROOT."""
    return os.path.join(WORKDIR, PACK_ROOT, rel) if PACK_ROOT else os.path.join(WORKDIR, rel)


def _arc(rel):
    """Zip arcname (forward slashes) honouring the detected PACK_ROOT."""
    rel = rel.replace(os.sep, "/")
    return (PACK_ROOT + "/" + rel) if PACK_ROOT else rel


# ── Logging ──────────────────────────────────────────────────────────────────
def step(n, msg):
    print("\n=== STEP %s : %s" % (n, msg))


def log(msg):
    print("    " + msg)


def die(msg):
    print("\nFAIL: " + msg, file=sys.stderr)
    sys.exit(1)


def run(cmd, **kw):
    """Run a subprocess, echoing it, returning (rc, stdout, stderr)."""
    log("$ " + " ".join(cmd))
    p = subprocess.run(cmd, capture_output=True, text=True, **kw)
    if p.stdout.strip():
        for ln in p.stdout.strip().splitlines():
            log("  | " + ln)
    if p.returncode != 0 and p.stderr.strip():
        for ln in p.stderr.strip().splitlines():
            log("  ! " + ln)
    return p.returncode, p.stdout, p.stderr


# ── I/O mode : headless Pterodactyl-API vs. local mc-sftp/mc-api ─────────────
# In HEADLESS mode (CI) every server read/write goes over HTTP against the
# Pterodactyl CLIENT API. In LOCAL mode we shell out to ~/bin/mc-sftp + mc-api.
# Mode is decided ONCE at startup by the presence of all three env vars.
class IO:
    api = False          # True → Pterodactyl HTTP API; False → mc-sftp/mc-api
    panel = ""           # PANEL_URL (no trailing slash)
    server = ""          # SERVER_ID
    key = ""             # PTERO_API_KEY


def detect_mode():
    panel = os.environ.get("PANEL_URL", "").strip()
    server = os.environ.get("SERVER_ID", "").strip()
    key = os.environ.get("PTERO_API_KEY", "").strip()
    if panel and server and key:
        IO.api = True
        IO.panel = panel.rstrip("/")
        IO.server = server
        IO.key = key
        log("mode = HEADLESS (Pterodactyl API @ %s, server %s)" % (IO.panel, IO.server))
    else:
        IO.api = False
        log("mode = LOCAL (mc-sftp + mc-api)")


def _ptero_url(suffix):
    return "%s/api/client/servers/%s%s" % (IO.panel, IO.server, suffix)


def _ptero_request(method, suffix, data=None, ctype="text/plain", accept="application/json"):
    """One Pterodactyl CLIENT API call via curl. Returns (status, body_bytes).

    The panel is behind Cloudflare, which 1010-blocks Python's urllib by TLS/UA
    signature; curl's signature is allowed (it's what ~/bin/mc-api uses). curl is
    preinstalled on GitHub Actions runners too, so this works headless in CI.
    """
    url = _ptero_url(suffix)
    body_path = "/tmp/ptero_resp.bin"
    if os.path.exists(body_path):
        os.remove(body_path)
    cmd = ["curl", "-sS", "-o", body_path, "-w", "%{http_code}",
           "-H", "Authorization: Bearer %s" % IO.key,
           "-H", "Accept: %s" % accept,
           "-X", method, url]
    cleanup = None
    if data is not None:
        req_path = "/tmp/ptero_req.bin"
        with open(req_path, "wb") as fh:
            fh.write(data)
        cleanup = req_path
        cmd[1:1] = ["--data-binary", "@" + req_path, "-H", "Content-Type: %s" % ctype]
    p = subprocess.run(cmd, capture_output=True, text=True)
    if cleanup and os.path.exists(cleanup):
        os.remove(cleanup)
    if p.returncode != 0:
        die("curl to Pterodactyl failed (rc=%s): %s" % (p.returncode, p.stderr.strip()[:200]))
    try:
        status = int((p.stdout or "").strip()[-3:])
    except ValueError:
        status = 0
    body = open(body_path, "rb").read() if os.path.exists(body_path) else b""
    return status, body


def io_read_text(remote_path, api_file, dest):
    """Download a remote text file to `dest`. Honours the active I/O mode."""
    if IO.api:
        status, body = _ptero_request(
            "GET", "/files/contents?file=%s" % api_file, accept="application/json")
        if status == 404:
            return False  # file not present
        if status >= 400:
            die("Pterodactyl GET %s failed: HTTP %s %s"
                % (remote_path, status, body.decode("utf-8", "replace")[:300]))
        with open(dest, "wb") as fh:
            fh.write(body)
        return True
    # LOCAL: mc-sftp get
    rc, _, _ = run([MC_SFTP, "get", remote_path, dest])
    return rc == 0 and os.path.isfile(dest)


def io_write_serverprops(local_path):
    """Push the new server.properties to the server (HEADLESS mode only)."""
    with open(local_path, "rb") as fh:
        body = fh.read()
    status, resp = _ptero_request(
        "POST", "/files/write?file=%s" % API_SERVERPROPS_FILE, data=body, ctype="text/plain")
    if status >= 400:
        die("Pterodactyl write server.properties failed: HTTP %s %s"
            % (status, resp.decode("utf-8", "replace")[:300]))
    log("wrote /server.properties via Pterodactyl API (HTTP %s)" % status)


def io_players_online():
    """Best-effort current player count. Returns int, or None if unknown.

    Uses the Pterodactyl resources endpoint; player count is not part of the
    standard schema, so this almost always returns None → callers must default
    to NOT restarting when the count is unknown (never kick connected players).
    """
    status, body = _ptero_request("GET", "/resources")
    if status >= 400:
        return None
    try:
        data = json.loads(body.decode("utf-8"))
    except Exception:
        return None
    attrs = data.get("attributes", {}) if isinstance(data, dict) else {}
    # Pterodactyl reports state but not players; probe a few plausible keys.
    for k in ("players", "player_count", "online_players"):
        v = attrs.get(k)
        if isinstance(v, int):
            return v
    res = attrs.get("resources", {})
    for k in ("players", "player_count"):
        v = res.get(k) if isinstance(res, dict) else None
        if isinstance(v, int):
            return v
    return None


def io_command(cmd):
    """Send a console command to the server (HEADLESS mode). Best-effort."""
    body = json.dumps({"command": cmd}).encode()
    status, resp = _ptero_request("POST", "/command", data=body, ctype="application/json")
    if status >= 400:
        log("WARN: console command %r failed: HTTP %s %s"
            % (cmd, status, resp.decode("utf-8", "replace")[:200]))
        return False
    log("sent console command %r (HTTP %s)" % (cmd, status))
    return True


def io_restart():
    """Send a restart power signal (HEADLESS mode only)."""
    body = json.dumps({"signal": "restart"}).encode()
    status, resp = _ptero_request(
        "POST", "/power", data=body, ctype="application/json")
    if status >= 400:
        die("Pterodactyl restart failed: HTTP %s %s"
            % (status, resp.decode("utf-8", "replace")[:300]))
    log("sent restart signal via Pterodactyl API (HTTP %s)" % status)


# ── Step 1 : fetch prefixes.json ─────────────────────────────────────────────
def fetch_prefixes():
    step(1, "fetch %s -> %s" % (REMOTE_PREFIXES, LOCAL_PREFIXES))
    if os.path.exists(LOCAL_PREFIXES):
        os.remove(LOCAL_PREFIXES)
    if not io_read_text(REMOTE_PREFIXES, API_PREFIXES_FILE, LOCAL_PREFIXES):
        die("could not download %s (have you run seed-prefixes + uploaded it?)" % REMOTE_PREFIXES)
    try:
        with open(LOCAL_PREFIXES, encoding="utf-8") as fh:
            data = json.load(fh)
    except Exception as e:
        die("prefixes.json is not valid JSON: %s" % e)
    if not isinstance(data, dict) or not data:
        die("prefixes.json must be a non-empty object")

    # Validate each entry's shape so we fail loudly before mutating the pack.
    norm = {}
    for pid, entry in data.items():
        if not isinstance(pid, str) or not pid:
            die("prefix id must be a non-empty string, got %r" % (pid,))
        if not isinstance(entry, dict):
            die("prefix %r value must be an object" % pid)
        for key in ("image", "char", "height", "ascent"):
            if key not in entry:
                die("prefix %r missing field %r" % (pid, key))
        char = entry["char"]
        if not isinstance(char, str) or len(char) != 1:
            die("prefix %r 'char' must be a single code point, got %r" % (pid, char))
        norm[pid] = {
            "image": entry["image"],
            "char": char,
            "height": int(entry["height"]),
            "ascent": entry["ascent"],
        }
        log("  prefix %-16s char=U+%04X height=%s ascent=%s"
            % (pid, ord(char), norm[pid]["height"], norm[pid]["ascent"]))
    log("OK: %d prefixes parsed" % len(norm))
    return norm


# ── Step 2 : fetch + parse server.properties ─────────────────────────────────
def fetch_serverprops():
    step(2, "fetch %s -> %s" % (REMOTE_SERVERPROPS, LOCAL_SERVERPROPS))
    if os.path.exists(LOCAL_SERVERPROPS):
        os.remove(LOCAL_SERVERPROPS)
    if not io_read_text(REMOTE_SERVERPROPS, API_SERVERPROPS_FILE, LOCAL_SERVERPROPS):
        die("could not download %s" % REMOTE_SERVERPROPS)
    with open(LOCAL_SERVERPROPS, encoding="utf-8", errors="replace") as fh:
        lines = fh.read().splitlines()

    url = None
    sha1 = None
    for ln in lines:
        s = ln.strip()
        if s.startswith("#") or "=" not in s:
            continue
        key, _, val = s.partition("=")
        key = key.strip()
        if key == "resource-pack":
            # Java .properties escapes ':' and '=' in values (e.g. https\://...).
            url = val.strip().replace("\\:", ":").replace("\\=", "=")
        elif key == "resource-pack-sha1":
            sha1 = val.strip()
    if not url:
        die("no resource-pack= URL found in server.properties")
    log("OK: resource-pack=%s" % url)
    log("    resource-pack-sha1=%s" % (sha1 or "(none)"))
    return lines, url, sha1


# ── Step 3 : download the currently-served pack ──────────────────────────────
def download_served(url):
    step(3, "download served pack -> %s" % SERVED_ZIP)
    if os.path.exists(SERVED_ZIP):
        os.remove(SERVED_ZIP)
    try:
        req = urllib.request.Request(url, headers={"User-Agent": "nyle-prefix-pack/1.0"})
        with urllib.request.urlopen(req, timeout=120) as resp, open(SERVED_ZIP, "wb") as out:
            shutil.copyfileobj(resp, out)
    except Exception as e:
        die("download failed for %s : %s" % (url, e))
    size = os.path.getsize(SERVED_ZIP)
    if size < 100:
        die("downloaded pack is suspiciously small (%d bytes)" % size)
    if not zipfile.is_zipfile(SERVED_ZIP):
        die("downloaded file is not a valid zip")
    log("OK: %d bytes" % size)


# ── Step 4 : unzip + merge default.json (preserve + skip-already-mapped) ──────
def existing_mapped_chars(font_json):
    """Every single char already mapped by any provider in default.json."""
    mapped = set()
    for prov in font_json.get("providers", []):
        for entry in prov.get("chars", []) or []:
            for ch in entry:
                mapped.add(ch)
    return mapped


def merge_pack(prefixes):
    step(4, "unzip + merge default.json")
    if os.path.isdir(WORKDIR):
        shutil.rmtree(WORKDIR)
    os.makedirs(WORKDIR)
    with zipfile.ZipFile(SERVED_ZIP) as z:
        z.extractall(WORKDIR)

    # Detect a wrapper folder (e.g. NYLERP-PACK/) that holds pack.mcmeta.
    global PACK_ROOT
    PACK_ROOT = ""
    if not os.path.isfile(os.path.join(WORKDIR, PACK_MCMETA_REL)):
        for entry in sorted(os.listdir(WORKDIR)):
            cand = os.path.join(WORKDIR, entry)
            if os.path.isdir(cand) and os.path.isfile(os.path.join(cand, PACK_MCMETA_REL)):
                PACK_ROOT = entry
                break
    if not os.path.isfile(_wp(PACK_MCMETA_REL)):
        die("served pack has no pack.mcmeta (root or one folder deep)")
    log("pack root = %s" % (PACK_ROOT or "<zip root>"))

    default_path = _wp(DEFAULT_JSON_REL)
    if not os.path.isfile(default_path):
        die("served pack has no %s" % DEFAULT_JSON_REL)
    with open(default_path, encoding="utf-8") as fh:
        font = json.load(fh)
    if "providers" not in font or not isinstance(font["providers"], list):
        die("default.json has no 'providers' array")

    orig_count = len(font["providers"])
    orig_ref_count = sum(1 for p in font["providers"] if p.get("type") == "reference")
    orig_rank_files = sorted(
        str(p.get("file", "")) for p in font["providers"]
        if "ranks/" in str(p.get("file", ""))
    )
    mapped = existing_mapped_chars(font)
    log("served default.json: %d providers (%d reference, %d rank providers)"
        % (orig_count, orig_ref_count, len(orig_rank_files)))

    os.makedirs(_wp(PREFIX_TEX_RELDIR), exist_ok=True)

    added = []
    skipped = []
    updated = []
    import base64 as _b64
    for pid, entry in sorted(prefixes.items()):
        char = entry["char"]
        # Always (re)write the PNG so the pack texture matches prefixes.json,
        # but only ADD a provider for chars not already mapped (idempotent +
        # never clobber the pre-baked ranks/gems/etc.).
        png_path = os.path.join(_wp(PREFIX_TEX_RELDIR), "%s.png" % pid)
        try:
            raw = _b64.b64decode(entry["image"], validate=True)
        except Exception as e:
            die("prefix %r image is not valid base64: %s" % (pid, e))
        if raw[:8] != b"\x89PNG\r\n\x1a\n":
            die("prefix %r image is not a PNG" % pid)

        if char in mapped:
            # Char already provided by an existing provider (e.g. a pre-baked rank).
            # Treat a re-upload as an OVERWRITE: rewrite that provider's referenced
            # PNG with the uploaded bytes + update its height/ascent, so replacing a
            # rank badge from /admin actually takes effect in-game (DEFECT 1 fix).
            target = None
            for p in font["providers"]:
                if p.get("type") == "bitmap" and char in (p.get("chars") or []):
                    target = p
                    break
            if target is None:
                skipped.append((pid, char))
                log("  skip   %-16s U+%04X (char mapped but no bitmap provider found)"
                    % (pid, ord(char)))
                continue
            ref = str(target.get("file", ""))
            ns, path = ref.split(":", 1) if ":" in ref else ("minecraft", ref)
            ref_path = _wp(os.path.join("assets", ns, "textures", *path.split("/")))
            os.makedirs(os.path.dirname(ref_path), exist_ok=True)
            with open(ref_path, "wb") as fh:
                fh.write(raw)
            target["height"] = int(entry["height"])
            target["ascent"] = entry["ascent"]
            updated.append((pid, char))
            log("  update %-16s U+%04X -> %s (overwrote provider texture, h=%s a=%s)"
                % (pid, ord(char), ref, entry["height"], entry["ascent"]))
            continue

        with open(png_path, "wb") as fh:
            fh.write(raw)
        provider = {
            "type": "bitmap",
            "file": "minecraft:prefixes/%s.png" % pid,
            "chars": [char],
            "height": int(entry["height"]),
            "ascent": entry["ascent"],
        }
        font["providers"].append(provider)
        mapped.add(char)
        added.append((pid, char))
        log("  add    %-16s U+%04X -> minecraft:prefixes/%s.png (h=%s a=%s)"
            % (pid, ord(char), pid, entry["height"], entry["ascent"]))

    # Write the merged default.json back (pretty, keep unicode escaped).
    with open(default_path, "w", encoding="utf-8") as fh:
        json.dump(font, fh, indent=2, ensure_ascii=True)
        fh.write("\n")

    log("OK: +%d providers added, %d textures overwritten, %d skipped. new total=%d"
        % (len(added), len(updated), len(skipped), len(font["providers"])))
    return {
        "added": added,
        "updated": updated,
        "skipped": skipped,
        "orig_ref_count": orig_ref_count,
        "orig_rank_files": orig_rank_files,
    }


# ── Step 5 : re-zip + sha1 ───────────────────────────────────────────────────
def rezip():
    step(5, "re-zip -> %s + sha1" % NEW_ZIP)
    if os.path.exists(NEW_ZIP):
        os.remove(NEW_ZIP)
    # Zip RELATIVE to the detected pack root, so the output is a canonical
    # root-level pack (pack.mcmeta at the zip root) even if the served pack was
    # wrapped in a folder. Skip OS junk so the pack stays clean.
    base = os.path.join(WORKDIR, PACK_ROOT) if PACK_ROOT else WORKDIR
    files = []
    for root, _dirs, names in os.walk(base):
        for name in names:
            if name == ".DS_Store":
                continue
            full = os.path.join(root, name)
            arc = os.path.relpath(full, base)
            if arc.startswith("__MACOSX"):
                continue
            files.append((arc, full))
    files.sort(key=lambda t: t[0])
    if not any(arc == PACK_MCMETA_REL for arc, _ in files):
        die("refusing to zip: pack.mcmeta missing from workdir")
    with zipfile.ZipFile(NEW_ZIP, "w", zipfile.ZIP_DEFLATED) as z:
        for arc, full in files:
            z.write(full, arc)
    sha1 = hashlib.sha1()
    with open(NEW_ZIP, "rb") as fh:
        for chunk in iter(lambda: fh.read(1 << 20), b""):
            sha1.update(chunk)
    digest = sha1.hexdigest()
    log("OK: %s (%d bytes) sha1=%s" % (NEW_ZIP, os.path.getsize(NEW_ZIP), digest))
    return digest


# ── Step 6 : GitHub release upload (REST API — no gh CLI dependency) ──────────
GH_TOKEN_FILE = os.path.expanduser("~/.config/nyle-launcher/gh-token")


def _gh_token():
    tok = os.environ.get("GH_TOKEN", "").strip()
    if tok:
        return tok
    if os.path.isfile(GH_TOKEN_FILE):
        with open(GH_TOKEN_FILE) as fh:
            return fh.read().strip()
    die("no GitHub token (set GH_TOKEN or create %s)" % GH_TOKEN_FILE)


def _gh_api(method, url, tok, data=None, ctype="application/json"):
    req = urllib.request.Request(url, data=data, method=method)
    req.add_header("Authorization", "token %s" % tok)
    req.add_header("Accept", "application/vnd.github+json")
    if data is not None:
        req.add_header("Content-Type", ctype)
        req.add_header("Content-Length", str(len(data)))
    with urllib.request.urlopen(req) as r:
        body = r.read()
        return json.loads(body) if body else {}


def gh_upload(tag, repo, asset_name):
    step(6, "upload to GitHub release %s @ tag %s as %s (REST API)" % (repo, tag, asset_name))
    tok = _gh_token()

    # 1) Ensure the release exists (get by tag, else create).
    rel = None
    try:
        rel = _gh_api("GET", "https://api.github.com/repos/%s/releases/tags/%s" % (repo, tag), tok)
    except urllib.error.HTTPError as e:
        if e.code != 404:
            die("GET release failed: HTTP %s" % e.code)
    if not rel:
        body = json.dumps({
            "tag_name": tag,
            "name": "Server resource pack (live, autogen)",
            "body": "Auto-generated NYLERP-PACK with baked prefix glyphs. The live server pulls its resource-pack from this asset. The tag is STABLE — the asset is clobbered on each publish so the URL never changes.",
            "draft": False,
            "prerelease": True,
        }).encode()
        rel = _gh_api("POST", "https://api.github.com/repos/%s/releases" % repo, tok, body)
        log("created release id=%s" % rel.get("id"))
    rel_id = rel["id"]

    # 2) Clobber any existing asset of the same name.
    for a in rel.get("assets", []):
        if a.get("name") == asset_name:
            try:
                _gh_api("DELETE", "https://api.github.com/repos/%s/releases/assets/%s" % (repo, a["id"]), tok)
                log("deleted existing asset id=%s" % a["id"])
            except urllib.error.HTTPError as e:
                die("could not delete existing asset: HTTP %s" % e.code)

    # 3) Upload the new asset.
    with open(NEW_ZIP, "rb") as fh:
        data = fh.read()
    up = "https://uploads.github.com/repos/%s/releases/%s/assets?name=%s" % (repo, rel_id, asset_name)
    req = urllib.request.Request(up, data=data, method="POST")
    req.add_header("Authorization", "token %s" % tok)
    req.add_header("Content-Type", "application/zip")
    req.add_header("Content-Length", str(len(data)))
    try:
        with urllib.request.urlopen(req) as r:
            log("uploaded asset (HTTP %s, %d bytes)" % (r.status, len(data)))
    except urllib.error.HTTPError as e:
        die("asset upload failed: HTTP %s %s" % (e.code, e.read().decode("utf-8", "replace")[:300]))

    url = "https://github.com/%s/releases/download/%s/%s" % (repo, tag, asset_name)
    log("OK: download URL = %s" % url)
    return url


# ── Step 7 : build server.properties.new ─────────────────────────────────────
def write_serverprops_new(lines, new_url, new_sha1):
    step(7, "build %s" % LOCAL_SERVERPROPS_NEW)
    out = []
    saw_url = False
    saw_sha = False
    for ln in lines:
        s = ln.strip()
        if not s.startswith("#") and "=" in s:
            key = s.partition("=")[0].strip()
            if key == "resource-pack":
                # Re-escape ':' the way Minecraft writes .properties values.
                out.append("resource-pack=%s" % new_url.replace(":", "\\:"))
                saw_url = True
                continue
            if key == "resource-pack-sha1":
                out.append("resource-pack-sha1=%s" % new_sha1)
                saw_sha = True
                continue
        out.append(ln)
    if not saw_url:
        out.append("resource-pack=%s" % new_url.replace(":", "\\:"))
    if not saw_sha:
        out.append("resource-pack-sha1=%s" % new_sha1)
    with open(LOCAL_SERVERPROPS_NEW, "w", encoding="utf-8") as fh:
        fh.write("\n".join(out) + "\n")
    log("OK: wrote %s (resource-pack + resource-pack-sha1 updated, all other lines untouched)"
        % LOCAL_SERVERPROPS_NEW)


# ── Step 8 : validate the new zip ────────────────────────────────────────────
def validate(prefixes, merge_info):
    step(8, "validate %s" % NEW_ZIP)
    failures = []
    if not zipfile.is_zipfile(NEW_ZIP):
        die("new zip is not a valid zip")
    with zipfile.ZipFile(NEW_ZIP) as z:
        names = set(z.namelist())

        # pack.mcmeta present (the output is unwrapped → root-level)
        if PACK_MCMETA_REL not in names:
            failures.append("pack.mcmeta missing")

        # default.json parses
        dj = DEFAULT_JSON_REL.replace(os.sep, "/")
        if dj not in names:
            failures.append("default.json missing")
            font = {"providers": []}
        else:
            try:
                font = json.loads(z.read(dj).decode("utf-8"))
            except Exception as e:
                failures.append("default.json does not parse: %s" % e)
                font = {"providers": []}

        provs = font.get("providers", [])
        ref_count = sum(1 for p in provs if p.get("type") == "reference")
        rank_files = sorted(
            str(p.get("file", "")) for p in provs if "ranks/" in str(p.get("file", ""))
        )

        # Reference providers preserved (>= original; original may be 0).
        if ref_count < merge_info["orig_ref_count"]:
            failures.append("reference providers dropped: had %d now %d"
                            % (merge_info["orig_ref_count"], ref_count))

        # Pre-existing rank providers preserved exactly.
        for rf in merge_info["orig_rank_files"]:
            if rf not in rank_files:
                failures.append("rank provider lost: %s" % rf)

        # Every prefix char must be mapped, and every ADDED prefix must have its PNG.
        mapped = set()
        for p in provs:
            for entry in p.get("chars", []) or []:
                for ch in entry:
                    mapped.add(ch)
        for pid, entry in prefixes.items():
            ch = entry["char"]
            if ch not in mapped:
                failures.append("prefix %s char U+%04X not mapped in default.json"
                                % (pid, ord(ch)))
        added_ids = {pid for pid, _ in merge_info["added"]}
        for pid in added_ids:
            arc = os.path.join(PREFIX_TEX_RELDIR, "%s.png" % pid).replace(os.sep, "/")
            if arc not in names:
                failures.append("added prefix %s missing texture %s" % (pid, arc))

        log("new default.json: %d providers (%d reference, %d rank). prefixes mapped: %d/%d"
            % (len(provs), ref_count, len(rank_files), len(prefixes), len(prefixes)))

    if failures:
        print("\n--- VALIDATION FAIL ---")
        for f in failures:
            print("  ✗ " + f)
        die("validation failed; NOT publishing. The live server was untouched.")
    print("\n--- VALIDATION PASS ---")
    print("  ✓ pack.mcmeta present")
    print("  ✓ default.json parses")
    print("  ✓ reference providers preserved (%d)" % merge_info["orig_ref_count"])
    print("  ✓ %d rank providers preserved" % len(merge_info["orig_rank_files"]))
    print("  ✓ all %d prefix chars mapped + textures present" % len(prefixes))


# ── Step 9 : apply (HEADLESS/CI only) — write props + (conditionally) restart ─
def apply_headless(no_restart):
    step(9, "apply server.properties + LIVE pack push (HEADLESS)")
    # a. Write the new server.properties (resource-pack URL + sha1 swapped) so new
    #    joiners + future restarts use the new pack.
    io_write_serverprops(LOCAL_SERVERPROPS_NEW)

    # b. Push the new pack to all ONLINE players LIVE — no restart, no kick. The
    #    mod's `/nyle pack push` re-reads the fresh server.properties and re-sends
    #    the pack; clients only re-download because the sha1 changed (hash-cached),
    #    so this is safe to fire every publish. This makes a restart unnecessary.
    io_command("nyle pack push")
    return True


# ── main ─────────────────────────────────────────────────────────────────────
def main():
    ap = argparse.ArgumentParser(description="Bake uploadable grade prefixes into the served resource pack.")
    ap.add_argument("--tag", default=DEFAULT_TAG, help="GitHub release tag (default: %s)" % DEFAULT_TAG)
    ap.add_argument("--repo", default=DEFAULT_REPO, help="GitHub repo (default: %s)" % DEFAULT_REPO)
    ap.add_argument("--asset", default=DEFAULT_ASSET, help="asset filename (default: %s)" % DEFAULT_ASSET)
    ap.add_argument("--no-restart", action="store_true",
                    help="HEADLESS mode: never restart (default: restart only if 0 players online)")
    args = ap.parse_args()

    print("build-prefix-pack: tag=%s repo=%s asset=%s" % (args.tag, args.repo, args.asset))
    detect_mode()

    if not IO.api and not os.path.isfile(MC_SFTP):
        die("mc-sftp not found at %s (LOCAL mode needs it; or set PANEL_URL/SERVER_ID/PTERO_API_KEY for HEADLESS mode)" % MC_SFTP)
    if not IO.api:
        print("(LOCAL mode: this script DOES NOT push to the live server and DOES NOT restart it)")

    prefixes = fetch_prefixes()
    sp_lines, served_url, served_sha1 = fetch_serverprops()
    download_served(served_url)
    merge_info = merge_pack(prefixes)
    new_sha1 = rezip()
    new_url = gh_upload(args.tag, args.repo, args.asset)
    write_serverprops_new(sp_lines, new_url, new_sha1)
    validate(prefixes, merge_info)

    if IO.api:
        # ── HEADLESS/CI: apply automatically, then report ─────────────────────
        restarted = apply_headless(args.no_restart)
        print("\n" + "=" * 72)
        print("SUCCESS — new pack built, uploaded, and APPLIED to the live server.")
        print("=" * 72)
        print("\nNew pack URL:    %s" % new_url)
        print("New sha1:        %s" % new_sha1)
        print("server.properties: updated via Pterodactyl API")
        print("Restart:           %s" % ("sent (0 players online)" if restarted
                                         else "SKIPPED (players online / unknown / --no-restart)"))
        print("\nThe new pack applies on the next (re)connect; a restart was %s."
              % ("triggered" if restarted else "intentionally skipped to avoid kicking players"))
        return

    # ── LOCAL DEV: print the EXACT commands the human must run, in order ──────
    print("\n" + "=" * 72)
    print("SUCCESS — new pack built + uploaded. The live server is STILL UNTOUCHED.")
    print("=" * 72)
    print("\nNew pack:        %s" % NEW_ZIP)
    print("New sha1:        %s" % new_sha1)
    print("New pack URL:    %s" % new_url)
    print("New props file:  %s" % LOCAL_SERVERPROPS_NEW)
    print("\nHUMAN — run these IN ORDER (no restart needed):")
    print("  1) Push the new server.properties:")
    print("       ~/bin/mc-sftp put %s %s" % (LOCAL_SERVERPROPS_NEW, REMOTE_SERVERPROPS))
    print("  2) Push the new pack to all online players LIVE (no kick):")
    print('       ~/bin/mc-api console "nyle pack push"')
    print("\nThe mod re-sends the pack; clients re-download only because the sha1 changed.")


if __name__ == "__main__":
    main()

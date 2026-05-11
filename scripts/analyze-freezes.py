#!/usr/bin/env python3
"""
analyze-freezes.py — Real-data freeze analyzer for NyleRP bug reports.

Cross-correlates:
  1. freezes-*.log (MicroFreezeTracker hitches with stack signatures)
  2. blue-*.log (full thread dumps captured during stalls)
  3. server logs/latest.log (server-side activity in the same window)
  4. client logs/latest.log (mod loading, errors, etc.)

For each chronic hitch signature, identifies:
  - Which method (vanilla + mod) is on the stack
  - What the SERVER was doing in the same second
  - Whether OTHER threads (chunk builders, GC threads, mod ticks) were
    holding locks or doing heavy work

Output: a structured report. Not English prose like my prior analyses —
data-driven categorisation so the user can SEE the root cause.
"""

import os, re, sys, glob, json, gzip
from collections import defaultdict, Counter
from datetime import datetime, timezone

if len(sys.argv) < 2:
    print("usage: analyze-freezes.py <unpacked-report-dir> [<server-log-path>]", file=sys.stderr)
    sys.exit(2)

REPORT = sys.argv[1]
SERVER_LOG = sys.argv[2] if len(sys.argv) > 2 else None

rc = os.path.join(REPORT, "rendercheck")
client_log = os.path.join(REPORT, "logs", "latest.log")

# ── 1. Parse all freeze entries across sessions ────────────────────────

HITCH_RE = re.compile(r"^(\d{4}-\d{2}-\d{2})_(\d{2}-\d{2}-\d{2})\s+hitch=(\d+)ms\s+sig=(.+)$")
GC_RE    = re.compile(r"^(\d{4}-\d{2}-\d{2})_(\d{2}-\d{2}-\d{2})\s+GC pause=(\d+)ms\s+collector=([^\s]+(?:\s\w+)*?)\s+cause=(.+)$")
SNAP_RE  = re.compile(r"^---\s+System snapshot\s+@\s+(\d{4}-\d{2}-\d{2})_(\d{2}-\d{2}-\d{2})\s+---")
HEAP_RE  = re.compile(r"^\s+Heap:\s+used=(\d+)MB total=(\d+)MB max=(\d+)MB \((\S+)\)")

all_hitches = []
all_gc = []
all_snapshots = []

def parse_ts(d, t):
    """combine '2026-05-11' + '13-16-07' → datetime UTC unaware."""
    return datetime.strptime(f"{d} {t.replace('-', ':')}", "%Y-%m-%d %H:%M:%S")

freeze_files = sorted(glob.glob(os.path.join(rc, "freezes-*.log")))
for f in freeze_files:
    with open(f) as fh:
        snap_ts = None
        for line in fh:
            m = HITCH_RE.match(line.rstrip())
            if m:
                d, t, ms, sig = m.groups()
                all_hitches.append({"ts": parse_ts(d, t), "ms": int(ms), "sig": sig, "file": os.path.basename(f)})
                continue
            m = GC_RE.match(line.rstrip())
            if m:
                d, t, ms, coll, cause = m.groups()
                all_gc.append({"ts": parse_ts(d, t), "ms": int(ms), "collector": coll.strip(), "cause": cause.strip(), "file": os.path.basename(f)})
                continue
            m = SNAP_RE.match(line.rstrip())
            if m:
                snap_ts = parse_ts(*m.groups())
                continue
            m = HEAP_RE.match(line.rstrip())
            if m and snap_ts:
                used, total, mx, pct = m.groups()
                all_snapshots.append({"ts": snap_ts, "used": int(used), "total": int(total), "max": int(mx), "pct": pct})
                snap_ts = None

# Deduplicate hitches by (timestamp, sig) — each freeze log echoes them in cumulative summaries
seen = set()
hitches = []
for h in all_hitches:
    key = (h["ts"], h["sig"])
    if key in seen: continue
    seen.add(key)
    hitches.append(h)

print(f"=== Bug report ===")
print(f"  freeze logs:   {len(freeze_files)}")
print(f"  unique hitches: {len(hitches)}")
print(f"  GC pauses:     {len(all_gc)}")
print(f"  heap snapshots: {len(all_snapshots)}")

# ── 2. Cluster hitches by signature simplification ─────────────────────

def simplify_sig(sig):
    """Pick the top 1-2 stack frames and trim them to a short fingerprint."""
    # The sig is "frame1  ←  frame2  ←  frame3  ←  frame4  ←"
    frames = [f.strip() for f in sig.split("←") if f.strip()]
    if not frames: return sig[:80]
    top = frames[0]
    # Trim args
    if "(" in top: top = top.split("(")[0]
    if ":" in top:
        # Drop the line number
        top = top.rsplit(":", 1)[0]
    return top

def classify(sig):
    """Categorise by ownership — what is at the top of the stack."""
    s = sig.split("←")[0].strip()
    if "org.lwjgl.opengl" in s or "GL11C" in s or "GL32C" in s or "glfw" in s.lower(): return "GPU/native"
    if "Sodium" in s or "caffeinemc" in s.lower():        return "Sodium"
    if "iris" in s.lower():                                return "Iris"
    if "lithium" in s.lower():                             return "Lithium"
    if "ferritecore" in s.lower():                         return "FerriteCore"
    if "krypton" in s.lower():                             return "Krypton"
    if "moulberry" in s.lower() or "axiom" in s.lower():   return "Axiom"
    if "pointblank" in s.lower():                          return "PointBlank"
    if "vicmatskiv" in s.lower():                          return "PointBlank"
    if "geckolib" in s.lower():                            return "GeckoLib"
    if "entityculling" in s.lower() or "tr7zw" in s.lower(): return "EntityCulling"
    if "immediatelyfast" in s.lower() or "raphimc" in s.lower(): return "ImmediatelyFast"
    if "architectury" in s.lower():                        return "Architectury"
    if "trinkets" in s.lower():                            return "Trinkets"
    if "jei" in s.lower() or "mezz" in s.lower():          return "JEI"
    if "nyle" in s.lower() or "com.nyle" in s.lower():     return "Nyle (our code)"
    if "java.lang.ClassLoader" in s or "defineClass" in s: return "Class loading"
    if "java.io" in s or "java.nio.file" in s:             return "File I/O"
    if "FontStorage" in s or "class_377" in s:             return "Font glyph"
    if "GameRenderer" in s or "class_757" in s or "class_761" in s: return "Vanilla render"
    if "class_310" in s:                                   return "MinecraftClient"
    if "sun." in s or "jdk." in s:                         return "JVM/JDK"
    if "java.util" in s or "java.lang" in s:               return "Java core"
    return "Other"

cat_total = defaultdict(int)
cat_count = defaultdict(int)
cat_max = defaultdict(int)
cat_examples = defaultdict(list)

for h in hitches:
    c = classify(h["sig"])
    cat_total[c] += h["ms"]
    cat_count[c] += 1
    cat_max[c] = max(cat_max[c], h["ms"])
    if len(cat_examples[c]) < 2:
        cat_examples[c].append((h["ts"], h["ms"], simplify_sig(h["sig"])))

print("\n=== Hitches by category (sorted by TOTAL stall time) ===")
print(f"{'CATEGORY':<22}{'COUNT':>7}{'TOTAL ms':>11}{'MAX ms':>9}  EXAMPLE")
for c in sorted(cat_total, key=cat_total.get, reverse=True):
    print(f"{c:<22}{cat_count[c]:>7}{cat_total[c]:>11}{cat_max[c]:>9}  {cat_examples[c][0][2][:70]}")

# ── 3. Look at the LATEST session only for the most recent picture ─────

latest_file = freeze_files[-1] if freeze_files else None
if latest_file:
    sess = os.path.basename(latest_file).replace("freezes-", "").replace(".log", "")
    latest_hitches = [h for h in hitches if h["file"] == os.path.basename(latest_file)]
    print(f"\n=== LATEST session ({sess}) — {len(latest_hitches)} unique hitches ===")
    print(f"{'TIME':<10}{'ms':>6}  category — top frame")
    for h in latest_hitches:
        c = classify(h["sig"])
        print(f"{h['ts'].strftime('%H:%M:%S'):<10}{h['ms']:>6}  [{c}] {simplify_sig(h['sig'])[:75]}")

# ── 4. GC behaviour ────────────────────────────────────────────────────

print(f"\n=== GC pauses (de-duped) ===")
seen_gc = set()
gcs = []
for g in all_gc:
    key = (g["ts"], g["collector"])
    if key in seen_gc: continue
    seen_gc.add(key)
    gcs.append(g)
gcs_by_coll = defaultdict(list)
for g in gcs: gcs_by_coll[g["collector"]].append(g["ms"])
for coll, mss in gcs_by_coll.items():
    print(f"  {coll:<30} count={len(mss):>4}  total={sum(mss):>6}ms  max={max(mss):>5}ms  avg={sum(mss)//max(1,len(mss)):>4}ms")

# ── 5. Heap trend ──────────────────────────────────────────────────────

if all_snapshots:
    print(f"\n=== Heap occupancy over time ===")
    for s in all_snapshots[-10:]:
        bar = "█" * (int(s["used"]) * 40 // max(1, int(s["max"])))
        print(f"  {s['ts'].strftime('%H:%M:%S')}  {s['used']:>5} / {s['max']:>5} MB  {s['pct']:>6}  {bar}")

# ── 6. Inspect a couple of blue-*.log dumps for the latest session ─────

if latest_file:
    sess_prefix = sess[:13]  # e.g. "2026-05-11_13" — same hour
    blues = sorted(glob.glob(os.path.join(rc, f"blue-{sess_prefix}*.log")))
    if blues:
        print(f"\n=== Thread dumps captured in latest session ({len(blues)} files) ===")
        # Show top render-thread frame from each
        for b in blues[:3]:
            with open(b) as fh:
                content = fh.read()
            # Find Render thread frames
            m = re.search(r'"Render thread"[^"]*\n(.+?)(?=\n"|\Z)', content, re.DOTALL)
            if m:
                frames = [l.strip() for l in m.group(1).split("\n") if l.strip().startswith("at ")][:5]
                print(f"\n  {os.path.basename(b)}:")
                for fr in frames: print(f"    {fr[:110]}")

# ── 7. Mod list shipped (from client latest.log) ───────────────────────

if os.path.exists(client_log):
    print(f"\n=== Mods loaded (top of latest.log) ===")
    with open(client_log) as fh:
        in_mods = False
        count = 0
        for line in fh:
            if "Loading" in line and "mods:" in line:
                in_mods = True
                print(f"  {line.strip()}")
                continue
            if in_mods:
                if line.strip().startswith("- "):
                    count += 1
                else:
                    break
        print(f"  ({count} top-level mods listed)")

# ── 8. Server log correlation (if provided) ────────────────────────────

if SERVER_LOG and os.path.exists(SERVER_LOG):
    print(f"\n=== Server-side activity within ±2 s of each chronic hitch ===")
    SERVER_TS_RE = re.compile(r"^\[(\d{2}):(\d{2}):(\d{2})\]")
    server_lines = []
    with open(SERVER_LOG) as fh:
        for line in fh:
            m = SERVER_TS_RE.match(line)
            if m: server_lines.append((m.group(1)+":"+m.group(2)+":"+m.group(3), line.rstrip()))
    # For chronic categories (>500ms total), find server lines in the window
    chronic = [c for c in cat_total if cat_total[c] >= 500]
    for c in chronic[:5]:
        print(f"\n  Category: {c}")
        events_around = set()
        for h in hitches:
            if classify(h["sig"]) != c: continue
            t = h["ts"].strftime("%H:%M:%S")
            tm = h["ts"].minute * 60 + h["ts"].second
            for st, line in server_lines:
                hh, mm, ss = map(int, st.split(":"))
                stm = mm * 60 + ss
                if abs(stm - tm) <= 2 and h["ts"].hour == hh:
                    events_around.add(line[:130])
                    if len(events_around) >= 5: break
            if len(events_around) >= 5: break
        for e in sorted(events_around)[:5]:
            print(f"    {e}")

print()

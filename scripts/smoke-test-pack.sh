#!/bin/bash
# SMOKE-TEST DU PACK (owner 2026-07-11 : « le launcher DOIT TOUJOURS être opérationnel à 100% »)
# ═══════════════════════════════════════════════════════════════════════════════════════════
# Boote un VRAI serveur Fabric avec EXACTEMENT les mods du pack, dans un bac à sable jetable,
# AVANT tout upload-pack.py. Attrape les crashs de chargement qui tuent le jeu de tous les
# joueurs : collisions de mixins (incident 2026-07-11 03:27 — @ModifyConstant nyleessentials vs
# nylepets sur ServerPlayNetworkHandler = serveurs ET clients morts), entrypoints cassés,
# doublons Set.of, dépendances manquantes. Un pack qui ne boote pas côté serveur ne DOIT PAS
# être poussé : la quasi-totalité des crashs de chargement sont communs client/serveur.
#
# Usage : bash scripts/smoke-test-pack.sh   (exit 0 = pack sain, exit 1 = NE PAS PUSHER)
# RÈGLE : upload-pack.py refuse de tourner si SMOKE_OK n'est pas frais (voir garde ci-dessous).
set -u
PACK="$(cd "$(dirname "$0")/.." && pwd)/pack"
JAVA="${JAVA_HOME:-/Users/nyle/dev-tools/jdk-21.0.3+9/Contents/Home}/bin/java"
SANDBOX="/tmp/nyle-pack-smoke"
FABRIC_INSTALLER_URL="https://meta.fabricmc.net/v2/versions/loader/1.21.1/0.19.1/1.0.3/server/jar"
TIMEOUT_S=300
MARKER="$PACK/../.smoke-ok"

echo "═ SMOKE-TEST PACK — bac à sable: $SANDBOX"
rm -rf "$SANDBOX"; mkdir -p "$SANDBOX/mods" "$SANDBOX/config"

# 1. Mods du pack (client-only purs tolérés : Fabric les ignore côté serveur)
cp "$PACK/mods/"*.jar "$SANDBOX/mods/" || { echo "✖ pack/mods introuvable"; exit 1; }
# Configs du pack si présentes (certains mods crashent sur config par défaut absente)
[ -d "$PACK/config" ] && cp -R "$PACK/config/." "$SANDBOX/config/" 2>/dev/null

# 2. Serveur fabric (téléchargé une fois, ensuite en cache)
CACHE="$HOME/.cache/nyle-smoke"; mkdir -p "$CACHE"
if [ ! -f "$CACHE/fabric-server.jar" ]; then
  echo "  téléchargement fabric-server launcher…"
  curl -sL --max-time 120 "$FABRIC_INSTALLER_URL" -o "$CACHE/fabric-server.jar" || { echo "✖ download fabric"; exit 1; }
fi
cp "$CACHE/fabric-server.jar" "$SANDBOX/server.jar"
echo "eula=true" > "$SANDBOX/eula.txt"
cat > "$SANDBOX/server.properties" << EOP
level-type=minecraft\\:flat
online-mode=false
max-players=1
motd=smoke
server-port=25599
generate-structures=false
spawn-protection=0
EOP

# 3. Boot : succès = la ligne « Done (…s)! » ; échec = crash/exception fatale/timeout.
cd "$SANDBOX"
: > server.log
"$JAVA" -Xmx3G -jar server.jar --nogui > server.log 2>&1 &
PID=$!
OK=0
for i in $(seq 1 $TIMEOUT_S); do
  if grep -q 'Done (' server.log 2>/dev/null; then OK=1; break; fi
  if ! kill -0 $PID 2>/dev/null; then break; fi          # process mort avant Done = crash
  if grep -qE 'Mixin transformation of .* failed|Critical injection failure|ExceptionInInitializerError|Could not execute entrypoint' server.log 2>/dev/null; then break; fi
  sleep 1
done
# stop propre puis kill
{ echo "stop"; } >/dev/null 2>&1
kill $PID 2>/dev/null; sleep 2; kill -9 $PID 2>/dev/null

if [ "$OK" = "1" ]; then
  MODS=$(grep -m1 -oE 'Loading [0-9]+ mods' server.log || true)
  echo "✔ PACK SAIN — boot complet ($MODS)"
  date +%s > "$MARKER"
  exit 0
fi
echo "✖ PACK CASSÉ — le serveur n'a pas booté. Extraits :"
grep -E 'Mixin transformation|Critical injection|ExceptionInInitializerError|Could not execute entrypoint|ERROR' server.log | head -10
echo "  log complet : $SANDBOX/server.log"
rm -f "$MARKER"
exit 1

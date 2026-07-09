#!/usr/bin/env bash
# sign-release.sh — signe les binaires Windows d'une release GitHub avec le certificat
# Certum « Open Source Developer Lenny TRIDAT » (clé privée dans le cloud SimplySign).
#
#   bash scripts/sign-release.sh v0.3.21          # signe MSI + EXE portable de la release
#   bash scripts/sign-release.sh --list           # liste les alias du keystore SimplySign
#
# Prérequis (une fois) :
#   1. SimplySign Desktop installé (macOS) + app mobile SimplySign appairée (OTP).
#   2. SimplySign Desktop CONNECTÉ (icône verte) avant de lancer ce script — la clé
#      privée n'est accessible qu'avec la session ouverte.
#   3. Variables (ou défauts ci-dessous) : SIMPLYSIGN_PKCS11 (chemin .dylib), SIGN_PIN.
#
# Ce que fait le script :
#   - télécharge jsign (jar épinglé SHA-256) au premier run ;
#   - télécharge le MSI + le ZIP portable de la release <tag> ;
#   - signe le MSI et le NyleLauncher.exe du portable (SHA-256 + horodatage RFC3161
#     time.certum.pl), re-zippe le portable ;
#   - REMPLACE les assets de la release (delete + upload, mêmes noms → mêmes URLs) ;
#   - vérifie que chaque asset répond 200 après remplacement.
#
# La réputation SmartScreen se construit ENSUITE sur ce certificat au fil des
# téléchargements — les alertes disparaissent progressivement (jours→semaines).
set -euo pipefail

REPO="NYL3E/NyleLauncher"
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
TOKEN_FILE="$HOME/.config/nyle-launcher/gh-token"
JSIGN_VERSION="6.0"
JSIGN_SHA256="05ca18d4ab7b8c2183289b5378d32860f0ea0f3bdab1f1b8cae5894fb225fa8a"
JSIGN_JAR="$ROOT/tools/jsign-$JSIGN_VERSION.jar"
TSA="http://time.certum.pl"
JAVA_HOME="${JAVA_HOME:-/Users/nyle/dev-tools/jdk-21.0.3+9/Contents/Home}"
JAVA="$JAVA_HOME/bin/java"
KEYTOOL="$JAVA_HOME/bin/keytool"

# ── Module PKCS#11 SimplySign : env > autodétection ─────────────────────────
find_pkcs11() {
  if [ -n "${SIMPLYSIGN_PKCS11:-}" ] && [ -f "$SIMPLYSIGN_PKCS11" ]; then
    echo "$SIMPLYSIGN_PKCS11"; return
  fi
  local candidates=(
    "/Applications/SimplySignDesktop.app/Contents/Resources/lib/libSimplySignPKCS.dylib"
    "/Applications/SimplySignDesktop.app/Contents/Frameworks/libSimplySignPKCS.dylib"
    "/usr/local/lib/libSimplySignPKCS.dylib"
    "/Library/SimplySign/libSimplySignPKCS.dylib"
    "/usr/local/lib/pkcs11/libcryptoCertum3PKCS.dylib"
    "/Library/cryptoCertum/libcryptoCertum3PKCS.dylib"
  )
  for c in "${candidates[@]}"; do [ -f "$c" ] && { echo "$c"; return; }; done
  # dernier recours : scan de l'app
  find /Applications/SimplySignDesktop.app -name "*PKCS*.dylib" 2>/dev/null | head -1
}

PKCS11_LIB="$(find_pkcs11 || true)"
if [ -z "$PKCS11_LIB" ]; then
  echo "ERREUR: module PKCS#11 SimplySign introuvable." >&2
  echo "Installe SimplySign Desktop (https://www.certum.eu → SimplySign) puis" >&2
  echo "relance, ou exporte SIMPLYSIGN_PKCS11=/chemin/vers/libSimplySignPKCS.dylib" >&2
  exit 1
fi
echo "PKCS#11 : $PKCS11_LIB"

PKCS11_CFG="$(mktemp /tmp/simplysign-pkcs11.XXXXXX.cfg)"
trap 'rm -f "$PKCS11_CFG"' EXIT
cat > "$PKCS11_CFG" <<EOF
name = SimplySign
library = $PKCS11_LIB
EOF

# ── jsign (téléchargé + épinglé) ─────────────────────────────────────────────
if [ ! -f "$JSIGN_JAR" ]; then
  mkdir -p "$ROOT/tools"
  echo "Téléchargement de jsign $JSIGN_VERSION…"
  curl -sL "https://github.com/ebourg/jsign/releases/download/$JSIGN_VERSION/jsign-$JSIGN_VERSION.jar" -o "$JSIGN_JAR"
  actual=$(shasum -a 256 "$JSIGN_JAR" | cut -d' ' -f1)
  if [ "$actual" != "$JSIGN_SHA256" ]; then
    echo "ERREUR: SHA-256 de jsign inattendu ($actual) — abandon." >&2
    rm -f "$JSIGN_JAR"; exit 1
  fi
fi

# ── mode --list : découvrir l'alias du certificat sur la carte virtuelle ─────
if [ "${1:-}" = "--list" ]; then
  read -r -s -p "PIN SimplySign : " PIN; echo
  "$KEYTOOL" -list -keystore NONE -storetype PKCS11 \
    -providerClass sun.security.pkcs11.SunPKCS11 -providerArg "$PKCS11_CFG" \
    -storepass "$PIN"
  exit 0
fi

TAG="${1:?usage: sign-release.sh <tag vX.Y.Z> | --list}"
TK="$(cat "$TOKEN_FILE")"
# PIN : env SIGN_PIN > trousseau macOS (service nyle-simplysign) > prompt interactif (TTY requis).
# Pour le stocker UNE FOIS dans le trousseau (saisie sécurisée, hors de tout historique) :
#   security add-generic-password -s nyle-simplysign -a "$USER" -w
PIN="${SIGN_PIN:-}"
if [ -z "$PIN" ]; then
  PIN="$(security find-generic-password -s nyle-simplysign -w 2>/dev/null || true)"
fi
if [ -z "$PIN" ]; then
  if [ -t 0 ]; then
    read -r -s -p "PIN SimplySign : " PIN; echo
  else
    echo "ERREUR: pas de PIN. Stocke-le une fois dans le trousseau :" >&2
    echo "  security add-generic-password -s nyle-simplysign -a \"\$USER\" -w" >&2
    echo "(saisie masquée), puis relance ce script." >&2
    exit 1
  fi
fi
ALIAS="${SIGN_ALIAS:-}"   # vide = jsign prend le premier alias code-signing

WORK="$(mktemp -d /tmp/sign-release.XXXXXX)"
echo "Espace de travail : $WORK"

api() { curl -s -H "Authorization: token $TK" -H "Accept: application/vnd.github+json" "$@"; }

REL_JSON="$(api "https://api.github.com/repos/$REPO/releases/tags/$TAG")"
REL_ID="$(echo "$REL_JSON" | python3 -c 'import json,sys; print(json.load(sys.stdin)["id"])')"
echo "Release $TAG (id $REL_ID)"

sign_file() {
  local f="$1"
  local args=(--keystore "$PKCS11_CFG" --storetype PKCS11 --storepass "$PIN"
              --alg SHA-256 --tsaurl "$TSA" --tsmode RFC3161
              --name "NyleLauncher" --url "https://www.nylerp.fr")
  [ -n "$ALIAS" ] && args+=(--alias "$ALIAS")
  "$JAVA" -jar "$JSIGN_JAR" "${args[@]}" "$f"
}

replace_asset() {
  local name="$1" path="$2"
  local aid
  aid="$(echo "$REL_JSON" | python3 -c "
import json,sys
for a in json.load(sys.stdin)['assets']:
    if a['name'] == '$name': print(a['id']); break")"
  if [ -n "$aid" ]; then
    api -X DELETE "https://api.github.com/repos/$REPO/releases/assets/$aid" > /dev/null
  fi
  local code
  code="$(curl -s -o /dev/null -w '%{http_code}' -X POST \
    -H "Authorization: token $TK" -H "Content-Type: application/octet-stream" \
    --data-binary "@$path" \
    "https://uploads.github.com/repos/$REPO/releases/$REL_ID/assets?name=$name")"
  [ "$code" = "201" ] || { echo "ERREUR upload $name → HTTP $code" >&2; exit 1; }
  echo "  ✔ $name remplacé (signé)"
}

download_asset() {
  local name="$1"
  curl -sL -H "Authorization: token $TK" \
    "https://github.com/$REPO/releases/download/$TAG/$name" -o "$WORK/$name"
  [ -s "$WORK/$name" ] || { echo "ERREUR: asset $name introuvable sur $TAG" >&2; exit 1; }
}

# ── 1. MSI ───────────────────────────────────────────────────────────────────
MSI="NyleLauncher-windows-$TAG.msi"
echo "→ $MSI"
download_asset "$MSI"
sign_file "$WORK/$MSI"
replace_asset "$MSI" "$WORK/$MSI"

# ── 2. Portable : signer l'EXE dedans puis re-zipper ─────────────────────────
ZIP="NyleLauncher-windows-portable-$TAG.zip"
echo "→ $ZIP"
download_asset "$ZIP"
( cd "$WORK" && unzip -q "$ZIP" )
chmod -R u+w "$WORK/NyleLauncher"   # l'unzip préserve le read-only ; jsign signe EN PLACE (écriture requise)
EXE="$(find "$WORK/NyleLauncher" -maxdepth 1 -name "*.exe" | head -1)"
[ -n "$EXE" ] || { echo "ERREUR: exe introuvable dans le zip portable" >&2; exit 1; }
sign_file "$EXE"
( cd "$WORK" && rm -f "$ZIP" && zip -qr "$ZIP" "NyleLauncher" )
replace_asset "$ZIP" "$WORK/$ZIP"

# ── 3. Vérification publique (chemin joueur) ─────────────────────────────────
for name in "$MSI" "$ZIP"; do
  code="$(curl -s -o /dev/null -w '%{http_code}' -L "https://github.com/$REPO/releases/download/$TAG/$name?t=$(date +%s)")"
  echo "  $name → HTTP $code"
  [ "$code" = "200" ] || { echo "ERREUR: $name ne répond pas 200 après remplacement" >&2; exit 1; }
done

echo
echo "✅ Release $TAG signée (Certum, SHA-256, horodatée $TSA)."
echo "   Rappel : la réputation SmartScreen se construit sur ce certificat au fil"
echo "   des téléchargements — les alertes s'estompent en quelques jours/semaines."
rm -rf "$WORK"

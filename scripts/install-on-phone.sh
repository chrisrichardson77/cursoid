#!/usr/bin/env bash
#
# Installs Cursoid on a phone attached to *this* machine.
#
#   ./scripts/install-on-phone.sh              # release build over USB
#   ./scripts/install-on-phone.sh --debug      # unminified build instead
#   ./scripts/install-on-phone.sh --wifi       # pair over Wi-Fi, no cable
#   ./scripts/install-on-phone.sh --force      # uninstall first if the key differs
#
set -uo pipefail

RELEASE_APK="dist/cursoid-0.1.0.apk"
DEBUG_APK="dist/cursoid-0.1.0-debug.apk"
PKG="dev.cursoid"
DEBUG_PKG="dev.cursoid.debug"

apk="$RELEASE_APK"
pkg="$PKG"
force=0
wifi=0

usage() {
  cat <<'EOF'
Installs Cursoid on a phone attached to this machine.

  ./scripts/install-on-phone.sh              release build over USB
  ./scripts/install-on-phone.sh --debug      unminified build instead
  ./scripts/install-on-phone.sh --wifi       pair over Wi-Fi, no cable needed
  ./scripts/install-on-phone.sh --force      uninstall first if the signing key differs
  ./scripts/install-on-phone.sh path/to.apk  install some other APK

  ANDROID_SERIAL=<serial>                    choose between several attached devices
EOF
}

while [ $# -gt 0 ]; do
  case "$1" in
    --debug) apk="$DEBUG_APK"; pkg="$DEBUG_PKG" ;;
    --force) force=1 ;;
    --wifi)  wifi=1 ;;
    -h|--help) usage; exit 0 ;;
    *) apk="$1" ;;
  esac
  shift
done

cd "$(dirname "$0")/.."

say()  { printf '\n\033[1m%s\033[0m\n' "$*"; }
err()  { printf '\033[31m%s\033[0m\n' "$*" >&2; }
step() { printf '  %s\n' "$*"; }

# --- find adb ----------------------------------------------------------------

find_adb() {
  if command -v adb >/dev/null 2>&1; then command -v adb; return; fi
  local candidates=(
    "${ANDROID_HOME:-}/platform-tools/adb"
    "${ANDROID_SDK_ROOT:-}/platform-tools/adb"
    "$HOME/Library/Android/sdk/platform-tools/adb"
    "$HOME/Android/Sdk/platform-tools/adb"
    "$HOME/android-sdk/platform-tools/adb"
    "/usr/local/share/android-sdk/platform-tools/adb"
    "/opt/android-sdk/platform-tools/adb"
  )
  local c
  for c in "${candidates[@]}"; do
    [ -n "$c" ] && [ -x "$c" ] && { echo "$c"; return; }
  done
  return 1
}

if ! ADB="$(find_adb)"; then
  err "Couldn't find adb on this machine."
  say "Install just the platform tools (no full SDK needed):"
  step "macOS:   brew install --cask android-platform-tools"
  step "Linux:   sudo apt install android-tools-adb"
  step "Windows: winget install Google.PlatformTools"
  step "Or unzip https://developer.android.com/studio/releases/platform-tools"
  exit 1
fi

if [ ! -f "$apk" ]; then
  err "No APK at $apk"
  step "Run this from the repo root, or pass a path: $0 path/to.apk"
  exit 1
fi

"$ADB" start-server >/dev/null 2>&1

# --- optional Wi-Fi pairing (Android 11+, no cable) --------------------------

if [ "$wifi" -eq 1 ]; then
  say "Wireless debugging pairing"
  step "On the phone: Settings > Developer options > Wireless debugging > ON"
  step "Tap 'Pair device with pairing code'. It shows an IP:PORT and a 6-digit code."
  printf '\n  Pairing IP:PORT (e.g. 192.168.1.20:37103): '
  read -r pair_addr
  printf '  6-digit pairing code: '
  read -r pair_code
  if ! "$ADB" pair "$pair_addr" "$pair_code"; then
    err "Pairing failed. Check the phone and this machine are on the same Wi-Fi network."
    exit 1
  fi
  step "Now read the address under 'Wireless debugging' itself (a different port)."
  printf '  Connect IP:PORT (e.g. 192.168.1.20:41234): '
  read -r conn_addr
  "$ADB" connect "$conn_addr" || { err "Connect failed."; exit 1; }
fi

# --- pick a device -----------------------------------------------------------

devices_raw() { "$ADB" devices | tail -n +2 | grep -v '^\s*$'; }

online_count()       { devices_raw | grep -cw "device"; }
unauthorized_count() { devices_raw | grep -cw "unauthorized"; }

if [ "$(online_count)" -eq 0 ] && [ "$(unauthorized_count)" -gt 0 ]; then
  say "The phone is connected but hasn't authorised this computer."
  step "Unlock the phone and tap 'Allow' on the 'Allow USB debugging?' prompt."
  step "Tick 'Always allow from this computer' so it sticks."
  printf '\n  Press Return once you have accepted it: '
  read -r _
  "$ADB" kill-server >/dev/null 2>&1
  "$ADB" start-server >/dev/null 2>&1
  sleep 2
fi

if [ "$(online_count)" -eq 0 ] && [ "$(unauthorized_count)" -gt 0 ]; then
  err "The phone is still showing as unauthorised."
  step "The 'Allow USB debugging?' dialog has to be accepted on the phone itself."
  step "If it never appears: Developer options > Revoke USB debugging authorisations,"
  step "then unplug, replug, and watch the phone screen."
  exit 1
fi

if [ "$(online_count)" -eq 0 ]; then
  err "No phone visible to adb."
  say "Work through these — the first two catch almost every case:"
  step "1. The cable must carry data. Many charger cables are power-only;"
  step "   if the phone charges but never appears, try a different cable."
  step "2. Enable USB debugging: Settings > About phone > tap 'Build number'"
  step "   seven times, then Settings > System > Developer options > USB debugging."
  step "3. Set the USB mode to 'File transfer' / 'MTP', not 'Charging only'."
  step "   Search the Settings app for 'USB' if there is no notification to tap."
  step "4. Then re-run this script."
  say "No cable to hand? Android 11+ can do this over Wi-Fi:"
  step "$0 --wifi"
  exit 1
fi

if [ "$(online_count)" -gt 1 ] && [ -z "${ANDROID_SERIAL:-}" ]; then
  err "More than one device is attached:"
  devices_raw
  say "Pick one by serial:"
  step "ANDROID_SERIAL=<serial> $0"
  exit 1
fi

model="$("$ADB" shell getprop ro.product.model 2>/dev/null | tr -d '\r')"
release="$("$ADB" shell getprop ro.build.version.release 2>/dev/null | tr -d '\r')"
say "Installing $(basename "$apk") on ${model:-the phone} (Android ${release:-?})"

# --- install -----------------------------------------------------------------

do_install() { "$ADB" install -r "$apk" 2>&1; }

out="$(do_install)"
echo "$out" | grep -v '^\s*$' | sed 's/^/  /'

if echo "$out" | grep -q "INSTALL_FAILED_UPDATE_INCOMPATIBLE\|signatures do not match"; then
  say "An existing install of $pkg was signed with a different key."
  if [ "$force" -eq 1 ]; then
    step "Removing it (--force), then retrying."
  else
    step "Uninstalling it will delete that copy's saved API key and settings."
    printf '\n  Uninstall and reinstall? [y/N]: '
    read -r reply
    case "$reply" in [yY]*) ;; *) err "Left alone. Re-run with --force to skip this prompt."; exit 1 ;; esac
  fi
  "$ADB" uninstall "$pkg" >/dev/null 2>&1
  out="$(do_install)"
  echo "$out" | grep -v '^\s*$' | sed 's/^/  /'
fi

if echo "$out" | grep -q "INSTALL_FAILED_USER_RESTRICTED"; then
  err "The phone blocked the install."
  step "Xiaomi/Redmi/POCO: Developer options > turn on 'Install via USB' and"
  step "'USB debugging (Security settings)'. Both need a signed-in Mi account."
  exit 1
fi

if ! echo "$out" | grep -q "^Success\|Success$"; then
  err "Install did not report success. Full output is above."
  exit 1
fi

# --- launch ------------------------------------------------------------------

component="$("$ADB" shell cmd package resolve-activity --brief "$pkg" 2>/dev/null | tail -1 | tr -d '\r')"
if [ -n "$component" ] && [ "${component#*/}" != "$component" ]; then
  "$ADB" shell am start -n "$component" >/dev/null 2>&1
else
  "$ADB" shell monkey -p "$pkg" -c android.intent.category.LAUNCHER 1 >/dev/null 2>&1
fi

say "Done — Cursoid is on the phone and starting."
step "To look around without an API key, turn on Demo mode on the sign-in screen."
step "For real agents, paste a key from cursor.com/dashboard > Integrations > API Keys."

#!/usr/bin/env bash
# Sets up an XGODY / "ADT-3" Allwinner H723 projector (Android 14) that already has Magisk root:
# new launcher, Projector app, debloat, Play pinned to its factory build, Magisk boot scripts, settings.
#
#   SERIAL=<adb serial or ip:5555> ./deploy.sh [--dry-run] [--no-picture] [--force]
#
#   --dry-run     show what would change, change nothing
#   --no-picture  leave the HDMI picture mode as it is (default: "Standard")
#   --force       go on even on a firmware build this was not tried on
# Needs adb (ADB=/path/to/adb to use another one). The projector must allow root for the ADB shell
# (Magisk -> Superuser -> Shell). Safe to run again; a snapshot of the state before is saved in backups/.
set -uo pipefail
HERE=$(cd "$(dirname "$0")" && pwd)
cd "$HERE"
DRY=0 PICTURE=1 FORCE=0
for a in "$@"; do
  case "$a" in
    --dry-run) DRY=1 ;; --no-picture) PICTURE=0 ;; --force) FORCE=1 ;;
    *) echo "unknown option $a"; exit 2 ;;
  esac
done
A=("${ADB:-adb}"); [ -n "${SERIAL:-}" ] && A+=(-s "$SERIAL")
S() { "${A[@]}" shell "$1" < /dev/null | tr -d '\r'; }
push() { "${A[@]}" push "$1" "$2" < /dev/null >/dev/null || die "push $1 failed"; }
step() { echo; echo "=== $*"; }
die() { echo; echo "STOP: $*"; exit 1; }
# Firmware builds this was run on (both XGODY units, March and May 2026 builds).
BUILDS="h723_evb1-user 14 UP1A.231105.001.A1 user.bsw.20260324.082128 release-keys
h723_evb1-user 14 UP1A.231105.001.A1 user.bsw.20260523.060358 release-keys"
mkdir -p logs backups
LOG="logs/deploy-$(date +%Y%m%d-%H%M%S)$([ $DRY = 1 ] && echo -dry).log"
exec > >(tee "$LOG") 2>&1 < /dev/null

step "Projector"
[ "$(S 'echo ok')" = ok ] || die "no adb device (set SERIAL=..., see 'adb devices')"
B=$(S 'getprop ro.build.description'); SER=$(S 'getprop ro.serialno')
echo "build:  $B"; echo "serial: $SER"
if ! printf '%s\n' "$BUILDS" | grep -qxF "$B"; then
  echo "tried on:"; printf '  %s\n' "$BUILDS"
  [ $FORCE = 1 ] || die "unknown firmware build; compare it first, then run again with --force"
fi
S 'su -c id' | grep -q "uid=0" || die "no root for the ADB shell: Magisk -> Superuser -> allow Shell (or answer the prompt on screen), then run again"
echo "root:   $(S 'su -c "magisk -v"')"
S 'pm path com.mylove.setting' | grep -q package: || die "com.mylove.setting (vendor settings) not found: this is not the firmware this was made for"

step "Snapshot of the current state (read-only) -> backups/"
BK="backups/$SER-$(date +%Y%m%d-%H%M%S)"
push device/backup.sh /data/local/tmp/cyt-backup.sh
S 'sh /data/local/tmp/cyt-backup.sh >/dev/null 2>&1; rm -f /data/local/tmp/cyt-backup.sh'
"${A[@]}" pull /data/local/tmp/cyt-backup "$BK" < /dev/null >/dev/null && echo "saved to deploy/$BK"

step "Launcher and Projector apps"
for apk in ../apk/tvhome-launcher.apk ../apk/projector.apk; do
  if [ $DRY = 1 ]; then echo "  [dry-run] adb install -r $apk"; else echo "  $apk: $("${A[@]}" install -r "$apk" < /dev/null | tr -d '\r' | tail -1)"; fi
done

step "Root part (runs on the projector)"
S 'rm -rf /data/local/tmp/cyt; mkdir -p /data/local/tmp/cyt/device /data/local/tmp/cyt/magisk'
push disable.txt /data/local/tmp/cyt/disable.txt
for f in apply.sh verify.sh; do push "device/$f" "/data/local/tmp/cyt/device/$f"; done
for f in cyt-play-pin.sh cyt-cast.sh cyt-keys.sh cyt-settings-key.sh; do push "../magisk/$f" "/data/local/tmp/cyt/magisk/$f"; done
S "su -c 'DRY=$DRY sh /data/local/tmp/cyt/device/apply.sh'"

if [ $PICTURE = 1 ]; then
  step "HDMI picture mode: Standard (opens HDMI for a few seconds)"
  if [ $DRY = 1 ]; then echo "  [dry-run] picmode standard"; else
    S 'am start -n com.softwinner.awsource/.MainActivity >/dev/null 2>&1'; sleep 6
    S 'O=$(pm path com.homeprojector.settings | head -1 | cut -d: -f2); V=$(pm path com.mylove.setting | head -1 | cut -d: -f2); printf "picmode standard\nquit\n" | su -c "CLASSPATH=$O:$V app_process /system/bin com.homeprojector.settings.Helper" 2>&1 | grep "^ok [a-z]" | sed -E "s/ [a-z_,]+,custom//; s/^/  /"'
    S 'am start -a android.intent.action.MAIN -c android.intent.category.HOME >/dev/null 2>&1'
  fi
fi

step "Checks"
sleep 3
S "su -c 'sh /data/local/tmp/cyt/device/verify.sh'"
echo
echo "Left to do by hand: Projector -> Keystone / Zoom; Google Play -> Settings -> Auto-update apps -> off;"
echo "install your apps from Google Play. Log: deploy/$LOG"

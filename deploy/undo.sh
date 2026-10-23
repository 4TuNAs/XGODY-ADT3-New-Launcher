#!/usr/bin/env bash
# Takes the projector back to the snapshot deploy.sh saved before it changed anything.
#   SERIAL=<adb serial or ip:5555> ./undo.sh backups/<serial>-<date>
# Re-enables what deploy.sh disabled, gives Home back to the launcher that had it, removes the Magisk scripts,
# the root grant for Projector, and the launcher and Projector apps. Reboot afterwards.
set -uo pipefail
HERE=$(cd "$(dirname "$0")" && pwd)
cd "$HERE"
BK=${1:?usage: SERIAL=... ./undo.sh backups/<serial>-<date>}
[ -f "$BK/packages_disabled.txt" ] || { echo "no packages_disabled.txt in $BK"; exit 1; }
A=("${ADB:-adb}"); [ -n "${SERIAL:-}" ] && A+=(-s "$SERIAL")
S() { "${A[@]}" shell "$1" < /dev/null | tr -d '\r'; }
WAS=$(sed 's/package://' "$BK/packages_disabled.txt" | tr -d '\r')
HOME_WAS=$(tail -1 "$BK/home_resolved.txt" | tr -d '\r')
echo "Home before the deploy: $HOME_WAS"
grep -v -e '^#' -e '^$' disable.txt | tr -d '\r' | while read -r p; do
  echo "$WAS" | grep -qx "$p" && continue
  S "pm enable $p" | sed 's/^/  /'
done
case "$HOME_WAS" in
  */*) S "pm enable ${HOME_WAS%%/*}; cmd package set-home-activity --user 0 $HOME_WAS" | sed 's/^/  /' ;;
esac
S 'su -c "for s in cyt-play-pin cyt-cast cyt-keys; do for p in \$(pgrep -f service.d/\$s.sh\$); do kill -9 \$p; done; rm -f /data/adb/service.d/\$s.sh; done; rm -rf /data/adb/cyt; pm enable com.android.vending/com.google.android.finsky.recoverymode.safeselfupdate.SafeSelfUpdateService; pm enable com.mylove.setting/.MainActivity; pm enable com.mylove.setting/.ui.activity.WifiSettingActivity"'
U=$(S 'pm list packages -U com.homeprojector.settings' | sed -n 's/.*uid://p')
[ -n "$U" ] && S "su -c \"magisk --sqlite 'DELETE FROM policies WHERE uid=$U'\""
S 'settings delete secure enabled_accessibility_services; for k in window_animation_scale transition_animation_scale animator_duration_scale; do settings put global $k 1.0; done'
S 'pm uninstall com.homeprojector.settings; pm uninstall com.gathin.tvhome' | sed 's/^/  /'
S 'cmd package resolve-activity --brief -a android.intent.action.MAIN -c android.intent.category.HOME | tail -1'
echo "Done. Reboot the projector."

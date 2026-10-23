#!/system/bin/sh
# Runs on the projector as root. Prints one PASS / FAIL line per check, then a total.
D=/data/local/tmp/cyt
ok=0; bad=0
check() { if [ "$2" = 1 ]; then echo "PASS  $1"; ok=$((ok+1)); else echo "FAIL  $1${3:+  ($3)}"; bad=$((bad+1)); fi; }
is() { [ "$1" = "$2" ] && echo 1 || echo 0; }

H=$(cmd package resolve-activity --brief -a android.intent.action.MAIN -c android.intent.category.HOME | tail -1)
check "Home is the new launcher" $(is "$H" com.gathin.tvhome/.MainActivity) "$H"
check "Projector app installed" $(pm list packages | grep -c "com.homeprojector.settings$")
DIS=$(pm list packages -d | sed 's/package://'); INST=$(pm list packages | sed 's/package://')
miss=""
while read -r line; do
  p=$(echo "${line%%#*}" | tr -d ' \r'); [ -z "$p" ] && continue
  echo "$INST" | grep -qx "$p" || continue
  echo "$DIS" | grep -qx "$p" || miss="$miss $p"
done < $D/disable.txt
check "Packages from disable.txt are disabled" $(is "$miss" "") "still on:$miss"
check "Vendor settings enabled (power key menu)" $(pm list packages -e | grep -c "com.mylove.setting$")
check "Power key menu resolves" $(cmd package resolve-activity --brief -n com.mylove.setting/.PowerActivity | tail -1 | grep -c PowerActivity)
check "Vendor settings icon hidden" $(is "$(cmd package query-activities --brief -a android.intent.action.MAIN -c android.intent.category.LAUNCHER | grep -c mylove)" 0)
U=$(pm list packages -U com.homeprojector.settings | sed -n 's/.*uid://p' | tr -d '\r')
check "Magisk root granted to Projector (uid $U)" $(magisk --sqlite "SELECT policy FROM policies WHERE uid=$U" | grep -c "policy=2")
F=$(sed -n 's/^FACTORY=//p' /data/adb/service.d/cyt-play-pin.sh 2>/dev/null)
V=$(pm list packages --show-versioncode com.android.vending | sed -n 's/.*versionCode://p' | tr -d '\r')
check "Google Play at the pinned factory build" $(is "$V" "$F") "now $V, pinned $F"
check "Play self-update service disabled" $(dumpsys package com.android.vending | awk '/disabledComponents:/{f=1;next} /enabledComponents:|^ *User [0-9]+:/{f=0} f' | grep -c SafeSelfUpdateService)
for s in cyt-play-pin cyt-cast cyt-keys cyt-settings-key; do
  check "Magisk script $s present and running" $([ -x /data/adb/service.d/$s.sh ] && [ -n "$(pgrep -f "service.d/$s.sh\$")" ] && echo 1 || echo 0)
done
check "Remote-in-HDMI flag set" $([ -f /data/adb/cyt/keys ] && echo 1 || echo 0)
check "Animations 0.5x" $(is "$(settings get global window_animation_scale)$(settings get global transition_animation_scale)$(settings get global animator_duration_scale)" "0.50.50.5")
check "HDMI-CEC settings on" $(is "$(for k in hdmi_cec_enabled tv_wake_on_one_touch_play tv_send_standby_on_sleep; do cmd hdmi_control cec_setting get $k; done | grep -c '= 1')" 3)
check "No Google Cast receiver" $(is "$(pm list packages | grep -c mediashell)" 0)
echo "TOTAL: $ok passed, $bad failed"

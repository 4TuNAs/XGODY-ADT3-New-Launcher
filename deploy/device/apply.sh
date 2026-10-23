#!/system/bin/sh
# Runs on the projector as root (deploy.sh: su -c sh /data/local/tmp/cyt/device/apply.sh). DRY=1 only prints.
# Everything here is safe to run again: steps that are already done are skipped or repeated with the same result.
D=/data/local/tmp/cyt
DRY=${DRY:-0}
run() { if [ "$DRY" = 1 ]; then echo "  [dry-run] $*"; else "$@" 2>&1 | sed 's/^/  /'; fi; }
say() { echo "--- $*"; }

say "Home: the new launcher"
run cmd package set-home-activity --user 0 com.gathin.tvhome/.MainActivity
run pm grant com.gathin.tvhome android.permission.ACCESS_FINE_LOCATION

say "Disable packages (disable.txt)"
INST=$(pm list packages | sed 's/package://')
DIS=$(pm list packages -d | sed 's/package://')
n=0; m=0
while read -r line; do
  p=$(echo "${line%%#*}" | tr -d ' \r')
  [ -z "$p" ] && continue
  if ! echo "$INST" | grep -qx "$p"; then echo "  $p: not on this projector"; continue; fi
  if echo "$DIS" | grep -qx "$p"; then m=$((m+1)); continue; fi
  run pm disable-user --user 0 "$p"; n=$((n+1))
done < $D/disable.txt
echo "  newly disabled: $n, already disabled: $m"

say "Vendor settings: on, but no icon and no second Wi-Fi screen (the power key menu lives in this app)"
run pm enable com.mylove.setting
run pm disable com.mylove.setting/.MainActivity
run pm disable com.mylove.setting/.ui.activity.WifiSettingActivity

say "Root for the Projector app (Magisk policy by uid)"
U=$(pm list packages -U com.homeprojector.settings | sed -n 's/.*uid://p' | tr -d '\r')
if [ -n "$U" ]; then run magisk --sqlite "REPLACE INTO policies (uid,policy,until,logging,notification) VALUES($U,2,0,1,0)"
else echo "  Projector is not installed"; fi

say "Google Play: back to the factory build and pinned there"
V=$(pm list packages --show-versioncode com.android.vending | sed -n 's/.*versionCode://p' | tr -d '\r')
if dumpsys package com.android.vending | grep -q "codePath=/data/app"; then
  run pm uninstall-system-updates com.android.vending
  V=$(pm list packages --show-versioncode com.android.vending | sed -n 's/.*versionCode://p' | tr -d '\r')
else
  echo "  already the factory build ($V)"
fi
run pm disable com.android.vending/com.google.android.finsky.recoverymode.safeselfupdate.SafeSelfUpdateService
[ "$DRY" = 1 ] || sed -i "s/^FACTORY=.*/FACTORY=$V/" $D/magisk/cyt-play-pin.sh
echo "  pinned versionCode: $V"

say "Magisk boot scripts: Play pin, AirPlay/DLNA, remote in HDMI"
run mkdir -p /data/adb/service.d /data/adb/cyt
for s in cyt-play-pin.sh cyt-cast.sh cyt-keys.sh; do
  run cp $D/magisk/$s /data/adb/service.d/$s
  run chmod 0755 /data/adb/service.d/$s
done
run touch /data/adb/cyt/keys
BB="$(magisk --path)/.magisk/busybox/busybox"
for s in cyt-play-pin.sh cyt-cast.sh cyt-keys.sh; do
  for p in $(pgrep -f "service.d/$s\$"); do run kill -9 $p; done
done
for p in $(pgrep -f "resetprop -w sys.lmk_top_app"); do run kill $p 2>/dev/null; done
if [ "$DRY" != 1 ]; then
  for s in cyt-play-pin.sh cyt-cast.sh cyt-keys.sh; do
    nohup $BB sh /data/adb/service.d/$s >/dev/null 2>&1 &
  done
  sleep 1
fi

say "Settings: faster animations, HDMI-CEC on"
for k in window_animation_scale transition_animation_scale animator_duration_scale; do run settings put global $k 0.5; done
for k in hdmi_cec_enabled tv_wake_on_one_touch_play tv_send_standby_on_sleep; do run cmd hdmi_control cec_setting set $k 1; done

say "Google Cast receiver: not used (no Cast certificate on this model)"
if pm list packages | grep -q com.google.android.apps.mediashell; then run pm uninstall com.google.android.apps.mediashell; else echo "  not installed"; fi
echo APPLY_DONE

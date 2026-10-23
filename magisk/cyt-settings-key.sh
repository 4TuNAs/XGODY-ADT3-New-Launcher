#!/system/bin/sh
# Projector app (2026-10-23): the IR remote's Settings key opens Projector.
# The firmware sends KEYCODE_SETTINGS to the vendor settings screen (com.mylove.setting/.MainActivity). That screen
# is hidden here (the vendor app itself has to stay for the power key menu), so the key did nothing. This script
# watches the IR receiver (sunxi-ir, Linux KEY_SETUP) and opens Projector instead.
# Undo: su -c rm /data/adb/service.d/cyt-settings-key.sh, then reboot.
APP=com.homeprojector.settings/.MainActivity
until [ "$(getprop sys.boot_completed)" = 1 ]; do sleep 5; done
sleep 10
while true; do
  DEV=""
  for d in /sys/class/input/event*; do
    [ "$(cat $d/device/name 2>/dev/null)" = sunxi-ir ] && DEV=/dev/input/${d##*/}
  done
  if [ -z "$DEV" ]; then sleep 60; continue; fi
  getevent -ql "$DEV" | while read -r type code value; do
    [ "$type $code $value" = "EV_KEY KEY_SETUP DOWN" ] && am start -n $APP >/dev/null 2>&1
  done
  sleep 5
done

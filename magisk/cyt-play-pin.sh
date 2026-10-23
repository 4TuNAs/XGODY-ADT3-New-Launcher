#!/system/bin/sh
# cleanyourtv, 2026-10-23: keep Google Play at the factory build 34.1 (versionCode 83410908).
# Play 53.x has no native Cronet for this 32-bit Android 14 and shows "Check your Wi-Fi" for every request.
# Runs at boot from Magisk service.d; every 30 min it removes a Play self-update if one was installed.
# Undo: su -c rm /data/adb/service.d/cyt-play-pin.sh, then reboot.
FACTORY=83410908
LOG=/data/local/tmp/cyt-play-pin.log
until [ "$(getprop sys.boot_completed)" = 1 ]; do sleep 5; done
sleep 60
while true; do
  V=$(pm list packages --show-versioncode com.android.vending | sed -n 's/.*versionCode://p')
  if [ -n "$V" ] && [ "$V" != "$FACTORY" ]; then
    echo "$(date '+%F %T') Play versionCode $V, rolling back to $FACTORY" >> "$LOG"
    pm uninstall-system-updates com.android.vending >> "$LOG" 2>&1
  fi
  sleep 1800
done

#!/system/bin/sh
# Projector app (2026-10-23): keeps AirPlay/DLNA (tranScreen) running when the owner switched it on.
# Flag set by the Projector app: /data/adb/cyt/cast_screen. Google Cast was removed on 23.10 (no Cast certificate).
# Undo: su -c rm /data/adb/service.d/cyt-cast.sh, then reboot.
D=/data/adb/cyt
until [ "$(getprop sys.boot_completed)" = 1 ]; do sleep 5; done
sleep 30
while true; do
  if [ -f $D/cast_screen ] && ! pgrep -f com.android.toofifi:ftdlna >/dev/null; then
    for s in .service.LicenseCheckService .service.PairService .service.PincodeService .service.ExtraService .service.RemoteService; do
      am startservice -n com.android.toofifi/$s >/dev/null 2>&1
    done
    sleep 10
  fi
  sleep 20
done

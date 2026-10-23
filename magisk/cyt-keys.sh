#!/system/bin/sh
# Projector app (2026-10-23): the remote while HDMI plays, when the owner switched it on (flag /data/adb/cyt/keys).
#  1. The firmware wipes the accessibility service list on every focus change (DisplayContent.enableNfxAccessibility,
#     a Netflix hack), so while HDMI (com.softwinner.awlivetv) is in front this puts com.homeprojector.settings/.KeyService
#     back (Back and Home to the box over CEC).
#  2. Input/Source while HDMI is in front: the firmware opens the source picker (com.softwinner.awsource), which jumps
#     straight back into HDMI. This closes the picker and opens the home screen instead, so the button toggles.
# Waits on the focused-app property (sys.lmk_top_app, set by the window manager) instead of polling, except for a
# 1 s check of the service list while HDMI is in front.
# Undo: su -c rm /data/adb/service.d/cyt-keys.sh /data/adb/cyt/keys, then reboot.
D=/data/adb/cyt
S=com.homeprojector.settings/.KeyService
HDMI=com.softwinner.awlivetv
PICKER=com.softwinner.awsource
RP=$(command -v resetprop || echo /debug_ramdisk/resetprop)
until [ "$(getprop sys.boot_completed)" = 1 ]; do sleep 5; done
sleep 10
enable() {
  cur=$(settings get secure enabled_accessibility_services | tr ':' '\n' | grep -v -e '^$' -e '^null$' -e homeprojector | tr '\n' ':')
  settings put secure enabled_accessibility_services "${cur}$S"
  settings put secure accessibility_enabled 1
}
last=""
while true; do
  if [ ! -f $D/keys ]; then sleep 30; continue; fi
  top=$(getprop sys.lmk_top_app)
  if [ "$top" = "$HDMI" ]; then
    case "$(settings get secure enabled_accessibility_services)" in *homeprojector*) ;; *) enable ;; esac
    last=$top
    timeout 1 $RP -w sys.lmk_top_app "$top" >/dev/null 2>&1
    continue
  fi
  if [ "$top" = "$PICKER" ] && [ "$last" = "$HDMI" ]; then
    am force-stop $PICKER
    am start -a android.intent.action.MAIN -c android.intent.category.HOME >/dev/null 2>&1
  fi
  last=$top
  $RP -w sys.lmk_top_app "$top" >/dev/null 2>&1 || sleep 2
done

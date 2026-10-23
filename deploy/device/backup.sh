#!/system/bin/sh
# Read-only snapshot of the projector state before the deploy (packages, settings, Home, a screenshot).
D=/data/local/tmp/cyt-backup
rm -rf $D; mkdir -p $D
pm list packages -f > $D/packages_all.txt
pm list packages -d > $D/packages_disabled.txt
pm list packages -s > $D/packages_system.txt
pm list packages -3 > $D/packages_user.txt
getprop > $D/getprop.txt
for ns in system secure global; do settings list $ns > $D/settings_$ns.txt; done
cmd package query-activities --brief -a android.intent.action.MAIN -c android.intent.category.HOME > $D/home_claimants.txt 2>&1
cmd package resolve-activity --brief -a android.intent.action.MAIN -c android.intent.category.HOME > $D/home_resolved.txt 2>&1
dumpsys activity activities | grep -E 'mFocusedApp|topResumedActivity' > $D/focus.txt
{ wm size; wm density; echo "display_density_forced=$(settings get secure display_density_forced)"; } > $D/display.txt
dumpsys tv_input > $D/tv_input.txt 2>&1
cmd package query-activities --brief -a android.intent.action.MAIN -c android.intent.category.LEANBACK_LAUNCHER > $D/leanback_apps.txt 2>&1
cmd package query-activities --brief -a android.intent.action.MAIN -c android.intent.category.LAUNCHER > $D/launcher_apps.txt 2>&1
{
  echo "service.adb.tcp.port=$(getprop service.adb.tcp.port)"
  echo "persist.adb.tcp.port=$(getprop persist.adb.tcp.port)"
  echo "ro.adb.secure=$(getprop ro.adb.secure)"
  echo "user_setup_complete=$(settings get secure user_setup_complete)"
  echo "device_provisioned=$(settings get global device_provisioned)"
  echo "adb_wifi_enabled=$(settings get global adb_wifi_enabled)"
  echo "sdk=$(getprop ro.build.version.sdk)"
} > $D/state.txt
screencap -p $D/00-stock.png
ls -l $D
echo BACKUP_DONE

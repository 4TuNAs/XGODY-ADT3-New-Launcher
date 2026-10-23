# Firmware notes: XGODY Gimbal 3 Pro / "ADT-3", Allwinner H723, Android 14

What this firmware does differently, found while setting up two units. Paths and names are from builds
`UP1A.231105.001.A1 user.bsw.20260324.082128` and `user.bsw.20260523.060358`.

## The device

| | |
|---|---|
| SoC | Allwinner H723 (`sun50iw15p1`, board `hermes`), 32-bit userspace only (`armeabi-v7a`) |
| RAM | 1 GB, about 400–450 MB free after the cleanup |
| Panel | 1280×720 |
| Fingerprint | `ADT-3/adt3/adt3:14/...`, manufacturer `askey` (Google's ADT-3 developer box) |
| Wi-Fi / BT | SV6621S |
| CEC name | `Gimbal3Pro`, CEC HAL `hdmi_cec.hermes.so` |
| HDMI app | `com.softwinner.awlivetv` (TIF), source picker `com.softwinner.awsource` |
| Vendor settings | `com.mylove.setting` (keystone, projection, picture, power menu) |
| Stock launcher | `com.mylove.ui` |
| Casting | tranScreen `com.android.toofifi` (AirPlay, DLNA), `com.toofifi.miracast` |

## Installing APKs

The package parser refuses an APK signed with the v2 scheme only (`INSTALL_PARSE_FAILED_NO_CERTIFICATES`). Sign with
v1 too:

```gradle
signingConfigs { debug { enableV1Signing true } }
```

## Display: keystone, zoom, projection

The vendor app talks to `com.softwinner.tv.AwTvDisplayManager` (classes shipped inside its APK), which calls the
HIDL service `vendor.display.config IDisplayConfig`:

- `setKeystoreValue(zoomH, zoomV, lbx, lby, rbx, rby, ltx, lty, rtx, rty)`: inward offsets of the four corners in
  panel pixels. The HAL stores them in `persist.display.keystone_*` and applies them at every boot.
- `factorySetPanelValue(E_AW_PANEL_CONFIG_MIRROR, 0..3)` + `persist.sys.projection`: front, rear, ceiling,
  ceiling + rear.
- Zoom in Projector is a uniform inset added to all four corners, so the keystone shape is kept.

The Projector app runs a root helper with `app_process` and `CLASSPATH=<projector.apk>:<com.mylove.setting apk>`,
so it uses the vendor classes without copying them. The vendor app's own version differs between builds
(an updated APK in `/data/app` on one unit, 1.6 in `/system/app/NewPSettings` on the other); both work.

## Picture

`AwTvDisplayManager` + the tv server (`vendor.aw.homlet.tvsystem.tvserver@1.0 ITvServer`):

- modes: `standard, cinema, vivid, game, energy_saving, computer, hdr, custom` (`getPictureModeList`,
  `setPictureModeByName`);
- brightness, contrast, saturation, hue, sharpness: `getBasicControl`, and for storing
  `factorySetBasicControl(E_AW_SOURCE_TYPE_MAX, mode, type, value)`. It only takes values for the mode on screen,
  and `setBasicControl` switches to `custom` by itself. The vendor app edits `custom` only.
- colour temperature (`setColorTemp`: standard, cool, warm), HDMI signal type (`setHDMIVideoPCMode`: auto, video,
  PC).
- Settings are kept per input. Before HDMI has been opened after power-on, the server reports the "no source" set.

## Power key

`PhoneWindowManager.powerPress` → `shutDown()` starts `com.mylove.setting/.PowerActivity`: power off, restart,
screen off, sleep timer (`persist.sys.power_off_time`). With the package disabled the power key does nothing on any
remote (`ActivityNotFoundException` in the log). Keep the package, disable only its launcher entry:

```
su -c pm disable com.mylove.setting/.MainActivity
```

Power-on mode (on with mains power / standby): `ITvServer.factorySetPowerMode(0|1|2)`.

Side effect of hiding the vendor app's launcher entry: the IR remote's **Settings** key (`KEYCODE_SETTINGS`), which
the firmware sends to `com.mylove.setting/.MainActivity`, does nothing. Use Settings in the launcher instead, or
`pm enable` that activity again if you want the key (the vendor app then shows up in the app list).

## Keys the firmware takes before apps

`TvWindowManager.interceptKeyBeforeQueueing` (in `services.jar`):

- `KEYCODE_TV_INPUT` → starts `com.softwinner.awsource/.MainActivity`, which goes straight into HDMI;
- `KEYCODE_MENU` → over media apps (awlivetv, the stock video player, YouTube…) broadcasts
  `com.mylove.DISPLAY_SETTINGS`, the vendor picture panel;
- `KEYCODE_SETTINGS` → `com.mylove.setting/.MainActivity`;
- volume → `com.softwinner.tvvolume` if the custom volume UI is on.

Nothing an app or accessibility service does can stop these. `awlivetv` itself finishes on Back (`onKeyUp`), and
passes other keys to the TIF session.

## Accessibility services do not survive

`DisplayContent.updateFocusedWindowLocked` calls `enableNfxAccessibility(focus is Netflix)` on **every focus
change**. With `false` it writes `accessibility_enabled=0` and `enabled_accessibility_services=""`, so any
accessibility service is switched off as soon as another window takes focus (and turned into
`com.android.nfx/.NfxAccessibilityService` while Netflix is in front). On top of that, Android drops a service that
is not `directBootAware` from the list at boot.

The workaround here is a Magisk script that waits on `sys.lmk_top_app` (set by the same method) and switches the
service back on while the HDMI app is in front.

## HDMI-CEC

- Arrows and OK reach the CEC device through the framework's passthrough. Back (`<User Control Pressed>` Exit) and
  Home (Root Menu) do not, because awlivetv takes them; `IHdmiControlService.sendKeyEvent(0, keycode, pressed)` from
  root sends them to the active source.
- `cmd hdmi_control cec_setting set hdmi_cec_enabled|tv_wake_on_one_touch_play|tv_send_standby_on_sleep 0|1`.
- `dumpsys hdmi_control` lists the CEC message history with ACK / FAIL per message.
- Standby over CEC is `PowerManager.goToSleep`: screen and light off, the SoC stays up, the fan keeps running.

## Google Play

- Self-update to 53.x breaks Play: its `config.armeabi_v7a` split has no `libcronet`, the bundled Cronet would have
  to match Google Play services exactly, and the platform HttpEngine (Cronet 114) is "ineligible". Every request
  fails, Play shows "Check your Wi-Fi".
- `pm uninstall-system-updates com.android.vending` returns to the factory 34.1 (`versionCode 83410908` on both
  builds), which works. Disabling
  `com.android.vending/com.google.android.finsky.recoverymode.safeselfupdate.SafeSelfUpdateService` and a boot
  script that removes any new update keep it there.
- After every rollback Play sets "Auto-update apps" back to "always"; turn it off again in Play.

## Chromecast built-in

Google's receiver (`com.google.android.apps.mediashell`) installs from Play and is visible to phones, but:

- device certificate provisioning (`certificateprovisioning/v1/devicecertificates/create`) answers **403**: Google
  does not issue Cast certificates to this device;
- phones send an auth challenge (`CastV2.Authenticate.Challenge.In`), the receiver can not answer, the phone closes
  the socket;
- without a certificate the receiver also never confirms its foreground service and Android 14 kills it after 30 s
  (`ForegroundServiceDidNotStartInTimeException`).

Root does not change this; the certificate has to be signed by Google. For casting video use DLNA (tranScreen) or
the Web Video Caster Receiver app.

## AirPlay / DLNA without the tranScreen screen

tranScreen's receivers start headless as root:

```
for s in LicenseCheckService PairService PincodeService ExtraService RemoteService; do
  am startservice -n com.android.toofifi/.service.$s
done
```

`am force-stop com.android.toofifi` stops them again (about 220 MB of RAM).

## Other

- Network ADB listens on 5555 from boot (`service.adb.tcp.port=5555`), unencrypted.
- `stay_on_while_plugged_in` and `screen_off_timeout` differed between the two units (one never slept, the other
  after 15 minutes); the deploy script leaves them as they are.
- Firmware OTA: `com.mylove.osota`, `com.softwinner.update`, `com.softwinner.settingsassist`. Re-enable them before
  updating the firmware on purpose.

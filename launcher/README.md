# Launcher

`../apk/tvhome-launcher.apk` is [cleanyourtv](https://github.com/Gathin23/cleanyourtv) (package `com.gathin.tvhome`),
built from upstream commit `34e1c42` with two local changes ([`local-changes.diff`](local-changes.diff)):

- **v1 (JAR) signing** next to v2. This firmware refuses v2-only APKs (`INSTALL_PARSE_FAILED_NO_CERTIFICATES`).
- **[`config.xml`](config.xml)** for this projector:

| Setting | Value |
|---|---|
| `dock_apps` | YouTube, Kyivstar TV, Netflix, Projector |
| `library_apps` | Netflix (the phone build has no TV icon) |
| `source_panel_service` | `com.softwinner.awsource/.MainActivity`: the Inputs button opens HDMI |
| `live_tv_package` | empty: there is no tuner |
| `library_button` | on: app library button in the top bar |

To change the dock, edit `dock_apps` and rebuild.

## Rebuild

```bash
git clone https://github.com/Gathin23/cleanyourtv && cd cleanyourtv
git checkout 34e1c42                       # or a newer commit, then re-check the diff
git apply /path/to/launcher/local-changes.diff
cd launcher
STOCK_LAUNCHER=none TV_HOST=<projector IP> ./build-and-install.sh   # builds, installs, makes it Home
```

Without a projector at hand: `./gradlew assembleRelease` in `launcher/`.
`STOCK_LAUNCHER=none` leaves the stock launcher alone; the deploy script disables `com.mylove.ui` itself.

Licenses: cleanyourtv is MIT ([LICENSE-cleanyourtv.txt](LICENSE-cleanyourtv.txt)); the Outfit font is
SIL OFL 1.1 ([FONT-LICENSE-OFL.txt](FONT-LICENSE-OFL.txt)).

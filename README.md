# AvdBuddy desktop app

Run the following commands from the directory containing this README.

A Kotlin desktop app for managing Android Virtual Devices without opening Android Studio.
Built with Compose Desktop and the default JetBrains Jewel light theme. No Material UI.

The application targets macOS, Windows, and Linux. Install an Android SDK with
command-line tools, emulator, and platform-tools for your host. Running devices
requires the Android Emulator's normal hardware acceleration, runtime libraries, and graphics support
(Windows hypervisor support or Linux KVM as appropriate).

## Run

Run with Compose Hot Reload:

```bash
./gradlew hotRun --auto
```

On Windows, use `.\gradlew.bat hotRun --auto` in PowerShell.

Keep this process running and save Kotlin UI changes. Compose Hot Reload recompiles
and updates the running app while retaining its state. It uses the JBR 25 toolchain;
normal packaged builds (`createDistributable`) do not enable Hot Reload.

Supported application dependencies are managed by KPM in `package.yml`.
Use `kpm list` to inspect them and `kpm i group:artifact:version` to add one.
Desktop platform selection and test-only dependencies remain in Gradle.

The build requires a JDK to start Gradle. The Gradle toolchain resolver provisions
JBR 25, which Jewel requires. Xcode is not required for development.

## Features

- Device sidebar and a large, framed emulator preview
- Select devices without starting them; stopped devices show their icon, name, and a Start button
- Central Play button boots/resumes the selected device; new launches run inside the app without a separate emulator window
- Native live screen streaming with immediate touch/drag input and basic ASCII keyboard input
- Host GPU acceleration for all launches, new devices, and duplicates
- Right-click or use the device menu for duplicate, rename, delete, and Show in Finder / File Explorer / File Manager
- In-app device creation dialog: name, fixed Pixel profile, and Android version
- Google Play Store and services included, with host-native images selected automatically
- Create closes the dialog and shows setup in the device preview, then starts the device automatically
- Download missing Android images during setup, with storage checks, cancellation, and retry
- SDK and emulator storage settings, with SDK validation and unavailable-drive errors
- Running-device detection
- Diagnostics and Nucleus automatic update checks
- Keyboard shortcuts: Cmd on macOS, Ctrl on Windows/Linux; N to create, comma for Settings, Backspace to delete, and left/right arrows to rotate

On macOS, Settings imports SDK and AVD folder preferences from the previous Swift
app on first use. Saved settings and update preferences use native config folders:

| Platform | App settings | Default SDK |
| --- | --- | --- |
| macOS | `~/Library/Application Support/AvdBuddy` | `~/Library/Android/sdk` |
| Windows | `%APPDATA%\AvdBuddy` | `%LOCALAPPDATA%\Android\Sdk` |
| Linux | `$XDG_CONFIG_HOME/AvdBuddy` (default `~/.config/AvdBuddy`) | `~/Android/Sdk` |

SDK discovery checks the saved path, `ANDROID_HOME`, `ANDROID_SDK_ROOT`, then the
platform default. Device storage respects `ANDROID_AVD_HOME`,
`ANDROID_EMULATOR_HOME`, and `ANDROID_USER_HOME`, defaulting to `~/.android/avd`.
SDK, emulator, and ADB locations use host-native paths and accept spaces.

Switching emulator folders does not move existing devices or change Android Studio's
settings. System images remain under the selected SDK's `system-images` folder.
The app creates regular directories and does not create symlinks. Offline linked
storage is reported before an install starts.

For command-line diagnostics, run the packaged app executable with `--diagnostics`.

The selected device's live view uses the emulator's authenticated gRPC screen stream.
Raw RGBA frames go directly to Skia, with stale frames dropped instead of queued.
Touch presses, movement, and releases use the same persistent connection; keyboard
input still uses ADB. No screenshot subprocesses or PNG writes run per live frame.
Only the selected device streams. Multi-touch is not supported.

AvdBuddy launches devices with `-gpu host` and a token-protected gRPC endpoint, and
writes `hw.gpu.enabled=yes` / `hw.gpu.mode=host` for newly created and duplicated
AVDs. Existing devices also use host rendering when launched through AvdBuddy.
Devices started by an older AvdBuddy build need to be stopped and started again.
Externally launched devices require a discovery file with a gRPC port and bearer
token to stream; otherwise the preview asks for a restart through AvdBuddy.

Previews are saved as `.avdbuddy-preview.png` inside each AVD folder when leaving
the live view, and captured again before stopping through AvdBuddy. A previously saved
Quick Boot screenshot is used if available. No screenshot is invented for a device
that has never been captured. The preview image is separate from Android's Quick
Boot state: normal emulator snapshot loading/saving remains enabled.

## Device profiles

Built-in profiles live in `src/main/resources/profiles/*.yml`. The initial catalog
contains Pixel 10 with a 1080 × 2424 display at 420 dpi, 4096 MiB emulator RAM,
and 16 GiB device storage. RAM and storage are fixed emulator defaults. Android
versions are independent of the profile and have no physical-device support cap.
Only stable Google Play images matching the host architecture are offered,
including images that have not been downloaded yet. Equivalent SDK extension
variants share one entry. Previews, Google-APIs-only images, and cross-architecture
fallbacks are excluded from built-in device creation.

To add a device, add its YAML definition and matching `layout`, `back.webp`, and
`mask.webp` under `src/main/resources/device-frames/`, then register its ID in
`HardwareProfiles.all`. Keep display dimensions and frame placement consistent
with the skin layout. The catalog tests validate the bundled artwork and profile.
Custom profile creation/import and automatic installation of a missing Android SDK
are not part of this first creation-dialog implementation; SDK location remains
configurable in Settings.

Pixel frame files are bundled unmodified from the official JetBrains Android
plugin repository at a pinned revision. Their Apache 2.0 license and provenance
are distributed under `src/main/resources/licenses/`. Each created device gets
its own frame files, so SDK device definitions and installed SDK skins are not
required. Existing devices keep their configurations.

## Verification

```bash
./gradlew test
```

Windows equivalent: `.\gradlew.bat test`.

The test command runs only backend tests. They use temporary SDK/AVD fixtures and run
headlessly, without opening windows or clicking through the app.

Use [Spectre](https://spectre.sebastiano.dev/) interactively against the running
development app for UI inspection. The installed Spectre CLI supplies its agent
when attaching; no Spectre dependency is packaged with the app. There are no runnable Compose/Spectre UI tests
and no UI test script. Do not add or run automated UI test suites.

```bash
./gradlew hotRun --auto
# In another terminal, find the AvdBuddy process and attach:
spectre ps
spectre attach <pid>
spectre windows <session-id>
spectre tree <session-id>
# Inspect or interact as needed, then detach:
spectre detach <session-id>
```

Interactive actions operate on the actual running app and its real devices.
The last attach check encountered a Spectre session error; interactive attachment
still needs troubleshooting.

## Packaging

```bash
./gradlew createDistributable
./gradlew packageDmg packageZip
```

The `.app` is in `build/compose/binaries/main/app/`. Nucleus packages the DMG,
ZIP and `latest-mac.yml` update metadata beneath `build/compose/binaries/main/`.
These commands only build local artifacts; they do not publish a GitHub release.

The local installer formats are DMG/ZIP on macOS, NSIS on Windows, and AppImage
on Linux. Signing, notarization, and release publishing are not configured.

The updater retains verified downloads and user-controlled installation, but no
update service is configured yet. Background checks stay inactive; manual checks
explain that automatic updates are not available yet. The new distribution service
will supply the update source.

Inspired by [VirtualBuddy](https://github.com/insidegui/VirtualBuddy).

[MIT](LICENSE) · Alex Styl

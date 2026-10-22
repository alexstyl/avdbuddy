# AvdBuddy

A Kotlin desktop app for managing Android Virtual Devices without opening Android Studio.
Built with Compose Desktop, JetBrains Jewel, and Compose Unstyled. No Material UI.

## Run

On macOS:

```bash
./scripts/runMac
```

This builds a standalone `.app` with a bundled JetBrains Runtime and launches it.
The first build downloads Gradle, dependencies, and JBR 25 if needed.

For UI development with Compose Hot Reload:

```bash
./scripts/runDev
# Equivalent: ./gradlew hotRun --auto
```

Keep this process running and save Kotlin UI changes. Compose Hot Reload recompiles
and updates the running app while retaining its state. It uses the JBR 25 toolchain;
normal packaged builds (`runMac`) do not enable Hot Reload.

Supported application dependencies are managed by KPM in `package.yml`.
Use `kpm list` to inspect them and `kpm i group:artifact:version` to add one.
Desktop platform selection and test-only dependencies remain in Gradle.

The build requires a JDK to start Gradle. The Gradle toolchain resolver provisions
JBR 25, which Jewel requires. Xcode is not required for development.

## Features

- Device shelf with phone, foldable, tablet, Wear OS, desktop, TV, automotive, and XR devices
- Double-click to launch or stop; right-click for duplicate, rename, delete, and Show in Finder
- Guided device creation with Android versions on the left and minor/API releases
  on the right, plus downloads, device profiles,
  RAM, internal storage, SD cards, and device frames
- SDK and emulator storage settings, with SDK validation and unavailable-drive errors
- Running-device detection and Stop all
- Diagnostics and Nucleus automatic update checks
- Keyboard shortcuts: Cmd+N to create, Cmd+, for Settings, Cmd+A to select all,
  and Cmd+Backspace to delete selected devices. Cmd-click toggles selection; Shift-click selects a range

Settings imports the SDK and AVD folder preferences from the previous Swift app on
first use, including an external or custom device location. Settings are saved in
`~/Library/Application Support/AvdBuddy/settings.properties`.

SDK discovery checks the saved path, `ANDROID_SDK_ROOT`, `ANDROID_HOME`, then
`~/Library/Android/sdk`. Device storage defaults to `ANDROID_AVD_HOME`, the Android
user/emulator home override, or `~/.android/avd`.

Switching emulator folders does not move existing devices or change Android Studio's
settings. System images remain under the selected SDK's `system-images` folder.
The app creates regular directories and does not create symlinks. Offline linked
storage is reported before an install starts.

For command-line diagnostics, run the packaged app executable with `--diagnostics`.

## Verification

```bash
./gradlew test
./scripts/verifyUI
```

Backend tests use temporary SDK and AVD fixtures. The UI test uses
[Spectre by Sebastiano Poggi](https://spectre.sebastiano.dev/) to drive real, visible
Jewel/Compose windows: changing storage, selecting a device type and system image,
configuring and creating a device, launching it, confirming/cancelling deletion,
and renaming. It verifies SDK-before-storage ordering and writes screenshots to
`build/reports/spectre/`.

UI tests require an unlocked desktop session. Screenshot capture may require macOS
Screen Recording permission for the Java runtime. Tests never install real Android
images or change your personal device library.

## Packaging

```bash
./gradlew createDistributable
./scripts/releaseMac
```

The `.app` is in `build/compose/binaries/main/app/`. Nucleus packages the DMG,
ZIP and `latest-mac.yml` update metadata beneath `build/compose/binaries/main/`.
These commands only build local artifacts; they do not publish a GitHub release.

Installed direct-download builds check for updates at startup and every 12 hours.
About → Check for updates also checks manually. Users download a checksum-verified
update and choose Restart and install; restart is disabled during device operations.
Automatic checks can be disabled in that window. Development builds skip updates.
Homebrew installations use `brew upgrade --cask avdbuddy` instead.

For a signed, notarized release preparation:

```bash
RELEASE_SIGN_IDENTITY='Developer ID Application: …' \
NOTARY_KEYCHAIN_PROFILE='your-notary-profile' \
SPARKLE_TOOLS_DIR='/path/to/Sparkle/bin' \
./scripts/releaseMac -PsignedRelease=true
```

The Sparkle tools must have access to the existing `avdbuddy` EdDSA signing key in
Keychain. The script creates `build/sparkle/appcast.xml` and `AvdBuddy.dmg` for
existing Swift installations. It preserves the bundle ID, feed URL and public key;
Compose version 1.0.0 uses build 1000, above the previous build 600.
The Compose app itself uses Nucleus, not the Swift Sparkle runtime.

Before publishing separately, verify signing/notarization and test an upgrade from
an installed Swift release and a Compose release on a test machine. A future
`v1.0.0` GitHub release must include the Nucleus ZIP and matching `latest-mac.yml`,
plus the signed Sparkle `AvdBuddy.dmg` and `appcast.xml`. Keep artifact filenames
exactly as referenced by the metadata. Subsequent releases must increment the
app version and macOS build number. Homebrew cask sources remain in
`packaging/homebrew/`. Swift sources and Xcode project files have been removed.

Inspired by [VirtualBuddy](https://github.com/insidegui/VirtualBuddy).

[MIT](LICENSE) · Alex Styl

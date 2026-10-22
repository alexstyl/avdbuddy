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

- Device sidebar and a large, framed emulator preview
- Select devices without starting them; stopped previews show the last captured screen in monochrome
- Central Play button boots/resumes the selected device; new launches run inside the app without a separate emulator window
- Native live screen streaming with immediate touch/drag input and basic ASCII keyboard input
- Host GPU acceleration for all launches, new devices, and duplicates
- Right-click or use the device menu for duplicate, rename, delete, and Show in Finder
- Guided device creation with Android versions on the left and minor/API releases
  on the right, plus downloads, device profiles,
  RAM, internal storage, SD cards, and device frames
- SDK and emulator storage settings, with SDK validation and unavailable-drive errors
- Running-device detection
- Diagnostics and Nucleus automatic update checks
- Keyboard shortcuts: Cmd+N to create, Cmd+, for Settings, and Cmd+Backspace to delete the selected device

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

## Verification

```bash
./gradlew test
```

Only backend tests run automatically. They use temporary SDK/AVD fixtures and run
headlessly, without opening windows or clicking through the app.

Use [Spectre](https://spectre.sebastiano.dev/) interactively against the running
development app for UI inspection. The installed Spectre CLI supplies its agent
when attaching; no Spectre dependency is packaged with the app. There are no runnable Compose/Spectre UI tests
and no UI test script. Do not add or run automated UI test suites.

```bash
./scripts/runDev
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

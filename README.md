# AvdBuddy

![AvdBuddy](./avdbuddy.jpg)

AvdBuddy is a native macOS app for managing Android Virtual Devices without going through Android Studio.

It focuses on the common emulator workflow:
- browse your existing AVDs from a visual home screen
- launch an emulator with a double click
- create new AVDs through a guided wizard
- duplicate, rename, and delete AVDs
- download Android system images from Google when needed

## What It Does

AvdBuddy reads the Android SDK and local AVD setup on your Mac, then gives you a faster UI for:
- viewing all AVDs in one place
- distinguishing them visually with stable per-device gradients
- creating phones, tablets, foldables, TVs, and Wear OS emulators
- selecting Android versions, variants, architecture, storage, RAM, SD card, and Google Play services options

## Requirements

AvdBuddy expects a working Android emulator toolchain on the Mac:
- Android SDK command-line tools
- `avdmanager`
- `sdkmanager`
- Android Emulator
- `adb`

It looks for the SDK in:
- `ANDROID_SDK_ROOT`
- `ANDROID_HOME`
- `~/Library/Android/sdk`

It reads AVDs from:
- `~/.android/avd`

## License

This project is licensed under the MIT License. See [LICENSE](./LICENSE).

## Development

Run the test suite:

```bash
swift test
```

Run the app:

```bash
./scripts/runMac
```

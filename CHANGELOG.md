# Changelog

All notable changes to this project will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.0.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [Unreleased]

## [0.4.0] - 2026-03-02

### Added
- `Foldable` is now its own separate form factor.
- Added device frame support when creating new emulators.
- You can now delete emulators pressing `CMD + Delete`

### Changed
- Disabled autoupdates when app is installed via Brew.

## [0.3.0] - 2026-03-01

### Added
- Added in-app update checks powered by Sparkle, using GitHub Releases for macOS update delivery.
- Added `Check for Updates` actions in the app menu and the info window.

## [0.2.0] - 2026-03-1

### Added
- Added Android SDK selection and setup flows so the app can detect, validate, override, and persist the SDK path from the UI.
- Added broader emulator form factor support across creation and classification, including tablet, Wear OS, TV, desktop, automotive, XR, and foldable-aware device handling.
- Added an info window with app details and social links.

### Changed
- Improved home screen AVD interactions with system-accent card selection, Command-click multi-select, Command-A select all, background click deselection, and double-click launch.
- Removed the home screen status toast for a cleaner main window.

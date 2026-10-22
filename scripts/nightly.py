#!/usr/bin/env python3
"""Prepare and verify nightly installers without starting any graphical application."""
import argparse
import hashlib
import json
import os
from pathlib import Path
import shutil
import subprocess
import sys
import zipfile

FORMATS = {"macos": {".dmg", ".zip"}, "windows": {".exe", ".zip"}, "linux": {".deb", ".AppImage", ".zip"}}


def installer_files(root, platform):
    files = sorted(p for p in Path(root).glob("*/*")
                   if p.is_file() and p.name.startswith("AvdBuddy-Nightly-") and p.suffix in FORMATS[platform])
    found = {p.suffix for p in files}
    if found != FORMATS[platform]:
        raise ValueError(f"Missing installers for {platform}: {FORMATS[platform] - found}")
    if len(files) != len(found):
        raise ValueError("Ambiguous installer outputs; use a clean build directory")
    return files


def verify_runtime(root):
    root = Path(root)
    jars = list(root.rglob("*.jar"))
    forbidden = [p for p in jars if any(name in p.name.lower() for name in ("spectre", "hot-reload"))]
    if forbidden:
        raise ValueError(f"Development tooling in package: {forbidden}")
    identities = []
    for jar in jars:
        with zipfile.ZipFile(jar) as archive:
            if "avdbuddy-build.properties" in archive.namelist():
                identities.append((jar, archive.read("avdbuddy-build.properties").decode("utf-8")))
    if len(identities) != 1 or "channel=nightly" not in identities[0][1]:
        raise ValueError("Packaged application must contain exactly one nightly build identity")
    # Run the shipped launcher with its bundled JVM. Main's diagnostics path
    # returns before Compose starts; no windows, emulator boot, or UI driver.
    name = "AvdBuddy-Nightly.exe" if sys.platform == "win32" else "AvdBuddy-Nightly" if sys.platform == "darwin" else "avdbuddy-nightly"
    launchers = [p for p in root.rglob(name) if p.is_file()]
    if len(launchers) != 1:
        raise ValueError(f"Expected one packaged launcher, found {launchers}")
    environment = dict(os.environ)
    environment["JAVA_TOOL_OPTIONS"] = (environment.get("JAVA_TOOL_OPTIONS", "") + " -Djava.awt.headless=true").strip()
    result = subprocess.run([str(launchers[0].resolve()), "--diagnostics"],
                            env=environment, text=True, capture_output=True, timeout=60, check=True)
    print(result.stdout)
    if "Channel: nightly" not in result.stdout or "Self-updates: disabled" not in result.stdout:
        raise ValueError("Packaged runtime does not identify itself as an update-disabled nightly")
    for plist in root.rglob("Info.plist"):
        text = plist.read_text()
        if "SUFeedURL" in text or "SUPublicEDKey" in text:
            raise ValueError("Nightly package contains a stable Sparkle update feed")


def collect(root, platform, destination):
    destination = Path(destination)
    destination.mkdir(parents=True, exist_ok=True)
    for file in installer_files(root, platform):
        shutil.copy2(file, destination / file.name)
        print(file.name)


def release_assets(directory):
    files = sorted(Path(directory).iterdir())
    allowed = set.union(*FORMATS.values())
    if not files or any(not p.is_file() or not p.name.startswith("AvdBuddy-Nightly-") or p.suffix not in allowed for p in files):
        raise ValueError("Release assets must contain only nightly installers; update feeds are forbidden")
    # Require both macOS architectures and all three desktop operating systems.
    expected = {("mac", "arm64", ".dmg"), ("mac", "x64", ".dmg"),
                ("win", "x64", ".exe"), ("linux", "x64", ".AppImage"), ("linux", "x64", ".deb")}
    for platform, arch, extension in expected:
        if not any(f"-{platform}-{arch}" in p.name and p.suffix == extension for p in files):
            raise ValueError(f"Missing {platform}/{arch} {extension} installer")
    return files


def publish(directory, tag, commit, repo):
    if not tag.startswith("nightly-"):
        raise ValueError("Nightly publishing requires a nightly-* tag")
    assets = release_assets(directory)
    sums = Path(directory) / "SHA256SUMS.txt"
    sums.write_text("".join(f"{hashlib.sha256(p.read_bytes()).hexdigest()}  {p.name}\n" for p in assets))
    notes = Path(directory).parent / "nightly-notes.md"
    notes.write_text(f"""Nightly build from `{commit}`. Experimental, manually installed builds with bundled Java.

- macOS: download the DMG for Apple Silicon (`arm64`) or Intel (`x64`) and drag AvdBuddy-Nightly to Applications.
- Windows x64: run the `.exe` installer. ZIP archives are also available.
- Linux x64: install the `.deb`, or make the `.AppImage` executable and run it (FUSE may be required).

These builds are unsigned; macOS Gatekeeper or Windows SmartScreen may require manual approval.
Install a host Android SDK with command-line tools, emulator, and platform-tools separately.

Nightlies use a separate app/install identity and preferences. They use your regular Android devices, so device changes are shared with stable and Android Studio.
Nightlies never check for or install updates. Download a newer nightly manually.
This prerelease is not GitHub's Latest release and includes no stable update feeds.

SHA-256 checksums are in `SHA256SUMS.txt`. Tests and headless packaged-runtime checks passed on every build host; graphical operation and accelerated emulator boot are not automated.
""")
    subprocess.run(["gh", "release", "create", tag, "--repo", repo, "--target", commit,
                    "--title", f"AvdBuddy Nightly {tag.removeprefix('nightly-')}",
                    "--draft", "--prerelease", "--latest=false", "--notes-file", str(notes),
                    *map(str, assets), str(sums)], check=True)
    subprocess.run(["gh", "release", "edit", tag, "--repo", repo,
                    "--draft=false", "--prerelease", "--latest=false"], check=True)
    published = json.loads(subprocess.check_output(["gh", "api", f"repos/{repo}/releases/tags/{tag}"]))
    if not published["prerelease"] or published["draft"]:
        raise ValueError("Nightly release was not published as a prerelease")
    latest = json.loads(subprocess.check_output(["gh", "api", f"repos/{repo}/releases/latest"]))
    if latest["tag_name"] == tag:
        raise ValueError("Nightly incorrectly became the stable Latest release")
    print(published["html_url"])


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__)
    sub = parser.add_subparsers(dest="command", required=True)
    verify = sub.add_parser("verify")
    verify.add_argument("root")
    gather = sub.add_parser("collect")
    gather.add_argument("root")
    gather.add_argument("platform", choices=FORMATS)
    gather.add_argument("destination")
    release = sub.add_parser("publish")
    release.add_argument("directory")
    release.add_argument("tag")
    release.add_argument("commit")
    release.add_argument("repo")
    args = parser.parse_args()
    if args.command == "verify": verify_runtime(args.root)
    elif args.command == "collect": collect(args.root, args.platform, args.destination)
    elif args.command == "publish": publish(args.directory, args.tag, args.commit, args.repo)

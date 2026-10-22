import importlib.util
from pathlib import Path
import tempfile
import unittest

spec = importlib.util.spec_from_file_location("nightly", Path(__file__).parents[1] / "nightly.py")
nightly = importlib.util.module_from_spec(spec)
spec.loader.exec_module(nightly)


class NightlyArtifactsTest(unittest.TestCase):
    def test_collect_ignores_update_feeds_and_working_app_copies(self):
        with tempfile.TemporaryDirectory() as tmp:
            root = Path(tmp)
            for folder, name in [("dmg", "AvdBuddy-Nightly-test-mac-arm64.dmg"),
                                 ("zip", "AvdBuddy-Nightly-test-mac-arm64.zip"),
                                 ("dmg", "latest-mac.yml"), ("zip", "appcast.xml")]:
                (root / folder).mkdir(exist_ok=True)
                (root / folder / name).write_text("fixture")
            self.assertEqual({".dmg"}, {p.suffix for p in nightly.installer_files(root, "macos")})
            (root / "dmg" / "AvdBuddy-Nightly-old-mac-arm64.dmg").touch()
            with self.assertRaises(ValueError): nightly.installer_files(root, "macos")

    def test_publish_requires_all_platforms_and_rejects_stable_update_metadata(self):
        with tempfile.TemporaryDirectory() as tmp:
            root = Path(tmp)
            for target in ["mac-arm64.dmg", "mac-x64.dmg", "win-x64.exe", "linux-x64.AppImage"]:
                (root / f"AvdBuddy-Nightly-test-{target}").touch()
            self.assertEqual(4, len(nightly.release_assets(root)))
            (root / "latest-mac.yml").touch()
            with self.assertRaises(ValueError): nightly.release_assets(root)
            (root / "latest-mac.yml").unlink()
            (root / "AvdBuddy-Nightly-test-win-x64.exe").unlink()
            with self.assertRaises(ValueError): nightly.release_assets(root)

    def test_publish_rejects_extra_or_duplicate_installers(self):
        with tempfile.TemporaryDirectory() as tmp:
            root = Path(tmp)
            targets = ["mac-arm64.dmg", "mac-x64.dmg", "win-x64-nsis.exe", "linux-x64.AppImage"]
            for target in targets:
                (root / f"AvdBuddy-Nightly-test-{target}").touch()
            for extra in ["mac-arm64.zip", "linux-x64.deb", "mac-arm64.dmg"]:
                with self.subTest(extra=extra):
                    file = root / f"AvdBuddy-Nightly-extra-{extra}"
                    file.touch()
                    with self.assertRaises(ValueError): nightly.release_assets(root)
                    file.unlink()
            # Four files still fail if two target the same architecture.
            (root / "AvdBuddy-Nightly-test-mac-x64.dmg").unlink()
            (root / "AvdBuddy-Nightly-duplicate-mac-arm64.dmg").touch()
            with self.assertRaises(ValueError): nightly.release_assets(root)

    def test_release_accepts_actual_ci_filenames_and_x64_architecture_aliases(self):
        with tempfile.TemporaryDirectory() as tmp:
            root = Path(tmp)
            for name in ["AvdBuddy-Nightly-nightly.4050744-mac-arm64.dmg",
                         "AvdBuddy-Nightly-nightly.4050744-mac-x64.dmg",
                         "AvdBuddy-Nightly-nightly.4050744-win-x64-nsis.exe"]:
                (root / name).touch()
            for arch in ["x86_64", "x64", "amd64"]:
                with self.subTest(arch=arch):
                    appimage = root / f"AvdBuddy-Nightly-nightly.4050744-linux-{arch}.AppImage"
                    appimage.touch()
                    self.assertEqual(4, len(nightly.release_assets(root)))
                    appimage.unlink()

    def test_publisher_cannot_use_a_stable_tag(self):
        with self.assertRaises(ValueError): nightly.publish("unused", "v1.0.0", "commit", "owner/repo")


if __name__ == "__main__": unittest.main()

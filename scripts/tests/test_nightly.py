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
            self.assertEqual({".dmg", ".zip"}, {p.suffix for p in nightly.installer_files(root, "macos")})
            (root / "zip" / "AvdBuddy-Nightly-old-mac-arm64.zip").touch()
            with self.assertRaises(ValueError): nightly.installer_files(root, "macos")

    def test_publish_requires_all_platforms_and_rejects_stable_update_metadata(self):
        with tempfile.TemporaryDirectory() as tmp:
            root = Path(tmp)
            for target in ["mac-arm64.dmg", "mac-x64.dmg", "win-x64.exe", "linux-x64.AppImage", "linux-x64.deb"]:
                (root / f"AvdBuddy-Nightly-test-{target}").touch()
            self.assertEqual(5, len(nightly.release_assets(root)))
            (root / "latest-mac.yml").touch()
            with self.assertRaises(ValueError): nightly.release_assets(root)
            (root / "latest-mac.yml").unlink()
            (root / "AvdBuddy-Nightly-test-win-x64.exe").unlink()
            with self.assertRaises(ValueError): nightly.release_assets(root)

    def test_publisher_cannot_use_a_stable_tag(self):
        with self.assertRaises(ValueError): nightly.publish("unused", "v1.0.0", "commit", "owner/repo")


if __name__ == "__main__": unittest.main()

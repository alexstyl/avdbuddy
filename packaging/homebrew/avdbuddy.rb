cask "avdbuddy" do
  version "0.4.1"
  sha256 "b1b2237a02951ebb03f2a20adce807ace29d8db737b7c7194e753ebf06dda241"

  url "https://github.com/alexstyl/avdbuddy/releases/download/#{version}/AvdBuddy-#{version}.dmg"
  name "AvdBuddy"
  desc "Native macOS app for managing Android Virtual Devices"
  homepage "https://github.com/alexstyl/avdbuddy"

  auto_updates true
  app "AvdBuddy.app"

  zap trash: [
    "~/Library/Application Support/AvdBuddy",
    "~/Library/Preferences/com.alexstyl.avdbuddy.plist",
    "~/Library/Saved Application State/com.alexstyl.avdbuddy.savedState",
  ]
end

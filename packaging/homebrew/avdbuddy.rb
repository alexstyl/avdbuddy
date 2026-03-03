cask "avdbuddy" do
  version "0.4.1"
  sha256 "8178cc5848f8d00ff67e8d1232e582b3707f607498bf8ee002071df4f08f7a0d"

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

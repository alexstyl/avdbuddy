pluginManagement { repositories { gradlePluginPortal(); google(); mavenCentral() } }
plugins { id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0" }
dependencyResolutionManagement { repositories { google(); mavenCentral(); maven("https://www.jetbrains.com/intellij-repository/releases") } }
rootProject.name = "AvdBuddy"

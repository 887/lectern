pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}

plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
}

rootProject.name = "whisperboy"
include(":app")
// cold-start-perf F.1 — sibling com.android.test module that hosts the
// macrobenchmark recording the cold-boot path. See baselineprofile/README
// of intent in baselineprofile/build.gradle.kts.
include(":baselineprofile")

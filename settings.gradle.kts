pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}

plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
        // tesseract4android publishes only to JitPack. The filter means JitPack can
        // serve exactly that one group and nothing else — it never gets a chance to
        // shadow an artifact that should come from Central or Google.
        //
        // F-Droid disallows JitPack as a dependency source; the fdroiddata recipe will
        // build Tesseract4Android from source instead (its Gradle build publishes to
        // mavenLocal under these same coordinates, which is the documented route).
        maven {
            url = uri("https://jitpack.io")
            content {
                includeGroup("cz.adaptech.tesseract4android")
            }
        }
    }
}

rootProject.name = "Tickbox"
include(":app")

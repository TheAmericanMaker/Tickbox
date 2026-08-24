pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}

// No toolchain resolver on purpose. The foojay convention plugin downloads a JDK from the
// network during configuration, which F-Droid's scanner rejects outright — it flags
// `org.gradle.toolchains.foojay-resolver` as a "usual suspect" and refuses to build, because a
// build that fetches its own toolchain is not reproducible.
//
// `jvmToolchain(17)` in app/build.gradle.kts still applies; Gradle resolves it from a locally
// installed JDK 17 and fails with a clear message if none is present. That requirement is
// already documented in CLAUDE.md, and it was never really optional: the plugin could not
// rescue the Java 25 problem either, because the JVM running the Gradle daemon is the one that
// has to be old enough.

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
        // tesseract4android publishes only to JitPack. The filter means JitPack can
        // serve exactly that one group and nothing else — it never gets a chance to
        // shadow an artifact that should come from Central or Google.
        //
        // F-Droid accepts this: its inclusion policy lists JitPack.io as a trusted Maven
        // repository, so the fdroiddata recipe can resolve the dependency normally and does
        // not need to build Tesseract4Android from source.
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

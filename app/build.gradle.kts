import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
    alias(libs.plugins.ktlint)
}

// Release signing resolves from keystore.properties (local, gitignored) or environment
// variables (CI), and tolerates having neither: a contributor with no key — and
// F-Droid's build server, which never has one — gets an unsigned release APK instead of
// a broken build.
val keystoreProperties = Properties().apply {
    val file = rootProject.file("keystore.properties")
    if (file.exists()) file.inputStream().use { load(it) }
}

fun signingValue(property: String, environmentVariable: String): String? =
    keystoreProperties.getProperty(property)
        ?: System.getenv(environmentVariable)?.takeIf { it.isNotBlank() }

val releaseStoreFile: File? = signingValue("storeFile", "KEYSTORE_FILE")
    ?.let { rootProject.file(it) }
    ?.takeIf { it.exists() }

android {
    namespace = "com.theamericanmaker.tickbox"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.theamericanmaker.tickbox"
        minSdk = 26
        targetSdk = 35
        versionCode = 1
        versionName = "1.0.0"

        ksp {
            arg("room.schemaLocation", "$projectDir/schemas")
        }
    }

    // Two ways to ship the same app. `withOcr` is Tickbox as it has been; `noOcr` drops
    // Tesseract, Leptonica and the language model, which is ~31 MB of a ~31.5 MB download —
    // the app's own code is 2.5 MB of dex. See #31.
    //
    // A runtime download instead of a build variant is not possible: Android 10 forbids
    // executing code from the app's data directory, so native libraries have to arrive
    // through the package manager. The model alone is downloadable but is only 3.9 MB of it,
    // and fetching it would cost the app its "no network permission" claim.
    flavorDimensions += "ocr"
    productFlavors {
        create("withOcr") { dimension = "ocr" }
        create("noOcr") { dimension = "ocr" }
    }

    signingConfigs {
        create("release") {
            if (releaseStoreFile != null) {
                storeFile = releaseStoreFile
                storePassword = signingValue("storePassword", "KEYSTORE_PASSWORD")
                keyAlias = signingValue("keyAlias", "KEY_ALIAS")
                keyPassword = signingValue("keyPassword", "KEY_PASSWORD")
            }
        }
    }

    buildTypes {
        debug {
            // Lets a debug build sit alongside a release install, which is how the
            // migration from Smart Toolkit gets validated without risking real data.
            applicationIdSuffix = ".debug"
            versionNameSuffix = "-debug"
        }
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
            // Null means "no keystore available" and produces an unsigned APK.
            signingConfig = if (releaseStoreFile != null) signingConfigs.getByName("release") else null
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    buildFeatures {
        compose = true
    }
    testOptions {
        unitTests.isIncludeAndroidResources = true
    }
}

kotlin {
    jvmToolchain(17)
}

dependencies {
    implementation(libs.core.ktx)
    implementation(libs.lifecycle.runtime.ktx)
    implementation(libs.lifecycle.runtime.compose)
    implementation(libs.lifecycle.viewmodel.compose)
    implementation(libs.activity.compose)

    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.ui.graphics)
    implementation(libs.compose.ui.tooling.preview)
    implementation(libs.compose.material3)
    implementation(libs.compose.material.icons.extended)
    debugImplementation(libs.compose.ui.tooling)

    implementation(libs.navigation.compose)

    implementation(libs.room.runtime)
    implementation(libs.room.ktx)
    ksp(libs.room.compiler)

    implementation(libs.datastore.preferences)

    implementation(libs.coroutines.android)

    // OCR, in the withOcr flavour only. Apache-2.0 wrapper around Tesseract 5 + Leptonica;
    // ~27 MB of native libs across the four ABIs, plus the 4 MB English model in
    // src/withOcr/assets. R8 cannot shrink any of it — it shrinks bytecode, not native code.
    "withOcrImplementation"(libs.tesseract4android)

    implementation(libs.reorderable)

    testImplementation(libs.junit)
    testImplementation(libs.coroutines.test)
    testImplementation(libs.turbine)
    testImplementation(libs.robolectric)
    testImplementation(libs.androidx.test.core)
    testImplementation(libs.androidx.test.junit)
    testImplementation(libs.room.testing)
}

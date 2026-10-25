plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "app.limbo.keyboard"
    compileSdk = 34

    defaultConfig {
        applicationId = "app.limbo.keyboard"
        minSdk = 26
        targetSdk = 34
        versionCode = 2
        versionName = "1.0.0"
        resourceConfigurations += listOf("en", "ru")
    }

    // Release signing comes from the environment when publishing, and falls back to the throwaway
    // key in toolchain/.keys so a local `assembleRelease` still produces something installable.
    // Never commit a real keystore: see docs/RELEASE.md.
    val releaseKeystore = file(
        System.getenv("LIMBO_KEYSTORE") ?: "$rootDir/toolchain/.keys/limbo-release.jks"
    )

    signingConfigs {
        create("release") {
            if (releaseKeystore.exists()) {
                storeFile = releaseKeystore
                storePassword = System.getenv("LIMBO_STORE_PASSWORD") ?: "limbo-keyboard"
                keyAlias = System.getenv("LIMBO_KEY_ALIAS") ?: "limbo"
                keyPassword = System.getenv("LIMBO_KEY_PASSWORD") ?: "limbo-keyboard"
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            // Without a keystore we still want a byte-for-byte installable build for smoke tests.
            signingConfig = if (releaseKeystore.exists()) {
                signingConfigs.getByName("release")
            } else {
                signingConfigs.getByName("debug")
            }
        }
        debug {
            applicationIdSuffix = ".debug"
        }
    }

    /**
     * Size splits.
     *
     * A keyboard has no native code, so per-ABI APKs would be byte-identical copies of each
     * other; the universal APK is the only honest artefact today. The block stays here, off,
     * because the moment an .so is added (a gesture engine, a native optimiser) `./gradlew
     * assembleRelease` should start producing real ABI variants without anyone editing this file.
     *
     * Enable by setting LIMBO_ABI_SPLITS=1, or ship an App Bundle (`./gradlew bundleRelease`)
     * and let Play/deliver generate the splits.
     */
    splits {
        abi {
            isEnable = System.getenv("LIMBO_ABI_SPLITS") == "1"
            reset()
            include("armeabi-v7a", "arm64-v8a", "x86", "x86_64")
            isUniversalApk = true
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    buildFeatures {
        buildConfig = false
    }

    lint {
        abortOnError = false
    }
}

// The project intentionally has no runtime dependencies: the whole interface is
// drawn on device, so the app stays small and starts instantly.
dependencies {
    implementation(kotlin("stdlib"))
}

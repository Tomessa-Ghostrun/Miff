import com.google.firebase.crashlytics.buildtools.gradle.CrashlyticsExtension

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.google.services)
    alias(libs.plugins.firebase.crashlytics)
}

android {
    namespace = "com.tomessa.miff"
    // compileSdk 37 (not 36): the current AndroidX releases (Compose 1.12, Navigation 2.10, Core 1.19) require it.
    // compileSdk only unlocks APIs at build time; runtime behavior is set by targetSdk, kept at 36 (Google Play requirement).
    compileSdk = 37

    defaultConfig {
        applicationId = "com.tomessa.miff"
        minSdk = 26
        targetSdk = 36
        versionCode = 1
        versionName = "0.1.0"
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    androidResources {
        // Generates res/xml/locales_config.xml from the res/values-* folders (per-app language, Android 13+).
        // The unqualified res/values folder is English (see res/resources.properties): English is the fallback.
        generateLocaleConfig = true
    }

    buildTypes {
        debug {
            // en-XA (long accented text) and ar-XB (right-to-left) pseudo-locales, to test layouts without translations.
            isPseudoLocalesEnabled = true
        }
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            isDebuggable = false
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            // Unsigned on purpose: no signing key exists yet (never commit one).
            // No automatic upload of the R8 mapping to Firebase from a developer machine: a build must never write to
            // the Firebase project. CI will enable it (-Pmiff.uploadCrashlyticsMapping=true) once release builds are shared.
            (this as ExtensionAware).extensions.configure<CrashlyticsExtension>("firebaseCrashlytics") {
                mappingFileUploadEnabled =
                    providers.gradleProperty("miff.uploadCrashlyticsMapping").map { it == "true" }.getOrElse(false)
            }
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    lint {
        abortOnError = true
        checkReleaseBuilds = true
        // targetSdk stays at 36 on purpose (Google Play requirement); moving to 37 is a separate, tested decision.
        disable += "OldTargetApi"
        error += setOf("HardcodedText", "MissingTranslation", "ExtraTranslation", "RtlHardcoded")
    }

    testOptions {
        unitTests.isReturnDefaultValues = true
    }
}

// Release keeps only the supported languages from libraries (smaller APK; any other device language falls back to
// English). Debug keeps everything so that the en-XA / ar-XB pseudo-locales work.
androidComponents {
    onVariants(selector().withBuildType("release")) { variant ->
        variant.androidResources.localeFilters.addAll("en", "fr")
    }
}

dependencies {
    implementation(platform(libs.androidx.compose.bom))
    implementation(platform(libs.firebase.bom))

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.kotlinx.coroutines.play.services)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.foundation)
    implementation(libs.androidx.compose.material3)

    implementation(libs.firebase.firestore)
    implementation(libs.firebase.auth)
    implementation(libs.firebase.crashlytics)
    implementation(libs.firebase.analytics)
    // App Check: Play Integrity in release, debug provider in debug (never shipped in release).
    releaseImplementation(libs.firebase.appcheck.playintegrity)
    debugImplementation(libs.firebase.appcheck.debug)

    debugImplementation(libs.androidx.compose.ui.tooling)
    debugImplementation(libs.androidx.compose.ui.tooling.preview)

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
}

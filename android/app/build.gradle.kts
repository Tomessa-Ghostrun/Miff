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

        // Single place for the public pages of the legal texts (AUTH-W, not deployed yet) and for the provisional
        // contact (ticket decision N2: replace by a dedicated address before the external beta).
        // Override without editing the code: -Pmiff.legalBaseUrl=... and -Pmiff.supportEmail=...
        // A "/" at the end is removed here too (the app also copes with it, see legalPageUrl).
        val legalBaseUrl =
            providers.gradleProperty("miff.legalBaseUrl").getOrElse("https://miff-3d283.web.app").trimEnd('/')
        val supportEmail = providers.gradleProperty("miff.supportEmail").getOrElse("tomessa.mfouka@gmail.com")
        require(legalBaseUrl.startsWith("https://")) { "miff.legalBaseUrl must be an https:// address (no clear text)" }
        buildConfigField("String", "LEGAL_BASE_URL", "\"$legalBaseUrl\"")
        buildConfigField("String", "SUPPORT_EMAIL", "\"$supportEmail\"")
        // LOC-1 (places). Providers of the map and of the address suggestions: all free, none needs a key (decision of the ticket,
        // section 12). Override without editing the code: -Pmiff.mapStyleUrl=..., -Pmiff.photonUrl=... (https only).
        // The optional fallback of the suggestions (Geoapify, free plan) is OFF unless a key is given at build time with
        // -Pmiff.geoapifyKey=... (in your own ~/.gradle/gradle.properties): NO key is ever committed. A key inside an app can be
        // extracted: turning the fallback on in a published build is a decision of the PM (see docs/dev/lieux-fournisseurs-gratuits.md).
        val mapStyleUrl =
            providers.gradleProperty("miff.mapStyleUrl").getOrElse("https://tiles.openfreemap.org/styles/positron")
        val photonUrl = providers.gradleProperty("miff.photonUrl").getOrElse("https://photon.komoot.io/api/")
        val geoapifyKey = providers.gradleProperty("miff.geoapifyKey").getOrElse("")
        require(mapStyleUrl.startsWith("https://")) { "miff.mapStyleUrl must be an https:// address (no clear text)" }
        require(photonUrl.startsWith("https://")) { "miff.photonUrl must be an https:// address (no clear text)" }
        require(Regex("[A-Za-z0-9]*").matches(geoapifyKey)) { "miff.geoapifyKey must only hold letters and digits" }
        buildConfigField("String", "PLACE_MAP_STYLE_URL", "\"$mapStyleUrl\"")
        buildConfigField("String", "PHOTON_URL", "\"$photonUrl\"")
        buildConfigField("String", "GEOAPIFY_KEY", "\"$geoapifyKey\"")
        // Durations of the launch (journal line + readout on screen), for the PM. Only the debug and releaseLike builds turn it on.
        buildConfigField("boolean", "STARTUP_METRICS", "false")
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
            buildConfigField("boolean", "STARTUP_METRICS", "true")
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
        // "releaseLike": the RELEASE build (same code, same R8 minification, not debuggable, same release sources: no diagnostic
        // screen, Play Integrity App Check) but signed with the DEBUG key, so that the PM can install it on a phone and compare
        // the launch time with the debug APK. It is a test build: never published, never uploaded anywhere. The debug key is
        // the standard AGP one (on CI, the stable keystore read from a secret by the workflow; nothing secret is in the repository).
        // It adds the launch-duration readout (STARTUP_METRICS), which the real release does not contain.
        create("releaseLike") {
            initWith(getByName("release"))
            matchingFallbacks += "release"
            isDebuggable = false
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            signingConfig = signingConfigs.getByName("debug")
            buildConfigField("boolean", "STARTUP_METRICS", "true")
            (this as ExtensionAware).extensions.configure<CrashlyticsExtension>("firebaseCrashlytics") {
                mappingFileUploadEnabled = false
            }
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    // releaseLike is a release build: it uses the release sources (no diagnostic screen, Play Integrity App Check), not a copy.
    sourceSets {
        getByName("releaseLike") {
            java.directories.add("src/release/java")
            kotlin.directories.add("src/release/java")
        }
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
        // Screen tests (Robolectric) need the app resources: they run in testDebugUnitTest only.
        unitTests.isIncludeAndroidResources = true
    }
}

// Release keeps only the supported languages from libraries (smaller APK; any other device language falls back to
// English). Debug keeps everything so that the en-XA / ar-XB pseudo-locales work.
androidComponents {
    for (buildType in listOf("release", "releaseLike")) {
        onVariants(selector().withBuildType(buildType)) { variant ->
            variant.androidResources.localeFilters.addAll("en", "fr")
        }
    }
}

dependencies {
    implementation(platform(libs.androidx.compose.bom))
    implementation(platform(libs.firebase.bom))

    implementation(libs.androidx.core.ktx)
    // Splash screen API (Android 12+) with a back-port down to minSdk 26: the system launch screen shows the blue
    // background and the "M + i" without the heart; the Compose animation takes over from there.
    implementation(libs.androidx.core.splashscreen)
    implementation(libs.androidx.activity.compose)
    // Baseline Profile (app/src/main/baseline-prof.txt): lets the system pre-compile the start-up path of the app at install time.
    // Official AndroidX library, Apache 2.0, free, a few KB; it only installs the profile shipped in the APK. Already pulled in
    // by Compose, declared here so that the version is ours and visible.
    implementation(libs.androidx.profileinstaller)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.kotlinx.coroutines.play.services)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.foundation)
    implementation(libs.androidx.compose.material3)

    // "Continuer avec Google": Credential Manager (Google's recommended replacement of the old Sign-In SDK) and the
    // Google ID token helper. Needed by ticket AUTH-1; free, maintained by Google/AndroidX.
    implementation(libs.androidx.credentials)
    implementation(libs.androidx.credentials.play.services.auth)
    implementation(libs.google.googleid)

    // Onboarding profile step (ONB-2): phone number validation and E.164 formatting (libphonenumber, Apache 2.0, free),
    // and the orientation of the chosen photo (ExifInterface), which is then re-encoded with NO metadata.
    implementation(libs.libphonenumber)
    implementation(libs.androidx.exifinterface)

    // Map with the perimeter of a place (LOC-1): MapLibre Native, BSD-2-Clause, free. See docs/dev/lieux-fournisseurs-gratuits.md.
    implementation(libs.maplibre.android)

    implementation(libs.firebase.firestore)
    implementation(libs.firebase.auth)
    implementation(libs.firebase.crashlytics)
    implementation(libs.firebase.analytics)
    // App Check: Play Integrity in release, debug provider in debug (never shipped in release).
    releaseImplementation(libs.firebase.appcheck.playintegrity)
    add("releaseLikeImplementation", libs.firebase.appcheck.playintegrity)
    debugImplementation(libs.firebase.appcheck.debug)

    debugImplementation(libs.androidx.compose.ui.tooling)
    debugImplementation(libs.androidx.compose.ui.tooling.preview)

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    // Screen tests on the JVM (no emulator needed, runs in CI). Test-only: nothing of this reaches the APK/AAB.
    testImplementation(libs.robolectric)
    testImplementation(platform(libs.androidx.compose.bom))
    testImplementation(libs.androidx.compose.ui.test.junit4)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
}

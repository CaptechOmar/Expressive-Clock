plugins {
    // No `kotlin.android`: AGP 9 has built-in Kotlin support and applying it is a hard error.
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
}

// Set by CI (`-PclockBuild=<run number>`); absent on a local build.
val clockBuild = (findProperty("clockBuild") as String?)?.toIntOrNull()

android {
    namespace = "app.materialclock"
    compileSdk = 37

    defaultConfig {
        applicationId = "app.materialclock"
        minSdk = 26
        targetSdk = 36
        // CI passes its run number as `clockBuild`, so every published beta has a higher version
        // code than the last and Android treats the next one as an update. Local builds get 1.
        versionCode = clockBuild ?: 1
        versionName = "0.1.0"
    }

    /**
     * Release signing, with the key kept out of the repository.
     *
     * The four properties live in `~/.gradle/gradle.properties`, not here and not in the project's
     * own `gradle.properties`, so nothing checked in can leak them. When they are absent the
     * release build falls back to the debug key: that still produces an installable APK for
     * sideloading a personal build, and it is deliberately useless for distribution, because a
     * debug-keyed APK cannot be updated by a properly signed one later.
     */
    val releaseStore = (findProperty("clockStoreFile") as String?)?.let(::file)?.takeIf { it.exists() }
    if (releaseStore != null) {
        signingConfigs.create("release") {
            storeFile = releaseStore
            storePassword = findProperty("clockStorePassword") as String?
            keyAlias = findProperty("clockKeyAlias") as String?
            keyPassword = findProperty("clockKeyPassword") as String?
        }
    }

    buildTypes {
        release {
            signingConfig = signingConfigs.findByName("release") ?: signingConfigs.getByName("debug")
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                // Must be the -optimize variant; plain proguard-android.txt carries -dontoptimize,
                // which is a hard error on AGP 9.
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
        }
        /**
         * What gets published as a beta release: the optimised release build, signed with the one
         * permanent key, under its own application id.
         *
         * The separate id (`.beta`) matters. It installs beside the debug build and beside any
         * other copy of the app, and it keeps its own data, so updating a beta never touches them
         * and they never block it. The permanent key matters more: Android only updates an app in
         * place when the new APK is signed with the same key, and CI's own debug key is generated
         * fresh on every run, which is why every earlier build had to be uninstalled first.
         */
        create("beta") {
            initWith(getByName("release"))
            applicationIdSuffix = ".beta"
            versionNameSuffix = "-beta" + (clockBuild?.let { ".$it" } ?: "")
            matchingFallbacks += "release"
        }
        debug {
            applicationIdSuffix = ".debug"
            versionNameSuffix = "-debug"
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    androidResources {
        localeFilters += listOf("en")
    }

    // No accounts, no telemetry, no ad IDs. The dependency manifest stays out of the APK too.
    dependenciesInfo {
        includeInApk = false
        includeInBundle = true
    }

    packaging {
        resources {
            excludes += setOf(
                "/META-INF/{AL2.0,LGPL2.1}",
                "/META-INF/*.version",
                "/META-INF/com.android.tools/**",
                "/kotlin/**",
                "/DebugProbesKt.bin",
            )
        }
    }

    testOptions {
        unitTests {
            isIncludeAndroidResources = true
            isReturnDefaultValues = true
        }
    }
}

kotlin {
    compilerOptions {
        // [M3E] Most expressive components are still opt-in even though MaterialExpressiveTheme
        // itself is not. Opting in centrally keeps the annotation noise out of every call site.
        optIn.addAll(
            "androidx.compose.material3.ExperimentalMaterial3Api",
            "androidx.compose.material3.ExperimentalMaterial3ExpressiveApi",
            "androidx.compose.foundation.ExperimentalFoundationApi",
            "androidx.compose.ui.text.ExperimentalTextApi",
        )
    }
}

dependencies {
    implementation(platform(libs.androidx.compose.bom))

    implementation(libs.androidx.material3)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.text)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.material.icons.extended)

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)

    implementation(libs.androidx.datastore.preferences)
    implementation(libs.androidx.graphics.shapes)

    debugImplementation(libs.androidx.compose.ui.tooling)

    testImplementation(libs.junit)
    testImplementation(libs.kotlin.test)
    testImplementation(libs.androidx.test.core)
}

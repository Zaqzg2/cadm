import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
    alias(libs.plugins.hilt)
}

// Firebase is wired only when app/google-services.json exists. Without it the app still builds and
// opens in a preview mode (no sign-in), so navigation can be checked before Firebase is registered.
if (file("google-services.json").exists()) {
    apply(plugin = "com.google.gms.google-services")
}

// Optional stable signing key (all four values come from GitHub secrets, see README.md). A fixed key
// keeps the same signature on every build, so a new APK installs over the old one without wiping data.
// PKCS12 keystore: the store password and the key password must be identical (KEY_PASSWORD may be
// left unset and then defaults to KEYSTORE_PASSWORD). Without them the debug key is used, like Kady.
val stableKeystore: File? = System.getenv("KEYSTORE_PATH")
    ?.takeIf { it.isNotBlank() }
    ?.let { file(it) }
    ?.takeIf { it.exists() }
val stableStorePassword: String? = System.getenv("KEYSTORE_PASSWORD")?.takeIf { it.isNotBlank() }
val stableKeyAlias: String? = System.getenv("KEY_ALIAS")?.takeIf { it.isNotBlank() }
val stableKeyPassword: String? = System.getenv("KEY_PASSWORD")?.takeIf { it.isNotBlank() } ?: stableStorePassword
val useStableSigning = stableKeystore != null && stableStorePassword != null && stableKeyAlias != null

// Every CI run gets a higher versionCode, and the same number shows in the app (More screen), so it is
// always clear which build is installed.
val ciRunNumber = System.getenv("GITHUB_RUN_NUMBER")?.toIntOrNull() ?: 1

android {
    namespace = "com.cady.cadymarketing"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.cady.cadymarketing"
        // Same floor as Kady (Android 7.0).
        minSdk = 24
        targetSdk = 36
        versionCode = ciRunNumber
        versionName = "0.1.$ciRunNumber"
    }

    signingConfigs {
        if (useStableSigning) {
            create("stable") {
                storeFile = stableKeystore
                storePassword = stableStorePassword
                keyAlias = stableKeyAlias
                keyPassword = stableKeyPassword
            }
        }
    }

    val stableSigning = signingConfigs.findByName("stable")

    buildTypes {
        debug {
            if (stableSigning != null) signingConfig = stableSigning
        }
        release {
            // The optimized build (R8 shrinking). It is signed with the stable key when provided,
            // otherwise with the debug key purely so it installs directly on a test phone.
            isMinifyEnabled = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            signingConfig = stableSigning ?: signingConfigs.getByName("debug")
        }
    }

    // The CI debug build is the one that must always succeed; the optimized build is extra, so lint's
    // release-only vital checks are not allowed to stop it.
    lint {
        checkReleaseBuilds = false
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
}

// Modern compilerOptions DSL — the old `kotlinOptions { jvmTarget = "17" }` syntax is a hard error.
kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_17)
        // Same project-wide opt-ins as Kady: TopAppBar & co. are @ExperimentalMaterial3Api in this BOM
        // and @RequiresOptIn defaults to ERROR level, so one flag here covers every screen.
        freeCompilerArgs.add("-opt-in=androidx.compose.material3.ExperimentalMaterial3Api")
        freeCompilerArgs.add("-opt-in=androidx.compose.foundation.ExperimentalFoundationApi")
        freeCompilerArgs.add("-opt-in=androidx.compose.foundation.layout.ExperimentalLayoutApi")
        freeCompilerArgs.add("-opt-in=androidx.compose.ui.ExperimentalComposeUiApi")
    }
}

ksp {
    // Exports Room's schema JSON per version for migration testing — no extra Gradle plugin needed.
    arg("room.schemaLocation", "$projectDir/schemas")
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.activity.compose)

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons.extended)
    debugImplementation(libs.androidx.compose.ui.tooling)

    implementation(libs.androidx.navigation.compose)

    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)

    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)
    implementation(libs.androidx.hilt.navigation.compose)

    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.kotlinx.coroutines.play.services)

    implementation(platform(libs.firebase.bom))
    implementation(libs.firebase.auth)
    implementation(libs.firebase.firestore)

    implementation(libs.androidx.datastore.preferences)

    testImplementation(libs.junit)
}

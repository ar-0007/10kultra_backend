import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
    alias(libs.plugins.hilt)
}

android {
    namespace = "com.tenkultra.tv"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.tenkultra.tv"
        minSdk = 21
        targetSdk = 34
        versionCode = 1
        versionName = "1.0"
        vectorDrawables { useSupportLibrary = true }

        // OPTIONAL fixed-MAC build (for a box whose MAC is already whitelisted with the provider):
        //   ./gradlew.bat :app:assembleRelease --offline -PforcedMac=DA:7A:69:4E:DE:E0
        // Without the property this is empty and every box keeps its own UNIQUE derived MAC,
        // so the normal production APK is completely unaffected.
        buildConfigField(
            "String",
            "FORCED_MAC",
            "\"${(project.findProperty("forcedMac") as String?).orEmpty()}\""
        )

        // OPTIONAL hardcoded-portal build (a TEST APK pinned to one server, no activation screen):
        //   ./gradlew.bat :app:assembleDebug -PforcedPortal=http://star.homeip.net
        // Empty in every normal build, so the shipping APK keeps the dashboard activation flow and
        // its three selectable servers exactly as they are.
        buildConfigField(
            "String",
            "FORCED_PORTAL",
            "\"${(project.findProperty("forcedPortal") as String?).orEmpty()}\""
        )
    }

    // Release signing. The keystore and its passwords are NOT in git — losing them means no future
    // build can ever install over an installed 10K Ultra app, so back both up off this machine.
    // Values come from `android-app/keystore.properties` (gitignored) or, on a CI box, from the
    // env vars TENKULTRA_STORE_FILE / _STORE_PASSWORD / _KEY_ALIAS / _KEY_PASSWORD.
    // See keystore.properties.example.
    val keystoreProps = Properties().apply {
        val f = rootProject.file("keystore.properties")
        if (f.exists()) f.inputStream().use { load(it) }
    }
    fun signingValue(prop: String, env: String): String? =
        keystoreProps.getProperty(prop) ?: System.getenv(env)

    val storeFileName = signingValue("storeFile", "TENKULTRA_STORE_FILE") ?: "tenkultra-release.jks"
    val keystoreFile = rootProject.file(storeFileName)
    val canSignRelease = keystoreFile.exists() &&
        signingValue("storePassword", "TENKULTRA_STORE_PASSWORD") != null

    signingConfigs {
        if (canSignRelease) {
            create("release") {
                storeFile = keystoreFile
                storePassword = signingValue("storePassword", "TENKULTRA_STORE_PASSWORD")
                keyAlias = signingValue("keyAlias", "TENKULTRA_KEY_ALIAS") ?: "tenkultra"
                keyPassword = signingValue("keyPassword", "TENKULTRA_KEY_PASSWORD")
                    ?: signingValue("storePassword", "TENKULTRA_STORE_PASSWORD")
            }
        }
    }

    buildTypes {
        release {
            // Production hardening: R8 shrinks + obfuscates the bytecode (anti-clone).
            isMinifyEnabled = true
            isShrinkResources = true
            // Unsigned rather than debug-signed when the keystore is absent, so a misconfigured
            // machine fails at install time instead of quietly shipping an unupdatable APK.
            signingConfig = if (canSignRelease) signingConfigs.getByName("release") else null
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
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
        compose = true
        buildConfig = true
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }

    lint {
        // Release lintVital pulls extra artifacts; skip so release builds work offline.
        checkReleaseBuilds = false
        abortOnError = false
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.activity.compose)

    // Compose
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.foundation)
    implementation(libs.androidx.compose.material.icons.extended)
    implementation(libs.androidx.navigation.compose)
    debugImplementation(libs.androidx.compose.ui.tooling)

    // TV Compose
    implementation(libs.androidx.tv.material)

    // Media3 / ExoPlayer
    implementation(libs.androidx.media3.exoplayer)
    implementation(libs.androidx.media3.exoplayer.hls)
    implementation(libs.androidx.media3.ui)

    // Networking
    implementation(libs.retrofit)
    implementation(libs.retrofit.converter.gson)
    implementation(libs.okhttp)
    implementation(libs.okhttp.logging.interceptor)
    implementation(libs.okhttp.dnsoverhttps)
    implementation(libs.gson)

    // Room
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)

    // DataStore
    implementation(libs.androidx.datastore.preferences)

    // Hilt
    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)
    implementation(libs.androidx.hilt.navigation.compose)

    // Image loading
    implementation(libs.coil.compose)

    // Coroutines
    implementation(libs.kotlinx.coroutines.android)

    // In-app QR scanner (mobile pairing): scans the TV's tenkultra:// connect QR.
    implementation("com.journeyapps:zxing-android-embedded:4.3.0")

    // Mobile (touch) UI — proper Material3 components (NavigationBar, Card, Slider…). Version from BOM.
    implementation("androidx.compose.material3:material3")
}

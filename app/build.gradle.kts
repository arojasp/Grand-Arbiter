import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ksp)
}

android {
    namespace = "es.alejandro.grandarbiter"
    compileSdk = 37

    defaultConfig {
        applicationId = "es.alejandro.grandarbiter"
        minSdk = 26
        targetSdk = 37
        versionCode = 7
        versionName = "1.1.2"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    // Upload key for Google Play, never committed. Two sources, the first one found wins:
    //  1. CI (.github/workflows/release.yml): environment variables UPLOAD_KEYSTORE_FILE (path to the
    //     .jks decoded from a GitHub secret), UPLOAD_KEYSTORE_PASSWORD, UPLOAD_KEY_ALIAS, UPLOAD_KEY_PASSWORD.
    //  2. Your PC: keystore.properties (gitignored) at the repo root:
    //       storeFile=C:/path/to/grand-arbiter-upload.jks
    //       storePassword=…   keyAlias=upload   keyPassword=…
    // Neither: release builds are simply unsigned (the CI workflow, other machines). Never a debug key.
    val ciKeystore = System.getenv("UPLOAD_KEYSTORE_FILE")?.takeIf { it.isNotBlank() }
    // GitHub passes a secret that doesn't exist as an EMPTY variable, not a missing one: treat blank as missing.
    fun requiredEnv(name: String): String =
        System.getenv(name)?.takeIf { it.isNotBlank() } ?: error("$name is not set (secret missing in the GitHub environment \"play\"?)")
    val keystoreFile = rootProject.file("keystore.properties")
    if (ciKeystore != null || keystoreFile.exists()) {
        val keystore = Properties().apply {
            if (ciKeystore != null) {
                setProperty("storeFile", ciKeystore)
                setProperty("storePassword", requiredEnv("UPLOAD_KEYSTORE_PASSWORD"))
                setProperty("keyAlias", requiredEnv("UPLOAD_KEY_ALIAS"))
                setProperty("keyPassword", requiredEnv("UPLOAD_KEY_PASSWORD"))
            } else {
                keystoreFile.inputStream().use { load(it) }
            }
        }
        signingConfigs {
            create("release") {
                storeFile = file(keystore.getProperty("storeFile"))
                storePassword = keystore.getProperty("storePassword")
                keyAlias = keystore.getProperty("keyAlias")
                keyPassword = keystore.getProperty("keyPassword")
            }
        }
    }

    buildTypes {
        release {
            // R8: shrinks code (material-icons-extended alone is several MB) and resources.
            // Play deobfuscates crash traces with the mapping file bundled in the AAB.
            isMinifyEnabled = true
            isShrinkResources = true
            // The app has no C/C++ of its own, but two AndroidX libraries ship native code
            // (graphics path, DataStore). Their symbol tables go into the AAB so Play Console can
            // name the functions in a native crash / ANR trace (Play warns without them).
            ndk { debugSymbolLevel = "SYMBOL_TABLE" }
            signingConfigs.findByName("release")?.let { signingConfig = it }
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    buildFeatures {
        compose = true
        buildConfig = true   // VERSION_CODE, compared with the published manifest's minVersionCode
    }
}

// Room schema JSON per database version (AppDatabase from v8): reviewable in git, usable for migration tests.
ksp {
    arg("room.schemaLocation", "$projectDir/schemas")
}

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_11)
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons.extended)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.androidx.core.splashscreen)

    // Koin
    implementation(libs.koin.android)
    implementation(libs.koin.compose)

    // Room
    implementation(libs.room.runtime)
    implementation(libs.room.ktx)
    ksp(libs.room.compiler)

    // OkHttp (Scryfall bulk downloads)
    implementation(libs.okhttp.logging)

    // DataStore
    implementation(libs.datastore.preferences)

    // Coil
    implementation(libs.coil.compose)

    // Serialization (rules bundle)
    implementation(libs.kotlinx.serialization.json)

    // Google Play In-App Updates (only works for installs from Play)
    implementation(libs.play.app.update.ktx)


    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    debugImplementation(libs.androidx.compose.ui.tooling)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
}

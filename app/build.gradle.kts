import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
}

/**
 * Signing is opt-in and there are two keys, because Play will not accept the sideload key:
 *
 *  - `keystore.properties` — the ECDSA P-256 key that signs sideloaded APKs.
 *  - `upload-keystore.properties` — an RSA 2048 key for Play uploads, selected with `-PplayUpload`.
 *
 * With neither present the release variant stays unsigned, so a fresh clone still builds.
 */
class SigningKey(
    private val propertiesFile: File,
    private val environmentPrefix: String,
) {
    private val properties: Properties? = propertiesFile
        .takeIf { it.exists() }
        ?.let { file -> Properties().apply { file.inputStream().use(::load) } }

    private fun value(key: String, variable: String): String? =
        properties?.getProperty(key) ?: System.getenv("${environmentPrefix}_$variable")

    val path: String? = value("storeFile", "KEYSTORE")
    val storePassword: String? = value("storePassword", "KEYSTORE_PASSWORD")
    val alias: String? = value("keyAlias", "KEY_ALIAS")
    val aliasPassword: String? = value("keyPassword", "KEY_PASSWORD")

    val isUsable: Boolean =
        listOf(path, storePassword, alias, aliasPassword).all { !it.isNullOrBlank() } &&
            File(path!!).exists()
}

val sideloadKey = SigningKey(rootProject.file("keystore.properties"), "CURSOID")
val uploadKey = SigningKey(rootProject.file("upload-keystore.properties"), "CURSOID_UPLOAD")

/** `./gradlew bundleRelease -PplayUpload` produces an AAB signed with the Play upload key. */
val forPlayUpload = providers.gradleProperty("playUpload").isPresent
val activeKey = if (forPlayUpload) uploadKey else sideloadKey

if (forPlayUpload && !uploadKey.isUsable) {
    error("-PplayUpload needs upload-keystore.properties or the CURSOID_UPLOAD_* variables.")
}

android {
    namespace = "dev.cursoid"
    compileSdk = 37

    defaultConfig {
        applicationId = "dev.cursoid"
        minSdk = 26
        targetSdk = 36
        versionCode = 1
        versionName = "0.1.0"
    }

    signingConfigs {
        if (activeKey.isUsable) {
            create("cursoid") {
                storeFile = file(activeKey.path!!)
                storePassword = activeKey.storePassword
                keyAlias = activeKey.alias
                keyPassword = activeKey.aliasPassword
                // minSdk 26 means v1 JAR signing is dead weight; v3 buys key rotation later.
                // App bundles are signed jar-style regardless of these APK-only switches.
                enableV1Signing = false
                enableV2Signing = true
                enableV3Signing = true
            }
        }
    }

    buildTypes {
        debug {
            applicationIdSuffix = ".debug"
            versionNameSuffix = "-debug"
            // Distinguishable on the launcher when both variants are sideloaded together.
            resValue("string", "app_name", "Cursoid debug")
            // Share the sideload key so debug builds also update in place across machines.
            if (activeKey.isUsable) {
                signingConfig = signingConfigs.getByName("cursoid")
            }
        }
        release {
            resValue("string", "app_name", "Cursoid")
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
            if (activeKey.isUsable) {
                signingConfig = signingConfigs.getByName("cursoid")
            }
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
        resValues = true
    }


    packaging {
        resources.excludes += setOf("/META-INF/{AL2.0,LGPL2.1}")
    }

    testOptions {
        unitTests {
            isIncludeAndroidResources = true
            all { test ->
                test.systemProperty("roborazzi.test.record", "true")
            }
        }
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons.extended)
    debugImplementation(libs.androidx.compose.ui.tooling)

    implementation(libs.androidx.navigation.compose)
    implementation(libs.androidx.datastore.preferences)
    implementation(libs.androidx.work.runtime.ktx)

    implementation(libs.okhttp)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.coil.compose)
    implementation(libs.coil.network.okhttp)

    testImplementation(libs.junit)

    // Screens render on the JVM with Robolectric's native graphics plus Roborazzi, because this
    // machine cannot run an emulator.
    testImplementation(libs.robolectric)
    testImplementation(libs.androidx.test.ext.junit)
    testImplementation(platform(libs.androidx.compose.bom))
    testImplementation(libs.androidx.compose.ui.test.junit4)
    testImplementation(libs.roborazzi)
    testImplementation(libs.roborazzi.compose)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
}

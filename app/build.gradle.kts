import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
}

/**
 * Release signing is opt-in: point `keystore.properties` (or the matching environment variables) at
 * a keystore and release builds are signed. Without it the release variant stays unsigned, so a
 * fresh clone still builds.
 */
val keystoreConfig: Properties? = rootProject.file("keystore.properties")
    .takeIf { it.exists() }
    ?.let { file -> Properties().apply { file.inputStream().use(::load) } }

fun signingValue(key: String, environmentVariable: String): String? =
    keystoreConfig?.getProperty(key) ?: System.getenv(environmentVariable)

val keystorePath = signingValue("storeFile", "CURSOID_KEYSTORE")
val keystorePassword = signingValue("storePassword", "CURSOID_KEYSTORE_PASSWORD")
val keystoreAlias = signingValue("keyAlias", "CURSOID_KEY_ALIAS")
val keystoreAliasPassword = signingValue("keyPassword", "CURSOID_KEY_PASSWORD")
val hasReleaseKey = listOf(
    keystorePath,
    keystorePassword,
    keystoreAlias,
    keystoreAliasPassword,
).all { !it.isNullOrBlank() } && file(keystorePath!!).exists()

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
        if (hasReleaseKey) {
            create("release") {
                storeFile = file(keystorePath!!)
                storePassword = keystorePassword
                keyAlias = keystoreAlias
                keyPassword = keystoreAliasPassword
            }
        }
    }

    buildTypes {
        debug {
            applicationIdSuffix = ".debug"
            versionNameSuffix = "-debug"
            // Distinguishable on the launcher when both variants are sideloaded together.
            resValue("string", "app_name", "Cursoid debug")
        }
        release {
            resValue("string", "app_name", "Cursoid")
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
            if (hasReleaseKey) {
                signingConfig = signingConfigs.getByName("release")
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

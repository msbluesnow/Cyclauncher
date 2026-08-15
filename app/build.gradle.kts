plugins {
    alias(libs.plugins.android.application)
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    namespace = "dev.msbs.cyclauncher"
    compileSdk {
        version = release(36) {
            minorApiLevel = 1
        }
    }

    defaultConfig {
        applicationId = "dev.msbs.cyclauncher"
        minSdk = 24
        targetSdk = 36
        versionCode = 9
        versionName = "0.7.0 Alpha"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        release {
            optimization {
                enable = false
            }
        }
    }

    dependenciesInfo {
        // Disable dependency metadata in APK — required by F-Droid
        includeInApk = false
        includeInBundle = false
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }

    buildFeatures {
        compose = true
    }
}

androidComponents {
    onVariants { variant ->
        val versionCode = variant.outputs.map { it.versionCode.get() }.firstOrNull()
            ?: android.defaultConfig.versionCode
        variant.outputs.forEach { output ->
            output.outputFileName.set("Cyclauncher-${versionCode}.apk")
        }
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.graphics)
    implementation(libs.androidx.material3)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.material.icons.extended)

    implementation(libs.coil.compose)
}
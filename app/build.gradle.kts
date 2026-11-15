plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.rawag.voicebubble"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.rawag.voicebubble"
        minSdk = 26
        targetSdk = 34
        versionCode = 2
        versionName = "2.0"

        ndk { abiFilters += listOf("arm64-v8a") }
        externalNativeBuild {
            cmake {
                arguments += listOf("-DCMAKE_BUILD_TYPE=Release", "-DANDROID_STL=c++_static")
                cppFlags += listOf("-O3")
            }
        }
    }

    signingConfigs {
        create("release") {
            storeFile = file("release.keystore")
            storePassword = "voicebubble"
            keyAlias = "voicebubble"
            keyPassword = "voicebubble"
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            signingConfig = signingConfigs.getByName("release")
        }
    }

    externalNativeBuild {
        cmake {
            path = file("src/main/cpp/CMakeLists.txt")
            version = "3.22.1"
        }
    }

    // Whisper model is bundled in assets and must stay uncompressed
    androidResources { noCompress += listOf("bin") }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
}

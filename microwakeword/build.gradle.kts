plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
}

/**
 * microWakeWord wake-word engine.
 *
 * A thin Kotlin/JNI wrapper around TFLite-Micro: the audio frontend (40 mel-like
 * features per 30 ms window at a 10 ms stride), int8 model inference and the sliding
 * window detector all live in native code.
 *
 * Modelled on Home Assistant's Apache-2.0 `microwakeword` module.
 * https://github.com/home-assistant/android/tree/main/microwakeword
 */
android {
    namespace = "uk.org.retallack.jarvis.microwakeword"
    compileSdk = 35
    ndkVersion = libs.versions.androidNdk.get()

    defaultConfig {
        minSdk = 26

        ndk {
            abiFilters += listOf("arm64-v8a")
        }

        externalNativeBuild {
            cmake {
                // Required for Android 15+ 16 KB page-size compatibility.
                arguments += "-DANDROID_SUPPORT_FLEXIBLE_PAGE_SIZES=ON"
            }
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    externalNativeBuild {
        cmake {
            path = file("src/main/cpp/CMakeLists.txt")
            version = libs.versions.cmake.get()
        }
    }
}

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}
android {
    namespace = "hub.vpn"
    compileSdk = 34
    defaultConfig {
        applicationId = "hub.vpn"
        minSdk = 23          // Android 6.0
        targetSdk = 23       // старое поведение, чтобы не требовались новые разрешения
        versionCode = 1
        versionName = "0.1"
    }
    signingConfigs {
        // Постоянный ключ подписи: тогда новую версию можно ставить поверх старой, не удаляя приложение
        getByName("debug") {
            storeFile = file("pashtet-debug.keystore")
            storePassword = "android"
            keyAlias = "pashtet"
            keyPassword = "android"
        }
    }
    buildTypes { release { isMinifyEnabled = false } }
    compileOptions { sourceCompatibility = JavaVersion.VERSION_1_8; targetCompatibility = JavaVersion.VERSION_1_8 }
    kotlinOptions { jvmTarget = "1.8" }
    lint { abortOnError = false }
}
base { archivesName.set("PashtetVPN") }  // файл будет называться PashtetVPN-debug.apk

// Этап 2: сюда добавляется ядро (libv2ray.aar в app/libs)
// dependencies { implementation(fileTree(mapOf("dir" to "libs", "include" to listOf("*.aar")))) }

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.tpj.millcar"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.tpj.millcar"
        minSdk = 31
        targetSdk = 35
        versionCode = 1
        versionName = "0.1"
    }

    // Saját, fix kulcs: így minden új APK frissíteni tudja az előzőt.
    // A keystore nincs a repóban. Ha megvan lokálisan (app/millcar.keystore), azzal írja alá,
    // különben az alapértelmezett debug kulccsal (CI-hez így is működik).
    val localKeystore = file("millcar.keystore")
    if (localKeystore.exists()) {
        signingConfigs {
            create("mill") {
                storeFile = localKeystore
                storePassword = "millcar"
                keyAlias = "millcar"
                keyPassword = "millcar"
            }
        }
    }

    buildTypes {
        getByName("debug") {
            if (localKeystore.exists()) signingConfig = signingConfigs.getByName("mill")
        }
        getByName("release") {
            isMinifyEnabled = false
            if (localKeystore.exists()) signingConfig = signingConfigs.getByName("mill")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
}

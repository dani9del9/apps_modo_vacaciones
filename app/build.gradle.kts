plugins {
    id("com.android.application")
}

android {
    namespace = "com.dani.modovacaciones"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.dani.modovacaciones"
        minSdk = 26
        targetSdk = 35
        versionCode = 1
        versionName = "1.0"
    }

    // Firma fija incluida en el repo: así cada APK compilado por GitHub Actions
    // se puede instalar encima del anterior sin desinstalar (es una app personal).
    signingConfigs {
        create("app") {
            storeFile = file("keystore/modovacaciones.p12")
            storePassword = "modovacaciones"
            keyAlias = "modovacaciones"
            keyPassword = "modovacaciones"
            storeType = "pkcs12"
        }
    }

    buildTypes {
        getByName("release") {
            isMinifyEnabled = false
            signingConfig = signingConfigs.getByName("app")
        }
        getByName("debug") {
            signingConfig = signingConfigs.getByName("app")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    lint {
        abortOnError = false
        checkReleaseBuilds = false
    }
}

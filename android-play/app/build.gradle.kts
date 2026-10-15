import java.util.Properties

plugins {
    id("com.android.application")
}

// Upload key for Play: copy keystore.properties.example to keystore.properties and fill it in.
// Without it the release build is left unsigned (Android Studio can sign it in Build > Generate Signed Bundle).
val keyProps = Properties().apply {
    val f = rootProject.file("keystore.properties")
    if (f.exists()) f.inputStream().use { load(it) }
}

android {
    namespace = "com.alexandercbgm.ugomonikota"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.alexandercbgm.ugomonikota"
        minSdk = 24
        targetSdk = 36
        versionCode = 15
        versionName = "1.1"
    }

    signingConfigs {
        if (keyProps.isNotEmpty()) {
            create("upload") {
                storeFile = rootProject.file(keyProps.getProperty("storeFile"))
                storePassword = keyProps.getProperty("storePassword")
                keyAlias = keyProps.getProperty("keyAlias")
                keyPassword = keyProps.getProperty("keyPassword")
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            if (keyProps.isNotEmpty()) signingConfig = signingConfigs.getByName("upload")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    androidResources {
        // keep the game's fonts and page uncompressed so the WebView reads them straight from the APK
        noCompress += listOf("woff2", "html")
        // the game speaks English and Russian; drop other languages that libraries bring along
        localeFilters += listOf("en", "ru")
    }
}

dependencies {
    implementation("com.android.billingclient:billing:9.1.0")
}

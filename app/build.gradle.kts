plugins {
    id("com.android.application")
}

android {
    namespace = "dev.alastorkaneki.cursedkeyboard"
    compileSdk = 35

    defaultConfig {
        applicationId = "dev.alastorkaneki.cursedkeyboard"
        minSdk = 23
        targetSdk = 35
        versionCode = 2
        versionName = "1.1.0"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    sourceSets {
        getByName("main") {
            java.setSrcDirs(listOf("src/cursed/java"))
            res.setSrcDirs(listOf("src/cursed/res"))
            manifest.srcFile("src/cursed/AndroidManifest.xml")
        }
    }
}

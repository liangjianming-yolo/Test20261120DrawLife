plugins {
    id("com.android.library")
}

android {
    namespace = "com.drawlife.backgroundtoolkit"
    resourcePrefix = "drawlife_toolkit_"
    compileSdk {
        version = release(37)
    }

    defaultConfig {
        minSdk = 26
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
}

dependencies {
    implementation("androidx.core:core-ktx:1.19.1")
    api("androidx.work:work-runtime:2.12.0")
    api(platform("com.google.firebase:firebase-bom:35.2.0"))
    api("com.google.firebase:firebase-messaging")
}

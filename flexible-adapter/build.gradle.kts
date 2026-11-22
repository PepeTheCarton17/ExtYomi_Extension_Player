// Vendored from https://github.com/arkon/FlexibleAdapter at c8013533 (Apache-2.0).
// JitPack can no longer build this fork because its legacy `grabver` plugin depends on
// `nu.studer:java-ordered-properties:1.0.1`, which is no longer published. Keeping the
// small library in-tree makes ExtYomi builds self-contained.
plugins {
    id("com.android.library")
}

android {
    namespace = "eu.davidea.flexibleadapter"
    compileSdk = 36

    defaultConfig {
        minSdk = 26
    }

    buildFeatures {
        buildConfig = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    lint {
        abortOnError = false
    }
}

dependencies {
    api(androidx.recyclerview)
    implementation(androidx.corektx)
    implementation(androidx.annotation)
}

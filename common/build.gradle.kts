plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.serialization)
}

android {
    namespace = "cordobot.pacificrim.cortv.common"
    compileSdk = 37

    defaultConfig {
        minSdk = 23
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_11)
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.kotlinx.serialization.json)
}

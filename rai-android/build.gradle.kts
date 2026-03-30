plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.serialization)
}

android {
    namespace  = "ai.rakuten.rai.android"
    compileSdk = 36

    defaultConfig {
        minSdk = 26

        buildConfigField("String", "RAKUTEN_TEXT_BASE_URL",  "\"https://api.ai.public.rakuten-it.com/anthropic/\"")
        buildConfigField("String", "RAKUTEN_IMAGE_BASE_URL", "\"https://api.ai.public.rakuten-it.com/google-vertexai/v1/\"")
        buildConfigField("String", "RAKUTEN_DEFAULT_TEXT_MODEL",  "\"claude-sonnet-4-6\"")
        buildConfigField("String", "RAKUTEN_DEFAULT_IMAGE_MODEL", "\"gemini-3.1-flash-image-preview\"")
    }

    buildFeatures {
        buildConfig = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
    }

    kotlin {
        compilerOptions {
            jvmTarget = org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_21
        }
    }
}

dependencies {
    api(project(":rai-core"))
    api(libs.koin.android)

    implementation(libs.androidx.lifecycle.viewmodel.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.kotlinx.serialization.json)
    api(libs.okhttp)
    api(libs.okhttp.logging)

    testImplementation(libs.kotlin.test)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.mockk)
    testImplementation(libs.koin.test)
}

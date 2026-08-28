plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.plugin.compose")
}

val controlApiBaseUrl = providers.gradleProperty("GANJ_CONTROL_API_BASE_URL").orElse("").get()
val telegramBotRedirectUri = providers.gradleProperty("GANJ_TELEGRAM_BOT_REDIRECT_URI")
    .orElse("https://auth.invalid/telegram")
    .get()
val escapedControlApiBaseUrl = controlApiBaseUrl
    .replace("\\", "\\\\")
    .replace("\"", "\\\"")
val escapedTelegramBotRedirectUri = telegramBotRedirectUri
    .replace("\\", "\\\\")
    .replace("\"", "\\\"")

android {
    namespace = "com.ganj.vpn"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.ganj.vpn"
        minSdk = 24
        targetSdk = 36
        versionCode = 3
        versionName = "0.3.0"
        buildConfigField("String", "CONTROL_API_BASE_URL", "\"$escapedControlApiBaseUrl\"")
        buildConfigField("String", "TELEGRAM_BOT_REDIRECT_URI", "\"$escapedTelegramBotRedirectUri\"")

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables.useSupportLibrary = true
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
        }
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    packaging.resources.excludes += "/META-INF/{AL2.0,LGPL2.1}"
}

dependencies {
    implementation(project(":core:billing"))
    implementation(project(":core:control-api"))
    implementation(project(":core:device-identity"))
    implementation(project(":core:play-billing"))
    implementation(project(":core:vpn-api"))
    implementation(project(":core:xray-runtime"))

    val composeBom = platform("androidx.compose:compose-bom:2026.06.00")
    implementation(composeBom)
    androidTestImplementation(composeBom)

    implementation("androidx.activity:activity-compose:1.13.0")
    implementation("androidx.lifecycle:lifecycle-viewmodel:2.10.0")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.10.0")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-tooling-preview")
    debugImplementation("androidx.compose.ui:ui-tooling")

    testImplementation("junit:junit:4.13.2")
}

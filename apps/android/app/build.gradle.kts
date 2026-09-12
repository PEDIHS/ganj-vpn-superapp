import java.net.URI

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.plugin.compose")
}

val controlApiBaseUrl = providers.gradleProperty("GANJ_CONTROL_API_BASE_URL").orElse("").get()
val telegramRedirectUri = providers.gradleProperty("GANJ_TELEGRAM_REDIRECT_URI")
    .orElse("https://auth.invalid/ganj/telegram/callback")
    .get()
val alphaArm64Only = providers.gradleProperty("GANJ_ALPHA_ARM64_ONLY").orElse("false").get().toBoolean()
val controlApiUri = controlApiBaseUrl.takeIf { it.isNotBlank() }?.let(::URI)
controlApiUri?.let { uri ->
    require(uri.scheme == "https" && !uri.host.isNullOrBlank()) {
        "GANJ_CONTROL_API_BASE_URL must be an absolute HTTPS URI"
    }
    require(uri.userInfo == null && uri.query == null && uri.fragment == null) {
        "GANJ_CONTROL_API_BASE_URL cannot contain credentials, query or fragment"
    }
    require(uri.path.endsWith("/v1/")) {
        "GANJ_CONTROL_API_BASE_URL must end with /v1/"
    }
}
val escapedControlApiBaseUrl = controlApiBaseUrl
    .replace("\\", "\\\\")
    .replace("\"", "\\\"")
val escapedTelegramRedirectUri = telegramRedirectUri
    .replace("\\", "\\\\")
    .replace("\"", "\\\"")
val telegramRedirect = URI(telegramRedirectUri)
require(telegramRedirect.scheme == "https" && !telegramRedirect.host.isNullOrBlank()) {
    "GANJ_TELEGRAM_REDIRECT_URI must be an absolute HTTPS URI"
}
val telegramRedirectPath: String = telegramRedirect.rawPath?.takeIf { it.isNotBlank() } ?: "/"

android {
    namespace = "com.ganj.vpn"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.ganj.vpn"
        minSdk = 24
        targetSdk = 36
        versionCode = 8
        versionName = "0.3.5-alpha"
        buildConfigField("String", "CONTROL_API_BASE_URL", "\"$escapedControlApiBaseUrl\"")
        buildConfigField("String", "TELEGRAM_REDIRECT_URI", "\"$escapedTelegramRedirectUri\"")
        manifestPlaceholders["telegramAuthHost"] = telegramRedirect.host
        manifestPlaceholders["telegramAuthPath"] = telegramRedirectPath
        if (alphaArm64Only) {
            ndk {
                abiFilters += "arm64-v8a"
            }
        }

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

    packaging {
        resources.excludes += "/META-INF/{AL2.0,LGPL2.1}"
        if (alphaArm64Only) {
            jniLibs.useLegacyPackaging = true
        }
    }
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
    androidTestImplementation("androidx.test:runner:1.6.2")
    androidTestImplementation("androidx.test.ext:junit:1.2.1")

    implementation("androidx.activity:activity-compose:1.13.0")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.10.0")
    implementation("androidx.lifecycle:lifecycle-viewmodel:2.10.0")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-tooling-preview")

    testImplementation("junit:junit:4.13.2")
}

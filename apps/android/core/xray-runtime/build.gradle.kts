plugins {
    id("com.android.library")
}

android {
    namespace = "com.ganj.vpn.core.xray"
    compileSdk = 36

    defaultConfig {
        minSdk = 24
        consumerProguardFiles("consumer-rules.pro")
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

dependencies {
    implementation(project(":core:vpn-api"))

    // The adapter calls the pinned gomobile surface reflectively so the Ganj-owned boundary stays
    // small and can be rebuilt from the audited upstream source without application-code changes.
    runtimeOnly("io.github.toolshubofficial:libxray:26.6.27")

    testImplementation("junit:junit:4.13.2")
}

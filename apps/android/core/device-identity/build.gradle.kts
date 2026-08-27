plugins {
    id("com.android.library")
}

android {
    namespace = "com.ganj.vpn.core.deviceidentity"
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
    implementation(project(":core:control-api"))
    implementation("org.bouncycastle:bcprov-jdk18on:1.85.2")

    testImplementation("junit:junit:4.13.2")
}

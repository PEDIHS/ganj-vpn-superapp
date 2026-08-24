pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}

rootProject.name = "GanjVpnAndroid"
include(":app")
include(":core:billing")
include(":core:control-api")
include(":core:observability")
include(":core:play-billing")
include(":core:subscription")
include(":core:vpn-api")

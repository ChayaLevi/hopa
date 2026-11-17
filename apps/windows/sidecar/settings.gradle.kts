pluginManagement {
    repositories {
        gradlePluginPortal()
        google()
        mavenCentral()
    }
}

dependencyResolutionManagement {
    repositories {
        google()
        mavenCentral()
    }
    versionCatalogs {
        create("libs") {
            from(files("../../../apps/android/gradle/libs.versions.toml"))
        }
    }
}

rootProject.name = "hopa-windows-sidecar"

include(":know")
project(":know").projectDir = file("../../../packages/know")

include(":core")
project(":core").projectDir = file("../../../packages/core")

include(":remote")
project(":remote").projectDir = file("../../../packages/remote")

include(":state")
project(":state").projectDir = file("../../../packages/state")

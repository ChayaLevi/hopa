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

include(":knowledge")
project(":knowledge").projectDir = file("../../../packages/knowledge")

include(":engine")
project(":engine").projectDir = file("../../../packages/engine")

include(":net")
project(":net").projectDir = file("../../../packages/net")

include(":state")
project(":state").projectDir = file("../../../packages/state")

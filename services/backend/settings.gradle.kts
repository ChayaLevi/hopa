pluginManagement {
    includeBuild("../../build-logic")
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
            from(files("../../apps/android/gradle/libs.versions.toml"))
        }
    }
}

rootProject.name = "hopa-backend"

include(":knowledge")
project(":knowledge").projectDir = file("../../packages/knowledge")
include(":contracts")
project(":contracts").projectDir = file("../../packages/contracts")
include(":engine")
project(":engine").projectDir = file("../../packages/engine")

include(":stores")
project(":stores").projectDir = file("../../packages/stores")

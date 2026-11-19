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

include(":catalog")
project(":catalog").projectDir = file("../../packages/catalog")
include(":contracts")
project(":contracts").projectDir = file("../../packages/contracts")
include(":codec")
project(":codec").projectDir = file("../../packages/codec")
include(":observation")
project(":observation").projectDir = file("../../packages/observation")
include(":records")
project(":records").projectDir = file("../../packages/records")
include(":prompt")
project(":prompt").projectDir = file("../../packages/prompt")
include(":agent")
project(":agent").projectDir = file("../../packages/agent")


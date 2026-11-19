pluginManagement {
    includeBuild("../../../build-logic")
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

include(":catalog")
project(":catalog").projectDir = file("../../../core/catalog")

include(":contracts")
project(":contracts").projectDir = file("../../../core/contracts")

include(":observation")
project(":observation").projectDir = file("../../../core/observation")
include(":records")
project(":records").projectDir = file("../../../core/records")
include(":agent")
project(":agent").projectDir = file("../../../core/agent")

include(":providers")
project(":providers").projectDir = file("../../../core/providers")
include(":transport")
project(":transport").projectDir = file("../../../core/transport")


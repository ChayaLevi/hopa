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

include(":knowledge")
project(":knowledge").projectDir = file("../../../packages/knowledge")

include(":contracts")
project(":contracts").projectDir = file("../../../packages/contracts")
include(":codec")
project(":codec").projectDir = file("../../../packages/codec")

include(":routing")
project(":routing").projectDir = file("../../../packages/routing")
include(":observation")
project(":observation").projectDir = file("../../../packages/observation")
include(":records")
project(":records").projectDir = file("../../../packages/records")
include(":prompt")
project(":prompt").projectDir = file("../../../packages/prompt")
include(":execution")
project(":execution").projectDir = file("../../../packages/execution")

include(":providers")
project(":providers").projectDir = file("../../../packages/providers")
include(":transport")
project(":transport").projectDir = file("../../../packages/transport")


pluginManagement {
    includeBuild("../../build-logic")
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
        maven { url = uri("https://jitpack.io") }
    }
}

rootProject.name = "hopa-android"

include(":app")
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
include(":providers")
project(":providers").projectDir = file("../../packages/providers")
include(":transport")
project(":transport").projectDir = file("../../packages/transport")
include(":capabilities")
project(":capabilities").projectDir = file("capabilities")
include(":stores")
project(":stores").projectDir = file("stores")
include(":data")
project(":data").projectDir = file("data")

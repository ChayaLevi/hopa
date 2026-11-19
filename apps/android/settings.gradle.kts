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
project(":catalog").projectDir = file("../../core/catalog")
include(":contracts")
project(":contracts").projectDir = file("../../core/contracts")
include(":observation")
project(":observation").projectDir = file("../../core/observation")
include(":records")
project(":records").projectDir = file("../../core/records")
include(":agent")
project(":agent").projectDir = file("../../core/agent")
include(":providers")
project(":providers").projectDir = file("../../core/providers")
include(":transport")
project(":transport").projectDir = file("../../core/transport")
include(":capabilities")
project(":capabilities").projectDir = file("capabilities")
include(":stores")
project(":stores").projectDir = file("stores")
include(":data")
project(":data").projectDir = file("data")

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
include(":knowledge")
project(":knowledge").projectDir = file("../../packages/knowledge")
include(":contracts")
project(":contracts").projectDir = file("../../packages/contracts")
include(":engine")
project(":engine").projectDir = file("../../packages/engine")
include(":net")
project(":net").projectDir = file("../../packages/net")
include(":capabilities")
project(":capabilities").projectDir = file("capabilities")
include(":stores")
project(":stores").projectDir = file("../../packages/stores")
include(":data")
project(":data").projectDir = file("data")

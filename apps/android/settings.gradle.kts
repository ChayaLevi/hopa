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
include(":turn")
project(":turn").projectDir = file("../../packages/turn")
include(":models")
project(":models").projectDir = file("../../packages/models")
include(":net")
project(":net").projectDir = file("../../packages/net")
include(":capabilities")
project(":capabilities").projectDir = file("capabilities")
include(":stores")
project(":stores").projectDir = file("stores")
include(":data")
project(":data").projectDir = file("data")

pluginManagement {
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
include(":testing")
include(":knowledge")
project(":knowledge").projectDir = file("../../packages/knowledge")
include(":engine")
project(":engine").projectDir = file("../../packages/engine")
include(":net")
project(":net").projectDir = file("../../packages/net")
include(":capabilities-android")
project(":capabilities-android").projectDir = file("capabilities")
include(":state")
project(":state").projectDir = file("../../packages/state")
include(":data-android")
project(":data-android").projectDir = file("data")

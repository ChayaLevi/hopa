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
include(":know")
project(":know").projectDir = file("../../packages/know")
include(":core")
project(":core").projectDir = file("../../packages/core")
include(":remote")
project(":remote").projectDir = file("../../packages/remote")
include(":capabilities-android")
project(":capabilities-android").projectDir = file("capabilities")
include(":data")
project(":data").projectDir = file("../../packages/data")
include(":data-android")
project(":data-android").projectDir = file("data")

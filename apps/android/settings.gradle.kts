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
include(":routing")
project(":routing").projectDir = file("../../packages/routing")
include(":observation")
project(":observation").projectDir = file("../../packages/observation")
include(":records")
project(":records").projectDir = file("../../packages/records")
include(":prompt")
project(":prompt").projectDir = file("../../packages/prompt")
include(":execution")
project(":execution").projectDir = file("../../packages/execution")
include(":turn")
project(":turn").projectDir = file("../../packages/turn")
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

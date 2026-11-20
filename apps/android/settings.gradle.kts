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
include(":runlog")
project(":runlog").projectDir = file("../../core/runlog")
include(":agent")
project(":agent").projectDir = file("../../core/agent")
include(":inference")
project(":inference").projectDir = file("../../core/inference")
include(":bridge")
project(":bridge").projectDir = file("../../core/bridge")
include(":backend")
project(":backend").projectDir = file("../../core/backend")
include(":capabilities")
project(":capabilities").projectDir = file("capabilities")
include(":stores")
project(":stores").projectDir = file("stores")
include(":data")
project(":data").projectDir = file("data")
include(":core:ui")
project(":core:ui").projectDir = file("core/ui")
include(":feature:usage")
project(":feature:usage").projectDir = file("feature/usage")
include(":feature:memory")
project(":feature:memory").projectDir = file("feature/memory")
include(":feature:skills")
project(":feature:skills").projectDir = file("feature/skills")
include(":feature:profiles")
project(":feature:profiles").projectDir = file("feature/profiles")

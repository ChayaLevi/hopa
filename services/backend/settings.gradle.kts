pluginManagement {
    includeBuild("../../build-logic")
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
            from(files("../../apps/android/gradle/libs.versions.toml"))
        }
    }
}

rootProject.name = "hopa-backend"

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
include(":decision")
project(":decision").projectDir = file("../../core/decision")
include(":decision-rules")
project(":decision-rules").projectDir = file("../../core/decision-rules")
include(":decision-classifier")
project(":decision-classifier").projectDir = file("../../core/decision-classifier")
include(":decision-graph")
project(":decision-graph").projectDir = file("../../core/decision-graph")
include(":inference")
project(":inference").projectDir = file("../../core/inference")

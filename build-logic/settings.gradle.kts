pluginManagement {
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
        // The ktlint plugin's artifact is published through the plugin portal.
        gradlePluginPortal()
    }
    versionCatalogs {
        create("libs") {
            from(files("../apps/android/gradle/libs.versions.toml"))
        }
    }
}

rootProject.name = "hopa-build-logic"

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
        // Facebook SDK is on mavenCentral now, but keep jitpack as a fallback
        // in case a transitive dependency needs it.
        maven("https://jitpack.io")
    }
}

rootProject.name = "digital-twins"
include(":app")

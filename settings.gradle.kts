pluginManagement {
    repositories {
        google()
        // Official Maven Central endpoint; the alternate hostname avoids regional CDN failures.
        maven("https://repo1.maven.org/maven2")
        mavenCentral()
        gradlePluginPortal()
    }
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories { google(); maven("https://repo1.maven.org/maven2"); mavenCentral() }
}
rootProject.name = "PauseCart"
include(":app")

pluginManagement {
    repositories {
        google()
        // Mirrors reduce CI failures when Maven Central redirects large Kotlin artifacts.
        maven { url = uri("https://maven.aliyun.com/repository/gradle-plugin") }
        maven { url = uri("https://maven.aliyun.com/repository/central") }
        gradlePluginPortal()
        mavenCentral()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        // Use independent Maven mirrors before Maven Central as a resilient fallback.
        maven { url = uri("https://maven.aliyun.com/repository/central") }
        maven { url = uri("https://repo.huaweicloud.com/repository/maven/") }
        mavenCentral()
    }
}

rootProject.name = "Reet"
include(":app")

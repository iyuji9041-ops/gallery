pluginManagement {
    repositories {
        val pocketMaven = file("/root/maven/localMvnRepository")
        if (pocketMaven.exists()) {
            maven { url = pocketMaven.toURI() }
        }
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        val pocketMaven = file("/root/maven/localMvnRepository")
        if (pocketMaven.exists()) {
            maven { url = pocketMaven.toURI() }
        }
        google()
        mavenCentral()
    }
}

rootProject.name = "Gallery"
include(":app")

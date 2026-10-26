pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
        // ← JitPack para los plugins (y para la dependencia escpos-android)
        maven { url = uri("https://jitpack.io") }
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
        // ← JitPack para todas las dependencias de los módulos
        maven { url = uri("https://jitpack.io") }
    }
}

rootProject.name = "My Application"
include(":app")

pluginManagement {
    repositories {
        mavenLocal()
        mavenCentral()
        gradlePluginPortal()
        maven("https://maven.fabricmc.net/")
        maven("https://maven.kikugie.dev/snapshots") { name = "KikuGie Snapshots" }
    }
}

plugins {
    id("dev.kikugie.stonecutter") version "0.8.3"
}

stonecutter {
    create(rootProject) {
        val supportsMc26 = org.gradle.api.JavaVersion.current()
            .isCompatibleWith(org.gradle.api.JavaVersion.VERSION_25)
        // See https://stonecutter.kikugie.dev/wiki/start/#choosing-minecraft-versions
        // 旧版本使用版本组(project != version)减少发布产物数量
        //  - :1.16.5 -> 构建基于 1.19.4，发布声明兼容 1.16.5
        //  - :1.17 -> 构建基于 1.19.4，发布声明兼容 1.17-1.17.1
        //  - :1.18 -> 构建基于 1.19.4，发布声明兼容 1.18-1.18.2
        //  - :1.19 -> 构建基于 1.19.4，发布声明兼容 1.19-1.19.4
        //version(project = "1.16.5", version = "1.19.4")
        //version(project = "1.17", version = "1.19.4")
        //version(project = "1.18", version = "1.19.4")
        //version(project = "1.19", version = "1.19.4")
        //versions("1.20", "1.20.1", "1.20.2", "1.20.3", "1.20.4", "1.20.5", "1.20.6", "1.21", "1.21.1", "1.21.2", "1.21.3", "1.21.4", "1.21.5", "1.21.6", "1.21.7", "1.21.8", "1.21.9", "1.21.10", "1.21.11")
        versions("1.21.11")
        if (supportsMc26) {
            version("26.1")
        }
        vcsVersion = "1.21.11"
    }
}

rootProject.name = "LumiChat"

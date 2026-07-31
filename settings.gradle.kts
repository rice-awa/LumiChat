pluginManagement {
    repositories {
        maven("https://mirrors.cloud.tencent.com/nexus/repository/maven-public/")
        maven("https://mirrors.cloud.tencent.com/nexus/repository/gradle-plugin/")
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
        // 仅保留已验证可构建的版本组，避免将 1.19.4 构建产物错误发布为 1.16.5-1.18.2 兼容
        version(project = "1.19", version = "1.19.4")
        versions("1.20", "1.20.1", "1.20.2", "1.20.3", "1.20.4", "1.20.5", "1.20.6", "1.21", "1.21.1", "1.21.2", "1.21.3", "1.21.4", "1.21.5", "1.21.6", "1.21.7", "1.21.8", "1.21.9", "1.21.10", "1.21.11")
        if (supportsMc26) {
            version("26.1")
            versions("26.1.1", "26.1.2")
            version("26.2")
        }
        vcsVersion = if (supportsMc26) "26.2" else "1.21.11"
    }
}

rootProject.name = "LumiChat"

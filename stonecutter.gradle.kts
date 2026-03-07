plugins {
    id("dev.kikugie.stonecutter")
    id("net.fabricmc.fabric-loom-remap") version "1.14-SNAPSHOT" apply false
    // id("me.modmuss50.mod-publish-plugin") version "1.0.+" apply false
}

// ============================================================================
// Stonecutter 版本配置说明
// ============================================================================
// `stonecutter active "1.21.11"` - 设置 IDE 开发时的活动版本
//   - 决定哪个版本的代码在 IDE 中可见和可调试
//   - 影响 Stonecutter 预处理后的代码生成
//   - 切换命令: ./gradlew setActiveVersion -Pversion=1.21.11
//
// `vcsVersion = "1.21.11"` (在 settings.gradle.kts 中) - VCS 版本
//   - resetActiveVersion 任务的重置目标版本
//   - 提交前应运行 resetActiveVersion 重置到此版本，避免生成临时状态
//   - 应与 active 版本保持一致以减少混淆
//
// 两者关系: active 用于开发，vcsVersion 用于版本控制重置
// 最佳实践: 提交前运行 `./gradlew resetActiveVersion` 重置到 vcsVersion
// ============================================================================
stonecutter active "1.21.11"

/*
// Make newer versions be published last
stonecutter tasks {
    order("publishModrinth")
    order("publishCurseforge")
}
 */

// Stonecutter 参数配置
// 文档: https://stonecutter.kikugie.dev/wiki/config/params
//
// 注意: 项目当前未使用 Stonecutter 的源码替换功能 (swaps/constants/dependencies)
// 原因:
// 1. fabric.mod.json 中的版本变量由 Gradle processResources 任务处理，无需 swaps
// 2. 业务代码中的 mod_version 通过 FabricLoader API 动态获取，无需编译时替换
// 3. 多版本兼容使用 //? 条件注释块处理，不使用字符串替换
//
// 如需启用源码替换，取消下方注释:
// stonecutter parameters {
//     // 在源码中使用: String version = /*$mod_version*/;
//     swaps["mod_version"] = "\"${property("mod.version")}\";"
//     // 在源码中使用: String mcVersion = /*$minecraft*/;
//     swaps["minecraft"] = "\"${node.metadata.version}\";"
//     // 在源码中使用: //? if release
//     constants["release"] = property("mod.id") != "template"
//     // 在源码中使用: //? dependencies fapi
//     dependencies["fapi"] = node.project.property("deps.fabric_api") as String
// }

plugins {
    id("dev.kikugie.stonecutter")
    id("net.fabricmc.fabric-loom-remap") version "1.17.21" apply false
    id("net.fabricmc.fabric-loom") version "1.17.21" apply false
    // id("me.modmuss50.mod-publish-plugin") version "1.0.+" apply false
}

// ============================================================================
// Stonecutter 版本配置说明
// ============================================================================
// 真实任务名（Stonecutter 0.8.3，均在 `stonecutter` 任务组下，必须加引号）：
//   ./gradlew "Set active project to <节点名>"   - 切到指定节点，处理全部版本注释
//   ./gradlew "Reset active project"             - 切回 vcsVersion，提交前执行
//   ./gradlew "Refresh active project"           - 对当前 active 重跑注释处理
// 旧文档里的 `setActiveVersion` / `resetActiveVersion` / `stonecutterReset`
// 在 0.8.3 中并不存在，执行会报 `Task not found`。
//
// `stonecutter active "1.21.11"` - 当前活动版本
//   - 决定共享 `src/` 中条件注释处于哪个版本的展开状态，也决定 IDE 里可见/可调试的代码
//   - 切换活动版本会**就地改写 `src/`**（由 stonecutterMerge<SourceSet> 完成），
//     以及本文件的 `active "..."` 字符串
//   - 在整个项目中必须且只能赋值一次
//   - 此处固定为 "1.21.11"，保证低 Java 环境也能加载项目
//
// `vcsVersion`（在 settings.gradle.kts 中）- VCS 重置点
//   - 仓库中 `src/` 的规范形态，也是 "Reset active project" 的目标
//   - 必须指向一个已注册节点，否则 Stonecutter 在配置阶段直接报错
//   - 因此它跟随 supportsMc26 条件：Java 25 环境下为最新节点（当前 "26.3"），
//     否则为 "1.21.11"
//   - 提交前运行 `./gradlew "Reset active project"` 把 src/ 重置回该形态
//
// 已知不一致：Java 25 机器上 vcsVersion 为 "26.3"，而 `src/` 实际提交的形态是
// "1.21.11"（与 active 一致）。此时 "Reset active project" 会把 src/ 改写成
// 26.3 形态并产生 diff，导致 scripts/check-before-commit.ps1 第 4 步的
// `git diff --exit-code` 门禁失败。
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

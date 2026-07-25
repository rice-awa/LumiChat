# A LLM Chat Mod for Minecraft

一个想让你眼前一亮的Minecraft Fabric模组，集成了LLM（大语言模型）聊天功能，支持多种AI服务和自定义功能。

## ✨ 核心功能

- 🤖 **多LLM服务支持** - OpenAI、OpenRouter、DeepSeek等，可扩展架构，强制健康检查
- 💬 **智能上下文管理** - 基于字符长度的精确控制，智能压缩，对话恢复
- 📝 **热编辑提示词模板系统** - 内置多种预设，支持游戏内热编辑，内置变量系统
- 🔧 **Tool Call** - 13个内置游戏API，智能权限控制
- 📚 **历史记录管理** - 持久化存储，多格式导出，统计分析
- 📊 **完善日志系统** - 多级别分类日志，异步处理，文件轮转
- 📢 **AI聊天广播** - OP可控的全服广播功能
- ⚙️ **配置管理** - 热重载，游戏内切换Provider和模型，健康状态监控
- 🧪 **测试策略** - 基础单元测试保障核心逻辑，复杂场景以游戏内实测为准

## 🚀 快速开始

### 1. 安装模组
将编译好的jar文件放入Minecraft的`mods`文件夹中。
> 你可以在项目的 [Release](https://github.com/riceawa/LumiChat/releases) 页面下载各版本的mod文件。或者在 [Actions artifacts](https://github.com/rice-awa/LumiChat/actions/workflows/build.yml) 中获取最新编译的jar文件

### 2. 配置API密钥
1. 首次使用时，尝试发送任意消息：`/llmchat 你好`
2. 系统会自动检测配置问题并显示详细的配置指导
3. 使用 `/llmchat setup` 查看配置向导
4. 编辑 `config/lumichat/config.json` 文件，添加你的API密钥
5. 使用 `/llmchat reload` 重载配置（需要OP权限）

> 📖 **详细配置指南**: 查看 [配置指南](docs/CONFIGURATION_GUIDE.md) 了解完整的配置选项和多Provider设置

## 💬 基本使用

### 基本聊天
```bash
/llmchat 你好，请介绍一下Minecraft的基本玩法
```

### 常用命令
```bash
/llmchat clear                    # 清空聊天历史
/llmchat resume                   # 恢复最近的对话内容
/llmchat resume list              # 列出所有历史对话记录
/llmchat resume 2                 # 恢复指定ID的对话（如#2）
/llmchat template set creative    # 切换到创造模式助手模板
/llmchat help                     # 显示帮助信息
```

### 提示词模板管理
```bash
/llmchat template list                      # 列出所有可用模板
/llmchat template set creative              # 切换到创造模式助手模板
/llmchat template create my_assistant       # 创建新的自定义模板
/llmchat template edit my_assistant         # 热编辑模板（进入编辑模式）
/llmchat template preview                   # 预览当前编辑的模板
/llmchat template save                      # 保存模板
```

### Provider健康检查
```bash
/llmchat provider list                      # 查看所有Provider状态（缓存）
/llmchat provider check                     # 强制检测所有Provider状态
/llmchat provider check openai             # 强制检测指定Provider状态
```

### 管理员命令（需要OP权限）
```bash
/llmchat provider switch openrouter        # 切换Provider
/llmchat model set gpt-4                   # 设置模型
/llmchat broadcast enable                  # 开启AI聊天广播
/llmchat reload                            # 重载配置文件
```

> 📖 **完整命令指南**: 查看 [命令指南](docs/COMMANDS_GUIDE.md) 了解所有可用命令和详细用法

## 📚 文档导航

### 构建和开发文档
- 🔧 [多版本构建指南](multiversionbuild.md) - Stonecutter 多版本构建完整指南

### 功能详细文档
- 📖 [配置指南](docs/CONFIGURATION_GUIDE.md) - 完整的配置选项和多Provider设置
- 💻 [命令指南](docs/COMMANDS_GUIDE.md) - 所有可用命令和详细用法（包含Resume命令扩展功能）
- 🔧 [热编辑模板系统](docs/features/TEMPLATE_HOT_EDITING_SYSTEM.md) - 游戏内模板编辑完整指南
- 🔧 [内置变量系统](docs/features/BUILTIN_VARIABLES_SYSTEM_SUMMARY.md) - 15+内置变量详细说明
- 🔍 [Provider健康检查](docs/features/PROVIDER_FORCE_CHECK_SYSTEM.md) - 强制检测和诊断系统
- 🔧 [Tool Call开发](docs/features/TOOL_CALL_DEVELOPMENT.md) - Tool Call功能详解
- 🛡️ [Tool Call安全](docs/features/TOOL_CALL_SECURITY.md) - 权限控制和安全机制
- 🎮 [Tool 演示](docs/features/FUNCTION_DEMO.md) - 实际使用示例和效果展示
- 📢 [广播功能](docs/features/BROADCAST_FEATURE.md) - AI聊天广播功能详解
- 🧠 [上下文管理](docs/features/CONTEXT_MANAGEMENT.md) - 智能上下文管理和压缩
- 📊 [日志和历史](docs/features/LOGGING_AND_HISTORY.md) - 日志系统和历史记录管理
- 🧪 [测试指南](docs/TESTING_GUIDE.md) - 基础单元测试与游戏内验证流程
- 🎯 [使用演示](docs/guides/DEMO_USAGE.md) - 实际使用场景和示例

## 🔧 Tool Call 功能概览

启用Tool Call后，AI可以主动调用游戏API获取实时信息和执行操作：

### 主要功能类别
- 🌍 **世界信息查询** - 获取世界状态、附近实体、天气时间等
- 👤 **玩家状态查询** - 查看生命值、背包、位置、状态效果等
- 🔍 **mcwiki查询** - 根据关键词查询mcwiki的内容，回复结果
- 🎮 **服务器信息** - 获取服务器状态、在线玩家、性能数据
- 💬 **交互功能** - 发送消息、广播通知等
- ⚡ **管理员功能** - 执行指令、设置方块、生成实体、控制环境（需OP权限）

### 使用示例
```bash
/llmchat 帮我查看一下当前的游戏状态
# AI会自动调用相关函数获取世界信息和玩家状态

/llmchat 附近有什么生物吗？
# AI会调用get_nearby_entities函数查询附近实体

/llmchat 我的背包里有什么物品？
# AI会调用get_inventory函数查看背包内容

/llmchat resume list
# 查看所有历史对话记录，选择要恢复的对话

/llmchat resume 3
# 恢复第3个历史对话，继续之前的讨论

# 创建个性化AI助手
/llmchat template create my_helper
/llmchat template edit system 你是{{player}}的专属助手，现在是{{time}}，你在{{dimension}}
/llmchat template var set specialty 建筑设计
/llmchat template save
/llmchat template set my_helper
# 现在AI会根据你的个性化设置进行回复
```

> 📖 **详细功能文档**: 查看 [Tool 演示](docs/features/FUNCTION_DEMO.md) 和 [Tool Call安全](docs/features/TOOL_CALL_SECURITY.md) 了解完整功能列表和安全机制

## 🧪 测试和开发

### 运行测试
```bash
./gradlew test                    # 运行所有测试
./gradlew test jacocoTestReport   # 生成测试报告
```

### 质量指标
- **代码质量得分**: 76.7% (B级标准)
- **测试覆盖率**: 核心功能100%覆盖
- **API兼容性**: 完全符合OpenAI标准

> 📖 **开发指南**: 查看 [测试指南](docs/TESTING_GUIDE.md) 了解完整的测试框架和开发规范

## 🧠 高级功能

### 🔥 热编辑提示词模板系统
- **游戏内热编辑** - 无需重启游戏，实时创建和编辑提示词模板
- **内置变量系统** - 支持 `{{player}}`、`{{time}}`、`{{x}}`、`{{y}}`、`{{z}}` 等15+内置变量
- **自定义变量** - 支持用户定义变量，如 `{{specialty}}`、`{{assistant_name}}`
- **实时预览** - 编辑过程中可随时预览模板效果和变量值
- **智能引导** - 详细的编辑菜单和操作提示
- **模板复制** - 支持复制现有模板进行快速定制
- **即时生效** - 模板切换后立即应用新的系统提示词

### 🔍 Provider健康检查系统
- **强制检测** - 支持强制检测所有或指定Provider的连接状态
- **智能诊断** - 根据错误类型提供针对性解决建议
- **超时优化** - 30秒检测超时，适应各种网络环境
- **状态缓存** - 5分钟智能缓存，平衡实时性和性能
- **错误分类** - 区分认证、网络、配置、API等不同错误类型

### 智能上下文管理
- **字符长度精确控制** - 基于实际字符数量而非消息数量，更精确的上下文管理
- **完整消息压缩** - 压缩完整消息（如1/2的消息），保持消息完整性
- **智能压缩算法** - AI驱动的上下文压缩，保留重要信息
- **对话恢复** - 使用 `/llmchat resume` 快速恢复上次对话
- **压缩通知** - 友好的用户提示，可配置开启/关闭
- **成本优化** - 支持配置专用压缩模型降低费用

### 增强的历史记录管理
- **智能会话列表** - 使用 `/llmchat resume list` 查看所有历史对话
- **精确对话恢复** - 通过数字ID（如 `/llmchat resume 2`）恢复指定对话
- **会话标题显示** - AI自动生成的对话标题，便于识别内容
- **时间和统计信息** - 显示对话时间、消息数量和使用的模板
- **对话预览** - 恢复对话时显示前几条消息预览

> ⚠️ **重要配置提醒**: 建议将 `maxContextCharacters` 设置为比模型默认上下文长度低的值，以确保系统有足够空间进行压缩和处理。例如，对于支持128k上下文的模型，建议设置为100,000字符。

> 📖 **详细功能文档**: 查看 [上下文管理](docs/features/CONTEXT_MANAGEMENT.md) 了解完整的上下文管理功能

## 📋 项目信息

### 依赖项
- Fabric API
- OkHttp3 (HTTP客户端)
- Gson (JSON处理)
- Typesafe Config (配置管理)

### 许可证
本项目采用 [MIT](./LICENSE) 许可证。

### 贡献
欢迎提交Issue和Pull Request来改进这个模组！

### ⚠️ 重要提醒
1. 请确保你有有效的API密钥才能使用LLM功能
2. API调用可能产生费用，请注意使用量
3. 管理员功能需要OP权限，包括执行指令、设置方块、生成实体等
4. AI聊天广播默认关闭，OP可根据需要开启
5. 建议配置专用压缩模型（如gpt-4o-mini）降低费用
6. **重要**: 请将 `maxContextCharacters` 设置为比模型默认上下文长度低的值，为压缩和处理预留空间
7. 使用 `/llmchat provider check` 定期检查Provider连接状态
8. 热编辑模板时使用 `{{变量名}}` 格式引用内置和自定义变量
9. execute_command 采用显式允许列表（非黑名单），默认关闭，需管理员审核后启用

## 📖 开发参考资料

- [Fabric Commands API](https://fabricmc.net/wiki/tutorial:commands) — 命令注册与权限
- [Gradle Toolchains](https://docs.gradle.org/current/userguide/toolchains.html) — 跨 Java 版本编译
- [Stonecutter Docs](https://stonecutter.kikugie.dev/) — 多版本构建框架
- [OkHttp](https://square.github.io/okhttp/) — HTTP 客户端与 MockWebServer
- [Mojang Mappings](https://fabricmc.net/wiki/tutorial:migrating_to_mojang_mappings) — Mojang 官方映射迁移指南
- `docs/api/Notable_Minecraft_changes.md` — 跨版本 Minecraft API 破坏性变更参考

## 📝 更新日志

### v2.2.0 (2026-07-25) - 最新版本
- 🎮 **Minecraft 26.x 版本扩展** - 新增 26.1.1、26.1.2、26.2 支持，拆分 26.1 版本组（26.1 独立，26.1.1 与 26.1.2 合并）
- 🛠️ **CI/CD 优化** - 单 job buildAndCollect 多版本构建对齐 Stonecutter 官方模板，恢复 dev-build 多版本并行构建，新增 opencode 工作流
- 🔧 **构建修复** - 移除不存在的 foojay-resolver-convention 插件（Gradle 9.4 已内置 toolchain 供应），补充缺失的 kotlin-stdlib 依赖
- 🏷️ **术语统一** - 统一工具调用配置中的模组ID术语，补充英文模组描述
- 📝 **文档清理** - 删除临时开发文档，优化 agent 配置文档

### v2.1.0 (2026-06-19)
- 🔥 **迁移至 Mojang mappings** - 从 Yarn mappings 迁移到 Mojang/official mappings，覆盖命令、上下文、Tool Call、模板、Mixin 与兼容层等核心代码。
- 📦 **多版本构建恢复** - 验证 1.19 版本组、1.20-1.20.6、1.21-1.21.11，以及可选的 26.1/26.2 构建节点。
- 🛠️ **构建工具升级** - Fabric Loom/remap 插件升级到 1.15-SNAPSHOT，Gradle Wrapper 升级到 9.4.0，并完善 Mojang mappings 与 26.1 unobfuscated 构建分流。
- ☕ **Java 版本矩阵明确化** - 26.1 使用 Java 25，1.20.5+ 使用 Java 21，1.18+ 使用 Java 17，1.17 使用 Java 16，旧版本使用 Java 8。
- ✅ **迁移验证完成** - 已验证代表性节点 `:1.19:build`、`:1.20.6:build`、`:1.21.11:build`、`buildAndCollect`，并完成 1.21.11 与 26.1 服务端启动 smoke test。

### v2.0.1 (2026-03-16)
- 🔥 **更多版本矩阵支持** - 添加对 1.19-1.19.4、1.20-1.20.6 与 1.21-1.21.11 跨代构建的支持（含 Stonecutter 版本组）
- 📦 **Stonecutter 架构优化** - 重构多版本构建架构，提升构建效率
- 🛠️ **Mixin 配置修复** - 修复客户端 mixin 配置未打包问题，补充 datagen 依赖
- ⚙️ **兼容性增强** - 修复全版本 compatibilityLevel 兼容矩阵，确保各版本正常运行
- 📝 **Java 字节码目标** - 修复 1.16.5 等低版本的 Java 字节码目标版本问题

### v2.0.0 (2026-02-21)
- 🔥 **Stonecutter 多版本支持** - 使用 Stonecutter 框架支持 1.21-1.21.11 多版本构建
- 🔥 **项目重构** - 模组ID从 `lumichat` 统一为 `lumichat`，项目名统一为 `LumiChat`
- 📚 **构建指南** - 新增多版本构建完整文档
- ⚡ **架构升级** - 全面迁移至 Kotlin DSL 和 Stonecutter 多版本架构

### v1.8.1 (2026-02-20)
- 🔥 **迁移至 Fabric 1.21.11** - 完整适配 Minecraft 1.21.11 版本
- 🛡️ **PvP 状态获取修复** - 修复 1.21.11 版本中 PvP 状态获取代码的兼容性问题
- 📚 **迁移文档** - 新增详细的 1.21.11 迁移指南
- ⚡ **依赖更新** - 更新所有依赖版本以匹配新平台

### v1.7.1 (2025-08-04)
- 🔥 **工具调用提示信息显示修复** - 修复多轮工具调用时LLM提示信息未显示的问题
- ✨ **完善的交互体验** - AI执行函数前的思考过程现在会正确显示给玩家
- 🎯 **符合OpenAI API规范** - 正确实现content和tool_calls的处理顺序
- 🛡️ **保持向后兼容** - 不影响现有功能，支持递归和非递归工具调用场景
- 📚 **详细修复文档** - 新增工具调用显示修复的完整技术文档

### v1.7.0 (2025-07-30)
- 🔥 **热编辑提示词模板系统** - 游戏内实时创建和编辑提示词模板
- 🔥 **内置变量系统** - 支持15+内置变量（玩家名、时间、坐标、游戏状态等）
- 🔥 **Provider强制检测系统** - 30秒超时的强制健康检查，智能错误诊断
- ✨ 新增模板热编辑命令：`create`、`edit`、`preview`、`save`、`var`等
- ✨ 新增Provider检测命令：`/llmchat provider check [provider]`
- 🎯 **模板切换系统提示词修复** - 切换模板后立即应用新的系统提示词
- 📋 **智能编辑引导** - 详细的编辑菜单和操作提示
- 🛡️ **变量实时预览** - 编辑时显示所有变量的当前值
- 📚 **完整文档支持** - 新增热编辑系统和健康检查文档

### v1.6.1 (2025-07-28)
- 🔥 **Resume命令功能扩展** - 全面升级的历史对话管理系统
- ✨ 新增 `/llmchat resume list` - 列出所有历史对话记录，显示标题和详细信息
- ✨ 新增 `/llmchat resume <数字>` - 通过简单的数字ID精确恢复指定对话
- 🎯 **智能会话索引** - 最新对话为#1，直观的数字ID系统
- 📋 **丰富的会话信息** - 显示AI生成的标题、时间戳、消息数量和模板
- 🛡️ **完善的错误处理** - 友好的用户提示和异常处理机制
- 📚 **完整文档支持** - 新增详细的使用指南和优化总结文档

### v1.6.0 (2025-07-27)
- 🔥 **智能上下文管理升级** - 60k默认上下文长度，智能压缩替代简单删除
- 🔥 **压缩通知系统** - 友好的用户提示，可配置开启/关闭
- 🔥 **自定义压缩模型** - 支持配置专用压缩模型，优化成本控制
- ✨ 新增 `/llmchat resume` 命令 - 快速恢复上次对话内容
- 💰 成本优化：聊天用高质量模型，压缩用经济模型
- 🛡️ 优化聊天逻辑和providers切换逻辑

### v1.5.0 (2025-07-25)
- 🔥 **强大的管理员功能** - 6个新的管理员专用Tool Call功能
- 🔥 **统一权限管理系统** - PermissionHelper工具类，多层安全保护
- 🔥 **执行指令功能** - 安全地执行服务器指令，指令黑名单保护
- ✨ 新增世界操作功能：设置方块、生成实体、传送玩家
- ✨ 新增环境控制功能：天气控制、时间控制

### v1.4.0 (2025-07-25)
- 🔥 **完善的日志系统** - 多级别、分类、异步日志记录
- 🔥 **增强的历史记录管理** - 统计分析、多格式导出、高级搜索
- 🔥 **性能监控** - 详细的API响应时间和资源使用监控

### 历史版本
查看完整的版本历史和详细更新内容，请参考项目的Git提交记录。

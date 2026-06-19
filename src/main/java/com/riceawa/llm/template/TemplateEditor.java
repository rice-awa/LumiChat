package com.riceawa.llm.template;

import com.riceawa.llm.util.EntityHelper;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 提示词模板编辑器
 * 管理玩家的模板编辑会话
 */
public class TemplateEditor {
    private static TemplateEditor instance;
    private final Map<UUID, EditSession> editSessions;

    private TemplateEditor() {
        this.editSessions = new ConcurrentHashMap<>();
    }

    public static TemplateEditor getInstance() {
        if (instance == null) {
            synchronized (TemplateEditor.class) {
                if (instance == null) {
                    instance = new TemplateEditor();
                }
            }
        }
        return instance;
    }

    /**
     * 开始编辑会话
     */
    public void startEditSession(Player player, String templateId, boolean isNewTemplate) {
        UUID playerId = player.getUUID();
        
        // 如果已有编辑会话，先结束
        if (editSessions.containsKey(playerId)) {
            endEditSession(player);
        }

        PromptTemplateManager templateManager = PromptTemplateManager.getInstance();
        PromptTemplate template;

        if (isNewTemplate) {
            // 创建新模板
            template = new PromptTemplate(templateId, "新模板", "用户创建的模板", "");
            player.displayClientMessage(Component.literal("✨ 开始创建新模板: " + templateId).withStyle(ChatFormatting.GREEN), false);
        } else {
            // 编辑现有模板
            template = templateManager.getTemplate(templateId);
            if (template == null) {
                player.displayClientMessage(Component.literal("❌ 模板不存在: " + templateId).withStyle(ChatFormatting.RED), false);
                return;
            }
            player.displayClientMessage(Component.literal("✏️ 开始编辑模板: " + templateId).withStyle(ChatFormatting.GREEN), false);
        }

        // 创建编辑会话
        EditSession session = new EditSession(templateId, template.copy(), isNewTemplate);
        editSessions.put(playerId, session);

        // 显示编辑菜单
        showEditMenu(player);
    }

    /**
     * 结束编辑会话
     */
    public void endEditSession(Player player) {
        UUID playerId = player.getUUID();
        EditSession session = editSessions.remove(playerId);
        
        if (session != null) {
            player.displayClientMessage(Component.literal("📝 编辑会话已结束").withStyle(ChatFormatting.YELLOW), false);
        }
    }

    /**
     * 获取编辑会话
     */
    public EditSession getEditSession(Player player) {
        return editSessions.get(player.getUUID());
    }

    /**
     * 检查玩家是否在编辑模式
     */
    public boolean isEditing(Player player) {
        return editSessions.containsKey(player.getUUID());
    }

    /**
     * 显示编辑菜单
     */
    public void showEditMenu(Player player) {
        EditSession session = getEditSession(player);
        if (session == null) {
            return;
        }

        PromptTemplate template = session.getTemplate();
        
        player.displayClientMessage(Component.literal("").withStyle(ChatFormatting.GRAY), false);
        player.displayClientMessage(Component.literal("=== 模板编辑器 ===").withStyle(ChatFormatting.GOLD), false);
        player.displayClientMessage(Component.literal("模板ID: " + template.getId()).withStyle(ChatFormatting.AQUA), false);
        player.displayClientMessage(Component.literal("模板名称: " + template.getName()).withStyle(ChatFormatting.AQUA), false);
        player.displayClientMessage(Component.literal("").withStyle(ChatFormatting.GRAY), false);
        
        player.displayClientMessage(Component.literal("📝 编辑选项:").withStyle(ChatFormatting.YELLOW), false);
        player.displayClientMessage(Component.literal("  1️⃣ /llmchat template edit name <新名称> - 修改模板名称").withStyle(ChatFormatting.WHITE), false);
        player.displayClientMessage(Component.literal("  2️⃣ /llmchat template edit desc <新描述> - 修改模板描述").withStyle(ChatFormatting.WHITE), false);
        player.displayClientMessage(Component.literal("  3️⃣ /llmchat template edit system <系统提示词> - 修改系统提示词").withStyle(ChatFormatting.WHITE), false);
        player.displayClientMessage(Component.literal("  4️⃣ /llmchat template edit prefix <用户前缀> - 修改用户消息前缀").withStyle(ChatFormatting.WHITE), false);
        player.displayClientMessage(Component.literal("  5️⃣ /llmchat template edit suffix <用户后缀> - 修改用户消息后缀").withStyle(ChatFormatting.WHITE), false);
        player.displayClientMessage(Component.literal("").withStyle(ChatFormatting.GRAY), false);
        
        player.displayClientMessage(Component.literal("🔧 变量管理:").withStyle(ChatFormatting.YELLOW), false);
        player.displayClientMessage(Component.literal("  📋 /llmchat template var list - 列出所有变量").withStyle(ChatFormatting.WHITE), false);
        player.displayClientMessage(Component.literal("  ➕ /llmchat template var set <名称> <值> - 设置自定义变量").withStyle(ChatFormatting.WHITE), false);
        player.displayClientMessage(Component.literal("  ➖ /llmchat template var remove <名称> - 删除自定义变量").withStyle(ChatFormatting.WHITE), false);
        player.displayClientMessage(Component.literal("").withStyle(ChatFormatting.GRAY), false);

        player.displayClientMessage(Component.literal("🔍 其他操作:").withStyle(ChatFormatting.YELLOW), false);
        player.displayClientMessage(Component.literal("  👁️ /llmchat template preview - 预览当前模板").withStyle(ChatFormatting.WHITE), false);
        player.displayClientMessage(Component.literal("  💾 /llmchat template save - 保存并应用模板").withStyle(ChatFormatting.WHITE), false);
        player.displayClientMessage(Component.literal("  ❌ /llmchat template cancel - 取消编辑").withStyle(ChatFormatting.WHITE), false);
        player.displayClientMessage(Component.literal("").withStyle(ChatFormatting.GRAY), false);

        player.displayClientMessage(Component.literal("💡 变量使用提示:").withStyle(ChatFormatting.YELLOW), false);
        player.displayClientMessage(Component.literal("  • 使用 {{变量名}} 格式在模板中引用变量").withStyle(ChatFormatting.GRAY), false);
        player.displayClientMessage(Component.literal("  • 内置变量: {{player}}, {{time}}, {{date}}, {{x}}, {{y}}, {{z}} 等").withStyle(ChatFormatting.GRAY), false);
        player.displayClientMessage(Component.literal("  • 内置变量会自动获取当前值，无需手动设置").withStyle(ChatFormatting.GRAY), false);
        player.displayClientMessage(Component.literal("  • 使用 preview 命令查看所有可用变量及其当前值").withStyle(ChatFormatting.GRAY), false);
    }

    /**
     * 预览模板
     */
    public void previewTemplate(Player player) {
        EditSession session = getEditSession(player);
        if (session == null) {
            player.displayClientMessage(Component.literal("❌ 没有正在编辑的模板").withStyle(ChatFormatting.RED), false);
            return;
        }

        PromptTemplate template = session.getTemplate();
        
        player.displayClientMessage(Component.literal("").withStyle(ChatFormatting.GRAY), false);
        player.displayClientMessage(Component.literal("=== 模板预览 ===").withStyle(ChatFormatting.GOLD), false);
        player.displayClientMessage(Component.literal("ID: " + template.getId()).withStyle(ChatFormatting.AQUA), false);
        player.displayClientMessage(Component.literal("名称: " + template.getName()).withStyle(ChatFormatting.AQUA), false);
        player.displayClientMessage(Component.literal("描述: " + template.getDescription()).withStyle(ChatFormatting.AQUA), false);
        player.displayClientMessage(Component.literal("").withStyle(ChatFormatting.GRAY), false);
        
        player.displayClientMessage(Component.literal("📋 系统提示词:").withStyle(ChatFormatting.YELLOW), false);
        String systemPrompt = template.getSystemPrompt();
        if (systemPrompt != null && !systemPrompt.trim().isEmpty()) {
            // 分行显示长文本
            String[] lines = systemPrompt.split("\n");
            for (String line : lines) {
                if (line.length() > 80) {
                    // 长行分割显示
                    for (int i = 0; i < line.length(); i += 80) {
                        int end = Math.min(i + 80, line.length());
                        player.displayClientMessage(Component.literal("  " + line.substring(i, end)).withStyle(ChatFormatting.WHITE), false);
                    }
                } else {
                    player.displayClientMessage(Component.literal("  " + line).withStyle(ChatFormatting.WHITE), false);
                }
            }
        } else {
            player.displayClientMessage(Component.literal("  (未设置)").withStyle(ChatFormatting.GRAY), false);
        }
        
        player.displayClientMessage(Component.literal("").withStyle(ChatFormatting.GRAY), false);
        player.displayClientMessage(Component.literal("📝 用户消息前缀:").withStyle(ChatFormatting.YELLOW), false);
        String prefix = template.getUserPromptPrefix();
        if (prefix != null && !prefix.trim().isEmpty()) {
            player.displayClientMessage(Component.literal("  " + prefix).withStyle(ChatFormatting.WHITE), false);
        } else {
            player.displayClientMessage(Component.literal("  (未设置)").withStyle(ChatFormatting.GRAY), false);
        }
        
        player.displayClientMessage(Component.literal("📝 用户消息后缀:").withStyle(ChatFormatting.YELLOW), false);
        String suffix = template.getUserPromptSuffix();
        if (suffix != null && !suffix.trim().isEmpty()) {
            player.displayClientMessage(Component.literal("  " + suffix).withStyle(ChatFormatting.WHITE), false);
        } else {
            player.displayClientMessage(Component.literal("  (未设置)").withStyle(ChatFormatting.GRAY), false);
        }
        
        player.displayClientMessage(Component.literal("").withStyle(ChatFormatting.GRAY), false);

        // 显示内置变量
        player.displayClientMessage(Component.literal("🔧 内置变量 (自动获取):").withStyle(ChatFormatting.YELLOW), false);
        player.displayClientMessage(Component.literal("  {{player}} = " + player.getName().getString() + " (玩家名)").withStyle(ChatFormatting.GREEN), false);
        player.displayClientMessage(Component.literal("  {{time}} = " + java.time.LocalDateTime.now().format(
            java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")) + " (当前时间)").withStyle(ChatFormatting.GREEN), false);
        player.displayClientMessage(Component.literal("  {{date}} = " + java.time.LocalDate.now().format(
            java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd")) + " (当前日期)").withStyle(ChatFormatting.GREEN), false);
        player.displayClientMessage(Component.literal("  {{world}} = " + EntityHelper.getWorld(player).dimension().identifier().toString() + " (世界)").withStyle(ChatFormatting.GREEN), false);
        player.displayClientMessage(Component.literal("  {{x}}, {{y}}, {{z}} = " + (int)player.getX() + ", " + (int)player.getY() + ", " + (int)player.getZ() + " (坐标)").withStyle(ChatFormatting.GREEN), false);
        player.displayClientMessage(Component.literal("  {{health}}, {{level}} = " + (int)player.getHealth() + ", " + player.experienceLevel + " (生命值, 等级)").withStyle(ChatFormatting.GREEN), false);
        player.displayClientMessage(Component.literal("  更多内置变量: {{hour}}, {{minute}}, {{dimension}}, {{gamemode}}, {{weather}}, {{server}}").withStyle(ChatFormatting.GREEN), false);

        player.displayClientMessage(Component.literal("").withStyle(ChatFormatting.GRAY), false);
        player.displayClientMessage(Component.literal("🔧 自定义变量 (" + template.getVariables().size() + "个):").withStyle(ChatFormatting.YELLOW), false);
        if (!template.getVariables().isEmpty()) {
            for (Map.Entry<String, String> entry : template.getVariables().entrySet()) {
                player.displayClientMessage(Component.literal("  {{" + entry.getKey() + "}} = " + entry.getValue()).withStyle(ChatFormatting.AQUA), false);
            }
        } else {
            player.displayClientMessage(Component.literal("  (无自定义变量)").withStyle(ChatFormatting.GRAY), false);
        }

        player.displayClientMessage(Component.literal("").withStyle(ChatFormatting.GRAY), false);
    }

    /**
     * 保存模板
     */
    public void saveTemplate(Player player) {
        EditSession session = getEditSession(player);
        if (session == null) {
            player.displayClientMessage(Component.literal("❌ 没有正在编辑的模板").withStyle(ChatFormatting.RED), false);
            return;
        }

        try {
            PromptTemplateManager templateManager = PromptTemplateManager.getInstance();
            PromptTemplate template = session.getTemplate();
            
            if (session.isNewTemplate()) {
                templateManager.addTemplate(template);
                player.displayClientMessage(Component.literal("✅ 新模板已创建并保存: " + template.getId()).withStyle(ChatFormatting.GREEN), false);
            } else {
                templateManager.updateTemplate(template);
                player.displayClientMessage(Component.literal("✅ 模板已更新并保存: " + template.getId()).withStyle(ChatFormatting.GREEN), false);
            }
            
            // 结束编辑会话
            endEditSession(player);
            
            player.displayClientMessage(Component.literal("💡 使用 /llmchat template set " + template.getId() + " 来应用此模板").withStyle(ChatFormatting.GRAY), false);
            
        } catch (Exception e) {
            player.displayClientMessage(Component.literal("❌ 保存模板失败: " + e.getMessage()).withStyle(ChatFormatting.RED), false);
        }
    }

    /**
     * 编辑会话类
     */
    public static class EditSession {
        private final String templateId;
        private final PromptTemplate template;
        private final boolean isNewTemplate;

        public EditSession(String templateId, PromptTemplate template, boolean isNewTemplate) {
            this.templateId = templateId;
            this.template = template;
            this.isNewTemplate = isNewTemplate;
        }

        public String getTemplateId() {
            return templateId;
        }

        public PromptTemplate getTemplate() {
            return template;
        }

        public boolean isNewTemplate() {
            return isNewTemplate;
        }
    }
}

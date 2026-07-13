package com.riceawa.llm.template;

import com.riceawa.llm.compat.MessageCompat;
import com.riceawa.llm.util.EntityHelper;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;
import java.util.regex.Pattern;

/**
 * 提示词模板编辑器
 * 管理玩家的模板编辑会话
 */
public class TemplateEditor {
    private static final int MAX_NAME_LENGTH = 64;
    private static final int MAX_DESCRIPTION_LENGTH = 512;
    private static final int MAX_SYSTEM_PROMPT_LENGTH = 8192;
    private static final int MAX_AFFIX_LENGTH = 512;
    private static final int MAX_VARIABLE_VALUE_LENGTH = 2048;
    private static final Pattern SAFE_IDENTIFIER_PATTERN = Pattern.compile("[A-Za-z0-9_.-]{1,64}");

    private static volatile TemplateEditor instance;
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
    public boolean startEditSession(Player player, String templateId, boolean isNewTemplate) {
        String idError = validateTemplateId(templateId);
        if (idError != null) {
            MessageCompat.displayClientMessage(player, Component.literal("❌ " + idError).withStyle(ChatFormatting.RED), false);
            return false;
        }

        UUID playerId = player.getUUID();
        PromptTemplateManager templateManager = PromptTemplateManager.getInstance();
        PromptTemplate template;

        if (isNewTemplate) {
            // 创建新模板
            template = new PromptTemplate(templateId, "新模板", "用户创建的模板", "");
            MessageCompat.displayClientMessage(player, Component.literal("✨ 开始创建新模板: " + templateId).withStyle(ChatFormatting.GREEN), false);
        } else {
            // 编辑现有模板
            template = templateManager.getTemplate(templateId);
            if (template == null) {
                MessageCompat.displayClientMessage(player, Component.literal("❌ 模板不存在: " + templateId).withStyle(ChatFormatting.RED), false);
                return false;
            }
            MessageCompat.displayClientMessage(player, Component.literal("✏️ 开始编辑模板: " + templateId).withStyle(ChatFormatting.GREEN), false);
        }

        // 只有新会话已确认有效后，才替换现有编辑会话
        if (editSessions.containsKey(playerId)) {
            endEditSession(player);
        }

        // 创建编辑会话
        EditSession session = new EditSession(templateId, template.copy(), isNewTemplate);
        editSessions.put(playerId, session);

        // 显示编辑菜单
        showEditMenu(player);
        return true;
    }

    public static String validateTemplateId(String templateId) {
        if (templateId == null || templateId.isEmpty()) {
            return "模板ID不能为空";
        }
        if (templateId.length() > 64) {
            return "模板ID长度不能超过64个字符";
        }
        if (!SAFE_IDENTIFIER_PATTERN.matcher(templateId).matches()) {
            return "模板ID只能包含英文字母、数字、下划线、点和连字符";
        }
        return null;
    }

    public static String validateName(String name) {
        if (name == null || name.isBlank()) {
            return "模板名称不能为空";
        }
        if (name.length() > MAX_NAME_LENGTH) {
            return "模板名称长度不能超过64个字符";
        }
        return null;
    }

    public static String validateDescription(String description) {
        if (description != null && description.length() > MAX_DESCRIPTION_LENGTH) {
            return "模板描述长度不能超过512个字符";
        }
        return null;
    }

    public static String validateSystemPrompt(String systemPrompt) {
        if (systemPrompt != null && systemPrompt.length() > MAX_SYSTEM_PROMPT_LENGTH) {
            return "系统提示词长度不能超过8192个字符";
        }
        return null;
    }

    public static String validatePrefix(String prefix) {
        if (prefix != null && prefix.length() > MAX_AFFIX_LENGTH) {
            return "用户消息前缀长度不能超过512个字符";
        }
        return null;
    }

    public static String validateSuffix(String suffix) {
        if (suffix != null && suffix.length() > MAX_AFFIX_LENGTH) {
            return "用户消息后缀长度不能超过512个字符";
        }
        return null;
    }

    public static String validateVariable(String name, String value) {
        if (name == null || name.isEmpty()) {
            return "变量名不能为空";
        }
        if (name.length() > 64) {
            return "变量名长度不能超过64个字符";
        }
        if (!SAFE_IDENTIFIER_PATTERN.matcher(name).matches()) {
            return "变量名只能包含英文字母、数字、下划线、点和连字符";
        }
        if (value == null) {
            return "变量值不能为null";
        }
        if (value.length() > MAX_VARIABLE_VALUE_LENGTH) {
            return "变量值长度不能超过2048个字符";
        }
        return null;
    }

    public static String validateTemplate(PromptTemplate template) {
        if (template == null) {
            return "模板不能为空";
        }

        String error = validateTemplateId(template.getId());
        if (error == null) error = validateName(template.getName());
        if (error == null) error = validateDescription(template.getDescription());
        if (error == null) error = validateSystemPrompt(template.getSystemPrompt());
        if (error == null) error = validatePrefix(template.getUserPromptPrefix());
        if (error == null) error = validateSuffix(template.getUserPromptSuffix());
        if (error != null) {
            return error;
        }

        try {
            for (Map.Entry<String, String> variable : template.getVariables().entrySet()) {
                error = validateVariable(variable.getKey(), variable.getValue());
                if (error != null) {
                    return error;
                }
            }
        } catch (RuntimeException exception) {
            return "模板变量不能为空";
        }
        return null;
    }

    public String updateName(Player player, String name) {
        return update(player, () -> validateName(name), template -> template.setName(name));
    }

    public String updateDescription(Player player, String description) {
        return update(player, () -> validateDescription(description), template -> template.setDescription(description));
    }

    public String updateSystemPrompt(Player player, String systemPrompt) {
        return update(player, () -> validateSystemPrompt(systemPrompt), template -> template.setSystemPrompt(systemPrompt));
    }

    public String updatePrefix(Player player, String prefix) {
        return update(player, () -> validatePrefix(prefix), template -> template.setUserPromptPrefix(prefix));
    }

    public String updateSuffix(Player player, String suffix) {
        return update(player, () -> validateSuffix(suffix), template -> template.setUserPromptSuffix(suffix));
    }

    public String setVariable(Player player, String name, String value) {
        return update(player, () -> validateVariable(name, value), template -> template.setVariable(name, value));
    }

    public String removeVariable(Player player, String name) {
        EditSession session = getEditSession(player);
        if (session == null) {
            return "没有正在编辑的模板，请先使用 /llmchat template edit <模板ID>";
        }
        String validationError = validateVariable(name, "");
        if (validationError != null) {
            return validationError;
        }
        if (!session.getTemplate().getVariables().containsKey(name)) {
            return "变量不存在: " + name;
        }
        session.getTemplate().removeVariable(name);
        return null;
    }

    public String copyTemplate(String fromId, String toId) {
        String idError = validateTemplateId(toId);
        if (idError != null) {
            return idError;
        }

        PromptTemplateManager templateManager = PromptTemplateManager.getInstance();
        PromptTemplate sourceTemplate = templateManager.getTemplate(fromId);
        if (sourceTemplate == null) {
            return "源模板不存在: " + fromId;
        }
        if (templateManager.hasTemplate(toId)) {
            return "目标模板已存在: " + toId;
        }

        PromptTemplate newTemplate = sourceTemplate.copy();
        newTemplate.setId(toId);
        newTemplate.setName(sourceTemplate.getName() + " (副本)");
        String validationError = validateTemplate(newTemplate);
        if (validationError != null) {
            return validationError;
        }
        templateManager.addTemplate(newTemplate);
        return null;
    }

    private String update(Player player, Supplier<String> validator, TemplateMutation mutation) {
        EditSession session = getEditSession(player);
        if (session == null) {
            return "没有正在编辑的模板，请先使用 /llmchat template edit <模板ID>";
        }
        String validationError = validator.get();
        if (validationError != null) {
            return validationError;
        }
        mutation.apply(session.getTemplate());
        return null;
    }

    @FunctionalInterface
    private interface TemplateMutation {
        void apply(PromptTemplate template);
    }

    /**
     * 结束编辑会话
     */
    public void endEditSession(Player player) {
        UUID playerId = player.getUUID();
        EditSession session = editSessions.remove(playerId);
        
        if (session != null) {
            MessageCompat.displayClientMessage(player, Component.literal("📝 编辑会话已结束").withStyle(ChatFormatting.YELLOW), false);
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
        
        MessageCompat.displayClientMessage(player, Component.literal("").withStyle(ChatFormatting.GRAY), false);
        MessageCompat.displayClientMessage(player, Component.literal("=== 模板编辑器 ===").withStyle(ChatFormatting.GOLD), false);
        MessageCompat.displayClientMessage(player, Component.literal("模板ID: " + template.getId()).withStyle(ChatFormatting.AQUA), false);
        MessageCompat.displayClientMessage(player, Component.literal("模板名称: " + template.getName()).withStyle(ChatFormatting.AQUA), false);
        MessageCompat.displayClientMessage(player, Component.literal("").withStyle(ChatFormatting.GRAY), false);
        
        MessageCompat.displayClientMessage(player, Component.literal("📝 编辑选项:").withStyle(ChatFormatting.YELLOW), false);
        MessageCompat.displayClientMessage(player, Component.literal("  1️⃣ /llmchat template edit name <新名称> - 修改模板名称").withStyle(ChatFormatting.WHITE), false);
        MessageCompat.displayClientMessage(player, Component.literal("  2️⃣ /llmchat template edit desc <新描述> - 修改模板描述").withStyle(ChatFormatting.WHITE), false);
        MessageCompat.displayClientMessage(player, Component.literal("  3️⃣ /llmchat template edit system <系统提示词> - 修改系统提示词").withStyle(ChatFormatting.WHITE), false);
        MessageCompat.displayClientMessage(player, Component.literal("  4️⃣ /llmchat template edit prefix <用户前缀> - 修改用户消息前缀").withStyle(ChatFormatting.WHITE), false);
        MessageCompat.displayClientMessage(player, Component.literal("  5️⃣ /llmchat template edit suffix <用户后缀> - 修改用户消息后缀").withStyle(ChatFormatting.WHITE), false);
        MessageCompat.displayClientMessage(player, Component.literal("").withStyle(ChatFormatting.GRAY), false);
        
        MessageCompat.displayClientMessage(player, Component.literal("🔧 变量管理:").withStyle(ChatFormatting.YELLOW), false);
        MessageCompat.displayClientMessage(player, Component.literal("  📋 /llmchat template var list - 列出所有变量").withStyle(ChatFormatting.WHITE), false);
        MessageCompat.displayClientMessage(player, Component.literal("  ➕ /llmchat template var set <名称> <值> - 设置自定义变量").withStyle(ChatFormatting.WHITE), false);
        MessageCompat.displayClientMessage(player, Component.literal("  ➖ /llmchat template var remove <名称> - 删除自定义变量").withStyle(ChatFormatting.WHITE), false);
        MessageCompat.displayClientMessage(player, Component.literal("").withStyle(ChatFormatting.GRAY), false);

        MessageCompat.displayClientMessage(player, Component.literal("🔍 其他操作:").withStyle(ChatFormatting.YELLOW), false);
        MessageCompat.displayClientMessage(player, Component.literal("  👁️ /llmchat template preview - 预览当前模板").withStyle(ChatFormatting.WHITE), false);
        MessageCompat.displayClientMessage(player, Component.literal("  💾 /llmchat template save - 保存并应用模板").withStyle(ChatFormatting.WHITE), false);
        MessageCompat.displayClientMessage(player, Component.literal("  ❌ /llmchat template cancel - 取消编辑").withStyle(ChatFormatting.WHITE), false);
        MessageCompat.displayClientMessage(player, Component.literal("").withStyle(ChatFormatting.GRAY), false);

        MessageCompat.displayClientMessage(player, Component.literal("💡 变量使用提示:").withStyle(ChatFormatting.YELLOW), false);
        MessageCompat.displayClientMessage(player, Component.literal("  • 使用 {{变量名}} 格式在模板中引用变量").withStyle(ChatFormatting.GRAY), false);
        MessageCompat.displayClientMessage(player, Component.literal("  • 内置变量: {{player}}, {{time}}, {{date}}, {{x}}, {{y}}, {{z}} 等").withStyle(ChatFormatting.GRAY), false);
        MessageCompat.displayClientMessage(player, Component.literal("  • 内置变量会自动获取当前值，无需手动设置").withStyle(ChatFormatting.GRAY), false);
        MessageCompat.displayClientMessage(player, Component.literal("  • 使用 preview 命令查看所有可用变量及其当前值").withStyle(ChatFormatting.GRAY), false);
    }

    /**
     * 预览模板
     */
    public void previewTemplate(Player player) {
        EditSession session = getEditSession(player);
        if (session == null) {
            MessageCompat.displayClientMessage(player, Component.literal("❌ 没有正在编辑的模板").withStyle(ChatFormatting.RED), false);
            return;
        }

        PromptTemplate template = session.getTemplate();
        
        MessageCompat.displayClientMessage(player, Component.literal("").withStyle(ChatFormatting.GRAY), false);
        MessageCompat.displayClientMessage(player, Component.literal("=== 模板预览 ===").withStyle(ChatFormatting.GOLD), false);
        MessageCompat.displayClientMessage(player, Component.literal("ID: " + template.getId()).withStyle(ChatFormatting.AQUA), false);
        MessageCompat.displayClientMessage(player, Component.literal("名称: " + template.getName()).withStyle(ChatFormatting.AQUA), false);
        MessageCompat.displayClientMessage(player, Component.literal("描述: " + template.getDescription()).withStyle(ChatFormatting.AQUA), false);
        MessageCompat.displayClientMessage(player, Component.literal("").withStyle(ChatFormatting.GRAY), false);
        
        MessageCompat.displayClientMessage(player, Component.literal("📋 系统提示词:").withStyle(ChatFormatting.YELLOW), false);
        String systemPrompt = template.getSystemPrompt();
        if (systemPrompt != null && !systemPrompt.trim().isEmpty()) {
            // 分行显示长文本
            String[] lines = systemPrompt.split("\n");
            for (String line : lines) {
                if (line.length() > 80) {
                    // 长行分割显示
                    for (int i = 0; i < line.length(); i += 80) {
                        int end = Math.min(i + 80, line.length());
                        MessageCompat.displayClientMessage(player, Component.literal("  " + line.substring(i, end)).withStyle(ChatFormatting.WHITE), false);
                    }
                } else {
                    MessageCompat.displayClientMessage(player, Component.literal("  " + line).withStyle(ChatFormatting.WHITE), false);
                }
            }
        } else {
            MessageCompat.displayClientMessage(player, Component.literal("  (未设置)").withStyle(ChatFormatting.GRAY), false);
        }
        
        MessageCompat.displayClientMessage(player, Component.literal("").withStyle(ChatFormatting.GRAY), false);
        MessageCompat.displayClientMessage(player, Component.literal("📝 用户消息前缀:").withStyle(ChatFormatting.YELLOW), false);
        String prefix = template.getUserPromptPrefix();
        if (prefix != null && !prefix.trim().isEmpty()) {
            MessageCompat.displayClientMessage(player, Component.literal("  " + prefix).withStyle(ChatFormatting.WHITE), false);
        } else {
            MessageCompat.displayClientMessage(player, Component.literal("  (未设置)").withStyle(ChatFormatting.GRAY), false);
        }
        
        MessageCompat.displayClientMessage(player, Component.literal("📝 用户消息后缀:").withStyle(ChatFormatting.YELLOW), false);
        String suffix = template.getUserPromptSuffix();
        if (suffix != null && !suffix.trim().isEmpty()) {
            MessageCompat.displayClientMessage(player, Component.literal("  " + suffix).withStyle(ChatFormatting.WHITE), false);
        } else {
            MessageCompat.displayClientMessage(player, Component.literal("  (未设置)").withStyle(ChatFormatting.GRAY), false);
        }
        
        MessageCompat.displayClientMessage(player, Component.literal("").withStyle(ChatFormatting.GRAY), false);

        // 显示内置变量
        MessageCompat.displayClientMessage(player, Component.literal("🔧 内置变量 (自动获取):").withStyle(ChatFormatting.YELLOW), false);
        MessageCompat.displayClientMessage(player, Component.literal("  {{player}} = " + player.getName().getString() + " (玩家名)").withStyle(ChatFormatting.GREEN), false);
        MessageCompat.displayClientMessage(player, Component.literal("  {{time}} = " + java.time.LocalDateTime.now().format(
            java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")) + " (当前时间)").withStyle(ChatFormatting.GREEN), false);
        MessageCompat.displayClientMessage(player, Component.literal("  {{date}} = " + java.time.LocalDate.now().format(
            java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd")) + " (当前日期)").withStyle(ChatFormatting.GREEN), false);
        //? >=1.21.11 {
        MessageCompat.displayClientMessage(player, Component.literal("  {{world}} = " + EntityHelper.getWorld(player).dimension().identifier().toString() + " (世界)").withStyle(ChatFormatting.GREEN), false);
        //?} else {
        /*MessageCompat.displayClientMessage(player, Component.literal("  {{world}} = " + EntityHelper.getWorld(player).dimension().location().toString() + " (世界)").withStyle(ChatFormatting.GREEN), false);
        *//*?}*/
        MessageCompat.displayClientMessage(player, Component.literal("  {{x}}, {{y}}, {{z}} = " + (int)player.getX() + ", " + (int)player.getY() + ", " + (int)player.getZ() + " (坐标)").withStyle(ChatFormatting.GREEN), false);
        MessageCompat.displayClientMessage(player, Component.literal("  {{health}}, {{level}} = " + (int)player.getHealth() + ", " + player.experienceLevel + " (生命值, 等级)").withStyle(ChatFormatting.GREEN), false);
        MessageCompat.displayClientMessage(player, Component.literal("  更多内置变量: {{hour}}, {{minute}}, {{dimension}}, {{gamemode}}, {{weather}}, {{server}}").withStyle(ChatFormatting.GREEN), false);

        MessageCompat.displayClientMessage(player, Component.literal("").withStyle(ChatFormatting.GRAY), false);
        MessageCompat.displayClientMessage(player, Component.literal("🔧 自定义变量 (" + template.getVariables().size() + "个):").withStyle(ChatFormatting.YELLOW), false);
        if (!template.getVariables().isEmpty()) {
            for (Map.Entry<String, String> entry : template.getVariables().entrySet()) {
                MessageCompat.displayClientMessage(player, Component.literal("  {{" + entry.getKey() + "}} = " + entry.getValue()).withStyle(ChatFormatting.AQUA), false);
            }
        } else {
            MessageCompat.displayClientMessage(player, Component.literal("  (无自定义变量)").withStyle(ChatFormatting.GRAY), false);
        }

        MessageCompat.displayClientMessage(player, Component.literal("").withStyle(ChatFormatting.GRAY), false);
    }

    /**
     * 保存模板
     */
    public boolean saveTemplate(Player player) {
        EditSession session = getEditSession(player);
        if (session == null) {
            MessageCompat.displayClientMessage(player, Component.literal("❌ 没有正在编辑的模板").withStyle(ChatFormatting.RED), false);
            return false;
        }

        try {
            PromptTemplateManager templateManager = PromptTemplateManager.getInstance();
            PromptTemplate template = session.getTemplate();
            String validationError = validateTemplate(template);
            if (validationError != null) {
                MessageCompat.displayClientMessage(player, Component.literal("❌ " + validationError).withStyle(ChatFormatting.RED), false);
                return false;
            }
            
            if (session.isNewTemplate()) {
                templateManager.addTemplate(template);
                MessageCompat.displayClientMessage(player, Component.literal("✅ 新模板已创建并保存: " + template.getId()).withStyle(ChatFormatting.GREEN), false);
            } else {
                templateManager.updateTemplate(template);
                MessageCompat.displayClientMessage(player, Component.literal("✅ 模板已更新并保存: " + template.getId()).withStyle(ChatFormatting.GREEN), false);
            }
            
            // 结束编辑会话
            endEditSession(player);
            
            MessageCompat.displayClientMessage(player, Component.literal("💡 使用 /llmchat template set " + template.getId() + " 来应用此模板").withStyle(ChatFormatting.GRAY), false);
            return true;
        } catch (Exception e) {
            MessageCompat.displayClientMessage(player, Component.literal("❌ 保存模板失败: " + e.getMessage()).withStyle(ChatFormatting.RED), false);
            return false;
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

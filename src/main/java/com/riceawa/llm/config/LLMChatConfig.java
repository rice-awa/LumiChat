package com.riceawa.llm.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.riceawa.llm.logging.LogConfig;
import com.riceawa.llm.logging.LogLevel;
import com.riceawa.llm.logging.LogManager;
import com.riceawa.llm.context.ChatMode;

import net.fabricmc.loader.api.FabricLoader;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * LLM聊天配置管理
 */
public class LLMChatConfig {
    private static volatile LLMChatConfig instance;
    private final Gson gson;
    private final Path configFile;
    private boolean isInitializing = false;
    private ProviderManager providerManager;

    // 配置版本
    private static final String CURRENT_CONFIG_VERSION = "3.0.0";

    // 配置项 - 使用ConfigDefaults中的默认值
    private String configVersion = CURRENT_CONFIG_VERSION;
    private String currentProvider = ConfigDefaults.EMPTY_STRING;
    private String currentModel = ConfigDefaults.EMPTY_STRING;
    private List<Provider> providers = new ArrayList<>();

    private ChatSettings chat = ChatSettings.defaults();
    private SecuritySettings security = SecuritySettings.defaults();
    private ModelExtras models = ModelExtras.defaults();
    private AdvancedSettings advanced = AdvancedSettings.defaults();

    private ConcurrencySettings concurrencySettings = ConcurrencySettings.createDefault();
    private LogConfig logConfig = LogConfig.createDefault();

    private LLMChatConfig() {
        this.isInitializing = true;
        this.gson = new GsonBuilder().setPrettyPrinting().create();

        Path configDir = FabricLoader.getInstance().getConfigDir().resolve("lumichat");
        this.configFile = configDir.resolve("config.json");

        // 确保配置目录存在
        try {
            Files.createDirectories(configDir);
        } catch (IOException e) {
            throw new RuntimeException("Failed to create config directory", e);
        }

        // 加载配置
        loadConfig();

        // 初始化Provider管理器
        this.providerManager = new ProviderManager(this.providers);

        // 验证和修复配置
        validateAndFixConfiguration();

        this.isInitializing = false;

        LogManager.getInstance().system("LLMChatConfig initialized successfully");
    }

    public static LLMChatConfig getInstance() {
        if (instance == null) {
            synchronized (LLMChatConfig.class) {
                if (instance == null) {
                    instance = new LLMChatConfig();
                }
            }
        }
        return instance;
    }

    private static <T> T nvl(T value, T defaultValue) {
        return value != null ? value : defaultValue;
    }

    /**
     * 加载配置
     */
    private void loadConfig() {
        if (!Files.exists(configFile)) {
            LogManager.getInstance().system("Config file does not exist, creating default configuration...");
            createDefaultConfig();
            return;
        }

        try (InputStreamReader reader = new InputStreamReader(
                Files.newInputStream(configFile), StandardCharsets.UTF_8)) {
            JsonObject root = gson.fromJson(reader, JsonObject.class);
            String version = root.has("configVersion") ? root.get("configVersion").getAsString() : "0.0.0";

            if (version.startsWith("3.")) {
                ConfigData data = gson.fromJson(root, ConfigData.class);
                applyConfigDataV3(data);
            } else {
                ConfigDataV2 data = gson.fromJson(root, ConfigDataV2.class);
                applyConfigDataV2(data);
                saveConfig();
            }
        } catch (Exception e) {
            LogManager.getInstance().error("Failed to load config, backing up and recreating", e);
            backupCorruptedConfig();
            createDefaultConfig();
            saveConfig();
        }
    }

    /**
     * 保存配置
     */
    public void saveConfig() {
        try (OutputStreamWriter writer = new OutputStreamWriter(
                Files.newOutputStream(configFile), StandardCharsets.UTF_8)) {
            ConfigData data = createConfigData();
            LogManager.getInstance().system("Saving config v" + data.configVersion);
            gson.toJson(data, writer);
            LogManager.getInstance().system("Configuration saved successfully");
        } catch (IOException e) {
            LogManager.getInstance().error("Failed to save config: " + e.getMessage());
        }
    }

    /**
     * 重新加载配置
     */
    public void reload() {
        loadConfig();

        // 重载配置后，更新现有的上下文实例
        if (!isInitializing) {
            try {
                com.riceawa.llm.context.ChatContextManager.getInstance().updateMaxContextLength();
            } catch (Exception e) {
                LogManager.getInstance().error("Failed to update existing contexts after reload: " + e.getMessage());
            }

            // 触发异步健康检查
            triggerHealthCheck();
        }
    }

    /**
     * 触发provider健康检查
     */
    private void triggerHealthCheck() {
        try {
            ProviderManager providerManager = new ProviderManager(this.providers);
            providerManager.checkAllProvidersHealth().whenComplete((healthMap, throwable) -> {
                if (throwable != null) {
                    LogManager.getInstance().error("Provider health check failed: " + throwable.getMessage());
                } else {
                    LogManager.getInstance().system("Provider health check completed for " + healthMap.size() + " providers");
                }
            });
        } catch (Exception e) {
            LogManager.getInstance().error("Failed to trigger health check: " + e.getMessage());
        }
    }

    /**
     * 备份损坏的配置文件
     */
    private void backupCorruptedConfig() {
        try {
            Path backupFile = configFile.getParent().resolve("config.json.backup." + System.currentTimeMillis());
            Files.copy(configFile, backupFile, StandardCopyOption.REPLACE_EXISTING);
            LogManager.getInstance().system("Corrupted config backed up to: " + backupFile);
        } catch (IOException e) {
            LogManager.getInstance().error("Failed to backup corrupted config: " + e.getMessage());
        }
    }

    /**
     * 创建默认配置
     */
    private void createDefaultConfig() {
        this.configVersion = CURRENT_CONFIG_VERSION;
        this.currentProvider = ConfigDefaults.EMPTY_STRING;
        this.currentModel = ConfigDefaults.EMPTY_STRING;
        this.providers = ConfigDefaults.createDefaultProviders();
        this.chat = ChatSettings.defaults();
        this.security = SecuritySettings.defaults();

        selectInitialProviderAndModel();

        LogManager.getInstance().system("Created default configuration with " + this.providers.size() + " providers");

        try (OutputStreamWriter writer = new OutputStreamWriter(
                Files.newOutputStream(configFile), StandardCharsets.UTF_8)) {
            ConfigData data = createConfigData();
            data.models = null;
            data.advanced = null;
            gson.toJson(data, writer);
            LogManager.getInstance().system("Default configuration saved to file (system sections omitted)");
        } catch (IOException e) {
            LogManager.getInstance().error("Failed to save default config: " + e.getMessage());
        }
    }

    /**
     * 选择初始的Provider和Model
     */
    private void selectInitialProviderAndModel() {
        ProviderManager manager = new ProviderManager(this.providers);
        ProviderManager.ProviderModelResult result = manager.fixCurrentConfiguration("", "");

        if (result.isSuccess()) {
            this.currentProvider = result.getProviderName();
            this.currentModel = result.getModelName();
            LogManager.getInstance().system("Selected initial provider: " + this.currentProvider + ", model: " + this.currentModel);
        } else {
            this.currentProvider = ConfigDefaults.EMPTY_STRING;
            this.currentModel = ConfigDefaults.EMPTY_STRING;
            LogManager.getInstance().system("No valid provider configuration found: " + result.getMessage());
        }
    }

    /**
     * 应用配置数据 v2→v3 迁移路径
     */
    private void applyConfigDataV2(ConfigDataV2 data) {
        int mcc;
        if (data.maxContextLength != null) {
            mcc = data.maxContextLength;
        } else if (data.maxContextCharacters != null) {
            mcc = data.maxContextCharacters;
        } else {
            mcc = ConfigDefaults.DEFAULT_MAX_CONTEXT_CHARACTERS;
        }

        this.configVersion = CURRENT_CONFIG_VERSION;
        this.currentProvider = nvl(data.currentProvider, ConfigDefaults.EMPTY_STRING);
        this.currentModel = nvl(data.currentModel, ConfigDefaults.EMPTY_STRING);
        this.providers = (data.providers != null && !data.providers.isEmpty())
                ? data.providers : ConfigDefaults.createDefaultProviders();

        this.chat = ChatSettings.builder()
            .defaultPromptTemplate(nvl(data.defaultPromptTemplate, ConfigDefaults.DEFAULT_PROMPT_TEMPLATE))
            .temperature(nvl(data.defaultTemperature, ConfigDefaults.DEFAULT_TEMPERATURE))
            .maxTokens(nvl(data.defaultMaxTokens, ConfigDefaults.DEFAULT_MAX_TOKENS))
            .maxContextCharacters(mcc)
            .enableHistory(nvl(data.enableHistory, ConfigDefaults.DEFAULT_ENABLE_HISTORY))
            .enableToolCall(nvl(data.enableToolCall, ConfigDefaults.DEFAULT_ENABLE_TOOL_CALL))
            .enableBroadcast(nvl(data.enableBroadcast, ConfigDefaults.DEFAULT_ENABLE_BROADCAST))
            .broadcastPlayers(data.broadcastPlayers != null ? new HashSet<>(data.broadcastPlayers) : ConfigDefaults.createDefaultBroadcastPlayers())
            .enableChatIntegration(nvl(data.enableChatIntegration, ConfigDefaults.DEFAULT_ENABLE_CHAT_INTEGRATION))
            .defaultChatMode(nvl(data.defaultChatMode, ConfigDefaults.DEFAULT_DEFAULT_CHAT_MODE))
            .enableGlobalContext(nvl(data.enableGlobalContext, ConfigDefaults.DEFAULT_ENABLE_GLOBAL_CONTEXT))
            .globalContextPrompt(nvl(data.globalContextPrompt, ConfigDefaults.DEFAULT_GLOBAL_CONTEXT_PROMPT))
            .build();

        this.security = SecuritySettings.builder()
            .enableExecuteCommand(nvl(data.enableExecuteCommand, ConfigDefaults.DEFAULT_ENABLE_EXECUTE_COMMAND))
            .executeCommandReturnFullOutput(nvl(data.executeCommandReturnFullOutput, ConfigDefaults.DEFAULT_EXECUTE_COMMAND_RETURN_FULL_OUTPUT))
            .executeCommandBlocklist(data.executeCommandBlocklist != null ? new HashSet<>(data.executeCommandBlocklist) : ConfigDefaults.createDefaultExecuteCommandBlocklist())
            .executeCommandMaxLength(nvl(data.executeCommandMaxLength, ConfigDefaults.DEFAULT_EXECUTE_COMMAND_MAX_LENGTH))
            .wikiApiUrl(nvl(data.wikiApiUrl, ConfigDefaults.DEFAULT_WIKI_API_URL))
            .wikiAllowedHosts(data.wikiAllowedHosts != null ? new HashSet<>(data.wikiAllowedHosts) : ConfigDefaults.createDefaultWikiAllowedHosts())
            .build();

        this.models = ModelExtras.builder()
            .compressionModel(nvl(data.compressionModel, ConfigDefaults.DEFAULT_COMPRESSION_MODEL))
            .titleGenerationModel(nvl(data.titleGenerationModel, ConfigDefaults.DEFAULT_TITLE_GENERATION_MODEL))
            .enableTitleGeneration(nvl(data.enableTitleGeneration, ConfigDefaults.DEFAULT_ENABLE_TITLE_GENERATION))
            .enableCompressionNotification(nvl(data.enableCompressionNotification, ConfigDefaults.DEFAULT_ENABLE_COMPRESSION_NOTIFICATION))
            .build();

        boolean enableRecursive = nvl(data.enableRecursiveToolCalls, ConfigDefaults.DEFAULT_ENABLE_RECURSIVE_TOOL_CALLS);
        int maxDepth = nvl(data.maxToolCallDepth, ConfigDefaults.DEFAULT_MAX_TOOL_CALL_DEPTH);

        ConcurrencySettings cs = data.concurrencySettings != null ? data.concurrencySettings : ConcurrencySettings.createDefault();
        LogConfig lc = data.logConfig != null ? data.logConfig : LogConfig.createDefault();

        this.advanced = AdvancedSettings.builder()
            .toolCall(AdvancedSettings.ToolCallSettings.builder().enableRecursive(enableRecursive).maxDepth(maxDepth).build())
            .http(createHttpSettingsFromOld(cs))
            .concurrency(createSchedulerSettingsFromOld(cs))
            .retry(createRetrySettingsFromOld(cs))
            .logSettings(createLogSettingsFromOld(lc))
            .build();

        rebuildCompatibilityObjects();
        this.providerManager = new ProviderManager(this.providers);
    }

    /**
     * 应用配置数据 v3
     */
    private void applyConfigDataV3(ConfigData data) {
        this.configVersion = CURRENT_CONFIG_VERSION;
        this.currentProvider = nvl(data.currentProvider, ConfigDefaults.EMPTY_STRING);
        this.currentModel = nvl(data.currentModel, ConfigDefaults.EMPTY_STRING);
        this.providers = (data.providers != null && !data.providers.isEmpty())
                ? data.providers : ConfigDefaults.createDefaultProviders();

        this.chat = data.chat != null ? data.chat : ChatSettings.defaults();
        this.security = data.security != null ? data.security : SecuritySettings.defaults();
        this.models = data.models != null ? data.models : ModelExtras.defaults();
        this.advanced = data.advanced != null ? data.advanced : AdvancedSettings.defaults();

        rebuildCompatibilityObjects();
        this.providerManager = new ProviderManager(this.providers);
    }

    /**
     * 从v3嵌套配置重建兼容对象 (ConcurrencySettings/LogConfig)
     */
    private void rebuildCompatibilityObjects() {
        AdvancedSettings.HttpSettings h = advanced.getHttp();
        AdvancedSettings.SchedulerSettings s = advanced.getConcurrency();
        AdvancedSettings.RetrySettings r = advanced.getRetry();
        AdvancedSettings.LogSettings ls = advanced.getLogSettings();

        ConcurrencySettings cs = new ConcurrencySettings();
        cs.setConnectTimeoutMs(h.getConnectTimeoutMs());
        cs.setReadTimeoutMs(h.getReadTimeoutMs());
        cs.setWriteTimeoutMs(h.getWriteTimeoutMs());
        cs.setMaxIdleConnections(h.getMaxIdleConnections());
        cs.setKeepAliveDurationMs(h.getKeepAliveDurationMs());
        cs.setMaxConcurrentRequests(s.getMaxConcurrentRequests());
        cs.setQueueCapacity(s.getQueueCapacity());
        cs.setRequestTimeoutMs(s.getRequestTimeoutMs());
        cs.setCorePoolSize(s.getCorePoolSize());
        cs.setMaximumPoolSize(s.getMaximumPoolSize());
        cs.setKeepAliveTimeMs(s.getKeepAliveTimeMs());
        cs.setEnableRetry(r.isEnabled());
        cs.setMaxRetryAttempts(r.getMaxAttempts());
        cs.setRetryDelayMs(r.getDelayMs());
        cs.setRetryBackoffMultiplier(r.getBackoffMultiplier());
        this.concurrencySettings = cs;

        LogConfig lc = new LogConfig();
        lc.setLogLevel(LogLevel.valueOf(ls.getLevel().toUpperCase()));
        lc.setEnableFileLogging(ls.isFile());
        lc.setEnableConsoleLogging(ls.isConsole());
        lc.setEnableJsonFormat(ls.isJson());
        lc.setEnableAsyncLogging(ls.isAsync());
        lc.setMaxFileSize(ls.getMaxFileSize());
        lc.setMaxBackupFiles(ls.getMaxBackupFiles());
        lc.setRetentionDays(ls.getRetentionDays());
        lc.setEnableLLMRequestLog(ls.isLlmRequestLog());
        lc.setLogFullRequestBody(ls.isLogFullBodies());
        lc.setLogFullResponseBody(ls.isLogFullBodies());
        lc.setMaxLogContentLength(ls.getMaxContentLength());
        lc.setDebugMode(ls.isDebugMode());
        this.logConfig = lc;
    }

    private AdvancedSettings.HttpSettings createHttpSettingsFromOld(ConcurrencySettings cs) {
        AdvancedSettings.HttpSettings h = AdvancedSettings.HttpSettings.defaults();
        h.connectTimeoutMs = cs.getConnectTimeoutMs();
        h.readTimeoutMs = cs.getReadTimeoutMs();
        h.writeTimeoutMs = cs.getWriteTimeoutMs();
        h.maxIdleConnections = cs.getMaxIdleConnections();
        h.keepAliveDurationMs = (int) cs.getKeepAliveDurationMs();
        return h;
    }

    private AdvancedSettings.SchedulerSettings createSchedulerSettingsFromOld(ConcurrencySettings cs) {
        AdvancedSettings.SchedulerSettings s = AdvancedSettings.SchedulerSettings.defaults();
        s.maxConcurrentRequests = cs.getMaxConcurrentRequests();
        s.queueCapacity = cs.getQueueCapacity();
        s.requestTimeoutMs = (int) cs.getRequestTimeoutMs();
        s.corePoolSize = cs.getCorePoolSize();
        s.maximumPoolSize = cs.getMaximumPoolSize();
        s.keepAliveTimeMs = (int) cs.getKeepAliveTimeMs();
        return s;
    }

    private AdvancedSettings.RetrySettings createRetrySettingsFromOld(ConcurrencySettings cs) {
        AdvancedSettings.RetrySettings r = AdvancedSettings.RetrySettings.defaults();
        r.enabled = cs.isEnableRetry();
        r.maxAttempts = cs.getMaxRetryAttempts();
        r.delayMs = (int) cs.getRetryDelayMs();
        r.backoffMultiplier = cs.getRetryBackoffMultiplier();
        return r;
    }

    private AdvancedSettings.LogSettings createLogSettingsFromOld(LogConfig lc) {
        AdvancedSettings.LogSettings l = AdvancedSettings.LogSettings.defaults();
        l.level = lc.getLogLevel().name();
        l.file = lc.isEnableFileLogging();
        l.console = lc.isEnableConsoleLogging();
        l.json = lc.isEnableJsonFormat();
        l.async = lc.isEnableAsyncLogging();
        l.maxFileSize = lc.getMaxFileSize();
        l.maxBackupFiles = lc.getMaxBackupFiles();
        l.retentionDays = lc.getRetentionDays();
        l.llmRequestLog = lc.isEnableLLMRequestLog();
        l.logFullBodies = lc.isLogFullRequestBody() || lc.isLogFullResponseBody();
        l.maxContentLength = lc.getMaxLogContentLength();
        return l;
    }

    /**
     * 验证和修复配置
     */
    private void validateAndFixConfiguration() {
        boolean needsSave = false;

        if (!ConfigDefaults.isValidConfigValue("maxContextCharacters", this.chat.getMaxContextCharacters())) {
            LogManager.getInstance().system("Invalid maxContextCharacters (" + this.chat.getMaxContextCharacters() + "), resetting to default");
            this.chat = ChatSettings.builder().cloneFrom(this.chat).maxContextCharacters(ConfigDefaults.DEFAULT_MAX_CONTEXT_CHARACTERS).build();
            needsSave = true;
        }

        if (!ConfigDefaults.isValidConfigValue("defaultTemperature", this.chat.getTemperature())) {
            LogManager.getInstance().system("Invalid defaultTemperature (" + this.chat.getTemperature() + "), resetting to default");
            this.chat = ChatSettings.builder().cloneFrom(this.chat).temperature(ConfigDefaults.DEFAULT_TEMPERATURE).build();
            needsSave = true;
        }

        if (!ConfigDefaults.isValidConfigValue("defaultMaxTokens", this.chat.getMaxTokens())) {
            LogManager.getInstance().system("Invalid defaultMaxTokens (" + this.chat.getMaxTokens() + "), resetting to default");
            this.chat = ChatSettings.builder().cloneFrom(this.chat).maxTokens(ConfigDefaults.DEFAULT_MAX_TOKENS).build();
            needsSave = true;
        }

        if (!ConfigDefaults.isValidConfigValue("executeCommandMaxLength", this.security.getExecuteCommandMaxLength())) {
            LogManager.getInstance().system("Invalid executeCommandMaxLength (" + this.security.getExecuteCommandMaxLength()
                    + "), resetting to default");
            this.security = SecuritySettings.builder().cloneFrom(this.security).executeCommandMaxLength(ConfigDefaults.DEFAULT_EXECUTE_COMMAND_MAX_LENGTH).build();
            needsSave = true;
        }
        // 验证和修复Provider配置
        ProviderManager.ProviderModelResult result = providerManager.fixCurrentConfiguration(
            this.currentProvider, this.currentModel);

        if (result.isSuccess()) {
            if (!result.getProviderName().equals(this.currentProvider) ||
                !result.getModelName().equals(this.currentModel)) {
                this.currentProvider = result.getProviderName();
                this.currentModel = result.getModelName();
                needsSave = true;
                LogManager.getInstance().system("Provider configuration fixed: " + result.getMessage());
            }
        } else {
            LogManager.getInstance().system("Provider configuration issue: " + result.getMessage());
        }

        // 如果有修复，保存配置
        if (needsSave && !isInitializing) {
            saveConfig();
        }
    }

    /**
     * 创建配置数据
     */
    private ConfigData createConfigData() {
        ConfigData data = new ConfigData();
        data.configVersion = CURRENT_CONFIG_VERSION;
        data.currentProvider = this.currentProvider;
        data.currentModel = this.currentModel;
        data.providers = this.providers;
        data.chat = this.chat;
        data.security = this.security;
        data.models = this.models;
        data.advanced = this.advanced;
        return data;
    }

    // Getters and Setters

    public String getDefaultPromptTemplate() { return chat.getDefaultPromptTemplate(); }

    public void setDefaultPromptTemplate(String template) {
        this.chat = ChatSettings.builder().cloneFrom(this.chat).defaultPromptTemplate(template).build();
        saveConfig();
    }

    public double getDefaultTemperature() { return chat.getTemperature(); }

    public void setDefaultTemperature(double temperature) {
        this.chat = ChatSettings.builder().cloneFrom(this.chat).temperature(temperature).build();
        saveConfig();
    }

    public int getDefaultMaxTokens() { return chat.getMaxTokens(); }

    public void setDefaultMaxTokens(int maxTokens) {
        this.chat = ChatSettings.builder().cloneFrom(this.chat).maxTokens(maxTokens).build();
        saveConfig();
    }

    public int getMaxContextCharacters() { return chat.getMaxContextCharacters(); }

    public void setMaxContextCharacters(int maxContextCharacters) {
        this.chat = ChatSettings.builder().cloneFrom(this.chat).maxContextCharacters(maxContextCharacters).build();

        if (!isInitializing) {
            saveConfig();

            try {
                com.riceawa.llm.context.ChatContextManager.getInstance().updateMaxContextLength();
            } catch (Exception e) {
                LogManager.getInstance().error("Failed to update existing contexts with new max context characters: " + e.getMessage());
            }
        }
    }

    public int getMaxContextLength() { return chat.getMaxContextCharacters(); }

    public void setMaxContextLength(int maxContextLength) { setMaxContextCharacters(maxContextLength); }

    public boolean isEnableHistory() { return chat.isEnableHistory(); }

    public void setEnableHistory(boolean enableHistory) {
        this.chat = ChatSettings.builder().cloneFrom(this.chat).enableHistory(enableHistory).build();
        saveConfig();
    }

    public boolean isEnableToolCall() { return chat.isEnableToolCall(); }

    public void setEnableToolCall(boolean enableToolCall) {
        this.chat = ChatSettings.builder().cloneFrom(this.chat).enableToolCall(enableToolCall).build();
        saveConfig();
    }

    public boolean isEnableBroadcast() { return chat.isEnableBroadcast(); }

    public void setEnableBroadcast(boolean enableBroadcast) {
        this.chat = ChatSettings.builder().cloneFrom(this.chat).enableBroadcast(enableBroadcast).build();
        saveConfig();
    }

    public boolean isEnableChatIntegration() { return chat.isEnableChatIntegration(); }

    public void setChatIntegrationEnabled(boolean enableChatIntegration) {
        this.chat = ChatSettings.builder().cloneFrom(this.chat).enableChatIntegration(enableChatIntegration).build();
        saveConfig();
    }

    public ChatMode getDefaultChatMode() { return ChatMode.fromName(chat.getDefaultChatMode()); }

    public void setDefaultChatMode(ChatMode mode) {
        this.chat = ChatSettings.builder().cloneFrom(this.chat).defaultChatMode(mode.getName()).build();
        saveConfig();
    }

    public boolean isEnableExecuteCommand() { return security.isEnableExecuteCommand(); }

    public void setEnableExecuteCommand(boolean enableExecuteCommand) {
        this.security = SecuritySettings.builder().cloneFrom(this.security).enableExecuteCommand(enableExecuteCommand).build();
        saveConfig();
    }

    public boolean isExecuteCommandReturnFullOutput() { return security.isExecuteCommandReturnFullOutput(); }

    public void setExecuteCommandReturnFullOutput(boolean executeCommandReturnFullOutput) {
        this.security = SecuritySettings.builder().cloneFrom(this.security).executeCommandReturnFullOutput(executeCommandReturnFullOutput).build();
        saveConfig();
    }

    public Set<String> getExecuteCommandBlocklist() { return security.getExecuteCommandBlocklist(); }

    public void setExecuteCommandBlocklist(Set<String> executeCommandBlocklist) {
        this.security = SecuritySettings.builder().cloneFrom(this.security)
            .executeCommandBlocklist(executeCommandBlocklist != null ? new HashSet<>(executeCommandBlocklist) : ConfigDefaults.createDefaultExecuteCommandBlocklist())
            .build();
        saveConfig();
    }

    public int getExecuteCommandMaxLength() { return security.getExecuteCommandMaxLength(); }

    public void setExecuteCommandMaxLength(int executeCommandMaxLength) {
        int validated = ConfigDefaults.isValidConfigValue("executeCommandMaxLength", executeCommandMaxLength)
                ? executeCommandMaxLength : ConfigDefaults.DEFAULT_EXECUTE_COMMAND_MAX_LENGTH;
        this.security = SecuritySettings.builder().cloneFrom(this.security).executeCommandMaxLength(validated).build();
        saveConfig();
    }

    public Set<String> getBroadcastPlayers() { return chat.getBroadcastPlayers(); }

    public void setBroadcastPlayers(Set<String> broadcastPlayers) {
        this.chat = ChatSettings.builder().cloneFrom(this.chat)
            .broadcastPlayers(broadcastPlayers != null ? new HashSet<>(broadcastPlayers) : new HashSet<>())
            .build();
        saveConfig();
    }

    public void addBroadcastPlayer(String playerName) {
        if (playerName != null && !playerName.trim().isEmpty()) {
            Set<String> players = new HashSet<>(chat.getBroadcastPlayers());
            players.add(playerName.trim());
            this.chat = ChatSettings.builder().cloneFrom(this.chat).broadcastPlayers(players).build();
            saveConfig();
        }
    }

    public void removeBroadcastPlayer(String playerName) {
        if (playerName != null) {
            Set<String> players = new HashSet<>(chat.getBroadcastPlayers());
            players.remove(playerName.trim());
            this.chat = ChatSettings.builder().cloneFrom(this.chat).broadcastPlayers(players).build();
            saveConfig();
        }
    }

    public boolean isBroadcastPlayer(String playerName) {
        return playerName != null && chat.getBroadcastPlayers().contains(playerName.trim());
    }

    public void clearBroadcastPlayers() {
        this.chat = ChatSettings.builder().cloneFrom(this.chat).broadcastPlayers(new HashSet<>()).build();
        saveConfig();
    }

    public boolean isEnableGlobalContext() { return chat.isEnableGlobalContext(); }

    public void setEnableGlobalContext(boolean enableGlobalContext) {
        this.chat = ChatSettings.builder().cloneFrom(this.chat).enableGlobalContext(enableGlobalContext).build();
        saveConfig();
    }

    public String getGlobalContextPrompt() { return chat.getGlobalContextPrompt(); }

    public void setGlobalContextPrompt(String globalContextPrompt) {
        this.chat = ChatSettings.builder().cloneFrom(this.chat).globalContextPrompt(globalContextPrompt != null ? globalContextPrompt : "").build();
        saveConfig();
    }

    public boolean isEnableTitleGeneration() { return models.isEnableTitleGeneration(); }

    public void setEnableTitleGeneration(boolean enableTitleGeneration) {
        this.models = ModelExtras.builder().cloneFrom(this.models).enableTitleGeneration(enableTitleGeneration).build();
        saveConfig();
    }

    public String getTitleGenerationModel() { return models.getTitleGenerationModel(); }

    public void setTitleGenerationModel(String titleGenerationModel) {
        this.models = ModelExtras.builder().cloneFrom(this.models).titleGenerationModel(titleGenerationModel != null ? titleGenerationModel : "").build();
        saveConfig();
    }

    /**
     * 获取有效的标题生成模型
     * 如果未设置专门的标题生成模型，则使用当前模型
     */
    public String getEffectiveTitleGenerationModel() {
        if (models.getTitleGenerationModel() != null && !models.getTitleGenerationModel().trim().isEmpty()) {
            return models.getTitleGenerationModel();
        }
        return getCurrentModel();
    }

    public String getCompressionModel() { return models.getCompressionModel(); }

    public void setCompressionModel(String compressionModel) {
        this.models = ModelExtras.builder().cloneFrom(this.models).compressionModel(compressionModel != null ? compressionModel : "").build();
        saveConfig();
    }

    public boolean isEnableCompressionNotification() { return models.isEnableCompressionNotification(); }

    public void setEnableCompressionNotification(boolean enableCompressionNotification) {
        this.models = ModelExtras.builder().cloneFrom(this.models).enableCompressionNotification(enableCompressionNotification).build();
        saveConfig();
    }

    /**
     * 获取用于压缩的模型，如果未设置则返回当前模型
     */
    public String getEffectiveCompressionModel() {
        if (models.getCompressionModel() == null || models.getCompressionModel().trim().isEmpty()) {
            return getCurrentModel();
        }
        return models.getCompressionModel();
    }

    public ConcurrencySettings getConcurrencySettings() { return concurrencySettings; }

    public void setConcurrencySettings(ConcurrencySettings concurrencySettings) {
        this.concurrencySettings = concurrencySettings != null ? concurrencySettings : ConcurrencySettings.createDefault();
        saveConfig();
    }

    public LogConfig getLogConfig() { return logConfig; }

    public void setLogConfig(LogConfig logConfig) {
        this.logConfig = logConfig != null ? logConfig : LogConfig.createDefault();
        saveConfig();
    }

    public List<Provider> getProviders() { return new ArrayList<>(providers); }

    public void setProviders(List<Provider> providers) {
        this.providers = providers != null ? new ArrayList<>(providers) : new ArrayList<>();

        this.providerManager = new ProviderManager(this.providers);

        validateAndFixConfiguration();

        saveConfig();
    }

    public void addProvider(Provider provider) {
        if (provider != null && provider.isValid()) {
            providers.removeIf(p -> p.getName().equals(provider.getName()));
            providers.add(provider);

            this.providerManager = new ProviderManager(this.providers);

            if (!isProviderModelValid(this.currentProvider, this.currentModel)) {
                validateAndFixConfiguration();
            }

            saveConfig();
        }
    }

    public void removeProvider(String providerName) {
        boolean removingCurrentProvider = providerName.equals(currentProvider);

        providers.removeIf(p -> p.getName().equals(providerName));

        this.providerManager = new ProviderManager(this.providers);

        if (removingCurrentProvider) {
            ProviderManager.ProviderModelResult result = providerManager.fixCurrentConfiguration("", "");
            if (result.isSuccess()) {
                this.currentProvider = result.getProviderName();
                this.currentModel = result.getModelName();
                LogManager.getInstance().system("Switched to provider: " + this.currentProvider + ", model: " + this.currentModel);
            } else {
                this.currentProvider = ConfigDefaults.EMPTY_STRING;
                this.currentModel = ConfigDefaults.EMPTY_STRING;
                LogManager.getInstance().system("No valid provider available after removal");
            }
        }

        saveConfig();
    }

    public Provider getProvider(String providerName) {
        return providers.stream()
                .filter(p -> p.getName().equals(providerName))
                .findFirst()
                .orElse(null);
    }

    public String getCurrentProvider() { return currentProvider; }

    public void setCurrentProvider(String currentProvider) {
        this.currentProvider = currentProvider != null ? currentProvider : "";
        saveConfig();
    }

    public String getCurrentModel() { return currentModel; }

    public void setCurrentModel(String currentModel) {
        this.currentModel = currentModel != null ? currentModel : "";
        saveConfig();
    }

    /**
     * 获取当前provider的配置
     */
    public Provider getCurrentProviderConfig() {
        if (currentProvider.isEmpty()) {
            return null;
        }
        return getProvider(currentProvider);
    }

    /**
     * 检查指定provider是否支持指定模型
     */
    public boolean isModelSupported(String providerName, String model) {
        Provider provider = getProvider(providerName);
        return provider != null && provider.supportsModel(model);
    }

    /**
     * 获取指定provider支持的所有模型
     */
    public List<String> getSupportedModels(String providerName) {
        Provider provider = getProvider(providerName);
        return provider != null ? new ArrayList<>(provider.getModels()) : new ArrayList<>();
    }

    /**
     * 获取所有有效的Provider列表
     */
    public List<Provider> getValidProviders() {
        return providerManager != null ? providerManager.getValidProviders() : new ArrayList<>();
    }

    /**
     * 获取配置状态报告
     */
    public String getConfigurationReport() {
        if (providerManager == null) {
            return "Provider管理器未初始化";
        }
        return providerManager.getConfigurationReport().getReportText();
    }

    /**
     * 自动修复当前的Provider和Model配置
     * @return 修复结果信息
     */
    public String autoFixConfiguration() {
        if (providerManager == null) {
            return "Provider管理器未初始化";
        }

        ProviderManager.ProviderModelResult result = providerManager.fixCurrentConfiguration(
            this.currentProvider, this.currentModel);

        if (result.isSuccess()) {
            boolean changed = false;
            if (!result.getProviderName().equals(this.currentProvider)) {
                this.currentProvider = result.getProviderName();
                changed = true;
            }
            if (!result.getModelName().equals(this.currentModel)) {
                this.currentModel = result.getModelName();
                changed = true;
            }

            if (changed) {
                saveConfig();
            }

            return result.getMessage();
        } else {
            return "配置修复失败: " + result.getMessage();
        }
    }

    /**
     * 检查指定的Provider和Model组合是否有效
     */
    public boolean isProviderModelValid(String providerName, String modelName) {
        return providerManager != null &&
               providerManager.isProviderModelValid(providerName, modelName);
    }

    /**
     * 检查是否有任何有效的provider配置
     */
    public boolean hasAnyValidProvider() {
        return providerManager != null && !providerManager.getValidProviders().isEmpty();
    }

    /**
     * 获取第一个有效配置的provider
     */
    public Provider getFirstValidProvider() {
        return providerManager != null ?
            providerManager.getFirstValidProvider().orElse(null) : null;
    }

    /**
     * 检查是否是第一次使用（所有API密钥都未配置）
     */
    public boolean isFirstTimeUse() {
        return !hasAnyValidProvider();
    }

    /**
     * 检查当前配置是否有效（用于配置验证）
     */
    public boolean isConfigurationValid() {
        if (!hasAnyValidProvider()) {
            return false;
        }

        ProviderManager.ProviderModelResult result = providerManager.fixCurrentConfiguration(
            this.currentProvider, this.currentModel);

        if (result.isSuccess()) {
            if (!result.getProviderName().equals(this.currentProvider) ||
                !result.getModelName().equals(this.currentModel)) {
                this.currentProvider = result.getProviderName();
                this.currentModel = result.getModelName();
                saveConfig();
            }
            return true;
        }

        return false;
    }

    /**
     * 简化的配置恢复功能
     */
    public boolean validateAndCompleteConfig() {
        boolean updated = false;

        if (concurrencySettings == null) {
            concurrencySettings = ConcurrencySettings.createDefault();
            updated = true;
        }

        if (logConfig == null) {
            logConfig = LogConfig.createDefault();
            updated = true;
        }

        if (providers == null || providers.isEmpty()) {
            providers = ConfigDefaults.createDefaultProviders();
            updated = true;
        }

        this.providerManager = new ProviderManager(this.providers);

        ProviderManager.ProviderModelResult result = providerManager.fixCurrentConfiguration(
            this.currentProvider, this.currentModel);

        if (result.isSuccess()) {
            if (!result.getProviderName().equals(this.currentProvider) ||
                !result.getModelName().equals(this.currentModel)) {
                this.currentProvider = result.getProviderName();
                this.currentModel = result.getModelName();
                updated = true;
            }
        } else {
            this.currentProvider = ConfigDefaults.EMPTY_STRING;
            this.currentModel = ConfigDefaults.EMPTY_STRING;
        }

        if (!ConfigDefaults.isValidConfigValue("executeCommandMaxLength", this.security.getExecuteCommandMaxLength())) {
            this.security = SecuritySettings.builder().cloneFrom(this.security).executeCommandMaxLength(ConfigDefaults.DEFAULT_EXECUTE_COMMAND_MAX_LENGTH).build();
            updated = true;
        }

        if (updated) {
            saveConfig();
        }

        return updated;
    }

    public String getConfigVersion() { return configVersion; }

    public String getWikiApiUrl() { return security.getWikiApiUrl(); }

    public void setWikiApiUrl(String wikiApiUrl) {
        this.security = SecuritySettings.builder().cloneFrom(this.security).wikiApiUrl(wikiApiUrl != null ? wikiApiUrl : ConfigDefaults.DEFAULT_WIKI_API_URL).build();
        saveConfig();
    }

    public Set<String> getWikiAllowedHosts() { return security.getWikiAllowedHosts(); }

    public void setWikiAllowedHosts(Set<String> wikiAllowedHosts) {
        this.security = SecuritySettings.builder().cloneFrom(this.security)
            .wikiAllowedHosts(wikiAllowedHosts != null ? new HashSet<>(wikiAllowedHosts) : ConfigDefaults.createDefaultWikiAllowedHosts())
            .build();
        saveConfig();
    }

    public boolean isEnableRecursiveToolCalls() { return advanced.getToolCall().isEnableRecursive(); }

    public void setEnableRecursiveToolCalls(boolean enableRecursiveToolCalls) {
        AdvancedSettings.ToolCallSettings tcs = AdvancedSettings.ToolCallSettings.builder()
            .cloneFrom(this.advanced.getToolCall()).enableRecursive(enableRecursiveToolCalls).build();
        this.advanced = AdvancedSettings.builder().cloneFrom(this.advanced).toolCall(tcs).build();
        saveConfig();
    }

    public int getMaxToolCallDepth() { return advanced.getToolCall().getMaxDepth(); }

    public void setMaxToolCallDepth(int maxToolCallDepth) {
        int validated = Math.max(1, Math.min(25, maxToolCallDepth));
        AdvancedSettings.ToolCallSettings tcs = AdvancedSettings.ToolCallSettings.builder()
            .cloneFrom(this.advanced.getToolCall()).maxDepth(validated).build();
        this.advanced = AdvancedSettings.builder().cloneFrom(this.advanced).toolCall(tcs).build();
        saveConfig();
    }

    /**
     * 配置数据类 v2 (旧版本，用于迁移)
     */
    private static class ConfigDataV2 {
        // 基础配置
        String configVersion;
        String defaultPromptTemplate;
        Double defaultTemperature;
        Integer defaultMaxTokens;
        Integer maxContextLength;
        Integer maxContextCharacters;

        // 功能开关配置
        Boolean enableHistory;
        Boolean enableToolCall;
        Boolean enableBroadcast;
        Set<String> broadcastPlayers;
        Boolean enableChatIntegration;
        String defaultChatMode;
        Boolean enableExecuteCommand;
        Boolean executeCommandReturnFullOutput;
        Set<String> executeCommandBlocklist;
        Integer executeCommandMaxLength;
        // 全局上下文配置
        Boolean enableGlobalContext;
        String globalContextPrompt;

        // 压缩和标题生成功能配置
        Boolean enableCompressionNotification;
        Boolean enableTitleGeneration;

        // Wiki API 配置
        String wikiApiUrl;
        Set<String> wikiAllowedHosts;

        // 多轮工具调用配置
        Boolean enableRecursiveToolCalls;
        Integer maxToolCallDepth;

        // 系统配置
        ConcurrencySettings concurrencySettings;
        LogConfig logConfig;
        List<Provider> providers;

        // 模型相关配置（放在最后）
        String compressionModel;
        String titleGenerationModel;
        String currentProvider;
        String currentModel;
    }

    /**
     * 配置数据类 v3 (当前版本，嵌套结构)
     */
    private static class ConfigData {
        String configVersion;
        String currentProvider;
        String currentModel;
        List<Provider> providers;
        ChatSettings chat;
        SecuritySettings security;
        ModelExtras models;
        AdvancedSettings advanced;
    }
}

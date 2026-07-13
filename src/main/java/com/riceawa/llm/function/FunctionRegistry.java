package com.riceawa.llm.function;

import com.google.gson.JsonObject;
import com.riceawa.llm.compat.ServerThreadCompat;
import com.riceawa.llm.core.LLMConfig;
import com.riceawa.llm.function.impl.WikiBatchPagesFunction;
import com.riceawa.llm.function.impl.WikiPageFunction;
import com.riceawa.llm.function.impl.WikiSearchFunction;
import com.riceawa.llm.logging.LogManager;
import com.riceawa.llm.util.EntityHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;

import java.util.*;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

/**
 * Function注册表，管理所有可用的LLM函数
 */
public class FunctionRegistry {
    private static final int IO_THREADS = 2;
    private static final int IO_QUEUE_CAPACITY = 64;
    private static final AtomicInteger IO_THREAD_COUNTER = new AtomicInteger();
    private static final Set<String> AUDITED_ASYNC_FUNCTIONS = Set.of(
            "wiki_search", "wiki_page", "wiki_batch_pages");
    private static final Set<Class<? extends LLMFunction>> AUDITED_ASYNC_FUNCTION_TYPES = Set.of(
            WikiSearchFunction.class, WikiPageFunction.class, WikiBatchPagesFunction.class);
    private static final ThreadPoolExecutor TOOL_IO_EXECUTOR = new ThreadPoolExecutor(
            IO_THREADS,
            IO_THREADS,
            30L,
            TimeUnit.SECONDS,
            new ArrayBlockingQueue<>(IO_QUEUE_CAPACITY),
            runnable -> {
                Thread thread = new Thread(runnable,
                        "LumiChat-Tool-IO-" + IO_THREAD_COUNTER.incrementAndGet());
                thread.setDaemon(true);
                return thread;
            },
            new ThreadPoolExecutor.AbortPolicy());

    private static volatile FunctionRegistry instance;
    private final Map<String, LLMFunction> functions;
    private final Map<String, Set<String>> categoryFunctions;

    private FunctionRegistry() {
        this.functions = new ConcurrentHashMap<>();
        this.categoryFunctions = new ConcurrentHashMap<>();
        registerDefaultFunctions();
    }

    public static FunctionRegistry getInstance() {
        if (instance == null) {
            synchronized (FunctionRegistry.class) {
                if (instance == null) {
                    instance = new FunctionRegistry();
                }
            }
        }
        return instance;
    }

    /**
     * 注册函数
     */
    public void registerFunction(LLMFunction function) {
        functions.put(function.getName(), function);
        
        // 添加到类别映射
        String category = function.getCategory();
        categoryFunctions.computeIfAbsent(category, k -> new HashSet<>()).add(function.getName());
    }

    /**
     * 注销函数
     */
    public void unregisterFunction(String name) {
        LLMFunction function = functions.remove(name);
        if (function != null) {
            String category = function.getCategory();
            Set<String> categorySet = categoryFunctions.get(category);
            if (categorySet != null) {
                categorySet.remove(name);
                if (categorySet.isEmpty()) {
                    categoryFunctions.remove(category);
                }
            }
        }
    }

    /**
     * 获取函数
     */
    public LLMFunction getFunction(String name) {
        return functions.get(name);
    }

    /**
     * 获取所有函数
     */
    public Collection<LLMFunction> getAllFunctions() {
        return new ArrayList<>(functions.values());
    }

    /**
     * 获取启用的函数
     */
    public Collection<LLMFunction> getEnabledFunctions() {
        return functions.values().stream()
                .filter(LLMFunction::isEnabled)
                .collect(Collectors.toList());
    }

    /**
     * 获取玩家可用的函数
     */
    public Collection<LLMFunction> getAvailableFunctions(Player player) {
        return functions.values().stream()
                .filter(LLMFunction::isEnabled)
                .filter(function -> function.hasPermission(player))
                .collect(Collectors.toList());
    }

    /**
     * 获取指定类别的函数
     */
    public Collection<LLMFunction> getFunctionsByCategory(String category) {
        Set<String> functionNames = categoryFunctions.get(category);
        if (functionNames == null) {
            return Collections.emptyList();
        }
        
        return functionNames.stream()
                .map(functions::get)
                .filter(Objects::nonNull)
                .collect(Collectors.toList());
    }

    /**
     * 获取所有类别
     */
    public Set<String> getCategories() {
        return new HashSet<>(categoryFunctions.keySet());
    }

    /**
     * 检查函数是否存在
     */
    public boolean hasFunction(String name) {
        return functions.containsKey(name);
    }

    /**
     * 为LLM配置生成工具定义（新的OpenAI API格式）
     */
    public List<LLMConfig.ToolDefinition> generateToolDefinitions(Player player) {
        Collection<LLMFunction> availableFunctions = getAvailableFunctions(player);
        List<LLMConfig.ToolDefinition> definitions = new ArrayList<>();

        for (LLMFunction function : availableFunctions) {
            LLMConfig.FunctionDefinition functionDef = new LLMConfig.FunctionDefinition(
                    function.getName(),
                    function.getDescription(),
                    function.getParametersSchema()
            );
            LLMConfig.ToolDefinition toolDef = new LLMConfig.ToolDefinition(functionDef);
            definitions.add(toolDef);
        }

        return definitions;
    }

    /**
     * 为LLM配置生成函数定义（保持向后兼容）
     * @deprecated 使用 generateToolDefinitions 替代
     */
    @Deprecated
    public List<LLMConfig.FunctionDefinition> generateFunctionDefinitions(Player player) {
        Collection<LLMFunction> availableFunctions = getAvailableFunctions(player);
        List<LLMConfig.FunctionDefinition> definitions = new ArrayList<>();

        for (LLMFunction function : availableFunctions) {
            LLMConfig.FunctionDefinition definition = new LLMConfig.FunctionDefinition(
                    function.getName(),
                    function.getDescription(),
                    function.getParametersSchema()
            );
            definitions.add(definition);
        }

        return definitions;
    }

    /**
     * 执行工具调用
     */
    LLMFunction.FunctionResult executeFunction(String functionName, Player player,
                                               JsonObject arguments) {
        Objects.requireNonNull(player, "player");
        net.minecraft.server.MinecraftServer server = EntityHelper.getServerSafe(player);
        if (server == null || !server.isSameThread()) {
            throw new IllegalStateException("Minecraft function must run on server thread");
        }

        LLMFunction function = getFunction(functionName);
        if (function == null) {
            return LLMFunction.FunctionResult.error("函数不存在: " + functionName);
        }
        
        if (!function.isEnabled()) {
            return LLMFunction.FunctionResult.error("函数已禁用: " + functionName);
        }
        
        if (!function.hasPermission(player)) {
            return LLMFunction.FunctionResult.error("没有权限调用函数: " + functionName);
        }

        if (function.executionMode() != LLMFunction.ExecutionMode.SERVER_THREAD) {
            return LLMFunction.FunctionResult.error("异步函数不能通过同步接口执行: " + functionName);
        }
        
        try {
            JsonObject argumentsSnapshot = arguments == null ? new JsonObject() : arguments.deepCopy();
            auditExecutionIfGeneric(functionName, player, function.executionMode());
            return function.execute(player, server, argumentsSnapshot);
        } catch (Exception e) {
            logExecutionFailure(functionName, player, function.executionMode());
            return LLMFunction.FunctionResult.error("函数执行失败: " + e.getMessage());
        }
    }

    /**
     * Validates a tool call on the server thread, then dispatches it to its declared executor.
     */
    public CompletableFuture<LLMFunction.FunctionResult> executeFunctionAsync(
            String functionName, ServerPlayer player, JsonObject arguments) {
        Objects.requireNonNull(functionName, "functionName");
        Objects.requireNonNull(player, "player");

        net.minecraft.server.MinecraftServer server = EntityHelper.getServer(player);
        CompletableFuture<LLMFunction.FunctionResult> resultFuture = new CompletableFuture<>();

        ServerThreadCompat.execute(server, () -> {
            if (!server.isSameThread()) {
                throw new IllegalStateException("Minecraft function must run on server thread");
            }

            LLMFunction function = getFunction(functionName);
            if (function == null) {
                resultFuture.complete(LLMFunction.FunctionResult.error("函数不存在: " + functionName));
                return;
            }
            if (!function.isEnabled()) {
                resultFuture.complete(LLMFunction.FunctionResult.error("函数已禁用: " + functionName));
                return;
            }
            if (!function.hasPermission(player)) {
                resultFuture.complete(LLMFunction.FunctionResult.error("没有权限调用函数: " + functionName));
                return;
            }

            JsonObject argumentsSnapshot = arguments == null ? new JsonObject() : arguments.deepCopy();
            LLMFunction.ExecutionMode mode = function.executionMode();
            if (mode == LLMFunction.ExecutionMode.SERVER_THREAD) {
                resultFuture.complete(executeFunction(functionName, player, argumentsSnapshot));
                return;
            }

            if (!AUDITED_ASYNC_FUNCTIONS.contains(functionName)
                    || !AUDITED_ASYNC_FUNCTION_TYPES.contains(function.getClass())) {
                resultFuture.complete(LLMFunction.FunctionResult.error(
                        "函数未获准异步执行: " + functionName));
                return;
            }

            auditExecution(functionName, player, mode);
            UUID playerId = player.getUUID();
            try {
                TOOL_IO_EXECUTOR.execute(() -> executeAsyncFunction(
                        functionName, playerId, function, argumentsSnapshot, resultFuture));
            } catch (RejectedExecutionException exception) {
                logExecutionFailure(functionName, playerId, mode);
                resultFuture.complete(LLMFunction.FunctionResult.error("函数执行失败: 工具执行队列已满"));
            }
        }).whenComplete((ignored, throwable) -> {
            if (throwable != null) {
                resultFuture.completeExceptionally(unwrapCompletionException(throwable));
            }
        });

        return resultFuture;
    }

    private void executeAsyncFunction(String functionName, UUID playerId, LLMFunction function,
                                      JsonObject argumentsSnapshot,
                                      CompletableFuture<LLMFunction.FunctionResult> resultFuture) {
        try {
            // Audited ASYNC functions receive no Minecraft objects and only a detached argument tree.
            resultFuture.complete(function.execute(null, null, argumentsSnapshot));
        } catch (Throwable throwable) {
            logExecutionFailure(functionName, playerId, function.executionMode());
            resultFuture.complete(LLMFunction.FunctionResult.error(
                    "函数执行失败: " + throwable.getMessage()));
        }
    }

    private static Throwable unwrapCompletionException(Throwable throwable) {
        if (throwable instanceof java.util.concurrent.CompletionException
                && throwable.getCause() != null) {
            return throwable.getCause();
        }
        return throwable;
    }

    static boolean shouldAuditGeneric(String functionName) {
        return !"execute_command".equals(functionName);
    }

    private static void auditExecutionIfGeneric(String functionName, Player player,
                                                LLMFunction.ExecutionMode mode) {
        if (shouldAuditGeneric(functionName)) {
            auditExecution(functionName, player, mode);
        }
    }

    private static void auditExecution(String functionName, Player player,
                                       LLMFunction.ExecutionMode mode) {
        auditExecution(functionName, player.getUUID(), mode);
    }

    private static void auditExecution(String functionName, UUID playerId,
                                       LLMFunction.ExecutionMode mode) {
        LogManager.getInstance().audit("Tool execution", Map.of(
                "function", functionName,
                "player_uuid", playerId.toString(),
                "mode", mode.name()));
    }

    private static void logExecutionFailure(String functionName, Player player,
                                            LLMFunction.ExecutionMode mode) {
        logExecutionFailure(functionName, player.getUUID(), mode);
    }

    private static void logExecutionFailure(String functionName, UUID playerId,
                                            LLMFunction.ExecutionMode mode) {
        LogManager.getInstance().error("Tool execution failed [function=" + functionName
                + ", player_uuid=" + playerId + ", mode=" + mode.name() + "]");
    }

    /**
     * 注册默认函数
     */
    private void registerDefaultFunctions() {
        // 注册基础信息函数
        registerFunction(new GetTimeFunction());
        registerFunction(new GetPlayerInfoFunction());
        registerFunction(new GetWeatherFunction());

        // 注册信息查询函数
        registerFunction(new com.riceawa.llm.function.impl.WorldInfoFunction());
        registerFunction(new com.riceawa.llm.function.impl.PlayerStatsFunction());
        registerFunction(new com.riceawa.llm.function.impl.InventoryFunction());
        registerFunction(new com.riceawa.llm.function.impl.ServerInfoFunction());
        registerFunction(new com.riceawa.llm.function.impl.NearbyEntitiesFunction());
        registerFunction(new com.riceawa.llm.function.impl.PlayerEffectsFunction());

        // 注册交互功能函数
        registerFunction(new com.riceawa.llm.function.impl.SendMessageFunction());
        registerFunction(new com.riceawa.llm.function.impl.TeleportPlayerFunction());

        // 注册管理员功能函数（需要OP权限）
        registerFunction(new com.riceawa.llm.function.impl.ExecuteCommandFunction());
        registerFunction(new com.riceawa.llm.function.impl.SetBlockFunction());
        registerFunction(new com.riceawa.llm.function.impl.SummonEntityFunction());
        registerFunction(new com.riceawa.llm.function.impl.WeatherControlFunction());
        registerFunction(new com.riceawa.llm.function.impl.TimeControlFunction());
        
        // 注册Wiki功能函数
        registerFunction(new com.riceawa.llm.function.impl.WikiSearchFunction());
        registerFunction(new com.riceawa.llm.function.impl.WikiPageFunction());
        registerFunction(new com.riceawa.llm.function.impl.WikiBatchPagesFunction());
    }

    /**
     * 获取时间函数（示例）
     */
    private static class GetTimeFunction implements LLMFunction {
        @Override
        public String getName() {
            return "get_time";
        }

        @Override
        public String getDescription() {
            return "获取当前游戏时间";
        }

        @Override
        public JsonObject getParametersSchema() {
            JsonObject schema = new JsonObject();
            schema.addProperty("type", "object");
            schema.add("properties", new JsonObject());
            return schema;
        }

        @Override
        public FunctionResult execute(Player player, net.minecraft.server.MinecraftServer server, JsonObject arguments) {
            ServerLevel world = EntityHelper.getServerWorldSafe(player);
            if (world == null) {
                return FunctionResult.error("无法获取世界信息");
            }
            long time = EntityHelper.getDayTime(world);
            int hours = (int) ((time / 1000 + 6) % 24);
            int minutes = (int) ((time % 1000) * 60 / 1000);
            
            String timeString = String.format("%02d:%02d", hours, minutes);
            return FunctionResult.success("当前游戏时间是: " + timeString);
        }

        @Override
        public boolean hasPermission(Player player) {
            return true; // 所有玩家都可以查看时间
        }

        @Override
        public boolean isEnabled() {
            return true;
        }

        @Override
        public String getCategory() {
            return "info";
        }
    }

    /**
     * 获取玩家信息函数（示例）
     */
    private static class GetPlayerInfoFunction implements LLMFunction {
        @Override
        public String getName() {
            return "get_player_info";
        }

        @Override
        public String getDescription() {
            return "获取玩家基本信息";
        }

        @Override
        public JsonObject getParametersSchema() {
            JsonObject schema = new JsonObject();
            schema.addProperty("type", "object");
            schema.add("properties", new JsonObject());
            return schema;
        }

        @Override
        public FunctionResult execute(Player player, net.minecraft.server.MinecraftServer server, JsonObject arguments) {
            String info = String.format("玩家: %s, 生命值: %.1f/%.1f, 经验等级: %d", 
                    player.getName().getString(),
                    player.getHealth(),
                    player.getMaxHealth(),
                    player.experienceLevel);
            
            return FunctionResult.success(info);
        }

        @Override
        public boolean hasPermission(Player player) {
            return true;
        }

        @Override
        public boolean isEnabled() {
            return true;
        }

        @Override
        public String getCategory() {
            return "info";
        }
    }

    /**
     * 获取天气函数（示例）
     */
    private static class GetWeatherFunction implements LLMFunction {
        @Override
        public String getName() {
            return "get_weather";
        }

        @Override
        public String getDescription() {
            return "获取当前天气信息";
        }

        @Override
        public JsonObject getParametersSchema() {
            JsonObject schema = new JsonObject();
            schema.addProperty("type", "object");
            schema.add("properties", new JsonObject());
            return schema;
        }

        @Override
        public FunctionResult execute(Player player, net.minecraft.server.MinecraftServer server, JsonObject arguments) {
            var world = EntityHelper.getWorld(player);
            if (world == null) {
                return FunctionResult.error("无法获取世界信息");
            }
            boolean isRaining = world.isRaining();
            boolean isThundering = world.isThundering();
            
            String weather;
            if (isThundering) {
                weather = "雷雨";
            } else if (isRaining) {
                weather = "下雨";
            } else {
                weather = "晴朗";
            }
            
            return FunctionResult.success("当前天气: " + weather);
        }

        @Override
        public boolean hasPermission(Player player) {
            return true;
        }

        @Override
        public boolean isEnabled() {
            return true;
        }

        @Override
        public String getCategory() {
            return "info";
        }
    }
}

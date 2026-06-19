package com.riceawa.llm.function.impl;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.riceawa.llm.config.LLMChatConfig;
import com.riceawa.llm.function.LLMFunction;
import net.minecraft.world.entity.player.Player;
import net.minecraft.server.MinecraftServer;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

import java.io.IOException;
import java.util.concurrent.TimeUnit;

/**
 * Wiki搜索函数，用于搜索Minecraft Wiki内容
 */
public class WikiSearchFunction implements LLMFunction {
    
    private static final OkHttpClient httpClient = new OkHttpClient.Builder()
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .build();
    
    private static final Gson gson = new Gson();
    
    @Override
    public String getName() {
        return "wiki_search";
    }
    
    @Override
    public String getDescription() {
        return "搜索Minecraft Wiki内容，获取相关页面信息。Returns:搜索结果字典，包含匹配页面列表和分页信息,返回的结果并不是详细信息，请根据情况决定是否在使用该工具后接着使用pageAPI查看详细信息。Notes:此工具只能依照关键词匹配搜索描述，可能无法返回跟关键词完全匹配的内容，注意分辨";
    }
    
    @Override
    public JsonObject getParametersSchema() {
        JsonObject schema = new JsonObject();
        schema.addProperty("type", "object");
        JsonObject properties = new JsonObject();
        
        // 必需参数：搜索关键词
        JsonObject query = new JsonObject();
        query.addProperty("type", "string");
        query.addProperty("description", "搜索关键词，支持中文和英文，(尽量使用中文)");
        properties.add("query", query);
        
        // 可选参数：结果数量限制
        JsonObject limit = new JsonObject();
        limit.addProperty("type", "integer");
        limit.addProperty("description", "搜索结果数量限制，默认5，最大20");
        limit.addProperty("minimum", 1);
        limit.addProperty("maximum", 20);
        limit.addProperty("default", 5);
        properties.add("limit", limit);
        
        // 可选参数：命名空间
        JsonObject namespaces = new JsonObject();
        namespaces.addProperty("type", "string");
        namespaces.addProperty("description", "搜索的命名空间，多个用逗号分隔（可选）");
        properties.add("namespaces", namespaces);
        
        schema.add("properties", properties);
        
        // 必需参数列表
        JsonArray required = new JsonArray();
        required.add("query");
        schema.add("required", required);
        
        return schema;
    }
    
    @Override
    public FunctionResult execute(Player player, MinecraftServer server, JsonObject arguments) {
        try {
            // 解析参数
            if (!arguments.has("query")) {
                return FunctionResult.error("缺少必需参数: query");
            }
            
            String query = arguments.get("query").getAsString().trim();
            if (query.isEmpty()) {
                return FunctionResult.error("搜索关键词不能为空");
            }
            
            int limit = arguments.has("limit") ? arguments.get("limit").getAsInt() : 5;
            if (limit < 1 || limit > 20) {
                limit = Math.max(1, Math.min(20, limit));
            }
            
            String namespaces = arguments.has("namespaces") ? 
                    arguments.get("namespaces").getAsString() : null;
            
            // 构建API请求URL
            String wikiBaseUrl = LLMChatConfig.getInstance().getWikiApiUrl();
            StringBuilder urlBuilder = new StringBuilder(wikiBaseUrl + "/api/search");
            urlBuilder.append("?q=").append(java.net.URLEncoder.encode(query, "UTF-8"));
            urlBuilder.append("&limit=").append(limit);
            if (namespaces != null && !namespaces.trim().isEmpty()) {
                urlBuilder.append("&namespaces=").append(java.net.URLEncoder.encode(namespaces, "UTF-8"));
            }
            
            String url = urlBuilder.toString();
            
            // 发送HTTP请求
            Request request = new Request.Builder()
                    .url(url)
                    .addHeader("User-Agent", "Luminous-LLMChat-Mod/1.0")
                    .build();
            
            try (Response response = httpClient.newCall(request).execute()) {
                // 使用统一的HTTP响应处理
                WikiErrorHandler.HttpResponseResult httpResult = WikiErrorHandler.handleHttpResponse(response, query);
                if (!httpResult.isSuccess()) {
                    return httpResult.errorResult;
                }
                
                JsonObject jsonResponse = httpResult.jsonResponse;
                
                // 解析搜索结果
                JsonObject data = jsonResponse.getAsJsonObject("data");
                JsonArray results = data.getAsJsonArray("results");
                JsonObject pagination = data.getAsJsonObject("pagination");
                
                if (results.size() == 0) {
                    return FunctionResult.success("没有找到与 \"" + query + "\" 相关的Wiki页面");
                }
                
                // 格式化搜索结果
                StringBuilder resultText = new StringBuilder();
                resultText.append("=== Wiki搜索结果 ===\n");
                resultText.append("搜索关键词: ").append(query).append("\n");
                
                // 安全获取totalHits字段
                int totalHits = results.size(); // 默认值为结果数组长度
                if (pagination.has("totalHits") && !pagination.get("totalHits").isJsonNull()) {
                    totalHits = pagination.get("totalHits").getAsInt();
                }
                
                resultText.append("找到 ").append(totalHits)
                         .append(" 个结果，显示前 ").append(results.size()).append(" 个:\n\n");
                
                for (int i = 0; i < results.size(); i++) {
                    JsonObject result = results.get(i).getAsJsonObject();
                    String title = result.get("title").getAsString();
                    String snippet = result.get("snippet").getAsString();
                    String namespace = result.get("namespace").getAsString();
                    
                    resultText.append(i + 1).append(". ").append(title);
                    if (!namespace.equals("主要")) {
                        resultText.append(" (").append(namespace).append(")");
                    }
                    resultText.append("\n");
                    
                    // 限制摘要长度
                    if (snippet.length() > 100) {
                        snippet = snippet.substring(0, 100) + "...";
                    }
                    resultText.append("   ").append(snippet).append("\n\n");
                }
                
                // 安全检查hasMore字段
                if (pagination.has("hasMore") && !pagination.get("hasMore").isJsonNull() 
                    && pagination.get("hasMore").getAsBoolean()) {
                    resultText.append("💡 提示: 还有更多搜索结果，可以使用 wiki_page 函数获取具体页面内容\n");
                }
                
                // 返回结构化数据
                JsonObject resultData = new JsonObject();
                resultData.addProperty("searchQuery", query);
                resultData.add("results", results);
                resultData.add("pagination", pagination);
                
                return FunctionResult.success(resultText.toString(), resultData);
                
            }
            
        } catch (IOException e) {
            return FunctionResult.error("网络请求失败: " + e.getMessage());
        } catch (Exception e) {
            return FunctionResult.error("搜索处理失败: " + e.getMessage());
        }
    }
    
    @Override
    public boolean hasPermission(Player player) {
        return true; // 所有玩家都可以搜索Wiki
    }
    
    @Override
    public boolean isEnabled() {
        return true;
    }
    
    @Override
    public String getCategory() {
        return "wiki";
    }
}
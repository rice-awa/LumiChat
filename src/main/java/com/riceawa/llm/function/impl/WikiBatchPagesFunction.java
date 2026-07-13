package com.riceawa.llm.function.impl;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.riceawa.llm.config.LLMChatConfig;
import com.riceawa.llm.function.LLMFunction;
import com.riceawa.llm.function.WikiEndpointPolicy;
import net.minecraft.world.entity.player.Player;
import net.minecraft.server.MinecraftServer;
import okhttp3.HttpUrl;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * Wiki批量页面获取函数，用于同时获取多个Wiki页面的内容
 */
public class WikiBatchPagesFunction implements LLMFunction {
    
    private static final OkHttpClient httpClient = WikiEndpointPolicy.newSecureClientBuilder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)  // 批量请求需要更长时间
            .build();
    
    private static final Gson gson = new Gson();
    private static final MediaType JSON = MediaType.get("application/json; charset=utf-8");
    
    @Override
    public String getName() {
        return "wiki_batch_pages";
    }
    
    @Override
    public String getDescription() {
        return "批量获取多个Minecraft Wiki页面的内容，支持同时查询多个页面";
    }
    
    @Override
    public JsonObject getParametersSchema() {
        JsonObject schema = new JsonObject();
        schema.addProperty("type", "object");
        JsonObject properties = new JsonObject();
        
        // 必需参数：页面名称列表
        JsonObject pages = new JsonObject();
        pages.addProperty("type", "array");
        pages.addProperty("description", "要获取的Wiki页面名称列表");
        JsonObject items = new JsonObject();
        items.addProperty("type", "string");
        pages.add("items", items);
        pages.addProperty("minItems", 1);
        pages.addProperty("maxItems", 20);  // API限制最大20个
        properties.add("pages", pages);
        
        // 可选参数：输出格式
        JsonObject format = new JsonObject();
        format.addProperty("type", "string");
        format.addProperty("description", "输出格式：markdown（默认）或 html");
        JsonArray enumArray = new JsonArray();
        enumArray.add("markdown");
        enumArray.add("html");
        format.add("enum", enumArray);
        format.addProperty("default", "markdown");
        properties.add("format", format);
        
        // 可选参数：并发数
        JsonObject concurrency = new JsonObject();
        concurrency.addProperty("type", "integer");
        concurrency.addProperty("description", "并发处理数量，默认3，最大5");
        concurrency.addProperty("minimum", 1);
        concurrency.addProperty("maximum", 5);
        concurrency.addProperty("default", 3);
        properties.add("concurrency", concurrency);
        
        // 可选参数：是否使用缓存
        JsonObject useCache = new JsonObject();
        useCache.addProperty("type", "boolean");
        useCache.addProperty("description", "是否使用缓存");
        useCache.addProperty("default", true);
        properties.add("use_cache", useCache);
        
        // 可选参数：每页内容长度限制
        JsonObject maxLength = new JsonObject();
        maxLength.addProperty("type", "integer");
        maxLength.addProperty("description", "每个页面内容长度限制（字符数），0表示不限制内容长度限制（字符数）。");
        maxLength.addProperty("minimum", 0);
        maxLength.addProperty("maximum", 8000);
        maxLength.addProperty("default", 1000);
        properties.add("max_length", maxLength);
        
        schema.add("properties", properties);
        
        // 必需参数列表
        JsonArray required = new JsonArray();
        required.add("pages");
        schema.add("required", required);
        
        return schema;
    }
    
    @Override
    public FunctionResult execute(Player player, MinecraftServer server, JsonObject arguments) {
        try {
            // 解析参数
            if (!arguments.has("pages")) {
                return FunctionResult.error("缺少必需参数: pages");
            }
            
            JsonArray pagesArray = arguments.getAsJsonArray("pages");
            if (pagesArray.size() == 0) {
                return FunctionResult.error("页面列表不能为空");
            }
            
            if (pagesArray.size() > 20) {
                return FunctionResult.error("页面数量不能超过20个（当前: " + pagesArray.size() + "）");
            }
            
            // 转换为字符串列表
            List<String> pageNames = new ArrayList<>();
            for (JsonElement element : pagesArray) {
                String pageName = element.getAsString().trim();
                if (!pageName.isEmpty()) {
                    pageNames.add(pageName);
                }
            }
            
            if (pageNames.isEmpty()) {
                return FunctionResult.error("没有有效的页面名称");
            }
            
            String format = arguments.has("format") ? 
                    arguments.get("format").getAsString() : "markdown";
            if (!format.equals("markdown") && !format.equals("html")) {
                format = "markdown";
            }
            
            int concurrency = arguments.has("concurrency") ? 
                    arguments.get("concurrency").getAsInt() : 3;
            if (concurrency < 1 || concurrency > 5) {
                concurrency = Math.max(1, Math.min(5, concurrency));
            }
            
            boolean useCache = !arguments.has("use_cache") || 
                              arguments.get("use_cache").getAsBoolean();
            
            int maxLength = arguments.has("max_length") ? 
                    arguments.get("max_length").getAsInt() : 1000;
            if (maxLength < 0) maxLength = 1000;
            if (maxLength > 8000) maxLength = 8000;
            
            // 构建请求体
            JsonObject requestBody = new JsonObject();
            requestBody.add("pages", pagesArray);
            requestBody.addProperty("format", format);
            requestBody.addProperty("concurrency", concurrency);
            requestBody.addProperty("useCache", useCache);
            
            HttpUrl url = WikiEndpointPolicy.validate(
                    LLMChatConfig.getInstance().getWikiApiUrl(),
                    LLMChatConfig.getInstance().getWikiAllowedHosts())
                    .newBuilder()
                    .addPathSegments("api/pages")
                    .build();
            String requestBodyString = gson.toJson(requestBody);
            
            // 发送HTTP POST请求
            RequestBody body = RequestBody.create(requestBodyString, JSON);
            Request request = new Request.Builder()
                    .url(url)
                    .post(body)
                    .addHeader("User-Agent", "Luminous-LLMChat-Mod/1.0")
                    .addHeader("Content-Type", "application/json")
                    .build();
            
            try (Response response = httpClient.newCall(request).execute()) {
                if (!response.isSuccessful()) {
                    return FunctionResult.error("Wiki API请求失败: HTTP " + response.code());
                }
                
                String responseBody = response.body().string();
                JsonObject jsonResponse = gson.fromJson(responseBody, JsonObject.class);
                
                if (jsonResponse == null) {
                    return FunctionResult.error("Wiki API返回无效响应");
                }
                
                if (!jsonResponse.has("success") || !jsonResponse.get("success").getAsBoolean()) {
                    if (jsonResponse.has("error")) {
                        JsonObject error = jsonResponse.getAsJsonObject("error");
                        String errorMsg = WikiErrorHandler.handleError(error, null);
                        return FunctionResult.error("Wiki批量获取失败: " + errorMsg);
                    } else {
                        return FunctionResult.error("Wiki批量获取失败: 未知错误");
                    }
                }
                
                // 解析批量结果
                JsonObject data = jsonResponse.getAsJsonObject("data");
                if (data == null) {
                    return FunctionResult.error("Wiki API返回数据为空");
                }
                JsonObject results = data.getAsJsonObject("results");
                if (results == null) {
                    return FunctionResult.error("Wiki API返回结果为空");
                }
                JsonObject summary = data.getAsJsonObject("summary");
                if (summary == null) {
                    return FunctionResult.error("Wiki API返回统计信息为空");
                }
                
                int totalPages = summary.get("totalPages").getAsInt();
                int successCount = summary.get("successCount").getAsInt();
                int errorCount = summary.get("errorCount").getAsInt();
                
                // 格式化返回结果
                StringBuilder resultText = new StringBuilder();
                resultText.append("=== Wiki批量页面获取结果 ===\n");
                resultText.append("请求页面数: ").append(totalPages)
                         .append("，成功: ").append(successCount)
                         .append("，失败: ").append(errorCount).append("\n\n");
                
                // 处理每个页面的结果
                int processedCount = 0;
                for (String pageName : pageNames) {
                    if (results.has(pageName)) {
                        JsonObject pageResult = results.getAsJsonObject(pageName);
                        boolean pageSuccess = pageResult.get("success").getAsBoolean();
                        
                        resultText.append("【").append(++processedCount).append("】 ").append(pageName);
                        
                        if (pageSuccess) {
                            resultText.append(" ✅\n");
                            JsonObject pageData = pageResult.getAsJsonObject("data");
                            if (pageData == null) {
                                resultText.append("错误: 页面数据为空\n\n");
                                continue;
                            }
                            JsonObject page = pageData.getAsJsonObject("page");
                            if (page == null) {
                                resultText.append("错误: 页面信息为空\n\n");
                                continue;
                            }
                            JsonObject content = page.getAsJsonObject("content");
                            if (content == null) {
                                resultText.append("错误: 页面内容为空\n\n");
                                continue;
                            }
                            
                            String pageContent;
                            if (format.equals("markdown")) {
                                if (content.has("markdown") && !content.get("markdown").isJsonNull()) {
                                    pageContent = content.get("markdown").getAsString();
                                } else {
                                    pageContent = "内容不可用（markdown格式）";
                                }
                            } else {
                                if (content.has("html") && !content.get("html").isJsonNull()) {
                                    pageContent = content.get("html").getAsString();
                                } else {
                                    pageContent = "内容不可用（html格式）";
                                }
                            }
                            
                            // 应用长度限制
                            if (maxLength > 0 && pageContent.length() > maxLength) {
                                pageContent = pageContent.substring(0, maxLength) + "\n[内容过长，已截断...]";
                            }
                            
                            resultText.append(pageContent).append("\n\n");
                            resultText.append("────────────────────────────────\n\n");
                            
                        } else {
                            resultText.append(" ❌\n");
                            if (pageResult.has("error")) {
                                JsonObject pageError = pageResult.getAsJsonObject("error");
                                String errorMsg = WikiErrorHandler.handleBatchPageError(pageError, pageName, 3);
                                resultText.append("错误: ").append(errorMsg).append("\n");
                            } else {
                                resultText.append("错误: 未知错误\n\n");
                            }
                        }
                        
                        // 防止单次返回内容过长
                        if (resultText.length() > 8000) {
                            resultText.append("...\n[响应内容过长，已截断剩余页面]");
                            break;
                        }
                    }
                }
                
                // 添加版权信息
                resultText.append("📖 内容来源: 中文 Minecraft Wiki (CC BY-NC-SA 3.0)\n");
                resultText.append("⚡ 处理格式: ").append(format)
                         .append("，并发数: ").append(concurrency).append("\n");
                
                // 返回结构化数据
                JsonObject resultData = new JsonObject();
                resultData.add("requestedPages", pagesArray);
                resultData.add("summary", summary);
                resultData.addProperty("format", format);
                resultData.addProperty("concurrency", concurrency);
                resultData.addProperty("useCache", useCache);
                resultData.addProperty("maxLength", maxLength);
                
                return FunctionResult.success(resultText.toString(), resultData);
                
            }
            
        } catch (IOException e) {
            return FunctionResult.error("网络请求失败: " + e.getMessage());
        } catch (Exception e) {
            return FunctionResult.error("批量页面处理失败: " + e.getMessage());
        }
    }

    @Override
    public ExecutionMode executionMode() {
        return ExecutionMode.ASYNC;
    }
    
    @Override
    public boolean hasPermission(Player player) {
        return true; // 所有玩家都可以批量查看Wiki页面
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

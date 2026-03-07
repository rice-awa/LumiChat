package com.riceawa.llm.compat;

import net.minecraft.util.Identifier;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Identifier 兼容层
 * 统一处理不同 Minecraft 版本的 Identifier 构造 API 差异
 * 
 * <p>在 1.21.5+ 中，Identifier 构造方法被废弃，推荐使用：
 * <ul>
 *   <li>Identifier.of(String namespace, String path)</li>
 *   <li>Identifier.of(String id)</li>
 * </ul>
 * 
 * <p>在旧版本中，使用构造方法：
 * <ul>
 *   <li>new Identifier(String namespace, String path)</li>
 *   <li>new Identifier(String id)</li>
 * </ul>
 * 
 * <p>注意：当前支持的版本 1.21.5 及以上都支持 Identifier.of()，
 * 此兼容层保留以便未来可能的版本扩展。
 */
public final class IdentifierCompat {
    
    private IdentifierCompat() {}
    
    /**
     * 解析或创建 Identifier
     * 优先使用 Identifier.tryParse，失败时创建 minecraft 命名空间的 Identifier
     * 
     * @param id ID 字符串
     * @return 解析的 Identifier，如果无法解析则返回 null
     */
    @Nullable
    public static Identifier parse(String id) {
        return Identifier.tryParse(id);
    }
    
    /**
     * 解析或创建 Identifier
     * 优先使用 Identifier.tryParse，失败时创建 minecraft 命名空间的 Identifier
     * 
     * @param id ID 字符串（可以包含命名空间或不包含）
     * @return 解析或创建的 Identifier
     */
    @NotNull
    public static Identifier parseOrCreate(String id) {
        Identifier parsed = Identifier.tryParse(id);
        if (parsed != null) {
            return parsed;
        }
        // 如果无法解析，尝试作为 minecraft 命名空间的路径
        //? >=1.21.5 {
        return Identifier.of("minecraft", id);
        //?} else {
        /*return new Identifier("minecraft", id);
        *//*?}*/
    }
    
    /**
     * 创建带有命名空间的 Identifier
     * 
     * @param namespace 命名空间
     * @param path 路径
     * @return 新创建的 Identifier
     */
    @NotNull
    public static Identifier of(String namespace, String path) {
        //? >=1.21.5 {
        return Identifier.of(namespace, path);
        //?} else {
        /*return new Identifier(namespace, path);
        *//*?}*/
    }
    
    /**
     * 从完整 ID 字符串创建 Identifier
     * 如果字符串不包含命名空间，默认使用 minecraft
     * 
     * @param id 完整 ID 字符串（如 "minecraft:stone" 或 "stone"）
     * @return 新创建的 Identifier
     */
    @NotNull
    public static Identifier of(String id) {
        //? >=1.21.5 {
        return Identifier.of(id);
        //?} else {
        /*return new Identifier(id);
        *//*?}*/
    }
    
    /**
     * 根据实体类型名称创建 Identifier
     * 支持带命名空间和不带命名空间两种格式
     * 
     * @param entityType 实体类型名称
     * @return 对应的 Identifier
     */
    @NotNull
    public static Identifier forEntityType(String entityType) {
        //? >=1.21.5 {
        if (entityType.contains(":")) {
            return Identifier.of(entityType);
        } else {
            return Identifier.of("minecraft", entityType);
        }
        //?} else {
        /*if (entityType.contains(":")) {
            return new Identifier(entityType);
        } else {
            return new Identifier("minecraft", entityType);
        }
        *//*?}*/
    }
    
    /**
     * 根据方块类型名称创建 Identifier
     * 支持带命名空间和不带命名空间两种格式
     * 
     * @param blockType 方块类型名称
     * @return 对应的 Identifier
     */
    @NotNull
    public static Identifier forBlockType(String blockType) {
        return forEntityType(blockType); // 逻辑相同，复用
    }
}

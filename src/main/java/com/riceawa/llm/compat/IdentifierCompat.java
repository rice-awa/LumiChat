package com.riceawa.llm.compat;

//? >=1.21.11 {
import net.minecraft.resources.Identifier;
//?} else {
/*import net.minecraft.resources.ResourceLocation;
*//*?}*/
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Identifier 兼容层
 * 统一处理不同 Minecraft 版本的 Identifier/ResourceLocation 构造 API 差异
 */
public final class IdentifierCompat {

    private IdentifierCompat() {}

    /**
     * 解析或创建 Identifier
     * 优先使用 tryParse，失败时创建 minecraft 命名空间的 Identifier
     *
     * @param id ID 字符串
     * @return 解析的 Identifier，如果无法解析则返回 null
     */
    @Nullable
    //? >=1.21.11 {
    public static Identifier parse(String id) {
        return Identifier.tryParse(id);
    //?} else {
    /*public static ResourceLocation parse(String id) {
        return ResourceLocation.tryParse(id);
    *//*?}*/
    }

    /**
     * 解析或创建 Identifier
     * 优先使用 tryParse，失败时创建 minecraft 命名空间的 Identifier
     *
     * @param id ID 字符串（可以包含命名空间或不包含）
     * @return 解析或创建的 Identifier
     */
    @NotNull
    //? >=1.21.11 {
    public static Identifier parseOrCreate(String id) {
        Identifier parsed = Identifier.tryParse(id);
    //?} else {
    /*public static ResourceLocation parseOrCreate(String id) {
        ResourceLocation parsed = ResourceLocation.tryParse(id);
    *//*?}*/
        if (parsed != null) {
            return parsed;
        }
        // 如果无法解析，尝试作为 minecraft 命名空间的路径
        //? >=1.21.11 {
        return Identifier.fromNamespaceAndPath("minecraft", id);
        //?} else if >=1.21 {
        /*return ResourceLocation.fromNamespaceAndPath("minecraft", id);
        *///?} else {
        /*return new ResourceLocation("minecraft", id);
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
    //? >=1.21.11 {
    public static Identifier of(String namespace, String path) {
    //?} else {
    /*public static ResourceLocation of(String namespace, String path) {
    *//*?}*/
        //? >=1.21.11 {
        return Identifier.fromNamespaceAndPath(namespace, path);
        //?} else if >=1.21 {
        /*return ResourceLocation.fromNamespaceAndPath(namespace, path);
        *///?} else {
        /*return new ResourceLocation(namespace, path);
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
    //? >=1.21.11 {
    public static Identifier of(String id) {
    //?} else {
    /*public static ResourceLocation of(String id) {
    *//*?}*/
        //? >=1.21.11 {
        return Identifier.parse(id);
        //?} else if >=1.21 {
        /*return ResourceLocation.parse(id);
        *///?} else {
        /*return new ResourceLocation(id);
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
    //? >=1.21.11 {
    public static Identifier forEntityType(String entityType) {
    //?} else {
    /*public static ResourceLocation forEntityType(String entityType) {
    *//*?}*/
        if (entityType.contains(":")) {
            //? >=1.21.11 {
            return Identifier.parse(entityType);
            //?} else if >=1.21 {
            /*return ResourceLocation.parse(entityType);
            *///?} else {
            /*return new ResourceLocation(entityType);
            *//*?}*/
        } else {
            //? >=1.21.11 {
            return Identifier.fromNamespaceAndPath("minecraft", entityType);
            //?} else if >=1.21 {
            /*return ResourceLocation.fromNamespaceAndPath("minecraft", entityType);
            *///?} else {
            /*return new ResourceLocation("minecraft", entityType);
            *//*?}*/
        }
    }

    /**
     * 根据方块类型名称创建 Identifier
     * 支持带命名空间和不带命名空间两种格式
     *
     * @param blockType 方块类型名称
     * @return 对应的 Identifier
     */
    @NotNull
    //? >=1.21.11 {
    public static Identifier forBlockType(String blockType) {
    //?} else {
    /*public static ResourceLocation forBlockType(String blockType) {
    *//*?}*/
        return forEntityType(blockType);
    }
}

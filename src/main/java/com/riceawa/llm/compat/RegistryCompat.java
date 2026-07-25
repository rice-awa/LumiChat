package com.riceawa.llm.compat;

//? >=1.21.11 {
import net.minecraft.resources.Identifier;
//?} else {
/*import net.minecraft.resources.ResourceLocation;
*//*?}*/
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.block.Block;
import org.jetbrains.annotations.Nullable;

/**
 * 注册表 API 兼容层
 * 统一处理不同 Minecraft 版本的 BuiltInRegistries 查找 API 差异
 */
public final class RegistryCompat {

    private RegistryCompat() {}

    /**
     * 根据方块类型名称查找方块
     * 支持带命名空间和不带命名空间两种格式
     *
     * @param blockType 方块类型名称
     * @return 对应的 Block，如果未找到则返回 null
     */
    @Nullable
    public static Block getBlock(String blockType) {
        //? >=1.21.11 {
        Identifier id = IdentifierCompat.forBlockType(blockType);
        //?} else {
        /*ResourceLocation id = IdentifierCompat.forBlockType(blockType);
        *//*?}*/
        if (!BuiltInRegistries.BLOCK.containsKey(id)) {
            return null;
        }
        //? >=1.21.2 {
        return BuiltInRegistries.BLOCK.getValue(id);
        //?} else {
        /*return BuiltInRegistries.BLOCK.get(id);
        *//*?}*/
    }

    /**
     * 根据实体类型名称查找实体类型
     * 支持带命名空间和不带命名空间两种格式
     *
     * @param entityType 实体类型名称
     * @return 对应的 EntityType，如果未找到则返回 null
     */
    @Nullable
    public static EntityType<?> getEntityType(String entityType) {
        //? >=1.21.11 {
        Identifier id = IdentifierCompat.forEntityType(entityType);
        //?} else {
        /*ResourceLocation id = IdentifierCompat.forEntityType(entityType);
        *//*?}*/
        if (!BuiltInRegistries.ENTITY_TYPE.containsKey(id)) {
            return null;
        }
        //? >=1.21.2 {
        return BuiltInRegistries.ENTITY_TYPE.getValue(id);
        //?} else {
        /*return BuiltInRegistries.ENTITY_TYPE.get(id);
        *//*?}*/
    }
}

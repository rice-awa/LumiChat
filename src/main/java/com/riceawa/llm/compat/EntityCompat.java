package com.riceawa.llm.compat;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import org.jetbrains.annotations.Nullable;

/**
 * 实体 API 兼容层
 * 统一处理不同 Minecraft 版本的实体创建 API 差异
 */
public final class EntityCompat {

    private EntityCompat() {}

    /**
     * 使用指定实体类型在世界中创建实体实例
     *
     * @param type 实体类型
     * @param level 目标世界
     * @return 创建的 Entity，如果创建失败则返回 null
     */
    @Nullable
    public static Entity create(EntityType<?> type, ServerLevel level) {
        //? >=1.21.2 {
        return type.create(level, net.minecraft.world.entity.EntitySpawnReason.COMMAND);
        //?} else {
        /*return type.create(level);
        *//*?}*/
    }
}

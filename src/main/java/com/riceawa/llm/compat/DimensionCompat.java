package com.riceawa.llm.compat;

import net.minecraft.world.level.Level;

/**
 * 维度 API 兼容层
 * 统一处理不同 Minecraft 版本的维度标识符获取与显示名称转换
 */
public final class DimensionCompat {

    private DimensionCompat() {}

    /**
     * 获取维度的完整标识符字符串（如 "minecraft:overworld"）
     *
     * @param world 目标世界/维度
     * @return 维度标识符字符串
     */
    public static String getDimensionId(Level world) {
        //? >=1.21.11 {
        return world.dimension().identifier().toString();
        //?} else {
        /*return world.dimension().location().toString();
        *//*?}*/
    }

    /**
     * 获取维度的显示名称（中文）
     *
     * @param world 目标世界/维度
     * @return 维度中文显示名称
     */
    public static String getDisplayName(Level world) {
        String dimensionId = getDimensionId(world);
        switch (dimensionId) {
            case "minecraft:overworld":
                return "主世界";
            case "minecraft:the_nether":
                return "下界";
            case "minecraft:the_end":
                return "末地";
            default:
                return dimensionId;
        }
    }
}

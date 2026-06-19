package com.riceawa.llm.function.impl;

import com.google.gson.JsonObject;
import com.riceawa.llm.function.LLMFunction;
import com.riceawa.llm.util.EntityHelper;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.levelgen.Heightmap;

/**
 * 获取世界信息的函数
 */
public class WorldInfoFunction implements LLMFunction {
    
    @Override
    public String getName() {
        return "get_world_info";
    }
    
    @Override
    public String getDescription() {
        return "获取当前世界的基本信息，包括维度、种子、难度等";
    }
    
    @Override
    public JsonObject getParametersSchema() {
        JsonObject schema = new JsonObject();
        schema.addProperty("type", "object");
        JsonObject properties = new JsonObject();
        
        // 可选参数：是否包含详细信息
        JsonObject includeDetails = new JsonObject();
        includeDetails.addProperty("type", "boolean");
        includeDetails.addProperty("description", "是否包含详细的世界信息");
        includeDetails.addProperty("default", false);
        properties.add("include_details", includeDetails);
        
        schema.add("properties", properties);
        return schema;
    }
    
    @Override
    public FunctionResult execute(Player player, MinecraftServer server, JsonObject arguments) {
        try {
            ServerLevel world = (ServerLevel) EntityHelper.getWorld(player);
            if (world == null) {
                return FunctionResult.error("无法获取世界信息");
            }
            boolean includeDetails = arguments.has("include_details") && 
                                   arguments.get("include_details").getAsBoolean();
            
            StringBuilder info = new StringBuilder();
            
            // 基本世界信息
            info.append("=== 世界信息 ===\n");
            info.append("维度: ").append(getDimensionName(world)).append("\n");
            info.append("难度: ").append(world.getDifficulty().getDisplayName().getString()).append("\n");
            info.append("游戏模式: ").append(server.getDefaultGameType().getLongDisplayName().getString()).append("\n");
            info.append("是否硬核: ").append(server.isHardcore() ? "是" : "否").append("\n");
            
            // 时间信息
            long time = EntityHelper.getDayTime(world);
            int hours = (int) ((time / 1000 + 6) % 24);
            int minutes = (int) ((time % 1000) * 60 / 1000);
            info.append("游戏时间: ").append(String.format("%02d:%02d", hours, minutes)).append("\n");
            info.append("游戏天数: ").append(time / 24000 + 1).append("\n");
            
            // 天气信息
            info.append("天气: ");
            if (world.isThundering()) {
                info.append("雷雨");
            } else if (world.isRaining()) {
                info.append("下雨");
            } else {
                info.append("晴朗");
            }
            info.append("\n");
            
            // 玩家位置信息
            BlockPos pos = player.blockPosition();
            info.append("玩家位置: ").append(pos.getX()).append(", ")
                .append(pos.getY()).append(", ").append(pos.getZ()).append("\n");
            
            // 生物群系信息
            Holder<Biome> biome = world.getBiome(pos);
            String biomeName = biome.unwrapKey().map(key -> key.identifier().toString()).orElse("未知");
            info.append("当前生物群系: ").append(biomeName).append("\n");
            
            if (includeDetails) {
                // 详细信息
                info.append("\n=== 详细信息 ===\n");
                info.append("世界种子: ").append(world.getSeed()).append("\n");
                info.append("世界边界大小: ").append((int)world.getWorldBorder().getSize()).append("\n");
                //? >=1.21.9 {
                BlockPos spawnPos = world.getLevelData().getRespawnData().pos();
                //?} else {
                /*BlockPos spawnPos = world.getSharedSpawnPos();
                *//*?}*/
                info.append("出生点: ").append(spawnPos.getX()).append(", ")
                    .append(spawnPos.getY()).append(", ")
                    .append(spawnPos.getZ()).append("\n");
                info.append("海平面高度: ").append(world.getSeaLevel()).append("\n");
                info.append("最低建筑高度: ").append(world.getMinY()).append("\n");
                //? >=1.21.9 {
                info.append("最高建筑高度: ").append(world.getHeight(Heightmap.Types.WORLD_SURFACE, pos.getX(), pos.getZ())).append("\n");
                //?} else {
                /*info.append("最高建筑高度: ").append(world.getHeight(Heightmap.Types.WORLD_SURFACE, pos.getX(), pos.getZ())).append("\n");
                *//*?}*/
            }
            
            return FunctionResult.success(info.toString());
            
        } catch (Exception e) {
            return FunctionResult.error("获取世界信息失败: " + e.getMessage());
        }
    }
    
    private String getDimensionName(Level world) {
        String dimensionId = world.dimension().identifier().toString();
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
    
    @Override
    public boolean hasPermission(Player player) {
        return true; // 所有玩家都可以查看世界信息
    }
    
    @Override
    public boolean isEnabled() {
        return true;
    }
    
    @Override
    public String getCategory() {
        return "world";
    }
}

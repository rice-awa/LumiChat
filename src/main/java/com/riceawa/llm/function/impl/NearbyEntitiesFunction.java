package com.riceawa.llm.function.impl;

import com.google.gson.JsonObject;
import com.riceawa.llm.function.LLMFunction;
import com.riceawa.llm.util.EntityHelper;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.AgeableMob;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.List;

/**
 * 获取附近实体信息的函数
 */
public class NearbyEntitiesFunction implements LLMFunction {
    
    @Override
    public String getName() {
        return "get_nearby_entities";
    }
    
    @Override
    public String getDescription() {
        return "获取玩家附近的实体信息，包括生物、玩家等";
    }
    
    @Override
    public JsonObject getParametersSchema() {
        JsonObject schema = new JsonObject();
        schema.addProperty("type", "object");
        JsonObject properties = new JsonObject();
        
        // 搜索半径参数
        JsonObject radius = new JsonObject();
        radius.addProperty("type", "number");
        radius.addProperty("description", "搜索半径（方块数）");
        radius.addProperty("default", 16);
        radius.addProperty("minimum", 1);
        radius.addProperty("maximum", 64);
        properties.add("radius", radius);
        
        // 实体类型过滤
        JsonObject entityType = new JsonObject();
        entityType.addProperty("type", "string");
        entityType.addProperty("description", "实体类型过滤：all(全部), players(玩家), mobs(生物), hostile(敌对), passive(友好)");
        entityType.addProperty("default", "all");
        properties.add("entity_type", entityType);
        
        schema.add("properties", properties);
        return schema;
    }
    
    @Override
    public FunctionResult execute(Player player, MinecraftServer server, JsonObject arguments) {
        try {
            // 获取参数
            double radius = arguments.has("radius") ? 
                arguments.get("radius").getAsDouble() : 16.0;
            String entityType = arguments.has("entity_type") ? 
                arguments.get("entity_type").getAsString() : "all";
            
            // 限制搜索半径
            radius = Math.min(Math.max(radius, 1), 64);
            
            var world = EntityHelper.getWorld(player);
            if (world == null) {
                return FunctionResult.error("无法获取世界信息");
            }
            Vec3 playerPos = EntityHelper.getPos(player);
            AABB searchAABB = new AABB(
                playerPos.x - radius, playerPos.y - radius, playerPos.z - radius,
                playerPos.x + radius, playerPos.y + radius, playerPos.z + radius
            );
            
            List<Entity> entities = world.getEntities(player, searchAABB);
            
            StringBuilder result = new StringBuilder();
            result.append("=== 附近实体信息 ===\n");
            result.append("搜索半径: ").append((int)radius).append(" 方块\n");
            result.append("搜索类型: ").append(getEntityTypeDescription(entityType)).append("\n\n");
            
            int count = 0;
            int playerCount = 0, hostileCount = 0, passiveCount = 0, otherCount = 0;
            
            for (Entity entity : entities) {
                // 根据类型过滤
                if (!shouldIncludeEntity(entity, entityType)) {
                    continue;
                }
                
                count++;
                double distance = player.distanceTo(entity);
                String entityName = getEntityDisplayName(entity);
                
                result.append(String.format("%d. %s (距离: %.1f方块)", count, entityName, distance));
                
                // 添加额外信息
                if (entity instanceof LivingEntity livingEntity) {
                    result.append(String.format(" [生命: %.1f/%.1f]", 
                        livingEntity.getHealth(), livingEntity.getMaxHealth()));
                }
                
                if (entity instanceof Player) {
                    playerCount++;
                    result.append(" [玩家]");
                } else if (entity instanceof Monster) {
                    hostileCount++;
                    result.append(" [敌对生物]");
                } else if (entity instanceof AgeableMob) {
                    passiveCount++;
                    result.append(" [友好生物]");
                } else {
                    otherCount++;
                    result.append(" [其他实体]");
                }
                
                result.append("\n");
            }
            
            if (count == 0) {
                result.append("在指定范围内没有找到符合条件的实体。\n");
            } else {
                result.append("\n=== 统计 ===\n");
                result.append("总计: ").append(count).append(" 个实体\n");
                if (playerCount > 0) result.append("玩家: ").append(playerCount).append("\n");
                if (hostileCount > 0) result.append("敌对生物: ").append(hostileCount).append("\n");
                if (passiveCount > 0) result.append("友好生物: ").append(passiveCount).append("\n");
                if (otherCount > 0) result.append("其他实体: ").append(otherCount).append("\n");
            }
            
            return FunctionResult.success(result.toString());
            
        } catch (Exception e) {
            return FunctionResult.error("获取附近实体信息失败: " + e.getMessage());
        }
    }
    
    private boolean shouldIncludeEntity(Entity entity, String entityType) {
        switch (entityType.toLowerCase()) {
            case "players":
                return entity instanceof Player;
            case "mobs":
                return entity instanceof LivingEntity && !(entity instanceof Player);
            case "hostile":
                return entity instanceof Monster;
            case "passive":
                return entity instanceof AgeableMob;
            case "all":
            default:
                return true;
        }
    }
    
    private String getEntityTypeDescription(String entityType) {
        switch (entityType.toLowerCase()) {
            case "players": return "玩家";
            case "mobs": return "生物";
            case "hostile": return "敌对生物";
            case "passive": return "友好生物";
            case "all":
            default: return "全部实体";
        }
    }
    
    private String getEntityDisplayName(Entity entity) {
        if (entity instanceof Player) {
            return entity.getName().getString() + " (玩家)";
        }
        
        String entityName = entity.getName().getString();
        String entityType = entity.getType().toString();
        
        // 如果显示名称为空或者是默认的，使用实体类型
        if (entityName.isEmpty() || entityName.equals(entityType)) {
            return entityType;
        }
        
        return entityName;
    }
    
    @Override
    public boolean hasPermission(Player player) {
        return true; // 所有玩家都可以查看附近实体
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

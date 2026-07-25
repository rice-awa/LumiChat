package com.riceawa.llm.compat;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.biome.Biome;

public final class WorldInfoCompat {

    private WorldInfoCompat() {}

    public static String getBiomeId(Holder<Biome> biome) {
        //? >=1.21.11 {
        return biome.unwrapKey().map(key -> key.identifier().toString()).orElse("未知");
        //?} else {
        /*return biome.unwrapKey().map(key -> key.location().toString()).orElse("未知");
        *//*?}*/
    }

    public static BlockPos getSpawnPosition(ServerLevel world) {
        //? >=1.21.11 {
        return world.getRespawnData().pos();
        //?} else if >=1.21.9 {
        /*return world.getLevelData().getRespawnData().pos();
        *///?} else {
        /*return world.getSharedSpawnPos();
        *//*?}*/
    }

    public static int getMinBuildHeight(ServerLevel world) {
        //? >=1.21.2 {
        return world.getMinY();
        //?} else {
        /*return world.getMinBuildHeight();
        *//*?}*/
    }
}

package com.riceawa.llm.compat;

import net.minecraft.server.level.ServerLevel;

//? >=26.1 {
/*import net.minecraft.core.registries.Registries;
import net.minecraft.world.clock.WorldClocks;
*///?}

public final class WorldTimeCompat {

    private WorldTimeCompat() {}

    public static long getDayTime(ServerLevel world) {
        //? >=26.1 {
        /*var clock = world.registryAccess().lookupOrThrow(Registries.WORLD_CLOCK).getOrThrow(WorldClocks.OVERWORLD);
        return world.clockManager().getTotalTicks(clock);
        *///?} else {
        return world.getDayTime();
        //?}
    }

    public static void setDayTime(ServerLevel world, long time) {
        //? >=26.1 {
        /*var clock = world.registryAccess().lookupOrThrow(Registries.WORLD_CLOCK).getOrThrow(WorldClocks.OVERWORLD);
        world.clockManager().setTotalTicks(clock, time);
        *///?} else {
        world.setDayTime(time);
        //?}
    }
}

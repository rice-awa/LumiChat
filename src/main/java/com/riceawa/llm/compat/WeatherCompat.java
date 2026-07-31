package com.riceawa.llm.compat;

import net.minecraft.server.level.ServerLevel;

//? >=26.1 {
/*import net.minecraft.world.level.saveddata.WeatherData;
*///?}

public final class WeatherCompat {

    private WeatherCompat() {}

    public static void setWeatherParameters(ServerLevel world, int clearDuration, int weatherDuration, boolean raining, boolean thundering) {
        //? >=26.1 {
        /*WeatherData weatherData = world.getWeatherData();
        weatherData.setClearWeatherTime(clearDuration);
        weatherData.setRainTime(weatherDuration);
        weatherData.setThunderTime(weatherDuration);
        weatherData.setRaining(raining);
        weatherData.setThundering(thundering);
        *///?} else {
        world.setWeatherParameters(clearDuration, weatherDuration, raining, thundering);
        //?}
    }
}

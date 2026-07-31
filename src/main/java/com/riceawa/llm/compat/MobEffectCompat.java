package com.riceawa.llm.compat;

import net.minecraft.world.effect.MobEffectInstance;

public final class MobEffectCompat {

    private MobEffectCompat() {}

    public static String getId(MobEffectInstance effect) {
        //? if >=1.20.5 {
        return effect.getEffect().unwrapKey()
            .map(key -> {
                //? if >=1.21.11 {
                return key.identifier().toString();
                //?} else {
                /*return key.location().toString();
                *///?}
            })
            .map(id -> id.startsWith("minecraft:") ? id.substring(10) : id)
            .orElse("unknown");
        //?} else {
        /*String id = effect.getEffect().toString();
        if (id.startsWith("minecraft:")) {
            return id.substring(10);
        }
        return id;
        *///?}
    }

    public static boolean isBeneficial(MobEffectInstance effect) {
        //? if >=1.20.5 {
        return effect.getEffect().value().isBeneficial();
        //?} else {
        /*return effect.getEffect().isBeneficial();*/
        //?}
    }

    public static String getTranslationKey(MobEffectInstance effect) {
        //? if >=1.20.5 {
        return effect.getEffect().value().getDescriptionId();
        //?} else {
        /*return effect.getEffect().getDescriptionId();*/
        //?}
    }
}

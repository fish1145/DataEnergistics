package com.fish_dan_.data_energistics.mixin.core;

import appeng.util.CowMap;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import java.util.Map;

@Mixin(CowMap.class)
public interface CowMapAccessor<K, V> {

    @Accessor("map")
    Map<K, V> dataEnergistics$getMap();

    @Accessor("map")
    void dataEnergistics$setMap(Map<K, V> map);
}

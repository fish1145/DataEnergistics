package com.fish_dan_.data_energistics.mixin.core;

import appeng.api.behaviors.ContainerItemStrategies;
import appeng.api.behaviors.ContainerItemStrategy;
import appeng.api.stacks.AEKeyType;
import appeng.util.CowMap;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(ContainerItemStrategies.class)
public interface ContainerItemStrategiesAccessor {

    @Accessor("strategies")
    static CowMap<AEKeyType, ContainerItemStrategy<?, ?>> dataEnergistics$getStrategies() {
        throw new AssertionError();
    }
}

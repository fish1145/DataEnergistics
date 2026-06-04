package com.fish_dan_.data_energistics.mixin.core;

import appeng.api.behaviors.StackExportStrategy;
import appeng.api.stacks.AEKeyType;
import appeng.parts.automation.StackWorldBehaviors;
import appeng.util.CowMap;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(StackWorldBehaviors.class)
public interface StackWorldBehaviorsAccessor {

    @Accessor("exportStrategies")
    static CowMap<AEKeyType, StackExportStrategy.Factory> dataEnergistics$getExportStrategies() {
        throw new AssertionError();
    }
}

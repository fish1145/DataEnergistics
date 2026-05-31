package com.fish_dan_.data_energistics.mixin.advancedae;

import net.pedroksl.advanced_ae.common.logic.AdvPatternProviderLogic;

import appeng.api.networking.security.IActionSource;
import appeng.api.stacks.GenericStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import java.util.List;

@Mixin(AdvPatternProviderLogic.class)
public interface AdvancedAePatternProviderLogicFieldAccessor {

    @Accessor("actionSource")
    IActionSource dataEnergistics$getActionSource();

    @Accessor("sendList")
    List<GenericStack> dataEnergistics$getSendList();
}

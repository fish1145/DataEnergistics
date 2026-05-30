package com.fish_dan_.data_energistics.mixin;

import appeng.api.networking.security.IActionSource;
import appeng.api.stacks.GenericStack;
import appeng.helpers.patternprovider.PatternProviderLogic;
import appeng.helpers.patternprovider.PatternProviderLogicHost;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import java.util.List;

@Mixin(PatternProviderLogic.class)
public interface PatternProviderLogicFieldAccessor {

    @Accessor("host")
    PatternProviderLogicHost dataEnergistics$getHost();

    @Accessor("actionSource")
    IActionSource dataEnergistics$getActionSource();

    @Accessor("sendList")
    List<GenericStack> dataEnergistics$getSendList();
}

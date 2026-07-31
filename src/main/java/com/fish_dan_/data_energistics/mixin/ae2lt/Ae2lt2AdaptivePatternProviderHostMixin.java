package com.fish_dan_.data_energistics.mixin.ae2lt;

import com.fish_dan_.data_energistics.blockentity.AdaptivePatternProviderBlockEntity;

import org.spongepowered.asm.mixin.Mixin;

/**
 * Triggers the mixin plugin's soft attachment of AE2LT 2.0's public wireless-provider interface.
 */
@Mixin(AdaptivePatternProviderBlockEntity.class)
public abstract class Ae2lt2AdaptivePatternProviderHostMixin {}

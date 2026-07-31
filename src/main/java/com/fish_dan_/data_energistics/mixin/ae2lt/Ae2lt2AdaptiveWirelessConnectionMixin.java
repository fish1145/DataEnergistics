package com.fish_dan_.data_energistics.mixin.ae2lt;

import com.fish_dan_.data_energistics.ae2.AdaptiveWirelessConnection;

import org.spongepowered.asm.mixin.Mixin;

/**
 * Triggers the mixin plugin's soft attachment of Thunderbolt's wireless-connection contract.
 */
@Mixin(AdaptiveWirelessConnection.class)
public abstract class Ae2lt2AdaptiveWirelessConnectionMixin {}

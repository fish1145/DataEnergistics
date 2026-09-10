package com.fish_dan_.data_energistics.item.powered.cannon.ammunition;

import com.fish_dan_.data_energistics.ae2.key.DataFlowKey;
import com.fish_dan_.data_energistics.integration.ModFlags;
import com.fish_dan_.data_energistics.integration.weapon.appflux.FluxAmmunition;

import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.AEKey;

import net.minecraft.world.item.Items;

import org.jspecify.annotations.Nullable;

public enum RailAmmunition {

    BLAZE,
    HEAVY,
    DATA,
    FE;

    public int duration() {
        return this == HEAVY ? 270 : 200;
    }

    public long cost() {
        return this == DATA ? 1500 : this == FE ? 800 : 1;
    }

    public long consumed(int ticks) {
        return (cost() * Math.clamp(ticks, 0, duration()) + duration() - 1) / duration();
    }

    public double energy(int ticks) {
        return 200.0D * Math.clamp(ticks, 0, duration()) / duration();
    }

    public float damage(int cards) {
        AmmunitionRules.checkCards(cards);
        return switch (this) {
            case BLAZE -> cards == 0 ? 8 : cards == 1 ? 10 : 14;
            case HEAVY -> cards == 0 ? 16 : 25;
            case DATA -> cards == 2 ? 6 : 3;
            case FE -> cards == 2 ? 2 : 1;
        };
    }

    /** Half-ticks keep the 1.5-tick FE period exact without rounding the interval. */
    public int intervalHalfTicks(int cards) {
        AmmunitionRules.checkCards(cards);
        return this == FE ? (cards == 0 ? 10 : cards == 1 ? 6 : 3) : 2;
    }

    public int color() {
        return switch (this) {
            case BLAZE -> 0xFF952D;
            case HEAVY -> 0xD7B4FF;
            case DATA -> 0x40E8FA;
            case FE -> 0x8ABDFF;
        };
    }

    public static @Nullable RailAmmunition fromKey(AEKey key) {
        if (key.equals(AEItemKey.of(Items.BLAZE_ROD))) return BLAZE;
        if (key.equals(AEItemKey.of(Items.HEAVY_CORE))) return HEAVY;
        if (key.equals(DataFlowKey.of())) return DATA;
        if (ModFlags.isAppFluxLoaded() && key.equals(FluxAmmunition.key())) return FE;
        return null;
    }
}

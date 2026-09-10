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

    public int cooldownTicks() {
        return this == HEAVY ? 160 : 80;
    }

    public long cost() {
        return this == DATA ? 1500 : this == FE ? 800 : 1;
    }

    public float damage(int cards) {
        AmmunitionRules.checkCards(cards);
        return switch (this) {
            case BLAZE -> cards == 0 ? 8 : cards == 1 ? 10 : 14;
            case HEAVY -> cards == 0 ? 34 : 40;
            case DATA -> cards == 2 ? 20 : 13;
            case FE -> cards == 2 ? 19 : 11;
        };
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
        if (key instanceof AEItemKey itemKey) {
            if (itemKey.getItem() == Items.BLAZE_ROD) return BLAZE;
            if (itemKey.getItem() == Items.HEAVY_CORE) return HEAVY;
            return null;
        }
        if (key.equals(DataFlowKey.of())) return DATA;
        if (ModFlags.isAppFluxLoaded() && key.equals(FluxAmmunition.key())) return FE;
        return null;
    }
}

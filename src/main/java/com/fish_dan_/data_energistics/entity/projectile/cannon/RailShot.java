package com.fish_dan_.data_energistics.entity.projectile.cannon;

import com.fish_dan_.data_energistics.item.powered.cannon.ammunition.AmmunitionRules;
import com.fish_dan_.data_energistics.item.powered.cannon.ammunition.RailAmmunition;

import net.minecraft.nbt.CompoundTag;

/** Full-charge parameters fixed at release, independent of later weapon or disk changes. */
public record RailShot(RailAmmunition ammunition, int cards, float charge) {

    public RailShot {
        AmmunitionRules.checkCards(cards);
        if (!Float.isFinite(charge) || charge <= 0 || charge > 1) throw new IllegalArgumentException("Invalid rail shot charge");
    }

    public void save(CompoundTag tag) {
        tag.putString("Ammo", ammunition.name());
        tag.putInt("Cards", cards);
        tag.putFloat("Charge", charge);
    }

    public static RailShot load(CompoundTag tag) {
        return new RailShot(RailAmmunition.valueOf(tag.getString("Ammo")), tag.getInt("Cards"), tag.getFloat("Charge"));
    }
}

package com.fish_dan_.data_energistics.entity.projectile.cannon;

import com.fish_dan_.data_energistics.item.powered.MatterConvergingCrossbowMode;

import net.minecraft.nbt.CompoundTag;

/** A shot snapshots its firing mode and damage budget so switching/upgrading the weapon cannot alter it in flight. */
public record CannonShot(MatterConvergingCrossbowMode mode, float charge, float referenceSpeed) {

    public static final CannonShot CROSSBOW = new CannonShot(MatterConvergingCrossbowMode.CROSSBOW, 1.0F, 0.0F);

    public CannonShot {
        if (!Float.isFinite(charge) || charge < 0 || charge > 1 || !Float.isFinite(referenceSpeed) || referenceSpeed < 0) {
            throw new IllegalArgumentException("Invalid cannon damage budget");
        }
    }

    public float damageScale() {
        return this.mode == MatterConvergingCrossbowMode.RAIL ? this.charge : 1.0F;
    }

    public float damageSpeed(float currentSpeed) {
        return this.mode == MatterConvergingCrossbowMode.CROSSBOW ? currentSpeed : this.referenceSpeed;
    }

    public void save(CompoundTag parent) {
        if (this.mode == MatterConvergingCrossbowMode.CROSSBOW) return;
        CompoundTag tag = new CompoundTag();
        tag.putInt("Mode", this.mode.id());
        tag.putFloat("Charge", this.charge);
        tag.putFloat("ReferenceSpeed", this.referenceSpeed);
        parent.put("CannonShot", tag);
    }

    public static CannonShot load(CompoundTag parent) {
        if (!parent.contains("CannonShot", 10)) return CROSSBOW;
        CompoundTag tag = parent.getCompound("CannonShot");
        return new CannonShot(MatterConvergingCrossbowMode.fromId(tag.getInt("Mode")), tag.getFloat("Charge"), tag.getFloat("ReferenceSpeed"));
    }
}

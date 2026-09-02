package com.fish_dan_.data_energistics.common.beam;

/** Fixed beam range and idle demand; concentrating cards affect only their own device. */
public enum BeamDeviceKind {

    PART(64, 1, 0.15F),
    DIRECTIONAL(64, 2, 0.28F),
    OMNI(128, 4, 0.08F);

    public static final int UPGRADE_SLOTS = 3;
    private final int baseRange;
    private final int basePower;
    private final float width;

    BeamDeviceKind(int baseRange, int basePower, float width) {
        this.baseRange = baseRange;
        this.basePower = basePower;
        this.width = width;
    }

    public int range(int cards) {
        return this.baseRange << cards;
    }

    public int idlePower(int cards, int connections) {
        return (this.basePower << cards) + (this == OMNI ? connections : 0);
    }

    public float width() {
        return this.width;
    }
}

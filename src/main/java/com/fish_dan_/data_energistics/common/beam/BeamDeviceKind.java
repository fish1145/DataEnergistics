package com.fish_dan_.data_energistics.common.beam;

/** Fixed beam range and idle demand; each concentrating card adds eight blocks to its own device range. */
public enum BeamDeviceKind {

    PART(64, 1, 0.15F),
    DIRECTIONAL(64, 2, 0.28F),
    OMNI(128, 4, 0.08F);

    public static final int UPGRADE_SLOTS = 3;
    public static final int RANGE_PER_CARD = 8;
    private final int baseRange;
    private final int basePower;
    private final float width;

    BeamDeviceKind(int baseRange, int basePower, float width) {
        this.baseRange = baseRange;
        this.basePower = basePower;
        this.width = width;
    }

    public int range(int cards) {
        return this.baseRange + cards * RANGE_PER_CARD;
    }

    public int idlePower(int cards, int connections) {
        return (this.basePower << cards) + (this == OMNI ? connections : 0);
    }

    public float width() {
        return this.width;
    }
}

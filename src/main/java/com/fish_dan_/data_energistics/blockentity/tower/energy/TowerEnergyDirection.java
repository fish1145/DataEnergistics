package com.fish_dan_.data_energistics.blockentity.tower.energy;

/**
 * Describes the transfer directions permitted by one resolved tower energy endpoint.
 */
public enum TowerEnergyDirection {

    SOURCE(true, false),
    SINK(false, true),
    BIDIRECTIONAL(true, true);

    private final boolean extractAllowed;
    private final boolean receiveAllowed;

    TowerEnergyDirection(boolean extractAllowed, boolean receiveAllowed) {
        this.extractAllowed = extractAllowed;
        this.receiveAllowed = receiveAllowed;
    }

    /**
     * Returns whether the endpoint may provide energy.
     *
     * @return true for source-capable endpoints
     */
    public boolean allowsExtract() {
        return this.extractAllowed;
    }

    /**
     * Returns whether the endpoint may accept energy.
     *
     * @return true for receiver-capable endpoints
     */
    public boolean allowsReceive() {
        return this.receiveAllowed;
    }

    /**
     * Resolves a usable direction from capability permissions.
     *
     * @param canExtract whether extraction is allowed
     * @param canReceive whether insertion is allowed
     * @return the usable direction, or null when neither operation is allowed
     */
    public static TowerEnergyDirection fromPermissions(boolean canExtract, boolean canReceive) {
        if (canExtract && canReceive) {
            return BIDIRECTIONAL;
        }
        if (canExtract) {
            return SOURCE;
        }
        if (canReceive) {
            return SINK;
        }
        return null;
    }
}

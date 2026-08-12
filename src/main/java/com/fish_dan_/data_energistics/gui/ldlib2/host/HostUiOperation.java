package com.fish_dan_.data_energistics.gui.ldlib2.host;

/**
 * Membership change that both sides of one hosted LDLib2 menu apply in the same order.
 */
public enum HostUiOperation {

    /**
     * Creates and attaches one fresh provider tree.
     */
    OPEN(0),

    /**
     * Removes and releases one currently attached provider tree.
     */
    CLOSE(1);

    private final int networkId;

    HostUiOperation(int networkId) {
        this.networkId = networkId;
    }

    /**
     * Returns the stable wire id used by the lifecycle payload codec.
     *
     * @return non-negative operation id
     */
    public int networkId() {
        return this.networkId;
    }

    /**
     * Resolves a validated operation id received from the network.
     *
     * @param networkId encoded operation id
     * @return matching operation
     */
    public static HostUiOperation fromNetworkId(int networkId) {
        return switch (networkId) {
            case 0 -> OPEN;
            case 1 -> CLOSE;
            default -> throw new IllegalArgumentException("Unknown host UI operation id: " + networkId);
        };
    }
}

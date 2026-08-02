package com.fish_dan_.data_energistics.common.crafting.trinity.planning.algorithm.cycle.mip.radix.model;

import java.util.Map;

/**
 * Signals that a proof-carrying radix model exceeds the configured safe structural envelope.
 */
public final class TrinityRadixModelLimitException extends RuntimeException {

    private final Map<String, String> metadata;

    public TrinityRadixModelLimitException(Map<String, String> metadata) {
        super(metadata.getOrDefault("reason", "radix_model_limit"));
        this.metadata = Map.copyOf(metadata);
    }

    /**
     * @return immutable structured limit details for the user-facing diagnostic
     */
    public Map<String, String> metadata() {
        return this.metadata;
    }
}

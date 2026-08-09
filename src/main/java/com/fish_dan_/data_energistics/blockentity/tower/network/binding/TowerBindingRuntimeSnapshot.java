package com.fish_dan_.data_energistics.blockentity.tower.network.binding;

import com.fish_dan_.data_energistics.blockentity.tower.network.TowerVirtualDeviceSnapshot;
import com.fish_dan_.data_energistics.blockentity.tower.network.TowerVirtualDeviceState;

import java.util.List;

/**
 * Immutable aggregate status for one persisted binding.
 *
 * @param binding           persisted binding
 * @param state             aggregate state
 * @param failure           aggregate diagnostic reason
 * @param requestedChannels requested channel count
 * @param grantedChannels   granted channel count
 * @param devices           individual resolved devices
 * @param storedFe          frozen FE stored at the binding anchor
 * @param capacityFe        frozen FE capacity at the binding anchor
 * @param canExtractFe      whether the anchor can supply FE
 * @param canReceiveFe      whether the anchor can receive FE
 */
public record TowerBindingRuntimeSnapshot(TowerBinding binding,
                                          TowerVirtualDeviceState state,
                                          String failure,
                                          long requestedChannels,
                                          long grantedChannels,
                                          List<TowerVirtualDeviceSnapshot> devices,
                                          long storedFe,
                                          long capacityFe,
                                          boolean canExtractFe,
                                          boolean canReceiveFe) {

    /**
     * Creates a channel-only snapshot for callers that do not publish FE endpoint state.
     */
    public TowerBindingRuntimeSnapshot(TowerBinding binding,
                                       TowerVirtualDeviceState state,
                                       String failure,
                                       long requestedChannels,
                                       long grantedChannels,
                                       List<TowerVirtualDeviceSnapshot> devices) {
        this(binding, state, failure, requestedChannels, grantedChannels, devices, 0, 0, false, false);
    }

    /** Validates and defensively copies one binding snapshot. */
    public TowerBindingRuntimeSnapshot {
        if (requestedChannels < 0 || grantedChannels < 0 || grantedChannels > requestedChannels) {
            throw new IllegalArgumentException("Tower binding runtime channel counters are invalid");
        }
        if (storedFe < 0 || capacityFe < storedFe) {
            throw new IllegalArgumentException("Tower binding runtime FE snapshot is invalid");
        }
        devices = List.copyOf(devices);
    }
}

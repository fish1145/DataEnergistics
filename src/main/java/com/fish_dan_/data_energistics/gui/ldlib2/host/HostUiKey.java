package com.fish_dan_.data_energistics.gui.ldlib2.host;

import net.minecraft.resources.ResourceLocation;

/**
 * Stable, strongly typed identity for one independently hosted child UI.
 */
public record HostUiKey(ResourceLocation id) {

    /**
     * Maximum textual identity length accepted by the lifecycle network protocol.
     */
    public static final int MAX_NETWORK_LENGTH = 256;

    /**
     * Rejects an identity that could not be registered or addressed by a host.
     */
    public HostUiKey {
        if (id == null) {
            throw new IllegalArgumentException("Host UI id must not be null");
        }
        if (id.toString().length() > MAX_NETWORK_LENGTH) {
            throw new IllegalArgumentException(
                    "Host UI id exceeds the network limit of " + MAX_NETWORK_LENGTH + " characters: " + id);
        }
    }
}

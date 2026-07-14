package com.fish_dan_.data_energistics.gui.ldlib2;

import net.minecraft.resources.ResourceLocation;

/** Stable, strongly typed identity for one independently hosted child UI. */
public record HostUiKey(ResourceLocation id) {

    /** Rejects an identity that could not be registered or addressed by a host. */
    public HostUiKey {
        if (id == null) {
            throw new IllegalArgumentException("Host UI id must not be null");
        }
    }
}

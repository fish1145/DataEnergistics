package com.fish_dan_.data_energistics.api.registry.machine.upload;

import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

import java.util.Objects;

/** Stable live workstation variant used for provider-panel grouping and display. */
public record PatternUploadWorkstationVariant(ResourceLocation id, Component displayName) {

    public PatternUploadWorkstationVariant {
        Objects.requireNonNull(id, "Pattern upload workstation variant ID");
        displayName = Objects.requireNonNull(displayName, "Pattern upload workstation variant name").copy();
    }

    @Override
    public Component displayName() {
        return this.displayName.copy();
    }
}

package com.fish_dan_.data_energistics.menu.common;

import net.minecraft.resources.ResourceLocation;

import org.jetbrains.annotations.Nullable;

public interface PatternEncodingSourceAware {

    void clearPatternSourceState();

    void setPendingPatternSource(@Nullable ResourceLocation workstationId);

    @Nullable
    ResourceLocation getPendingPatternSource();

    void clearPendingPatternSource();

    @Nullable
    ResourceLocation getLastEncodedPatternSource();

    void setLastEncodedPatternSource(@Nullable ResourceLocation workstationId);

    boolean isPatternSourceEnabled();

    void setPatternSourceEnabled(boolean enabled);
}

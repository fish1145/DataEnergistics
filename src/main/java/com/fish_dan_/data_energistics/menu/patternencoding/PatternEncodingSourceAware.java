package com.fish_dan_.data_energistics.menu.patternencoding;

import net.minecraft.resources.ResourceLocation;

import org.jspecify.annotations.Nullable;

public interface PatternEncodingSourceAware {

    void data_energistics$clearPatternSourceState();

    void data_energistics$setPendingPatternSource(@Nullable ResourceLocation workstationId);

    @Nullable
    ResourceLocation data_energistics$getPendingPatternSource();

    void data_energistics$clearPendingPatternSource();

    @Nullable
    ResourceLocation data_energistics$getLastEncodedPatternSource();

    void data_energistics$setLastEncodedPatternSource(@Nullable ResourceLocation workstationId);

    boolean data_energistics$isPatternSourceEnabled();

    void data_energistics$setPatternSourceEnabled(boolean enabled);

    boolean data_energistics$isUploadEnabled();

    void data_energistics$setUploadEnabled(boolean enabled);
}

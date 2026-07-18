package com.fish_dan_.data_energistics.mixin.core;

import net.minecraft.network.chat.Component;

import appeng.parts.AEBasePart;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/**
 * Provides the write side of AE2 part custom names for pattern provider renaming.
 *
 * <p>
 * AE2 exposes custom names for reading but does not expose a setter that can preserve arbitrary components and clear
 * them again.
 */
@Mixin(AEBasePart.class)
public interface AEBasePartNameAccessor {

    /**
     * Updates the custom name stored and persisted by the AE2 part.
     *
     * @param customName new custom name, or {@code null} to clear it
     */
    @Accessor("customName")
    void dataEnergistics$setCustomName(@Nullable Component customName);
}

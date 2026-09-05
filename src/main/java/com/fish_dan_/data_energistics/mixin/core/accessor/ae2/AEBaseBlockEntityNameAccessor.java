package com.fish_dan_.data_energistics.mixin.core.accessor.ae2;

import appeng.blockentity.AEBaseBlockEntity;

import net.minecraft.network.chat.Component;

import org.jspecify.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/**
 * Provides the write side of AE2 block entity custom names for pattern provider renaming.
 *
 * <p>
 * AE2 exposes custom names for reading but does not expose a component-preserving setter that can also clear them.
 */
@Mixin(AEBaseBlockEntity.class)
public interface AEBaseBlockEntityNameAccessor {

    /**
     * Updates the custom name stored and persisted by the AE2 block entity.
     *
     * @param customName new custom name, or {@code null} to clear it
     */
    @Accessor("customName")
    void dataEnergistics$setCustomName(@Nullable Component customName);
}

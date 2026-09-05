package com.fish_dan_.data_energistics.mixin.extendedae;

import com.fish_dan_.data_energistics.ae2.grid.UnlimitedExtractableStorage;

import appeng.api.config.Actionable;
import appeng.api.networking.security.IActionSource;
import appeng.api.stacks.AEKey;
import appeng.api.storage.MEStorage;

import com.glodblock.github.extendedae.common.inventory.InfinityCellInventory;
import org.spongepowered.asm.mixin.Mixin;

/** Exposes ExtendedAE infinity-cell extraction as an explicit non-consuming storage capability. */
@Mixin(value = InfinityCellInventory.class, remap = false)
public abstract class ExtendedAeInfinityCellInventoryMixin implements UnlimitedExtractableStorage {

    @Override
    public boolean supportsUnlimitedExtraction(AEKey key, IActionSource source) {
        return ((MEStorage) (Object) this).extract(key, Long.MAX_VALUE, Actionable.SIMULATE, source) == Long.MAX_VALUE;
    }
}

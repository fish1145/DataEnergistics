package com.fish_dan_.data_energistics.mixin.core.grid.storage;

import com.fish_dan_.data_energistics.ae2.grid.UnlimitedExtractableStorage;

import appeng.api.config.Actionable;
import appeng.api.networking.security.IActionSource;
import appeng.api.stacks.AEKey;
import appeng.api.storage.MEStorage;
import org.spongepowered.asm.mixin.Mixin;

/** Exposes AE2 creative-cell semantics without inferring them from its displayed stack count. */
@Mixin(targets = "appeng.me.cells.CreativeCellInventory", remap = false)
public abstract class CreativeCellInventoryMixin implements UnlimitedExtractableStorage {

    @Override
    public boolean supportsUnlimitedExtraction(AEKey key, IActionSource source) {
        return ((MEStorage) (Object) this).extract(key, Long.MAX_VALUE, Actionable.SIMULATE, source) == Long.MAX_VALUE;
    }
}

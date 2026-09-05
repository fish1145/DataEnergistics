package com.fish_dan_.data_energistics.mixin.core.grid.storage;

import com.fish_dan_.data_energistics.ae2.grid.UnlimitedExtractableStorage;

import appeng.api.config.Actionable;
import appeng.api.networking.security.IActionSource;
import appeng.api.stacks.AEKey;
import appeng.api.storage.MEStorage;
import appeng.me.storage.DelegatingMEInventory;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;

/** Propagates non-consuming extraction through AE2 storage wrappers such as {@code DriveWatcher}. */
@Mixin(DelegatingMEInventory.class)
public abstract class DelegatingMEInventoryUnlimitedStorageMixin implements UnlimitedExtractableStorage {

    @Shadow
    protected abstract MEStorage getDelegate();

    @Override
    public boolean supportsUnlimitedExtraction(AEKey key, IActionSource source) {
        return this.getDelegate() instanceof UnlimitedExtractableStorage unlimited &&
                unlimited.supportsUnlimitedExtraction(key, source) &&
                ((MEStorage) (Object) this).extract(key, Long.MAX_VALUE, Actionable.SIMULATE, source) == Long.MAX_VALUE;
    }
}

package com.fish_dan_.data_energistics.mixin.core;

import com.fish_dan_.data_energistics.ae2.SaturatingKeyCounter;

import appeng.api.stacks.KeyCounter;
import appeng.api.storage.MEStorage;
import appeng.me.storage.NetworkStorage;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/** Prevents mounted AE storage totals from wrapping past {@link Long#MAX_VALUE}. */
@Mixin(NetworkStorage.class)
public abstract class NetworkStorageMixin {

    @Unique
    private final KeyCounter dataEnergistics$storageContribution = new KeyCounter();

    @Redirect(
              method = "getAvailableStacks",
              at = @At(
                       value = "INVOKE",
                       target = "Lappeng/api/storage/MEStorage;getAvailableStacks(Lappeng/api/stacks/KeyCounter;)V"))
    private void dataEnergistics$mergeAvailableStacks(MEStorage storage, KeyCounter total) {
        this.dataEnergistics$storageContribution.clear();
        storage.getAvailableStacks(this.dataEnergistics$storageContribution);
        SaturatingKeyCounter.merge(total, this.dataEnergistics$storageContribution);
    }
}

package com.fish_dan_.data_energistics.mixin;

import appeng.api.networking.IGridNode;
import appeng.menu.me.networktool.NetworkStatus;
import com.fish_dan_.data_energistics.blockentity.DataExtractorBlockEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(NetworkStatus.class)
public abstract class NetworkStatusMixin {
    @Redirect(
            method = "fromGrid",
            at = @At(
                    value = "INVOKE",
                    target = "Lappeng/api/networking/IGridNode;getIdlePowerUsage()D"
            )
    )
    private static double dataEnergistics$getDisplayedIdlePowerUsage(IGridNode machine) {
        Object owner = machine.getOwner();
        if (owner instanceof DataExtractorBlockEntity extractor) {
            return extractor.getTargetCount() > 0 ? DataExtractorBlockEntity.AE_POWER_PER_TICK : 0.0;
        }

        return machine.getIdlePowerUsage();
    }
}

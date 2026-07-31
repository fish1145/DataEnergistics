package com.fish_dan_.data_energistics.mixin.ae2lt;

import com.fish_dan_.data_energistics.Data_Energistics;
import com.fish_dan_.data_energistics.ae2.ChannelHubGrid;

import appeng.api.networking.IGrid;
import appeng.api.networking.IGridNode;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Prevents Thunderbolt's phase-three max-flow result from overwriting Channel Hub shared-pool assignments.
 */
@Pseudo
@Mixin(targets = "com.moakiee.thunderbolt.ae2.channel.BorrowedCapacityCalculator", remap = false)
public abstract class ThunderboltPathingCalculationCompatMixin {

    @Unique
    private static final AtomicBoolean DATA_ENERGISTICS_LOGGED_BYPASS = new AtomicBoolean();

    /**
     * Returns no external flow result only for grids whose nonlinear Hub compression is owned by DataE.
     */
    @Inject(method = "assignChannels", at = @At("HEAD"), cancellable = true, require = 1, remap = false)
    private static void dataEnergistics$bypassMaxFlowForChannelHub(
                                                                   IGrid grid,
                                                                   List<IGridNode> overloadedControllers,
                                                                   CallbackInfoReturnable<Object> callback) {
        if (!ChannelHubGrid.shouldBypassExternalMaxFlow(grid)) {
            return;
        }
        if (DATA_ENERGISTICS_LOGGED_BYPASS.compareAndSet(false, true)) {
            Data_Energistics.LOGGER.info(
                    "Channel Hub shared-pool allocation is authoritative for matching grids; " + "Thunderbolt max-flow remains active for grids without a Hub.");
        }
        callback.setReturnValue(null);
    }
}

package com.fish_dan_.data_energistics.mixin.ae2lt;

import com.fish_dan_.data_energistics.ae2.ChannelHubHost;

import appeng.api.networking.IGrid;
import appeng.api.networking.IGridNode;
import appeng.me.pathfinding.PathingCalculation;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Disables AE2 Lightning Tech's alternative max-flow pass for grids containing a Data Energistics channel hub.
 *
 * <p>
 * AE2 Lightning Tech enables that pass for every grid with a normal controller and then rejects the regular AE2
 * channel allocator before applying its own controller-face flow. A channel hub deliberately uses a controller-wide
 * shared pool, so the two allocation models cannot be combined.
 * </p>
 */
@Mixin(value = PathingCalculation.class, priority = 2000)
public abstract class Ae2ltPathingCalculationCompatMixin {

    /**
     * AE2 Lightning Tech's flag controlling its alternative max-flow calculation.
     */
    @Shadow(remap = false)
    private boolean ae2lt$useMaxFlow;

    /**
     * Turns off AE2 Lightning Tech's calculation after it has initialized its controller state.
     *
     * @param grid     grid being recalculated
     * @param callback constructor injection callback
     */
    @Inject(method = "<init>", at = @At("TAIL"), require = 1)
    private void dataEnergistics$disableMaxFlowForChannelHubs(IGrid grid, CallbackInfo callback) {
        for (IGridNode node : grid.getNodes()) {
            if (node.getOwner() instanceof ChannelHubHost) {
                this.ae2lt$useMaxFlow = false;
                return;
            }
        }
    }
}

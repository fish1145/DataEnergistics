package com.fish_dan_.data_energistics.mixin.core;

import com.fish_dan_.data_energistics.ae2.grid.PathingTopologyRevision;
import com.fish_dan_.data_energistics.ae2.grid.VirtualGridBridge;

import appeng.me.Grid;
import appeng.me.service.PathingService;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Prevents incoming virtual members from inflating controllerless physical channel-power accounting.
 */
@Mixin(PathingService.class)
public abstract class PathingServiceMixin implements PathingTopologyRevision {

    /**
     * Monotonic generation used to invalidate controller-geometry capacity snapshots after every repath request.
     */
    @Unique
    private long dataEnergistics$pathingTopologyRevision;

    /**
     * Advances the cache generation after AE2 records a new pathing reset.
     */
    @Inject(method = "repath", at = @At("TAIL"), require = 1)
    private void dataEnergistics$advancePathingTopologyRevision(CallbackInfo callback) {
        this.dataEnergistics$pathingTopologyRevision = Math.incrementExact(this.dataEnergistics$pathingTopologyRevision);
    }

    @Override
    public long dataEnergistics$pathingTopologyRevision() {
        return this.dataEnergistics$pathingTopologyRevision;
    }

    /**
     * Returns the physical node count while leaving AE2's native channel assignment untouched.
     *
     * @param grid grid being repathed
     * @return physical node count
     */
    @Redirect(
              method = "onServerEndTick",
              at = @At(value = "INVOKE", target = "Lappeng/me/Grid;size()I"),
              require = 1)
    private int dataEnergistics$countPhysicalNodes(Grid grid) {
        return ((VirtualGridBridge) grid).physicalNodeCount();
    }
}

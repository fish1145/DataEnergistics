package com.fish_dan_.data_energistics.mixin.core;

import com.fish_dan_.data_energistics.ae2.VirtualGridBridge;

import appeng.me.Grid;
import appeng.me.service.PathingService;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * Prevents incoming virtual members from inflating controllerless physical channel-power accounting.
 */
@Mixin(PathingService.class)
public abstract class PathingServiceMixin {

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

package com.fish_dan_.data_energistics.mixin.core.grid.network;

import com.fish_dan_.data_energistics.ae2.grid.VirtualGridNode;

import appeng.api.networking.IGrid;
import appeng.api.networking.IGridNode;
import appeng.me.pathfinding.PathingCalculation;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import java.util.ArrayList;
import java.util.List;

/**
 * Keeps virtual members out of AE2's physical controller traversal without altering native allocation rules.
 */
@Mixin(PathingCalculation.class)
public abstract class PathingCalculationMixin {

    /**
     * Filters incoming virtual nodes from controller discovery while preserving physical iteration order.
     *
     * @param grid         grid being pathed
     * @param machineClass requested machine class
     * @return physical nodes only
     */
    @Redirect(
              method = { "<init>", "propagateAssignments" },
              at = @At(
                       value = "INVOKE",
                       target = "Lappeng/api/networking/IGrid;getMachineNodes(Ljava/lang/Class;)Ljava/lang/Iterable;"),
              require = 2)
    private Iterable<IGridNode> dataEnergistics$physicalMachineNodes(IGrid grid, Class<?> machineClass) {
        List<IGridNode> physicalNodes = new ArrayList<>();
        for (IGridNode node : grid.getMachineNodes(machineClass)) {
            if (!(node instanceof VirtualGridNode virtualNode) || virtualNode.virtualPrimaryGrid() == null) {
                physicalNodes.add(node);
            }
        }
        return physicalNodes;
    }
}

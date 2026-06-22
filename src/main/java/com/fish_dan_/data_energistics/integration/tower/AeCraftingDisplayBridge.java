package com.fish_dan_.data_energistics.integration.tower;

import com.fish_dan_.data_energistics.util.ReflectionAccess;

import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;

import appeng.blockentity.crafting.CraftingBlockEntity;
import appeng.blockentity.crafting.MolecularAssemblerBlockEntity;
import appeng.blockentity.crafting.PatternProviderBlockEntity;
import org.jetbrains.annotations.Nullable;

/**
 * Bridge for identifying AE crafting display components used by the Data Distribution Tower UI.
 *
 * <p>
 * Some optional AE-like crafting multiblocks only expose their role through methods that are not part of a stable
 * compile-time API. Reflection stays inside this bridge so tower logic can consume explicit capability-style answers.
 */
public final class AeCraftingDisplayBridge {

    /**
     * Returns whether the block entity should be displayed as an AE crafting component.
     *
     * @param blockEntity block entity being inspected
     * @return true when the target contributes to an AE crafting display group
     */
    public boolean isDisplayComponent(@Nullable BlockEntity blockEntity) {
        if (blockEntity == null || blockEntity instanceof PatternProviderBlockEntity) {
            return false;
        }
        if (blockEntity instanceof CraftingBlockEntity || blockEntity instanceof MolecularAssemblerBlockEntity) {
            return true;
        }
        Block block = blockEntity.getBlockState().getBlock();
        if (block.getClass().getName().contains("CraftingUnitBlock")) {
            return true;
        }
        return isReflectiveDisplayComponent(blockEntity);
    }

    /**
     * Returns whether the block entity bridges an AE crafting cluster.
     *
     * @param blockEntity block entity being inspected
     * @return true for pattern providers that represent crafting cluster bridges
     */
    public boolean isClusterBridge(@Nullable BlockEntity blockEntity) {
        return blockEntity instanceof PatternProviderBlockEntity;
    }

    /**
     * Returns whether the block entity participates in crafting cluster traversal.
     *
     * @param blockEntity block entity being inspected
     * @return true when the target is a display component or bridge
     */
    public boolean isClusterNode(@Nullable BlockEntity blockEntity) {
        return isDisplayComponent(blockEntity) || isClusterBridge(blockEntity);
    }

    /**
     * Returns whether the block entity is the reflective core block of a crafting cluster.
     *
     * @param blockEntity block entity being inspected
     * @return true when an optional component exposes isCoreBlock() == true
     */
    public boolean isReflectiveCoreBlock(@Nullable BlockEntity blockEntity) {
        if (blockEntity == null || !hasZeroArgMethod(blockEntity.getClass(), "isCoreBlock")) {
            return false;
        }

        Object value = ReflectionAccess.invokeNoArg(blockEntity, "isCoreBlock");
        return value instanceof Boolean bool && bool;
    }

    /**
     * Returns a stable display priority for AE crafting cluster representatives.
     *
     * @param blockEntity block entity being inspected
     * @return higher values sort before lower values
     */
    public int displayPriority(@Nullable BlockEntity blockEntity) {
        if (blockEntity instanceof CraftingBlockEntity) {
            return isReflectiveCoreBlock(blockEntity) ? 3 : 2;
        }
        if (blockEntity instanceof MolecularAssemblerBlockEntity) {
            return 1;
        }
        if (isReflectiveDisplayComponent(blockEntity)) {
            return isReflectiveCoreBlock(blockEntity) ? 3 : 2;
        }
        return 0;
    }

    private boolean isReflectiveDisplayComponent(BlockEntity blockEntity) {
        Class<?> type = blockEntity.getClass();
        String className = type.getName();
        return className.contains("Crafting") && hasZeroArgMethod(type, "isCoreBlock") && hasZeroArgMethod(type, "getStorageBytes") && hasZeroArgMethod(type, "getAcceleratorThreads");
    }

    private boolean hasZeroArgMethod(Class<?> type, String methodName) {
        return ReflectionAccess.hasNoArgMethod(type, methodName);
    }
}

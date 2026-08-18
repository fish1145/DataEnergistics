package com.fish_dan_.data_energistics.integration.tower.energy.neoecoae;

import net.minecraft.world.level.block.entity.BlockEntity;

import org.jspecify.annotations.Nullable;

import java.util.Set;

/**
 * Bridge for optional NeoECOAE tower target metadata.
 *
 * <p>
 * Data Distribution Tower needs this bridge to prioritize and group NeoECOAE subsystem blocks without leaking optional
 * class names into core tower logic.
 */
public final class NeoEcoAeTowerBridge {

    private static final String BLOCK_ENTITY_PREFIX = "cn.dancingsnow.neoecoae.blocks.entity.";
    private static final Set<String> PREFERRED_SUBSYSTEM_HOST_CLASSES = Set.of(
            "cn.dancingsnow.neoecoae.blocks.entity.crafting.ECOCraftingSystemBlockEntity",
            "cn.dancingsnow.neoecoae.blocks.entity.storage.ECOStorageSystemBlockEntity",
            "cn.dancingsnow.neoecoae.blocks.entity.computation.ECOComputationSystemBlockEntity");

    /**
     * Returns whether the block entity is a preferred NeoECOAE subsystem host.
     *
     * @param blockEntity block entity being inspected
     * @return true when the class is a preferred subsystem host
     */
    public boolean isPreferredSubsystemHost(@Nullable BlockEntity blockEntity) {
        return blockEntity != null && PREFERRED_SUBSYSTEM_HOST_CLASSES.contains(blockEntity.getClass().getName());
    }

    /**
     * Returns whether the block entity belongs to NeoECOAE subsystem classes.
     *
     * @param blockEntity block entity being inspected
     * @return true when the class name is inside the NeoECOAE block entity package
     */
    public boolean isSubsystemComponent(@Nullable BlockEntity blockEntity) {
        return blockEntity != null && blockEntity.getClass().getName().startsWith(BLOCK_ENTITY_PREFIX);
    }
}

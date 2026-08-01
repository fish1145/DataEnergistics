package com.fish_dan_.data_energistics.blockentity.tower.network;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;

import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Map;

/**
 * Encodes versioned tower bindings and migrates the legacy linked-position list.
 */
public interface TowerBindingPersistence {

    /**
     * Reads current bindings or migrates legacy linked positions in list order.
     *
     * @param root                 tower block-entity tag
     * @param towerDimensionId     dimension used by legacy anchors, nullable only for already-versioned data
     * @param legacyDisabledStates legacy position-level disable state
     * @return immutable bindings ordered by FIFO sequence
     */
    List<TowerBinding> read(CompoundTag root,
                            @Nullable ResourceLocation towerDimensionId,
                            Map<BlockPos, Boolean> legacyDisabledStates);

    /**
     * Writes the complete current binding representation.
     *
     * @param root     tower block-entity tag
     * @param bindings bindings to persist
     */
    void write(CompoundTag root, List<TowerBinding> bindings);
}

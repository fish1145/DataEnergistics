package com.fish_dan_.data_energistics.menu;

import net.minecraft.core.BlockPos;

import org.jetbrains.annotations.Nullable;

/**
 * Exposes the Digital Construct Flower host state required by its status GUI.
 */
public interface DigitalConstructFlowerMenuHost {

    /**
     * Reports whether the host's ME node is currently online.
     */
    boolean isOnline();

    /**
     * Reports whether the declared multiblock structure is currently formed.
     */
    boolean isStructureFormed();

    /**
     * Returns how many blocks were matched in the current valid structure.
     */
    int getMatchedBlockCount();

    /**
     * Returns how many pattern buffer compartments are currently bound to the formed structure.
     */
    int getPatternBufferCount();

    /**
     * Returns the last structure validation error, or an empty string when no error is active.
     */
    String getLastFailureReason();

    /**
     * Returns the world position for the last structure validation error when the matcher reported one.
     */
    @Nullable
    BlockPos getLastFailurePosition();

    /**
     * Returns the live crafting CPU target snapshot visible from this host's ME network.
     */
    DigitalConstructFlowerCraftingStatus getCraftingStatus();

    /**
     * Returns how many AE key types are stored in the host UUID storage.
     */
    int getStoredTypeCount();

    /**
     * Returns the total host UUID storage amount as a decimal string because it may exceed {@code long}.
     */
    String getStoredAmountText();

    /**
     * Returns active virtual CPU partitions contributed by the formed trinity structure.
     */
    int getCpuPartitionCount();

    /**
     * Returns virtual CPU partitions that are currently running a job.
     */
    int getBusyCpuPartitionCount();

    /**
     * Returns aggregate crafting storage bytes exposed by the virtual CPU profile.
     */
    long getCpuStorageBytes();

    /**
     * Returns aggregate co-processors exposed by the virtual CPU profile.
     */
    int getCpuCoProcessors();
}

package com.fish_dan_.data_energistics.menu;

import com.fish_dan_.data_energistics.common.trinity.TrinityDataCoreStorageStatus;

import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.player.Player;

import org.jetbrains.annotations.Nullable;

/**
 * Exposes the Trinity Data Core host state required by its status GUI.
 */
public interface TrinityDataCoreMenuHost {

    /** Sentinel displayed when the formed main storage core structure has no finite capacity limit. */
    String UNLIMITED_STORAGE_CAPACITY = "MAX";

    /**
     * Reports whether the host has an active Trinity access hatch.
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
     * Reports whether the declared crafting CPU child structure is currently formed.
     */
    boolean isCpuStructureFormed();

    /**
     * Returns how many blocks were matched in the current valid crafting CPU child structure.
     */
    int getCpuStructureMatchedBlockCount();

    /**
     * Returns the last crafting CPU child structure validation error, or an empty string when no error is active.
     */
    String getCpuLastFailureReason();

    /**
     * Returns the world position for the last crafting CPU child structure validation error when one is available.
     */
    @Nullable
    BlockPos getCpuLastFailurePosition();

    /**
     * Reports whether the declared crafting child structure is currently formed.
     */
    boolean isCraftingStructureFormed();

    /**
     * Returns how many blocks were matched in the current valid crafting child structure.
     */
    int getCraftingStructureMatchedBlockCount();

    /**
     * Returns how many pattern processing cores are currently matched by the crafting child structure.
     */
    int getCraftingPatternCoreCount();

    /**
     * Returns the total recognizable pattern capacity contributed by the crafting child structure.
     */
    int getCraftingPatternCapacity();

    /**
     * Reports whether the current valid crafting-core aggregate has any state eligible for an atomic refund.
     */
    boolean hasRefundablePatternState();

    /**
     * Atomically returns queued inputs and pending outputs from the current valid crafting-core aggregate.
     *
     * @param player player who receives AE-network, inventory, and final world-drop refund delivery
     * @return true only when every participating queued state was cleared and delivery was invoked
     */
    boolean tryRefundAll(Player player);

    /**
     * Returns the last crafting child structure validation error, or an empty string when no error is active.
     */
    String getCraftingLastFailureReason();

    /**
     * Returns the world position for the last crafting child structure validation error when one is available.
     */
    @Nullable
    BlockPos getCraftingLastFailurePosition();

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
    TrinityDataCoreCraftingStatus getCraftingStatus();

    /**
     * Returns the authoritative storage contents and capacity profile as one immutable state.
     */
    TrinityDataCoreStorageStatus getStorageStatus();

    /**
     * Legacy UI bridge retained until the LDLib2 storage accessor replaces the old menu fields.
     */
    default int getStoredTypeCount() {
        return getStorageStatus().typeCount();
    }

    /**
     * Legacy UI bridge retained until the LDLib2 storage accessor replaces the old menu fields.
     */
    default String getStoredAmountText() {
        return getStorageStatus().totalAmount().toString();
    }

    /**
     * Legacy UI bridge retained until the LDLib2 storage accessor replaces the old menu fields.
     */
    default String getStoredTypeCapacityText() {
        TrinityDataCoreStorageStatus status = getStorageStatus();
        return status.unlimited() ? UNLIMITED_STORAGE_CAPACITY : Integer.toString(status.typeCapacity());
    }

    /**
     * Legacy UI bridge retained until the LDLib2 storage accessor replaces the old menu fields.
     */
    default String getStoredAmountCapacityText() {
        TrinityDataCoreStorageStatus status = getStorageStatus();
        return status.unlimited() ? UNLIMITED_STORAGE_CAPACITY : status.amountCapacity().toString();
    }

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

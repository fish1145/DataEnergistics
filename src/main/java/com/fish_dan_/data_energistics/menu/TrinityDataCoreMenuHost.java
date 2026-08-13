package com.fish_dan_.data_energistics.menu;

import com.fish_dan_.data_energistics.common.crafting.trinity.status.TrinityCpuListStatus;
import com.fish_dan_.data_energistics.common.trinity.host.TrinityDataCoreStorageStatus;
import com.fish_dan_.data_energistics.common.trinity.host.TrinityDataCoreStorageView;
import com.fish_dan_.data_energistics.common.trinity.host.TrinityHostedActionStatus;
import com.fish_dan_.data_energistics.common.trinity.host.TrinityPatternCatalogView;
import com.fish_dan_.data_energistics.common.trinity.host.TrinityPatternSlotAction;
import com.fish_dan_.data_energistics.common.trinity.host.TrinityPatternSlotResult;
import com.fish_dan_.data_energistics.common.trinity.pattern.TrinityPatternMaintenanceSnapshot;

import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

import org.jetbrains.annotations.Nullable;

import java.util.UUID;

/**
 * Exposes the Trinity Data Core host state required by its status GUI.
 */
public interface TrinityDataCoreMenuHost {

    /**
     * Sentinel displayed when the formed main storage core structure has no finite capacity limit.
     */
    String UNLIMITED_STORAGE_CAPACITY = "MAX";

    /**
     * Returns the persistent identity used to bind CPU status requests to this exact host.
     */
    UUID getHostId();

    /**
     * Reports whether the host has an active Trinity information exchange depot.
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
     * Atomically returns every installed pattern from the current valid crafting-core aggregate.
     *
     * @param player player who receives inventory-first and final world-drop pattern delivery
     * @return precise transaction outcome without mixing patterns into retained item delivery
     */
    TrinityHostedActionStatus startPatternRefund(Player player);

    /**
     * Best-effort migrates distinct AE-storage patterns and active network pattern-container slots into Trinity.
     */
    TrinityHostedActionStatus startPatternMigration(Player player);

    /** Returns the non-persistent capacity or active pattern-maintenance progress snapshot. */
    TrinityPatternMaintenanceSnapshot getPatternMaintenanceSnapshot();

    /** Returns whether a migration or installed-pattern refund currently owns the catalog. */
    default boolean isPatternMaintenanceActive() {
        return getPatternMaintenanceSnapshot().active();
    }

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
     * Returns one atomic storage snapshot containing the current capacity and every exact stored AE key.
     */
    TrinityDataCoreStorageView getStorageView(int firstEntry);

    /**
     * Returns one bounded aggregate pattern page with the exact layout and content revisions that identify it.
     */
    TrinityPatternCatalogView getPatternCatalogView(int firstGlobalSlot);

    /**
     * Applies one revision-bound aggregate slot click after resolving its current physical core and slot.
     */
    TrinityPatternSlotResult applyPatternSlotAction(Player player,
                                                    long layoutRevision,
                                                    long catalogRevision,
                                                    int globalSlot,
                                                    ItemStack carried,
                                                    TrinityPatternSlotAction action);

    /**
     * Installs one pattern from the main UI player inventory into the first available aggregate slot.
     *
     * @param pattern one encoded pattern removed from the source inventory slot
     * @return whether the pattern was installed and the source removal may be committed
     */
    boolean tryQuickMovePatternFromPlayer(ItemStack pattern);

    /**
     * Returns the priority used when the Trinity storage is mounted into AE2.
     */
    int getStoragePriority();

    /**
     * Changes the priority used when the Trinity storage is mounted into AE2.
     *
     * @return whether the authoritative value changed
     */
    boolean setStoragePriority(int priority);

    /**
     * Returns the priority published for every pattern in the Trinity aggregate provider.
     */
    int getPatternPriority();

    /**
     * Changes the priority published for every pattern in the Trinity aggregate provider.
     *
     * @return whether the authoritative value changed
     */
    boolean setPatternPriority(int priority);

    /**
     * Returns the exact ordered CPUs currently published by this structure to AE2.
     */
    TrinityCpuListStatus getCpuListStatus();

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

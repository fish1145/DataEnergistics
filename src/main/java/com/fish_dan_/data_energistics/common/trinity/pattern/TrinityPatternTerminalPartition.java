package com.fish_dan_.data_energistics.common.trinity.pattern;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;

import appeng.api.implementations.blockentities.PatternContainerGroup;
import appeng.api.inventories.InternalInventory;
import appeng.api.networking.IGrid;
import appeng.api.networking.IGridNode;
import appeng.helpers.patternprovider.PatternContainer;

import java.util.List;
import java.util.UUID;

/**
 * Exposes one bounded live view of a Trinity pattern core to AE2's pattern access terminal.
 *
 * <p>
 * AE2 limits one pattern-access update to 128 changed entries, so every physical core is represented by one or more
 * independently mounted partitions. Implementations expose a revision-guarded inventory proxy and own a virtual
 * grid-node lifecycle that the lease-holding access hatch can attach or detach explicitly.
 */
public interface TrinityPatternTerminalPartition extends PatternContainer {

    /**
     * Maximum inventory size that one AE2 pattern-access packet can update atomically.
     *
     * <p>
     * This field exists because AE2 19.2.8 caps the packet's non-empty slot map at 128 entries; it is the hard boundary
     * used by {@link #createLayout(TrinityPatternCatalog, PatternContainerGroup)}.
     */
    int MAX_PATTERN_SLOTS = 128;

    /**
     * Stable identity used by an access hatch to reconcile virtual terminal nodes without merging different routes.
     *
     * @param hostId         persistent Trinity host identity
     * @param coreId         persistent physical pattern-core identity
     * @param partitionIndex zero-based partition number inside the physical core
     */
    record PartitionKey(UUID hostId, UUID coreId, int partitionIndex) {

        /** Validates every persistent routing component before the key enters a terminal layout. */
        public PartitionKey {
            if (hostId == null) {
                throw new IllegalArgumentException("A Trinity terminal partition requires a host UUID");
            }
            if (coreId == null) {
                throw new IllegalArgumentException("A Trinity terminal partition requires a core UUID");
            }
            if (partitionIndex < 0) {
                throw new IllegalArgumentException("A Trinity terminal partition index must not be negative");
            }
        }
    }

    /**
     * @return stable host/core/partition identity used for layout reconciliation
     */
    PartitionKey key();

    /**
     * @return catalog topology revision captured when this partition was created
     */
    long layoutRevision();

    /**
     * @return immutable world position used to derive deterministic catalog ordering
     */
    BlockPos corePosition();

    /**
     * @return physical core capacity captured by the validated catalog layout
     */
    int coreCapacity();

    /**
     * @return first physical core slot represented by terminal slot zero
     */
    int firstCoreSlot();

    /**
     * @return number of live slots exposed by this partition, never greater than {@link #MAX_PATTERN_SLOTS}
     */
    int slotCount();

    /**
     * Connects this partition's virtual node directly to the lease-holding access node.
     *
     * <p>
     * Reattaching to the same access node is idempotent and repairs a missing direct connection. Call
     * {@link #detach()} before moving the partition to a different access node.
     *
     * @param level      server level shared by both nodes
     * @param accessNode active access-hatch grid node that owns the network lease
     */
    void attach(ServerLevel level, IGridNode accessNode);

    /** Destroys the ephemeral virtual node and all of its direct grid connections. */
    void detach();

    /**
     * @return whether the virtual node still has its direct connection to the selected access node
     */
    boolean isAttached();

    /**
     * @param grid expected lease grid
     * @return whether the live virtual node is directly attached and currently belongs to that grid
     */
    boolean isAttachedTo(IGrid grid);

    /**
     * Compares immutable layout metadata while deliberately preserving owner object identity as a separate concern.
     *
     * @param other desired partition definition
     * @return true when an existing virtual owner can be reused without leaving AE2 with a stale inventory or sort key
     */
    boolean hasSameLayout(TrinityPatternTerminalPartition other);

    /**
     * Narrows the inherited terminal contract to the guarded live inventory owned by this partition.
     *
     * @return fixed-size proxy that rejects access after its captured catalog layout becomes stale
     */
    @Override
    InternalInventory getTerminalPatternInventory();

    /**
     * Creates the immutable terminal partition layout used by a lease-holding access hatch.
     *
     * @param catalog authoritative live catalog that invalidates stale partition inventory proxies
     * @param group   common terminal group used by every generated partition
     * @return detached partitions in stable core-position and physical-slot order
     */
    static List<TrinityPatternTerminalPartition> createLayout(TrinityPatternCatalog catalog,
                                                              PatternContainerGroup group) {
        return VirtualNodePatternTerminalPartition.createLayout(catalog, group);
    }
}

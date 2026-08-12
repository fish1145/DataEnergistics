package com.fish_dan_.data_energistics.common.trinity.pattern;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;

import appeng.api.implementations.blockentities.PatternContainerGroup;
import appeng.api.inventories.BaseInternalInventory;
import appeng.api.inventories.InternalInventory;
import appeng.api.networking.GridFlags;
import appeng.api.networking.GridHelper;
import appeng.api.networking.IGrid;
import appeng.api.networking.IGridConnection;
import appeng.api.networking.IGridNode;
import appeng.api.networking.IGridNodeListener;
import appeng.api.networking.IManagedGridNode;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/** Default live-inventory and virtual-grid-node implementation of {@link TrinityPatternTerminalPartition}. */
public final class VirtualNodePatternTerminalPartition implements TrinityPatternTerminalPartition {

    /** Supported physical capacities, kept explicit so an invalid structure cannot publish an accidental layout. */
    private static final Set<Integer> SUPPORTED_CORE_CAPACITIES = Set.of(64, 128, 512);

    /** Mask reserving the low 32 bits of a terminal sort key for the stable global partition ordinal. */
    private static final long SORT_HOST_MASK = 0xFFFF_FFFF_0000_0000L;

    /**
     * Ephemeral nodes deliberately have no NBT of their own; the information exchange depot reconstructs them from the
     * catalog.
     */
    private static final IGridNodeListener<VirtualNodePatternTerminalPartition> NODE_LISTENER = (owner, node) -> {
        // The physical core and host persist all authoritative state.
    };

    /** Stable persistent identity used by a lease hatch to retain this owner while its layout remains unchanged. */
    private final PartitionKey key;

    /** Authoritative catalog consulted before every inventory access. */
    private final TrinityPatternCatalog catalog;

    /** Topology generation that authorized this partition and its global indexes. */
    private final long layoutRevision;

    /** Exact core object, position, and block capacity captured by the validated layout. */
    private final TrinityPatternCatalog.CoreMount coreMount;

    /** First absolute physical slot exposed by terminal slot zero. */
    private final int firstCoreSlot;

    /** First global catalog index represented by terminal slot zero. */
    private final int firstGlobalIndex;

    /** Fixed inventory size retained even after the backing layout becomes stale. */
    private final int slotCount;

    /** Revision-guarded inventory proxy that never exposes the core's raw sub-inventory. */
    private final InternalInventory terminalInventory;

    /** Shared immutable group value that combines every partition under one terminal heading. */
    private final PatternContainerGroup terminalGroup;

    /** Stable ordering value captured by AE2 when it first tracks this owner. */
    private final long terminalSortOrder;

    /** Re-creatable out-of-world node that makes this owner discoverable by the AE2 terminal. */
    @Nullable
    private IManagedGridNode managedNode;

    /** Access node selected by the current lease; a different target requires an explicit detach. */
    @Nullable
    private IGridNode accessNode;

    private VirtualNodePatternTerminalPartition(TrinityPatternCatalog catalog,
                                                long layoutRevision,
                                                PartitionKey key,
                                                TrinityPatternCatalog.CoreMount coreMount,
                                                int firstCoreSlot,
                                                int firstGlobalIndex,
                                                int slotCount,
                                                PatternContainerGroup terminalGroup,
                                                long terminalSortOrder) {
        this.catalog = catalog;
        this.layoutRevision = layoutRevision;
        this.key = key;
        this.coreMount = coreMount;
        this.firstCoreSlot = firstCoreSlot;
        this.firstGlobalIndex = firstGlobalIndex;
        this.slotCount = slotCount;
        this.terminalInventory = new GuardedPatternInventory();
        this.terminalGroup = terminalGroup;
        this.terminalSortOrder = terminalSortOrder;
    }

    /**
     * Builds an immutable coordinate-sorted terminal layout for validated physical core mounts.
     *
     * <p>
     * Every returned owner is initially detached. The caller should reconcile them by {@link PartitionKey}, reuse an
     * existing owner only when {@link #hasSameLayout(TrinityPatternTerminalPartition)} succeeds, and then attach only
     * the owners published by the current network lease.
     *
     * @param catalog authoritative catalog supplying one active immutable layout
     * @param group   common terminal group used by every generated partition
     * @return immutable partitions in core-position and physical-slot order
     */
    static List<TrinityPatternTerminalPartition> createLayout(TrinityPatternCatalog catalog,
                                                              PatternContainerGroup group) {
        TrinityPatternCatalog.LayoutSnapshot layout = catalog.layoutSnapshot();
        if (!layout.active()) {
            return List.of();
        }
        PatternContainerGroup sharedGroup = copyGroup(group);
        ArrayList<TrinityPatternTerminalPartition> partitions = new ArrayList<>();
        int globalPartitionIndex = 0;
        for (TrinityPatternCatalog.CoreRange range : layout.ranges()) {
            TrinityPatternCatalog.CoreMount mount = range.mount();
            validateRange(range);
            int partitionCount = mount.blockCapacity() / MAX_PATTERN_SLOTS;
            if (mount.blockCapacity() % MAX_PATTERN_SLOTS != 0) {
                partitionCount++;
            }
            for (int partitionIndex = 0; partitionIndex < partitionCount; partitionIndex++) {
                int firstSlot = partitionIndex * MAX_PATTERN_SLOTS;
                int lastSlotExclusive = Math.min(firstSlot + MAX_PATTERN_SLOTS, mount.blockCapacity());
                partitions.add(new VirtualNodePatternTerminalPartition(
                        catalog,
                        layout.revision(),
                        new PartitionKey(catalog.hostId(), range.coreId(), partitionIndex),
                        mount,
                        firstSlot,
                        Math.addExact(range.firstGlobalIndex(), firstSlot),
                        lastSlotExclusive - firstSlot,
                        sharedGroup,
                        createSortOrder(catalog.hostId(), globalPartitionIndex)));
                globalPartitionIndex = Math.incrementExact(globalPartitionIndex);
            }
        }
        return List.copyOf(partitions);
    }

    @Override
    public PartitionKey key() {
        return this.key;
    }

    @Override
    public long layoutRevision() {
        return this.layoutRevision;
    }

    @Override
    public BlockPos corePosition() {
        return this.coreMount.position();
    }

    @Override
    public int coreCapacity() {
        return this.coreMount.blockCapacity();
    }

    @Override
    public int firstCoreSlot() {
        return this.firstCoreSlot;
    }

    @Override
    public int slotCount() {
        return this.slotCount;
    }

    @Override
    public boolean hasSameLayout(TrinityPatternTerminalPartition other) {
        return other instanceof VirtualNodePatternTerminalPartition implementation &&
                this.catalog == implementation.catalog &&
                this.key.equals(implementation.key) &&
                this.layoutRevision == implementation.layoutRevision &&
                this.coreMount.core() == implementation.coreMount.core() &&
                this.coreMount.position().equals(implementation.coreMount.position()) &&
                this.coreMount.blockCapacity() == implementation.coreMount.blockCapacity() &&
                this.firstCoreSlot == implementation.firstCoreSlot &&
                this.firstGlobalIndex == implementation.firstGlobalIndex &&
                this.slotCount == implementation.slotCount &&
                this.terminalSortOrder == implementation.terminalSortOrder &&
                this.terminalGroup.equals(implementation.terminalGroup);
    }

    @Override
    public void attach(ServerLevel level, IGridNode accessNode) {
        if (!isLayoutCurrent()) {
            throw new IllegalStateException("Cannot attach stale Trinity terminal partition " + this.key);
        }
        if (accessNode.getLevel() != level) {
            throw new IllegalArgumentException("A Trinity terminal partition and its access node must share a level");
        }
        IGrid targetGrid = accessNode.getGrid();
        if (this.accessNode != null && this.accessNode != accessNode) {
            throw new IllegalStateException("Detach Trinity terminal partition " + this.key +
                    " before moving it to another access node");
        }

        if (this.managedNode != null) {
            IGridNode partitionNode = this.managedNode.getNode();
            if (partitionNode == null) {
                throw new IllegalStateException("Attached Trinity terminal partition " + this.key + " has no node");
            }
            if (!hasDirectConnection(partitionNode, accessNode)) {
                GridHelper.createConnection(accessNode, partitionNode);
            }
            if (partitionNode.getGrid() != targetGrid) {
                throw new IllegalStateException("Trinity terminal partition " + this.key +
                        " did not join its access grid");
            }
            this.accessNode = accessNode;
            return;
        }

        IManagedGridNode nextNode = GridHelper.createManagedNode(this, NODE_LISTENER)
                .setInWorldNode(false)
                .setFlags(GridFlags.CANNOT_CARRY)
                .setIdlePowerUsage(0.0D);
        int owningPlayerId = accessNode.getOwningPlayerId();
        if (owningPlayerId >= 0) {
            nextNode.setOwningPlayerId(owningPlayerId);
        }
        nextNode.create(level, null);
        IGridNode partitionNode = nextNode.getNode();
        if (partitionNode == null) {
            throw new IllegalStateException("Failed to create a virtual node for Trinity terminal partition " + this.key);
        }
        try {
            GridHelper.createConnection(accessNode, partitionNode);
            if (partitionNode.getGrid() != targetGrid) {
                throw new IllegalStateException("Trinity terminal partition " + this.key +
                        " did not join its access grid");
            }
        } catch (RuntimeException exception) {
            nextNode.destroy();
            throw exception;
        }
        this.managedNode = nextNode;
        this.accessNode = accessNode;
    }

    @Override
    public void detach() {
        IManagedGridNode previousNode = this.managedNode;
        this.managedNode = null;
        this.accessNode = null;
        if (previousNode != null) {
            previousNode.destroy();
        }
    }

    @Override
    public boolean isAttached() {
        IGridNode partitionNode = this.managedNode == null ? null : this.managedNode.getNode();
        return isLayoutCurrent() && partitionNode != null && this.accessNode != null &&
                hasDirectConnection(partitionNode, this.accessNode);
    }

    @Override
    public boolean isAttachedTo(IGrid grid) {
        IGridNode partitionNode = this.managedNode == null ? null : this.managedNode.getNode();
        return isLayoutCurrent() && partitionNode != null && this.accessNode != null &&
                hasDirectConnection(partitionNode, this.accessNode) && partitionNode.getGrid() == grid;
    }

    @Nullable
    @Override
    public IGrid getGrid() {
        return !isLayoutCurrent() || this.managedNode == null ? null : this.managedNode.getGrid();
    }

    @Override
    public boolean isVisibleInTerminal() {
        return isAttached();
    }

    @Override
    public InternalInventory getTerminalPatternInventory() {
        return this.terminalInventory;
    }

    @Override
    public long getTerminalSortOrder() {
        return this.terminalSortOrder;
    }

    @Override
    public PatternContainerGroup getTerminalGroup() {
        return this.terminalGroup;
    }

    /**
     * Resolves one terminal-local slot through the current catalog revision and verifies all captured mount metadata.
     *
     * @param localSlot zero-based terminal inventory slot
     * @return current exact global slot, or {@code null} after any topology invalidation or replacement
     */
    @Nullable
    private TrinityPatternCatalog.GlobalSlot resolveLocalSlot(int localSlot) {
        if (localSlot < 0 || localSlot >= this.slotCount) {
            throw new IllegalArgumentException("Trinity terminal partition slot out of range: " + localSlot);
        }
        int coreSlot = Math.addExact(this.firstCoreSlot, localSlot);
        TrinityPatternCatalog.GlobalSlot resolved = this.catalog.resolveCoreSlot(
                this.layoutRevision,
                this.coreMount,
                coreSlot);
        if (resolved == null || !resolved.range().coreId().equals(this.key.coreId()) ||
                resolved.core() != this.coreMount.core() ||
                resolved.globalIndex() != this.firstGlobalIndex + localSlot ||
                resolved.coreSlot() != coreSlot) {
            return null;
        }
        return resolved;
    }

    /**
     * @return whether the partition's first slot still resolves through its captured authoritative layout
     */
    private boolean isLayoutCurrent() {
        return resolveLocalSlot(0) != null;
    }

    private static void validateRange(TrinityPatternCatalog.CoreRange range) {
        TrinityPatternCatalog.CoreMount mount = range.mount();
        if (!SUPPORTED_CORE_CAPACITIES.contains(mount.blockCapacity())) {
            throw new IllegalArgumentException("Unsupported Trinity terminal core capacity " + mount.blockCapacity() +
                    " at " + mount.position());
        }
    }

    private static PatternContainerGroup copyGroup(PatternContainerGroup group) {
        return new PatternContainerGroup(group.icon(), group.name(), List.copyOf(group.tooltip()));
    }

    private static long createSortOrder(UUID hostId, int globalPartitionIndex) {
        long mixedHost = hostId.getMostSignificantBits() ^ Long.rotateLeft(hostId.getLeastSignificantBits(), 23);
        return mixedHost & SORT_HOST_MASK | Integer.toUnsignedLong(globalPartitionIndex);
    }

    private static boolean hasDirectConnection(IGridNode partitionNode, IGridNode accessNode) {
        for (IGridConnection connection : partitionNode.getConnections()) {
            if (connection.getOtherSide(partitionNode) == accessNode) {
                return true;
            }
        }
        return false;
    }

    /**
     * Fixed-size terminal inventory that rejects every operation once its catalog revision becomes stale.
     */
    private final class GuardedPatternInventory extends BaseInternalInventory {

        /** Returns the immutable number of terminal slots represented by this partition. */
        @Override
        public int size() {
            return VirtualNodePatternTerminalPartition.this.slotCount;
        }

        /** Returns the live core limit only while this local slot still resolves exactly. */
        @Override
        public int getSlotLimit(int slot) {
            TrinityPatternCatalog.GlobalSlot resolved = resolveLocalSlot(slot);
            return resolved == null ? 0 : resolved.core().patternInventory().getSlotLimit(resolved.coreSlot());
        }

        /** Reads a defensive pattern copy or empty after topology invalidation. */
        @Override
        public ItemStack getStackInSlot(int slotIndex) {
            TrinityPatternCatalog.GlobalSlot resolved = resolveLocalSlot(slotIndex);
            return resolved == null ? ItemStack.EMPTY : resolved.core().pattern(resolved.coreSlot());
        }

        /** Writes directly only when the captured topology still resolves to the same physical core slot. */
        @Override
        public void setItemDirect(int slotIndex, ItemStack stack) {
            TrinityPatternCatalog.GlobalSlot resolved = resolveLocalSlot(slotIndex);
            if (resolved != null) {
                resolved.core().trySetPattern(resolved.coreSlot(), stack);
            }
        }

        /** Delegates supported-pattern validation only to the currently resolved physical core. */
        @Override
        public boolean isItemValid(int slot, ItemStack stack) {
            TrinityPatternCatalog.GlobalSlot resolved = resolveLocalSlot(slot);
            return resolved != null && resolved.core().patternInventory().isItemValid(resolved.coreSlot(), stack);
        }

        /** Returns the offered stack unchanged when stale, otherwise delegates one atomic physical insertion. */
        @Override
        public ItemStack insertItem(int slot, ItemStack stack, boolean simulate) {
            TrinityPatternCatalog.GlobalSlot resolved = resolveLocalSlot(slot);
            return resolved == null ? stack : resolved.core().patternInventory()
                    .insertItem(resolved.coreSlot(), stack, simulate);
        }

        /** Returns empty when stale, otherwise delegates one atomic physical extraction. */
        @Override
        public ItemStack extractItem(int slot, int amount, boolean simulate) {
            TrinityPatternCatalog.GlobalSlot resolved = resolveLocalSlot(slot);
            return resolved == null ? ItemStack.EMPTY : resolved.core().patternInventory()
                    .extractItem(resolved.coreSlot(), amount, simulate);
        }

        /** Forwards AE2 change notification only while the physical slot identity remains current. */
        @Override
        public void sendChangeNotification(int slot) {
            TrinityPatternCatalog.GlobalSlot resolved = resolveLocalSlot(slot);
            if (resolved != null) {
                resolved.core().patternInventory().sendChangeNotification(resolved.coreSlot());
            }
        }
    }
}

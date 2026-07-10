package com.fish_dan_.data_energistics.common.trinity;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;

import appeng.api.implementations.blockentities.PatternContainerGroup;
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
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/** Default live-inventory and virtual-grid-node implementation of {@link TrinityPatternTerminalPartition}. */
public final class TrinityPatternTerminalPartitionImpl implements TrinityPatternTerminalPartition {

    /** Supported physical capacities, kept explicit so an invalid structure cannot publish an accidental layout. */
    private static final Set<Integer> SUPPORTED_CORE_CAPACITIES = Set.of(64, 128, 512);

    /** Mask reserving the low 32 bits of a terminal sort key for the stable global partition ordinal. */
    private static final long SORT_HOST_MASK = 0xFFFF_FFFF_0000_0000L;

    /**
     * Ephemeral nodes deliberately have no NBT of their own; the access hatch reconstructs them from the catalog.
     */
    private static final IGridNodeListener<TrinityPatternTerminalPartitionImpl> NODE_LISTENER = (owner, node) -> {
        // The physical core and host persist all authoritative state.
    };

    /** Stable persistent identity used by a lease hatch to retain this owner while its layout remains unchanged. */
    private final PartitionKey key;

    /** Interface-only reference to the physical core that owns patterns and queued work. */
    private final TrinityPatternCore core;

    /** Immutable mount position used to detect catalog reordering or a moved physical core. */
    private final BlockPos corePosition;

    /** First absolute physical slot exposed by terminal slot zero. */
    private final int firstCoreSlot;

    /** Live bounded sub-inventory; writes immediately reach the physical core. */
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

    private TrinityPatternTerminalPartitionImpl(PartitionKey key,
                                                TrinityPatternCore core,
                                                BlockPos corePosition,
                                                int firstCoreSlot,
                                                int lastCoreSlotExclusive,
                                                PatternContainerGroup terminalGroup,
                                                long terminalSortOrder) {
        this.key = key;
        this.core = core;
        this.corePosition = corePosition.immutable();
        this.firstCoreSlot = firstCoreSlot;
        this.terminalInventory = core.patternInventory().getSubInventory(firstCoreSlot, lastCoreSlotExclusive);
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
     * @param hostId stable host UUID shared by every partition key
     * @param mounts physical core mounts found in the crafting child structure
     * @param group  common terminal group used by every generated partition
     * @return immutable partitions in core-position and physical-slot order
     */
    static List<TrinityPatternTerminalPartition> createLayout(UUID hostId,
                                                              List<TrinityPatternCatalog.CoreMount> mounts,
                                                              PatternContainerGroup group) {
        if (hostId == null) {
            throw new IllegalArgumentException("A Trinity terminal layout requires a host UUID");
        }
        if (mounts == null) {
            throw new IllegalArgumentException("A Trinity terminal layout requires mounted cores");
        }
        PatternContainerGroup sharedGroup = copyGroup(group);

        ArrayList<TrinityPatternCatalog.CoreMount> sortedMounts = new ArrayList<>(mounts.size());
        for (TrinityPatternCatalog.CoreMount mount : mounts) {
            if (mount == null) {
                throw new IllegalArgumentException("A Trinity terminal layout contains a null core mount");
            }
            sortedMounts.add(mount);
        }
        sortedMounts.sort((left, right) -> left.position().compareTo(right.position()));

        Set<BlockPos> positions = new HashSet<>();
        Set<UUID> coreIds = new HashSet<>();
        ArrayList<TrinityPatternTerminalPartition> partitions = new ArrayList<>();
        int globalPartitionIndex = 0;
        for (TrinityPatternCatalog.CoreMount mount : sortedMounts) {
            validateMount(mount, positions, coreIds);
            TrinityPatternCore core = mount.core();
            int partitionCount = core.patternCapacity() / MAX_PATTERN_SLOTS;
            if (core.patternCapacity() % MAX_PATTERN_SLOTS != 0) {
                partitionCount++;
            }
            for (int partitionIndex = 0; partitionIndex < partitionCount; partitionIndex++) {
                int firstSlot = partitionIndex * MAX_PATTERN_SLOTS;
                int lastSlotExclusive = Math.min(firstSlot + MAX_PATTERN_SLOTS, core.patternCapacity());
                partitions.add(new TrinityPatternTerminalPartitionImpl(
                        new PartitionKey(hostId, core.coreId(), partitionIndex),
                        core,
                        mount.position(),
                        firstSlot,
                        lastSlotExclusive,
                        sharedGroup,
                        createSortOrder(hostId, globalPartitionIndex)));
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
    public TrinityPatternCore core() {
        return this.core;
    }

    @Override
    public BlockPos corePosition() {
        return this.corePosition;
    }

    @Override
    public int firstCoreSlot() {
        return this.firstCoreSlot;
    }

    @Override
    public int slotCount() {
        return this.terminalInventory.size();
    }

    @Override
    public void attach(ServerLevel level, IGridNode accessNode) {
        if (level == null) {
            throw new IllegalArgumentException("A Trinity terminal partition requires a server level");
        }
        if (accessNode == null) {
            throw new IllegalArgumentException("A Trinity terminal partition requires an access grid node");
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
        return partitionNode != null && this.accessNode != null && hasDirectConnection(partitionNode, this.accessNode);
    }

    @Override
    public boolean isAttachedTo(IGrid grid) {
        if (grid == null) {
            throw new IllegalArgumentException("A Trinity terminal partition grid is required");
        }
        IGridNode partitionNode = this.managedNode == null ? null : this.managedNode.getNode();
        return partitionNode != null && isAttached() && partitionNode.getGrid() == grid;
    }

    @Nullable
    @Override
    public IGrid getGrid() {
        return this.managedNode == null ? null : this.managedNode.getGrid();
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

    private static void validateMount(TrinityPatternCatalog.CoreMount mount,
                                      Set<BlockPos> positions,
                                      Set<UUID> coreIds) {
        TrinityPatternCore core = mount.core();
        if (!positions.add(mount.position())) {
            throw new IllegalArgumentException("Duplicate Trinity terminal core position " + mount.position());
        }
        if (mount.blockCapacity() != core.patternCapacity()) {
            throw new IllegalArgumentException("Trinity terminal core capacity mismatch at " + mount.position() +
                    ": block declares " + mount.blockCapacity() + " slots but core owns " + core.patternCapacity());
        }
        if (!SUPPORTED_CORE_CAPACITIES.contains(core.patternCapacity())) {
            throw new IllegalArgumentException("Unsupported Trinity terminal core capacity " + core.patternCapacity() +
                    " at " + mount.position());
        }
        UUID coreId = core.coreId();
        if (coreId == null) {
            throw new IllegalArgumentException("Trinity terminal core at " + mount.position() + " has no UUID");
        }
        if (!coreIds.add(coreId)) {
            throw new IllegalArgumentException("Duplicate Trinity terminal core UUID " + coreId);
        }
    }

    private static PatternContainerGroup copyGroup(PatternContainerGroup group) {
        if (group == null || group.name() == null || group.tooltip() == null) {
            throw new IllegalArgumentException("A Trinity terminal layout requires a complete terminal group");
        }
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
}

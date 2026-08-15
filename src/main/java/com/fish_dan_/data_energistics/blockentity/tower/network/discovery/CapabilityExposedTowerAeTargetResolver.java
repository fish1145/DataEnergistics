package com.fish_dan_.data_energistics.blockentity.tower.network.discovery;

import com.fish_dan_.data_energistics.ae2.grid.VirtualGridBridge;
import com.fish_dan_.data_energistics.blockentity.tower.DataDistributionTowerBlockEntity;
import com.fish_dan_.data_energistics.blockentity.tower.network.domain.TowerDeviceKey;
import com.fish_dan_.data_energistics.blockentity.tower.network.domain.TowerNetworkDomain;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;

import appeng.api.networking.GridFlags;
import appeng.api.networking.IGrid;
import appeng.api.networking.IGridNode;
import appeng.api.parts.IPart;
import appeng.blockentity.networking.CableBusBlockEntity;
import appeng.blockentity.networking.ControllerBlockEntity;
import appeng.me.InWorldGridNode;
import appeng.parts.AEBasePart;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Capability-gated AE target resolver. Multipart fallback nodes come from a typed Mixin bridge on the capability host;
 * discovery never casts to a multipart host and never creates a grid connection.
 */
public final class CapabilityExposedTowerAeTargetResolver {

    /**
     * Starts one reconciliation-scoped round so repeated anchors can share immutable target-grid device snapshots.
     *
     * @return isolated resolution round
     */
    public ResolutionRound beginResolutionRound() {
        return new ResolutionRoundImpl();
    }

    /**
     * Resolves anchors against one consistent set of target-grid raw-device snapshots.
     */
    public interface ResolutionRound {

        /**
         * Resolves and locally validates one loaded anchor without creating connections or loading chunks.
         *
         * @param level       anchor level
         * @param anchor      connector/range anchor
         * @param primaryGrid requesting tower grid
         * @param mode        point or scope validation
         * @return immutable partial-success result; empty when the anchor chunk is unloaded
         */
        TowerTargetResolution resolve(Level level,
                                      BlockPos anchor,
                                      IGrid primaryGrid,
                                      TowerTargetDiscoveryMode mode);
    }

    private static TowerTargetResolution resolve(Level level,
                                                 BlockPos anchor,
                                                 IGrid primaryGrid,
                                                 TowerTargetDiscoveryMode mode,
                                                 Map<IGrid, List<RawDevice>> rawDevicesByGrid) {
        if (!level.isLoaded(anchor)) {
            return new TowerTargetResolution(List.of(), List.of());
        }

        List<IGridNode> exposedNodes = DataDistributionTowerBlockEntity.getConnectableNodes(level, anchor);
        Set<IGrid> seenGrids = Collections.newSetFromMap(new IdentityHashMap<>());
        Map<RawDeviceIdentity, Integer> occurrences = new HashMap<>();
        ArrayList<TowerResolvedGrid> resolvedGrids = new ArrayList<>();
        for (IGridNode exposedNode : exposedNodes) {
            IGrid targetGrid = exposedNode.getGrid();
            if (targetGrid == null) {
                continue;
            }
            if (!seenGrids.add(targetGrid)) {
                continue;
            }
            List<TowerResolvedDevice> devices = resolveDevices(targetGrid, occurrences, rawDevicesByGrid);
            resolvedGrids.add(new TowerResolvedGrid(
                    targetGrid,
                    devices,
                    validateGrid(targetGrid, primaryGrid, mode, devices)));
        }
        return new TowerTargetResolution(exposedNodes, resolvedGrids);
    }

    private static List<TowerResolvedDevice> resolveDevices(
                                                            IGrid grid,
                                                            Map<RawDeviceIdentity, Integer> occurrences,
                                                            Map<IGrid, List<RawDevice>> rawDevicesByGrid) {
        List<RawDevice> rawDevices = rawDevicesByGrid.computeIfAbsent(
                grid,
                CapabilityExposedTowerAeTargetResolver::snapshotRawDevices);
        ArrayList<TowerResolvedDevice> devices = new ArrayList<>(rawDevices.size());
        for (RawDevice rawDevice : rawDevices) {
            RawDeviceIdentity identity = new RawDeviceIdentity(
                    rawDevice.dimensionId(), rawDevice.position(), rawDevice.side(), rawDevice.nodeType());
            int occurrence = occurrences.getOrDefault(identity, 0);
            occurrences.put(identity, occurrence + 1);
            devices.add(new TowerResolvedDevice(
                    rawDevice.node(),
                    new TowerDeviceKey(
                            rawDevice.dimensionId(),
                            rawDevice.position(),
                            rawDevice.side(),
                            rawDevice.nodeType(),
                            occurrence),
                    rawDevice.registrationOrder(),
                    rawDevice.node().hasFlag(GridFlags.REQUIRE_CHANNEL)));
        }
        return List.copyOf(devices);
    }

    private static List<RawDevice> snapshotRawDevices(IGrid grid) {
        TowerNetworkDomain domain = grid.getService(TowerNetworkDomain.class);
        ArrayList<RawDevice> rawDevices = new ArrayList<>();
        for (IGridNode node : domain.localNodes()) {
            rawDevices.add(rawDevice(node, domain.registrationOrder(node)));
        }
        rawDevices.sort(Comparator
                .comparing(RawDevice::dimensionId, Comparator.comparing(ResourceLocation::toString))
                .thenComparing(RawDevice::position, Comparator.nullsLast(CapabilityExposedTowerAeTargetResolver::comparePosition))
                .thenComparingInt(RawDevice::side)
                .thenComparing(RawDevice::nodeType)
                .thenComparingLong(RawDevice::registrationOrder));
        return List.copyOf(rawDevices);
    }

    private static TowerTargetGridFailure validateGrid(IGrid targetGrid,
                                                       IGrid primaryGrid,
                                                       TowerTargetDiscoveryMode mode,
                                                       List<TowerResolvedDevice> devices) {
        if (targetGrid == primaryGrid) {
            return TowerTargetGridFailure.PRIMARY_GRID;
        }
        IGrid existingPrimary = ((VirtualGridBridge) targetGrid).virtualPrimaryGrid();
        if (existingPrimary != null && existingPrimary != primaryGrid) {
            return TowerTargetGridFailure.ALREADY_SUBORDINATE;
        }
        for (TowerResolvedDevice device : devices) {
            if (device.node().getOwner() instanceof ControllerBlockEntity) {
                return TowerTargetGridFailure.CONTROLLER_PRESENT;
            }
        }
        if (mode == TowerTargetDiscoveryMode.SCOPE) {
            if (devices.size() != 1 || !devices.getFirst().node().getConnections().isEmpty()) {
                return TowerTargetGridFailure.SCOPE_REQUIRES_SINGLE_UNCONNECTED_NODE;
            }
        }
        return TowerTargetGridFailure.NONE;
    }

    private static RawDevice rawDevice(IGridNode node, long registrationOrder) {
        Object owner = node.getOwner();
        BlockPos position = owner instanceof BlockEntity blockEntity ? blockEntity.getBlockPos().immutable() : node instanceof InWorldGridNode inWorldNode ? inWorldNode.getLocation().immutable() : null;
        int side = resolvePartSide(node, owner, position);
        return new RawDevice(
                node,
                node.getLevel().dimension().location(),
                position,
                side,
                owner.getClass().getName(),
                registrationOrder);
    }

    /**
     * Resolves a mounted side without reflection, including third-party parts hosted by AE's cable bus.
     */
    private static int resolvePartSide(IGridNode node, Object owner, @Nullable BlockPos position) {
        if (owner instanceof AEBasePart part) {
            Direction partSide = part.getSide();
            return partSide == null ? -1 : partSide.ordinal();
        }
        if (!(owner instanceof IPart part) || position == null) {
            return -1;
        }
        if (!(node.getLevel().getBlockEntity(position) instanceof CableBusBlockEntity cableBusBlockEntity)) {
            return -1;
        }
        for (Direction direction : Direction.values()) {
            IPart candidate = cableBusBlockEntity.getPart(direction);
            if (candidate == part || candidate != null && (candidate.getGridNode() == node || candidate.getExternalFacingNode() == node)) {
                return direction.ordinal();
            }
        }
        return -1;
    }

    private static int comparePosition(BlockPos left, BlockPos right) {
        int comparison = Integer.compare(left.getX(), right.getX());
        if (comparison != 0) {
            return comparison;
        }
        comparison = Integer.compare(left.getY(), right.getY());
        if (comparison != 0) {
            return comparison;
        }
        return Integer.compare(left.getZ(), right.getZ());
    }

    private static final class ResolutionRoundImpl implements ResolutionRound {

        private final Map<IGrid, List<RawDevice>> rawDevicesByGrid = new IdentityHashMap<>();

        @Override
        public TowerTargetResolution resolve(Level level,
                                             BlockPos anchor,
                                             IGrid primaryGrid,
                                             TowerTargetDiscoveryMode mode) {
            return CapabilityExposedTowerAeTargetResolver.resolve(level, anchor, primaryGrid, mode, this.rawDevicesByGrid);
        }
    }

    private record RawDevice(IGridNode node,
                             ResourceLocation dimensionId,
                             @Nullable BlockPos position,
                             int side,
                             String nodeType,
                             long registrationOrder) {}

    private record RawDeviceIdentity(ResourceLocation dimensionId,
                                     @Nullable BlockPos position,
                                     int side,
                                     String nodeType) {}
}

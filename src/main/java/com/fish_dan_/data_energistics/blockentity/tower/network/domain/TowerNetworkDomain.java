package com.fish_dan_.data_energistics.blockentity.tower.network.domain;

import com.fish_dan_.data_energistics.blockentity.tower.network.binding.TowerRuntimeKey;
import com.fish_dan_.data_energistics.blockentity.tower.network.energy.TowerEnergyAccessSnapshot;

import net.minecraft.core.BlockPos;

import appeng.api.networking.IGrid;
import appeng.api.networking.IGridNode;
import appeng.api.networking.IGridService;
import org.jspecify.annotations.Nullable;

import java.util.List;
import java.util.Optional;

/**
 * Owns runtime tower state for exactly one physical AE grid.
 *
 * <p>
 * The domain revision invalidates channel and FE snapshots whenever physical or virtual membership changes. Runtime
 * identity and leases are intentionally not persisted.
 * </p>
 */
public interface TowerNetworkDomain extends IGridService {

    /**
     * Returns the physical grid that owns this domain instance.
     *
     * @return owning grid
     */
    IGrid grid();

    /**
     * Returns the current topology and endpoint revision.
     *
     * @return monotonically increasing runtime revision
     */
    long revision();

    /**
     * Invalidates cached domain snapshots for a concrete runtime change.
     *
     * @param reason change category used for diagnostics
     */
    void invalidate(TowerNetworkDomainChange reason);

    /**
     * Returns local physical nodes in their registration order.
     *
     * @return immutable local-node snapshot
     */
    List<IGridNode> localNodes();

    /**
     * Returns the stable runtime registration order for a local node.
     *
     * @param node local physical node
     * @return non-negative registration sequence
     * @throws IllegalArgumentException when the node does not belong to this domain snapshot
     */
    long registrationOrder(IGridNode node);

    /**
     * Adds or refreshes a physical tower participating in this primary grid.
     *
     * @param tower tower participant
     */
    void registerTower(TowerNetworkParticipant tower);

    /**
     * Removes a tower from this domain and releases its current ownership candidacy.
     *
     * @param tower tower participant
     */
    void unregisterTower(TowerNetworkParticipant tower);

    /**
     * Returns the last published result for one tower.
     *
     * @param towerKey tower identity
     * @return latest snapshot when reconciled
     */
    Optional<TowerNetworkTowerSnapshot> towerSnapshot(TowerRuntimeKey towerKey);

    /**
     * Returns the shared FE capability state visible through one active tower on this primary Grid.
     *
     * @param towerKey         requesting tower identity
     * @param excludedPosition adjacent capability owner that must not feed itself, or {@code null}
     * @return shared energy state, or an empty state when the requesting tower is inactive
     */
    TowerEnergyAccessSnapshot energySnapshot(TowerRuntimeKey towerKey, @Nullable BlockPos excludedPosition);

    /**
     * Inserts FE through one active tower into this primary Grid's shared cross-dimensional endpoint topology.
     *
     * @param towerKey         requesting tower identity
     * @param amount           requested non-negative FE
     * @param simulate         whether to simulate without mutation
     * @param excludedPosition adjacent capability owner that must not feed itself, or {@code null}
     * @return accepted FE in {@code [0, amount]}
     */
    long insertEnergy(TowerRuntimeKey towerKey,
                      long amount,
                      boolean simulate,
                      @Nullable BlockPos excludedPosition);

    /**
     * Extracts FE through one active tower from this primary Grid's shared cross-dimensional endpoint topology.
     *
     * @param towerKey         requesting tower identity
     * @param amount           requested non-negative FE
     * @param simulate         whether to simulate without mutation
     * @param excludedPosition adjacent capability owner that must not feed itself, or {@code null}
     * @return extracted FE in {@code [0, amount]}
     */
    long extractEnergy(TowerRuntimeKey towerKey,
                       long amount,
                       boolean simulate,
                       @Nullable BlockPos excludedPosition);
}

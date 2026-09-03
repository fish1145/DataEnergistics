package com.fish_dan_.data_energistics.common.crafting.trinity.planning.gateway;

import com.fish_dan_.data_energistics.common.crafting.trinity.capacity.TrinityCpuStorageCapacity;
import com.fish_dan_.data_energistics.common.crafting.trinity.planning.CraftingQuantityMode;
import com.fish_dan_.data_energistics.common.crafting.trinity.planning.graph.TrinityCraftingGraphSnapshot;
import com.fish_dan_.data_energistics.common.crafting.trinity.planning.inventory.TrinityPlanningInventory;
import com.fish_dan_.data_energistics.common.crafting.trinity.planning.request.TrinityPlanningLimits;

import appeng.api.stacks.AEKey;
import org.jspecify.annotations.Nullable;

import java.math.BigInteger;

/**
 * Immutable, background-safe input for one initial Trinity planning attempt.
 *
 * <p>
 * The request retains only value data captured on the server thread. It never owns a grid, level, provider, block
 * entity, or crafting CPU reference.
 * </p>
 */
public final class TrinityInitialPlanningRequest {

    private final TrinityCraftingGraphSnapshot graph;
    private final long gridScope;
    private final long requestId;
    private final AEKey target;
    private final BigInteger requestedAmount;
    private final CraftingQuantityMode quantityMode;
    private final TrinityPlanningInventory inventory;
    private final TrinityPlanningLimits limits;
    private final TrinityCpuStorageCapacity maxTrinityCapacity;

    private TrinityInitialPlanningRequest(Builder builder) {
        if (builder.graph == null || builder.target == null || builder.requestedAmount == null ||
                builder.quantityMode == null || builder.inventory == null || builder.limits == null ||
                builder.maxTrinityCapacity == null) {
            throw new IllegalStateException("A Trinity initial planning request is incomplete");
        }
        if (builder.gridScope <= 0L || builder.requestId <= 0L || builder.requestedAmount.signum() <= 0) {
            throw new IllegalStateException(
                    "A Trinity initial planning request requires positive Grid scope, identity, amount, and CPU capacity");
        }

        this.graph = builder.graph;
        this.gridScope = builder.gridScope;
        this.requestId = builder.requestId;
        this.target = builder.target;
        this.requestedAmount = builder.requestedAmount;
        this.quantityMode = builder.quantityMode;
        this.inventory = builder.inventory;
        this.limits = builder.limits;
        this.maxTrinityCapacity = builder.maxTrinityCapacity;
    }

    /**
     * @return empty builder for server-thread capture
     */
    public static Builder builder() {
        return new Builder();
    }

    public TrinityCraftingGraphSnapshot graph() {
        return this.graph;
    }

    public long gridScope() {
        return this.gridScope;
    }

    public long requestId() {
        return this.requestId;
    }

    public AEKey target() {
        return this.target;
    }

    public BigInteger requestedAmount() {
        return this.requestedAmount;
    }

    public CraftingQuantityMode quantityMode() {
        return this.quantityMode;
    }

    public TrinityPlanningInventory inventory() {
        return this.inventory;
    }

    public TrinityPlanningLimits limits() {
        return this.limits;
    }

    public TrinityCpuStorageCapacity maxTrinityCapacity() {
        return this.maxTrinityCapacity;
    }

    /**
     * Builds the multi-field request only after all mutable grid state has been converted to values.
     */
    public static final class Builder {

        private @Nullable TrinityCraftingGraphSnapshot graph;
        private long gridScope;
        private long requestId;
        private @Nullable AEKey target;
        private @Nullable BigInteger requestedAmount;
        private @Nullable CraftingQuantityMode quantityMode;
        private @Nullable TrinityPlanningInventory inventory;
        private @Nullable TrinityPlanningLimits limits;
        private @Nullable TrinityCpuStorageCapacity maxTrinityCapacity;

        private Builder() {}

        public Builder graph(TrinityCraftingGraphSnapshot graph) {
            this.graph = graph;
            return this;
        }

        public Builder gridScope(long gridScope) {
            this.gridScope = gridScope;
            return this;
        }

        public Builder requestId(long requestId) {
            this.requestId = requestId;
            return this;
        }

        public Builder target(AEKey target) {
            this.target = target;
            return this;
        }

        public Builder requestedAmount(BigInteger requestedAmount) {
            this.requestedAmount = requestedAmount;
            return this;
        }

        public Builder quantityMode(CraftingQuantityMode quantityMode) {
            this.quantityMode = quantityMode;
            return this;
        }

        public Builder inventory(TrinityPlanningInventory inventory) {
            this.inventory = inventory;
            return this;
        }

        public Builder limits(TrinityPlanningLimits limits) {
            this.limits = limits;
            return this;
        }

        public Builder maxTrinityCapacity(TrinityCpuStorageCapacity maxTrinityCapacity) {
            this.maxTrinityCapacity = maxTrinityCapacity;
            return this;
        }

        public TrinityInitialPlanningRequest build() {
            return new TrinityInitialPlanningRequest(this);
        }
    }
}

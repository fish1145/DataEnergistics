package com.fish_dan_.data_energistics.common.crafting.trinity.planning.gateway;

import com.fish_dan_.data_energistics.common.crafting.trinity.planning.CraftingQuantityMode;
import com.fish_dan_.data_energistics.common.crafting.trinity.planning.graph.TrinityCraftingGraphSnapshot;
import com.fish_dan_.data_energistics.common.crafting.trinity.planning.request.TrinityPlanningLimits;
import com.fish_dan_.data_energistics.configuration.schema.DataEnergisticsConfiguration.TrinityCraftingSchema;

import appeng.api.stacks.AEKey;

import java.math.BigInteger;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

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
    private final Map<AEKey, BigInteger> available;
    private final TrinityPlanningLimits limits;
    private final long maxTrinityBytes;

    private TrinityInitialPlanningRequest(Builder builder) {
        if (builder.graph == null || builder.target == null || builder.requestedAmount == null ||
                builder.quantityMode == null || builder.available == null || builder.limits == null) {
            throw new IllegalStateException("A Trinity initial planning request is incomplete");
        }
        if (builder.gridScope <= 0L || builder.requestId <= 0L || builder.requestedAmount.signum() <= 0 ||
                builder.maxTrinityBytes <= 0L) {
            throw new IllegalStateException(
                    "A Trinity initial planning request requires positive Grid scope, identity, amount, and CPU capacity");
        }

        LinkedHashMap<AEKey, BigInteger> copiedAvailable = new LinkedHashMap<>();
        builder.available.forEach((key, amount) -> {
            if (key == null || amount == null || amount.signum() <= 0) {
                throw new IllegalArgumentException("A Trinity inventory snapshot may contain only positive named amounts");
            }
            copiedAvailable.put(key, amount);
        });

        this.graph = builder.graph;
        this.gridScope = builder.gridScope;
        this.requestId = builder.requestId;
        this.target = builder.target;
        this.requestedAmount = builder.requestedAmount;
        this.quantityMode = builder.quantityMode;
        this.available = Collections.unmodifiableMap(copiedAvailable);
        this.limits = builder.limits;
        this.maxTrinityBytes = builder.maxTrinityBytes;
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

    public Map<AEKey, BigInteger> available() {
        return this.available;
    }

    public TrinityPlanningLimits limits() {
        return this.limits;
    }

    /**
     * @return detached compatibility configuration that cannot mutate this request
     * @deprecated use {@link #limits()}
     */
    @Deprecated(forRemoval = false)
    public TrinityCraftingSchema settings() {
        return this.limits.detachedSchema();
    }

    public long maxTrinityBytes() {
        return this.maxTrinityBytes;
    }

    /**
     * Builds the multi-field request only after all mutable grid state has been converted to values.
     */
    public static final class Builder {

        private TrinityCraftingGraphSnapshot graph;
        private long gridScope;
        private long requestId;
        private AEKey target;
        private BigInteger requestedAmount;
        private CraftingQuantityMode quantityMode;
        private Map<AEKey, BigInteger> available;
        private TrinityPlanningLimits limits;
        private long maxTrinityBytes;

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

        public Builder available(Map<AEKey, BigInteger> available) {
            this.available = available;
            return this;
        }

        public Builder settings(TrinityCraftingSchema settings) {
            this.limits = TrinityPlanningLimits.capture(settings);
            return this;
        }

        public Builder limits(TrinityPlanningLimits limits) {
            this.limits = limits;
            return this;
        }

        public Builder maxTrinityBytes(long maxTrinityBytes) {
            this.maxTrinityBytes = maxTrinityBytes;
            return this;
        }

        public TrinityInitialPlanningRequest build() {
            return new TrinityInitialPlanningRequest(this);
        }
    }
}

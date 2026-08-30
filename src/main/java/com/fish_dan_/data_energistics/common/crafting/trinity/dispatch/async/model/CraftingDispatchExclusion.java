package com.fish_dan_.data_energistics.common.crafting.trinity.dispatch.async.model;

import com.fish_dan_.data_energistics.common.crafting.trinity.dispatch.model.CraftingDispatchTarget;
import com.fish_dan_.data_energistics.common.crafting.trinity.dispatch.model.CraftingProviderId;
import com.fish_dan_.data_energistics.common.crafting.trinity.dispatch.model.MachineTargetId;
import com.fish_dan_.data_energistics.common.crafting.trinity.dispatch.model.ProviderCapacitySnapshot;
import com.fish_dan_.data_energistics.common.crafting.trinity.dispatch.model.ProviderRoutingMode;

import java.util.Optional;

/**
 * Immutable transient exclusion accumulated while replacing pre-ownership dispatch proposals.
 *
 * <p>
 * Provider scope removes every route published by one provider for the current work. Target scope removes one exact
 * machine when a physical identity is known, otherwise one provider-local route and routing contract. Exclusions are
 * request-local, never persisted, and have no effect on other workers or patterns.
 * </p>
 *
 * @param providerId      provider owning the failed selection
 * @param providerWide    whether every route from the provider is excluded
 * @param route           exact provider-local route for target scope
 * @param machineTargetId exact physical target when known
 * @param routingMode     routing contract of the target-scoped selection
 */
public record CraftingDispatchExclusion(
                                        CraftingProviderId providerId,
                                        boolean providerWide,
                                        Optional<CraftingDispatchTarget> route,
                                        Optional<MachineTargetId> machineTargetId,
                                        Optional<ProviderRoutingMode> routingMode) {

    public CraftingDispatchExclusion {
        if (providerId == null || route == null || machineTargetId == null || routingMode == null) {
            throw new IllegalArgumentException("Crafting dispatch exclusion context must not be null");
        }
        if (providerWide && (route.isPresent() || machineTargetId.isPresent() || routingMode.isPresent()) ||
                !providerWide && (route.isEmpty() || routingMode.isEmpty())) {
            throw new IllegalArgumentException("Crafting dispatch exclusion scope is inconsistent");
        }
    }

    /**
     * Excludes all routes of one failed provider for this work.
     *
     * @param snapshot failed provider selection
     * @return provider-scoped exclusion
     */
    public static CraftingDispatchExclusion provider(ProviderCapacitySnapshot snapshot) {
        return new CraftingDispatchExclusion(
                snapshot.providerId(),
                true,
                Optional.empty(),
                Optional.empty(),
                Optional.empty());
    }

    /**
     * Excludes one exact route or physical machine for this work.
     *
     * @param snapshot failed target selection
     * @return target-scoped exclusion
     */
    public static CraftingDispatchExclusion target(ProviderCapacitySnapshot snapshot) {
        return new CraftingDispatchExclusion(
                snapshot.providerId(),
                false,
                Optional.of(snapshot.route()),
                snapshot.machineTargetId(),
                Optional.of(snapshot.routingMode()));
    }

    /**
     * Tests a current immutable capacity candidate without reading mutable provider state.
     *
     * @param snapshot current candidate
     * @return whether this accumulated failure excludes the candidate
     */
    public boolean excludes(ProviderCapacitySnapshot snapshot) {
        if (this.providerWide) {
            return this.providerId.equals(snapshot.providerId());
        }
        if (this.machineTargetId.isPresent()) {
            return this.machineTargetId.equals(snapshot.machineTargetId());
        }
        return this.providerId.equals(snapshot.providerId()) &&
                this.route.orElseThrow().equals(snapshot.route()) &&
                this.routingMode.orElseThrow() == snapshot.routingMode();
    }
}

package com.fish_dan_.data_energistics.common.crafting.trinity.dispatch.provider;

import com.fish_dan_.data_energistics.api.crafting.dispatch.CountedCraftingAdmission;
import com.fish_dan_.data_energistics.api.crafting.dispatch.CountedCraftingProviderAdapter;
import com.fish_dan_.data_energistics.common.crafting.trinity.dispatch.commit.CountedCraftingPreparation;
import com.fish_dan_.data_energistics.common.crafting.trinity.dispatch.model.CraftingDispatchRejection;
import com.fish_dan_.data_energistics.common.crafting.trinity.dispatch.model.CraftingDispatchStatus;
import com.fish_dan_.data_energistics.common.crafting.trinity.dispatch.model.CraftingDispatchTarget;
import com.fish_dan_.data_energistics.common.crafting.trinity.dispatch.model.CraftingDispatchTargetAvailability;
import com.fish_dan_.data_energistics.common.crafting.trinity.dispatch.model.ProviderRoutingMode;

import appeng.api.crafting.IPatternDetails;
import appeng.api.networking.crafting.ICraftingProvider;
import appeng.api.stacks.KeyCounter;

/**
 * Single internal resolution boundary for direct, registered and generic crafting provider dispatch.
 */
public final class CountedCraftingProviderAdapters {

    private static final CountedCraftingProviderAdapterRegistry REGISTRY = new CountedCraftingProviderAdapterRegistryImpl();

    private CountedCraftingProviderAdapters() {}

    /**
     * Registers one external adapter selected by the frozen provider plugin registry.
     */
    public static void register(ICraftingProvider provider, CountedCraftingProviderAdapter adapter) {
        REGISTRY.register(provider, adapter);
    }

    /** Removes the exact adapter owned by a provider plugin lifecycle. */
    public static void unregister(ICraftingProvider provider, CountedCraftingProviderAdapter adapter) {
        REGISTRY.unregister(provider, adapter);
    }

    /**
     * Resolves and prepares the provider using registered adapter, direct contract, then generic-single priority.
     */
    public static CountedCraftingPreparation prepare(
                                                     ICraftingProvider provider,
                                                     IPatternDetails patternDetails,
                                                     KeyCounter[] prototype,
                                                     long requestedCount,
                                                     CraftingDispatchTargetAvailability targetAvailability) {
        CountedCraftingProviderAdapter adapter = REGISTRY.find(provider);
        if (adapter != null) {
            return prepareProviderTarget(
                    adapter,
                    patternDetails,
                    prototype,
                    requestedCount,
                    targetAvailability);
        }
        if (provider instanceof CountedCraftingProvider countedProvider) {
            return countedProvider.prepareBatch(patternDetails, prototype, requestedCount, targetAvailability);
        }
        return prepareProviderTarget(
                (details, ignoredPrototype, ignoredCount) -> new SingleCraftingAdmission(provider, details),
                patternDetails,
                prototype,
                1L,
                targetAvailability);
    }

    /**
     * Validates the exact count contract used by the CPU before any extraction or commit.
     */
    public static long validatedAdmissionCount(
                                               ICraftingProvider provider,
                                               CountedCraftingAdmission admission,
                                               long requestedCount) {
        long count = admission.count();
        if (count <= 0L || count > requestedCount) {
            throw new IllegalStateException(
                    "Crafting provider " + provider + " admitted " + count + " crafts outside requested range 1.." + requestedCount);
        }
        return count;
    }

    /**
     * Returns the internal provider-adapter generation used to invalidate future dispatch proposals.
     *
     * <p>
     * This common-layer read-only contract is not part of the third-party registration API.
     * </p>
     *
     * @return current identity-registry mutation revision
     */
    public static long mutationRevision() {
        return REGISTRY.mutationRevision();
    }

    /** Returns whether the frozen plugin runtime currently owns this provider's counted adapter. */
    public static boolean hasRegisteredAdapter(ICraftingProvider provider) {
        return REGISTRY.find(provider) != null;
    }

    /**
     * Classifies providers that do not expose explicit target capacity.
     *
     * <p>
     * A direct or registered counted contract remains aggregate-counted. All other providers retain AE2's native
     * single-call semantics and therefore remain unknown.
     * </p>
     *
     * @param provider live provider resolved from the publication index
     * @return conservative non-targeted routing mode
     */
    public static ProviderRoutingMode fallbackRoutingMode(ICraftingProvider provider) {
        return provider instanceof CountedCraftingProvider || REGISTRY.find(provider) != null ?
                ProviderRoutingMode.AGGREGATE :
                ProviderRoutingMode.UNKNOWN;
    }

    static CountedCraftingPreparation prepareProviderTarget(
                                                            CountedCraftingProviderAdapter adapter,
                                                            IPatternDetails patternDetails,
                                                            KeyCounter[] prototype,
                                                            long requestedCount,
                                                            CraftingDispatchTargetAvailability targetAvailability) {
        if (requestedCount <= 0L) {
            throw new IllegalArgumentException("Requested counted crafting amount must be positive: " + requestedCount);
        }
        if (targetAvailability == null) {
            throw new IllegalArgumentException("Crafting dispatch target availability must not be null");
        }
        CraftingDispatchTarget target = CraftingDispatchTarget.provider();
        if (!targetAvailability.canAttempt(target)) {
            return CountedCraftingPreparation.rejected(
                    CraftingDispatchRejection.targeted(CraftingDispatchStatus.NO_CAPACITY, target));
        }
        CountedCraftingAdmission admission = adapter.prepareBatch(patternDetails, prototype, requestedCount);
        if (admission == null) {
            return CountedCraftingPreparation.rejected(
                    CraftingDispatchRejection.scoped(CraftingDispatchStatus.NO_CAPACITY));
        }
        return CountedCraftingPreparation.accepted(admission, target);
    }

    /**
     * Generic AE2 providers retain their original one-pattern physical submission semantics.
     */
    private record SingleCraftingAdmission(ICraftingProvider provider, IPatternDetails patternDetails)
            implements CountedCraftingAdmission {

        @Override
        public long count() {
            return 1L;
        }

        @Override
        public boolean commit(KeyCounter[] prototype) {
            return this.provider.pushPattern(this.patternDetails, prototype);
        }
    }
}

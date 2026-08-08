package com.fish_dan_.data_energistics.common.crafting.trinity.dispatch.provider;

import com.fish_dan_.data_energistics.Data_Energistics;
import com.fish_dan_.data_energistics.api.crafting.dispatch.CountedCraftingAdmission;
import com.fish_dan_.data_energistics.api.crafting.dispatch.CountedCraftingCapacity;
import com.fish_dan_.data_energistics.api.crafting.dispatch.CountedCraftingProviderAdapter;
import com.fish_dan_.data_energistics.api.crafting.dispatch.CountedCraftingRoutingMode;
import com.fish_dan_.data_energistics.api.crafting.dispatch.CountedCraftingTarget;
import com.fish_dan_.data_energistics.common.crafting.trinity.dispatch.capacity.ProviderCapacityView;
import com.fish_dan_.data_energistics.common.crafting.trinity.dispatch.capacity.TargetedCountedCraftingProvider;
import com.fish_dan_.data_energistics.common.crafting.trinity.dispatch.commit.CountedCraftingPreparation;
import com.fish_dan_.data_energistics.common.crafting.trinity.dispatch.model.CraftingDispatchRejection;
import com.fish_dan_.data_energistics.common.crafting.trinity.dispatch.model.CraftingDispatchStatus;
import com.fish_dan_.data_energistics.common.crafting.trinity.dispatch.model.CraftingDispatchTarget;
import com.fish_dan_.data_energistics.common.crafting.trinity.dispatch.model.CraftingDispatchTargetAvailability;
import com.fish_dan_.data_energistics.common.crafting.trinity.dispatch.model.CraftingProviderId;
import com.fish_dan_.data_energistics.common.crafting.trinity.dispatch.model.DispatchCapacity;
import com.fish_dan_.data_energistics.common.crafting.trinity.dispatch.model.MachineTargetId;
import com.fish_dan_.data_energistics.common.crafting.trinity.dispatch.model.ProviderCapacitySnapshot;
import com.fish_dan_.data_energistics.common.crafting.trinity.dispatch.model.ProviderRoutingMode;

import appeng.api.crafting.IPatternDetails;
import appeng.api.networking.crafting.ICraftingProvider;
import appeng.api.stacks.KeyCounter;

import java.util.List;
import java.util.Optional;
import java.util.OptionalLong;

/**
 * Single internal resolution boundary for direct, registered and generic crafting provider dispatch.
 */
public final class CountedCraftingProviderAdapters {

    private static final CountedCraftingProviderAdapterRegistry REGISTRY = new CountedCraftingProviderAdapterRegistryImpl();

    private CountedCraftingProviderAdapters() {
    }

    /**
     * Registers one external adapter selected by the frozen provider plugin registry.
     */
    public static void register(ICraftingProvider provider, CountedCraftingProviderAdapter adapter) {
        REGISTRY.register(provider, adapter);
    }

    /**
     * Removes the exact adapter owned by a provider plugin lifecycle.
     */
    public static void unregister(ICraftingProvider provider, CountedCraftingProviderAdapter adapter) {
        REGISTRY.unregister(provider, adapter);
    }

    /**
     * Clears every adapter owned by the stopped server.
     */
    public static void clear() {
        REGISTRY.clear();
    }

    /**
     * Captures immutable capacity snapshots through the provider's highest-priority compatible adapter.
     *
     * @param provider            live provider resolved from the publication index
     * @param providerId          stable identity of that publication
     * @param patternDetails      exact pattern selected by the crafting plan
     * @param prototype           read-only exact per-craft input prototype
     * @param requestedCrafts     positive logical craft count still eligible for dispatch
     * @param patternIdentity     immutable pattern signature owned by the caller
     * @param publicationRevision provider-index revision observed during capture
     * @param capacityRevision    adapter-registry revision observed during capture
     * @param captureTick         server tick at which capacity was observed
     * @return immutable capacity snapshots; an empty list means no currently usable route
     */
    public static List<ProviderCapacitySnapshot> captureCapacity(
            ICraftingProvider provider,
            CraftingProviderId providerId,
            IPatternDetails patternDetails,
            KeyCounter[] prototype,
            long requestedCrafts,
            String patternIdentity,
            long publicationRevision,
            long capacityRevision,
            long captureTick) {
        if (requestedCrafts <= 0L) {
            throw new IllegalArgumentException("Requested counted crafting capacity must be positive");
        }
        CapacityCaptureContext context = new CapacityCaptureContext(
                providerId,
                patternDetails,
                prototype,
                requestedCrafts,
                patternIdentity,
                publicationRevision,
                capacityRevision,
                captureTick);
        return List.copyOf(resolve(provider).capture().capture(context));
    }

    /**
     * Prepares the exact route represented by a previously captured snapshot.
     *
     * @param provider           current live provider
     * @param patternDetails     exact pattern selected by the crafting plan
     * @param prototype          read-only exact per-craft input prototype
     * @param requestedCount     positive maximum logical craft count offered to the route
     * @param snapshot           current revalidated capacity snapshot
     * @param targetAvailability current dispatch-window target filter
     * @return accepted one-shot admission or explicit rejection facts
     */
    public static CountedCraftingPreparation prepare(
            ICraftingProvider provider,
            IPatternDetails patternDetails,
            KeyCounter[] prototype,
            long requestedCount,
            ProviderCapacitySnapshot snapshot,
            CraftingDispatchTargetAvailability targetAvailability) {
        if (requestedCount <= 0L) {
            throw new IllegalArgumentException("Requested counted crafting amount must be positive: " + requestedCount);
        }
        CraftingDispatchTargetAvailability routeAvailability = snapshot.routingMode() == ProviderRoutingMode.AGGREGATE ?
                targetAvailability :
                target -> target.equals(snapshot.route()) && targetAvailability.canAttempt(target);
        return resolve(provider).prepare().prepare(
                new PreparationContext(
                        patternDetails,
                        prototype,
                        requestedCount,
                        snapshot,
                        routeAvailability));
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

    static CountedCraftingPreparation prepareProviderTarget(
            CountedCraftingProviderAdapter adapter,
            IPatternDetails patternDetails,
            KeyCounter[] prototype,
            long requestedCount,
            CraftingDispatchTargetAvailability targetAvailability) {
        if (requestedCount <= 0L) {
            throw new IllegalArgumentException("Requested counted crafting amount must be positive: " + requestedCount);
        }
        CraftingDispatchTarget target = CraftingDispatchTarget.provider();
        if (!targetAvailability.canAttempt(target)) {
            return unavailableTarget(target);
        }
        CountedCraftingAdmission admission = adapter.prepareBatch(patternDetails, prototype, requestedCount);
        return admission == null ?
                CountedCraftingPreparation.rejected(
                        CraftingDispatchRejection.scoped(CraftingDispatchStatus.NO_CAPACITY)) :
                CountedCraftingPreparation.accepted(admission, target);
    }

    /**
     * Resolves provider kinds once so capture and preparation share one precedence contract.
     */
    private static ResolvedProviderAdapter resolve(ICraftingProvider provider) {
        CountedCraftingProviderAdapter registered = REGISTRY.find(provider);
        if (registered != null) {
            return registeredAdapter(provider, registered);
        }
        if (provider instanceof TargetedCountedCraftingProvider targetedProvider) {
            return new ResolvedProviderAdapter(
                    context -> captureLegacyCapacity(targetedProvider, context),
                    context -> prepareLegacyTargeted(targetedProvider, context));
        }
        if (provider instanceof ProviderCapacityView capacityView) {
            ProviderPreparation preparation = provider instanceof CountedCraftingProvider countedProvider ?
                    context -> countedProvider.prepareBatch(
                            context.patternDetails(),
                            context.prototype(),
                            context.requestedCount(),
                            context.targetAvailability()) :
                    context -> prepareGeneric(provider, context);
            return new ResolvedProviderAdapter(
                    context -> captureLegacyCapacity(capacityView, context),
                    context -> {
                        if (context.snapshot().routingMode() == ProviderRoutingMode.TARGETED) {
                            throw new IllegalStateException(
                                    "Targeted capacity snapshot requires a targeted counted provider");
                        }
                        return preparation.prepare(context);
                    });
        }
        if (provider instanceof CountedCraftingProvider countedProvider) {
            return new ResolvedProviderAdapter(
                    context -> capturePublicCapacity(countedProvider, context),
                    context -> preparePublicTarget(countedProvider, context));
        }
        return new ResolvedProviderAdapter(
                context -> List.of(genericCapacity(context)),
                context -> prepareGeneric(provider, context));
    }

    /**
     * Builds the registered adapter bridge without exposing internal snapshot metadata to the plugin.
     */
    private static ResolvedProviderAdapter registeredAdapter(
            ICraftingProvider provider,
            CountedCraftingProviderAdapter adapter) {
        return new ResolvedProviderAdapter(
                context -> {
                    try {
                        return capturePublicCapacity(adapter, context);
                    } catch (RuntimeException exception) {
                        Data_Energistics.LOGGER.error(
                                "Registered counted crafting adapter for provider {} threw while capturing capacity; isolating that provider",
                                provider,
                                exception);
                        return List.of();
                    }
                },
                context -> preparePublicTarget(adapter, context));
    }

    /**
     * Converts a plugin-owned capacity observation to the immutable internal planning form.
     */
    private static List<ProviderCapacitySnapshot> capturePublicCapacity(
            CountedCraftingProviderAdapter adapter,
            CapacityCaptureContext context) {
        List<CountedCraftingCapacity> capacities = List.copyOf(adapter.captureCapacity(
                context.patternDetails(),
                context.prototype(),
                context.requestedCrafts()));
        return capacities.stream()
                .map(capacity -> new ProviderCapacitySnapshot(
                        context.providerId(),
                        toInternalTarget(capacity.target()),
                        capacity.target().machineIdentity().map(MachineTargetId::new),
                        context.patternIdentity(),
                        context.publicationRevision(),
                        context.capacityRevision(),
                        context.captureTick(),
                        toInternalRoutingMode(capacity.routingMode()),
                        toInternalCapacity(capacity.logicalCrafts()),
                        toInternalCapacity(capacity.maximumSingleBatch())))
                .toList();
    }

    /**
     * Preserves existing provider-owned target capture as a compatibility bridge.
     */
    private static List<ProviderCapacitySnapshot> captureLegacyCapacity(
            ProviderCapacityView capacityView,
            CapacityCaptureContext context) {
        return List.copyOf(capacityView.snapshotCapacity(
                context.providerId(),
                context.patternDetails(),
                context.prototype(),
                context.requestedCrafts(),
                context.patternIdentity(),
                context.publicationRevision(),
                context.capacityRevision(),
                context.captureTick()));
    }

    /**
     * Captures the conservative one-call semantics of an unadapted AE2 provider.
     */
    private static ProviderCapacitySnapshot genericCapacity(CapacityCaptureContext context) {
        return new ProviderCapacitySnapshot(
                context.providerId(),
                CraftingDispatchTarget.provider(),
                Optional.empty(),
                context.patternIdentity(),
                context.publicationRevision(),
                context.capacityRevision(),
                context.captureTick(),
                ProviderRoutingMode.UNKNOWN,
                DispatchCapacity.Unknown.INSTANCE,
                new DispatchCapacity.Known(1L));
    }

    /**
     * Prepares through the public exact-target contract used by registered and direct public adapters.
     */
    private static CountedCraftingPreparation preparePublicTarget(
            CountedCraftingProviderAdapter adapter,
            PreparationContext context) {
        CraftingDispatchTarget target = context.snapshot().route();
        if (!context.targetAvailability().canAttempt(target)) {
            return unavailableTarget(target);
        }
        CountedCraftingAdmission admission = adapter.prepareBatchForTarget(
                context.patternDetails(),
                context.prototype(),
                context.requestedCount(),
                toPublicTarget(context.snapshot()));
        return admission == null ? unavailableCapacity(context.snapshot()) :
                CountedCraftingPreparation.accepted(admission, target);
    }

    /**
     * Prepares an existing internal target-aware provider without changing its direct contract.
     */
    private static CountedCraftingPreparation prepareLegacyTargeted(
            TargetedCountedCraftingProvider provider,
            PreparationContext context) {
        if (context.snapshot().routingMode() != ProviderRoutingMode.TARGETED) {
            return provider.prepareBatch(
                    context.patternDetails(),
                    context.prototype(),
                    context.requestedCount(),
                    context.targetAvailability());
        }
        CraftingDispatchTarget target = context.snapshot().route();
        if (!context.targetAvailability().canAttempt(target)) {
            return unavailableTarget(target);
        }
        CountedCraftingAdmission admission = provider.prepareBatchForTarget(
                context.patternDetails(),
                context.prototype(),
                context.requestedCount(),
                target);
        return admission == null ?
                CountedCraftingPreparation.rejected(CraftingDispatchRejection.targeted(
                        CraftingDispatchStatus.NO_CAPACITY,
                        target)) :
                CountedCraftingPreparation.accepted(admission, target);
    }

    /**
     * Retains AE2's generic single-pattern physical submission contract.
     */
    private static CountedCraftingPreparation prepareGeneric(
            ICraftingProvider provider,
            PreparationContext context) {
        return prepareProviderTarget(
                (details, ignoredPrototype, ignoredCount) -> new SingleCraftingAdmission(provider, details),
                context.patternDetails(),
                context.prototype(),
                1L,
                context.targetAvailability());
    }

    /**
     * Creates a target-scoped rejection before invoking a provider adapter.
     */
    private static CountedCraftingPreparation unavailableTarget(CraftingDispatchTarget target) {
        return CountedCraftingPreparation.rejected(
                CraftingDispatchRejection.targeted(CraftingDispatchStatus.NO_CAPACITY, target));
    }

    /**
     * Preserves aggregate null-admission scope while making exact route failures target-specific.
     */
    private static CountedCraftingPreparation unavailableCapacity(ProviderCapacitySnapshot snapshot) {
        return snapshot.routingMode() == ProviderRoutingMode.AGGREGATE ?
                CountedCraftingPreparation.rejected(
                        CraftingDispatchRejection.scoped(CraftingDispatchStatus.NO_CAPACITY)) :
                unavailableTarget(snapshot.route());
    }

    /**
     * Converts an immutable public target without leaking internal target classes.
     */
    private static CraftingDispatchTarget toInternalTarget(CountedCraftingTarget target) {
        return new CraftingDispatchTarget(target.stableIdentity());
    }

    /**
     * Recreates the public target chosen from a validated internal capacity snapshot.
     */
    private static CountedCraftingTarget toPublicTarget(ProviderCapacitySnapshot snapshot) {
        if (snapshot.routingMode() == ProviderRoutingMode.AGGREGATE) {
            return CountedCraftingTarget.provider();
        }
        return snapshot.machineTargetId()
                .map(machine -> CountedCraftingTarget.machine(
                        snapshot.route().stableIdentity(), machine.stableIdentity()))
                .orElseGet(() -> CountedCraftingTarget.route(snapshot.route().stableIdentity()));
    }

    /**
     * Converts the intentionally smaller public routing vocabulary.
     */
    private static ProviderRoutingMode toInternalRoutingMode(CountedCraftingRoutingMode routingMode) {
        return switch (routingMode) {
            case TARGETED -> ProviderRoutingMode.TARGETED;
            case ORDERED -> ProviderRoutingMode.ORDERED;
            case AGGREGATE -> ProviderRoutingMode.AGGREGATE;
        };
    }

    /**
     * Converts absence to explicit internal unknown capacity without numeric sentinels.
     */
    private static DispatchCapacity toInternalCapacity(OptionalLong capacity) {
        return capacity.isPresent() ?
                new DispatchCapacity.Known(capacity.getAsLong()) :
                DispatchCapacity.Unknown.INSTANCE;
    }

    /**
     * Immutable metadata supplied only to internal capacity adapters.
     */
    private record CapacityCaptureContext(
            CraftingProviderId providerId,
            IPatternDetails patternDetails,
            KeyCounter[] prototype,
            long requestedCrafts,
            String patternIdentity,
            long publicationRevision,
            long capacityRevision,
            long captureTick) {
    }

    /**
     * Immutable preparation input shared by every resolved provider adapter.
     */
    private record PreparationContext(
            IPatternDetails patternDetails,
            KeyCounter[] prototype,
            long requestedCount,
            ProviderCapacitySnapshot snapshot,
            CraftingDispatchTargetAvailability targetAvailability) {
    }

    /**
     * Resolved capture and preparation strategies for one provider kind.
     */
    private record ResolvedProviderAdapter(CapacityCapture capture, ProviderPreparation prepare) {
    }

    /**
     * Internal capacity strategy selected once per boundary call.
     */
    @FunctionalInterface
    private interface CapacityCapture {

        List<ProviderCapacitySnapshot> capture(CapacityCaptureContext context);
    }

    /**
     * Internal preparation strategy selected once per boundary call.
     */
    @FunctionalInterface
    private interface ProviderPreparation {

        CountedCraftingPreparation prepare(PreparationContext context);
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

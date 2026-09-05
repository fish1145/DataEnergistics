package com.fish_dan_.data_energistics.ae2.patternprovider;

import com.fish_dan_.data_energistics.accessor.patternprovider.PatternProviderBatchAccess;
import com.fish_dan_.data_energistics.api.crafting.dispatch.CountedCraftingAdmission;
import com.fish_dan_.data_energistics.api.registry.machine.CraftingMachineScope;
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
import com.fish_dan_.data_energistics.common.entrypoint.machine.CraftingMachineCapacityAdapters;
import com.fish_dan_.data_energistics.common.entrypoint.machine.CraftingMachineCapacityAdapters.Observation;

import appeng.api.config.Actionable;
import appeng.api.config.LockCraftingMode;
import appeng.api.config.Settings;
import appeng.api.crafting.IPatternDetails;
import appeng.api.implementations.blockentities.ICraftingMachine;
import appeng.api.networking.crafting.ICraftingProvider;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.GenericStack;
import appeng.api.stacks.KeyCounter;
import appeng.helpers.patternprovider.PatternProviderLogic;
import appeng.helpers.patternprovider.PatternProviderTarget;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;

import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import org.jspecify.annotations.Nullable;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.function.BiFunction;
import java.util.function.Function;

/**
 * Implements capacity-aware counted dispatch for AE2's ordinary external-inventory pattern-provider path.
 *
 * <p>
 * Target discovery remains owned by AE2 through {@link PatternProviderBatchAccess}. This class only combines identical
 * craft inputs, derives a safe logical count from simulated target capacity and commits one scaled physical push.
 * </p>
 */
public final class PatternProviderBatching {

    private PatternProviderBatching() {}

    /**
     * Prepares AE2's ordinary provider route while retaining target-specific Blocking and capacity facts.
     *
     * @param logic              live AE2 provider logic
     * @param access             stable access to AE2 routing state
     * @param patternDetails     exact pattern selected by the crafting plan
     * @param prototype          one exact per-craft input prototype
     * @param requestedCount     positive maximum logical craft count
     * @param afterCommit        callback invoked after a successful physical push
     * @param targetAvailability current-window target filter
     * @return accepted admission or explicit rejection facts
     */
    public static CountedCraftingPreparation prepareStandardBatch(
                                                                  PatternProviderLogic logic,
                                                                  PatternProviderBatchAccess access,
                                                                  IPatternDetails patternDetails,
                                                                  KeyCounter[] prototype,
                                                                  long requestedCount,
                                                                  Runnable afterCommit,
                                                                  CraftingDispatchTargetAvailability targetAvailability) {
        return prepareStandardBatch(
                logic,
                access,
                patternDetails,
                patternDetails,
                prototype,
                requestedCount,
                afterCommit,
                targetAvailability);
    }

    /**
     * Prepares AE2's ordinary provider route with a separately authorized input-emission binding.
     *
     * <p>
     * Registered pattern identity remains authoritative for publication, blocking, capacity and success callbacks.
     * Only the ordinary external-inventory commit uses {@code extractionDetails} to emit the already extracted keys.
     * Dedicated crafting machines retain the registered pattern and their own validation semantics.
     * </p>
     */
    public static CountedCraftingPreparation prepareStandardBatch(
                                                                  PatternProviderLogic logic,
                                                                  PatternProviderBatchAccess access,
                                                                  IPatternDetails patternDetails,
                                                                  IPatternDetails extractionDetails,
                                                                  KeyCounter[] prototype,
                                                                  long requestedCount,
                                                                  Runnable afterCommit,
                                                                  CraftingDispatchTargetAvailability targetAvailability) {
        validateRequestedCount(requestedCount);

        if (!access.dataEnergistics$getSendList().isEmpty()) {
            return rejected(CraftingDispatchStatus.BUSY);
        }
        if (!access.dataEnergistics$getMainNode().isActive()) {
            return rejected(CraftingDispatchStatus.OFFLINE);
        }
        if (!access.dataEnergistics$getPatterns().contains(patternDetails)) {
            return rejected(CraftingDispatchStatus.REJECTED);
        }
        if (logic.getCraftingLockedReason() != LockCraftingMode.NONE) {
            return rejected(CraftingDispatchStatus.LOCKED);
        }

        var lockMode = logic.getConfigManager().getSetting(Settings.LOCK_CRAFTING_MODE);
        boolean singleCraftPath = requiresSingleCraftPath(lockMode, false);
        if (singleCraftPath && patternDetails == extractionDetails) {
            return prepareSingle(
                    logic,
                    patternDetails,
                    prototype,
                    requestedCount,
                    targetAvailability);
        }
        requestedCount = boundInputBatchLimit(singleCraftPath, requestedCount);
        var blockEntity = access.dataEnergistics$getHost().getBlockEntity();
        var level = blockEntity.getLevel();
        if (level == null) {
            return rejected(CraftingDispatchStatus.OFFLINE);
        }

        var possibleTargets = new ObjectArrayList<PushTarget>();
        for (Direction direction : access.dataEnergistics$invokeGetActiveSides()) {
            var adjacentPosition = blockEntity.getBlockPos().relative(direction);
            var adjacentSide = direction.getOpposite();
            var craftingMachine = ICraftingMachine.of(level, adjacentPosition, adjacentSide);
            boolean dedicatedCraftingMachine = craftingMachine != null && craftingMachine.acceptsPlans();
            if (requiresSingleCraftPath(LockCraftingMode.NONE, dedicatedCraftingMachine)) {
                if (targetAvailability.canAttempt(CraftingDispatchTarget.provider())) {
                    return prepareSingle(
                            logic,
                            patternDetails,
                            prototype,
                            requestedCount,
                            targetAvailability);
                }
                // A proposal may have selected another exact side from the same complete capacity capture. The
                // provider-scoped dedicated-machine route is not that target, so continue scanning rather than
                // allowing one adjacent crafting machine to hide every external processing route.
                continue;
            }

            var target = access.dataEnergistics$invokeFindAdapter(direction);
            if (target != null) {
                possibleTargets.add(new PushTarget(direction, target));
            }
        }

        if (!patternDetails.supportsPushInputsToExternalInventory()) {
            return rejected(CraftingDispatchStatus.REJECTED);
        }

        List<CraftingDispatchRejection> rejections = new ObjectArrayList<>();
        int normalizedRoundRobin = rearrangeRoundRobin(
                possibleTargets,
                access.dataEnergistics$getRoundRobinIndex());
        for (int targetOffset = 0; targetOffset < possibleTargets.size(); targetOffset++) {
            PushTarget possibleTarget = possibleTargets.get(targetOffset);
            CraftingDispatchTarget dispatchTarget = externalInventoryDispatchTarget(
                    singleCraftPath,
                    possibleTarget.direction());
            if (!targetAvailability.canAttempt(dispatchTarget)) {
                continue;
            }
            if (isBlockedByTargetContents(
                    logic.isBlocking(),
                    possibleTarget.target(),
                    access.dataEnergistics$getPatternInputs())) {
                rejections.add(CraftingDispatchRejection.targeted(
                        CraftingDispatchStatus.BLOCKED,
                        dispatchTarget));
                continue;
            }

            long count = simulateCapacity(possibleTarget.target(), prototype, requestedCount);
            if (count > 0L) {
                var adjacentPosition = blockEntity.getBlockPos().relative(possibleTarget.direction());
                var adjacentSide = possibleTarget.direction().getOpposite();
                Observation observation = CraftingMachineCapacityAdapters.capture(
                        level,
                        adjacentPosition,
                        adjacentSide,
                        patternDetails,
                        prototype,
                        count);
                if (observation != null) {
                    count = Math.min(count, observation.remainingLogicalCrafts());
                }
            }
            if (count <= 0L) {
                rejections.add(CraftingDispatchRejection.targeted(
                        CraftingDispatchStatus.NO_CAPACITY,
                        dispatchTarget));
                continue;
            }
            int nextRoundRobinIndex = nextRoundRobinIndex(normalizedRoundRobin, targetOffset);
            long admittedCount = count;

            return CountedCraftingPreparation.accepted(
                    ownershipAwareAdmission(admittedCount, prototype, (committedPrototype, transferOwnership) -> {
                        pushExpanded(
                                extractionDetails,
                                committedPrototype,
                                admittedCount,
                                access,
                                possibleTarget.direction(),
                                transferOwnership);
                        access.dataEnergistics$invokeOnPushPatternSuccess(patternDetails);
                        access.dataEnergistics$setRoundRobinIndex(nextRoundRobinIndex);
                        afterCommit.run();
                        return true;
                    }),
                    dispatchTarget,
                    rejections);
        }

        if (rejections.isEmpty()) {
            return rejected(CraftingDispatchStatus.NO_CAPACITY);
        }
        return CountedCraftingPreparation.rejected(rejections);
    }

    /**
     * Captures every ordinary AE2 side target without advancing the provider's round-robin cursor.
     *
     * <p>
     * Lock and dedicated-machine semantics that cannot be represented by a counted side target are exposed as one
     * conservative provider-level route. Blocking is represented per side: a target containing a published pattern
     * input reports zero capacity, while an eligible target reports the complete currently simulated batch capacity.
     * Offline, busy and unpublished patterns expose no route.
     * </p>
     */
    public static List<ProviderCapacitySnapshot> snapshotStandardCapacity(
                                                                          PatternProviderLogic logic,
                                                                          PatternProviderBatchAccess access,
                                                                          CraftingProviderId providerId,
                                                                          IPatternDetails patternDetails,
                                                                          KeyCounter[] prototype,
                                                                          long requestedCount,
                                                                          String patternIdentity,
                                                                          long publicationRevision,
                                                                          long capacityRevision,
                                                                          long captureTick) {
        validateRequestedCount(requestedCount);
        if (patternIdentity.isBlank()) {
            throw new IllegalArgumentException("Pattern provider capacity identity must not be blank");
        }
        if (publicationRevision < 0L || capacityRevision < 0L || captureTick < 0L) {
            throw new IllegalArgumentException("Pattern provider capacity revisions and capture tick must not be negative");
        }
        if (!access.dataEnergistics$getSendList().isEmpty() ||
                !access.dataEnergistics$getMainNode().isActive() ||
                !access.dataEnergistics$getPatterns().contains(patternDetails) ||
                logic.getCraftingLockedReason() != LockCraftingMode.NONE ||
                !patternDetails.supportsPushInputsToExternalInventory()) {
            return List.of();
        }

        var blockEntity = access.dataEnergistics$getHost().getBlockEntity();
        var level = blockEntity.getLevel();
        if (level == null) {
            return List.of();
        }
        var lockMode = logic.getConfigManager().getSetting(Settings.LOCK_CRAFTING_MODE);
        if (requiresSingleCraftPath(lockMode, false)) {
            return List.of(singleRouteSnapshot(
                    providerId,
                    patternIdentity,
                    publicationRevision,
                    capacityRevision,
                    captureTick));
        }

        ObjectArrayList<ProviderCapacitySnapshot> snapshots = new ObjectArrayList<>();
        boolean providerRouteCaptured = false;
        for (Direction direction : access.dataEnergistics$invokeGetActiveSides()) {
            var adjacentPosition = blockEntity.getBlockPos().relative(direction);
            var adjacentSide = direction.getOpposite();
            var craftingMachine = ICraftingMachine.of(level, adjacentPosition, adjacentSide);
            if (craftingMachine != null && craftingMachine.acceptsPlans()) {
                if (!providerRouteCaptured) {
                    snapshots.add(singleRouteSnapshot(
                            providerId,
                            patternIdentity,
                            publicationRevision,
                            capacityRevision,
                            captureTick));
                    providerRouteCaptured = true;
                }
                // Dedicated crafting-machine semantics remain a conservative one-craft provider route, but they do
                // not erase independent external-inventory sides published by this processing-pattern provider.
                continue;
            }

            PatternProviderTarget target = access.dataEnergistics$invokeFindAdapter(direction);
            if (target == null) {
                continue;
            }
            boolean blocked = isBlockedByTargetContents(
                    logic.isBlocking(),
                    target,
                    access.dataEnergistics$getPatternInputs());
            long capacity = blocked ? 0L : simulateCapacity(target, prototype, requestedCount);
            Observation observation = null;
            if (capacity > 0L) {
                observation = CraftingMachineCapacityAdapters.capture(
                        level,
                        adjacentPosition,
                        adjacentSide,
                        patternDetails,
                        prototype,
                        capacity);
                if (observation != null) {
                    capacity = Math.min(capacity, observation.remainingLogicalCrafts());
                }
            }
            snapshots.add(new ProviderCapacitySnapshot(
                    providerId,
                    targetFor(direction),
                    Optional.of(machineTargetId(
                            observation,
                            level,
                            adjacentPosition,
                            adjacentSide)),
                    patternIdentity,
                    publicationRevision,
                    capacityRevision,
                    captureTick,
                    ProviderRoutingMode.TARGETED,
                    new DispatchCapacity.Known(capacity),
                    new DispatchCapacity.Known(capacity)));
        }
        return List.copyOf(snapshots);
    }

    /**
     * Re-simulates and prepares exactly one side selected from a current capacity snapshot.
     */
    @Nullable
    public static CountedCraftingAdmission prepareStandardBatchForTarget(
                                                                         PatternProviderLogic logic,
                                                                         PatternProviderBatchAccess access,
                                                                         IPatternDetails patternDetails,
                                                                         KeyCounter[] prototype,
                                                                         long requestedCount,
                                                                         Runnable afterCommit,
                                                                         CraftingDispatchTarget target) {
        return prepareStandardBatchForTarget(
                logic,
                access,
                patternDetails,
                patternDetails,
                prototype,
                requestedCount,
                afterCommit,
                target);
    }

    /** Re-simulates one selected ordinary side while retaining a separately authorized input binding. */
    @Nullable
    public static CountedCraftingAdmission prepareStandardBatchForTarget(
                                                                         PatternProviderLogic logic,
                                                                         PatternProviderBatchAccess access,
                                                                         IPatternDetails patternDetails,
                                                                         IPatternDetails extractionDetails,
                                                                         KeyCounter[] prototype,
                                                                         long requestedCount,
                                                                         Runnable afterCommit,
                                                                         CraftingDispatchTarget target) {
        CountedCraftingPreparation preparation = prepareStandardBatch(
                logic,
                access,
                patternDetails,
                extractionDetails,
                prototype,
                requestedCount,
                afterCommit,
                candidate -> candidate.equals(target));
        return preparation.accepted() ? preparation.admission() : null;
    }

    /**
     * Selects the original one-craft provider path whenever batching could change lock or dedicated-machine semantics.
     */
    private static boolean requiresSingleCraftPath(LockCraftingMode lockMode, boolean dedicatedCraftingMachine) {
        return lockMode == LockCraftingMode.LOCK_UNTIL_RESULT ||
                lockMode == LockCraftingMode.LOCK_UNTIL_PULSE ||
                dedicatedCraftingMachine;
    }

    static long boundInputBatchLimit(boolean singleCraftPath, long requestedCount) {
        return singleCraftPath ? 1L : requestedCount;
    }

    /**
     * Applies AE2 Blocking semantics to one concrete external-inventory target.
     */
    private static boolean isBlockedByTargetContents(
                                                     boolean blocking,
                                                     PatternProviderTarget target,
                                                     Set<AEKey> patternInputs) {
        return blocking && target.containsPatternInput(patternInputs);
    }

    /** Creates a one-shot admission that preserves the provider's original single-craft routing. */
    public static CountedCraftingAdmission prepareSingle(
                                                         ICraftingProvider provider,
                                                         IPatternDetails patternDetails,
                                                         KeyCounter[] prototype,
                                                         long requestedCount) {
        validateRequestedCount(requestedCount);
        return admission(1L, prototype, inputs -> provider.pushPattern(patternDetails, inputs));
    }

    private static CountedCraftingPreparation prepareSingle(
                                                            ICraftingProvider provider,
                                                            IPatternDetails patternDetails,
                                                            KeyCounter[] prototype,
                                                            long requestedCount,
                                                            CraftingDispatchTargetAvailability targetAvailability) {
        CraftingDispatchTarget target = CraftingDispatchTarget.provider();
        if (!targetAvailability.canAttempt(target)) {
            return CountedCraftingPreparation.rejected(
                    CraftingDispatchRejection.targeted(CraftingDispatchStatus.NO_CAPACITY, target));
        }
        return CountedCraftingPreparation.accepted(
                prepareSingle(provider, patternDetails, prototype, requestedCount),
                target);
    }

    private static CountedCraftingPreparation rejected(CraftingDispatchStatus status) {
        return CountedCraftingPreparation.rejected(CraftingDispatchRejection.scoped(status));
    }

    private static ProviderCapacitySnapshot singleRouteSnapshot(
                                                                CraftingProviderId providerId,
                                                                String patternIdentity,
                                                                long publicationRevision,
                                                                long capacityRevision,
                                                                long captureTick) {
        return new ProviderCapacitySnapshot(
                providerId,
                CraftingDispatchTarget.provider(),
                Optional.empty(),
                patternIdentity,
                publicationRevision,
                capacityRevision,
                captureTick,
                ProviderRoutingMode.UNKNOWN,
                DispatchCapacity.Unknown.INSTANCE,
                new DispatchCapacity.Known(1L));
    }

    private static CraftingDispatchTarget targetFor(Direction direction) {
        return new CraftingDispatchTarget("side:" + direction.getName());
    }

    static CraftingDispatchTarget externalInventoryDispatchTarget(boolean singleCraftPath, Direction direction) {
        return singleCraftPath ? CraftingDispatchTarget.provider() : targetFor(direction);
    }

    private static MachineTargetId machineTargetId(
                                                   @Nullable Observation observation,
                                                   Level level,
                                                   BlockPos machinePosition,
                                                   Direction inputSide) {
        return observation != null && observation.scope() == CraftingMachineScope.BLOCK_ENTITY ?
                MachineTargetId.forBlockEntity(level.dimension(), machinePosition) :
                MachineTargetId.forBlockTarget(level.dimension(), machinePosition, inputSide);
    }

    static long simulateCapacity(PatternProviderTarget target, KeyCounter[] prototype, long requestedCount) {
        if (requestedCount <= 0L) {
            throw new IllegalArgumentException("requestedCount must be positive");
        }

        KeyCounter aggregated = aggregatePrototype(prototype);
        if (!aggregated.iterator().hasNext()) {
            return 1L;
        }
        long admitted = requestedCount;
        for (var entry : aggregated) {
            long perCraft = entry.getLongValue();
            long requestedAmount = Math.multiplyExact(perCraft, requestedCount);
            long inserted = target.insert(entry.getKey(), requestedAmount, Actionable.SIMULATE);
            if (inserted < 0L || inserted > requestedAmount) {
                throw new IllegalStateException("Pattern provider target returned an invalid simulated insertion amount");
            }
            admitted = Math.min(admitted, inserted / perCraft);
        }
        return admitted;
    }

    static void pushExpanded(
                             IPatternDetails inputEmissionDetails,
                             KeyCounter[] prototype,
                             long count,
                             PatternProviderBatchAccess access,
                             Direction direction,
                             Runnable transferOwnership) {
        List<GenericStack> expandedInputs = expandPatternInputs(inputEmissionDetails, prototype, count);
        List<GenericStack> sendList = access.dataEnergistics$getSendList();
        if (!sendList.isEmpty()) {
            throw new IllegalStateException("Pattern provider received another batch while inputs were pending");
        }

        access.dataEnergistics$setSendDirection(direction);
        sendList.addAll(expandedInputs);
        transferOwnership.run();
        if (!expandedInputs.isEmpty()) {
            access.dataEnergistics$alertPendingSendList();
        }
        access.dataEnergistics$invokeSendStacksOut();
    }

    static List<GenericStack> expandPatternInputs(
                                                  IPatternDetails patternDetails,
                                                  KeyCounter[] prototype,
                                                  long count) {
        if (count <= 0L) {
            throw new IllegalArgumentException("count must be positive");
        }

        PrototypeSnapshot prototypeSnapshot = copyAndAggregatePrototype(prototype);
        KeyCounter expectedInputs = prototypeSnapshot.aggregated();
        KeyCounter emittedInputs = new KeyCounter();
        ObjectArrayList<GenericStack> expandedInputs = new ObjectArrayList<>();
        KeyCounter[] perCraft = prototypeSnapshot.perCraft();
        patternDetails.pushInputsToExternalInventory(perCraft, (what, amount) -> {
            if (what == null) {
                throw new IllegalStateException("Pattern emitted a null input key");
            }
            if (amount <= 0L) {
                throw new IllegalStateException("Pattern emitted a non-positive input amount");
            }
            emittedInputs.set(what, Math.addExact(emittedInputs.get(what), amount));
            expandedInputs.add(new GenericStack(what, Math.multiplyExact(amount, count)));
        });
        if (!containsSameAmounts(expectedInputs, emittedInputs) ||
                !containsSameAmounts(emittedInputs, expectedInputs)) {
            throw new IllegalStateException("Pattern did not emit its complete per-craft input prototype");
        }
        return expandedInputs;
    }

    static CountedCraftingAdmission admission(
                                              long count,
                                              KeyCounter[] preparedPrototype,
                                              Function<KeyCounter[], Boolean> commit) {
        if (count <= 0L) {
            throw new IllegalArgumentException("count must be positive");
        }
        return new OneShotAdmission(count, preparedPrototype, (prototype, ignored) -> commit.apply(prototype));
    }

    static CountedCraftingAdmission ownershipAwareAdmission(
                                                            long count,
                                                            KeyCounter[] preparedPrototype,
                                                            BiFunction<KeyCounter[], Runnable, Boolean> commit) {
        if (count <= 0L) {
            throw new IllegalArgumentException("count must be positive");
        }
        return new OneShotAdmission(count, preparedPrototype, commit);
    }

    private static KeyCounter aggregatePrototype(KeyCounter[] prototype) {
        KeyCounter aggregated = new KeyCounter();
        for (int index = 0; index < prototype.length; index++) {
            KeyCounter counter = prototype[index];
            if (counter == null) {
                throw new IllegalArgumentException(
                        "Pattern-provider input prototype counter at index " + index + " must not be null");
            }
            for (var entry : counter) {
                AEKey key = entry.getKey();
                long amount = entry.getLongValue();
                if (amount < 0L) {
                    throw new IllegalArgumentException("prototype amounts must not be negative");
                }
                if (amount > 0L) {
                    aggregated.set(key, Math.addExact(aggregated.get(key), amount));
                }
            }
        }
        return aggregated;
    }

    /**
     * Copies and aggregates a prototype in one validated pass for expansion.
     *
     * <p>
     * The expansion path needs both representations. Building them together avoids traversing every prototype
     * counter twice while retaining the same live validation before pattern callbacks or target mutation.
     * </p>
     */
    private static PrototypeSnapshot copyAndAggregatePrototype(KeyCounter[] prototype) {
        KeyCounter aggregated = new KeyCounter();
        KeyCounter[] perCraft = new KeyCounter[prototype.length];
        for (int index = 0; index < prototype.length; index++) {
            KeyCounter source = prototype[index];
            if (source == null) {
                throw new IllegalArgumentException(
                        "Pattern-provider input prototype counter at index " + index + " must not be null");
            }
            KeyCounter copy = new KeyCounter();
            for (var entry : source) {
                AEKey key = entry.getKey();
                long amount = entry.getLongValue();
                if (amount < 0L) {
                    throw new IllegalArgumentException("prototype amounts must not be negative");
                }
                if (amount > 0L) {
                    aggregated.set(key, Math.addExact(aggregated.get(key), amount));
                    copy.add(key, amount);
                }
            }
            perCraft[index] = copy;
        }
        return new PrototypeSnapshot(aggregated, perCraft);
    }

    private static boolean containsSameAmounts(KeyCounter expected, KeyCounter actual) {
        for (var entry : expected) {
            if (actual.get(entry.getKey()) != entry.getLongValue()) {
                return false;
            }
        }
        return true;
    }

    private static void validateRequestedCount(long requestedCount) {
        if (requestedCount <= 0L) {
            throw new IllegalArgumentException("requestedCount must be positive");
        }
    }

    private static <T> int rearrangeRoundRobin(List<T> targets, int roundRobinIndex) {
        if (targets.isEmpty()) {
            return roundRobinIndex;
        }
        int start = Math.floorMod(roundRobinIndex, targets.size());
        for (int index = 0; index < start; index++) {
            targets.add(targets.get(index));
        }
        targets.subList(0, start).clear();
        return start;
    }

    static int nextRoundRobinIndex(int normalizedRoundRobin, int acceptedTargetOffset) {
        if (normalizedRoundRobin < 0 || acceptedTargetOffset < 0) {
            throw new IllegalArgumentException("Round-robin indexes must not be negative");
        }
        return Math.addExact(Math.addExact(normalizedRoundRobin, acceptedTargetOffset), 1);
    }

    private record PushTarget(Direction direction, PatternProviderTarget target) {}

    /** Holds the validated aggregate and per-craft copy produced during one prototype traversal. */
    private record PrototypeSnapshot(KeyCounter aggregated, KeyCounter[] perCraft) {}

    private static final class OneShotAdmission implements CountedCraftingAdmission {

        private final long count;
        private final KeyCounter[] preparedPrototype;
        private final BiFunction<KeyCounter[], Runnable, Boolean> commit;
        private boolean attempted;
        private boolean transferredInputOwnership;

        private OneShotAdmission(
                                 long count,
                                 KeyCounter[] preparedPrototype,
                                 BiFunction<KeyCounter[], Runnable, Boolean> commit) {
            this.count = count;
            this.preparedPrototype = preparedPrototype;
            this.commit = commit;
        }

        @Override
        public long count() {
            return this.count;
        }

        @Override
        public boolean hasTransferredInputOwnership() {
            return this.transferredInputOwnership;
        }

        @Override
        public boolean commit(KeyCounter[] prototype) {
            if (prototype != this.preparedPrototype) {
                throw new IllegalArgumentException("Admission must be committed with its prepared prototype");
            }
            if (this.attempted) {
                throw new IllegalStateException("Admission has already been committed");
            }
            this.attempted = true;
            return this.commit.apply(prototype, this::transferInputOwnership);
        }

        private void transferInputOwnership() {
            this.transferredInputOwnership = true;
        }
    }
}

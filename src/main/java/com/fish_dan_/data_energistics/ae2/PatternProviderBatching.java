package com.fish_dan_.data_energistics.ae2;

import com.fish_dan_.data_energistics.accessor.PatternProviderBatchAccess;
import com.fish_dan_.data_energistics.api.crafting.dispatch.CountedCraftingAdmission;
import com.fish_dan_.data_energistics.common.crafting.trinity.dispatch.commit.CountedCraftingPreparation;
import com.fish_dan_.data_energistics.common.crafting.trinity.dispatch.model.CraftingDispatchRejection;
import com.fish_dan_.data_energistics.common.crafting.trinity.dispatch.model.CraftingDispatchStatus;
import com.fish_dan_.data_energistics.common.crafting.trinity.dispatch.model.CraftingDispatchTarget;
import com.fish_dan_.data_energistics.common.crafting.trinity.dispatch.model.CraftingDispatchTargetAvailability;
import com.fish_dan_.data_energistics.common.crafting.trinity.dispatch.model.CraftingProviderId;
import com.fish_dan_.data_energistics.common.crafting.trinity.dispatch.model.DispatchCapacity;
import com.fish_dan_.data_energistics.common.crafting.trinity.dispatch.model.ProviderCapacitySnapshot;
import com.fish_dan_.data_energistics.common.crafting.trinity.dispatch.model.ProviderRoutingMode;

import net.minecraft.core.Direction;

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
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
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
     * Prepares the standard AE2 target selected by the current round-robin cursor.
     *
     * <p>
     * Blocking mode, result/pulse locking and dedicated crafting machines deliberately retain AE2's one-craft
     * {@link ICraftingProvider#pushPattern} behavior.
     * </p>
     */
    @Nullable
    public static CountedCraftingAdmission prepareStandardBatch(
                                                                PatternProviderLogic logic,
                                                                PatternProviderBatchAccess access,
                                                                IPatternDetails patternDetails,
                                                                KeyCounter[] prototype,
                                                                long requestedCount,
                                                                Runnable afterCommit) {
        return prepareStandardBatch(
                logic,
                access,
                patternDetails,
                prototype,
                requestedCount,
                afterCommit,
                CraftingDispatchTargetAvailability.all()).admission();
    }

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
        validatePreparation(patternDetails, prototype, requestedCount, afterCommit);
        if (logic == null || access == null) {
            throw new IllegalArgumentException("Pattern provider logic and batch access must not be null");
        }
        if (targetAvailability == null) {
            throw new IllegalArgumentException("Crafting dispatch target availability must not be null");
        }

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
        var blockEntity = access.dataEnergistics$getHost().getBlockEntity();
        var level = blockEntity.getLevel();
        if (level == null) {
            return rejected(CraftingDispatchStatus.OFFLINE);
        }

        var possibleTargets = new ArrayList<PushTarget>();
        for (Direction direction : access.dataEnergistics$invokeGetActiveSides()) {
            var adjacentPosition = blockEntity.getBlockPos().relative(direction);
            var adjacentSide = direction.getOpposite();
            var craftingMachine = ICraftingMachine.of(level, adjacentPosition, adjacentSide);
            boolean dedicatedCraftingMachine = craftingMachine != null && craftingMachine.acceptsPlans();
            if (selectsSingleCraftPath(false, LockCraftingMode.NONE, dedicatedCraftingMachine)) {
                return prepareSingle(
                        logic,
                        patternDetails,
                        prototype,
                        requestedCount,
                        targetAvailability);
            }

            var target = access.dataEnergistics$invokeFindAdapter(direction);
            if (target != null) {
                possibleTargets.add(new PushTarget(direction, target));
            }
        }

        if (!patternDetails.supportsPushInputsToExternalInventory()) {
            return rejected(CraftingDispatchStatus.REJECTED);
        }

        boolean singleCraft = selectsSingleCraftPath(logic.isBlocking(), lockMode, false);
        List<CraftingDispatchRejection> rejections = new ArrayList<>();
        int normalizedRoundRobin = rearrangeRoundRobin(
                possibleTargets,
                access.dataEnergistics$getRoundRobinIndex());
        for (int targetOffset = 0; targetOffset < possibleTargets.size(); targetOffset++) {
            PushTarget possibleTarget = possibleTargets.get(targetOffset);
            CraftingDispatchTarget dispatchTarget = targetFor(possibleTarget.direction());
            if (!targetAvailability.canAttempt(dispatchTarget)) {
                continue;
            }
            if (logic.isBlocking() &&
                    possibleTarget.target().containsPatternInput(access.dataEnergistics$getPatternInputs())) {
                rejections.add(CraftingDispatchRejection.targeted(
                        CraftingDispatchStatus.BLOCKED,
                        dispatchTarget));
                continue;
            }

            long count;
            if (singleCraft) {
                count = acceptsSingleCraft(possibleTarget.target(), prototype) ? 1L : 0L;
            } else {
                count = simulateCapacity(possibleTarget.target(), prototype, requestedCount);
            }
            if (count <= 0L) {
                rejections.add(CraftingDispatchRejection.targeted(
                        CraftingDispatchStatus.NO_CAPACITY,
                        dispatchTarget));
                continue;
            }
            int nextRoundRobinIndex = nextRoundRobinIndex(normalizedRoundRobin, targetOffset);

            return CountedCraftingPreparation.accepted(
                    ownershipAwareAdmission(count, prototype, (committedPrototype, transferOwnership) -> {
                        pushExpanded(
                                patternDetails,
                                committedPrototype,
                                count,
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
     * Routes whose blocking, lock or dedicated-machine semantics cannot be represented by a counted side target
     * are exposed as one conservative provider-level route. Offline, busy and unpublished patterns expose no route.
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
        validatePreparation(patternDetails, prototype, requestedCount, () -> {});
        if (logic == null || access == null || providerId == null) {
            throw new IllegalArgumentException("Pattern provider capacity context must not be null");
        }
        if (patternIdentity == null || patternIdentity.isBlank()) {
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
        if (selectsSingleCraftPath(logic.isBlocking(), lockMode, false)) {
            return List.of(singleRouteSnapshot(
                    providerId,
                    patternIdentity,
                    publicationRevision,
                    capacityRevision,
                    captureTick));
        }

        ArrayList<ProviderCapacitySnapshot> snapshots = new ArrayList<>();
        for (Direction direction : access.dataEnergistics$invokeGetActiveSides()) {
            var adjacentPosition = blockEntity.getBlockPos().relative(direction);
            var adjacentSide = direction.getOpposite();
            var craftingMachine = ICraftingMachine.of(level, adjacentPosition, adjacentSide);
            if (craftingMachine != null && craftingMachine.acceptsPlans()) {
                return List.of(singleRouteSnapshot(
                        providerId,
                        patternIdentity,
                        publicationRevision,
                        capacityRevision,
                        captureTick));
            }

            PatternProviderTarget target = access.dataEnergistics$invokeFindAdapter(direction);
            if (target == null) {
                continue;
            }
            long capacity = simulateCapacity(target, prototype, requestedCount);
            snapshots.add(new ProviderCapacitySnapshot(
                    providerId,
                    targetFor(direction),
                    Optional.empty(),
                    patternIdentity,
                    publicationRevision,
                    capacityRevision,
                    captureTick,
                    ProviderRoutingMode.TARGETED,
                    new DispatchCapacity.Known(capacity),
                    new DispatchCapacity.Known(requestedCount)));
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
        if (target == null) {
            throw new IllegalArgumentException("Pattern provider dispatch target must not be null");
        }
        CountedCraftingPreparation preparation = prepareStandardBatch(
                logic,
                access,
                patternDetails,
                prototype,
                requestedCount,
                afterCommit,
                candidate -> candidate.equals(target));
        return preparation.accepted() ? preparation.admission() : null;
    }

    /**
     * Selects the original one-craft provider path whenever batching could change AE2 blocking or lock semantics.
     */
    static boolean selectsSingleCraftPath(boolean blocking,
                                          LockCraftingMode lockMode,
                                          boolean dedicatedCraftingMachine) {
        if (lockMode == null) {
            throw new IllegalArgumentException("Pattern-provider lock mode must not be null");
        }
        return blocking ||
                lockMode == LockCraftingMode.LOCK_UNTIL_RESULT ||
                lockMode == LockCraftingMode.LOCK_UNTIL_PULSE ||
                dedicatedCraftingMachine;
    }

    /** Creates a one-shot admission that preserves the provider's original single-craft routing. */
    public static CountedCraftingAdmission prepareSingle(
                                                         ICraftingProvider provider,
                                                         IPatternDetails patternDetails,
                                                         KeyCounter[] prototype,
                                                         long requestedCount) {
        validatePreparation(patternDetails, prototype, requestedCount, () -> {});
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

    private static boolean acceptsSingleCraft(PatternProviderTarget target, KeyCounter[] prototype) {
        if (target == null || prototype == null) {
            throw new IllegalArgumentException(
                    "Pattern provider capacity target and input prototype must not be null");
        }
        for (int index = 0; index < prototype.length; index++) {
            KeyCounter inputs = prototype[index];
            if (inputs == null) {
                throw new IllegalArgumentException(
                        "Pattern-provider input prototype counter at index " + index + " must not be null");
            }
            for (var entry : inputs) {
                long amount = entry.getLongValue();
                if (amount < 0L) {
                    throw new IllegalArgumentException("prototype amounts must not be negative");
                }
                if (amount == 0L) {
                    continue;
                }
                long inserted = target.insert(entry.getKey(), amount, Actionable.SIMULATE);
                if (inserted < 0L || inserted > amount) {
                    throw new IllegalStateException(
                            "Pattern provider target returned an invalid simulated insertion amount");
                }
                if (inserted == 0L) {
                    return false;
                }
            }
        }
        return true;
    }

    static long simulateCapacity(PatternProviderTarget target, KeyCounter[] prototype, long requestedCount) {
        if (target == null || prototype == null) {
            throw new IllegalArgumentException(
                    "Pattern provider capacity target and input prototype must not be null");
        }
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
                             IPatternDetails patternDetails,
                             KeyCounter[] prototype,
                             long count,
                             PatternProviderBatchAccess access,
                             Direction direction,
                             Runnable transferOwnership) {
        if (patternDetails == null) {
            throw new IllegalArgumentException(
                    "Pattern details must not be null when expanding a pattern-provider batch");
        }
        if (access == null) {
            throw new IllegalArgumentException("Pattern-provider batch access must not be null");
        }
        if (direction == null) {
            throw new IllegalArgumentException("Pattern-provider batch direction must not be null");
        }
        if (transferOwnership == null) {
            throw new IllegalArgumentException("Pattern-provider ownership callback must not be null");
        }
        List<GenericStack> expandedInputs = expandPatternInputs(patternDetails, prototype, count);
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

        KeyCounter expectedInputs = aggregatePrototype(prototype);
        KeyCounter emittedInputs = new KeyCounter();
        ArrayList<GenericStack> expandedInputs = new ArrayList<>();
        KeyCounter[] perCraft = scalePrototype(prototype, 1L);
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
        return List.copyOf(expandedInputs);
    }

    static KeyCounter[] scalePrototype(KeyCounter[] prototype, long count) {
        if (prototype == null) {
            throw new IllegalArgumentException(
                    "Pattern-provider input prototype must not be null when scaling a batch");
        }
        if (count <= 0L) {
            throw new IllegalArgumentException("count must be positive");
        }

        KeyCounter[] expanded = new KeyCounter[prototype.length];
        for (int index = 0; index < prototype.length; index++) {
            KeyCounter source = prototype[index];
            if (source == null) {
                throw new IllegalArgumentException(
                        "Pattern-provider input prototype counter at index " + index + " must not be null");
            }
            KeyCounter scaled = new KeyCounter();
            for (var entry : source) {
                long amount = entry.getLongValue();
                if (amount < 0L) {
                    throw new IllegalArgumentException("prototype amounts must not be negative");
                }
                if (amount > 0L) {
                    scaled.add(entry.getKey(), Math.multiplyExact(amount, count));
                }
            }
            expanded[index] = scaled;
        }
        return expanded;
    }

    static CountedCraftingAdmission admission(
                                              long count,
                                              KeyCounter[] preparedPrototype,
                                              Function<KeyCounter[], Boolean> commit) {
        if (count <= 0L) {
            throw new IllegalArgumentException("count must be positive");
        }
        if (preparedPrototype == null) {
            throw new IllegalArgumentException("Prepared pattern-provider input prototype must not be null");
        }
        if (commit == null) {
            throw new IllegalArgumentException("Pattern-provider batch commit must not be null");
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
        if (preparedPrototype == null) {
            throw new IllegalArgumentException("Prepared pattern-provider input prototype must not be null");
        }
        if (commit == null) {
            throw new IllegalArgumentException("Ownership-aware pattern-provider batch commit must not be null");
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

    private static boolean containsSameAmounts(KeyCounter expected, KeyCounter actual) {
        for (var entry : expected) {
            if (actual.get(entry.getKey()) != entry.getLongValue()) {
                return false;
            }
        }
        return true;
    }

    private static void validatePreparation(
                                            IPatternDetails patternDetails,
                                            KeyCounter[] prototype,
                                            long requestedCount,
                                            Runnable afterCommit) {
        if (patternDetails == null || prototype == null) {
            throw new IllegalArgumentException(
                    "Pattern details and input prototype must not be null when preparing a pattern-provider batch");
        }
        if (afterCommit == null) {
            throw new IllegalArgumentException("Pattern-provider post-commit action must not be null");
        }
        if (requestedCount <= 0L) {
            throw new IllegalArgumentException("requestedCount must be positive");
        }
    }

    private static <T> int rearrangeRoundRobin(ArrayList<T> targets, int roundRobinIndex) {
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

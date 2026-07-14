package com.fish_dan_.data_energistics.ae2;

import com.fish_dan_.data_energistics.accessor.PatternProviderBatchAccess;
import com.fish_dan_.data_energistics.common.crafting.trinity.CountedCraftingAdmission;

import net.minecraft.core.Direction;

import appeng.api.config.Actionable;
import appeng.api.config.LockCraftingMode;
import appeng.api.config.Settings;
import appeng.api.crafting.IPatternDetails;
import appeng.api.implementations.blockentities.ICraftingMachine;
import appeng.api.networking.crafting.ICraftingProvider;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.KeyCounter;
import appeng.helpers.patternprovider.PatternProviderLogic;
import appeng.helpers.patternprovider.PatternProviderTarget;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Objects;
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
        validatePreparation(patternDetails, prototype, requestedCount, afterCommit);

        if (!access.dataEnergistics$getSendList().isEmpty() || !access.dataEnergistics$getMainNode().isActive() || !access.dataEnergistics$getPatterns().contains(patternDetails) || logic.getCraftingLockedReason() != LockCraftingMode.NONE) {
            return null;
        }

        var lockMode = logic.getConfigManager().getSetting(Settings.LOCK_CRAFTING_MODE);
        if (logic.isBlocking() || lockMode == LockCraftingMode.LOCK_UNTIL_RESULT || lockMode == LockCraftingMode.LOCK_UNTIL_PULSE) {
            return prepareSingle(logic, patternDetails, prototype, requestedCount);
        }

        var blockEntity = access.dataEnergistics$getHost().getBlockEntity();
        var level = blockEntity.getLevel();
        if (level == null) {
            return null;
        }

        var possibleTargets = new ArrayList<PushTarget>();
        for (Direction direction : access.dataEnergistics$invokeGetActiveSides()) {
            var adjacentPosition = blockEntity.getBlockPos().relative(direction);
            var adjacentSide = direction.getOpposite();
            var craftingMachine = ICraftingMachine.of(level, adjacentPosition, adjacentSide);
            if (craftingMachine != null && craftingMachine.acceptsPlans()) {
                return prepareSingle(logic, patternDetails, prototype, requestedCount);
            }

            var target = access.dataEnergistics$invokeFindAdapter(direction);
            if (target != null) {
                possibleTargets.add(new PushTarget(direction, target));
            }
        }

        if (!patternDetails.supportsPushInputsToExternalInventory()) {
            return null;
        }

        int normalizedRoundRobin = rearrangeRoundRobin(
                possibleTargets,
                access.dataEnergistics$getRoundRobinIndex());
        for (int targetOffset = 0; targetOffset < possibleTargets.size(); targetOffset++) {
            PushTarget possibleTarget = possibleTargets.get(targetOffset);
            long count = simulateCapacity(possibleTarget.target(), prototype, requestedCount);
            if (count <= 0L) {
                continue;
            }
            int nextRoundRobinIndex = nextRoundRobinIndex(normalizedRoundRobin, targetOffset);

            return ownershipAwareAdmission(count, prototype, (committedPrototype, transferOwnership) -> {
                pushExpanded(
                        patternDetails,
                        committedPrototype,
                        count,
                        possibleTarget.target(),
                        transferOwnership,
                        access::dataEnergistics$invokeAddToSendList);
                transferOwnership.run();
                access.dataEnergistics$invokeOnPushPatternSuccess(patternDetails);
                access.dataEnergistics$setSendDirection(possibleTarget.direction());
                access.dataEnergistics$invokeSendStacksOut();
                access.dataEnergistics$setRoundRobinIndex(nextRoundRobinIndex);
                afterCommit.run();
                return true;
            });
        }

        return null;
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

    static long simulateCapacity(PatternProviderTarget target, KeyCounter[] prototype, long requestedCount) {
        Objects.requireNonNull(target, "target");
        Objects.requireNonNull(prototype, "prototype");
        if (requestedCount <= 0L) {
            throw new IllegalArgumentException("requestedCount must be positive");
        }

        KeyCounter aggregated = aggregatePrototype(prototype);
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
                             PatternProviderTarget target,
                             Runnable transferOwnership,
                             IPatternDetails.PatternInputSink remainderSink) {
        Objects.requireNonNull(patternDetails, "patternDetails");
        Objects.requireNonNull(target, "target");
        Objects.requireNonNull(transferOwnership, "transferOwnership");
        Objects.requireNonNull(remainderSink, "remainderSink");
        KeyCounter[] expanded = scalePrototype(prototype, count);
        patternDetails.pushInputsToExternalInventory(expanded, (what, amount) -> {
            transferOwnership.run();
            long inserted = target.insert(what, amount, Actionable.MODULATE);
            if (inserted < 0L || inserted > amount) {
                throw new IllegalStateException("Pattern provider target returned an invalid insertion amount");
            }
            if (inserted < amount) {
                remainderSink.pushInput(what, amount - inserted);
            }
        });
    }

    static KeyCounter[] scalePrototype(KeyCounter[] prototype, long count) {
        Objects.requireNonNull(prototype, "prototype");
        if (count <= 0L) {
            throw new IllegalArgumentException("count must be positive");
        }

        KeyCounter[] expanded = new KeyCounter[prototype.length];
        for (int index = 0; index < prototype.length; index++) {
            KeyCounter source = Objects.requireNonNull(prototype[index], "prototype counter");
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
        Objects.requireNonNull(preparedPrototype, "preparedPrototype");
        Objects.requireNonNull(commit, "commit");
        return new OneShotAdmission(count, preparedPrototype, (prototype, ignored) -> commit.apply(prototype));
    }

    static CountedCraftingAdmission ownershipAwareAdmission(
                                                            long count,
                                                            KeyCounter[] preparedPrototype,
                                                            BiFunction<KeyCounter[], Runnable, Boolean> commit) {
        if (count <= 0L) {
            throw new IllegalArgumentException("count must be positive");
        }
        Objects.requireNonNull(preparedPrototype, "preparedPrototype");
        Objects.requireNonNull(commit, "commit");
        return new OneShotAdmission(count, preparedPrototype, commit);
    }

    private static KeyCounter aggregatePrototype(KeyCounter[] prototype) {
        KeyCounter aggregated = new KeyCounter();
        for (KeyCounter counter : prototype) {
            for (var entry : Objects.requireNonNull(counter, "prototype counter")) {
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

    private static void validatePreparation(
                                            IPatternDetails patternDetails,
                                            KeyCounter[] prototype,
                                            long requestedCount,
                                            Runnable afterCommit) {
        Objects.requireNonNull(patternDetails, "patternDetails");
        Objects.requireNonNull(prototype, "prototype");
        Objects.requireNonNull(afterCommit, "afterCommit");
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

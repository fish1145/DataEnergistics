package com.fish_dan_.data_energistics.common.crafting.trinity.execution.cpu;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.util.Mth;

import appeng.api.stacks.AEKey;
import appeng.api.stacks.AEKeyType;
import appeng.api.stacks.AEKeyTypes;
import it.unimi.dsi.fastutil.objects.Reference2LongMap;
import it.unimi.dsi.fastutil.objects.Reference2LongOpenHashMap;

import java.math.BigInteger;
import java.util.Map;

/**
 * Progress tracker for a Trinity Data Core virtual CPU job.
 *
 * <p>
 * AE2's tracker keeps mutation methods package-private, so this local tracker preserves the same visible progress
 * contract while remaining accessible to the Trinity Data Core runtime.
 */
final class TrinityDataCoreElapsedTimeTracker {

    private static final String ELAPSED_TIME_TAG = "elapsed_time";
    private static final String STARTED_WORK_TAG = "started_work";
    private static final String COMPLETED_WORK_TAG = "completed_work";
    private static final String PLAN_BASELINE_TAG = "plan_baseline";

    private long lastTime = System.nanoTime();
    private long elapsedTime;
    private final Reference2LongMap<AEKeyType> startedWorkByType = new Reference2LongOpenHashMap<>(
            AEKeyTypes.getAll().size());
    private final Reference2LongMap<AEKeyType> completedWorkByType = new Reference2LongOpenHashMap<>(
            AEKeyTypes.getAll().size());
    private boolean planBaseline;

    TrinityDataCoreElapsedTimeTracker() {}

    TrinityDataCoreElapsedTimeTracker(CompoundTag data) {
        this.elapsedTime = data.getLong(ELAPSED_TIME_TAG);
        readLongByTypeMap(data.getCompound(STARTED_WORK_TAG), this.startedWorkByType);
        readLongByTypeMap(data.getCompound(COMPLETED_WORK_TAG), this.completedWorkByType);
        this.planBaseline = data.getBoolean(PLAN_BASELINE_TAG);
    }

    /**
     * Records work that has been scheduled for this job.
     *
     * @param amount  amount scheduled
     * @param keyType AE key type for unit conversion
     */
    void addMaxItems(long amount, AEKeyType keyType) {
        if (amount < 0L) {
            throw new IllegalArgumentException("Tracked Trinity work must be non-negative");
        }
        updateTime();
        this.startedWorkByType.mergeLong(keyType, amount, this::saturatedSum);
    }

    /**
     * Installs the complete output baseline of a newly accepted compact plan exactly once.
     *
     * @param plannedOutputs complete aggregate pattern outputs
     */
    void initializePlanBaseline(Map<AEKey, BigInteger> plannedOutputs) {
        if (this.planBaseline || !this.startedWorkByType.isEmpty()) {
            throw new IllegalStateException("A Trinity plan progress baseline may only be initialized once");
        }
        mergeBigIntegerWork(plannedOutputs, this.startedWorkByType);
        this.planBaseline = true;
    }

    /**
     * Migrates a pre-baseline active save. Its existing started work is the already-dispatched prefix, while the
     * execution cursor supplies the exact undispatched suffix.
     *
     * @param pendingOutputs exact undispatched outputs restored with the execution cursor
     */
    void restorePlanBaseline(Map<AEKey, Long> pendingOutputs) {
        if (this.planBaseline) {
            return;
        }
        mergeLongWork(pendingOutputs, this.startedWorkByType);
        this.planBaseline = true;
    }

    /**
     * Completes the dispatched prefix of a legacy tracker with a newly planned exact remaining suffix.
     *
     * @param replacement complete outputs of the replacement remaining plan
     */
    void installReplacementAfterLegacyRestore(Map<AEKey, BigInteger> replacement) {
        if (this.planBaseline) {
            throw new IllegalStateException("An established Trinity progress baseline cannot be installed twice");
        }
        mergeBigIntegerWork(replacement, this.startedWorkByType);
        this.planBaseline = true;
    }

    /**
     * @return whether this tracker has a complete, stable plan baseline
     */
    boolean hasPlanBaseline() {
        return this.planBaseline;
    }

    /**
     * Replaces only the undispatched portion of an established plan baseline after deterministic replanning.
     *
     * @param previousPending outputs removed with the old remaining plan
     * @param replacement     complete outputs of the replacement remaining plan
     */
    void replacePendingPlan(Map<AEKey, Long> previousPending, Map<AEKey, BigInteger> replacement) {
        if (!this.planBaseline) {
            throw new IllegalStateException("A Trinity replacement requires an established progress baseline");
        }
        Reference2LongMap<AEKeyType> updated = new Reference2LongOpenHashMap<>(this.startedWorkByType);
        subtractLongWork(previousPending, updated);
        mergeBigIntegerWork(replacement, updated);
        this.startedWorkByType.clear();
        this.startedWorkByType.putAll(updated);
        updateTime();
    }

    /**
     * Records work that has been completed for this job.
     *
     * @param amount  amount completed
     * @param keyType AE key type for unit conversion
     */
    void decrementItems(long amount, AEKeyType keyType) {
        if (amount < 0L) {
            throw new IllegalArgumentException("Completed Trinity work must be non-negative");
        }
        updateTime();
        this.completedWorkByType.mergeLong(keyType, amount, this::saturatedSum);
    }

    /**
     * @return elapsed nanoseconds while the job has pending work
     */
    long elapsedTimeNanos() {
        boolean allDone = true;
        for (AEKeyType keyType : AEKeyTypes.getAll()) {
            if (this.completedWorkByType.getLong(keyType) < this.startedWorkByType.getLong(keyType)) {
                allDone = false;
                break;
            }
        }

        if (allDone) {
            return this.elapsedTime;
        }
        return this.elapsedTime + System.nanoTime() - this.lastTime;
    }

    /**
     * @return AE2-compatible fixed GUI scale; the represented ratio comes from the complete tracked baseline
     */
    long startItemCount() {
        return Integer.MAX_VALUE;
    }

    /**
     * @return remaining AE2-compatible GUI scale projected from the exact tracked ratio
     */
    long remainingItemCount() {
        return (long) (Integer.MAX_VALUE - (double) progress() * Integer.MAX_VALUE);
    }

    /**
     * @return serialized tracker state
     */
    CompoundTag writeToTag() {
        CompoundTag data = new CompoundTag();
        data.putLong(ELAPSED_TIME_TAG, this.elapsedTime);
        data.put(STARTED_WORK_TAG, writeLongByTypeMap(this.startedWorkByType));
        data.put(COMPLETED_WORK_TAG, writeLongByTypeMap(this.completedWorkByType));
        data.putBoolean(PLAN_BASELINE_TAG, this.planBaseline);
        return data;
    }

    private float progress() {
        double startedUnits = 0.0D;
        double completedUnits = 0.0D;
        for (AEKeyType keyType : AEKeyTypes.getAll()) {
            startedUnits += this.startedWorkByType.getLong(keyType) / (double) keyType.getAmountPerUnit();
            completedUnits += this.completedWorkByType.getLong(keyType) / (double) keyType.getAmountPerUnit();
        }
        if (startedUnits <= 0.0D) {
            return 0.0F;
        }
        return Mth.clamp((float) (completedUnits / startedUnits), 0.0F, 1.0F);
    }

    private void mergeBigIntegerWork(Map<AEKey, BigInteger> work,
                                     Reference2LongMap<AEKeyType> destination) {
        work.forEach((key, amount) -> {
            if (amount.signum() <= 0) {
                throw new IllegalArgumentException("Trinity plan work must contain positive keyed amounts");
            }
            destination.mergeLong(key.getType(), amount.longValueExact(), this::saturatedSum);
        });
    }

    private void mergeLongWork(Map<AEKey, Long> work,
                               Reference2LongMap<AEKeyType> destination) {
        work.forEach((key, amount) -> {
            if (amount <= 0L) {
                throw new IllegalArgumentException("Trinity pending work must contain positive keyed amounts");
            }
            destination.mergeLong(key.getType(), amount, this::saturatedSum);
        });
    }

    private static void subtractLongWork(Map<AEKey, Long> work,
                                         Reference2LongMap<AEKeyType> destination) {
        Reference2LongMap<AEKeyType> removal = new Reference2LongOpenHashMap<>();
        work.forEach((key, amount) -> {
            if (amount <= 0L) {
                throw new IllegalArgumentException("Trinity pending work must contain positive keyed amounts");
            }
            removal.mergeLong(key.getType(), amount, Math::addExact);
        });
        removal.reference2LongEntrySet().forEach(entry -> {
            long current = destination.getLong(entry.getKey());
            if (current < entry.getLongValue()) {
                throw new IllegalStateException("A Trinity replacement cannot remove more work than its baseline");
            }
            destination.put(entry.getKey(), current - entry.getLongValue());
        });
    }

    private void updateTime() {
        long currentTime = System.nanoTime();
        this.elapsedTime += currentTime - this.lastTime;
        this.lastTime = currentTime;
    }

    private long saturatedSum(long left, long right) {
        long result = left + right;
        return result < 0 ? Long.MAX_VALUE : result;
    }

    private static void readLongByTypeMap(CompoundTag tag, Reference2LongMap<AEKeyType> output) {
        for (AEKeyType keyType : AEKeyTypes.getAll()) {
            output.put(keyType, tag.getLong(keyType.getId().toString()));
        }
    }

    private static CompoundTag writeLongByTypeMap(Reference2LongMap<AEKeyType> input) {
        CompoundTag result = new CompoundTag();
        for (Reference2LongMap.Entry<AEKeyType> entry : input.reference2LongEntrySet()) {
            result.putLong(entry.getKey().getId().toString(), entry.getLongValue());
        }
        return result;
    }
}

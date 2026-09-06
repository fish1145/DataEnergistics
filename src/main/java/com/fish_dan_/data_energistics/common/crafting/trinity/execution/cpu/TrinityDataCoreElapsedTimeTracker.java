package com.fish_dan_.data_energistics.common.crafting.trinity.execution.cpu;

import appeng.api.stacks.AEKey;
import appeng.api.stacks.AEKeyType;
import appeng.api.stacks.AEKeyTypes;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.util.Mth;

import it.unimi.dsi.fastutil.objects.Reference2ObjectMap;
import it.unimi.dsi.fastutil.objects.Reference2ObjectOpenHashMap;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.math.MathContext;
import java.util.Map;

/**
 * Progress tracker for a Trinity Data Core virtual CPU job.
 *
 * <p>
 * AE2's tracker keeps mutation methods package-private, so this local tracker preserves the same visible progress
 * contract while remaining accessible to the Trinity Data Core runtime.
 */
final class TrinityDataCoreElapsedTimeTracker {

    private static final int MAX_BIG_INTEGER_BYTES = 512;

    private static final String ELAPSED_TIME_TAG = "elapsed_time";
    private static final String STARTED_WORK_TAG = "started_work";
    private static final String COMPLETED_WORK_TAG = "completed_work";
    private static final String PLAN_BASELINE_TAG = "plan_baseline";

    private long lastTime = System.nanoTime();
    private long elapsedTime;
    private final Reference2ObjectMap<AEKeyType, BigInteger> startedWorkByType = new Reference2ObjectOpenHashMap<>(
            AEKeyTypes.getAll().size());
    private final Reference2ObjectMap<AEKeyType, BigInteger> completedWorkByType = new Reference2ObjectOpenHashMap<>(
            AEKeyTypes.getAll().size());
    private boolean planBaseline;

    TrinityDataCoreElapsedTimeTracker() {}

    TrinityDataCoreElapsedTimeTracker(CompoundTag data) {
        this.elapsedTime = data.getLong(ELAPSED_TIME_TAG);
        readWorkByTypeMap(data.getCompound(STARTED_WORK_TAG), this.startedWorkByType);
        readWorkByTypeMap(data.getCompound(COMPLETED_WORK_TAG), this.completedWorkByType);
        this.planBaseline = data.getBoolean(PLAN_BASELINE_TAG);
    }

    /**
     * Records work that has been scheduled for this job.
     *
     * @param amount  amount scheduled
     * @param keyType AE key type for unit conversion
     */
    void addMaxItems(long amount, AEKeyType keyType) {
        addMaxItems(BigInteger.valueOf(amount), keyType);
    }

    void addMaxItems(BigInteger amount, AEKeyType keyType) {
        if (amount.signum() < 0) {
            throw new IllegalArgumentException("Tracked Trinity work must be non-negative");
        }
        updateTime();
        this.startedWorkByType.merge(keyType, amount, BigInteger::add);
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
     * Migrates a 3.1.3 active save. Its existing started work is the already-dispatched prefix, while the
     * execution cursor supplies the exact undispatched suffix.
     *
     * @param pendingOutputs exact undispatched outputs restored with the execution cursor
     */
    void restorePlanBaseline(Map<AEKey, BigInteger> pendingOutputs) {
        if (this.planBaseline) {
            return;
        }
        mergeBigIntegerWork(pendingOutputs, this.startedWorkByType);
        this.planBaseline = true;
    }

    /**
     * Replaces only the undispatched portion of an established plan baseline after deterministic replanning.
     *
     * @param previousPending outputs removed with the old remaining plan
     * @param replacement     complete outputs of the replacement remaining plan
     */
    void replacePendingPlan(Map<AEKey, BigInteger> previousPending, Map<AEKey, BigInteger> replacement) {
        if (!this.planBaseline) {
            throw new IllegalStateException("A Trinity replacement requires an established progress baseline");
        }
        Reference2ObjectMap<AEKeyType, BigInteger> updated = new Reference2ObjectOpenHashMap<>(this.startedWorkByType);
        subtractBigIntegerWork(previousPending, updated);
        mergeBigIntegerWork(replacement, updated);
        this.startedWorkByType.clear();
        this.startedWorkByType.putAll(updated);
        updateTime();
    }

    /**
     * Removes accepted work that was cancelled before execution. Quantities use each AE key type's
     * native storage units; display conversion remains in progress(). All affected type totals are
     * validated before changing the baseline or elapsed time. Already completed work is never reduced
     * or increased, and the remaining baseline may not fall below it.
     * The returned action must run once in the same server callback without intervening tracker mutations.
     *
     * @param cancelledOutputs positive exact cancelled output quantities; empty is a no-op
     * @throws IllegalArgumentException when a cancellation quantity is not positive
     * @throws IllegalStateException    when cancellation would withdraw completed or unscheduled work
     */
    Runnable prepareUncompletedWithdrawal(Map<AEKey, BigInteger> cancelledOutputs) {
        Reference2ObjectMap<AEKeyType, BigInteger> updated = new Reference2ObjectOpenHashMap<>();
        mergeBigIntegerWork(cancelledOutputs, updated);
        for (var entry : updated.reference2ObjectEntrySet()) {
            AEKeyType type = entry.getKey();
            BigInteger remaining = amount(this.startedWorkByType, type).subtract(entry.getValue());
            if (remaining.compareTo(amount(this.completedWorkByType, type)) < 0) {
                throw new IllegalStateException("Cancelled Trinity work exceeds the uncompleted baseline for " + type.getId());
            }
            entry.setValue(remaining);
        }
        long nextLastTime = updated.isEmpty() ? this.lastTime : System.nanoTime();
        long nextElapsed = this.elapsedTime + nextLastTime - this.lastTime;
        return new Runnable() {

            private boolean applied;

            @Override
            public void run() {
                if (applied) {
                    throw new IllegalStateException("A prepared progress withdrawal may only be applied once");
                }
                applied = true;
                elapsedTime = nextElapsed;
                lastTime = nextLastTime;
                startedWorkByType.putAll(updated);
            }
        };
    }

    /**
     * Records work that has been completed for this job.
     *
     * @param amount  amount completed
     * @param keyType AE key type for unit conversion
     */
    void decrementItems(long amount, AEKeyType keyType) {
        decrementItems(BigInteger.valueOf(amount), keyType);
    }

    void decrementItems(BigInteger amount, AEKeyType keyType) {
        if (amount.signum() < 0) {
            throw new IllegalArgumentException("Completed Trinity work must be non-negative");
        }
        updateTime();
        this.completedWorkByType.merge(keyType, amount, BigInteger::add);
    }

    /**
     * @return elapsed nanoseconds while the job has pending work
     */
    long elapsedTimeNanos() {
        boolean allDone = true;
        for (AEKeyType keyType : AEKeyTypes.getAll()) {
            if (amount(this.completedWorkByType, keyType).compareTo(amount(this.startedWorkByType, keyType)) < 0) {
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
        data.put(STARTED_WORK_TAG, writeWorkByTypeMap(this.startedWorkByType));
        data.put(COMPLETED_WORK_TAG, writeWorkByTypeMap(this.completedWorkByType));
        data.putBoolean(PLAN_BASELINE_TAG, this.planBaseline);
        return data;
    }

    private float progress() {
        BigDecimal startedUnits = BigDecimal.ZERO;
        BigDecimal completedUnits = BigDecimal.ZERO;
        for (AEKeyType keyType : AEKeyTypes.getAll()) {
            BigDecimal divisor = BigDecimal.valueOf(keyType.getAmountPerUnit());
            startedUnits = startedUnits.add(new BigDecimal(amount(this.startedWorkByType, keyType))
                    .divide(divisor, MathContext.DECIMAL64));
            completedUnits = completedUnits.add(new BigDecimal(amount(this.completedWorkByType, keyType))
                    .divide(divisor, MathContext.DECIMAL64));
        }
        if (startedUnits.signum() <= 0) {
            return 0.0F;
        }
        return Mth.clamp(completedUnits.divide(startedUnits, MathContext.DECIMAL64).floatValue(), 0.0F, 1.0F);
    }

    private void mergeBigIntegerWork(Map<AEKey, BigInteger> work,
                                     Reference2ObjectMap<AEKeyType, BigInteger> destination) {
        work.forEach((key, amount) -> {
            if (amount.signum() <= 0) {
                throw new IllegalArgumentException("Trinity plan work must contain positive keyed amounts");
            }
            destination.merge(key.getType(), amount, BigInteger::add);
        });
    }

    private static void subtractBigIntegerWork(
                                               Map<AEKey, BigInteger> work,
                                               Reference2ObjectMap<AEKeyType, BigInteger> destination) {
        Reference2ObjectMap<AEKeyType, BigInteger> removal = new Reference2ObjectOpenHashMap<>();
        work.forEach((key, amount) -> {
            if (amount.signum() <= 0) {
                throw new IllegalArgumentException("Trinity pending work must contain positive keyed amounts");
            }
            removal.merge(key.getType(), amount, BigInteger::add);
        });
        removal.reference2ObjectEntrySet().forEach(entry -> {
            BigInteger current = amount(destination, entry.getKey());
            if (current.compareTo(entry.getValue()) < 0) {
                throw new IllegalStateException("A Trinity replacement cannot remove more work than its baseline");
            }
            destination.put(entry.getKey(), current.subtract(entry.getValue()));
        });
    }

    private void updateTime() {
        long currentTime = System.nanoTime();
        this.elapsedTime += currentTime - this.lastTime;
        this.lastTime = currentTime;
    }

    private static BigInteger amount(
                                     Reference2ObjectMap<AEKeyType, BigInteger> amounts,
                                     AEKeyType key) {
        return amounts.getOrDefault(key, BigInteger.ZERO);
    }

    private static void readWorkByTypeMap(
                                          CompoundTag tag,
                                          Reference2ObjectMap<AEKeyType, BigInteger> output) {
        for (AEKeyType keyType : AEKeyTypes.getAll()) {
            String field = keyType.getId().toString();
            BigInteger amount = tag.contains(field, Tag.TAG_BYTE_ARRAY) ?
                    readBigInteger(tag.getByteArray(field)) : BigInteger.valueOf(tag.getLong(field));
            output.put(keyType, amount);
        }
    }

    private static CompoundTag writeWorkByTypeMap(Reference2ObjectMap<AEKeyType, BigInteger> input) {
        CompoundTag result = new CompoundTag();
        for (Reference2ObjectMap.Entry<AEKeyType, BigInteger> entry : input.reference2ObjectEntrySet()) {
            byte[] encoded = entry.getValue().toByteArray();
            if (encoded.length > MAX_BIG_INTEGER_BYTES) {
                throw new IllegalArgumentException("Trinity progress work exceeds the persistence byte limit");
            }
            result.putByteArray(entry.getKey().getId().toString(), encoded);
        }
        return result;
    }

    private static BigInteger readBigInteger(byte[] encoded) {
        if (encoded.length == 0 || encoded.length > MAX_BIG_INTEGER_BYTES) {
            throw new IllegalArgumentException("Trinity progress work has invalid persistence bytes");
        }
        return new BigInteger(encoded);
    }
}

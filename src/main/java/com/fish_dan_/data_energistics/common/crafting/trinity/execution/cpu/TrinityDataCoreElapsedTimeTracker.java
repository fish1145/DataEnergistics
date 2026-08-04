package com.fish_dan_.data_energistics.common.crafting.trinity.execution.cpu;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.util.Mth;

import appeng.api.stacks.AEKeyType;
import appeng.api.stacks.AEKeyTypes;
import it.unimi.dsi.fastutil.objects.Reference2LongMap;
import it.unimi.dsi.fastutil.objects.Reference2LongOpenHashMap;

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

    private long lastTime = System.nanoTime();
    private long elapsedTime;
    private final Reference2LongMap<AEKeyType> startedWorkByType = new Reference2LongOpenHashMap<>(
            AEKeyTypes.getAll().size());
    private final Reference2LongMap<AEKeyType> completedWorkByType = new Reference2LongOpenHashMap<>(
            AEKeyTypes.getAll().size());

    TrinityDataCoreElapsedTimeTracker() {}

    TrinityDataCoreElapsedTimeTracker(CompoundTag data) {
        this.elapsedTime = data.getLong(ELAPSED_TIME_TAG);
        readLongByTypeMap(data.getCompound(STARTED_WORK_TAG), this.startedWorkByType);
        readLongByTypeMap(data.getCompound(COMPLETED_WORK_TAG), this.completedWorkByType);
    }

    /**
     * Records work that has been scheduled for this job.
     *
     * @param amount  amount scheduled
     * @param keyType AE key type for unit conversion
     */
    void addMaxItems(long amount, AEKeyType keyType) {
        updateTime();
        this.startedWorkByType.merge(keyType, amount, this::saturatedSum);
    }

    /**
     * Records work that has been completed for this job.
     *
     * @param amount  amount completed
     * @param keyType AE key type for unit conversion
     */
    void decrementItems(long amount, AEKeyType keyType) {
        updateTime();
        this.completedWorkByType.merge(keyType, amount, this::saturatedSum);
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
     * @return deprecated AE2-style start count for GUI progress compatibility
     */
    long startItemCount() {
        return Integer.MAX_VALUE;
    }

    /**
     * @return deprecated AE2-style remaining count for GUI progress compatibility
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

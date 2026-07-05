package com.fish_dan_.data_energistics.common.crafting.flower;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.level.Level;

import appeng.api.config.Actionable;
import appeng.api.crafting.IPatternDetails;
import appeng.api.crafting.PatternDetailsHelper;
import appeng.api.networking.IGrid;
import appeng.api.networking.crafting.ICraftingPlan;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.GenericStack;
import appeng.crafting.CraftingLink;
import appeng.crafting.inv.ListCraftingInventory;
import appeng.me.service.CraftingService;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Persisted job state for one Digital Construct Flower virtual CPU.
 *
 * <p>
 * The runtime mirrors AE2's execution model but keeps the state local so virtual CPUs do not depend on native cluster
 * internals.
 */
final class DigitalConstructFlowerExecutingCraftingJob {

    private static final String LINK_TAG = "link";
    private static final String PLAYER_ID_TAG = "player_id";
    private static final String FINAL_OUTPUT_TAG = "final_output";
    private static final String WAITING_FOR_TAG = "waiting_for";
    private static final String TIME_TRACKER_TAG = "time_tracker";
    private static final String REMAINING_AMOUNT_TAG = "remaining_amount";
    private static final String TASKS_TAG = "tasks";
    private static final String CRAFTING_PROGRESS_TAG = "crafting_progress";

    final CraftingLink link;
    final ListCraftingInventory waitingFor;
    final Map<IPatternDetails, TaskProgress> tasks = new HashMap<>();
    final DigitalConstructFlowerElapsedTimeTracker timeTracker;
    GenericStack finalOutput;
    long remainingAmount;
    @Nullable
    Integer playerId;

    @FunctionalInterface
    interface CraftingDifferenceListener {

        /**
         * Reports that crafting amounts for one key changed.
         *
         * @param what changed key
         */
        void onCraftingDifference(AEKey what);
    }

    DigitalConstructFlowerExecutingCraftingJob(ICraftingPlan plan,
                                               CraftingDifferenceListener differenceListener,
                                               CraftingLink link,
                                               @Nullable Integer playerId) {
        this.finalOutput = plan.finalOutput();
        this.remainingAmount = this.finalOutput.amount();
        this.waitingFor = new ListCraftingInventory(differenceListener::onCraftingDifference);
        this.timeTracker = new DigitalConstructFlowerElapsedTimeTracker();
        for (var entry : plan.emittedItems()) {
            this.waitingFor.insert(entry.getKey(), entry.getLongValue(), Actionable.MODULATE);
            this.timeTracker.addMaxItems(entry.getLongValue(), entry.getKey().getType());
        }
        for (Map.Entry<IPatternDetails, Long> entry : plan.patternTimes().entrySet()) {
            this.tasks.computeIfAbsent(entry.getKey(), ignored -> new TaskProgress()).value += entry.getValue();
            for (GenericStack output : entry.getKey().getOutputs()) {
                long amount = output.amount() * entry.getValue() * output.what().getAmountPerUnit();
                this.timeTracker.addMaxItems(amount, output.what().getType());
            }
        }
        this.link = link;
        this.playerId = playerId;
    }

    DigitalConstructFlowerExecutingCraftingJob(CompoundTag data,
                                               HolderLookup.Provider registries,
                                               CraftingDifferenceListener differenceListener,
                                               DigitalConstructFlowerCpuLogic logic) {
        this.link = new CraftingLink(data.getCompound(LINK_TAG), logic.cpu());
        IGrid grid = logic.cpu().grid();
        if (grid != null) {
            ((CraftingService) grid.getCraftingService()).addLink(this.link);
        }

        this.finalOutput = GenericStack.readTag(registries, data.getCompound(FINAL_OUTPUT_TAG));
        this.remainingAmount = data.getLong(REMAINING_AMOUNT_TAG);
        this.waitingFor = new ListCraftingInventory(differenceListener::onCraftingDifference);
        this.waitingFor.readFromNBT(data.getList(WAITING_FOR_TAG, Tag.TAG_COMPOUND), registries);
        this.timeTracker = new DigitalConstructFlowerElapsedTimeTracker(data.getCompound(TIME_TRACKER_TAG));
        this.playerId = data.contains(PLAYER_ID_TAG, Tag.TAG_INT) ? data.getInt(PLAYER_ID_TAG) : null;

        Level level = Objects.requireNonNull(
                logic.cpu().level(),
                "Digital Construct Flower CPU job tasks require a level");

        ListTag tasksTag = data.getList(TASKS_TAG, Tag.TAG_COMPOUND);
        for (int index = 0; index < tasksTag.size(); index++) {
            CompoundTag item = tasksTag.getCompound(index);
            AEItemKey pattern = AEItemKey.fromTag(registries, item);
            IPatternDetails details = PatternDetailsHelper.decodePattern(pattern, level);
            if (details != null) {
                TaskProgress progress = new TaskProgress();
                progress.value = item.getLong(CRAFTING_PROGRESS_TAG);
                this.tasks.put(details, progress);
            }
        }
    }

    /**
     * @param registries registry lookup for AE key serialization
     * @return serialized job state
     */
    CompoundTag writeToTag(HolderLookup.Provider registries) {
        CompoundTag data = new CompoundTag();

        CompoundTag linkData = new CompoundTag();
        this.link.writeToNBT(linkData);
        data.put(LINK_TAG, linkData);

        data.put(FINAL_OUTPUT_TAG, GenericStack.writeTag(registries, this.finalOutput));
        data.put(WAITING_FOR_TAG, this.waitingFor.writeToNBT(registries));
        data.put(TIME_TRACKER_TAG, this.timeTracker.writeToTag());

        ListTag taskList = new ListTag();
        for (Map.Entry<IPatternDetails, TaskProgress> entry : this.tasks.entrySet()) {
            CompoundTag item = entry.getKey().getDefinition().toTag(registries);
            item.putLong(CRAFTING_PROGRESS_TAG, entry.getValue().value);
            taskList.add(item);
        }
        data.put(TASKS_TAG, taskList);

        data.putLong(REMAINING_AMOUNT_TAG, this.remainingAmount);
        if (this.playerId != null) {
            data.putInt(PLAYER_ID_TAG, this.playerId);
        }

        return data;
    }

    static final class TaskProgress {

        long value;
    }
}

package com.fish_dan_.data_energistics.common.crafting.trinity;

import com.fish_dan_.data_energistics.common.trinity.PatternRoute;
import com.fish_dan_.data_energistics.common.trinity.RoutedCraftingPatternDetails;

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
import java.util.function.Function;

/**
 * Persisted job state for one Trinity Data Core virtual CPU.
 *
 * <p>
 * The runtime mirrors AE2's execution model but keeps the state local so virtual CPUs do not depend on native cluster
 * internals.
 */
final class TrinityDataCoreExecutingCraftingJob {

    private static final String LINK_TAG = "link";
    private static final String PLAYER_ID_TAG = "player_id";
    private static final String FINAL_OUTPUT_TAG = "final_output";
    private static final String WAITING_FOR_TAG = "waiting_for";
    private static final String TIME_TRACKER_TAG = "time_tracker";
    private static final String REMAINING_AMOUNT_TAG = "remaining_amount";
    private static final String TASKS_TAG = "tasks";
    private static final String CRAFTING_PROGRESS_TAG = "crafting_progress";
    private static final String ROUTE_TAG = "route";

    final CraftingLink link;
    final ListCraftingInventory waitingFor;
    final Map<IPatternDetails, TaskProgress> tasks = new HashMap<>();
    final TrinityDataCoreElapsedTimeTracker timeTracker;
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

    TrinityDataCoreExecutingCraftingJob(ICraftingPlan plan,
                                        CraftingDifferenceListener differenceListener,
                                        CraftingLink link,
                                        @Nullable Integer playerId) {
        this.finalOutput = plan.finalOutput();
        this.remainingAmount = this.finalOutput.amount();
        this.waitingFor = new ListCraftingInventory(differenceListener::onCraftingDifference);
        this.timeTracker = new TrinityDataCoreElapsedTimeTracker();
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

    TrinityDataCoreExecutingCraftingJob(CompoundTag data,
                                        HolderLookup.Provider registries,
                                        CraftingDifferenceListener differenceListener,
                                        TrinityDataCoreCpuLogic logic) {
        this.link = new CraftingLink(data.getCompound(LINK_TAG), logic.cpu());
        IGrid grid = logic.cpu().grid();
        if (grid != null) {
            ((CraftingService) grid.getCraftingService()).addLink(this.link);
        }

        this.finalOutput = GenericStack.readTag(registries, data.getCompound(FINAL_OUTPUT_TAG));
        this.remainingAmount = data.getLong(REMAINING_AMOUNT_TAG);
        this.waitingFor = new ListCraftingInventory(differenceListener::onCraftingDifference);
        this.waitingFor.readFromNBT(data.getList(WAITING_FOR_TAG, Tag.TAG_COMPOUND), registries);
        this.timeTracker = new TrinityDataCoreElapsedTimeTracker(data.getCompound(TIME_TRACKER_TAG));
        this.playerId = data.contains(PLAYER_ID_TAG, Tag.TAG_INT) ? data.getInt(PLAYER_ID_TAG) : null;

        Level level = logic.cpu().level();

        ListTag tasksTag = data.getList(TASKS_TAG, Tag.TAG_COMPOUND);
        for (int index = 0; index < tasksTag.size(); index++) {
            CompoundTag item = tasksTag.getCompound(index);
            IPatternDetails details = readTaskDetails(
                    item,
                    taskTag -> PatternDetailsHelper.decodePattern(AEItemKey.fromTag(registries, taskTag), level));
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
            CompoundTag item = writeTaskDetails(entry.getKey(), registries);
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

    static CompoundTag writeTaskDetails(IPatternDetails details, HolderLookup.Provider registries) {
        return writeTaskDetails(details, definition -> definition.toTag(registries));
    }

    /**
     * Writes the definition and optional Trinity route for one persisted task.
     *
     * <p>
     * The supplied definition writer keeps this policy independent of the registry serialization mechanism.
     */
    static CompoundTag writeTaskDetails(IPatternDetails details, Function<AEItemKey, CompoundTag> definitionWriter) {
        CompoundTag item = definitionWriter.apply(details.getDefinition());
        if (details instanceof RoutedCraftingPatternDetails routedDetails) {
            item.put(ROUTE_TAG, routedDetails.route().writeToTag());
        }
        return item;
    }

    /**
     * Rebuilds a persisted task and reapplies its exact Trinity route after decoding the pattern definition.
     */
    @Nullable
    static IPatternDetails readTaskDetails(CompoundTag item,
                                           Function<CompoundTag, IPatternDetails> definitionReader) {
        IPatternDetails details = definitionReader.apply(item);
        if (details != null && item.contains(ROUTE_TAG, Tag.TAG_COMPOUND)) {
            return new RoutedCraftingPatternDetails(PatternRoute.readFromTag(item.getCompound(ROUTE_TAG)), details);
        }
        return details;
    }

    static final class TaskProgress {

        long value;
    }
}

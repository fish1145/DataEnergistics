package com.fish_dan_.data_energistics.common.crafting.trinity.execution.cpu;

import com.fish_dan_.data_energistics.Data_Energistics;
import com.fish_dan_.data_energistics.common.crafting.trinity.execution.state.TrinityPlanExecution;
import com.fish_dan_.data_energistics.common.crafting.trinity.planning.plan.TrinityCraftingPlan;
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
import appeng.api.stacks.KeyCounter;
import appeng.crafting.CraftingLink;
import appeng.crafting.inv.ListCraftingInventory;
import appeng.hooks.ticking.TickHandler;
import appeng.me.service.CraftingService;
import org.jetbrains.annotations.Nullable;

import java.util.AbstractMap;
import java.util.AbstractSet;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Set;
import java.util.function.Function;

import static com.fish_dan_.data_energistics.util.LongAmountMath.saturatingMultiplyNonNegative;

/**
 * Persisted job state for one Trinity Data Core virtual CPU.
 *
 * <p>
 * The runtime mirrors AE2's execution model but keeps the state local so virtual CPUs do not depend on native cluster
 * internals.
 */
final class TrinityDataCoreExecutingCraftingJob {

    private static final String SCHEMA_VERSION_TAG = "schema_version";
    private static final int LEGACY_SCHEMA_VERSION = 1;
    private static final int PLAN_SCHEMA_VERSION = 2;
    private static final String LINK_TAG = "link";
    private static final String PLAYER_ID_TAG = "player_id";
    private static final String FINAL_OUTPUT_TAG = "final_output";
    private static final String WAITING_FOR_TAG = "waiting_for";
    private static final String TIME_TRACKER_TAG = "time_tracker";
    private static final String REMAINING_AMOUNT_TAG = "remaining_amount";
    private static final String TASKS_TAG = "tasks";
    private static final String CRAFTING_PROGRESS_TAG = "crafting_progress";
    private static final String TASK_KIND_TAG = "kind";
    private static final String TASK_DEFINITION_TAG = "definition";
    private static final String PROVIDER_TASK_KIND = "provider";
    private static final String TRINITY_TASK_KIND = "trinity";
    private static final String ROUTE_TAG = "route";
    private static final String PLAN_EXECUTION_TAG = "plan_execution";
    private static final String SUSPENDED_TAG = "suspended";

    final CraftingLink link;
    final ListCraftingInventory waitingFor;
    private final ScheduledTasks scheduledTasks = new ScheduledTasks();
    final Map<IPatternDetails, TaskProgress> tasks = this.scheduledTasks.tasks();
    final TrinityDataCoreElapsedTimeTracker timeTracker;
    @Nullable
    private final TrinityPlanExecution planExecution;
    GenericStack finalOutput;
    long remainingAmount;
    boolean suspended;
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
        if (plan instanceof TrinityCraftingPlan trinityPlan) {
            this.planExecution = TrinityPlanExecution.create(
                    trinityPlan,
                    TickHandler.instance().getCurrentTick());
        } else {
            this.planExecution = null;
            for (var entry : plan.emittedItems()) {
                this.waitingFor.insert(entry.getKey(), entry.getLongValue(), Actionable.MODULATE);
                this.timeTracker.addMaxItems(entry.getLongValue(), entry.getKey().getType());
            }
            for (Map.Entry<IPatternDetails, Long> entry : plan.patternTimes().entrySet()) {
                long craftCount = entry.getValue();
                this.scheduledTasks.add(entry.getKey(), craftCount);
                for (GenericStack output : entry.getKey().getOutputs()) {
                    long amount = saturatingMultiplyNonNegative(output.amount(), craftCount);
                    amount = saturatingMultiplyNonNegative(amount, output.what().getAmountPerUnit());
                    this.timeTracker.addMaxItems(amount, output.what().getType());
                }
            }
        }
        this.link = link;
        this.playerId = playerId;
    }

    TrinityDataCoreExecutingCraftingJob(CompoundTag data,
                                        HolderLookup.Provider registries,
                                        CraftingDifferenceListener differenceListener,
                                        TrinityDataCoreCpuLogic logic) {
        if (!hasSupportedSchema(data)) {
            throw new IllegalArgumentException("Unsupported persisted Trinity Data Core CPU job schema");
        }
        int schemaVersion = data.getInt(SCHEMA_VERSION_TAG);
        this.link = new CraftingLink(data.getCompound(LINK_TAG), logic.cpu());
        this.finalOutput = GenericStack.readTag(registries, data.getCompound(FINAL_OUTPUT_TAG));
        this.remainingAmount = data.getLong(REMAINING_AMOUNT_TAG);
        this.suspended = data.getBoolean(SUSPENDED_TAG);
        this.waitingFor = new ListCraftingInventory(differenceListener::onCraftingDifference);
        this.waitingFor.readFromNBT(data.getList(WAITING_FOR_TAG, Tag.TAG_COMPOUND), registries);
        this.timeTracker = new TrinityDataCoreElapsedTimeTracker(data.getCompound(TIME_TRACKER_TAG));
        this.playerId = data.contains(PLAYER_ID_TAG, Tag.TAG_INT) ? data.getInt(PLAYER_ID_TAG) : null;

        if (schemaVersion == PLAN_SCHEMA_VERSION) {
            if (!data.contains(PLAN_EXECUTION_TAG, Tag.TAG_COMPOUND)) {
                throw new IllegalArgumentException("Persisted Trinity plan job is missing execution state");
            }
            this.planExecution = TrinityPlanExecution.restore(
                    data.getCompound(PLAN_EXECUTION_TAG),
                    registries,
                    TickHandler.instance().getCurrentTick());
            GenericStack executionOutput = this.planExecution.finalOutput();
            if (this.finalOutput == null ||
                    !this.finalOutput.what().equals(executionOutput.what()) ||
                    this.finalOutput.amount() != executionOutput.amount() ||
                    this.remainingAmount != this.planExecution.deliveryRemaining()) {
                throw new IllegalArgumentException("Persisted Trinity plan job disagrees with its execution target");
            }
        } else {
            this.planExecution = null;
            Level level = logic.cpu().level();
            ListTag tasksTag = data.getList(TASKS_TAG, Tag.TAG_COMPOUND);
            for (int index = 0; index < tasksTag.size(); index++) {
                CompoundTag item = tasksTag.getCompound(index);
                IPatternDetails details = readTaskDetails(
                        item,
                        taskTag -> PatternDetailsHelper.decodePattern(AEItemKey.fromTag(registries, taskTag), level));
                if (details != null) {
                    this.scheduledTasks.add(details, item.getLong(CRAFTING_PROGRESS_TAG));
                }
            }
        }

        IGrid grid = logic.cpu().grid();
        if (grid != null) {
            ((CraftingService) grid.getCraftingService()).addLink(this.link);
        }
    }

    /**
     * @param registries registry lookup for AE key serialization
     * @return serialized job state
     */
    CompoundTag writeToTag(HolderLookup.Provider registries) {
        CompoundTag data = new CompoundTag();
        data.putInt(
                SCHEMA_VERSION_TAG,
                this.planExecution == null ? LEGACY_SCHEMA_VERSION : PLAN_SCHEMA_VERSION);

        CompoundTag linkData = new CompoundTag();
        this.link.writeToNBT(linkData);
        data.put(LINK_TAG, linkData);

        data.put(FINAL_OUTPUT_TAG, GenericStack.writeTag(registries, this.finalOutput));
        data.put(WAITING_FOR_TAG, this.waitingFor.writeToNBT(registries));
        data.put(TIME_TRACKER_TAG, this.timeTracker.writeToTag());

        if (this.planExecution == null) {
            ListTag taskList = new ListTag();
            for (Map.Entry<IPatternDetails, TaskProgress> entry : this.tasks.entrySet()) {
                CompoundTag item = writeTaskDetails(entry.getKey(), registries);
                item.putLong(CRAFTING_PROGRESS_TAG, entry.getValue().value);
                taskList.add(item);
            }
            data.put(TASKS_TAG, taskList);
        } else {
            data.put(
                    PLAN_EXECUTION_TAG,
                    this.planExecution.save(registries, TickHandler.instance().getCurrentTick()));
        }

        data.putLong(REMAINING_AMOUNT_TAG, this.remainingAmount);
        data.putBoolean(SUSPENDED_TAG, this.suspended);
        if (this.playerId != null) {
            data.putInt(PLAYER_ID_TAG, this.playerId);
        }

        return data;
    }

    /**
     * Determines whether every scheduled task and requested output has completed.
     *
     * @return true when the job can transition to its finished state
     */
    boolean isComplete() {
        if (this.planExecution != null) {
            return this.planExecution.productionComplete() &&
                    this.planExecution.deliveryRemaining() == 0L &&
                    this.planExecution.completionOffer().isEmpty() &&
                    this.waitingFor.list.isEmpty();
        }
        return this.remainingAmount <= 0L && this.tasks.isEmpty() && this.waitingFor.list.isEmpty();
    }

    /** @return whether this job owns a compact Trinity execution cursor */
    boolean isTrinityPlan() {
        return this.planExecution != null;
    }

    /** @return compact execution cursor for a Trinity plan */
    TrinityPlanExecution trinityExecution() {
        if (this.planExecution == null) {
            throw new IllegalStateException("A legacy AE2 job has no Trinity execution cursor");
        }
        return this.planExecution;
    }

    /** Returns the indexed amount still scheduled by undispatched tasks. */
    long getPendingOutputs(AEKey key) {
        return this.planExecution == null ?
                this.scheduledTasks.pendingOutputs(key) :
                this.planExecution.pendingOutputs().getOrDefault(key, 0L);
    }

    /** Adds every indexed undispatched output to the supplied aggregate. */
    void addScheduledOutputsTo(KeyCounter output) {
        if (this.planExecution == null) {
            this.scheduledTasks.addOutputsTo(output);
            return;
        }
        this.planExecution.pendingOutputs().forEach(output::add);
    }

    /** Removes the exact counted dispatch from the derived scheduled-output index. */
    void recordTaskDispatch(IPatternDetails pattern, long craftCount) {
        this.scheduledTasks.recordDispatch(pattern, craftCount);
    }

    static CompoundTag writeTaskDetails(IPatternDetails details, HolderLookup.Provider registries) {
        return writeTaskDetails(details, definition -> definition.toTag(registries));
    }

    /**
     * Writes an explicitly typed task with its definition isolated from routing metadata.
     *
     * <p>
     * The supplied definition writer keeps this policy independent of the registry serialization mechanism.
     */
    static CompoundTag writeTaskDetails(IPatternDetails details, Function<AEItemKey, CompoundTag> definitionWriter) {
        CompoundTag item = new CompoundTag();
        if (details instanceof RoutedCraftingPatternDetails routedDetails) {
            item.putString(TASK_KIND_TAG, TRINITY_TASK_KIND);
            item.put(ROUTE_TAG, routedDetails.route().writeToTag());
        } else {
            item.putString(TASK_KIND_TAG, PROVIDER_TASK_KIND);
        }
        item.put(TASK_DEFINITION_TAG, definitionWriter.apply(details.getDefinition()));
        return item;
    }

    /**
     * Rebuilds a task only when its persisted kind, definition and route satisfy the current schema.
     */
    @Nullable
    static IPatternDetails readTaskDetails(CompoundTag item,
                                           Function<CompoundTag, IPatternDetails> definitionReader) {
        if (!item.contains(TASK_DEFINITION_TAG, Tag.TAG_COMPOUND)) {
            throw new IllegalArgumentException("Persisted Trinity CPU task is missing its pattern definition");
        }

        String taskKind = item.getString(TASK_KIND_TAG);
        return switch (taskKind) {
            case PROVIDER_TASK_KIND -> readProviderTask(item, definitionReader);
            case TRINITY_TASK_KIND -> readTrinityTask(item, definitionReader);
            default -> throw new IllegalArgumentException("Unknown persisted Trinity CPU task kind: " + taskKind);
        };
    }

    @Nullable
    private static IPatternDetails readProviderTask(CompoundTag item,
                                                    Function<CompoundTag, IPatternDetails> definitionReader) {
        if (item.contains(ROUTE_TAG)) {
            throw new IllegalArgumentException("Persisted provider task must not contain a Trinity route");
        }
        return definitionReader.apply(item.getCompound(TASK_DEFINITION_TAG));
    }

    @Nullable
    private static IPatternDetails readTrinityTask(CompoundTag item,
                                                   Function<CompoundTag, IPatternDetails> definitionReader) {
        if (!item.contains(ROUTE_TAG, Tag.TAG_COMPOUND)) {
            throw new IllegalArgumentException("Persisted Trinity task is missing its route");
        }
        PatternRoute route = PatternRoute.readFromTag(item.getCompound(ROUTE_TAG));
        IPatternDetails details = definitionReader.apply(item.getCompound(TASK_DEFINITION_TAG));
        return details == null ? null : new RoutedCraftingPatternDetails(route, details);
    }

    static boolean hasSupportedSchema(CompoundTag data) {
        if (!data.contains(SCHEMA_VERSION_TAG, Tag.TAG_INT)) {
            Data_Energistics.LOGGER.warn("Ignoring persisted Trinity Data Core CPU job without a schema version");
            return false;
        }
        int schemaVersion = data.getInt(SCHEMA_VERSION_TAG);
        if (schemaVersion != LEGACY_SCHEMA_VERSION && schemaVersion != PLAN_SCHEMA_VERSION) {
            Data_Energistics.LOGGER.warn(
                    "Ignoring persisted Trinity Data Core CPU job schema version {}; expected {} or {}",
                    schemaVersion,
                    LEGACY_SCHEMA_VERSION,
                    PLAN_SCHEMA_VERSION);
            return false;
        }
        return true;
    }

    static final class TaskProgress {

        long value;
    }

    /** Authoritative remaining tasks paired with their derived scheduled-output index. */
    static final class ScheduledTasks {

        private final TaskQueue tasks = new TaskQueue();
        private final TrinityScheduledOutputIndex outputs = new TrinityScheduledOutputIndexImpl();

        Map<IPatternDetails, TaskProgress> tasks() {
            return this.tasks;
        }

        void add(IPatternDetails pattern, long craftCount) {
            if (craftCount <= 0L) {
                throw new IllegalArgumentException("Scheduled task count must be positive: " + craftCount);
            }
            TaskProgress progress = this.tasks.computeIfAbsent(pattern, ignored -> new TaskProgress());
            progress.value = Math.addExact(progress.value, craftCount);
            this.outputs.add(pattern, craftCount);
        }

        long pendingOutputs(AEKey key) {
            return this.outputs.amount(key);
        }

        void addOutputsTo(KeyCounter output) {
            this.outputs.addTo(output);
        }

        void recordDispatch(IPatternDetails pattern, long craftCount) {
            this.outputs.remove(pattern, craftCount);
        }
    }

    /** Insertion-ordered task map whose bounded iterators rotate visited work to the tail. */
    static final class TaskQueue extends AbstractMap<IPatternDetails, TaskProgress> {

        private final Map<IPatternDetails, TaskNode> index = new HashMap<>();
        private final Set<Entry<IPatternDetails, TaskProgress>> entries = new AbstractSet<>() {

            @Override
            public Iterator<Entry<IPatternDetails, TaskProgress>> iterator() {
                return new TaskIterator();
            }

            @Override
            public int size() {
                return TaskQueue.this.size();
            }

            @Override
            public void clear() {
                TaskQueue.this.clear();
            }
        };
        @Nullable
        private TaskNode head;
        @Nullable
        private TaskNode tail;

        @Override
        public Set<Entry<IPatternDetails, TaskProgress>> entrySet() {
            return this.entries;
        }

        @Override
        public int size() {
            return this.index.size();
        }

        @Override
        public boolean containsKey(Object key) {
            return this.index.containsKey(key);
        }

        @Override
        public TaskProgress get(Object key) {
            TaskNode node = this.index.get(key);
            return node == null ? null : node.value;
        }

        @Override
        public TaskProgress put(IPatternDetails key, TaskProgress value) {
            TaskNode existing = this.index.get(key);
            if (existing != null) {
                return existing.setValue(value);
            }
            TaskNode node = new TaskNode(key, value);
            this.index.put(key, node);
            append(node);
            return null;
        }

        @Override
        public TaskProgress remove(Object key) {
            TaskNode node = this.index.get(key);
            if (node == null) {
                return null;
            }
            removeNode(node);
            return node.value;
        }

        @Override
        public void clear() {
            this.index.clear();
            this.head = null;
            this.tail = null;
        }

        private void append(TaskNode node) {
            node.previous = this.tail;
            node.next = null;
            if (this.tail == null) {
                this.head = node;
            } else {
                this.tail.next = node;
            }
            this.tail = node;
        }

        private void moveToTail(TaskNode node) {
            if (node == this.tail) {
                return;
            }
            if (node.previous == null) {
                this.head = node.next;
            } else {
                node.previous.next = node.next;
            }
            node.next.previous = node.previous;
            append(node);
        }

        private void removeNode(TaskNode node) {
            this.index.remove(node.key);
            if (node.previous == null) {
                this.head = node.next;
            } else {
                node.previous.next = node.next;
            }
            if (node.next == null) {
                this.tail = node.previous;
            } else {
                node.next.previous = node.previous;
            }
            node.previous = null;
            node.next = null;
        }

        private final class TaskIterator implements Iterator<Entry<IPatternDetails, TaskProgress>> {

            private int remaining = TaskQueue.this.size();
            @Nullable
            private TaskNode next = TaskQueue.this.head;
            @Nullable
            private TaskNode current;
            private boolean removable;

            @Override
            public boolean hasNext() {
                return this.remaining > 0;
            }

            @Override
            public Entry<IPatternDetails, TaskProgress> next() {
                if (!hasNext()) {
                    throw new NoSuchElementException();
                }
                this.current = this.next;
                this.next = this.current.next;
                this.remaining--;
                this.removable = true;
                moveToTail(this.current);
                return this.current;
            }

            @Override
            public void remove() {
                if (!this.removable) {
                    throw new IllegalStateException("Task iterator has no current entry to remove");
                }
                removeNode(this.current);
                this.removable = false;
            }
        }

        private final class TaskNode implements Entry<IPatternDetails, TaskProgress> {

            private final IPatternDetails key;
            private TaskProgress value;
            @Nullable
            private TaskNode previous;
            @Nullable
            private TaskNode next;

            private TaskNode(IPatternDetails key, TaskProgress value) {
                this.key = key;
                this.value = value;
            }

            @Override
            public IPatternDetails getKey() {
                return this.key;
            }

            @Override
            public TaskProgress getValue() {
                return this.value;
            }

            @Override
            public TaskProgress setValue(TaskProgress value) {
                TaskProgress previous = this.value;
                this.value = value;
                return previous;
            }
        }
    }
}

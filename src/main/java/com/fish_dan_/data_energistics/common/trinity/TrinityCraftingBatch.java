package com.fish_dan_.data_energistics.common.trinity;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;

/**
 * Immutable group of adjacent identical crafting dispatches accepted by one Trinity pattern slot.
 *
 * <p>
 * The group references a slot-local definition instead of retaining another encoded stack. All input access uses
 * defensive copies because {@link ItemStack} is mutable.
 * </p>
 */
public final class TrinityCraftingBatch {

    /**
     * A crafting pattern always receives a complete 3 by 3 input snapshot.
     */
    public static final int INPUT_SLOT_COUNT = 9;

    private static final String COUNT_TAG = "count";
    private static final String DEFINITION_ID_TAG = "definition_id";
    private static final String INPUTS_TAG = "inputs";
    private static final String MERGEABLE_TAG = "mergeable";
    private static final String PATTERN_TAG = "pattern";
    private static final String QUEUED_TICK_TAG = "queued_tick";
    private static final String ROUTE_TAG = "route";
    private static final String SLOT_TAG = "slot";
    private static final String STACK_TAG = "stack";

    private final long count;
    private final TrinityPatternDefinition definition;
    private final List<ItemStack> inputs;
    private final boolean mergeable;
    private final long queuedTick;
    private final PatternRoute route;

    /**
     * Creates one resolved queue group.
     *
     * @param queuedTick tick on which every dispatch in the group was accepted
     * @param route      exact host/core/slot destination selected by the crafting plan
     * @param definition slot-local complete pattern definition
     * @param inputs     exactly nine input stacks in row-major order
     * @param count      positive number of identical adjacent dispatches represented by this group
     * @param mergeable  whether later identical dispatches may extend this group
     */
    private TrinityCraftingBatch(long queuedTick, PatternRoute route, TrinityPatternDefinition definition,
                                 List<ItemStack> inputs, long count, boolean mergeable) {
        if (queuedTick < 0L) {
            throw new IllegalArgumentException("Queued tick must not be negative: " + queuedTick);
        }
        if (count <= 0L) {
            throw new IllegalArgumentException("Queued crafting count must be positive: " + count);
        }
        validateInputs(inputs);
        this.queuedTick = queuedTick;
        this.route = route;
        this.definition = definition;
        this.inputs = copyStacks(inputs);
        this.count = count;
        this.mergeable = mergeable;
    }

    /**
     * Creates one resolved queue group from a newly accepted dispatch or validated V2 state.
     *
     * @param queuedTick tick on which every dispatch in the group was accepted
     * @param route      exact host/core/slot destination
     * @param definition slot-local complete pattern definition
     * @param inputs     exactly nine row-major inputs
     * @param count      positive logical craft count
     * @param mergeable  whether later exact dispatches may extend the group
     * @return resolved counted group
     */
    public static TrinityCraftingBatch resolved(long queuedTick, PatternRoute route,
                                                TrinityPatternDefinition definition, List<ItemStack> inputs,
                                                long count, boolean mergeable) {
        if (!definition.resolved()) {
            throw new IllegalArgumentException("A new queued crafting group requires a resolved definition");
        }
        return new TrinityCraftingBatch(queuedTick, route, definition, inputs, count, mergeable);
    }

    /**
     * @return positive number of identical logical crafts represented by this group
     */
    public long count() {
        return this.count;
    }

    /**
     * @return slot-local definition ID referenced by this group
     */
    public long definitionId() {
        return this.definition.id();
    }

    /**
     * @return immutable referenced definition
     */
    public TrinityPatternDefinition definition() {
        return this.definition;
    }

    /**
     * @return defensive copies of all nine row-major crafting inputs
     */
    public List<ItemStack> inputs() {
        return copyStacks(this.inputs);
    }

    /**
     * @return whether this group may merge with a later exact dispatch
     */
    public boolean mergeable() {
        return this.mergeable;
    }

    /**
     * @return defensive copy of the encoded pattern referenced by this group
     */
    public ItemStack patternSnapshot() {
        return this.definition.pattern();
    }

    /**
     * @return tick on which this group was enqueued
     */
    public long queuedTick() {
        return this.queuedTick;
    }

    /**
     * @return immutable host/core/slot route that owns this group and its outputs
     */
    public PatternRoute route() {
        return this.route;
    }

    /**
     * Tests whether the currently installed definition and resolution exactly own this group.
     *
     * @param installedDefinition current slot definition
     * @return whether this group may execute against the supplied definition
     */
    public boolean matchesDefinition(TrinityPatternDefinition installedDefinition) {
        return this.definition == installedDefinition && installedDefinition.resolved();
    }

    /**
     * Tests whether the currently installed pattern is the exact definition captured by this group.
     *
     * @param pattern currently installed encoded pattern
     * @return true when item, components, and count match
     */
    public boolean matchesPattern(ItemStack pattern) {
        return this.definition.matchesPattern(pattern);
    }

    /**
     * Calculates how much of a compatible later group can fill this queue tail without overflowing.
     *
     * @param later later adjacent group candidate
     * @return positive transferable count, or zero when the merge key differs or this tail is full
     */
    long mergeableCount(TrinityCraftingBatch later) {
        if (!this.mergeable || !later.mergeable || this.count == Long.MAX_VALUE ||
                this.queuedTick != later.queuedTick || !this.route.equals(later.route) ||
                this.definition != later.definition) {
            return 0L;
        }
        return stackListsMatch(this.inputs, later.inputs) ? Math.min(later.count, Long.MAX_VALUE - this.count) : 0L;
    }

    /**
     * Combines part or all of a compatible later group without overflowing the retained tail.
     *
     * @param later      adjacent later group
     * @param laterCount positive count to transfer from the later group
     * @return one group containing the transferred logical count
     */
    TrinityCraftingBatch mergedWith(TrinityCraftingBatch later, long laterCount) {
        if (laterCount <= 0L || laterCount > mergeableCount(later)) {
            throw new IllegalArgumentException("Trinity crafting groups cannot merge count " + laterCount);
        }
        return withCount(Math.addExact(this.count, laterCount));
    }

    /**
     * @param count replacement positive logical count
     * @return copy of this exact group with the replacement count
     */
    TrinityCraftingBatch withCount(long count) {
        return new TrinityCraftingBatch(
                this.queuedTick,
                this.route,
                this.definition,
                this.inputs,
                count,
                this.mergeable);
    }

    /**
     * @return detached immutable copy that shares only the immutable definition value
     */
    public TrinityCraftingBatch copy() {
        return new TrinityCraftingBatch(this.queuedTick, this.route, this.definition, this.inputs, this.count,
                this.mergeable);
    }

    TrinityCraftingBatch withDefinition(TrinityPatternDefinition definition) {
        return new TrinityCraftingBatch(
                this.queuedTick, this.route, definition, this.inputs, this.count, this.mergeable);
    }

    CompoundTag writeToTag(HolderLookup.Provider registries) {
        CompoundTag data = new CompoundTag();
        data.putLong(COUNT_TAG, this.count);
        data.putLong(DEFINITION_ID_TAG, this.definition.id());
        data.putBoolean(MERGEABLE_TAG, this.mergeable);
        data.putLong(QUEUED_TICK_TAG, this.queuedTick);
        data.put(ROUTE_TAG, this.route.writeToTag());
        data.put(INPUTS_TAG, writeInputs(registries));
        return data;
    }

    static TrinityCraftingBatch readV2(CompoundTag data, TrinityPatternDefinition definition,
                                       HolderLookup.Provider registries) {
        if (!data.contains(COUNT_TAG, Tag.TAG_LONG) || !data.contains(DEFINITION_ID_TAG, Tag.TAG_LONG) ||
                !data.contains(MERGEABLE_TAG, Tag.TAG_BYTE) || !data.contains(QUEUED_TICK_TAG, Tag.TAG_LONG) ||
                !data.contains(ROUTE_TAG, Tag.TAG_COMPOUND) || !data.contains(INPUTS_TAG, Tag.TAG_LIST)) {
            throw new IllegalArgumentException("V2 queued crafting group is incomplete");
        }
        if (data.getLong(DEFINITION_ID_TAG) != definition.id()) {
            throw new IllegalArgumentException("V2 queued crafting group references the wrong definition");
        }
        if (!definition.resolved() &&
                (data.getLong(COUNT_TAG) != 1L || data.getBoolean(MERGEABLE_TAG))) {
            throw new IllegalArgumentException("Unresolved V1 crafting groups must retain count one without merging");
        }
        return new TrinityCraftingBatch(
                data.getLong(QUEUED_TICK_TAG),
                PatternRoute.readFromTag(data.getCompound(ROUTE_TAG)),
                definition,
                readInputs(data.getList(INPUTS_TAG, Tag.TAG_COMPOUND), registries),
                data.getLong(COUNT_TAG),
                data.getBoolean(MERGEABLE_TAG));
    }

    static V1Data readV1(CompoundTag data, HolderLookup.Provider registries) {
        if (!data.contains(QUEUED_TICK_TAG, Tag.TAG_LONG) || !data.contains(ROUTE_TAG, Tag.TAG_COMPOUND) ||
                !data.contains(PATTERN_TAG, Tag.TAG_COMPOUND)) {
            throw new IllegalArgumentException("V1 queued crafting batch is missing its tick, route, or pattern");
        }
        ItemStack pattern = normalizedPattern(ItemStack.parseOptional(registries, data.getCompound(PATTERN_TAG)));
        return new V1Data(
                data.getLong(QUEUED_TICK_TAG),
                PatternRoute.readFromTag(data.getCompound(ROUTE_TAG)),
                pattern,
                readInputs(data.getList(INPUTS_TAG, Tag.TAG_COMPOUND), registries));
    }

    static TrinityCraftingBatch fromV1(V1Data v1, TrinityPatternDefinition definition) {
        return new TrinityCraftingBatch(v1.queuedTick(), v1.route(), definition, v1.inputs(), 1L, false);
    }

    /**
     * Parsed no-version queue entry used only during atomic V1 migration.
     */
    record V1Data(long queuedTick, PatternRoute route, ItemStack pattern, List<ItemStack> inputs) {

        V1Data {
            pattern = pattern.copy();
            inputs = copyStacks(inputs);
        }
    }

    private ListTag writeInputs(HolderLookup.Provider registries) {
        ListTag inputList = new ListTag();
        for (int slot = 0; slot < this.inputs.size(); slot++) {
            ItemStack input = this.inputs.get(slot);
            if (input.isEmpty()) {
                continue;
            }
            CompoundTag entry = new CompoundTag();
            entry.putInt(SLOT_TAG, slot);
            entry.put(STACK_TAG, input.saveOptional(registries));
            inputList.add(entry);
        }
        return inputList;
    }

    private static List<ItemStack> readInputs(ListTag inputList, HolderLookup.Provider registries) {
        List<ItemStack> inputs = emptyInputs();
        boolean[] populatedSlots = new boolean[INPUT_SLOT_COUNT];
        for (int index = 0; index < inputList.size(); index++) {
            CompoundTag entry = inputList.getCompound(index);
            if (!entry.contains(SLOT_TAG, Tag.TAG_INT) || !entry.contains(STACK_TAG, Tag.TAG_COMPOUND)) {
                throw new IllegalArgumentException("Queued crafting input entry is incomplete");
            }
            int slot = entry.getInt(SLOT_TAG);
            if (slot < 0 || slot >= INPUT_SLOT_COUNT) {
                throw new IllegalArgumentException("Queued crafting input slot out of range: " + slot);
            }
            if (populatedSlots[slot]) {
                throw new IllegalArgumentException("Duplicate queued crafting input slot: " + slot);
            }
            populatedSlots[slot] = true;
            ItemStack input = ItemStack.parseOptional(registries, entry.getCompound(STACK_TAG));
            if (input.isEmpty()) {
                throw new IllegalArgumentException("Queued crafting input " + slot + " is empty");
            }
            inputs.set(slot, input);
        }
        validateInputs(inputs);
        return inputs;
    }

    private static List<ItemStack> emptyInputs() {
        ArrayList<ItemStack> inputs = new ArrayList<>(INPUT_SLOT_COUNT);
        for (int slot = 0; slot < INPUT_SLOT_COUNT; slot++) {
            inputs.add(ItemStack.EMPTY);
        }
        return inputs;
    }

    private static void validateInputs(List<ItemStack> inputs) {
        if (inputs.size() != INPUT_SLOT_COUNT) {
            throw new IllegalArgumentException(
                    "A queued crafting group requires exactly " + INPUT_SLOT_COUNT + " inputs, got " + inputs.size());
        }
        if (inputs.stream().allMatch(ItemStack::isEmpty)) {
            throw new IllegalArgumentException("A queued crafting group must contain at least one input");
        }
        for (int slot = 0; slot < inputs.size(); slot++) {
            ItemStack input = inputs.get(slot);
            if (!input.isEmpty() && input.getCount() > input.getMaxStackSize()) {
                throw new IllegalArgumentException(
                        "Queued crafting input " + slot + " exceeds its maximum stack size: " + input.getCount());
            }
        }
    }

    private static ItemStack normalizedPattern(ItemStack pattern) {
        if (pattern.isEmpty()) {
            throw new IllegalArgumentException("A queued crafting group requires an encoded pattern definition");
        }
        ItemStack normalized = pattern.copy();
        normalized.setCount(1);
        return normalized;
    }

    private static List<ItemStack> copyStacks(List<ItemStack> stacks) {
        ArrayList<ItemStack> copy = new ArrayList<>(stacks.size());
        for (ItemStack stack : stacks) {
            copy.add(stack.copy());
        }
        return List.copyOf(copy);
    }

    private static boolean stackListsMatch(List<ItemStack> first, List<ItemStack> second) {
        for (int slot = 0; slot < INPUT_SLOT_COUNT; slot++) {
            if (!ItemStack.matches(first.get(slot), second.get(slot))) {
                return false;
            }
        }
        return true;
    }
}

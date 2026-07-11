package com.fish_dan_.data_energistics.common.trinity;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;

/**
 * Immutable snapshot of one crafting dispatch accepted by a Trinity pattern core slot.
 *
 * <p>
 * The encoded pattern is captured with the inputs so replacing the visible pattern cannot silently execute queued
 * work against a different recipe. All returned stacks are defensive copies because {@link ItemStack} is mutable.
 */
public final class TrinityCraftingBatch {

    /** A crafting pattern always receives a complete 3 by 3 input snapshot. */
    public static final int INPUT_SLOT_COUNT = 9;

    private static final String QUEUED_TICK_TAG = "queued_tick";
    private static final String ROUTE_TAG = "route";
    private static final String PATTERN_TAG = "pattern";
    private static final String INPUTS_TAG = "inputs";
    private static final String SLOT_TAG = "slot";
    private static final String STACK_TAG = "stack";

    private final long queuedTick;
    private final PatternRoute route;
    private final ItemStack patternSnapshot;
    private final List<ItemStack> inputs;

    /**
     * Creates a batch from the exact pattern and 3 by 3 inputs accepted during one provider push.
     *
     * @param queuedTick      tick on which the dispatch was accepted
     * @param route           exact host/core/slot destination selected by the crafting plan
     * @param patternSnapshot encoded crafting pattern used for this dispatch
     * @param inputs          exactly nine input stacks in row-major order
     */
    public TrinityCraftingBatch(long queuedTick, PatternRoute route, ItemStack patternSnapshot, List<ItemStack> inputs) {
        if (queuedTick < 0L) {
            throw new IllegalArgumentException("Queued tick must not be negative: " + queuedTick);
        }
        if (patternSnapshot.isEmpty()) {
            throw new IllegalArgumentException("A queued crafting batch requires an encoded pattern snapshot");
        }
        if (patternSnapshot.getCount() != 1) {
            throw new IllegalArgumentException("A queued pattern snapshot must contain exactly one item");
        }
        if (inputs.size() != INPUT_SLOT_COUNT) {
            throw new IllegalArgumentException(
                    "A queued crafting batch requires exactly " + INPUT_SLOT_COUNT + " inputs, got " + inputs.size());
        }
        if (inputs.stream().allMatch(ItemStack::isEmpty)) {
            throw new IllegalArgumentException("A queued crafting batch must contain at least one input");
        }

        this.queuedTick = queuedTick;
        this.route = route;
        this.patternSnapshot = patternSnapshot.copy();
        this.inputs = copyStacks(inputs);
    }

    /**
     * @return tick on which this batch was enqueued
     */
    public long queuedTick() {
        return this.queuedTick;
    }

    /**
     * @return immutable host/core/slot route that owns this dispatch and its outputs
     */
    public PatternRoute route() {
        return this.route;
    }

    /**
     * @return defensive copy of the encoded pattern that owns this batch
     */
    public ItemStack patternSnapshot() {
        return this.patternSnapshot.copy();
    }

    /**
     * @return defensive copies of all nine row-major crafting inputs
     */
    public List<ItemStack> inputs() {
        return copyStacks(this.inputs);
    }

    /**
     * Tests whether the currently installed pattern is the exact definition captured by this batch.
     *
     * @param pattern currently installed encoded pattern
     * @return true when item and components match
     */
    public boolean matchesPattern(ItemStack pattern) {
        return ItemStack.isSameItemSameComponents(this.patternSnapshot, pattern);
    }

    CompoundTag writeToTag(HolderLookup.Provider registries) {
        CompoundTag data = new CompoundTag();
        data.putLong(QUEUED_TICK_TAG, this.queuedTick);
        data.put(ROUTE_TAG, this.route.writeToTag());
        data.put(PATTERN_TAG, this.patternSnapshot.saveOptional(registries));

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
        data.put(INPUTS_TAG, inputList);
        return data;
    }

    static TrinityCraftingBatch readFromTag(CompoundTag data, HolderLookup.Provider registries) {
        if (!data.contains(QUEUED_TICK_TAG, Tag.TAG_LONG) || !data.contains(ROUTE_TAG, Tag.TAG_COMPOUND) ||
                !data.contains(PATTERN_TAG, Tag.TAG_COMPOUND)) {
            throw new IllegalArgumentException("Queued crafting batch is missing its tick, route, or pattern snapshot");
        }

        PatternRoute route = PatternRoute.readFromTag(data.getCompound(ROUTE_TAG));
        ItemStack pattern = ItemStack.parseOptional(registries, data.getCompound(PATTERN_TAG));
        List<ItemStack> inputs = new ArrayList<>(INPUT_SLOT_COUNT);
        for (int slot = 0; slot < INPUT_SLOT_COUNT; slot++) {
            inputs.add(ItemStack.EMPTY);
        }

        boolean[] populatedSlots = new boolean[INPUT_SLOT_COUNT];
        ListTag inputList = data.getList(INPUTS_TAG, Tag.TAG_COMPOUND);
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
        return new TrinityCraftingBatch(data.getLong(QUEUED_TICK_TAG), route, pattern, inputs);
    }

    private static List<ItemStack> copyStacks(List<ItemStack> stacks) {
        ArrayList<ItemStack> copy = new ArrayList<>(stacks.size());
        for (ItemStack stack : stacks) {
            copy.add(stack.copy());
        }
        return List.copyOf(copy);
    }
}

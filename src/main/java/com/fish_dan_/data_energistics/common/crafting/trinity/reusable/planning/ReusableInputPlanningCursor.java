package com.fish_dan_.data_energistics.common.crafting.trinity.reusable.planning;

import com.fish_dan_.data_energistics.api.crafting.reusable.ReusableInputContext;
import com.fish_dan_.data_energistics.api.crafting.reusable.ReusableInputRule;
import com.fish_dan_.data_energistics.api.registry.reusable.ReusableInputRules;
import com.fish_dan_.data_energistics.common.crafting.trinity.planning.algorithm.TrinityPlanningControl;
import com.fish_dan_.data_energistics.common.crafting.trinity.planning.graph.TrinityBoundPatternInput;
import com.fish_dan_.data_energistics.common.crafting.trinity.planning.graph.TrinityCanonicalNbt;
import com.fish_dan_.data_energistics.common.crafting.trinity.reusable.planning.ReusableInputPlanningExpansion.Captured;
import com.fish_dan_.data_energistics.common.crafting.trinity.reusable.planning.ReusableInputPlanningExpansion.Reason;
import com.fish_dan_.data_energistics.common.crafting.trinity.reusable.planning.ReusableInputPlanningExpansion.Result;
import com.fish_dan_.data_energistics.common.crafting.trinity.reusable.planning.ReusableInputPlanningExpansion.Stopped;

import appeng.api.crafting.IPatternDetails;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.GenericStack;

import it.unimi.dsi.fastutil.objects.Object2IntLinkedOpenHashMap;
import it.unimi.dsi.fastutil.objects.Object2ObjectAVLTreeMap;
import it.unimi.dsi.fastutil.objects.ObjectArrayFIFOQueue;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import it.unimi.dsi.fastutil.objects.ObjectLinkedOpenHashSet;
import org.jspecify.annotations.Nullable;

import java.util.Iterator;
import java.util.List;
import java.util.function.LongSupplier;

/**
 * Resumable server-thread cursor. Every advance ends between bounded primitive operations; tick
 * budget exhaustion retains all progress and is distinct from the request's terminal deadline.
 * Live contexts and adapters remain confined to the constructing thread until this cursor is discarded.
 */
public final class ReusableInputPlanningCursor {

    private enum Phase {
        SORT_INVENTORY,
        COPY_INVENTORY,
        BEGIN_SLOT,
        ORIGINAL_OPTIONS,
        INVENTORY_OPTIONS,
        SEED_CARTESIAN,
        BEGIN_BINDING,
        CAPTURE_SLOT,
        MATERIAL_NEIGHBORS,
        SUCCESSOR_NEIGHBORS
    }

    private final ReusableInputContext context;
    private final List<AEItemKey> inventory;
    private final ReusableInputRules rules;
    private final int limit;
    private final TrinityPlanningControl control;
    private final Thread owner = Thread.currentThread();
    private final IPatternDetails.IInput[] inputs;
    private final Object2ObjectAVLTreeMap<String, AEItemKey> sortedInventory = new Object2ObjectAVLTreeMap<>();
    private final List<AEItemKey> orderedInventory = new ObjectArrayList<>();
    private final List<ObjectLinkedOpenHashSet<GenericStack>> original = new ObjectArrayList<>();
    private final List<Object2IntLinkedOpenHashMap<GenericStack>> ordinals = new ObjectArrayList<>();
    private final List<List<GenericStack>> options = new ObjectArrayList<>();
    private final ObjectArrayFIFOQueue<List<GenericStack>> pending = new ObjectArrayFIFOQueue<>();
    private final ObjectLinkedOpenHashSet<List<GenericStack>> seen = new ObjectLinkedOpenHashSet<>();
    private final List<List<TrinityBoundPatternInput>> bindings = new ObjectArrayList<>();
    private Phase phase = Phase.SORT_INVENTORY;
    private int inventoryIndex;
    private Iterator<AEItemKey> sortedIterator = List.<AEItemKey>of().iterator();
    private int slot;
    private int templateIndex;
    private GenericStack[] templates = new GenericStack[0];
    private List<GenericStack> encodedOptions = List.of();
    private int[] selected = new int[0];
    private List<GenericStack> assignment = List.of();
    private List<GenericStack> actual = List.of();
    private List<TrinityBoundPatternInput> captured = new ObjectArrayList<>();
    private boolean reusable;
    private boolean anyReusable;
    private @Nullable Result result;

    public ReusableInputPlanningCursor(ReusableInputContext context, List<AEItemKey> inventory,
                                       ReusableInputRules rules, int limit, TrinityPlanningControl control) {
        if (limit <= 0) {
            throw new IllegalArgumentException("Reusable input capture requires a positive binding limit");
        }
        this.context = context;
        this.inventory = List.copyOf(inventory);
        this.rules = rules;
        this.limit = limit;
        this.control = control;
        this.inputs = context.pattern().getInputs();
    }

    /**
     * Performs one server tick's allowance. A null return means progress is retained for a later tick,
     * never permission to use a partial model. Completion is stable across repeated calls.
     */
    public @Nullable Result advance(long sliceNanos, LongSupplier nanoClock) {
        if (Thread.currentThread() != owner) {
            throw new IllegalStateException("Reusable capture cursor must stay on its owning server thread");
        }
        if (sliceNanos <= 0L) {
            throw new IllegalArgumentException("Reusable capture slice must be positive");
        }
        long started = nanoClock.getAsLong();
        do {
            if (result != null) {
                return result;
            }
            if (control.cancellationRequested()) {
                return stop(Reason.CANCELLED);
            }
            if (control.deadlineExceeded()) {
                return stop(Reason.DEADLINE);
            }
            step();
            long now = nanoClock.getAsLong();
            if (now < started) {
                throw new IllegalStateException("Reusable capture slice clock moved backwards");
            }
            if (now - started >= sliceNanos) {
                return result;
            }
        } while (true);
    }

    /** Explicit owner cancellation does not release or synthesize physical assets; capture is read-only. */
    public void cancel() {
        if (Thread.currentThread() != owner) {
            throw new IllegalStateException("Reusable capture cancellation must run on its owning server thread");
        }
        if (result == null) {
            stop(Reason.CANCELLED);
        }
    }

    private void step() {
        switch (phase) {
            case SORT_INVENTORY -> {
                if (inventoryIndex < inventory.size()) {
                    AEItemKey key = inventory.get(inventoryIndex++);
                    sortedInventory.put(TrinityCanonicalNbt.encode(key.toTagGeneric(context.level().registryAccess())), key);
                } else {
                    sortedIterator = sortedInventory.values().iterator();
                    phase = Phase.COPY_INVENTORY;
                }
            }
            case COPY_INVENTORY -> {
                if (sortedIterator.hasNext()) {
                    orderedInventory.add(sortedIterator.next());
                } else {
                    phase = Phase.BEGIN_SLOT;
                }
            }
            case BEGIN_SLOT -> beginSlot();
            case ORIGINAL_OPTIONS -> captureOriginalOption();
            case INVENTORY_OPTIONS -> captureInventoryOption();
            case SEED_CARTESIAN -> seedCartesian();
            case BEGIN_BINDING -> beginBinding();
            case CAPTURE_SLOT -> captureSlot();
            case MATERIAL_NEIGHBORS -> materialNeighbor();
            case SUCCESSOR_NEIGHBORS -> successorNeighbor();
        }
    }

    private void beginSlot() {
        if (slot == inputs.length) {
            if (options.stream().anyMatch(List::isEmpty)) {
                result = new Captured(List.of(), false);
            } else {
                selected = new int[options.size()];
                phase = Phase.SEED_CARTESIAN;
            }
            return;
        }
        templates = inputs[slot].getPossibleInputs();
        original.add(new ObjectLinkedOpenHashSet<>());
        ordinals.add(new Object2IntLinkedOpenHashMap<>());
        templateIndex = 0;
        phase = Phase.ORIGINAL_OPTIONS;
    }

    private void captureOriginalOption() {
        if (templateIndex == templates.length) {
            encodedOptions = List.copyOf(original.get(slot));
            templateIndex = 0;
            inventoryIndex = 0;
            phase = Phase.INVENTORY_OPTIONS;
            return;
        }
        GenericStack template = templates[templateIndex++];
        if (template.amount() <= 0L || inputs[slot].getMultiplier() <= 0L) {
            throw new IllegalArgumentException("Reusable input capture requires positive pattern quantities");
        }
        if (inputs[slot].isValid(template.what(), context.level())) {
            original.get(slot).add(template);
            candidate(template);
        }
    }

    private void captureInventoryOption() {
        if (templateIndex == encodedOptions.size()) {
            options.add(List.copyOf(ordinals.get(slot).keySet()));
            slot++;
            phase = Phase.BEGIN_SLOT;
            return;
        }
        GenericStack template = encodedOptions.get(templateIndex);
        if (!(template.what() instanceof AEItemKey) || inventoryIndex == orderedInventory.size()) {
            templateIndex++;
            inventoryIndex = 0;
            return;
        }
        AEItemKey available = orderedInventory.get(inventoryIndex++);
        if (inputs[slot].isValid(available, context.level())) {
            candidate(new GenericStack(available, template.amount()));
        }
    }

    private void candidate(GenericStack template) {
        Object2IntLinkedOpenHashMap<GenericStack> indexes = ordinals.get(slot);
        if (!indexes.containsKey(template)) {
            if (indexes.size() >= limit) {
                stop(Reason.BINDING_LIMIT);
            } else {
                indexes.put(template, indexes.size());
            }
        }
    }

    private void seedCartesian() {
        List<GenericStack> seed = new ObjectArrayList<>(options.size());
        for (int index = 0; index < selected.length; index++) {
            seed.add(options.get(index).get(selected[index]));
        }
        enqueue(seed);
        int index = selected.length - 1;
        while (index >= 0 && ++selected[index] == options.get(index).size()) {
            selected[index--] = 0;
        }
        if (index < 0) {
            phase = Phase.BEGIN_BINDING;
        }
    }

    private void beginBinding() {
        if (pending.isEmpty()) {
            result = new Captured(bindings, anyReusable);
            return;
        }
        assignment = pending.dequeue();
        actual = new ObjectArrayList<>(assignment.size());
        for (int index = 0; index < assignment.size(); index++) {
            GenericStack template = assignment.get(index);
            actual.add(new GenericStack(template.what(), Math.multiplyExact(template.amount(), inputs[index].getMultiplier())));
        }
        captured = new ObjectArrayList<>(assignment.size());
        reusable = false;
        slot = 0;
        phase = Phase.CAPTURE_SLOT;
    }

    private void captureSlot() {
        if (slot == assignment.size()) {
            anyReusable |= reusable;
            bindings.add(List.copyOf(captured));
            slot = 0;
            templateIndex = 0;
            phase = Phase.MATERIAL_NEIGHBORS;
            return;
        }
        GenericStack template = assignment.get(slot);
        ReusableInputRule rule = null;
        if (template.what() instanceof AEItemKey) {
            rule = rules.resolve(ReusableInputContext.builder()
                    .pattern(context.pattern()).actualInput(actual.get(slot)).exactInputs(actual).inputSlot(slot)
                    .ownership(context.ownership()).actionSource(context.actionSource()).level(context.level())
                    .recipeId(context.recipeId()).machineMode(context.machineMode()).target(context.target()).build()).orElse(null);
        }
        if (rule == null && !original.get(slot).contains(template)) {
            phase = Phase.BEGIN_BINDING;
            return;
        }
        Object2IntLinkedOpenHashMap<GenericStack> indexes = ordinals.get(slot);
        if (!indexes.containsKey(template)) {
            indexes.put(template, indexes.size());
        }
        int alternative = indexes.getInt(template);
        if (rule == null) {
            captured.add(new TrinityBoundPatternInput(slot, alternative, template, inputs[slot].getMultiplier(),
                    inputs[slot].getRemainingKey(template.what())));
        } else {
            ReusableInputRule.Result transition = rule.advance((AEItemKey) template.what(), 1L);
            captured.add(new TrinityBoundPatternInput(slot, alternative, template, inputs[slot].getMultiplier(),
                    transition.successor(), rule, transition.byproducts()));
            reusable = true;
        }
        slot++;
    }

    private void materialNeighbor() {
        if (slot == assignment.size()) {
            slot = 0;
            phase = Phase.SUCCESSOR_NEIGHBORS;
        } else if (templateIndex == options.get(slot).size()) {
            slot++;
            templateIndex = 0;
        } else {
            List<GenericStack> next = new ObjectArrayList<>(assignment);
            next.set(slot, options.get(slot).get(templateIndex++));
            enqueue(next);
        }
    }

    private void successorNeighbor() {
        if (slot == captured.size()) {
            phase = Phase.BEGIN_BINDING;
            return;
        }
        TrinityBoundPatternInput binding = captured.get(slot++);
        if (binding.reusableRule() != null && binding.remainingKey() != null &&
                !binding.remainingKey().equals(binding.template().what()) &&
                inputs[binding.slotIndex()].isValid(binding.remainingKey(), context.level())) {
            List<GenericStack> next = new ObjectArrayList<>(assignment);
            next.set(binding.slotIndex(), new GenericStack(binding.remainingKey(), binding.template().amount()));
            enqueue(next);
        }
    }

    private void enqueue(List<GenericStack> templates) {
        List<GenericStack> immutable = List.copyOf(templates);
        if (!seen.contains(immutable)) {
            if (seen.size() >= limit) {
                stop(Reason.BINDING_LIMIT);
            } else {
                seen.add(immutable);
                pending.enqueue(immutable);
            }
        }
    }

    private Stopped stop(Reason reason) {
        Stopped stopped = new Stopped(reason, seen.size());
        result = stopped;
        return stopped;
    }
}

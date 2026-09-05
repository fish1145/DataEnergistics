package com.fish_dan_.data_energistics.common.crafting.trinity.reusable.planning;

import com.fish_dan_.data_energistics.api.crafting.reusable.ReusableInputContext;
import com.fish_dan_.data_energistics.api.crafting.reusable.ReusableInputRule;
import com.fish_dan_.data_energistics.api.registry.reusable.ReusableInputRules;
import com.fish_dan_.data_energistics.common.crafting.trinity.planning.algorithm.TrinityPlanningControl;
import com.fish_dan_.data_energistics.common.crafting.trinity.planning.graph.TrinityBoundPatternInput;
import com.fish_dan_.data_energistics.common.crafting.trinity.planning.graph.TrinityCanonicalNbt;

import appeng.api.crafting.IPatternDetails;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.GenericStack;

import it.unimi.dsi.fastutil.objects.Object2IntLinkedOpenHashMap;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import it.unimi.dsi.fastutil.objects.ObjectLinkedOpenHashSet;
import org.jspecify.annotations.Nullable;

import java.util.ArrayDeque;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

/**
 * Bounded server-thread capture of complete input assignments and their deterministic successor
 * closure. Rules are queried with every exact slot present; a rule proved for one assignment is
 * never reused for a different assignment. Returned bindings contain values only.
 */
public final class ReusableInputPlanningExpansion {

    private ReusableInputPlanningExpansion() {}

    /** Complete capture or an explicit bound; partial state graphs must never be planned. */
    public sealed interface Result permits Captured, Stopped {}

    /**
     * @param bindings          exact complete assignments in stable capture order; list index is the expanded ordinal
     * @param hasReusableInputs whether at least one explicit rule was applicable
     */
    public record Captured(List<List<TrinityBoundPatternInput>> bindings, boolean hasReusableInputs) implements Result {

        public Captured {
            bindings = bindings.stream().map(List::copyOf).toList();
        }
    }

    /** Bounded capture rejection; callers must retain diagnostics rather than plan a truncated graph. */
    public record Stopped(Reason reason, int examinedBindings) implements Result {}

    /** Distinguishes configured capture bounds from cooperative cancellation. */
    public enum Reason {
        BINDING_LIMIT,
        DEADLINE,
        CANCELLED
    }

    /**
     * Captures candidates from encoded alternatives plus visible inventory item keys, and then exact
     * declared successors. New states require both a registered rule and the original IInput.isValid.
     * Visible counts are not treated as owned stock; normal request inventory capture performs access checks.
     *
     * @param context         server callback context; exactInputs supplies a complete initial grid but is not retained
     * @param inventoryStates visible physical item keys, sorted canonically before expansion
     * @param rules           frozen server-thread adapter lookup
     * @param maximumBindings maximum visited complete assignments, including rejected speculative inventory states
     * @param control         capture deadline/cancellation shared by the request
     * @return complete frozen capture or an explicit stop with no partial binding list
     */
    public static Result capture(ReusableInputContext context, List<AEItemKey> inventoryStates,
                                 ReusableInputRules rules, int maximumBindings, TrinityPlanningControl control) {
        if (maximumBindings <= 0) {
            throw new IllegalArgumentException("Reusable input capture requires a positive binding limit");
        }
        return new Capture(context, inventoryStates, rules, maximumBindings, control).run();
    }

    private static final class Capture {

        private final ReusableInputContext context;
        private final List<AEItemKey> inventoryStates;
        private final ReusableInputRules rules;
        private final int maximumBindings;
        private final TrinityPlanningControl control;
        private final IPatternDetails.IInput[] inputs;
        private final List<ObjectLinkedOpenHashSet<GenericStack>> original = new ObjectArrayList<>();
        private final List<Object2IntLinkedOpenHashMap<GenericStack>> ordinals = new ObjectArrayList<>();
        private final ArrayDeque<List<GenericStack>> pending = new ArrayDeque<>();
        private final ObjectLinkedOpenHashSet<List<GenericStack>> seen = new ObjectLinkedOpenHashSet<>();
        private final List<List<TrinityBoundPatternInput>> bindings = new ObjectArrayList<>();
        private boolean hasReusableInputs;

        private Capture(ReusableInputContext context, List<AEItemKey> inventoryStates, ReusableInputRules rules,
                        int maximumBindings, TrinityPlanningControl control) {
            this.context = context;
            this.inventoryStates = List.copyOf(inventoryStates);
            this.rules = rules;
            this.maximumBindings = maximumBindings;
            this.control = control;
            this.inputs = context.pattern().getInputs();
        }

        private Result run() {
            try {
                List<List<GenericStack>> options = captureOptions();
                seedCartesian(options);
                while (!pending.isEmpty()) {
                    checkpoint();
                    List<GenericStack> templates = pending.removeFirst();
                    List<TrinityBoundPatternInput> captured = captureBinding(templates);
                    if (captured == null) {
                        continue;
                    }
                    bindings.add(captured);
                    for (int slot = 0; slot < templates.size(); slot++) {
                        for (GenericStack alternative : options.get(slot)) {
                            checkpoint();
                            List<GenericStack> assignment = new ObjectArrayList<>(templates);
                            assignment.set(slot, alternative);
                            enqueue(assignment);
                        }
                    }
                    for (TrinityBoundPatternInput slot : captured) {
                        if (slot.reusableRule() == null || slot.remainingKey() == null ||
                                slot.remainingKey().equals(slot.template().what())) {
                            continue;
                        }
                        checkpoint();
                        if (inputs[slot.slotIndex()].isValid(slot.remainingKey(), context.level())) {
                            List<GenericStack> successor = new ObjectArrayList<>(templates);
                            successor.set(slot.slotIndex(), new GenericStack(slot.remainingKey(), slot.template().amount()));
                            enqueue(successor);
                        }
                    }
                }
                checkpoint();
                return new Captured(bindings, hasReusableInputs);
            } catch (CaptureStopped stopped) {
                return new Stopped(stopped.reason, seen.size());
            }
        }

        private List<List<GenericStack>> captureOptions() {
            List<AEItemKey> orderedInventory = new ObjectArrayList<>(new ObjectLinkedOpenHashSet<>(inventoryStates));
            orderedInventory.sort(Comparator.comparing(key -> {
                checkpoint();
                return TrinityCanonicalNbt.encode(key.toTagGeneric(context.level().registryAccess()));
            }));
            List<List<GenericStack>> options = new ObjectArrayList<>(inputs.length);
            for (IPatternDetails.IInput input : inputs) {
                checkpoint();
                ObjectLinkedOpenHashSet<GenericStack> encoded = new ObjectLinkedOpenHashSet<>();
                for (GenericStack template : input.getPossibleInputs()) {
                    checkpoint();
                    if (template.amount() <= 0L || input.getMultiplier() <= 0L) {
                        throw new IllegalArgumentException("Reusable input capture requires positive pattern quantities");
                    }
                    if (input.isValid(template.what(), context.level())) {
                        encoded.add(template);
                    }
                }
                original.add(encoded);
                ObjectLinkedOpenHashSet<GenericStack> candidates = new ObjectLinkedOpenHashSet<>(encoded);
                for (GenericStack template : encoded) {
                    if (!(template.what() instanceof AEItemKey)) {
                        continue;
                    }
                    for (AEItemKey available : orderedInventory) {
                        checkpoint();
                        if (input.isValid(available, context.level())) {
                            candidates.add(new GenericStack(available, template.amount()));
                            if (candidates.size() > maximumBindings) {
                                throw new CaptureStopped(Reason.BINDING_LIMIT);
                            }
                        }
                    }
                }
                Object2IntLinkedOpenHashMap<GenericStack> indexes = new Object2IntLinkedOpenHashMap<>();
                candidates.forEach(candidate -> indexes.put(candidate, indexes.size()));
                ordinals.add(indexes);
                options.add(List.copyOf(candidates));
            }
            return options;
        }

        private void seedCartesian(List<List<GenericStack>> options) {
            if (options.stream().anyMatch(List::isEmpty)) {
                return;
            }
            int[] selected = new int[options.size()];
            while (true) {
                checkpoint();
                List<GenericStack> assignment = new ObjectArrayList<>(options.size());
                for (int slot = 0; slot < selected.length; slot++) {
                    assignment.add(options.get(slot).get(selected[slot]));
                }
                enqueue(assignment);
                int slot = selected.length - 1;
                while (slot >= 0 && ++selected[slot] == options.get(slot).size()) {
                    selected[slot--] = 0;
                }
                if (slot < 0) {
                    return;
                }
            }
        }

        private @Nullable List<TrinityBoundPatternInput> captureBinding(List<GenericStack> templates) {
            List<GenericStack> actual = new ObjectArrayList<>(templates.size());
            for (int slot = 0; slot < templates.size(); slot++) {
                GenericStack template = templates.get(slot);
                actual.add(new GenericStack(template.what(), Math.multiplyExact(template.amount(), inputs[slot].getMultiplier())));
            }
            List<TrinityBoundPatternInput> captured = new ObjectArrayList<>(templates.size());
            boolean reusable = false;
            for (int slot = 0; slot < templates.size(); slot++) {
                checkpoint();
                GenericStack template = templates.get(slot);
                ReusableInputRule rule = null;
                if (template.what() instanceof AEItemKey) {
                    Optional<ReusableInputRule> resolved = rules.resolve(ReusableInputContext.builder()
                            .pattern(context.pattern()).actualInput(actual.get(slot)).exactInputs(actual).inputSlot(slot)
                            .ownership(context.ownership()).actionSource(context.actionSource()).level(context.level())
                            .recipeId(context.recipeId()).machineMode(context.machineMode()).target(context.target()).build());
                    rule = resolved.orElse(null);
                }
                checkpoint();
                if (rule == null && !original.get(slot).contains(template)) {
                    return null;
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
                    ReusableInputRule.Result result = rule.advance((AEItemKey) template.what(), 1L);
                    captured.add(new TrinityBoundPatternInput(slot, alternative, template, inputs[slot].getMultiplier(),
                            result.successor(), rule, result.byproducts()));
                    reusable = true;
                }
            }
            hasReusableInputs |= reusable;
            return List.copyOf(captured);
        }

        private void enqueue(List<GenericStack> assignment) {
            List<GenericStack> immutable = List.copyOf(assignment);
            if (seen.contains(immutable)) {
                return;
            }
            if (seen.size() >= maximumBindings) {
                throw new CaptureStopped(Reason.BINDING_LIMIT);
            }
            seen.add(immutable);
            pending.addLast(immutable);
        }

        private void checkpoint() {
            if (control.cancellationRequested()) {
                throw new CaptureStopped(Reason.CANCELLED);
            }
            if (control.deadlineExceeded()) {
                throw new CaptureStopped(Reason.DEADLINE);
            }
        }
    }

    private static final class CaptureStopped extends RuntimeException {

        private final Reason reason;

        private CaptureStopped(Reason reason) {
            this.reason = reason;
        }
    }
}

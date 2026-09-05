package com.fish_dan_.data_energistics.api.crafting.reusable;

import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.GenericStack;

import net.minecraft.core.component.DataComponents;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

import it.unimi.dsi.fastutil.objects.Object2LongLinkedOpenHashMap;
import it.unimi.dsi.fastutil.objects.Object2LongMap;
import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import it.unimi.dsi.fastutil.objects.ObjectOpenHashSet;
import org.jspecify.annotations.Nullable;

import java.util.List;
import java.util.Map;

/**
 * Frozen, deterministic transition contract for one physical tool unit. No world, recipe callback,
 * random source or mutable stack is retained. The complete record value is its semantic cache identity;
 * adapter ID and revision alone are insufficient. Safe for concurrent readers after publication.
 * Exhaustion is a successful final use with no successor, distinct from an unknown input state.
 *
 * @param id                   stable rule-adapter identity
 * @param revision             non-negative rule revision, changed when the source contract changes
 * @param kind                 explicit behavior kind; a large use count alone never proves unchanged behavior
 * @param initialKey           actual initial tool with all components
 * @param damagePerUse         positive fixed damage increment, or zero outside FIXED_DAMAGE
 * @param breakAtDamage        exclusive surviving damage bound, or zero outside FIXED_DAMAGE
 * @param exhaustionByproducts outputs from the final fixed-damage use, empty for other kinds
 * @param transitions          complete finite state table, empty outside TRANSITIONS
 */
public record ReusableInputRule(ResourceLocation id, long revision, Kind kind, AEItemKey initialKey,
                                int damagePerUse, int breakAtDamage, List<GenericStack> exhaustionByproducts,
                                List<Transition> transitions) {

    public ReusableInputRule {
        if (revision < 0L) {
            throw new IllegalArgumentException("Reusable rule revision must not be negative");
        }
        exhaustionByproducts = checkedOutputs(exhaustionByproducts);
        transitions = List.copyOf(transitions);
        if (kind == Kind.FIXED_DAMAGE) {
            int initialDamage = initialKey.toStack().getDamageValue();
            if (damagePerUse <= 0 || initialDamage < 0 || initialDamage >= breakAtDamage ||
                    breakAtDamage > initialKey.toStack().getMaxDamage() || !transitions.isEmpty()) {
                throw new IllegalArgumentException("Invalid fixed-damage tool contract");
            }
        } else if (damagePerUse != 0 || breakAtDamage != 0 || !exhaustionByproducts.isEmpty()) {
            throw new IllegalArgumentException("Only fixed-damage contracts carry damage parameters");
        }
        if (kind == Kind.TRANSITIONS) {
            ObjectOpenHashSet<AEItemKey> states = new ObjectOpenHashSet<>();
            for (Transition transition : transitions) {
                if (!states.add(transition.input())) {
                    throw new IllegalArgumentException("Duplicate reusable input transition");
                }
            }
            if (!states.contains(initialKey)) {
                throw new IllegalArgumentException("Transition table omits the initial tool");
            }
            for (Transition transition : transitions) {
                if (transition.successor() != null && !states.contains(transition.successor())) {
                    throw new IllegalArgumentException("Transition table omits a successor state");
                }
            }
        } else if (!transitions.isEmpty()) {
            throw new IllegalArgumentException("Only state-table contracts carry transitions");
        }
    }

    /** A guaranteed unchanged tool, fixed positive damage, or an explicit finite transition graph. */
    public enum Kind {
        UNCHANGED,
        FIXED_DAMAGE,
        TRANSITIONS
    }

    /**
     * One successful use. A null successor means legal exhaustion; byproducts exclude the retained tool.
     * Every non-null successor must have its own row, making unknown behavior impossible inside the table.
     *
     * @param input      exact state before use
     * @param successor  exact retained state after use, or null when exhausted
     * @param byproducts deterministic positive outputs of this use
     */
    public record Transition(AEItemKey input, @Nullable AEItemKey successor, List<GenericStack> byproducts) {

        public Transition {
            byproducts = checkedOutputs(byproducts);
        }
    }

    /**
     * Exact cumulative result, excluding ordinary recipe outputs. The successor represents one retained
     * tool, never one tool per use. Byproduct amounts throw on long overflow rather than silently truncate.
     *
     * @param successor  retained tool after every requested use, or null on final legal exhaustion
     * @param byproducts summed transition outputs, excluding the successor
     */
    public record Result(@Nullable AEItemKey successor, List<GenericStack> byproducts) {

        public Result {
            byproducts = checkedOutputs(byproducts);
        }
    }

    /** @return explicit unchanged contract for the supplied exact state */
    public static ReusableInputRule unchanged(ResourceLocation id, long revision, AEItemKey key) {
        return new ReusableInputRule(id, revision, Kind.UNCHANGED, key, 0, 0, List.of(), List.of());
    }

    /**
     * Defines deterministic loss with unchanged item and non-Damage components. Reaching the bound
     * exhausts the tool on that use; this contract must come from authoritative recipe knowledge.
     *
     * @return fixed-loss rule; throws when the state or thresholds cannot describe a damageable item
     */
    public static ReusableInputRule fixedDamage(ResourceLocation id, long revision, AEItemKey key,
                                                int damagePerUse, int breakAtDamage,
                                                List<GenericStack> exhaustionByproducts) {
        return new ReusableInputRule(id, revision, Kind.FIXED_DAMAGE, key, damagePerUse, breakAtDamage,
                exhaustionByproducts, List.of());
    }

    /** @return complete deterministic graph, rejecting duplicate states and undeclared successors */
    public static ReusableInputRule transitions(ResourceLocation id, long revision, AEItemKey initialKey,
                                                List<Transition> transitions) {
        return new ReusableInputRule(id, revision, Kind.TRANSITIONS, initialKey, 0, 0, List.of(), transitions);
    }

    /**
     * Counts successful uses including the exhausting use. Long.MAX_VALUE denotes an unbounded
     * deterministic graph or unchanged rule, not proof of unchanged components. Unknown states throw.
     *
     * @param state exact physical state at the start of the proposed continuation
     * @return positive guaranteed number of uses, including the last use that consumes the tool
     */
    public long guaranteedUses(AEItemKey state) {
        if (kind == Kind.UNCHANGED) {
            requireInitial(state);
            return Long.MAX_VALUE;
        }
        if (kind == Kind.FIXED_DAMAGE) {
            int damage = checkedDamage(state);
            return ((long) breakAtDamage - damage + damagePerUse - 1L) / damagePerUse;
        }
        Map<AEItemKey, Transition> table = transitionTable();
        ObjectOpenHashSet<AEItemKey> seen = new ObjectOpenHashSet<>();
        long uses = 0L;
        AEItemKey cursor = state;
        while (true) {
            if (!seen.add(cursor)) {
                return Long.MAX_VALUE;
            }
            Transition transition = requireTransition(table, cursor);
            uses++;
            if (transition.successor() == null) {
                return uses;
            }
            cursor = transition.successor();
        }
    }

    /**
     * Predicts actual retained state and transition byproducts without executing a recipe. Zero uses
     * validates the state and returns it unchanged. Excess uses and unknown states throw; no clamping.
     *
     * @param state exact starting state
     * @param uses  non-negative count not exceeding the guarantee
     * @return immutable exact result, with no live callback or world access
     */
    public Result advance(AEItemKey state, long uses) {
        long guaranteed = guaranteedUses(state);
        if (uses < 0L || uses > guaranteed) {
            throw new IllegalArgumentException("Use count exceeds the guaranteed tool lifetime");
        }
        if (uses == 0L || kind == Kind.UNCHANGED) {
            return new Result(state, List.of());
        }
        if (kind == Kind.FIXED_DAMAGE) {
            if (uses == guaranteed) {
                return new Result(null, exhaustionByproducts);
            }
            ItemStack successor = state.toStack();
            successor.set(DataComponents.DAMAGE, Math.toIntExact(checkedDamage(state) + uses * damagePerUse));
            return new Result(AEItemKey.of(successor), List.of());
        }
        return advanceTable(state, uses);
    }

    private Result advanceTable(AEItemKey state, long uses) {
        Map<AEItemKey, Transition> table = transitionTable();
        Map<AEItemKey, Visit> visits = new Object2ObjectOpenHashMap<>();
        Object2LongLinkedOpenHashMap<AEKey> outputs = new Object2LongLinkedOpenHashMap<>();
        AEItemKey cursor = state;
        long completed = 0L;
        while (completed < uses) {
            Visit previous = visits.get(cursor);
            if (previous != null) {
                long cycles = (uses - completed) / (completed - previous.completed());
                if (cycles > 0L) {
                    for (var output : outputs.object2LongEntrySet()) {
                        long delta = output.getLongValue() - previous.outputs().getLong(output.getKey());
                        output.setValue(Math.addExact(output.getLongValue(), Math.multiplyExact(delta, cycles)));
                    }
                    completed += (completed - previous.completed()) * cycles;
                    visits.clear();
                    if (completed == uses) {
                        break;
                    }
                }
            }
            visits.put(cursor, new Visit(completed, new Object2LongLinkedOpenHashMap<>(outputs)));
            Transition transition = requireTransition(table, cursor);
            for (GenericStack output : transition.byproducts()) {
                outputs.put(output.what(), Math.addExact(outputs.getLong(output.what()), output.amount()));
            }
            completed++;
            if (transition.successor() == null) {
                return new Result(null, outputList(outputs));
            }
            cursor = transition.successor();
        }
        return new Result(cursor, outputList(outputs));
    }

    private int checkedDamage(AEItemKey state) {
        ItemStack comparable = state.toStack();
        int damage = comparable.getDamageValue();
        comparable.set(DataComponents.DAMAGE, initialKey.toStack().getDamageValue());
        if (damage < 0 || damage >= breakAtDamage || !initialKey.equals(AEItemKey.of(comparable))) {
            throw new IllegalArgumentException("Tool differs outside the declared Damage transition");
        }
        return damage;
    }

    private void requireInitial(AEItemKey state) {
        if (!initialKey.equals(state)) {
            throw new IllegalArgumentException("State does not match the unchanged tool contract");
        }
    }

    private Map<AEItemKey, Transition> transitionTable() {
        Map<AEItemKey, Transition> table = new Object2ObjectOpenHashMap<>();
        transitions.forEach(transition -> table.put(transition.input(), transition));
        return table;
    }

    private static Transition requireTransition(Map<AEItemKey, Transition> table, AEItemKey state) {
        Transition transition = table.get(state);
        if (transition == null) {
            throw new IllegalArgumentException("Undeclared reusable input state");
        }
        return transition;
    }

    private static List<GenericStack> checkedOutputs(List<GenericStack> outputs) {
        List<GenericStack> snapshot = List.copyOf(outputs);
        for (GenericStack output : snapshot) {
            if (output.amount() <= 0L) {
                throw new IllegalArgumentException("Reusable input byproduct amount must be positive");
            }
        }
        return snapshot;
    }

    private static List<GenericStack> outputList(Object2LongMap<AEKey> outputs) {
        List<GenericStack> result = new ObjectArrayList<>(outputs.size());
        outputs.forEach((key, amount) -> result.add(new GenericStack(key, amount)));
        return List.copyOf(result);
    }

    private record Visit(long completed, Object2LongMap<AEKey> outputs) {}
}

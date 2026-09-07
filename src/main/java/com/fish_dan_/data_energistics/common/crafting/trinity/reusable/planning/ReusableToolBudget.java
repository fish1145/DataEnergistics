package com.fish_dan_.data_energistics.common.crafting.trinity.reusable.planning;

import com.fish_dan_.data_energistics.api.crafting.reusable.ReusableInputRule;
import com.fish_dan_.data_energistics.common.crafting.trinity.planning.graph.TrinityCraftingGraphPattern;
import com.fish_dan_.data_energistics.common.crafting.trinity.planning.graph.TrinityCraftingGraphSnapshot;
import com.fish_dan_.data_energistics.common.crafting.trinity.planning.inventory.TrinityPlanningInventory;
import com.fish_dan_.data_energistics.common.crafting.trinity.planning.plan.TrinityCraftingPlan;
import com.fish_dan_.data_energistics.common.crafting.trinity.reusable.rules.FixedToolIdentity;

import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.AEKey;

import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.objects.Object2ObjectLinkedOpenHashMap;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import it.unimi.dsi.fastutil.objects.ObjectOpenHashSet;
import org.jspecify.annotations.Nullable;

import java.math.BigInteger;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

/** Request-local arithmetic tool capacity. Quantities remain physical units; durability is never an AE inventory. */
public final class ReusableToolBudget {

    private final Object2ObjectLinkedOpenHashMap<AEKey, ReusableInputRule> rules = new Object2ObjectLinkedOpenHashMap<>();
    private final TrinityPlanningInventory inventory;

    public ReusableToolBudget(TrinityCraftingGraphSnapshot graph, TrinityPlanningInventory inventory) {
        this.inventory = inventory;
        for (var pattern : graph.patterns()) {
            for (var assignment : pattern.reusableBindings()) {
                for (var binding : assignment) {
                    var lifetime = binding.lifetimeRule();
                    if (lifetime == null) continue;
                    var rule = FixedToolIdentity.rule(lifetime);
                    var previous = rules.putIfAbsent(rule.initialKey(), rule);
                    if (previous != null && !previous.equals(rule)) {
                        throw new Unsupported("Conflicting fixed-wear contracts for " + rule.initialKey());
                    }
                }
            }
        }
    }

    public boolean isEmpty() {
        return rules.isEmpty();
    }

    public TrinityCraftingGraphSnapshot reserve(TrinityCraftingGraphSnapshot graph, Map<AEKey, BigInteger> amounts) {
        var patterns = new ObjectArrayList<TrinityCraftingGraphPattern>();
        for (var pattern : graph.patterns()) {
            var reserved = new Object2ObjectLinkedOpenHashMap<AEKey, BigInteger>();
            for (var assignment : pattern.reusableBindings()) for (var binding : assignment) {
                if (binding.lifetimeBudget()) {
                    AEKey key = FixedToolIdentity.key((AEItemKey) binding.template().what());
                    var amount = amounts.get(key);
                    if (amount != null) reserved.put(key, amount);
                }
            }
            patterns.add(new TrinityCraftingGraphPattern(pattern.identity(), pattern.publication(), pattern.reusableBindings(), reserved));
        }
        return new TrinityCraftingGraphSnapshot(graph.revision(), patterns, graph.reusableInputFallbacks());
    }

    public TrinityPlanningInventory planningInventory() {
        var finite = new Object2ObjectLinkedOpenHashMap<AEKey, BigInteger>();
        var unlimited = new ObjectOpenHashSet<AEKey>();
        inventory.unlimitedKeys().forEach(key -> unlimited.add(planningKey(key)));
        inventory.finiteAmounts().forEach((key, amount) -> {
            AEKey normalized = planningKey(key);
            if (!unlimited.contains(normalized)) finite.merge(normalized, amount, BigInteger::add);
        });
        return new TrinityPlanningInventory(finite, unlimited);
    }

    public Map<AEKey, BigInteger> requiredTools(TrinityCraftingPlan plan) {
        var repeats = new Int2ObjectOpenHashMap<BigInteger>();
        for (var block : plan.cycleRepeatBlocks()) {
            for (int stage : block.stageOrder()) repeats.put(stage, block.repetitions());
        }
        var demands = new Object2ObjectLinkedOpenHashMap<AEKey, Demand>();
        for (var stage : plan.stages()) {
            BigInteger repeat = repeats.getOrDefault(stage.index(), BigInteger.ONE);
            for (var firing : stage.firings()) {
                // Ordinary exact inputs cannot spend a Damage-normalized tool reservation.
                for (AEKey key : firing.inputs().keySet()) {
                    if (matchingRule(key) != null) throw new Unsupported("A lifetime tool is also required as an exact consumed or state-dependent input: " + key);
                }
                var held = new Object2ObjectLinkedOpenHashMap<AEKey, BigInteger>();
                for (var binding : firing.exactBindings()) {
                    if (binding.lifetimeBudget()) {
                        held.merge(binding.template().what(), binding.consumedAmount(), BigInteger::add);
                    }
                }
                BigInteger operations = firing.count().multiply(repeat);
                held.forEach((key, quantity) -> demands.merge(key, new Demand(quantity, operations), (old, added) -> {
                    if (!old.held().equals(added.held())) throw new Unsupported("Mixed parallel tool holding counts for " + key);
                    return new Demand(quantity, old.operations().add(added.operations()));
                }));
            }
        }
        var result = new Object2ObjectLinkedOpenHashMap<AEKey, BigInteger>();
        demands.forEach((key, demand) -> result.put(key, requiredTools(rules.get(key), demand)));
        return result;
    }

    /** Restores real component-bearing keys for the actual CPU withdrawal, never canonical planning prototypes. */
    public Map<AEKey, BigInteger> physicalInputs(Map<AEKey, BigInteger> planned) {
        var physical = new Object2ObjectLinkedOpenHashMap<AEKey, BigInteger>();
        for (var entry : planned.entrySet()) {
            var rule = matchingRule(entry.getKey());
            if (rule == null) {
                physical.merge(entry.getKey(), entry.getValue(), BigInteger::add);
                continue;
            }
            BigInteger remaining = entry.getValue();
            for (Stock stock : stock(rule, remaining)) {
                BigInteger taken = remaining.min(stock.count());
                physical.merge(stock.state(), taken, BigInteger::add);
                remaining = remaining.subtract(taken);
                if (remaining.signum() == 0) break;
            }
            if (remaining.signum() > 0) {
                throw new IllegalStateException("A completed material plan reserved absent tools: " + entry.getKey());
            }
        }
        return physical;
    }

    private BigInteger requiredTools(ReusableInputRule rule, Demand demand) {
        BigInteger neededUses = demand.operations().multiply(demand.held());
        BigInteger units = BigInteger.ZERO;
        for (Stock stock : stock(rule, neededUses)) {
            BigInteger capacity = BigInteger.valueOf(rule.guaranteedUses(stock.state())).min(demand.operations());
            BigInteger taken = ceil(neededUses, capacity).min(stock.count());
            units = units.add(taken);
            neededUses = neededUses.subtract(taken.multiply(capacity));
            if (neededUses.signum() <= 0) return units;
        }
        BigInteger freshUses = BigInteger.valueOf(rule.guaranteedUses(rule.initialKey())).min(demand.operations());
        return units.add(ceil(neededUses, freshUses));
    }

    private List<Stock> stock(ReusableInputRule rule, BigInteger usefulUnits) {
        var result = new ObjectArrayList<Stock>();
        inventory.finiteAmounts().forEach((key, amount) -> {
            if (amount.signum() > 0 && key instanceof AEItemKey item && FixedToolIdentity.matches(rule, item)) {
                result.add(new Stock(item, amount));
            }
        });
        for (AEKey key : inventory.unlimitedKeys()) {
            if (key instanceof AEItemKey item && FixedToolIdentity.matches(rule, item)) {
                result.add(new Stock(item, usefulUnits));
            }
        }
        result.sort(Comparator.comparingLong((Stock stock) -> rule.guaranteedUses(stock.state())).reversed());
        return result;
    }

    private AEKey planningKey(AEKey key) {
        ReusableInputRule rule = matchingRule(key);
        return rule == null ? key : rule.initialKey();
    }

    private @Nullable ReusableInputRule matchingRule(AEKey key) {
        if (key instanceof AEItemKey item) {
            for (var rule : rules.values()) if (FixedToolIdentity.matches(rule, item)) return rule;
        }
        return null;
    }

    private static BigInteger ceil(BigInteger value, BigInteger divisor) {
        return value.add(divisor).subtract(BigInteger.ONE).divide(divisor);
    }

    private record Stock(AEItemKey state, BigInteger count) {}

    private record Demand(BigInteger held, BigInteger operations) {}

    public static final class Unsupported extends RuntimeException {

        private Unsupported(String message) {
            super(message);
        }
    }
}

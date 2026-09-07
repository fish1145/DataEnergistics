package com.fish_dan_.data_energistics.common.crafting.trinity.execution.cpu;

import com.fish_dan_.data_energistics.api.crafting.reusable.ReusableInputContext;
import com.fish_dan_.data_energistics.api.crafting.reusable.ReusableInputContext.Ownership;
import com.fish_dan_.data_energistics.api.crafting.reusable.ReusableInputRule;
import com.fish_dan_.data_energistics.api.crafting.reusable.dispatch.ReusableCraftingRequest.Input;
import com.fish_dan_.data_energistics.api.crafting.reusable.dispatch.ReusableCraftingRequest.SlotStack;
import com.fish_dan_.data_energistics.api.crafting.reusable.dispatch.ReusableCraftingRequest.Target;
import com.fish_dan_.data_energistics.api.crafting.reusable.dispatch.ReusableCraftingRequest.Tool;
import com.fish_dan_.data_energistics.api.registry.reusable.ReusableInputRules;
import com.fish_dan_.data_energistics.common.crafting.trinity.planning.graph.TrinityBoundPatternInput;
import com.fish_dan_.data_energistics.common.crafting.trinity.reusable.endpoint.NativeReusableCrafting;
import com.fish_dan_.data_energistics.common.crafting.trinity.reusable.rules.FixedToolIdentity;

import appeng.api.crafting.IPatternDetails;
import appeng.api.networking.security.IActionSource;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.GenericStack;
import appeng.api.stacks.KeyCounter;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;

import it.unimi.dsi.fastutil.ints.IntOpenHashSet;
import it.unimi.dsi.fastutil.objects.Object2LongLinkedOpenHashMap;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import org.jspecify.annotations.Nullable;

import java.math.BigInteger;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.function.Function;

/** One immutable logical firing contract; sample grid quantities never represent CPU-owned assets. */
final class TrinityReusableRecipe {

    record ToolSlot(int slot, AEItemKey state, long held, boolean unchanged, ReusableInputRule rule, boolean lifetime) {

        boolean accepts(AEKey key) {
            return lifetime ? key instanceof AEItemKey item && FixedToolIdentity.matches(rule, item) : state.equals(key);
        }

        BigInteger capacity(AEKey key, BigInteger operations) {
            return lifetime ? BigInteger.valueOf(rule.guaranteedUses((AEItemKey) key)).min(operations) :
                    unchanged ? operations : BigInteger.ONE;
        }
    }

    record ResidentTools(List<GenericStack> tools, long committed) {

        static final ResidentTools EMPTY = new ResidentTools(List.of(), 0L);
    }

    record Offer(long count, List<SlotStack> addedTools) {}

    private final IPatternDetails pattern;
    private final List<TrinityBoundPatternInput> bindings;
    private final List<GenericStack> exactInputs;
    private final List<Input> inputs;
    private final List<ToolSlot> tools;
    private final List<GenericStack> ordinaryRemainders;
    private final Optional<ResourceLocation> recipeId;

    TrinityReusableRecipe(IPatternDetails pattern, List<TrinityBoundPatternInput> bindings, Optional<ResourceLocation> recipeId) {
        this.pattern = pattern;
        this.bindings = List.copyOf(bindings);
        this.recipeId = recipeId;
        List<GenericStack> exact = new ObjectArrayList<>();
        List<Input> requirements = new ObjectArrayList<>();
        List<ToolSlot> held = new ObjectArrayList<>();
        List<GenericStack> remainders = new ObjectArrayList<>();
        for (TrinityBoundPatternInput binding : bindings) {
            long amount = binding.consumedAmount().longValueExact();
            GenericStack input = new GenericStack(binding.template().what(), amount);
            exact.add(input);
            if (binding.reusableRule() == null) {
                requirements.add(new Input(binding.slotIndex(), List.of(input), Optional.empty()));
                if (binding.remainingKey() != null) {
                    remainders.add(new GenericStack(binding.remainingKey(), binding.remainingAmount().longValueExact()));
                }
            } else {
                AEItemKey state = (AEItemKey) input.what();
                requirements.add(new Input(binding.slotIndex(), List.of(), Optional.of(
                        new Tool(amount, Ownership.CPU_SUPPLIED, binding.reusableRule(), binding.lifetimeBudget() ? Optional.empty() : Optional.of(state)))));
                held.add(new ToolSlot(binding.slotIndex(), state, amount,
                        state.equals(binding.reusableRule().advance(state, 1L).successor()), binding.reusableRule(), binding.lifetimeBudget()));
                for (GenericStack byproduct : binding.byproducts()) {
                    remainders.add(new GenericStack(byproduct.what(), Math.multiplyExact(byproduct.amount(), amount)));
                }
            }
        }
        this.exactInputs = List.copyOf(exact);
        this.inputs = List.copyOf(requirements);
        this.tools = List.copyOf(held);
        this.ordinaryRemainders = List.copyOf(remainders);
    }

    boolean matches(Target target, IActionSource source, ServerLevel level, ReusableInputRules rules) {
        IPatternDetails.IInput[] live = pattern.getInputs();
        if (live.length != bindings.size()) {
            return false;
        }
        IntOpenHashSet reusable = new IntOpenHashSet();
        for (TrinityBoundPatternInput binding : bindings) {
            if (binding.slotIndex() >= live.length || live[binding.slotIndex()].getMultiplier() != binding.multiplier()) {
                return false;
            }
            if (binding.reusableRule() != null) {
                reusable.add(binding.slotIndex());
                var current = rules.resolve(ReusableInputContext.builder().pattern(pattern).inputSlot(binding.slotIndex())
                        .actualInput(exactInputs.get(binding.slotIndex())).exactInputs(exactInputs).ownership(Ownership.CPU_SUPPLIED)
                        .actionSource(source).level(level).recipeId(recipeId).machineMode(target.mode()).target(target.route()).build());
                if (current.isEmpty() || !current.orElseThrow().equals(binding.reusableRule())) {
                    return false;
                }
            }
        }
        return NativeReusableCrafting.matches(pattern, exactInputs, reusable, recipeId, level);
    }

    List<Input> inputs() {
        return inputs;
    }

    List<ToolSlot> tools() {
        return tools;
    }

    List<GenericStack> ordinaryRemainders() {
        return ordinaryRemainders;
    }

    List<GenericStack> exactInputs() {
        return exactInputs;
    }

    KeyCounter[] sampleGrid() {
        KeyCounter[] sample = new KeyCounter[exactInputs.size()];
        for (int slot = 0; slot < sample.length; slot++) {
            sample[slot] = new KeyCounter();
            GenericStack input = exactInputs.get(slot);
            sample[slot].add(input.what(), input.amount());
        }
        return sample;
    }

    /** Finds a count against one shared CPU balance, so identical material/tool keys cannot be double booked. */
    Offer offer(long maximum, KeyCounter available, Function<ToolSlot, ResidentTools> residentTools) {
        long lower = 0L;
        long upper = maximum;
        List<SlotStack> selected = List.of();
        while (lower < upper) {
            long count = lower + ((upper - lower) >>> 1) + 1L;
            List<SlotStack> candidate = toolsFor(count, available, residentTools);
            if (candidate != null) {
                lower = count;
                selected = candidate;
            } else {
                upper = count - 1L;
            }
        }
        if (lower == 0L) {
            return new Offer(0L, List.of());
        }
        return new Offer(lower, List.copyOf(selected));
    }

    private @Nullable List<SlotStack> toolsFor(long count, KeyCounter available, Function<ToolSlot, ResidentTools> residentTools) {
        Object2LongLinkedOpenHashMap<AEKey> needed = new Object2LongLinkedOpenHashMap<>();
        List<SlotStack> added = new ObjectArrayList<>();
        try {
            for (Input input : inputs) {
                for (GenericStack consumed : input.consumedPerOperation()) {
                    needed.mergeLong(consumed.what(), Math.multiplyExact(consumed.amount(), count), Math::addExact);
                }
            }
            for (ToolSlot tool : tools) {
                ResidentTools resident = residentTools.apply(tool);
                BigInteger operations = BigInteger.valueOf(count).add(BigInteger.valueOf(resident.committed()));
                BigInteger missing = remainingCapacity(tool, resident.tools(), operations);
                if (missing.signum() <= 0) continue;
                List<GenericStack> candidates = new ObjectArrayList<>();
                for (var entry : available) {
                    if (tool.accepts(entry.getKey())) {
                        long free = entry.getLongValue() - Math.min(entry.getLongValue(), needed.getLong(entry.getKey()));
                        if (free > 0) candidates.add(new GenericStack(entry.getKey(), free));
                    }
                }
                candidates.sort(Comparator.comparing((GenericStack stack) -> tool.capacity(stack.what(), operations)).reversed());
                for (GenericStack candidate : candidates) {
                    BigInteger capacity = tool.capacity(candidate.what(), operations);
                    long take = missing.add(capacity).subtract(BigInteger.ONE).divide(capacity)
                            .min(BigInteger.valueOf(candidate.amount())).longValueExact();
                    needed.mergeLong(candidate.what(), take, Math::addExact);
                    added.add(new SlotStack(tool.slot(), new GenericStack(candidate.what(), take)));
                    missing = missing.subtract(capacity.multiply(BigInteger.valueOf(take)));
                    if (missing.signum() <= 0) break;
                }
                if (missing.signum() > 0) return null;
            }
        } catch (ArithmeticException tooLarge) {
            return null;
        }
        for (var entry : needed.object2LongEntrySet()) {
            if (available.get(entry.getKey()) < entry.getLongValue()) {
                return null;
            }
        }
        return added;
    }

    static BigInteger remainingCapacity(ToolSlot tool, List<GenericStack> tools, BigInteger operations) {
        BigInteger missing = operations.multiply(BigInteger.valueOf(tool.held()));
        for (GenericStack held : tools) {
            if (tool.accepts(held.what())) {
                missing = missing.subtract(tool.capacity(held.what(), operations).multiply(BigInteger.valueOf(held.amount())));
            }
        }
        return missing;
    }
}

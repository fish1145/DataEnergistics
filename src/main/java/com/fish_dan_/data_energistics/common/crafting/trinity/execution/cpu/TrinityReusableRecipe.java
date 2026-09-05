package com.fish_dan_.data_energistics.common.crafting.trinity.execution.cpu;

import com.fish_dan_.data_energistics.api.crafting.reusable.ReusableInputContext;
import com.fish_dan_.data_energistics.api.crafting.reusable.ReusableInputContext.Ownership;
import com.fish_dan_.data_energistics.api.crafting.reusable.dispatch.ReusableCraftingRequest.Input;
import com.fish_dan_.data_energistics.api.crafting.reusable.dispatch.ReusableCraftingRequest.SlotStack;
import com.fish_dan_.data_energistics.api.crafting.reusable.dispatch.ReusableCraftingRequest.Target;
import com.fish_dan_.data_energistics.api.crafting.reusable.dispatch.ReusableCraftingRequest.Tool;
import com.fish_dan_.data_energistics.api.registry.reusable.ReusableInputRules;
import com.fish_dan_.data_energistics.common.crafting.trinity.planning.graph.TrinityBoundPatternInput;
import com.fish_dan_.data_energistics.common.crafting.trinity.reusable.endpoint.NativeReusableCrafting;

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

import java.util.List;
import java.util.Optional;
import java.util.function.ToLongFunction;

/** One immutable logical firing contract; sample grid quantities never represent CPU-owned assets. */
final class TrinityReusableRecipe {

    record ToolSlot(int slot, AEItemKey state, long held, boolean unchanged) {}

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
                        new Tool(amount, Ownership.CPU_SUPPLIED, binding.reusableRule(), Optional.of(state)))));
                held.add(new ToolSlot(binding.slotIndex(), state, amount,
                        state.equals(binding.reusableRule().advance(state, 1L).successor())));
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

    Optional<ResourceLocation> recipeId() {
        return recipeId;
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
    Offer offer(long maximum, KeyCounter available, ToLongFunction<ToolSlot> freeResidentTools) {
        long lower = 0L;
        long upper = maximum;
        while (lower < upper) {
            long count = lower + ((upper - lower) >>> 1) + 1L;
            if (fits(count, available, freeResidentTools)) {
                lower = count;
            } else {
                upper = count - 1L;
            }
        }
        if (lower == 0L) {
            return new Offer(0L, List.of());
        }
        List<SlotStack> added = new ObjectArrayList<>();
        for (ToolSlot tool : tools) {
            long required = tool.unchanged() ? tool.held() : Math.multiplyExact(lower, tool.held());
            long missing = Math.max(0L, required - Math.min(required, freeResidentTools.applyAsLong(tool)));
            if (missing > 0L) {
                added.add(new SlotStack(tool.slot(), new GenericStack(tool.state(), missing)));
            }
        }
        return new Offer(lower, List.copyOf(added));
    }

    private boolean fits(long count, KeyCounter available, ToLongFunction<ToolSlot> freeResidentTools) {
        Object2LongLinkedOpenHashMap<AEKey> needed = new Object2LongLinkedOpenHashMap<>();
        try {
            for (Input input : inputs) {
                for (GenericStack consumed : input.consumedPerOperation()) {
                    needed.mergeLong(consumed.what(), Math.multiplyExact(consumed.amount(), count), Math::addExact);
                }
            }
            for (ToolSlot tool : tools) {
                long required = tool.unchanged() ? tool.held() : Math.multiplyExact(tool.held(), count);
                long missing = Math.max(0L, required - Math.min(required, freeResidentTools.applyAsLong(tool)));
                needed.mergeLong(tool.state(), missing, Math::addExact);
            }
        } catch (ArithmeticException tooLarge) {
            return false;
        }
        for (var entry : needed.object2LongEntrySet()) {
            if (available.get(entry.getKey()) < entry.getLongValue()) {
                return false;
            }
        }
        return true;
    }
}

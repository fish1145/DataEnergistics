package com.fish_dan_.data_energistics.common.crafting.trinity.execution.pattern;

import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;

import appeng.api.crafting.IPatternDetails;
import appeng.api.crafting.PatternDetailsTooltip;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.GenericStack;
import appeng.api.stacks.KeyCounter;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.function.ToLongFunction;

/** Odometer-style selector sharing the planner's stable Cartesian ordinal semantics. */
final class TrinityPatternSelectorImpl implements TrinityPatternSelector {

    @Override
    public Result select(IPatternDetails pattern,
                         int plannedOrdinal,
                         boolean dynamic,
                         long remainingCrafts,
                         ToLongFunction<AEKey> cpuAvailability,
                         ToLongFunction<AEKey> networkAvailability,
                         int maxVariants) {
        if (plannedOrdinal < 0 || remainingCrafts <= 0L || maxVariants <= 0) {
            throw new IllegalArgumentException("A Trinity runtime binding requires an ordinal, work and variant limit");
        }

        IPatternDetails.IInput[] inputs = pattern.getInputs();
        BigInteger exactVariantCount = countVariants(inputs);
        if (exactVariantCount.compareTo(BigInteger.valueOf(maxVariants)) > 0) {
            return new VariantLimit(exactVariantCount, maxVariants);
        }
        int variantCount = exactVariantCount.intValueExact();
        if (!dynamic && plannedOrdinal >= variantCount) {
            return new Unavailable(allAlternativeKeys(inputs));
        }

        LinkedHashSet<AEKey> observedKeys = allAlternativeKeys(inputs);
        Candidate best = null;
        int[] alternatives = new int[inputs.length];
        for (int ordinal = 0; ordinal < variantCount; ordinal++) {
            if (dynamic || ordinal == plannedOrdinal) {
                Candidate candidate;
                try {
                    candidate = evaluate(
                            inputs,
                            alternatives,
                            ordinal,
                            remainingCrafts,
                            cpuAvailability,
                            networkAvailability);
                } catch (ArithmeticException exception) {
                    return new ArithmeticOverflow("runtime_pattern_binding");
                }
                if (candidate.maximumCrafts() > 0L && (best == null || candidate.isBetterThan(best))) {
                    best = candidate;
                }
            }
            incrementOdometer(alternatives, inputs);
        }

        if (best == null) {
            return new Unavailable(observedKeys);
        }
        IPatternDetails extractionPattern = new BoundPatternDetails(pattern, best.selectedInputs());
        return new Selected(
                extractionPattern,
                best.ordinal(),
                best.maximumCrafts(),
                best.aggregatedInputs(),
                observedKeys);
    }

    private static BigInteger countVariants(IPatternDetails.IInput[] inputs) {
        BigInteger count = BigInteger.ONE;
        for (IPatternDetails.IInput input : inputs) {
            int alternatives = input.getPossibleInputs().length;
            if (alternatives == 0) {
                return BigInteger.ZERO;
            }
            count = count.multiply(BigInteger.valueOf(alternatives));
        }
        return count;
    }

    private static LinkedHashSet<AEKey> allAlternativeKeys(IPatternDetails.IInput[] inputs) {
        LinkedHashSet<AEKey> keys = new LinkedHashSet<>();
        for (IPatternDetails.IInput input : inputs) {
            for (GenericStack alternative : input.getPossibleInputs()) {
                keys.add(alternative.what());
            }
        }
        return keys;
    }

    private static Candidate evaluate(IPatternDetails.IInput[] inputs,
                                      int[] alternatives,
                                      int ordinal,
                                      long remainingCrafts,
                                      ToLongFunction<AEKey> cpuAvailability,
                                      ToLongFunction<AEKey> networkAvailability) {
        ArrayList<SelectedInput> selected = new ArrayList<>(inputs.length);
        LinkedHashMap<AEKey, Long> aggregated = new LinkedHashMap<>();
        for (int slot = 0; slot < inputs.length; slot++) {
            IPatternDetails.IInput input = inputs[slot];
            GenericStack template = input.getPossibleInputs()[alternatives[slot]];
            long amount = Math.multiplyExact(template.amount(), input.getMultiplier());
            aggregated.merge(template.what(), amount, Math::addExact);
            selected.add(new SelectedInput(input, template));
        }

        long maximumCrafts = remainingCrafts;
        LinkedHashMap<AEKey, Long> cpuAmounts = new LinkedHashMap<>();
        for (Map.Entry<AEKey, Long> entry : aggregated.entrySet()) {
            long cpuAmount = requireAvailable(cpuAvailability.applyAsLong(entry.getKey()));
            long networkAmount = requireAvailable(networkAvailability.applyAsLong(entry.getKey()));
            cpuAmounts.put(entry.getKey(), cpuAmount);
            long total = saturatingAdd(cpuAmount, networkAmount);
            maximumCrafts = Math.min(maximumCrafts, total / entry.getValue());
        }

        long networkBorrow = 0L;
        for (Map.Entry<AEKey, Long> entry : aggregated.entrySet()) {
            long batchAmount = Math.multiplyExact(entry.getValue(), maximumCrafts);
            long borrowed = Math.max(0L, batchAmount - Math.min(batchAmount, cpuAmounts.get(entry.getKey())));
            networkBorrow = Math.addExact(networkBorrow, borrowed);
        }

        ArrayList<GenericStack> exactInputs = new ArrayList<>(aggregated.size());
        aggregated.forEach((key, amount) -> exactInputs.add(new GenericStack(key, amount)));
        return new Candidate(
                ordinal,
                maximumCrafts,
                networkBorrow,
                List.copyOf(selected),
                List.copyOf(exactInputs));
    }

    private static long requireAvailable(long amount) {
        if (amount < 0L) {
            throw new IllegalArgumentException("A Trinity material availability cannot be negative");
        }
        return amount;
    }

    private static long saturatingAdd(long left, long right) {
        return left > Long.MAX_VALUE - right ? Long.MAX_VALUE : left + right;
    }

    private static void incrementOdometer(int[] alternatives, IPatternDetails.IInput[] inputs) {
        for (int slot = alternatives.length - 1; slot >= 0; slot--) {
            alternatives[slot]++;
            if (alternatives[slot] < inputs[slot].getPossibleInputs().length) {
                return;
            }
            alternatives[slot] = 0;
        }
    }

    private record Candidate(int ordinal,
                             long maximumCrafts,
                             long networkBorrow,
                             List<SelectedInput> selectedInputs,
                             List<GenericStack> aggregatedInputs) {

        private boolean isBetterThan(Candidate other) {
            if (this.maximumCrafts != other.maximumCrafts) {
                return this.maximumCrafts > other.maximumCrafts;
            }
            if (this.networkBorrow != other.networkBorrow) {
                return this.networkBorrow < other.networkBorrow;
            }
            return this.ordinal < other.ordinal;
        }
    }

    private record SelectedInput(IPatternDetails.IInput delegate, GenericStack template) {}

    /** Pattern wrapper used only for exact CPU-side extraction; providers always receive the registered delegate. */
    private static final class BoundPatternDetails implements IPatternDetails {

        private final IPatternDetails delegate;
        private final IInput[] inputs;

        private BoundPatternDetails(IPatternDetails delegate, List<SelectedInput> selectedInputs) {
            this.delegate = delegate;
            this.inputs = selectedInputs.stream().map(BoundInput::new).toArray(IInput[]::new);
        }

        @Override
        public AEItemKey getDefinition() {
            return this.delegate.getDefinition();
        }

        @Override
        public IInput[] getInputs() {
            return this.inputs.clone();
        }

        @Override
        public GenericStack getPrimaryOutput() {
            return this.delegate.getPrimaryOutput();
        }

        @Override
        public List<GenericStack> getOutputs() {
            return this.delegate.getOutputs();
        }

        @Override
        public boolean supportsPushInputsToExternalInventory() {
            return this.delegate.supportsPushInputsToExternalInventory();
        }

        @Override
        public void pushInputsToExternalInventory(KeyCounter[] inputHolder, PatternInputSink inputSink) {
            this.delegate.pushInputsToExternalInventory(inputHolder, inputSink);
        }

        @Override
        public PatternDetailsTooltip getTooltip(Level level, TooltipFlag flags) {
            return this.delegate.getTooltip(level, flags);
        }
    }

    /** One exact slot alternative retaining the live pattern's multiplier and remainder semantics. */
    private static final class BoundInput implements IPatternDetails.IInput {

        private final IPatternDetails.IInput delegate;
        private final GenericStack template;
        private final AEKey remainingKey;

        private BoundInput(SelectedInput selected) {
            this.delegate = selected.delegate();
            this.template = selected.template();
            this.remainingKey = this.delegate.getRemainingKey(this.template.what());
        }

        @Override
        public GenericStack[] getPossibleInputs() {
            return new GenericStack[] { this.template };
        }

        @Override
        public long getMultiplier() {
            return this.delegate.getMultiplier();
        }

        @Override
        public boolean isValid(AEKey input, Level level) {
            return this.template.what().equals(input);
        }

        @Override
        public AEKey getRemainingKey(AEKey template) {
            return this.template.what().equals(template) ? this.remainingKey : null;
        }
    }
}

package com.fish_dan_.data_energistics.common.crafting.trinity.execution.pattern;

import com.fish_dan_.data_energistics.common.crafting.trinity.execution.pattern.TrinityBoundPatternDetails.SlotBinding;
import com.fish_dan_.data_energistics.common.crafting.trinity.pattern.binding.TrinityPatternBindingEnumerator;
import com.fish_dan_.data_energistics.common.trinity.pattern.TrinityPatternPublicationSignature;

import appeng.api.crafting.IPatternDetails;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.GenericStack;

import it.unimi.dsi.fastutil.objects.Object2LongLinkedOpenHashMap;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import it.unimi.dsi.fastutil.objects.ObjectLinkedOpenHashSet;
import org.jspecify.annotations.Nullable;

import java.math.BigInteger;
import java.util.List;
import java.util.Set;
import java.util.function.Function;
import java.util.function.ToLongFunction;

/**
 * Selects one legal, deterministic input binding from a live pattern immediately before dispatch.
 *
 */
public final class TrinityPatternSelector {

    /**
     * @return the production Cartesian selector
     */
    public static TrinityPatternSelector create() {
        return new TrinityPatternSelector();
    }

    /**
     * A non-null input-binding outcome.
     */
    public sealed interface Result permits Selected, Unavailable, VariantLimit, ArithmeticOverflow {}

    /**
     * @param extractionPattern immutable wrapper exposing only the chosen alternative in each input slot
     * @param variantOrdinal    selected Cartesian ordinal
     * @param maximumCrafts     maximum currently material-feasible logical batch
     * @param inputsPerCraft    exact aggregated consumption for one firing
     * @param observedKeys      all keys whose inventory changes may change this decision
     */
    public record Selected(IPatternDetails extractionPattern,
                           int variantOrdinal,
                           long maximumCrafts,
                           List<GenericStack> inputsPerCraft,
                           Set<AEKey> observedKeys)
            implements Result {

        /**
         * Isolates collections returned by the selector from callers.
         */
        public Selected {
            inputsPerCraft = List.copyOf(inputsPerCraft);
            observedKeys = Set.copyOf(observedKeys);
        }
    }

    /**
     * @param observedKeys keys that should wake this stage when material availability changes
     */
    public record Unavailable(Set<AEKey> observedKeys) implements Result {

        /**
         * Isolates the wake set from mutable callers.
         */
        public Unavailable {
            observedKeys = Set.copyOf(observedKeys);
        }
    }

    /**
     * @param required number of legal Cartesian variants
     * @param limit    configured maximum
     */
    public record VariantLimit(BigInteger required, int limit) implements Result {}

    /**
     * @param operation exact quantity operation that crossed the AE2 long boundary
     */
    public record ArithmeticOverflow(String operation) implements Result {}

    private final TrinityPatternBindingEnumerator bindingEnumerator = TrinityPatternBindingEnumerator.create();

    /**
     * Selects either the planned ordinal or, for a cycle stage, the best currently executable alternative.
     *
     * @param pattern              exact live pattern returned by {@link TrinityPatternResolver}
     * @param plannedOrdinal       binding ordinal retained by the plan
     * @param dynamic              whether this cycle stage may switch to another legal binding
     * @param remainingCrafts      remaining logical firings for the work item
     * @param cpuAvailability      current CPU-owned amount for a key
     * @param networkAvailability  current simulatable network amount for a key
     * @param dynamicInputResolver authorized actual same-item alternatives and their usable CPU-owned quantities
     * @param maxVariants          configured Cartesian expansion bound
     * @return explicit selection, wait set, or a hard planning bound failure
     */
    public Result select(IPatternDetails pattern,
                         int plannedOrdinal,
                         boolean dynamic,
                         long remainingCrafts,
                         ToLongFunction<AEKey> cpuAvailability,
                         ToLongFunction<AEKey> networkAvailability,
                         Function<AEKey, List<GenericStack>> dynamicInputResolver,
                         int maxVariants) {
        if (plannedOrdinal < 0 || remainingCrafts <= 0L || maxVariants <= 0) {
            throw new IllegalArgumentException("A Trinity runtime binding requires an ordinal, work and variant limit");
        }

        List<RuntimeInput> inputs = captureInputs(pattern.getInputs());
        ObjectLinkedOpenHashSet<AEKey> observedKeys = allAlternativeKeys(inputs);
        List<TrinityPatternBindingEnumerator.Binding> bindings;
        if (dynamic) {
            TrinityPatternBindingEnumerator.Result enumeration = this.bindingEnumerator.enumerate(
                    inputs.stream().map(RuntimeInput::signature).toList(),
                    maxVariants);
            switch (enumeration) {
                case TrinityPatternBindingEnumerator.LimitExceeded(var required, var limit) -> {
                    return new VariantLimit(required, limit);
                }
                case TrinityPatternBindingEnumerator.ArithmeticOverflow(var axis) -> {
                    return new ArithmeticOverflow(axis);
                }
                case TrinityPatternBindingEnumerator.Enumerated(var enumerated) -> bindings = enumerated;
            }
        } else {
            List<Integer> alternatives = decodeCartesianOrdinal(plannedOrdinal, inputs);
            if (alternatives == null) {
                return new Unavailable(observedKeys);
            }
            bindings = List.of(new TrinityPatternBindingEnumerator.Binding(plannedOrdinal, alternatives));
        }

        Candidate best = null;
        for (TrinityPatternBindingEnumerator.Binding binding : bindings) {
            Candidate candidate;
            try {
                candidate = evaluate(
                        inputs,
                        binding.alternativeOrdinals(),
                        binding.cartesianOrdinal(),
                        remainingCrafts,
                        cpuAvailability,
                        networkAvailability,
                        dynamicInputResolver,
                        observedKeys);
            } catch (ArithmeticException exception) {
                return new ArithmeticOverflow("runtime_pattern_binding");
            }
            if (candidate.maximumCrafts() > 0L && (best == null || candidate.isBetterThan(best))) {
                best = candidate;
            }
        }

        if (best == null) {
            return new Unavailable(observedKeys);
        }
        IPatternDetails extractionPattern = new TrinityBoundPatternDetails(pattern, best.selectedInputs());
        best.aggregatedInputs().forEach(input -> observedKeys.add(input.what()));
        return new Selected(
                extractionPattern,
                best.ordinal(),
                best.maximumCrafts(),
                best.aggregatedInputs(),
                observedKeys);
    }

    private static List<RuntimeInput> captureInputs(IPatternDetails.IInput[] inputs) {
        ObjectArrayList<RuntimeInput> captured = new ObjectArrayList<>(inputs.length);
        for (IPatternDetails.IInput input : inputs) {
            captured.add(new RuntimeInput(input, TrinityPatternPublicationSignature.Input.capture(input)));
        }
        return List.copyOf(captured);
    }

    private static ObjectLinkedOpenHashSet<AEKey> allAlternativeKeys(List<RuntimeInput> inputs) {
        ObjectLinkedOpenHashSet<AEKey> keys = new ObjectLinkedOpenHashSet<>();
        for (RuntimeInput input : inputs) {
            for (TrinityPatternPublicationSignature.Alternative alternative : input.signature().alternatives()) {
                keys.add(alternative.stack().what());
            }
        }
        return keys;
    }

    private static Candidate evaluate(List<RuntimeInput> inputs,
                                      List<Integer> alternatives,
                                      int ordinal,
                                      long remainingCrafts,
                                      ToLongFunction<AEKey> cpuAvailability,
                                      ToLongFunction<AEKey> networkAvailability,
                                      Function<AEKey, List<GenericStack>> dynamicInputResolver,
                                      Set<AEKey> observedKeys) {
        ObjectArrayList<GenericStack> templates = new ObjectArrayList<>(inputs.size());
        ObjectArrayList<List<GenericStack>> aliases = new ObjectArrayList<>(inputs.size());
        ObjectArrayList<Object2LongLinkedOpenHashMap<AEKey>> allocations = new ObjectArrayList<>(inputs.size());
        Object2LongLinkedOpenHashMap<AEKey> cpuAmounts = new Object2LongLinkedOpenHashMap<>();
        Object2LongLinkedOpenHashMap<AEKey> totalAmounts = new Object2LongLinkedOpenHashMap<>();
        Object2LongLinkedOpenHashMap<AEKey> available = new Object2LongLinkedOpenHashMap<>();
        Object2LongLinkedOpenHashMap<AEKey> aliasLimits = new Object2LongLinkedOpenHashMap<>();
        Object2LongLinkedOpenHashMap<AEKey> aliasUsage = new Object2LongLinkedOpenHashMap<>();
        long[] remaining = new long[inputs.size()];
        for (int slot = 0; slot < inputs.size(); slot++) {
            RuntimeInput input = inputs.get(slot);
            TrinityPatternPublicationSignature.Alternative alternative = input.signature()
                    .alternatives()
                    .get(alternatives.get(slot));
            GenericStack template = alternative.stack();
            templates.add(template);
            remaining[slot] = Math.multiplyExact(template.amount(), input.signature().multiplier());
            allocations.add(new Object2LongLinkedOpenHashMap<>());
            captureAvailability(template.what(), cpuAvailability, networkAvailability, cpuAmounts, totalAmounts);
            List<GenericStack> candidates = dynamicInputResolver.apply(template.what());
            aliases.add(candidates);
            for (GenericStack candidate : candidates) {
                if (!(template.what() instanceof AEItemKey planned) ||
                        !(candidate.what() instanceof AEItemKey actual) || actual.getItem() != planned.getItem() ||
                        candidate.amount() <= 0L) {
                    throw new IllegalArgumentException("Dynamic input resolver returned an invalid same-item alternative");
                }
                captureAvailability(candidate.what(), cpuAvailability, networkAvailability, cpuAmounts, totalAmounts);
                aliasLimits.mergeLong(candidate.what(), candidate.amount(), Math::min);
                observedKeys.add(candidate.what());
            }
        }
        available.putAll(totalAmounts);
        // Reserve exact requirements for every slot first, so a flexible input cannot steal a later exact input.
        for (int slot = 0; slot < inputs.size(); slot++) {
            GenericStack template = templates.get(slot);
            long taken = allocate(allocations.get(slot), available, template.what(), remaining[slot], template.amount());
            remaining[slot] -= taken;
        }
        for (int slot = 0; slot < inputs.size(); slot++) {
            GenericStack template = templates.get(slot);
            for (GenericStack alias : aliases.get(slot)) {
                long allowed = aliasLimits.getLong(alias.what()) - aliasUsage.getLong(alias.what());
                long taken = allocate(allocations.get(slot), available, alias.what(),
                        Math.min(remaining[slot], allowed), template.amount());
                aliasUsage.addTo(alias.what(), taken);
                remaining[slot] -= taken;
                if (remaining[slot] == 0L) {
                    break;
                }
            }
            if (remaining[slot] > 0L) {
                return new Candidate(ordinal, 0L, 0L, List.of(), List.of());
            }
        }
        ObjectArrayList<SlotBinding> selected = new ObjectArrayList<>(inputs.size());
        Object2LongLinkedOpenHashMap<AEKey> aggregated = new Object2LongLinkedOpenHashMap<>();
        for (int slot = 0; slot < inputs.size(); slot++) {
            ObjectArrayList<GenericStack> slices = new ObjectArrayList<>();
            for (var allocation : allocations.get(slot).object2LongEntrySet()) {
                slices.add(new GenericStack(allocation.getKey(), allocation.getLongValue()));
                aggregated.mergeLong(allocation.getKey(), allocation.getLongValue(), Math::addExact);
            }
            selected.add(new SlotBinding(inputs.get(slot).delegate(), templates.get(slot), slices));
        }
        long maximumCrafts = remainingCrafts;
        for (var entry : aggregated.object2LongEntrySet()) {
            maximumCrafts = Math.min(maximumCrafts, totalAmounts.getLong(entry.getKey()) / entry.getLongValue());
        }
        for (var entry : aliasUsage.object2LongEntrySet()) {
            if (entry.getLongValue() > 0L) {
                maximumCrafts = Math.min(maximumCrafts, aliasLimits.getLong(entry.getKey()) / entry.getLongValue());
            }
        }

        long networkBorrow = 0L;
        for (var entry : aggregated.object2LongEntrySet()) {
            long batchAmount = Math.multiplyExact(entry.getLongValue(), maximumCrafts);
            long borrowed = Math.max(0L, batchAmount - Math.min(batchAmount, cpuAmounts.getLong(entry.getKey())));
            networkBorrow = Math.addExact(networkBorrow, borrowed);
        }

        ObjectArrayList<GenericStack> exactInputs = new ObjectArrayList<>(aggregated.size());
        aggregated.object2LongEntrySet().forEach(entry -> exactInputs.add(new GenericStack(entry.getKey(), entry.getLongValue())));
        return new Candidate(
                ordinal,
                maximumCrafts,
                networkBorrow,
                List.copyOf(selected),
                List.copyOf(exactInputs));
    }

    private static void captureAvailability(AEKey key, ToLongFunction<AEKey> cpuAvailability,
                                            ToLongFunction<AEKey> networkAvailability,
                                            Object2LongLinkedOpenHashMap<AEKey> cpuAmounts,
                                            Object2LongLinkedOpenHashMap<AEKey> totalAmounts) {
        if (!totalAmounts.containsKey(key)) {
            long cpu = requireAvailable(cpuAvailability.applyAsLong(key));
            long network = requireAvailable(networkAvailability.applyAsLong(key));
            cpuAmounts.put(key, cpu);
            totalAmounts.put(key, saturatingAdd(cpu, network));
        }
    }

    private static long allocate(Object2LongLinkedOpenHashMap<AEKey> allocated,
                                 Object2LongLinkedOpenHashMap<AEKey> available,
                                 AEKey key, long requested, long quantum) {
        long amount = Math.min(requested, available.getLong(key));
        amount -= amount % quantum;
        if (amount > 0L) {
            allocated.addTo(key, amount);
            available.addTo(key, -amount);
        }
        return amount;
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

    private static @Nullable List<Integer> decodeCartesianOrdinal(int ordinal, List<RuntimeInput> inputs) {
        int remaining = ordinal;
        Integer[] alternatives = new Integer[inputs.size()];
        for (int slot = inputs.size() - 1; slot >= 0; slot--) {
            int alternativeCount = inputs.get(slot).signature().alternatives().size();
            alternatives[slot] = remaining % alternativeCount;
            remaining /= alternativeCount;
        }
        return remaining == 0 ? List.of(alternatives) : null;
    }

    private record Candidate(int ordinal,
                             long maximumCrafts,
                             long networkBorrow,
                             List<SlotBinding> selectedInputs,
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

    private record RuntimeInput(IPatternDetails.IInput delegate,
                                TrinityPatternPublicationSignature.Input signature) {}
}

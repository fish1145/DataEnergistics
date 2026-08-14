package com.fish_dan_.data_energistics.common.crafting.trinity.execution.pattern;

import com.fish_dan_.data_energistics.common.crafting.trinity.pattern.binding.TrinityPatternBindingEnumerator;
import com.fish_dan_.data_energistics.common.trinity.pattern.TrinityPatternPublicationSignature;

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
import java.util.Optional;
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
     * Selects an exact runtime binding without enabling current-job dynamic input aliases.
     *
     * <p>
     * This overload preserves the existing selector contract for callers that do not own a
     * dynamic-output ledger.
     * </p>
     */
    public Result select(IPatternDetails pattern,
                         int plannedOrdinal,
                         boolean dynamic,
                         long remainingCrafts,
                         ToLongFunction<AEKey> cpuAvailability,
                         ToLongFunction<AEKey> networkAvailability,
                         int maxVariants) {
        return select(
                pattern,
                plannedOrdinal,
                dynamic,
                remainingCrafts,
                cpuAvailability,
                networkAvailability,
                ignored -> Optional.empty(),
                maxVariants);
    }

    /**
     * Selects either the planned ordinal or, for a cycle stage, the best currently executable alternative.
     *
     * @param pattern              exact live pattern returned by {@link TrinityPatternResolver}
     * @param plannedOrdinal       binding ordinal retained by the plan
     * @param dynamic              whether this cycle stage may switch to another legal binding
     * @param remainingCrafts      remaining logical firings for the work item
     * @param cpuAvailability      current CPU-owned amount for a key
     * @param networkAvailability  current simulatable network amount for a key
     * @param dynamicInputResolver current-job resolver for actual same-item variants returned by dynamic outputs
     * @param maxVariants          configured Cartesian expansion bound
     * @return explicit selection, wait set, or a hard planning bound failure
     */
    public Result select(IPatternDetails pattern,
                         int plannedOrdinal,
                         boolean dynamic,
                         long remainingCrafts,
                         ToLongFunction<AEKey> cpuAvailability,
                         ToLongFunction<AEKey> networkAvailability,
                         Function<GenericStack, Optional<AEItemKey>> dynamicInputResolver,
                         int maxVariants) {
        if (plannedOrdinal < 0 || remainingCrafts <= 0L || dynamicInputResolver == null || maxVariants <= 0) {
            throw new IllegalArgumentException("A Trinity runtime binding requires an ordinal, work and variant limit");
        }

        List<RuntimeInput> inputs = captureInputs(pattern.getInputs());
        LinkedHashSet<AEKey> observedKeys = allAlternativeKeys(inputs);
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
                        dynamicInputResolver);
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
        IPatternDetails extractionPattern = new BoundPatternDetails(pattern, best.selectedInputs());
        best.aggregatedInputs().forEach(input -> observedKeys.add(input.what()));
        return new Selected(
                extractionPattern,
                best.ordinal(),
                best.maximumCrafts(),
                best.aggregatedInputs(),
                observedKeys);
    }

    private static List<RuntimeInput> captureInputs(IPatternDetails.IInput[] inputs) {
        ArrayList<RuntimeInput> captured = new ArrayList<>(inputs.length);
        for (IPatternDetails.IInput input : inputs) {
            captured.add(new RuntimeInput(input, TrinityPatternPublicationSignature.Input.capture(input)));
        }
        return List.copyOf(captured);
    }

    private static LinkedHashSet<AEKey> allAlternativeKeys(List<RuntimeInput> inputs) {
        LinkedHashSet<AEKey> keys = new LinkedHashSet<>();
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
                                      Function<GenericStack, Optional<AEItemKey>> dynamicInputResolver) {
        ArrayList<SelectedInput> selected = new ArrayList<>(inputs.size());
        LinkedHashMap<AEKey, Long> aggregated = new LinkedHashMap<>();
        for (int slot = 0; slot < inputs.size(); slot++) {
            RuntimeInput input = inputs.get(slot);
            TrinityPatternPublicationSignature.Alternative alternative = input.signature()
                    .alternatives()
                    .get(alternatives.get(slot));
            GenericStack template = alternative.stack();
            long amount = Math.multiplyExact(template.amount(), input.signature().multiplier());
            AEKey selectedKey = dynamicInputResolver.apply(new GenericStack(template.what(), amount))
                    .<AEKey>map(key -> key)
                    .orElse(template.what());
            GenericStack selectedTemplate = new GenericStack(selectedKey, template.amount());
            AEKey remainingKey = selectedKey.equals(template.what()) ?
                    alternative.remainingKey() :
                    input.delegate().getRemainingKey(selectedKey);
            aggregated.merge(selectedKey, amount, Math::addExact);
            selected.add(new SelectedInput(input.delegate(), selectedTemplate, remainingKey));
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

    private static List<Integer> decodeCartesianOrdinal(int ordinal, List<RuntimeInput> inputs) {
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

    private record RuntimeInput(IPatternDetails.IInput delegate,
                                TrinityPatternPublicationSignature.Input signature) {}

    private record SelectedInput(IPatternDetails.IInput delegate,
                                 GenericStack template,
                                 AEKey remainingKey) {}

    /**
     * Pattern wrapper used only for exact CPU-side extraction; providers always receive the registered delegate.
     */
    @SuppressWarnings("ClassCanBeRecord")
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

    /**
     * One exact slot alternative retaining the live pattern's multiplier and remainder semantics.
     */
    private static final class BoundInput implements IPatternDetails.IInput {

        private final IPatternDetails.IInput delegate;
        private final GenericStack template;
        private final AEKey remainingKey;

        private BoundInput(SelectedInput selected) {
            this.delegate = selected.delegate();
            this.template = selected.template();
            this.remainingKey = selected.remainingKey();
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

package com.fish_dan_.data_energistics.common.crafting.dynamic;

import appeng.api.crafting.IPatternDetails;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.GenericStack;
import appeng.api.stacks.KeyCounter;

import it.unimi.dsi.fastutil.objects.Object2ObjectLinkedOpenHashMap;
import it.unimi.dsi.fastutil.objects.ObjectArrayFIFOQueue;
import org.jspecify.annotations.Nullable;

import java.util.Arrays;
import java.util.List;
import java.util.Map;

/**
 * Replays one pattern's original input-emission order while substituting explicitly bound same-item variants.
 *
 * <p>
 * The caller must provide the planned template selected for every original input slot. This class never infers
 * authorization from an actual input counter: it only translates actual keys that are either exact or have the same
 * registered item as their selected planned template. The original pattern still owns ordering, including processing
 * pattern sparse-slot expansion.
 * </p>
 */
public final class BoundPatternInputEmitter {

    private BoundPatternInputEmitter() {}

    /**
     * Emits actual inputs in the order requested by the registered pattern.
     *
     * @param originalDetails          registered pattern whose emission callback defines input order
     * @param selectedPlannedTemplates exact planned template selected for every condensed input slot
     * @param actualInputs             actual per-slot counters already extracted by the CPU
     * @param sink                     provider-owned destination for actual inputs
     */
    public static void emit(IPatternDetails originalDetails,
                            List<GenericStack> selectedPlannedTemplates,
                            KeyCounter[] actualInputs,
                            IPatternDetails.PatternInputSink sink) {
        IPatternDetails.IInput[] plannedInputs = originalDetails.getInputs();
        if (plannedInputs.length != selectedPlannedTemplates.size() || plannedInputs.length != actualInputs.length) {
            throw new IllegalArgumentException("Bound pattern input emission requires one binding per input slot");
        }

        KeyCounter[] syntheticInputs = new KeyCounter[plannedInputs.length];
        Map<AEKey, ObjectArrayFIFOQueue<ActualSlice>> actualByPlannedKey = new Object2ObjectLinkedOpenHashMap<>();
        for (int slot = 0; slot < plannedInputs.length; slot++) {
            IPatternDetails.IInput plannedInput = plannedInputs[slot];
            GenericStack plannedTemplate = selectedPlannedTemplates.get(slot);
            KeyCounter actualInput = actualInputs[slot];
            validateSlot(plannedInput, plannedTemplate, slot);

            long actualAmount = 0L;
            ObjectArrayFIFOQueue<ActualSlice> slices = actualByPlannedKey.computeIfAbsent(
                    plannedTemplate.what(),
                    ignored -> new ObjectArrayFIFOQueue<>());
            for (var actual : actualInput) {
                AEKey actualKey = actual.getKey();
                long amount = actual.getLongValue();
                validateActualKey(plannedTemplate.what(), actualKey, amount, slot);
                actualAmount = Math.addExact(actualAmount, amount);
                slices.enqueue(new ActualSlice(actualKey, amount));
            }

            long requiredAmount = Math.multiplyExact(plannedTemplate.amount(), plannedInput.getMultiplier());
            if (actualAmount != requiredAmount) {
                throw new IllegalArgumentException(
                        "Bound pattern input slot " + slot + " contains " + actualAmount +
                                " units but requires " + requiredAmount);
            }
            KeyCounter synthetic = new KeyCounter();
            synthetic.add(plannedTemplate.what(), actualAmount);
            syntheticInputs[slot] = synthetic;
        }

        originalDetails.pushInputsToExternalInventory(syntheticInputs, (plannedKey, amount) -> {
            if (plannedKey == null || amount <= 0L) {
                throw new IllegalStateException("Registered pattern emitted an invalid planned input");
            }
            ObjectArrayFIFOQueue<ActualSlice> slices = actualByPlannedKey.get(plannedKey);
            if (slices == null) {
                throw new IllegalStateException(
                        "Registered pattern emitted an input outside the authorized binding: " + plannedKey);
            }
            long remaining = amount;
            while (remaining > 0L) {
                if (slices.isEmpty()) {
                    throw new IllegalStateException(
                            "Registered pattern emitted more input than the authorized binding for " + plannedKey);
                }
                ActualSlice slice = slices.first();
                long emitted = Math.min(remaining, slice.remaining);
                sink.pushInput(slice.actualKey, emitted);
                slice.remaining -= emitted;
                remaining -= emitted;
                if (slice.remaining == 0L) {
                    slices.dequeue();
                }
            }
        });

        for (var entry : actualByPlannedKey.entrySet()) {
            if (!entry.getValue().isEmpty()) {
                throw new IllegalStateException(
                        "Registered pattern did not emit the complete authorized binding for " + entry.getKey());
            }
        }
    }

    private static void validateSlot(IPatternDetails.@Nullable IInput plannedInput,
                                     GenericStack plannedTemplate,
                                     int slot) {
        if (plannedInput == null) {
            throw new IllegalArgumentException("Registered pattern input slot " + slot + " is missing");
        }
        boolean declared = Arrays.stream(plannedInput.getPossibleInputs())
                .anyMatch(candidate -> candidate != null &&
                        candidate.amount() == plannedTemplate.amount() &&
                        candidate.what().equals(plannedTemplate.what()));
        if (!declared) {
            throw new IllegalArgumentException(
                    "Bound pattern input slot " + slot + " selected an undeclared planned template");
        }
    }

    private static void validateActualKey(AEKey plannedKey, AEKey actualKey, long amount, int slot) {
        if (amount <= 0L) {
            throw new IllegalArgumentException("Bound pattern input slot " + slot + " contains an invalid amount");
        }
        if (plannedKey.equals(actualKey)) {
            return;
        }
        if (!(plannedKey instanceof AEItemKey plannedItem) || !(actualKey instanceof AEItemKey actualItem) ||
                plannedItem.getItem() != actualItem.getItem()) {
            throw new IllegalArgumentException(
                    "Bound pattern input slot " + slot + " contains an unauthorized key " + actualKey);
        }
    }

    private static final class ActualSlice {

        private final AEKey actualKey;
        private long remaining;

        private ActualSlice(AEKey actualKey, long remaining) {
            this.actualKey = actualKey;
            this.remaining = remaining;
        }
    }
}

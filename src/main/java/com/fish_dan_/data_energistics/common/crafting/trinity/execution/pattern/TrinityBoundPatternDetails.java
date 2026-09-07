package com.fish_dan_.data_energistics.common.crafting.trinity.execution.pattern;

import com.fish_dan_.data_energistics.common.crafting.dynamic.BoundPatternInputEmitter;

import appeng.api.config.Actionable;
import appeng.api.crafting.IPatternDetails;
import appeng.api.crafting.PatternDetailsTooltip;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.GenericStack;
import appeng.api.stacks.KeyCounter;
import appeng.crafting.execution.CraftingCpuHelper;
import appeng.crafting.inv.ICraftingInventory;

import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;

import org.jspecify.annotations.Nullable;

import java.util.List;

/**
 * One server-thread dispatch allocation. Keeps exact physical slices for extraction while retaining the registered
 * pattern's identity and sparse input order. It is never published as a replacement provider pattern.
 */
public final class TrinityBoundPatternDetails implements IPatternDetails {

    private final IPatternDetails delegate;
    private final List<SlotBinding> slots;
    private final IInput[] inputs;
    private final List<GenericStack> plannedTemplates;

    TrinityBoundPatternDetails(IPatternDetails delegate, List<SlotBinding> slots) {
        this.delegate = delegate;
        this.slots = List.copyOf(slots);
        this.inputs = slots.stream().map(BoundInput::new).toArray(IInput[]::new);
        this.plannedTemplates = slots.stream().map(SlotBinding::plannedTemplate).toList();
    }

    /**
     * Whether this allocation can be delivered with the original provider pattern without rewriting emitted keys.
     * Exact extraction bindings exist even when no same-item substitution occurred. Those bindings must not disable
     * native single-craft fallback; genuine component substitutions still require a bound-input-aware provider.
     */
    public boolean preservesNativeInputs(IPatternDetails original) {
        if (this.delegate != original) {
            return false;
        }
        for (SlotBinding slot : this.slots) {
            for (GenericStack actual : slot.actualInputs()) {
                if (!actual.what().equals(slot.plannedTemplate().what())) {
                    return false;
                }
            }
        }
        return true;
    }

    /**
     * Extracts this allocation exactly, rolling back all slices if any is no longer available. Successful extraction
     * appends the original physical outputs and actual container remainders to the supplied counters.
     */
    public KeyCounter @Nullable [] extractInputs(ICraftingInventory inventory,
                                                 KeyCounter expectedOutputs,
                                                 KeyCounter expectedContainerItems) {
        KeyCounter outputs = new KeyCounter();
        KeyCounter containers = new KeyCounter();
        for (SlotBinding slot : this.slots) {
            for (GenericStack slice : slot.actualInputs()) {
                AEKey remainder = slot.delegate().getRemainingKey(slice.what());
                if (remainder != null) {
                    containers.add(remainder, slice.amount() / slot.plannedTemplate().amount());
                }
            }
        }
        for (GenericStack output : this.delegate.getOutputs()) {
            outputs.add(output.what(), output.amount());
        }
        KeyCounter[] extracted = new KeyCounter[this.slots.size()];
        for (int index = 0; index < this.slots.size(); index++) {
            SlotBinding slot = this.slots.get(index);
            KeyCounter owned = extracted[index] = new KeyCounter();
            for (GenericStack slice : slot.actualInputs()) {
                long amount = inventory.extract(slice.what(), slice.amount(), Actionable.MODULATE);
                if (amount < 0L || amount > slice.amount()) {
                    throw new IllegalStateException("Crafting inventory returned an invalid bound input amount");
                }
                owned.add(slice.what(), amount);
                if (amount != slice.amount()) {
                    CraftingCpuHelper.reinjectPatternInputs(inventory, extracted);
                    return null;
                }
            }
        }
        outputs.forEach(entry -> expectedOutputs.add(entry.getKey(), entry.getLongValue()));
        containers.forEach(entry -> expectedContainerItems.add(entry.getKey(), entry.getLongValue()));
        return extracted;
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
    public void pushInputsToExternalInventory(KeyCounter[] inputHolder, PatternInputSink sink) {
        BoundPatternInputEmitter.emit(this.delegate, this.plannedTemplates, inputHolder, sink);
    }

    @Override
    public PatternDetailsTooltip getTooltip(Level level, TooltipFlag flags) {
        return this.delegate.getTooltip(level, flags);
    }

    record SlotBinding(IPatternDetails.IInput delegate, GenericStack plannedTemplate, List<GenericStack> actualInputs) {

        SlotBinding {
            actualInputs = List.copyOf(actualInputs);
        }
    }

    private record BoundInput(SlotBinding slot) implements IPatternDetails.IInput {

        @Override
        public GenericStack[] getPossibleInputs() {
            return this.slot.actualInputs().stream()
                    .map(slice -> new GenericStack(slice.what(), this.slot.plannedTemplate().amount()))
                    .toArray(GenericStack[]::new);
        }

        @Override
        public long getMultiplier() {
            return this.slot.delegate().getMultiplier();
        }

        @Override
        public boolean isValid(AEKey input, Level level) {
            return this.slot.actualInputs().stream().anyMatch(slice -> slice.what().equals(input));
        }

        @Override
        public @Nullable AEKey getRemainingKey(AEKey template) {
            return this.slot.delegate().getRemainingKey(template);
        }
    }
}

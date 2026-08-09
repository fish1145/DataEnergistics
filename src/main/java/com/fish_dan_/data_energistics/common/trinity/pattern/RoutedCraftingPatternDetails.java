package com.fish_dan_.data_energistics.common.trinity.pattern;

import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;

import appeng.api.crafting.IPatternDetails;
import appeng.api.crafting.PatternDetailsTooltip;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.GenericStack;
import appeng.api.stacks.KeyCounter;

import java.util.List;

/**
 * Preserves the exact Trinity pattern slot selected by an AE2 crafting plan while retaining the original pattern
 * behavior.
 */
public final class RoutedCraftingPatternDetails implements IPatternDetails {

    private final PatternRoute route;
    private final IPatternDetails delegate;
    private final AEItemKey definition;

    public RoutedCraftingPatternDetails(PatternRoute route, IPatternDetails delegate) {
        this.route = route;
        this.delegate = delegate;
        this.definition = delegate.getDefinition();
    }

    /**
     * Returns the stable host/core/slot destination for this pattern instance.
     */
    public PatternRoute route() {
        return this.route;
    }

    /**
     * Returns the decoded AE2 pattern whose behavior is delegated by this wrapper.
     */
    public IPatternDetails delegate() {
        return this.delegate;
    }

    @Override
    public AEItemKey getDefinition() {
        return this.definition;
    }

    @Override
    public IInput[] getInputs() {
        return this.delegate.getInputs();
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

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof RoutedCraftingPatternDetails that)) {
            return false;
        }
        return this.route.equals(that.route) && this.definition.equals(that.definition);
    }

    @Override
    public int hashCode() {
        return 31 * this.route.hashCode() + this.definition.hashCode();
    }

    @Override
    public String toString() {
        return "RoutedCraftingPatternDetails[route=" + this.route + ", delegate=" + this.delegate + "]";
    }
}

package com.fish_dan_.data_energistics.mixin.core.crafting;

import com.fish_dan_.data_energistics.common.crafting.virtual.VirtualCraftingOutputAdapters;
import com.fish_dan_.data_energistics.common.crafting.virtual.VirtualCraftingOutputProjection;
import com.fish_dan_.data_energistics.common.crafting.virtual.VirtualCraftingPatternOutputs;

import appeng.api.crafting.IPatternDetails;
import appeng.api.crafting.PatternDetailsTooltip;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.GenericStack;
import appeng.crafting.pattern.AEProcessingPattern;

import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;

import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.List;

/**
 * Captures virtual completion metadata without replacing the declared processing-pattern output exposed to AE2.
 */
@SuppressWarnings("AddedMixinMembersNamePattern")
@Mixin(AEProcessingPattern.class)
public abstract class AEProcessingPatternVirtualOutputMixin implements VirtualCraftingPatternOutputs {

    @Shadow
    @Final
    private List<GenericStack> condensedOutputs;

    @Unique
    private VirtualCraftingOutputProjection dataEnergistics$virtualOutputProjection;

    @Unique
    private List<GenericStack> dataEnergistics$encodedOutputs;

    @Inject(method = "<init>", at = @At("RETURN"))
    private void dataEnergistics$projectVirtualOutputs(AEItemKey definition, CallbackInfo ci) {
        this.dataEnergistics$encodedOutputs = List.copyOf(this.condensedOutputs);
        this.dataEnergistics$virtualOutputProjection = VirtualCraftingOutputAdapters.project(this.condensedOutputs);
    }

    @Override
    public VirtualCraftingOutputProjection dataEnergistics$virtualOutputProjection() {
        return this.dataEnergistics$virtualOutputProjection;
    }

    @Unique
    public PatternDetailsTooltip getTooltip(Level level, TooltipFlag flags) {
        var tooltip = new PatternDetailsTooltip(PatternDetailsTooltip.OUTPUT_TEXT_PRODUCES);
        if (!this.dataEnergistics$virtualOutputProjection.hasVirtualOutputs()) {
            tooltip.addInputsAndOutputs((IPatternDetails) this);
            return tooltip;
        }
        for (var input : ((IPatternDetails) this).getInputs()) {
            GenericStack first = input.getPossibleInputs()[0];
            tooltip.addInput(first.what(), first.amount() * input.getMultiplier());
        }
        this.dataEnergistics$encodedOutputs.forEach(tooltip::addOutput);
        return tooltip;
    }
}

package com.fish_dan_.data_energistics.mixin.core.menu;

import com.fish_dan_.data_energistics.menu.patternencoding.PatternEncodingPreviewLayoutAware;
import com.fish_dan_.data_energistics.util.PatternEncodingPreviewLayoutHelper;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;

import appeng.parts.encoding.PatternEncodingLogic;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(PatternEncodingLogic.class)
public class PatternEncodingLogicPreviewLayoutMixin implements PatternEncodingPreviewLayoutAware {

    @Unique
    private int dataEnergistics$previewPanelOffsetX;
    @Unique
    private int dataEnergistics$previewPanelOffsetY;
    @Unique
    private int dataEnergistics$legacyPreviewPanelOffsetX;
    @Unique
    private int dataEnergistics$legacyPreviewPanelOffsetY;

    @Override
    public int data_energistics$getPreviewPanelOffsetX() {
        return this.dataEnergistics$previewPanelOffsetX;
    }

    @Override
    public int data_energistics$getPreviewPanelOffsetY() {
        return this.dataEnergistics$previewPanelOffsetY;
    }

    @Override
    public void data_energistics$setPreviewPanelOffset(int offsetX, int offsetY) {
        this.dataEnergistics$previewPanelOffsetX = offsetX;
        this.dataEnergistics$previewPanelOffsetY = offsetY;
    }

    @Override
    public void data_energistics$resetPreviewPanelOffset() {
        data_energistics$setPreviewPanelOffset(0, 0);
    }

    @Inject(method = "readFromNBT", at = @At("TAIL"))
    private void dataEnergistics$readPreviewLayout(CompoundTag data, HolderLookup.Provider registries,
                                                   CallbackInfo ci) {
        this.dataEnergistics$legacyPreviewPanelOffsetX = PatternEncodingPreviewLayoutHelper.readOffsetX(data);
        this.dataEnergistics$legacyPreviewPanelOffsetY = PatternEncodingPreviewLayoutHelper.readOffsetY(data);
        this.dataEnergistics$previewPanelOffsetX = this.dataEnergistics$legacyPreviewPanelOffsetX;
        this.dataEnergistics$previewPanelOffsetY = this.dataEnergistics$legacyPreviewPanelOffsetY;
    }

    @Inject(method = "writeToNBT", at = @At("TAIL"))
    private void dataEnergistics$writePreviewLayout(CompoundTag data, HolderLookup.Provider registries,
                                                    CallbackInfo ci) {
        PatternEncodingPreviewLayoutHelper.writeOffset(data,
                this.dataEnergistics$legacyPreviewPanelOffsetX,
                this.dataEnergistics$legacyPreviewPanelOffsetY);
    }
}

package com.fish_dan_.data_energistics.mixin.client.patternencoding;

import com.fish_dan_.data_energistics.client.screen.patternencoding.ProcessingPatternAmountContext;

import appeng.client.gui.me.common.MEStorageScreen;
import appeng.client.gui.me.items.PatternEncodingTermScreen;
import appeng.client.gui.style.ScreenStyle;
import appeng.menu.me.items.PatternEncodingTermMenu;
import appeng.parts.encoding.EncodingMode;

import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.Slot;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Captures the exact processing slot that opened AE2's amount sub-screen. */
@Mixin(PatternEncodingTermScreen.class)
public abstract class PatternEncodingTermScreenMixin extends MEStorageScreen<PatternEncodingTermMenu>
                                                     implements ProcessingPatternAmountContext {

    @Unique
    private boolean dataEnergistics$processingOutputAmountTarget;

    protected PatternEncodingTermScreenMixin(PatternEncodingTermMenu menu,
                                             Inventory playerInventory,
                                             Component title,
                                             ScreenStyle style) {
        super(menu, playerInventory, title, style);
    }

    @Inject(method = "mouseClicked", at = @At("HEAD"))
    private void dataEnergistics$captureProcessingAmountTarget(double xCoord,
                                                               double yCoord,
                                                               int button,
                                                               CallbackInfoReturnable<Boolean> cir) {
        this.dataEnergistics$processingOutputAmountTarget = false;
        if (!Minecraft.getInstance().options.keyPickItem.matchesMouse(button) ||
                this.menu.getMode() != EncodingMode.PROCESSING) {
            return;
        }

        Slot clicked = null;
        for (Slot candidate : this.menu.slots) {
            if (candidate.isActive() && this.isHovering(candidate, xCoord, yCoord)) {
                clicked = candidate;
                break;
            }
        }
        var outputs = this.menu.getProcessingOutputSlots();
        this.dataEnergistics$processingOutputAmountTarget = outputs.length > 0 && clicked == outputs[0];
    }

    @Override
    public boolean data_energistics$isProcessingOutputAmountTarget() {
        return this.dataEnergistics$processingOutputAmountTarget;
    }
}

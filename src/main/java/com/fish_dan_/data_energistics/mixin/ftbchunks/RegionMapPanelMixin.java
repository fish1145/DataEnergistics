package com.fish_dan_.data_energistics.mixin.ftbchunks;

import com.fish_dan_.data_energistics.integration.map.ftbchunks.client.FtbChunksOrbitalAdapter;
import com.fish_dan_.data_energistics.integration.map.ftbchunks.client.FtbChunksOrbitalMapBridge;

import net.minecraft.core.BlockPos;

import dev.ftb.mods.ftbchunks.client.gui.LargeMapScreen;
import dev.ftb.mods.ftbchunks.client.gui.RegionMapPanel;
import dev.ftb.mods.ftblibrary.ui.input.MouseButton;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Distinguishes a true background click from FTB Chunks icon clicks and map dragging before selecting a target. */
@Mixin(value = RegionMapPanel.class, remap = false)
public abstract class RegionMapPanelMixin implements FtbChunksOrbitalMapBridge.Input {

    @Unique
    private boolean dataEnergistics$trackingLeftPress;

    @Unique
    private int dataEnergistics$pressX;

    @Unique
    private int dataEnergistics$pressY;

    @Inject(method = "mousePressed", at = @At("RETURN"), require = 0)
    private void dataEnergistics$trackOrbitalClick(
                                                   MouseButton button,
                                                   CallbackInfoReturnable<Boolean> callback) {
        LargeMapScreen largeMap = ((RegionMapPanelAccessor) this).dataEnergistics$getLargeMap();
        boolean mapBackgroundPressed = callback.getReturnValueZ() &&
                button.isLeft() &&
                ((LargeMapScreenAccessor) largeMap).dataEnergistics$getGrabbed() == 1;
        if (mapBackgroundPressed) {
            this.dataEnergistics$trackingLeftPress = true;
            this.dataEnergistics$pressX = largeMap.getMouseX();
            this.dataEnergistics$pressY = largeMap.getMouseY();
        }
    }

    @Inject(method = "mouseReleased", at = @At("TAIL"), require = 0)
    private void dataEnergistics$completeOrbitalClick(MouseButton button, CallbackInfo callback) {
        if (!button.isLeft() || !this.dataEnergistics$trackingLeftPress) {
            return;
        }
        this.dataEnergistics$trackingLeftPress = false;
        LargeMapScreen largeMap = ((RegionMapPanelAccessor) this).dataEnergistics$getLargeMap();
        if (Math.abs(largeMap.getMouseX() - this.dataEnergistics$pressX) >= 5 ||
                Math.abs(largeMap.getMouseY() - this.dataEnergistics$pressY) >= 5) {
            return;
        }
        BlockPos target = ((RegionMapPanel) (Object) this).blockPos();
        FtbChunksOrbitalAdapter.INSTANCE.completeMapClick(
                largeMap.currentDimension().location(),
                target.getX(),
                target.getZ());
    }
}

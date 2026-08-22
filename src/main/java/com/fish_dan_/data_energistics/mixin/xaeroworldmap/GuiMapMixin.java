package com.fish_dan_.data_energistics.mixin.xaeroworldmap;

import com.fish_dan_.data_energistics.integration.map.xaero.client.XaeroOrbitalRightClickOption;
import com.fish_dan_.data_energistics.integration.map.xaero.client.XaeroWorldMapOrbitalAdapter;

import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;

import org.jspecify.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import xaero.map.gui.GuiMap;
import xaero.map.gui.dropdown.rightclick.RightClickOption;

import java.util.ArrayList;

/** Reads the coordinates already calculated by Xaero and delegates without consuming its normal map behavior. */
@Mixin(value = GuiMap.class, remap = false)
public abstract class GuiMapMixin {

    @Shadow
    private int mouseBlockPosX;

    @Shadow
    private int mouseBlockPosZ;

    @Shadow
    private @Nullable ResourceKey<Level> mouseBlockDim;

    @Shadow
    private int rightClickX;

    @Shadow
    private int rightClickZ;

    @Shadow
    private @Nullable ResourceKey<Level> rightClickDim;

    @Inject(method = "mapClicked", at = @At("TAIL"), require = 0)
    private void dataEnergistics$completeOrbitalSelection(
                                                          int button,
                                                          int x,
                                                          int y,
                                                          CallbackInfo callback) {
        if (button == 0 && this.mouseBlockDim != null) {
            XaeroWorldMapOrbitalAdapter.INSTANCE.completeMapClick(
                    this.mouseBlockDim.location(),
                    this.mouseBlockPosX,
                    this.mouseBlockPosZ);
        }
    }

    @Inject(method = "getRightClickOptions", at = @At("RETURN"), require = 0)
    private void dataEnergistics$appendOrbitalPreview(
                                                      CallbackInfoReturnable<ArrayList<RightClickOption>> callback) {
        if (this.rightClickDim == null || !XaeroWorldMapOrbitalAdapter.INSTANCE.shouldOfferPreviewAction()) {
            return;
        }
        ArrayList<RightClickOption> options = callback.getReturnValue();
        options.add(new XaeroOrbitalRightClickOption(
                (GuiMap) (Object) this,
                options.size(),
                this.rightClickDim.location(),
                this.rightClickX,
                this.rightClickZ));
    }
}

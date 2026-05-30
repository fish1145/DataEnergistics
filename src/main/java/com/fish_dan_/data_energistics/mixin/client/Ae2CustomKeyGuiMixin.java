package com.fish_dan_.data_energistics.mixin.client;

import com.fish_dan_.data_energistics.client.CustomKeyGuiRenderer;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;

import appeng.api.stacks.AEKey;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(targets = {
        "appeng.client.gui.me.common.MEStorageScreen",
        "appeng.client.gui.me.crafting.AbstractTableRenderer",
        "appeng.client.gui.me.networktool.NetworkStatusScreen",
        "appeng.client.gui.me.common.FinishedJobToast",
        "appeng.client.gui.widgets.InfoBar"
}, remap = false)
public abstract class Ae2CustomKeyGuiMixin {

    @Redirect(
              method = "*",
              at = @At(
                       value = "INVOKE",
                       target = "Lappeng/api/client/AEKeyRendering;drawInGui(Lnet/minecraft/client/Minecraft;Lnet/minecraft/client/gui/GuiGraphics;IILappeng/api/stacks/AEKey;)V"),
              require = 0)
    private void dataEnergistics$drawCustomGuiKey(
                                                  Minecraft minecraft,
                                                  GuiGraphics guiGraphics,
                                                  int x,
                                                  int y,
                                                  AEKey key) {
        CustomKeyGuiRenderer.draw(minecraft, guiGraphics, x, y, key);
    }
}

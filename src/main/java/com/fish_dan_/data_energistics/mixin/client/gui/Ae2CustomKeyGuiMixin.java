package com.fish_dan_.data_energistics.mixin.client.gui;

import com.fish_dan_.data_energistics.client.key.CustomKeyGuiRenderer;

import appeng.api.stacks.AEKey;
import appeng.client.gui.me.common.FinishedJobToast;
import appeng.client.gui.me.common.MEStorageScreen;
import appeng.client.gui.me.crafting.AbstractTableRenderer;
import appeng.client.gui.me.networktool.NetworkStatusScreen;
import appeng.client.gui.widgets.InfoBar;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(value = {
        MEStorageScreen.class,
        AbstractTableRenderer.class,
        NetworkStatusScreen.class,
        FinishedJobToast.class,
        InfoBar.class
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

package com.fish_dan_.data_energistics.mixin.client.crafting;

import com.fish_dan_.data_energistics.client.crafting.status.TrinityCraftingStatusText;
import com.fish_dan_.data_energistics.common.crafting.trinity.status.TrinityCraftingStatusEntry;

import appeng.client.gui.me.crafting.CraftingStatusTableRenderer;
import appeng.menu.me.crafting.CraftingStatusEntry;

import net.minecraft.network.chat.Component;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.List;

/**
 * Leaves AE2 table behavior intact while rendering Trinity's exact amounts instead of the bounded compatibility view.
 */
@Mixin(CraftingStatusTableRenderer.class)
public abstract class CraftingStatusTableRendererMixin {

    @Inject(method = "getEntryDescription(Lappeng/menu/me/crafting/CraftingStatusEntry;)Ljava/util/List;", at = @At("HEAD"), cancellable = true)
    private void dataEnergistics$exactDescription(CraftingStatusEntry entry, CallbackInfoReturnable<List<Component>> cir) {
        if (entry instanceof TrinityCraftingStatusEntry exact) {
            cir.setReturnValue(TrinityCraftingStatusText.lines(exact, false));
        }
    }

    @Inject(method = "getEntryTooltip(Lappeng/menu/me/crafting/CraftingStatusEntry;)Ljava/util/List;", at = @At("HEAD"), cancellable = true)
    private void dataEnergistics$exactTooltip(CraftingStatusEntry entry, CallbackInfoReturnable<List<Component>> cir) {
        if (entry instanceof TrinityCraftingStatusEntry exact) {
            cir.setReturnValue(TrinityCraftingStatusText.lines(exact, true));
        }
    }
}

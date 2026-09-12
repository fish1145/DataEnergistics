package com.fish_dan_.data_energistics.mixin.ftblibrary;

import com.lowdragmc.lowdraglib2.gui.holder.ModularUIContainerMenu;
import com.lowdragmc.lowdraglib2.gui.holder.ModularUIContainerScreen;
import com.lowdragmc.lowdraglib2.gui.ui.ModularUI;

import net.minecraft.client.gui.screens.Screen;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Hides FTB Library's sidebar only while the orbital control terminal workspace is open. */
@Mixin(targets = "dev.ftb.mods.ftblibrary.FTBLibraryClient", remap = false)
public abstract class FtbLibrarySidebarMixin {

    @Unique
    private static final String dataEnergistics$ORBITAL_CONTROL_ROOT_ID = "orbital_control_root";

    @Inject(method = "areButtonsVisible", at = @At("HEAD"), cancellable = true, remap = false)
    private static void dataEnergistics$hideOrbitalTerminalSidebar(
                                                                   Screen screen,
                                                                   CallbackInfoReturnable<Boolean> callback) {
        if (dataEnergistics$isOrbitalControlTerminal(screen)) {
            callback.setReturnValue(false);
        }
    }

    @Unique
    private static boolean dataEnergistics$isOrbitalControlTerminal(Screen screen) {
        if (!(screen instanceof ModularUIContainerScreen modularScreen)) {
            return false;
        }
        ModularUIContainerMenu menu = modularScreen.getMenu();
        ModularUI modularUI = menu.getModularUI();
        return modularUI != null && dataEnergistics$ORBITAL_CONTROL_ROOT_ID.equals(modularUI.ui.getRootElement().getId());
    }
}

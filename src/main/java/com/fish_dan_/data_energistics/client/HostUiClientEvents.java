package com.fish_dan_.data_energistics.client;

import com.fish_dan_.data_energistics.Data_Energistics;
import com.fish_dan_.data_energistics.gui.ldlib2.HostModularUI;

import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ScreenEvent;

import com.lowdragmc.lowdraglib2.gui.holder.IModularUIHolder;

/** Routes hosted-window keys before either AE2 or a legacy screen can consume them. */
@OnlyIn(Dist.CLIENT)
@EventBusSubscriber(modid = Data_Energistics.MODID, value = Dist.CLIENT)
public final class HostUiClientEvents {

    private HostUiClientEvents() {}

    /** Closes only the topmost hosted window and cancels the corresponding Screen key event. */
    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onKeyPressed(ScreenEvent.KeyPressed.Pre event) {
        if (event.getScreen() instanceof AbstractContainerScreen<?> screen &&
                screen.getMenu() instanceof IModularUIHolder holder &&
                holder.getModularUI() instanceof HostModularUI modularUI &&
                modularUI.handleKeyPressed(event.getKeyCode(), event.getScanCode(), event.getModifiers())) {
            event.setCanceled(true);
        }
    }
}

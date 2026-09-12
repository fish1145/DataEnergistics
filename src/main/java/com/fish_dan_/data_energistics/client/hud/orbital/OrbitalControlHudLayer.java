package com.fish_dan_.data_energistics.client.hud.orbital;

import com.fish_dan_.data_energistics.Data_Energistics;
import com.fish_dan_.data_energistics.client.hud.orbital.OrbitalControlHudPreferences.DisplayMode;
import com.fish_dan_.data_energistics.client.hud.orbital.rendering.OrbitalHudRenderer;

import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.LayeredDraw;
import net.neoforged.neoforge.client.event.RegisterGuiLayersEvent;

/** Non-interactive compact HUD; the editor and this layer share the same renderer and placement rules. */
public final class OrbitalControlHudLayer implements LayeredDraw.Layer {

    private OrbitalControlHudLayer() {}

    public static void register(RegisterGuiLayersEvent event) {
        event.registerAboveAll(Data_Energistics.id("orbital_control_hud"), new OrbitalControlHudLayer());
    }

    @Override
    public void render(GuiGraphics graphics, DeltaTracker deltaTracker) {
        Minecraft client = Minecraft.getInstance();
        var weapon = OrbitalControlHudClientState.weapon();
        var preferences = OrbitalControlHudPreferences.current();
        if (client.options.hideGui || client.screen != null || weapon == null || preferences.mode() == DisplayMode.HIDDEN) {
            return;
        }
        OrbitalHudRenderer.render(graphics, client.font, preferences, weapon,
                client.getWindow().getGuiScaledWidth(), client.getWindow().getGuiScaledHeight());
    }
}

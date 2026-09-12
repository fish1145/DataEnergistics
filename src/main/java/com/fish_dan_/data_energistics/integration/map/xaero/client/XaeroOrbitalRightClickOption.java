package com.fish_dan_.data_energistics.integration.map.xaero.client;

import net.minecraft.client.gui.screens.Screen;
import net.minecraft.resources.ResourceLocation;

import xaero.map.gui.GuiMap;
import xaero.map.gui.dropdown.rightclick.RightClickOption;

/** Immutable Xaero popup action that forwards only the captured dimension/X/Z to the common preview flow. */
public final class XaeroOrbitalRightClickOption extends RightClickOption {

    private static final String TRANSLATION_KEY = "screen.data_energistics.orbital_control_terminal.fire_control.map.preview";

    private final ResourceLocation dimensionId;
    private final int targetX;
    private final int targetZ;

    public XaeroOrbitalRightClickOption(
                                        GuiMap map,
                                        int index,
                                        ResourceLocation dimensionId,
                                        int targetX,
                                        int targetZ) {
        super(TRANSLATION_KEY, index, map);
        this.dimensionId = dimensionId;
        this.targetX = targetX;
        this.targetZ = targetZ;
    }

    @Override
    public void onAction(Screen ignoredScreen) {
        XaeroWorldMapOrbitalAdapter.INSTANCE.openRightClickPreview(
                this.dimensionId,
                this.targetX,
                this.targetZ);
    }
}

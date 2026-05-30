package com.fish_dan_.data_energistics.client.widget;

import com.fish_dan_.data_energistics.Data_Energistics;

import net.minecraft.resources.ResourceLocation;

import appeng.client.gui.style.Blitter;

public final class DataEnergisticsGuiTextures {

    private static final ResourceLocation OUTPUT_SIDE_ICONS = ResourceLocation.fromNamespaceAndPath(Data_Energistics.MODID, "textures/guis/output_side_icons.png");

    private DataEnergisticsGuiTextures() {}

    public static Blitter outputSidesIcon() {
        return Blitter.texture(OUTPUT_SIDE_ICONS, 64, 64).src(0, 16, 16, 16);
    }
}

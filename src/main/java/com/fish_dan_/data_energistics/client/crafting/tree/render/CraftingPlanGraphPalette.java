package com.fish_dan_.data_energistics.client.crafting.tree.render;

import net.minecraft.util.Mth;

/** Colors shared by the tree window, graph and PNG export, taken from the existing Trinity UI. */
public final class CraftingPlanGraphPalette {
    // Trinity home/status-panel borders and CPU-entry surfaces.
    public static final int CANVAS = 0xFF9A9FB4;
    public static final int FRAME = 0xFF696D88;
    public static final int MATERIAL = 0xFFF2F2F2;
    public static final int PROCESS = 0xFFCBCCD4;
    public static final int BUTTON = 0xFFADB0C4;
    public static final int BUTTON_HOVER = 0xFF9CD3FF;
    public static final int SELECTED = 0xFF9CD3FF;
    public static final int GRID = 0x18696D88;
    public static final int GRID_ACCENT = 0x30696D88;

    // Trinity UI text and information accents; progression textures provide semantic state colors.
    public static final int TEXT = 0xFF343548;
    public static final int MUTED_TEXT = 0xFF555568;
    public static final int ACCENT = 0xFF246082;
    public static final int EDGE = 0xFF4D4D67;
    public static final int MISSING = 0xFFB01F1F;
    public static final int STORED = 0xFF267A15;

    /** Stable, separated cycle accents using the saturation/value range of Trinity's purple progress accent. */
    static int cycle(int ordinal) {
        double hue = 0.70 + ordinal * 0.3819660112501051;
        return 0xFF000000 | Mth.hsvToRgb((float) (hue - Math.floor(hue)), 0.58F, 0.56F);
    }

    private CraftingPlanGraphPalette() {}
}

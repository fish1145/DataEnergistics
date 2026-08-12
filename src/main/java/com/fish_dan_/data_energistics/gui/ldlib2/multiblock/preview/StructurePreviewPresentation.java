package com.fish_dan_.data_energistics.gui.ldlib2.multiblock.preview;

/**
 * Declares the two shared preview compositions without coupling the panel to a particular host or recipe viewer.
 */
public enum StructurePreviewPresentation {

    /**
     * Full hosted-window presentation with the canonical material strip.
     */
    HOSTED(184, true),
    /**
     * Compact recipe-viewer presentation whose recipe slots are owned by the XEI composition.
     */
    XEI(158, false);

    /**
     * Shared panel and scene width.
     */
    public static final int WIDTH = 196;
    /**
     * Height reserved for the shared three-dimensional scene.
     */
    public static final int SCENE_HEIGHT = 128;
    /**
     * Top edge of the recipe-affecting control rail.
     */
    public static final int CONTROL_RAIL_TOP = 130;
    /**
     * Height of the recipe-affecting control rail.
     */
    public static final int CONTROL_RAIL_HEIGHT = 28;
    /**
     * Height available to controls after reserving LDLib2's five-pixel horizontal scrollbar.
     */
    public static final int CONTROL_CONTENT_HEIGHT = 23;
    /**
     * Top edge of the hosted material strip.
     */
    public static final int MATERIAL_STRIP_TOP = 160;
    /**
     * Height of the hosted material strip.
     */
    public static final int MATERIAL_STRIP_HEIGHT = 24;

    private final int height;
    private final boolean materialStrip;

    StructurePreviewPresentation(int height, boolean materialStrip) {
        this.height = height;
        this.materialStrip = materialStrip;
    }

    /**
     * Returns the exact panel height required by this composition.
     */
    public int height() {
        return this.height;
    }

    /**
     * Returns whether this composition owns and renders the canonical material strip.
     */
    public boolean hasMaterialStrip() {
        return this.materialStrip;
    }
}

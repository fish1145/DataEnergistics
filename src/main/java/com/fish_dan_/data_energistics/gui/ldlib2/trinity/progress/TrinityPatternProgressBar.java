package com.fish_dan_.data_energistics.gui.ldlib2.trinity.progress;

import com.fish_dan_.data_energistics.Data_Energistics;

import net.minecraft.resources.ResourceLocation;

import com.lowdragmc.lowdraglib2.gui.texture.SpriteTexture;
import com.lowdragmc.lowdraglib2.gui.ui.UIElement;
import dev.vfyjxf.taffy.style.TaffyPosition;

/** Renders an authored pattern-progress track with a fixed-phase, repeated fill texture. */
public final class TrinityPatternProgressBar extends UIElement {

    private static final String TEXTURE_ROOT = "textures/guis/trinity/progress/";
    private static final int VERTICAL_WIDTH = 10;
    private static final int VERTICAL_HEIGHT = 154;
    private static final int VERTICAL_LANE_LEFT = 4;
    private static final int VERTICAL_LANE_TOP = 3;
    private static final int VERTICAL_LANE_LENGTH = 148;
    private static final int VERTICAL_LANE_SIZE = 2;
    private static final int VERTICAL_TILE_WIDTH = 2;
    private static final int VERTICAL_TILE_HEIGHT = 4;

    private final UIElement reveal = new UIElement();
    private final UIElement fill = new UIElement();

    private TrinityPatternProgressBar(String id) {
        setId(id);
        setAllowHitTest(false);
        style(style -> style.backgroundTexture(sprite("track_vertical", VERTICAL_WIDTH, VERTICAL_HEIGHT)));
        layout(layout -> layout.width(VERTICAL_WIDTH).height(VERTICAL_HEIGHT));

        this.reveal.setId(id + "_reveal");
        this.reveal.setAllowHitTest(false);
        this.reveal.setOverflowVisible(false);
        this.fill.setId(id + "_fill");
        this.fill.setAllowHitTest(false);
        this.reveal.addChild(this.fill);
        addChild(this.reveal);
        updateReveal(0);
    }

    public static TrinityPatternProgressBar vertical(String id) {
        return new TrinityPatternProgressBar(id);
    }

    /** Shows {@code progress} of the selected state while preserving the fill texture's track-relative phase. */
    public void setProgress(float progress, TrinityPatternProgressAppearance appearance) {
        this.fill.style(style -> style.backgroundTexture(SpriteTexture
                .of(appearance.verticalTexture())
                .setSprite(0, 0, VERTICAL_TILE_WIDTH, VERTICAL_TILE_HEIGHT)
                .setWrapMode(SpriteTexture.WrapMode.REPEAT)));
        updateReveal(Math.round(progress * VERTICAL_LANE_LENGTH));
    }

    private void updateReveal(int filledPixels) {
        int hiddenPixels = VERTICAL_LANE_LENGTH - filledPixels;
        place(
                this.reveal,
                VERTICAL_LANE_LEFT,
                VERTICAL_LANE_TOP + hiddenPixels,
                VERTICAL_LANE_SIZE,
                filledPixels);
        place(this.fill, 0, -hiddenPixels, VERTICAL_LANE_SIZE, VERTICAL_LANE_LENGTH);
    }

    private static SpriteTexture sprite(String name, int width, int height) {
        ResourceLocation texture = ResourceLocation.fromNamespaceAndPath(
                Data_Energistics.MODID,
                TEXTURE_ROOT + name + ".png");
        return SpriteTexture.of(texture).setSprite(0, 0, width, height);
    }

    private static void place(UIElement element, int left, int top, int width, int height) {
        element.layout(layout -> layout
                .positionType(TaffyPosition.ABSOLUTE)
                .left(left)
                .top(top)
                .width(width)
                .height(height));
    }
}

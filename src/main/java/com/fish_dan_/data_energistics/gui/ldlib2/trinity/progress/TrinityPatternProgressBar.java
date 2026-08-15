package com.fish_dan_.data_energistics.gui.ldlib2.trinity.progress;

import com.fish_dan_.data_energistics.Data_Energistics;

import net.minecraft.resources.ResourceLocation;

import com.lowdragmc.lowdraglib2.gui.texture.SpriteTexture;
import com.lowdragmc.lowdraglib2.gui.ui.UIElement;
import dev.vfyjxf.taffy.style.TaffyPosition;
import org.jspecify.annotations.Nullable;

/** Renders an authored pattern-progress track with a fixed-phase, repeated fill texture. */
public final class TrinityPatternProgressBar extends UIElement {

    private static final String TEXTURE_ROOT = "textures/guis/trinity/progress/";
    private static final Geometry HORIZONTAL = new Geometry(175, 10, 3, 4, 169, 2, 4, 2);
    private static final Geometry VERTICAL = new Geometry(10, 154, 4, 3, 148, 2, 2, 4);

    private final Orientation orientation;
    private final Geometry geometry;
    private final UIElement reveal = new UIElement();
    private final UIElement fill = new UIElement();
    private @Nullable TrinityPatternProgressAppearance appearance;

    private TrinityPatternProgressBar(String id, Orientation orientation, Geometry geometry, String trackTexture) {
        this.orientation = orientation;
        this.geometry = geometry;
        setId(id);
        setAllowHitTest(false);
        style(style -> style.backgroundTexture(sprite(trackTexture, geometry.width(), geometry.height())));
        layout(layout -> layout.width(geometry.width()).height(geometry.height()));

        this.reveal.setId(id + "_reveal");
        this.reveal.setAllowHitTest(false);
        this.reveal.setOverflowVisible(false);
        this.fill.setId(id + "_fill");
        this.fill.setAllowHitTest(false);
        this.reveal.addChild(this.fill);
        addChild(this.reveal);
        updateReveal(0);
    }

    public static TrinityPatternProgressBar horizontal(String id) {
        return new TrinityPatternProgressBar(id, Orientation.HORIZONTAL, HORIZONTAL, "track_horizontal");
    }

    public static TrinityPatternProgressBar vertical(String id) {
        return new TrinityPatternProgressBar(id, Orientation.VERTICAL, VERTICAL, "track_vertical");
    }

    /** Shows {@code progress} of the selected state while preserving the fill texture's track-relative phase. */
    public void setProgress(float progress, TrinityPatternProgressAppearance appearance) {
        if (this.appearance != appearance) {
            ResourceLocation texture = this.orientation == Orientation.HORIZONTAL ? appearance.horizontalTexture() : appearance.verticalTexture();
            this.fill.style(style -> style.backgroundTexture(SpriteTexture
                    .of(texture)
                    .setSprite(0, 0, this.geometry.tileWidth(), this.geometry.tileHeight())
                    .setWrapMode(SpriteTexture.WrapMode.REPEAT)));
            this.appearance = appearance;
        }
        updateReveal(Math.round(progress * this.geometry.laneLength()));
    }

    /** Hides the repeated fill while retaining the authored track background. */
    public void clearProgress() {
        updateReveal(0);
    }

    private void updateReveal(int filledPixels) {
        if (this.orientation == Orientation.HORIZONTAL) {
            place(
                    this.reveal,
                    this.geometry.laneLeft(),
                    this.geometry.laneTop(),
                    filledPixels,
                    this.geometry.laneSize());
            place(this.fill, 0, 0, this.geometry.laneLength(), this.geometry.laneSize());
            return;
        }
        int hiddenPixels = this.geometry.laneLength() - filledPixels;
        place(
                this.reveal,
                this.geometry.laneLeft(),
                this.geometry.laneTop() + hiddenPixels,
                this.geometry.laneSize(),
                filledPixels);
        place(this.fill, 0, -hiddenPixels, this.geometry.laneSize(), this.geometry.laneLength());
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

    private enum Orientation {
        HORIZONTAL,
        VERTICAL
    }

    private record Geometry(
                            int width,
                            int height,
                            int laneLeft,
                            int laneTop,
                            int laneLength,
                            int laneSize,
                            int tileWidth,
                            int tileHeight) {}
}

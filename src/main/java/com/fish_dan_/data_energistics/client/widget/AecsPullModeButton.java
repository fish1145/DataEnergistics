package com.fish_dan_.data_energistics.client.widget;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.renderer.Rect2i;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

import appeng.client.gui.Icon;
import appeng.client.gui.style.Blitter;
import appeng.client.gui.widgets.ITooltip;

import java.util.List;
import java.util.function.Consumer;

public class AecsPullModeButton extends Button implements ITooltip {

    private static final ResourceLocation TEXTURE = ResourceLocation.fromNamespaceAndPath("ae2cs", "textures/gui/icons.png");
    private static final int ICON_SIZE = 16;
    private static final int TEXTURE_SIZE = 256;

    private final String titleKey;
    private final String enabledKey;
    private final String disabledKey;
    private final Consumer<Boolean> onChange;
    private boolean state;

    public AecsPullModeButton(
                              String titleKey,
                              String enabledKey,
                              String disabledKey,
                              Consumer<Boolean> onChange) {
        super(0, 0, ICON_SIZE, ICON_SIZE, Component.empty(), btn -> {}, DEFAULT_NARRATION);
        this.titleKey = titleKey;
        this.enabledKey = enabledKey;
        this.disabledKey = disabledKey;
        this.onChange = onChange;
    }

    public void setState(boolean state) {
        this.state = state;
    }

    public void setVisibility(boolean visible) {
        this.visible = visible;
        this.active = visible;
    }

    @Override
    public void onPress() {
        this.onChange.accept(!this.state);
    }

    @Override
    protected void renderWidget(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        if (!this.visible) {
            return;
        }

        int yOffset = isHovered() ? 1 : 0;
        Icon background = isHovered() ? Icon.TOOLBAR_BUTTON_BACKGROUND_HOVER : isFocused() ? Icon.TOOLBAR_BUTTON_BACKGROUND_FOCUS : Icon.TOOLBAR_BUTTON_BACKGROUND;

        background.getBlitter()
                .dest(getX() - 1, getY() + yOffset, 18, 20)
                .zOffset(2)
                .blit(guiGraphics);

        int textureX = this.state ? 0 : 16;
        Blitter blitter = Blitter.texture(TEXTURE, TEXTURE_SIZE, TEXTURE_SIZE).src(textureX, 0, ICON_SIZE, ICON_SIZE);
        if (!this.active) {
            blitter.opacity(0.5f);
        }
        blitter.dest(getX(), getY() + 1 + yOffset).zOffset(3).blit(guiGraphics);
    }

    @Override
    public List<Component> getTooltipMessage() {
        return List.of(
                Component.translatable(this.titleKey),
                Component.translatable(this.state ? this.enabledKey : this.disabledKey));
    }

    @Override
    public Rect2i getTooltipArea() {
        return new Rect2i(getX(), getY(), ICON_SIZE, ICON_SIZE);
    }

    @Override
    public boolean isTooltipAreaVisible() {
        return this.visible;
    }
}

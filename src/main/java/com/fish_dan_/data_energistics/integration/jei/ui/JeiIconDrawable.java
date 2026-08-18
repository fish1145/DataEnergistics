package com.fish_dan_.data_energistics.integration.jei.ui;

import net.minecraft.client.gui.GuiGraphics;

import appeng.client.gui.Icon;
import mezz.jei.api.gui.drawable.IDrawable;

public class JeiIconDrawable implements IDrawable {

    private final Icon icon;

    public JeiIconDrawable(Icon icon) {
        this.icon = icon;
    }

    @Override
    public int getWidth() {
        return this.icon.width;
    }

    @Override
    public int getHeight() {
        return this.icon.height;
    }

    @Override
    public void draw(GuiGraphics guiGraphics, int xOffset, int yOffset) {
        this.icon.getBlitter().dest(xOffset, yOffset).blit(guiGraphics);
    }
}

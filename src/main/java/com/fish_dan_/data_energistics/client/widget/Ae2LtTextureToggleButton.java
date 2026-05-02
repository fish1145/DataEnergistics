package com.fish_dan_.data_energistics.client.widget;

import appeng.client.gui.Icon;
import appeng.client.gui.style.Blitter;
import appeng.client.gui.widgets.ITooltip;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.renderer.Rect2i;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class Ae2LtTextureToggleButton extends Button implements ITooltip {
    private final List<ResourceLocation> textures;
    private final List<List<Component>> tooltips;
    private final Listener listener;
    private int stateIndex;

    public Ae2LtTextureToggleButton(ButtonType type, Listener listener) {
        super(0, 0, 16, 16, Component.empty(), btn -> listener.onChange(0), DEFAULT_NARRATION);
        this.textures = type.textures;
        this.tooltips = new ArrayList<>(type.textures.size());
        for (int i = 0; i < type.textures.size(); i++) {
            this.tooltips.add(Collections.emptyList());
        }
        this.listener = listener;
    }

    private static ResourceLocation texture(String path) {
        return ResourceLocation.fromNamespaceAndPath("ae2lt", "textures/gui/buttons/" + path + ".png");
    }

    public void setStateIndex(int index) {
        if (this.textures.isEmpty()) {
            this.stateIndex = 0;
            return;
        }

        if (index < 0) {
            index = 0;
        }
        if (index >= this.textures.size()) {
            index = this.textures.size() - 1;
        }
        this.stateIndex = index;
    }

    public void setState(boolean isOn) {
        setStateIndex(isOn ? 1 : 0);
    }

    public void setEjectState() {
        setStateIndex(2);
    }

    public void setTooltipAt(int index, List<Component> lines) {
        if (index < 0 || index >= this.tooltips.size()) {
            return;
        }
        this.tooltips.set(index, lines == null ? Collections.emptyList() : lines);
    }

    public void setTooltipOn(List<Component> lines) {
        setTooltipAt(1, lines);
    }

    public void setTooltipOff(List<Component> lines) {
        setTooltipAt(0, lines);
    }

    public void setTooltipEject(List<Component> lines) {
        setTooltipAt(2, lines);
    }

    public void setVisibility(boolean visible) {
        this.visible = visible;
        this.active = visible;
    }

    @Override
    public void onPress() {
        this.listener.onChange(this.stateIndex);
    }

    @Override
    protected void renderWidget(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        if (!this.visible) {
            return;
        }

        int yOffset = isHovered() ? 1 : 0;
        Icon background = isHovered() ? Icon.TOOLBAR_BUTTON_BACKGROUND_HOVER
                : isFocused() ? Icon.TOOLBAR_BUTTON_BACKGROUND_FOCUS : Icon.TOOLBAR_BUTTON_BACKGROUND;

        background.getBlitter()
                .dest(getX() - 1, getY() + yOffset, 18, 20)
                .zOffset(2)
                .blit(guiGraphics);

        if (this.textures.isEmpty()) {
            return;
        }

        int idx = Math.min(this.stateIndex, this.textures.size() - 1);
        Blitter blitter = Blitter.texture(this.textures.get(idx), 16, 16).src(0, 0, 16, 16);
        if (!this.active) {
            blitter.opacity(0.5f);
        }
        blitter.dest(getX(), getY() + 1 + yOffset).zOffset(3).blit(guiGraphics);
    }

    @Override
    public List<Component> getTooltipMessage() {
        if (this.tooltips.isEmpty()) {
            return Collections.emptyList();
        }
        int idx = Math.min(this.stateIndex, this.tooltips.size() - 1);
        return this.tooltips.get(idx);
    }

    @Override
    public Rect2i getTooltipArea() {
        return new Rect2i(getX(), getY(), 16, 16);
    }

    @Override
    public boolean isTooltipAreaVisible() {
        return this.visible && !getTooltipMessage().isEmpty();
    }

    public enum ButtonType {
        MODE(texture("wired_mode"), texture("wireless_mode")),
        AUTO_RETURN(texture("auto_input_off"), texture("auto_input_on"), texture("auto_input_ejection")),
        WIRELESS_STRATEGY(texture("single_target"), texture("even_distribution")),
        FILTERED_IMPORT(texture("filtered_import_off"), texture("filtered_import_on")),
        SPEED(texture("speed_normal"), texture("speed_fast"));

        private final List<ResourceLocation> textures;

        ButtonType(ResourceLocation textureOff, ResourceLocation textureOn) {
            this.textures = List.of(textureOff, textureOn);
        }

        ButtonType(ResourceLocation textureOff, ResourceLocation textureOn, ResourceLocation textureEject) {
            this.textures = List.of(textureOff, textureOn, textureEject);
        }
    }

    @FunctionalInterface
    public interface Listener {
        void onChange(int previousStateIndex);
    }
}

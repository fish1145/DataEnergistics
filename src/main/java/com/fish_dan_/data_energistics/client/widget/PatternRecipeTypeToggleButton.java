package com.fish_dan_.data_energistics.client.widget;

import com.fish_dan_.data_energistics.client.gui.DataEnergisticsIcon;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.renderer.Rect2i;
import net.minecraft.network.chat.Component;

import appeng.client.gui.widgets.ITooltip;
import org.jetbrains.annotations.NotNull;

import java.util.List;

public class PatternRecipeTypeToggleButton extends Button implements ITooltip {

    private final StateChangeHandler onChange;
    private boolean enabledState = true;
    private Component detailLine = Component.empty();

    public PatternRecipeTypeToggleButton(@NotNull StateChangeHandler onChange) {
        super(0, 0, 8, 8, Component.empty(), btn -> {}, DEFAULT_NARRATION);
        this.onChange = onChange;
    }

    public void setState(boolean enabled) {
        this.enabledState = enabled;
    }

    public void setDetailLine(@NotNull Component detailLine) {
        this.detailLine = detailLine;
    }

    @Override
    public void onPress() {
        this.enabledState = !this.enabledState;
        this.onChange.accept(this.enabledState);
    }

    @Override
    protected void renderWidget(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        if (!this.visible) {
            return;
        }

        DataEnergisticsIcon.getBlitter(this.enabledState ? "S_PATTERN_ENCODING_ENABLED" : "S_PATTERN_ENCODING_DISABLED")
                .dest(getX(), getY())
                .blit(guiGraphics);
    }

    @Override
    public List<Component> getTooltipMessage() {
        return List.of(
                Component.translatable(this.enabledState ? "button.data_energistics.pattern_encoding_recipe_type_toggle.enabled" : "button.data_energistics.pattern_encoding_recipe_type_toggle.disabled"),
                this.detailLine);
    }

    @Override
    public Rect2i getTooltipArea() {
        return new Rect2i(getX(), getY(), width, height);
    }

    @Override
    public boolean isTooltipAreaVisible() {
        return this.visible;
    }

    /**
     * Receives primitive toggle state without allocating a boxed {@link Boolean}.
     */
    @FunctionalInterface
    public interface StateChangeHandler {

        /**
         * Applies the newly selected state.
         */
        void accept(boolean enabled);
    }
}

package com.fish_dan_.data_energistics.client.screen.terminal;

import appeng.client.gui.Icon;
import appeng.client.gui.widgets.ITooltip;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.renderer.Rect2i;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

import org.jspecify.annotations.Nullable;

import java.util.List;
import java.util.function.BooleanSupplier;
import java.util.function.Supplier;

public class UniversalTerminalCycleButton extends Button implements ITooltip {

    private final Supplier<ItemStack> iconSupplier;
    private final Supplier<List<Component>> tooltipSupplier;
    private final BooleanSupplier selectedSupplier;
    private final @Nullable Supplier<int[]> fallbackPositionSupplier;

    public UniversalTerminalCycleButton(OnPress onPress, Supplier<ItemStack> iconSupplier,
                                        Supplier<List<Component>> tooltipSupplier,
                                        BooleanSupplier selectedSupplier) {
        this(onPress, iconSupplier, tooltipSupplier, selectedSupplier, null);
    }

    public UniversalTerminalCycleButton(OnPress onPress, Supplier<ItemStack> iconSupplier,
                                        Supplier<List<Component>> tooltipSupplier,
                                        BooleanSupplier selectedSupplier,
                                        @Nullable Supplier<int[]> fallbackPositionSupplier) {
        super(0, 0, 16, 16, Component.empty(), onPress, Button.DEFAULT_NARRATION);
        this.iconSupplier = iconSupplier;
        this.tooltipSupplier = tooltipSupplier;
        this.selectedSupplier = selectedSupplier;
        this.fallbackPositionSupplier = fallbackPositionSupplier;
    }

    @Override
    protected void renderWidget(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        if (!this.visible) {
            return;
        }

        if (this.fallbackPositionSupplier != null) {
            int[] fallbackPosition = this.fallbackPositionSupplier.get();
            if (fallbackPosition != null && fallbackPosition.length >= 2) {
                if (this.getX() <= 0) {
                    this.setX(fallbackPosition[0]);
                }
                if (this.getY() <= 0) {
                    this.setY(fallbackPosition[1]);
                }
            }
        }

        int yOffset = this.isHovered() ? 1 : 0;
        boolean selected = this.selectedSupplier.getAsBoolean();
        Icon background = this.isHovered() ? Icon.TOOLBAR_BUTTON_BACKGROUND_HOVER : ((selected || this.isFocused()) ? Icon.TOOLBAR_BUTTON_BACKGROUND_FOCUS : Icon.TOOLBAR_BUTTON_BACKGROUND);

        background.getBlitter().dest(this.getX() - 1, this.getY() + yOffset, 18, 20).zOffset(2).blit(guiGraphics);

        ItemStack iconStack = this.iconSupplier.get();
        if (!iconStack.isEmpty()) {
            guiGraphics.renderItem(iconStack, this.getX(), this.getY() + 1 + yOffset, 0, 3);
            Icon.OVERLAY_ON.getBlitter().dest(this.getX() + 8, this.getY() + 9 + yOffset, 8, 8).zOffset(4).blit(guiGraphics);
        }
    }

    @Override
    public List<Component> getTooltipMessage() {
        return List.copyOf(this.tooltipSupplier.get());
    }

    @Override
    public Rect2i getTooltipArea() {
        return new Rect2i(this.getX(), this.getY(), 16, 16);
    }

    @Override
    public boolean isTooltipAreaVisible() {
        return this.visible;
    }
}

package com.fish_dan_.data_energistics.client.ui.machine;

import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

import appeng.client.gui.Icon;
import appeng.client.gui.style.Blitter;
import com.lowdragmc.lowdraglib2.gui.ui.elements.Button;
import com.lowdragmc.lowdraglib2.gui.ui.event.HoverTooltips;
import com.lowdragmc.lowdraglib2.gui.ui.event.UIEvents;
import com.lowdragmc.lowdraglib2.gui.ui.rendering.GUIContext;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.function.BooleanSupplier;
import java.util.function.Supplier;

/**
 * Reuses AE2 toolbar sprites inside LDLib2 mouse handling for machine commands and side selectors.
 */
final class DataReassemblerIconButtonElement extends Button {

    private final Supplier<Blitter> iconSupplier;
    private final Supplier<ItemStack> itemSupplier;
    private final Supplier<List<Component>> tooltipSupplier;
    private final BooleanSupplier selectedSupplier;
    private final boolean small;

    DataReassemblerIconButtonElement(
                                     Supplier<Blitter> iconSupplier,
                                     Supplier<ItemStack> itemSupplier,
                                     Supplier<List<Component>> tooltipSupplier,
                                     BooleanSupplier selectedSupplier,
                                     boolean small,
                                     Runnable onClick) {
        this.iconSupplier = iconSupplier;
        this.itemSupplier = itemSupplier;
        this.tooltipSupplier = tooltipSupplier;
        this.selectedSupplier = selectedSupplier;
        this.small = small;
        noText();
        setOnClick(event -> onClick.run());
        addEventListener(UIEvents.HOVER_TOOLTIPS, event -> {
            List<Component> tooltip = this.tooltipSupplier.get();
            event.hoverTooltips = new HoverTooltips(List.copyOf(tooltip), null, null, ItemStack.EMPTY);
        });
        getLayout().width(small ? 8 : 16);
        getLayout().height(small ? 8 : 16);
        getLayout().paddingAll(0);
    }

    @Override
    public void drawBackgroundAdditional(GUIContext guiContext) {
        int x = Math.round(getPositionX());
        int y = Math.round(getPositionY());
        boolean hovered = getState() != State.DEFAULT;
        int yOffset = hovered && !this.small ? 1 : 0;

        if (!this.small) {
            Icon background = hovered ? Icon.TOOLBAR_BUTTON_BACKGROUND_HOVER : this.selectedSupplier.getAsBoolean() ? Icon.TOOLBAR_BUTTON_BACKGROUND_FOCUS : Icon.TOOLBAR_BUTTON_BACKGROUND;
            background.getBlitter().dest(x - 1, y + yOffset, 18, 20).zOffset(2).blit(guiContext.graphics);
        }

        ItemStack item = this.itemSupplier.get();
        if (!item.isEmpty()) {
            guiContext.graphics.renderItem(item, x, y + yOffset + (this.small ? 0 : 1), 0, 3);
        } else {
            @Nullable
            Blitter icon = this.iconSupplier.get();
            if (icon != null) {
                icon.dest(x, y + yOffset + (this.small ? 0 : 1))
                        .zOffset(this.small ? 20 : 3)
                        .blit(guiContext.graphics);
            }
        }
    }
}

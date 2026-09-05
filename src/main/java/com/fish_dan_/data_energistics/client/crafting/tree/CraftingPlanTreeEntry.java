package com.fish_dan_.data_energistics.client.crafting.tree;

import com.fish_dan_.data_energistics.menu.crafting.tree.session.CraftingPlanSessionTransfer;

import appeng.client.gui.me.crafting.CraftConfirmScreen;
import appeng.client.gui.style.WidgetStyle;
import appeng.client.gui.widgets.AE2Button;

import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.renderer.Rect2i;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.client.event.ScreenEvent;

/** Adds one independently positioned action between the native cancel/start controls. */
public final class CraftingPlanTreeEntry {

    private CraftingPlanTreeEntry() {}

    public static void onInit(ScreenEvent.Init.Post event) {
        if (event.getScreen() instanceof CraftConfirmScreen screen) event.addListener(new EntryButton(screen));
    }

    public static void refresh(CraftConfirmScreen screen) {
        for (var listener : screen.children()) if (listener instanceof EntryButton button) button.refresh();
    }

    private static final class EntryButton extends AE2Button {

        private final CraftConfirmScreen screen;
        private final WidgetStyle cancelStyle;
        private final WidgetStyle startStyle;
        private final Tooltip readyTooltip;
        private final Tooltip loadingTooltip;

        private EntryButton(CraftConfirmScreen screen) {
            super(0, 0, 80, 20, Component.translatable("gui.data_energistics.plan_tree.open"),
                    button -> ((CraftingPlanSessionTransfer) screen.getMenu()).data_energistics$openPlanTree());
            this.screen = screen;
            this.cancelStyle = screen.getStyle().getWidget("cancel");
            this.startStyle = screen.getStyle().getWidget("start");
            this.readyTooltip = Tooltip.create(getMessage());
            this.loadingTooltip = Tooltip.create(Component.translatable("gui.data_energistics.plan_tree.loading"));
            refresh();
        }

        private void refresh() {
            CraftingPlanSessionTransfer state = (CraftingPlanSessionTransfer) this.screen.getMenu();
            this.visible = state.data_energistics$hasTrinityCpu();
            this.active = this.visible && state.data_energistics$isTreeReady();
            Rect2i bounds = new Rect2i(this.screen.getGuiLeft(), this.screen.getGuiTop(), this.screen.getXSize(), this.screen.getYSize());
            var cancel = this.cancelStyle.resolve(bounds);
            var start = this.startStyle.resolve(bounds);
            // Center the whole button in the native action gap, not the asymmetrical screen background.
            setX((cancel.getX() + this.cancelStyle.getWidth() + start.getX() - getWidth()) / 2);
            setY(start.getY());
            setTooltip(this.active ? this.readyTooltip : this.loadingTooltip);
        }
    }
}

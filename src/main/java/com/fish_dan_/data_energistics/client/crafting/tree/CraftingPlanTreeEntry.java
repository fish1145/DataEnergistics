package com.fish_dan_.data_energistics.client.crafting.tree;

import com.fish_dan_.data_energistics.menu.crafting.tree.session.CraftingPlanSessionTransfer;

import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.client.event.ScreenEvent;

import appeng.client.gui.me.crafting.CraftConfirmScreen;

/** Adds one independently positioned action between the native cancel/start controls. */
public final class CraftingPlanTreeEntry {

    private CraftingPlanTreeEntry() {}

    public static void onInit(ScreenEvent.Init.Post event) {
        if (event.getScreen() instanceof CraftConfirmScreen screen) event.addListener(new EntryButton(screen));
    }

    public static void refresh(CraftConfirmScreen screen) {
        for (var listener : screen.children()) if (listener instanceof EntryButton button) button.refresh();
    }

    private static final class EntryButton extends Button {

        private final CraftConfirmScreen screen;

        private EntryButton(CraftConfirmScreen screen) {
            super(screen.getGuiLeft() + (screen.getXSize() - 80) / 2, screen.getGuiTop() + screen.getYSize() - 25,
                    80, 20, Component.translatable("gui.data_energistics.plan_tree.open"),
                    button -> ((CraftingPlanSessionTransfer) screen.getMenu()).data_energistics$openPlanTree(), DEFAULT_NARRATION);
            this.screen = screen;
            refresh();
        }

        private void refresh() {
            CraftingPlanSessionTransfer state = (CraftingPlanSessionTransfer) this.screen.getMenu();
            this.visible = state.data_energistics$hasTrinityCpu();
            this.active = this.visible && state.data_energistics$isTreeReady();
            setX(this.screen.getGuiLeft() + (this.screen.getXSize() - 80) / 2);
            setY(this.screen.getGuiTop() + this.screen.getYSize() - 25);
            setTooltip(Tooltip.create(Component.translatable(this.active ? "gui.data_energistics.plan_tree.open" : "gui.data_energistics.plan_tree.loading")));
        }
    }
}

package com.fish_dan_.data_energistics.client.screen.trinity;

import com.fish_dan_.data_energistics.gui.ldlib2.trinity.core.TrinityDataCoreHostUiKeys;
import com.fish_dan_.data_energistics.menu.trinity.TrinityDataCoreMenu;

import com.lowdragmc.lowdraglib2.gui.holder.IModularUIHolderMenu;
import com.lowdragmc.lowdraglib2.gui.ui.ModularUI;
import com.lowdragmc.lowdraglib2.gui.ui.elements.ItemSlot;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.inventory.Slot;

import java.util.LinkedHashSet;
import java.util.Set;

/** Vanilla container shell whose complete presentation and interaction tree is owned by LDLib2. */
public class TrinityDataCoreScreen extends AbstractContainerScreen<TrinityDataCoreMenu> {

    private static final double SWEEP_SAMPLE_DISTANCE = 8.0D;

    private final Set<Integer> quickMoveSweepSlots = new LinkedHashSet<>();
    private boolean quickMoveSweepActive;
    private double previousSweepMouseX;
    private double previousSweepMouseY;

    public TrinityDataCoreScreen(TrinityDataCoreMenu menu, Inventory playerInventory, Component title) {
        super(menu, playerInventory, title);
    }

    @Override
    protected void init() {
        ModularUI modularUI = modularUI();
        this.imageWidth = (int) modularUI.getWidth();
        this.imageHeight = (int) modularUI.getHeight();
        super.init();
        setFocused(modularUI.getWidget());
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (this.menu.getHostUiExtension().handleKeyPressed(keyCode, scanCode, modifiers)) {
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        boolean handled = super.mouseClicked(mouseX, mouseY, button);
        if (button == 0 && hasShiftDown() && this.menu.getCarried().isEmpty() && patternWindowOpen()) {
            Slot slot = nativePlayerSlotAt(mouseX, mouseY);
            if (slot != null) {
                this.quickMoveSweepActive = true;
                this.quickMoveSweepSlots.clear();
                this.quickMoveSweepSlots.add(slot.index);
                this.previousSweepMouseX = mouseX;
                this.previousSweepMouseY = mouseY;
            }
        }
        return handled;
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        boolean handled = super.mouseDragged(mouseX, mouseY, button, dragX, dragY);
        if (!this.quickMoveSweepActive || button != 0) {
            return handled;
        }
        if (!hasShiftDown() || !this.menu.getCarried().isEmpty() || !patternWindowOpen()) {
            clearQuickMoveSweep();
            return handled;
        }

        double deltaX = mouseX - this.previousSweepMouseX;
        double deltaY = mouseY - this.previousSweepMouseY;
        int samples = Math.max(1, (int) Math.ceil(Math.hypot(deltaX, deltaY) / SWEEP_SAMPLE_DISTANCE));
        for (int sample = 1; sample <= samples; sample++) {
            double progress = sample / (double) samples;
            quickMoveNativeSlotAt(
                    this.previousSweepMouseX + deltaX * progress,
                    this.previousSweepMouseY + deltaY * progress);
        }
        this.previousSweepMouseX = mouseX;
        this.previousSweepMouseY = mouseY;
        return true;
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        boolean handled = super.mouseReleased(mouseX, mouseY, button);
        if (button == 0) {
            clearQuickMoveSweep();
        }
        return handled;
    }

    private void quickMoveNativeSlotAt(double mouseX, double mouseY) {
        Slot slot = nativePlayerSlotAt(mouseX, mouseY);
        if (slot != null && this.quickMoveSweepSlots.add(slot.index) && slot.hasItem()) {
            slotClicked(slot, slot.index, 0, ClickType.QUICK_MOVE);
        }
    }

    private Slot nativePlayerSlotAt(double mouseX, double mouseY) {
        var hit = modularUI().ui.rootElement.hitTest(mouseX, mouseY);
        if (hit == null || !(hit.getA() instanceof ItemSlot itemSlot)) {
            return null;
        }
        Slot slot = itemSlot.getSlot();
        return slot.index >= 0 && slot.index < this.menu.slots.size() && this.menu.slots.get(slot.index) == slot &&
                slot.container == this.menu.getPlayer().getInventory() ? slot : null;
    }

    private boolean patternWindowOpen() {
        return this.menu.getHostUiExtension().isOpen(TrinityDataCoreHostUiKeys.PATTERN);
    }

    private void clearQuickMoveSweep() {
        this.quickMoveSweepActive = false;
        this.quickMoveSweepSlots.clear();
    }

    @Override
    protected void renderBg(GuiGraphics graphics, float partialTick, int mouseX, int mouseY) {}

    @Override
    protected void renderLabels(GuiGraphics graphics, int mouseX, int mouseY) {}

    private ModularUI modularUI() {
        if (this.menu instanceof IModularUIHolderMenu holder) {
            ModularUI modularUI = holder.getModularUI();
            if (modularUI != null) {
                return modularUI;
            }
        }
        throw new IllegalStateException("Trinity Data Core screen requires a mounted LDLib2 ModularUI");
    }
}

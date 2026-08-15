package com.fish_dan_.data_energistics.client.screen.machine;

import com.fish_dan_.data_energistics.client.CustomKeyGuiRenderer;
import com.fish_dan_.data_energistics.client.screen.GenericStackLookupScreen;
import com.fish_dan_.data_energistics.menu.storage.OrderPackageMenu;
import com.fish_dan_.data_energistics.registry.DEItems;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.Rect2i;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.Slot;

import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.GenericStack;
import appeng.client.gui.AEBaseScreen;
import appeng.client.gui.StackWithBounds;
import appeng.client.gui.style.ScreenStyle;
import org.jspecify.annotations.Nullable;

/**
 * Configures and displays the raw generic target of an order package.
 */
public final class OrderPackageScreen extends AEBaseScreen<OrderPackageMenu> implements GenericStackLookupScreen {

    /** Creates the screen using the authoritative 176 by 144 order-package layout. */
    public OrderPackageScreen(OrderPackageMenu menu, Inventory playerInventory, Component title, ScreenStyle style) {
        super(menu, playerInventory, title, style);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 1 && isTargetSlot(this.hoveredSlot)) {
            this.menu.clearTarget();
            return true;
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public void renderSlot(GuiGraphics guiGraphics, Slot slot) {
        AEKey target = getTarget(slot);
        if (target == null) {
            super.renderSlot(guiGraphics, slot);
            return;
        }

        if (target instanceof AEItemKey itemKey && itemKey.is(DEItems.ORDER_PACKAGE.get())) {
            guiGraphics.renderItem(DEItems.ORDER_PACKAGE.toStack(), slot.x, slot.y);
        } else {
            CustomKeyGuiRenderer.draw(this.minecraft, guiGraphics, slot.x, slot.y, target);
        }
    }

    @Override
    public @Nullable StackWithBounds dataEnergistics$getGenericStackUnderMouse(double mouseX, double mouseY) {
        AEKey target = getTarget(this.hoveredSlot);
        if (target == null) {
            return null;
        }
        return new StackWithBounds(
                new GenericStack(target, 1L),
                new Rect2i(this.leftPos + this.hoveredSlot.x, this.topPos + this.hoveredSlot.y, 16, 16));
    }

    private @Nullable AEKey getTarget(@Nullable Slot slot) {
        if (!isTargetSlot(slot)) {
            return null;
        }
        return this.menu.getHost().getTargetInventory().getKey(0);
    }

    private boolean isTargetSlot(@Nullable Slot slot) {
        return slot != null && slot.isActive() && this.menu.getSlotSemantic(slot) == OrderPackageMenu.TARGET;
    }
}

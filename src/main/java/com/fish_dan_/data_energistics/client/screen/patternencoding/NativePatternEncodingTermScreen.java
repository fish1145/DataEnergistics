package com.fish_dan_.data_energistics.client.screen.patternencoding;

import com.fish_dan_.data_energistics.ae2.DEAE2Keys;
import com.fish_dan_.data_energistics.client.screen.GenericStackLookupScreen;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.Rect2i;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

import appeng.api.stacks.AEKey;
import appeng.api.stacks.GenericStack;
import appeng.client.gui.StackWithBounds;
import appeng.client.gui.style.ScreenStyle;
import appeng.core.definitions.AEItems;
import appeng.items.misc.WrappedGenericStack;
import appeng.menu.me.items.PatternEncodingTermMenu;
import appeng.menu.slot.FakeSlot;
import appeng.parts.encoding.EncodingMode;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public class NativePatternEncodingTermScreen extends PatternEncodingPreviewScreen<PatternEncodingTermMenu>
                                             implements GenericStackLookupScreen {

    private static final int AE2_PREVIEW_PANEL_Y_OFFSET = 105;
    private static final int AE2_SEARCH_BOX_X = 42;
    private static final int AE2_SEARCH_BOX_Y = 6;

    public NativePatternEncodingTermScreen(PatternEncodingTermMenu menu, Inventory playerInventory,
                                           Component title, ScreenStyle style) {
        super(menu, playerInventory, title, style);
    }

    @Override
    protected int getPreviewPanelYOffset() {
        return AE2_PREVIEW_PANEL_Y_OFFSET;
    }

    @Override
    protected int getSearchBoxX() {
        return AE2_SEARCH_BOX_X;
    }

    @Override
    protected int getSearchBoxY() {
        return AE2_SEARCH_BOX_Y;
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    protected void renderTooltip(GuiGraphics guiGraphics, int mouseX, int mouseY) {
        super.renderTooltip(guiGraphics, mouseX, mouseY);
    }

    @Override
    public void renderSlot(GuiGraphics guiGraphics, Slot slot) {
        super.renderSlot(guiGraphics, slot);
    }

    @Override
    public @Nullable StackWithBounds dataEnergistics$getGenericStackUnderMouse(double mouseX, double mouseY) {
        Slot slot = this.hoveredSlot;
        if (!isProcessingInputSlot(slot)) {
            return null;
        }
        GenericStack wrappedKeyStack = getWrappedKeyStack(slot);
        if (wrappedKeyStack == null) {
            return null;
        }
        return new StackWithBounds(wrappedKeyStack, new Rect2i(this.leftPos + slot.x, this.topPos + slot.y, 16, 16));
    }

    private boolean isWrappedGenericProxyItem(ItemStack stack) {
        return !stack.isEmpty() && AEItems.WRAPPED_GENERIC_STACK.is(stack);
    }

    private List<FakeSlot> getVisibleProcessingInputSlots() {
        FakeSlot[] processingInputSlots = this.menu.getProcessingInputSlots();
        if (processingInputSlots == null || processingInputSlots.length == 0) {
            return List.of();
        }

        List<FakeSlot> visibleSlots = new ArrayList<>(processingInputSlots.length);
        for (FakeSlot slot : processingInputSlots) {
            if (slot != null && slot.isActive()) {
                visibleSlots.add(slot);
            }
        }
        visibleSlots.sort(Comparator.comparingInt((FakeSlot slot) -> slot.y).thenComparingInt(slot -> slot.x));
        return visibleSlots;
    }

    private boolean isProcessingInputSlot(@Nullable Slot slot) {
        if (slot == null || this.menu.getMode() != EncodingMode.PROCESSING) {
            return false;
        }
        for (FakeSlot inputSlot : getVisibleProcessingInputSlots()) {
            if (inputSlot == slot) {
                return true;
            }
        }
        return false;
    }

    private @Nullable GenericStack getWrappedKeyStack(@Nullable Slot slot) {
        if (slot == null || !slot.isActive() || !isWrappedGenericProxyItem(slot.getItem())) {
            return null;
        }
        WrappedGenericStack wrappedGenericStack = AEItems.WRAPPED_GENERIC_STACK.asItem();
        AEKey what = wrappedGenericStack.unwrapWhat(slot.getItem());
        long amount = wrappedGenericStack.unwrapAmount(slot.getItem());
        if (!DEAE2Keys.isCustomKey(what) || amount <= 0) {
            return null;
        }
        return new GenericStack(what, amount);
    }
}

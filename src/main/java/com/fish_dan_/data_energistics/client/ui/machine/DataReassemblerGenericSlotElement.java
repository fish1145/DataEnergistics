package com.fish_dan_.data_energistics.client.ui.machine;

import com.fish_dan_.data_energistics.Data_Energistics;
import com.fish_dan_.data_energistics.client.CustomKeyGuiRenderer;
import com.fish_dan_.data_energistics.client.GenericStackDisplayHelper;

import net.minecraft.client.renderer.Rect2i;
import net.minecraft.network.chat.Component;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

import appeng.api.client.AEKeyRendering;
import appeng.api.stacks.GenericStack;
import appeng.client.gui.StackWithBounds;
import appeng.core.localization.Tooltips;
import com.lowdragmc.lowdraglib2.gui.ui.elements.ItemSlot;
import com.lowdragmc.lowdraglib2.gui.ui.event.HoverTooltips;
import com.lowdragmc.lowdraglib2.gui.ui.event.UIEvent;
import com.lowdragmc.lowdraglib2.gui.ui.rendering.GUIContext;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.function.LongSupplier;
import java.util.function.Supplier;

/**
 * Maps an existing generic menu slot while rendering its synchronized AE key instead of its wrapper item.
 */
final class DataReassemblerGenericSlotElement extends ItemSlot {

    private final DataRipperReassemblerMachineUiState.GenericStorage storage;
    private final Supplier<GenericStack> stackSupplier;
    private final LongSupplier capacitySupplier;

    DataReassemblerGenericSlotElement(
                                      Slot slot,
                                      DataRipperReassemblerMachineUiState.GenericStorage storage,
                                      Supplier<GenericStack> stackSupplier,
                                      LongSupplier capacitySupplier) {
        super(slot);
        this.storage = storage;
        this.stackSupplier = stackSupplier;
        this.capacitySupplier = capacitySupplier;
        DataRipperReassemblerMachineUiProviderImpl.configureSlot(this, false);
    }

    /** Suppresses LDLib's item-viewer provider for the AE2 wrapper item. */
    @Override
    public ItemStack getValue() {
        return ItemStack.EMPTY;
    }

    @Override
    public void drawBackgroundAdditional(GUIContext guiContext) {
        GenericStack stack = validatedStack();
        if (stack != null) {
            int x = Math.round(getContentX());
            int y = Math.round(getContentY());
            CustomKeyGuiRenderer.draw(guiContext.mc, guiContext.graphics, x, y, stack.what());
            GenericStackDisplayHelper.renderSmallOverlay(
                    guiContext.graphics,
                    Math.round(getPositionX()),
                    Math.round(getPositionY()),
                    GenericStackDisplayHelper.formatCompactAmount(stack));
        }
        super.drawBackgroundAdditional(guiContext);
    }

    @Override
    protected void onHoverTooltips(UIEvent event) {
        event.hoverTooltips = new HoverTooltips(buildTooltip(), null, null, ItemStack.EMPTY);
    }

    List<Component> buildTooltip() {
        GenericStack stack = validatedStack();
        List<Component> tooltip = new ArrayList<>();
        long amount = 0;
        if (stack == null) {
            tooltip.add(Component.translatable(this.storage.isFluid() ? "screen.data_energistics.data_reassembler.fluid.empty" : "screen.data_energistics.data_reassembler.key.empty"));
        } else {
            tooltip.addAll(AEKeyRendering.getTooltip(stack.what()));
            amount = stack.amount();
        }

        long capacity = this.capacitySupplier.getAsLong();
        String suffix = this.storage.isFluid() ? " mB" : "";
        tooltip.add(Component.literal(amount + suffix + " / " + capacity + suffix)
                .withStyle(Tooltips.NORMAL_TOOLTIP_TEXT));
        return List.copyOf(tooltip);
    }

    /** Returns the hovered generic stack with its absolute 16x16 content bounds. */
    @Nullable
    StackWithBounds stackUnderMouse(double mouseX, double mouseY) {
        GenericStack stack = validatedStack();
        if (stack == null || !isMouseOverContent((float) mouseX, (float) mouseY)) {
            return null;
        }
        return new StackWithBounds(
                stack,
                new Rect2i(Math.round(getContentX()), Math.round(getContentY()), 16, 16));
    }

    private @Nullable GenericStack validatedStack() {
        GenericStack stack = this.stackSupplier.get();
        if (stack == null) {
            return null;
        }
        if (stack.amount() <= 0) {
            Data_Energistics.LOGGER.error("Invalid generic stack supplied for data reassembler {}: {}", this.storage, stack);
            throw new IllegalStateException("Generic data reassembler stack must have a key and positive amount");
        }
        return stack;
    }
}

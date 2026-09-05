package com.fish_dan_.data_energistics.client.screen.machine;

import com.fish_dan_.data_energistics.menu.machine.DataAsynchronousProcessingFactoryMenu;

import appeng.api.stacks.GenericStack;
import appeng.client.gui.style.ScreenStyle;
import appeng.client.gui.widgets.ProgressBar;
import appeng.core.localization.Tooltips;
import appeng.menu.SlotSemantic;
import appeng.menu.interfaces.IProgressProvider;

import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.Slot;

import org.jspecify.annotations.Nullable;

public final class DataAsynchronousProcessingFactoryScreen
                                                           extends DataRipperReassemblerScreen<DataAsynchronousProcessingFactoryMenu> {

    private final ProgressBar middleProgressBar;
    private final ProgressBar rightProgressBar;

    public DataAsynchronousProcessingFactoryScreen(DataAsynchronousProcessingFactoryMenu menu,
                                                   Inventory playerInventory,
                                                   Component title,
                                                   ScreenStyle style) {
        super(menu, playerInventory, title, style);
        this.middleProgressBar = new ProgressBar(this.menu.getMiddleProgressProvider(), style.getImage("progressBar"),
                ProgressBar.Direction.VERTICAL);
        this.rightProgressBar = new ProgressBar(this.menu.getRightProgressProvider(), style.getImage("progressBar"),
                ProgressBar.Direction.VERTICAL);
        this.widgets.add("progressBarMiddle", this.middleProgressBar);
        this.widgets.add("progressBarRight", this.rightProgressBar);
    }

    @Override
    protected void updateBeforeRender() {
        super.updateBeforeRender();
        updateProgressBar(this.middleProgressBar, this.menu.getMiddleProgressProvider());
        updateProgressBar(this.rightProgressBar, this.menu.getRightProgressProvider());
    }

    private static void updateProgressBar(ProgressBar progressBar, IProgressProvider progressProvider) {
        progressBar.visible = progressProvider.getMaxProgress() > 0;
        if (!progressBar.visible) {
            return;
        }

        int percent = progressProvider.getCurrentProgress() * 100 / Math.max(1, progressProvider.getMaxProgress());
        progressBar.setFullMsg(Component.literal(percent + "%"));
    }

    @Override
    protected boolean isGenericSemantic(SlotSemantic semantic) {
        return super.isGenericSemantic(semantic) || semantic == DataAsynchronousProcessingFactoryMenu.FLUID_INPUT_LEFT ||
                semantic == DataAsynchronousProcessingFactoryMenu.FLUID_INPUT_RIGHT ||
                isKeyInputSemantic(semantic) ||
                semantic == DataAsynchronousProcessingFactoryMenu.FLUID_OUTPUT_LEFT ||
                semantic == DataAsynchronousProcessingFactoryMenu.FLUID_OUTPUT_RIGHT ||
                isKeyOutputSemantic(semantic);
    }

    @Override
    protected Component getEmptySlotTooltip(SlotSemantic semantic) {
        if (isKeyInputSemantic(semantic) || isKeyOutputSemantic(semantic)) {
            return Component.translatable("screen.data_energistics.data_reassembler.key.empty");
        }
        return super.getEmptySlotTooltip(semantic);
    }

    @Override
    protected Component getAmountTooltip(SlotSemantic semantic, long amount) {
        if (semantic == DataAsynchronousProcessingFactoryMenu.FLUID_INPUT_LEFT ||
                semantic == DataAsynchronousProcessingFactoryMenu.FLUID_INPUT_RIGHT) {
            return Component.literal(amount + " mB / " + this.menu.getFluidInputCapacity() + " mB")
                    .withStyle(Tooltips.NORMAL_TOOLTIP_TEXT);
        }
        if (semantic == DataAsynchronousProcessingFactoryMenu.FLUID_OUTPUT_LEFT ||
                semantic == DataAsynchronousProcessingFactoryMenu.FLUID_OUTPUT_RIGHT) {
            return Component.literal(amount + " mB / " + this.menu.getFluidOutputCapacity() + " mB")
                    .withStyle(Tooltips.NORMAL_TOOLTIP_TEXT);
        }
        if (isKeyInputSemantic(semantic)) {
            return Component.literal(amount + " / " + this.menu.getKeyInputCapacity())
                    .withStyle(Tooltips.NORMAL_TOOLTIP_TEXT);
        }
        if (isKeyOutputSemantic(semantic)) {
            return Component.literal(amount + " / " + this.menu.getKeyOutputCapacity())
                    .withStyle(Tooltips.NORMAL_TOOLTIP_TEXT);
        }
        return super.getAmountTooltip(semantic, amount);
    }

    @Override
    protected @Nullable GenericStack getDisplayedGenericStack(@Nullable Slot slot) {
        if (slot == null || !slot.isActive()) {
            return null;
        }

        SlotSemantic semantic = this.menu.getSlotSemantic(slot);
        if (semantic == DataAsynchronousProcessingFactoryMenu.FLUID_INPUT_LEFT) {
            return switch (slot.y) {
                case 18 -> fluidStack(this.menu.fluidInputAId, this.menu.fluidInputAAmount);
                case 36 -> fluidStack(this.menu.fluidInputCId, this.menu.fluidInputCAmount);
                case 54 -> fluidStack(this.menu.fluidInputEId, this.menu.fluidInputEAmount);
                default -> null;
            };
        }
        if (semantic == DataAsynchronousProcessingFactoryMenu.FLUID_INPUT_RIGHT) {
            return switch (slot.y) {
                case 18 -> fluidStack(this.menu.fluidInputBId, this.menu.fluidInputBAmount);
                case 36 -> fluidStack(this.menu.fluidInputDId, this.menu.fluidInputDAmount);
                case 54 -> fluidStack(this.menu.fluidInputFId, this.menu.fluidInputFAmount);
                default -> null;
            };
        }
        if (semantic == DataAsynchronousProcessingFactoryMenu.FLUID_OUTPUT_LEFT) {
            return switch (slot.y) {
                case 100 -> fluidStack(this.menu.fluidOutputAId, this.menu.fluidOutputAAmount);
                case 118 -> fluidStack(this.menu.fluidOutputCId, this.menu.fluidOutputCAmount);
                default -> null;
            };
        }
        if (semantic == DataAsynchronousProcessingFactoryMenu.FLUID_OUTPUT_RIGHT) {
            return switch (slot.y) {
                case 100 -> fluidStack(this.menu.fluidOutputBId, this.menu.fluidOutputBAmount);
                case 118 -> fluidStack(this.menu.fluidOutputDId, this.menu.fluidOutputDAmount);
                default -> null;
            };
        }
        if (isKeyInputSemantic(semantic) || isKeyOutputSemantic(semantic)) {
            return GenericStack.fromItemStack(slot.getItem());
        }
        return super.getDisplayedGenericStack(slot);
    }

    private static boolean isKeyInputSemantic(SlotSemantic semantic) {
        return semantic == DataAsynchronousProcessingFactoryMenu.KEY_INPUT_TOP ||
                semantic == DataAsynchronousProcessingFactoryMenu.KEY_INPUT_MIDDLE ||
                semantic == DataAsynchronousProcessingFactoryMenu.KEY_INPUT_BOTTOM;
    }

    private static boolean isKeyOutputSemantic(SlotSemantic semantic) {
        return semantic == DataAsynchronousProcessingFactoryMenu.KEY_OUTPUT_TOP ||
                semantic == DataAsynchronousProcessingFactoryMenu.KEY_OUTPUT_BOTTOM;
    }
}

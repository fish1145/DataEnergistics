package com.fish_dan_.data_energistics.client.screen;

import com.fish_dan_.data_energistics.Data_Energistics;
import com.fish_dan_.data_energistics.client.ui.machine.DataRipperReassemblerMachineUiProviderImpl;
import com.fish_dan_.data_energistics.client.ui.machine.DataRipperReassemblerMachineUiState;
import com.fish_dan_.data_energistics.client.ui.machine.DataRipperReassemblerMachineUiStateImpl;
import com.fish_dan_.data_energistics.menu.DataRipperReassemblerMenu;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.PacketDistributor;

import appeng.api.behaviors.ContainerItemStrategies;
import appeng.api.behaviors.EmptyingAction;
import appeng.api.stacks.GenericStack;
import appeng.client.gui.StackWithBounds;
import appeng.core.localization.ButtonToolTips;
import appeng.core.localization.Tooltips;
import appeng.core.network.ServerboundPacket;
import appeng.core.network.serverbound.InventoryActionPacket;
import appeng.core.network.serverbound.SwapSlotsPacket;
import appeng.helpers.InventoryAction;
import appeng.menu.slot.AppEngSlot;
import appeng.menu.slot.DisabledSlot;
import appeng.util.ConfigMenuInventory;
import com.google.common.base.Stopwatch;
import com.lowdragmc.lowdraglib2.gui.holder.IModularUIHolderMenu;
import com.lowdragmc.lowdraglib2.gui.ui.ModularUI;
import com.lowdragmc.lowdraglib2.gui.ui.UIElement;
import com.mojang.blaze3d.platform.InputConstants;
import org.jetbrains.annotations.Nullable;
import org.lwjgl.glfw.GLFW;

import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * Thin vanilla container bridge that mounts the data reassembler's LDLib2 ModularUI on its existing menu.
 */
public final class DataRipperReassemblerScreen extends AbstractContainerScreen<DataRipperReassemblerMenu>
                                               implements GenericStackLookupScreen {

    private static final int IMAGE_WIDTH = 176;
    private static final int IMAGE_HEIGHT = 183;

    private final DataRipperReassemblerMachineUiProviderImpl uiProvider;
    private final ModularUI modularUI;
    private Stopwatch doubleClickTimer = Stopwatch.createStarted();
    private ItemStack doubleClickItem = ItemStack.EMPTY;
    private Slot doubleClickedSlot;
    private boolean handlingShiftClick;

    public DataRipperReassemblerScreen(
                                       DataRipperReassemblerMenu menu,
                                       Inventory playerInventory,
                                       Component title) {
        super(menu, playerInventory, title);
        this.imageWidth = IMAGE_WIDTH;
        this.imageHeight = IMAGE_HEIGHT;

        if (!(menu instanceof IModularUIHolderMenu holder)) {
            Data_Energistics.LOGGER.error("LDLib2 did not attach IModularUIHolderMenu to the data reassembler menu");
            throw new IllegalStateException("Data reassembler menu is missing its LDLib2 holder");
        }

        DataRipperReassemblerMachineUiState state = new DataRipperReassemblerMachineUiStateImpl(
                menu,
                playerInventory,
                title);
        this.uiProvider = new DataRipperReassemblerMachineUiProviderImpl();
        this.modularUI = this.uiProvider.createModularUI(state);
        holder.setModularUI(this.modularUI);
        this.uiProvider.mapExistingSlots(holder);
    }

    @Override
    protected void init() {
        super.init();
        setFocused(this.modularUI.getWidget());
        this.uiProvider.updateMappedSlotPositions();
    }

    @Override
    public boolean shouldCloseOnEsc() {
        return !this.uiProvider.isOutputDialogOpen();
    }

    @Override
    protected void renderBg(GuiGraphics guiGraphics, float partialTick, int mouseX, int mouseY) {}

    @Override
    protected void renderLabels(GuiGraphics guiGraphics, int mouseX, int mouseY) {}

    @Override
    protected void renderTooltip(GuiGraphics guiGraphics, int mouseX, int mouseY) {
        EmptyingAction emptyingAction = getEmptyingAction(this.hoveredSlot, this.menu.getCarried());
        if (emptyingAction != null) {
            guiGraphics.renderTooltip(
                    this.font,
                    Tooltips.getEmptyingTooltip(
                            ButtonToolTips.SetAction,
                            this.menu.getCarried(),
                            emptyingAction)
                            .stream()
                            .map(Component::getVisualOrderText)
                            .toList(),
                    mouseX,
                    mouseY);
        }
    }

    @Override
    protected boolean hasClickedOutside(double mouseX, double mouseY, int screenX, int screenY, int button) {
        return remainsOutside(
                super.hasClickedOutside(mouseX, mouseY, screenX, screenY, button),
                this.modularUI.getLastHoveredElement());
    }

    static boolean remainsOutside(boolean vanillaOutside, @Nullable UIElement hoveredElement) {
        return vanillaOutside && hoveredElement == null;
    }

    @Override
    protected void slotClicked(@Nullable Slot slot, int slotIdx, int mouseButton, ClickType clickType) {
        if (this.menu.isClientSideSlot(slot) || slot instanceof DisabledSlot) {
            return;
        }
        if (clickType == ClickType.CLONE && slot != null && GenericStack.isWrapped(slot.getItem())) {
            return;
        }
        if (clickType == ClickType.PICKUP && mouseButton == InputConstants.MOUSE_BUTTON_RIGHT && getEmptyingAction(slot, this.menu.getCarried()) != null) {
            PacketDistributor.sendToServer(new InventoryActionPacket(InventoryAction.EMPTY_ITEM, slotIdx, 0));
            return;
        }
        if (slot != null && InputConstants.isKeyDown(this.minecraft.getWindow().getWindow(), GLFW.GLFW_KEY_SPACE)) {
            PacketDistributor.sendToServer(new InventoryActionPacket(InventoryAction.MOVE_REGION, slot.index, 0));
            return;
        }
        if (slot != null && !this.handlingShiftClick && hasShiftDown() && mouseButton == 0) {
            this.handlingShiftClick = true;
            if (this.doubleClickItem.isEmpty() || this.doubleClickedSlot != slot || this.doubleClickTimer.elapsed(TimeUnit.MILLISECONDS) > 250L) {
                this.doubleClickedSlot = slot;
                this.doubleClickTimer = Stopwatch.createStarted();
                this.doubleClickItem = slot.hasItem() ? slot.getItem().copy() : ItemStack.EMPTY;
            } else {
                for (Slot inventorySlot : this.menu.slots) {
                    if (inventorySlot.mayPickup(this.menu.getPlayerInventory().player) && inventorySlot.hasItem() && isSameInventory(inventorySlot, slot) && AbstractContainerMenu.canItemQuickReplace(
                            inventorySlot,
                            this.doubleClickItem,
                            true)) {
                        slotClicked(inventorySlot, inventorySlot.index, 0, ClickType.QUICK_MOVE);
                    }
                }
                this.doubleClickItem = ItemStack.EMPTY;
            }
            this.handlingShiftClick = false;
        }
        super.slotClicked(slot, slotIdx, mouseButton, clickType);
    }

    @Override
    protected boolean checkHotbarKeyPressed(int keyCode, int scanCode) {
        Slot slot = this.hoveredSlot;
        if (!this.menu.getCarried().isEmpty() || slot == null) {
            return false;
        }
        if (this.minecraft.options.keySwapOffhand.matches(keyCode, scanCode)) {
            slotClicked(slot, slot.index, Inventory.SLOT_OFFHAND, ClickType.SWAP);
            return true;
        }
        for (int hotbarSlot = 0; hotbarSlot < Inventory.getSelectionSize(); hotbarSlot++) {
            if (!this.minecraft.options.keyHotbarSlots[hotbarSlot].matches(keyCode, scanCode)) {
                continue;
            }
            List<Slot> slots = this.menu.slots;
            for (Slot playerSlot : slots) {
                if (playerSlot.getContainerSlot() == hotbarSlot && playerSlot.container == this.menu.getPlayerInventory() && !playerSlot.mayPickup(this.menu.getPlayerInventory().player)) {
                    return false;
                }
            }
            if (!requiresSwapSlotsPacket(slot)) {
                slotClicked(slot, slot.index, hotbarSlot, ClickType.SWAP);
                return true;
            }
            for (Slot playerSlot : slots) {
                if (playerSlot.getContainerSlot() == hotbarSlot && playerSlot.container == this.menu.getPlayerInventory()) {
                    ServerboundPacket packet = new SwapSlotsPacket(playerSlot.index, slot.index);
                    PacketDistributor.sendToServer(packet);
                    return true;
                }
            }
        }
        return false;
    }

    static boolean requiresSwapSlotsPacket(Slot slot) {
        return slot.getMaxStackSize() != 64;
    }

    private static boolean isSameInventory(Slot first, Slot second) {
        return first.container == second.container;
    }

    static @Nullable EmptyingAction getEmptyingAction(@Nullable Slot slot, ItemStack carried) {
        if (!(slot instanceof AppEngSlot appEngSlot) || carried.isEmpty()) {
            return null;
        }
        if (!(appEngSlot.getInventory() instanceof ConfigMenuInventory configInventory)) {
            return null;
        }
        EmptyingAction emptyingAction = ContainerItemStrategies.getEmptyingAction(carried);
        return validateEmptyingAction(configInventory, slot.getContainerSlot(), emptyingAction);
    }

    static @Nullable EmptyingAction validateEmptyingAction(
                                                           ConfigMenuInventory configInventory,
                                                           int slot,
                                                           @Nullable EmptyingAction emptyingAction) {
        if (emptyingAction == null) {
            return null;
        }
        return configInventory.getDelegate().isAllowedIn(slot, emptyingAction.what()) ? emptyingAction : null;
    }

    @Override
    public @Nullable StackWithBounds dataEnergistics$getGenericStackUnderMouse(double mouseX, double mouseY) {
        return this.uiProvider.getGenericStackUnderMouse(mouseX, mouseY);
    }
}

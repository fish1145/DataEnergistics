package com.fish_dan_.data_energistics.client.screen.powered;

import com.fish_dan_.data_energistics.client.registry.DEKeyMappings;
import com.fish_dan_.data_energistics.item.powered.MatterConvergingCrossbowMode;
import com.fish_dan_.data_energistics.menu.powered.MatterConvergingCrossbowConfigMenu;

import appeng.client.gui.AEBaseScreen;
import appeng.client.gui.style.ScreenStyle;
import appeng.client.gui.widgets.UpgradesPanel;
import appeng.menu.SlotSemantics;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.Slot;

/** All item contents and the active mode come from AE slot synchronization and GuiSync. */
public final class MatterConvergingCrossbowConfigScreen extends AEBaseScreen<MatterConvergingCrossbowConfigMenu> {

    public MatterConvergingCrossbowConfigScreen(MatterConvergingCrossbowConfigMenu menu, Inventory inventory,
                                                Component title, ScreenStyle style) {
        super(menu, inventory, title, style);
        widgets.add("upgrades", new UpgradesPanel(menu.getSlots(SlotSemantics.UPGRADE), menu.getHost()));
    }

    @Override
    protected void updateBeforeRender() {
        super.updateBeforeRender();
        MatterConvergingCrossbowMode mode = MatterConvergingCrossbowMode.fromId(menu.activeMode);
        setTextContent("dialog_title", Component.translatable(
                "item.data_energistics.dark_string_data_settlement_tool.mode." + mode.nameKey()));
    }

    @Override
    public void renderSlot(GuiGraphics graphics, Slot slot) {
        super.renderSlot(graphics, slot);
        MatterConvergingCrossbowMode row = menu.rowOf(slot);
        if (row != null && row.id() != menu.activeMode) {
            graphics.pose().pushPose();
            graphics.pose().translate(0, 0, 200);
            graphics.fill(slot.x - 1, slot.y - 1, slot.x + 17, slot.y + 17, 0x90666666);
            graphics.pose().popPose();
        }
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 0 || button == 1) {
            double x = mouseX - leftPos;
            double y = mouseY - topPos;
            for (MatterConvergingCrossbowMode mode : MatterConvergingCrossbowMode.values()) {
                Slot cell = menu.cellSlot(mode);
                Slot ammo = menu.ammoSlot(mode);
                if (x >= cell.x + 18 && x < ammo.x - 1 && y >= cell.y - 2 && y < cell.y + 18) {
                    menu.sendSetMode(mode);
                    return true;
                }
                if (x >= ammo.x && x < ammo.x + 16 && y >= ammo.y && y < ammo.y + 16 && menu.getCarried().isEmpty()) {
                    if (mode.id() != menu.activeMode) menu.sendSetMode(mode);
                    else menu.sendCycleAmmo(mode, button == 1);
                    return true;
                }
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        MatterConvergingCrossbowMode requested = null;
        if (DEKeyMappings.TOGGLE_CROSSBOW_RAIL.matches(keyCode, scanCode)) requested = MatterConvergingCrossbowMode.RAIL;
        else if (DEKeyMappings.TOGGLE_CROSSBOW_ARMS.matches(keyCode, scanCode)) requested = MatterConvergingCrossbowMode.CROSSBOW;
        if (requested != null) {
            menu.sendSetMode(requested.id() == menu.activeMode ? MatterConvergingCrossbowMode.GRENADE : requested);
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }
}

package com.fish_dan_.data_energistics.client.screen.powered;

import com.fish_dan_.data_energistics.client.registry.DEKeyMappings;
import com.fish_dan_.data_energistics.item.powered.MatterConvergingCrossbowMode;
import com.fish_dan_.data_energistics.menu.powered.MatterConvergingCrossbowConfigMenu;

import appeng.client.gui.AEBaseScreen;
import appeng.client.gui.Icon;
import appeng.client.gui.style.PaletteColor;
import appeng.client.gui.style.ScreenStyle;
import appeng.client.gui.widgets.ToggleButton;
import appeng.client.gui.widgets.UpgradesPanel;
import appeng.menu.SlotSemantics;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/** All item contents and the active mode come from AE slot synchronization and GuiSync. */
public final class MatterConvergingCrossbowConfigScreen extends AEBaseScreen<MatterConvergingCrossbowConfigMenu> {

    private final Map<MatterConvergingCrossbowMode, ToggleButton> modeButtons = new EnumMap<>(MatterConvergingCrossbowMode.class);
    private final int selectionColor;

    public MatterConvergingCrossbowConfigScreen(MatterConvergingCrossbowConfigMenu menu, Inventory inventory,
                                                Component title, ScreenStyle style) {
        super(menu, inventory, title, style);
        selectionColor = style.getColor(PaletteColor.SELECTION_COLOR).toARGB();
        widgets.add("upgrades", new UpgradesPanel(menu.getSlots(SlotSemantics.UPGRADE), menu.getHost()));
        for (MatterConvergingCrossbowMode mode : MatterConvergingCrossbowMode.values()) {
            ToggleButton button = new ToggleButton(Icon.VALID, Icon.ARROW_RIGHT, selected -> menu.sendSetMode(mode));
            Component name = Component.translatable("item.data_energistics.star_shard.mode." + mode.nameKey());
            button.setTooltipOn(List.of(name, Component.translatable("screen.data_energistics.cannon.hint.selected")));
            button.setTooltipOff(List.of(name, Component.translatable("screen.data_energistics.cannon.hint.select_mode")));
            widgets.add("mode_" + mode.nameKey(), button);
            modeButtons.put(mode, button);
        }
    }

    @Override
    protected void updateBeforeRender() {
        super.updateBeforeRender();
        MatterConvergingCrossbowMode mode = MatterConvergingCrossbowMode.fromId(menu.activeMode);
        setTextContent("dialog_title", Component.translatable(
                "item.data_energistics.star_shard.mode." + mode.nameKey()));
        modeButtons.forEach((row, button) -> button.setState(row == mode));
    }

    @Override
    public void drawBG(GuiGraphics graphics, int offsetX, int offsetY, int mouseX, int mouseY, float partialTicks) {
        super.drawBG(graphics, offsetX, offsetY, mouseX, mouseY, partialTicks);
        // A dark title strip keeps the existing colored mode names readable on AE's light background.
        graphics.fill(offsetX + 8, offsetY + 6, offsetX + imageWidth - 8, offsetY + 23, 0xF0202730);
        for (MatterConvergingCrossbowMode mode : MatterConvergingCrossbowMode.values()) {
            Slot cell = menu.cellSlot(mode);
            int top = offsetY + cell.y - 6;
            boolean selected = mode.id() == menu.activeMode;
            int color = selected ? (selectionColor & 0xFFFFFF) | 0x30000000 : 0x126A707A;
            graphics.fill(offsetX + 8, top, offsetX + imageWidth - 8, top + 28, color);
            if (selected) graphics.fill(offsetX + 8, top, offsetX + 11, top + 28, selectionColor);
        }
        // The generated AE background has no baked slot outlines, so use AE's slot sprite for real inventory slots.
        for (Slot slot : menu.slots) {
            if (menu.getSlotSemantic(slot) != SlotSemantics.UPGRADE) {
                Icon.SLOT_BACKGROUND.getBlitter().dest(offsetX + slot.x - 1, offsetY + slot.y - 1).blit(graphics);
            }
        }
        int divider = offsetY + menu.getSlots(SlotSemantics.PLAYER_INVENTORY).getFirst().y - 19;
        graphics.fill(offsetX + 12, divider, offsetX + imageWidth - 12, divider + 1, 0xFF969BA4);
    }

    @Override
    public void renderSlot(GuiGraphics graphics, Slot slot) {
        super.renderSlot(graphics, slot);
        MatterConvergingCrossbowMode row = menu.rowOf(slot);
        if (row != null && row.id() != menu.activeMode) {
            graphics.pose().pushPose();
            graphics.pose().translate(0, 0, 200);
            graphics.fill(slot.x, slot.y, slot.x + 16, slot.y + 16, 0x486A707A);
            graphics.pose().popPose();
        }
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 0 || button == 1) {
            double x = mouseX - leftPos;
            double y = mouseY - topPos;
            for (MatterConvergingCrossbowMode mode : MatterConvergingCrossbowMode.values()) {
                Slot ammo = menu.ammoSlot(mode);
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
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontal, double vertical) {
        if (vertical != 0 && menu.getCarried().isEmpty()) {
            MatterConvergingCrossbowMode mode = MatterConvergingCrossbowMode.fromId(menu.activeMode);
            Slot ammo = menu.ammoSlot(mode);
            double x = mouseX - leftPos;
            double y = mouseY - topPos;
            if (x >= ammo.x && x < ammo.x + 16 && y >= ammo.y && y < ammo.y + 16) {
                menu.sendCycleAmmo(mode, vertical < 0);
                return true;
            }
        }
        return super.mouseScrolled(mouseX, mouseY, horizontal, vertical);
    }

    @Override
    protected List<Component> getTooltipFromContainerItem(ItemStack stack) {
        List<Component> lines = new ArrayList<>(super.getTooltipFromContainerItem(stack));
        if (hoveredSlot != null) {
            MatterConvergingCrossbowMode row = menu.rowOf(hoveredSlot);
            if (row != null) {
                String hint = hoveredSlot == menu.cellSlot(row) ? "cell" : row.id() == menu.activeMode ? "cycle_ammo" : "select_mode";
                lines.add(Component.translatable("screen.data_energistics.cannon.hint." + hint));
                if (row.id() != menu.activeMode) lines.add(Component.translatable("screen.data_energistics.cannon.hint.inactive"));
            }
        }
        return lines;
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

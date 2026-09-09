package com.fish_dan_.data_energistics.client.screen.powered;

import com.fish_dan_.data_energistics.item.powered.MatterConvergingCrossbowMode;
import com.fish_dan_.data_energistics.menu.powered.MatterConvergingCrossbowConfigMenu;
import com.fish_dan_.data_energistics.network.action.MatterConvergingCrossbowAmmoPayload;
import com.fish_dan_.data_energistics.network.action.MatterConvergingCrossbowModePayload;
import com.fish_dan_.data_energistics.registry.DEDataComponents;

import appeng.api.stacks.AEItemKey;
import appeng.api.storage.StorageCells;
import appeng.api.storage.cells.ISaveProvider;
import appeng.client.gui.AEBaseScreen;
import appeng.client.gui.style.ScreenStyle;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.ArrayList;
import java.util.List;

/** AE2 ScreenStyle based selector for the weapon mode and its disk ammunition. */
public final class MatterConvergingCrossbowConfigScreen extends AEBaseScreen<MatterConvergingCrossbowConfigMenu> {
    private final List<ItemStack> ammunition = new ArrayList<>();
    private MatterConvergingCrossbowMode selectedMode = MatterConvergingCrossbowMode.GRENADE;

    public MatterConvergingCrossbowConfigScreen(MatterConvergingCrossbowConfigMenu menu, Inventory inventory,
                                                Component title, ScreenStyle style) {
        super(menu, inventory, title, style);
        reloadAmmunition();
    }

    private void reloadAmmunition() {
        ItemStack weapon = this.menu.getPlayer().getItemInHand(this.menu.hand);
        this.selectedMode = MatterConvergingCrossbowMode.fromId(weapon.getOrDefault(DEDataComponents.MATTER_CONVERGING_CROSSBOW_MODE.get(), 0));
        this.ammunition.clear();
        var inventory = StorageCells.getCellInventory(weapon, (ISaveProvider) null);
        if (inventory != null) {
            for (var entry : inventory.getAvailableStacks()) {
                if (entry.getKey() instanceof AEItemKey key && entry.getLongValue() > 0) this.ammunition.add(key.toStack(1));
            }
        }
    }

    @Override
    public void drawFG(GuiGraphics graphics, int offsetX, int offsetY, int mouseX, int mouseY) {
        ItemStack weapon = this.menu.getPlayer().getItemInHand(this.menu.hand);
        ItemStack currentAmmo = this.ammunition.isEmpty() ? ItemStack.EMPTY : this.ammunition.getFirst();
        ResourceLocation selected = weapon.get(DEDataComponents.MATTER_CONVERGING_CROSSBOW_SELECTED_AMMO.get());
        if (selected != null) for (ItemStack candidate : this.ammunition) if (BuiltInRegistries.ITEM.getKey(candidate.getItem()).equals(selected)) currentAmmo = candidate;
        for (int i = 0; i < MatterConvergingCrossbowMode.values().length; i++) {
            int y = 20 + i * 27;
            boolean active = MatterConvergingCrossbowMode.values()[i] == this.selectedMode;
            graphics.fill(offsetX + 8, offsetY + y, offsetX + 30, offsetY + y + 22, active ? 0xFF6A7890 : 0xFF343944);
            graphics.renderItem(weapon, offsetX + 11, offsetY + y + 3);
            graphics.drawString(this.font, Component.translatable("item.data_energistics.dark_string_data_settlement_tool.mode." + MatterConvergingCrossbowMode.values()[i].nameKey()), offsetX + 36, offsetY + y + 7, active ? 0xFFFFFF : 0x777777, false);
            graphics.drawString(this.font, "→", offsetX + 145, offsetY + y + 6, 0xAAB7C8, false);
            if (!currentAmmo.isEmpty()) graphics.renderItem(currentAmmo, offsetX + 180, offsetY + y + 3);
            if (!active) graphics.fill(offsetX + 8, offsetY + y, offsetX + 212, offsetY + y + 22, 0xAA20242B);
        }
        for (int i = 0; i < Math.min(this.ammunition.size(), 9); i++) graphics.renderItem(this.ammunition.get(i), offsetX + 12 + i * 18, offsetY + 106);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button != 0) return super.mouseClicked(mouseX, mouseY, button);
        double x = mouseX - this.leftPos;
        double y = mouseY - this.topPos;
        for (int i = 0; i < MatterConvergingCrossbowMode.values().length; i++) {
            int row = 20 + i * 27;
            if (x >= 8 && x < 212 && y >= row && y < row + 22) {
                this.selectedMode = MatterConvergingCrossbowMode.values()[i];
                PacketDistributor.sendToServer(new MatterConvergingCrossbowModePayload(this.menu.hand == InteractionHand.OFF_HAND, this.selectedMode));
                return true;
            }
        }
        if (y >= 106 && y < 124) {
            int index = (int) ((x - 12) / 18);
            if (index >= 0 && index < this.ammunition.size()) {
                PacketDistributor.sendToServer(new MatterConvergingCrossbowAmmoPayload(this.menu.hand == InteractionHand.OFF_HAND, BuiltInRegistries.ITEM.getKey(this.ammunition.get(index).getItem())));
                return true;
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }
}

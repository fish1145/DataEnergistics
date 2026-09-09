package com.fish_dan_.data_energistics.client.screen.powered;

import com.fish_dan_.data_energistics.item.powered.MatterConvergingCrossbowMode;
import com.fish_dan_.data_energistics.network.action.MatterConvergingCrossbowAmmoPayload;
import com.fish_dan_.data_energistics.network.action.MatterConvergingCrossbowModePayload;
import com.fish_dan_.data_energistics.registry.DEDataComponents;

import appeng.api.stacks.AEItemKey;
import appeng.api.storage.StorageCells;
import appeng.api.storage.cells.ISaveProvider;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.ArrayList;
import java.util.List;

/** AE-style selector for the weapon mode and the ammunition selected in its storage disk. */
public final class MatterConvergingCrossbowConfigScreen extends Screen {
    private static final int PANEL_WIDTH = 220;
    private static final int PANEL_HEIGHT = 150;
    private final InteractionHand hand;
    private MatterConvergingCrossbowMode selectedMode;
    private final List<ItemStack> ammunition = new ArrayList<>();

    public MatterConvergingCrossbowConfigScreen(InteractionHand hand) {
        super(Component.translatable("screen.data_energistics.dark_string_data_settlement_tool"));
        this.hand = hand;
        this.selectedMode = MatterConvergingCrossbowMode.GRENADE;
    }

    @Override
    protected void init() {
        super.init();
        ItemStack weapon = Minecraft.getInstance().player.getItemInHand(this.hand);
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
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        this.renderBackground(graphics, mouseX, mouseY, partialTick);
        int left = (this.width - PANEL_WIDTH) / 2;
        int top = (this.height - PANEL_HEIGHT) / 2;
        graphics.fill(left, top, left + PANEL_WIDTH, top + PANEL_HEIGHT, 0xE6101420);
        graphics.drawString(this.font, this.title, left + 8, top + 7, 0xFFFFFF, false);
        ItemStack weapon = Minecraft.getInstance().player.getItemInHand(this.hand);
        ItemStack currentAmmo = this.ammunition.isEmpty() ? ItemStack.EMPTY : this.ammunition.getFirst();
        ResourceLocation selectedId = weapon.get(DEDataComponents.MATTER_CONVERGING_CROSSBOW_SELECTED_AMMO.get());
        if (selectedId != null) {
            for (ItemStack candidate : this.ammunition) {
                if (BuiltInRegistries.ITEM.getKey(candidate.getItem()).equals(selectedId)) {
                    currentAmmo = candidate;
                    break;
                }
            }
        }
        for (int i = 0; i < MatterConvergingCrossbowMode.values().length; i++) {
            int y = top + 25 + i * 27;
            MatterConvergingCrossbowMode mode = MatterConvergingCrossbowMode.values()[i];
            boolean active = mode == this.selectedMode;
            graphics.fill(left + 8, y, left + 30, y + 22, active ? 0xFF6A7890 : 0xFF343944);
            graphics.renderItem(weapon, left + 11, y + 3);
            graphics.drawString(this.font, Component.translatable("item.data_energistics.dark_string_data_settlement_tool.mode." + mode.nameKey()), left + 36, y + 7, active ? 0xFFFFFF : 0x777777, false);
            graphics.drawString(this.font, "→", left + 145, y + 6, 0xAAB7C8, false);
            if (!currentAmmo.isEmpty()) graphics.renderItem(currentAmmo, left + 180, y + 3);
            if (!active) graphics.fill(left + 8, y, left + 212, y + 22, 0xAA20242B);
        }
        int gridTop = top + 108;
        graphics.fill(left + 8, gridTop - 3, left + PANEL_WIDTH - 8, top + PANEL_HEIGHT - 8, 0xFF252A35);
        for (int i = 0; i < Math.min(this.ammunition.size(), 9); i++) {
            int x = left + 12 + (i % 9) * 22;
            graphics.fill(x - 1, gridTop + (i / 9) * 22 - 1, x + 20, gridTop + (i / 9) * 22 + 20, 0xFF8E98AA);
            graphics.renderItem(this.ammunition.get(i), x + 2, gridTop + (i / 9) * 22 + 2);
        }
        super.render(graphics, mouseX, mouseY, partialTick);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button != 0) return super.mouseClicked(mouseX, mouseY, button);
        int left = (this.width - PANEL_WIDTH) / 2;
        int top = (this.height - PANEL_HEIGHT) / 2;
        for (int i = 0; i < MatterConvergingCrossbowMode.values().length; i++) {
            int y = top + 25 + i * 27;
            if (mouseX >= left + 8 && mouseX < left + PANEL_WIDTH - 8 && mouseY >= y && mouseY < y + 22) {
                this.selectedMode = MatterConvergingCrossbowMode.values()[i];
                PacketDistributor.sendToServer(new MatterConvergingCrossbowModePayload(this.hand == InteractionHand.OFF_HAND, this.selectedMode));
                return true;
            }
        }
        int gridTop = top + 108;
        for (int i = 0; i < Math.min(this.ammunition.size(), 9); i++) {
            int x = left + 12 + (i % 9) * 22;
            if (mouseX >= x && mouseX < x + 22 && mouseY >= gridTop && mouseY < gridTop + 22) {
                ResourceLocation id = BuiltInRegistries.ITEM.getKey(this.ammunition.get(i).getItem());
                PacketDistributor.sendToServer(new MatterConvergingCrossbowAmmoPayload(this.hand == InteractionHand.OFF_HAND, id));
                return true;
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }
}

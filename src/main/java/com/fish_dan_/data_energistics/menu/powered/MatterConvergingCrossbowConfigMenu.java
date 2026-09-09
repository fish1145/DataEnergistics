package com.fish_dan_.data_energistics.menu.powered;

import com.fish_dan_.data_energistics.registry.DEMenus;

import appeng.menu.AEBaseMenu;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.MenuType;

public final class MatterConvergingCrossbowConfigMenu extends AEBaseMenu {
    public final InteractionHand hand;

    public MatterConvergingCrossbowConfigMenu(int id, Inventory inventory, InteractionHand hand) {
        this(DEMenus.MATTER_CONVERGING_CROSSBOW_CONFIG.get(), id, inventory, hand);
    }

    public MatterConvergingCrossbowConfigMenu(MenuType<?> type, int id, Inventory inventory, InteractionHand hand) {
        super(type, id, inventory, null);
        this.hand = hand;
        createPlayerInventorySlots(inventory);
    }

    public static MatterConvergingCrossbowConfigMenu fromNetwork(int id, Inventory inventory, RegistryFriendlyByteBuf data) {
        return new MatterConvergingCrossbowConfigMenu(id, inventory, data.readEnum(InteractionHand.class));
    }
}

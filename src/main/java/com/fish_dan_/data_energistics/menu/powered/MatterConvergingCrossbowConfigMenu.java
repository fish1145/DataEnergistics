package com.fish_dan_.data_energistics.menu.powered;

import com.fish_dan_.data_energistics.registry.DEMenus;
import com.fish_dan_.data_energistics.item.powered.MatterConvergingCrossbowItem;
import com.fish_dan_.data_energistics.item.powered.MatterConvergingCrossbowMode;
import com.fish_dan_.data_energistics.registry.DEDataComponents;

import appeng.menu.AEBaseMenu;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.resources.ResourceLocation;

public final class MatterConvergingCrossbowConfigMenu extends AEBaseMenu {
    private static final String ACTION_SET_MODE = "set_mode";
    private static final String ACTION_SET_AMMO = "set_ammo";
    public final InteractionHand hand;

    public MatterConvergingCrossbowConfigMenu(int id, Inventory inventory, InteractionHand hand) {
        this(DEMenus.MATTER_CONVERGING_CROSSBOW_CONFIG.get(), id, inventory, hand);
    }

    public MatterConvergingCrossbowConfigMenu(MenuType<?> type, int id, Inventory inventory, InteractionHand hand) {
        super(type, id, inventory, null);
        this.hand = hand;
        registerClientAction(ACTION_SET_MODE, Integer.class, this::setModeFromClient);
        registerClientAction(ACTION_SET_AMMO, ResourceLocation.class, this::setAmmoFromClient);
        createPlayerInventorySlots(inventory);
    }

    public void sendSetMode(int mode) {
        sendClientAction(ACTION_SET_MODE, mode);
    }

    public void sendSetAmmo(ResourceLocation itemId) {
        sendClientAction(ACTION_SET_AMMO, itemId);
    }

    private void setModeFromClient(Integer mode) {
        if (!(getPlayer().getItemInHand(this.hand).getItem() instanceof MatterConvergingCrossbowItem)) return;
        var updated = getPlayer().getItemInHand(this.hand).copy();
        updated.set(DEDataComponents.MATTER_CONVERGING_CROSSBOW_MODE.get(), MatterConvergingCrossbowMode.fromId(mode).id());
        getPlayer().setItemInHand(this.hand, updated);
        broadcastChanges();
    }

    private void setAmmoFromClient(ResourceLocation itemId) {
        if (getPlayer().getItemInHand(this.hand).getItem() instanceof MatterConvergingCrossbowItem item) {
            item.selectCannonAmmo(getPlayer().getItemInHand(this.hand), itemId);
        }
        broadcastChanges();
    }

    public static MatterConvergingCrossbowConfigMenu fromNetwork(int id, Inventory inventory, RegistryFriendlyByteBuf data) {
        InteractionHand hand = InteractionHand.MAIN_HAND;
        if (data != null) {
            hand = data.readEnum(InteractionHand.class);
        } else if (!(inventory.player.getMainHandItem().getItem() instanceof MatterConvergingCrossbowItem)
                && inventory.player.getOffhandItem().getItem() instanceof MatterConvergingCrossbowItem) {
            hand = InteractionHand.OFF_HAND;
        }
        return new MatterConvergingCrossbowConfigMenu(id, inventory, hand);
    }
}

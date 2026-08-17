package com.fish_dan_.data_energistics.item.orbital;

import com.fish_dan_.data_energistics.orbital.control.OrbitalControlTerminalSnapshot;
import com.fish_dan_.data_energistics.orbital.control.OrbitalControlUiFactory;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

import com.lowdragmc.lowdraglib2.gui.factory.HeldItemUIMenuType;
import com.lowdragmc.lowdraglib2.gui.ui.ModularUI;

/**
 * UUID-routed handheld entry point for the orbital weapon fire-control UI.
 *
 * <p>
 * The item has no owner or weapon components. The server opens an LDLib2 held-item menu only after looking up the
 * current player's accessible weapon index, and the menu rechecks that index while it remains open.
 * </p>
 */
public final class OrbitalControlTerminalItem extends Item implements HeldItemUIMenuType.HeldItemUI {

    public OrbitalControlTerminalItem(Properties properties) {
        super(properties.stacksTo(1));
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand usedHand) {
        ItemStack stack = player.getItemInHand(usedHand);
        if (level.isClientSide) {
            return InteractionResultHolder.sidedSuccess(stack, true);
        }
        if (!(player instanceof ServerPlayer serverPlayer)) {
            return InteractionResultHolder.fail(stack);
        }

        MinecraftServer server = serverPlayer.getServer();
        if (server == null || OrbitalControlTerminalSnapshot.capture(server, serverPlayer.getUUID()).weapons().isEmpty()) {
            return InteractionResultHolder.fail(stack);
        }
        boolean opened = HeldItemUIMenuType.openUI(serverPlayer, usedHand);
        return opened ? InteractionResultHolder.sidedSuccess(stack, false) : InteractionResultHolder.fail(stack);
    }

    @Override
    public ModularUI createUI(HeldItemUIMenuType.HeldItemUIHolder holder) {
        return OrbitalControlUiFactory.create(
                holder.player,
                () -> snapshot(holder).toComponent(),
                () -> stillValid(holder));
    }

    @Override
    public boolean stillValid(HeldItemUIMenuType.HeldItemUIHolder holder) {
        if (!HeldItemUIMenuType.HeldItemUI.super.stillValid(holder)) {
            return false;
        }
        if (!(holder.player instanceof ServerPlayer serverPlayer)) {
            return true;
        }
        MinecraftServer server = serverPlayer.getServer();
        return server != null && !snapshot(serverPlayer).weapons().isEmpty();
    }

    private static OrbitalControlTerminalSnapshot snapshot(HeldItemUIMenuType.HeldItemUIHolder holder) {
        ServerPlayer player = (ServerPlayer) holder.player;
        return snapshot(player);
    }

    private static OrbitalControlTerminalSnapshot snapshot(ServerPlayer player) {
        MinecraftServer server = player.getServer();
        if (server == null) {
            throw new IllegalStateException("An orbital control terminal requires an attached server player");
        }
        return OrbitalControlTerminalSnapshot.capture(server, player.getUUID());
    }
}

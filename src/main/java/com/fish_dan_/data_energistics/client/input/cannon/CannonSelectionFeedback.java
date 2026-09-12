package com.fish_dan_.data_energistics.client.input.cannon;

import com.fish_dan_.data_energistics.item.powered.MatterConvergingCrossbowItem;
import com.fish_dan_.data_energistics.item.powered.MatterConvergingCrossbowMode;
import com.fish_dan_.data_energistics.item.powered.cannon.storage.MountedAmmoCells;

import appeng.api.stacks.AEKey;

import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

import org.jspecify.annotations.Nullable;

/** Uses the same action-bar feedback as AE's color applicator, after inventory state has reached the client. */
public final class CannonSelectionFeedback {

    private static @Nullable Selection previous;
    private static boolean pending;

    private CannonSelectionFeedback() {}

    public static void tick(Minecraft minecraft) {
        var player = minecraft.player;
        if (player == null || minecraft.level == null) {
            previous = null;
            pending = false;
            return;
        }
        InteractionHand hand;
        if (player.getMainHandItem().getItem() instanceof MatterConvergingCrossbowItem) hand = InteractionHand.MAIN_HAND;
        else if (player.getOffhandItem().getItem() instanceof MatterConvergingCrossbowItem) hand = InteractionHand.OFF_HAND;
        else {
            previous = null;
            pending = false;
            return;
        }
        ItemStack weapon = player.getItemInHand(hand);
        MatterConvergingCrossbowMode mode = MatterConvergingCrossbowItem.mode(weapon);
        ItemStack ammo = MountedAmmoCells.peek(weapon, mode);
        Selection current = new Selection(minecraft.level.dimension(), hand,
                hand == InteractionHand.MAIN_HAND ? player.getInventory().selected : Inventory.SLOT_OFFHAND,
                mode, MountedAmmoCells.selectedKey(weapon, mode));
        if (!current.equals(previous)) {
            previous = current;
            pending = true;
        }
        // Menu changes remain pending until the player closes the menu and can see the hotbar again.
        if (pending && minecraft.screen == null && !minecraft.options.hideGui) {
            AEKey key = current.ammunition;
            Component ammunition = key == null ? Component.translatable("item.data_energistics.star_shard.projectile.none") : key.getDisplayName();
            Component message = Component.empty().append(weapon.getItem().getName(weapon))
                    .append(Component.literal("  |  ").withStyle(ChatFormatting.GRAY))
                    .append(ammunition.copy().withStyle(ChatFormatting.WHITE));
            player.displayClientMessage(message, true);
            pending = false;
        }
    }

    private record Selection(ResourceKey<Level> dimension, InteractionHand hand, int slot,
                             MatterConvergingCrossbowMode mode, @Nullable AEKey ammunition) {}
}

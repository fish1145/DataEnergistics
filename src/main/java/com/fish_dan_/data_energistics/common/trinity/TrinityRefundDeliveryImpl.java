package com.fish_dan_.data_energistics.common.trinity;

import com.fish_dan_.data_energistics.Data_Energistics;

import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;

import appeng.api.config.Actionable;
import appeng.api.networking.security.IActionSource;
import appeng.api.stacks.AEItemKey;
import appeng.api.storage.MEStorage;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/** Default AE-network, player-inventory, then world-drop implementation of {@link TrinityRefundDelivery}. */
public final class TrinityRefundDeliveryImpl implements TrinityRefundDelivery {

    private final Player player;
    @Nullable
    private final MEStorage networkStorage;
    @Nullable
    private final IActionSource actionSource;
    private List<TrinityItemAmount> preparedItems = List.of();
    private boolean prepared;
    private boolean delivered;

    /**
     * Creates one delivery bound to the player who requested the refund and an optional currently usable AE network.
     *
     * @param player         requesting player and final world-drop location
     * @param networkStorage lease-grid storage, or {@code null} when no AE network is available
     * @param actionSource   lease-grid permission source, or {@code null} with no AE network
     */
    public TrinityRefundDeliveryImpl(Player player,
                                     @Nullable MEStorage networkStorage,
                                     @Nullable IActionSource actionSource) {
        this.player = player;
        this.networkStorage = networkStorage;
        this.actionSource = actionSource;
    }

    @Override
    public boolean prepare(List<TrinityItemAmount> items) {
        if (this.prepared || this.player.level().isClientSide() || items.isEmpty()) {
            return false;
        }
        this.preparedItems = List.copyOf(items);
        this.prepared = true;
        return true;
    }

    @Override
    public void deliver(List<TrinityItemAmount> items) {
        if (!this.prepared || this.delivered || !this.preparedItems.equals(items)) {
            throw new IllegalStateException("Trinity refund delivery was not prepared for this aggregate");
        }
        this.delivered = true;
        for (TrinityItemAmount item : items) {
            deliverItem(item);
        }
    }

    private void deliverItem(TrinityItemAmount item) {
        long remaining = insertIntoNetwork(item);
        if (remaining == 0L) {
            return;
        }
        long worldRemainder = insertIntoPlayerInventory(item.key(), remaining);
        if (worldRemainder > 0L) {
            dropRemainder(item.key(), worldRemainder);
        }
    }

    private long insertIntoNetwork(TrinityItemAmount item) {
        if (this.networkStorage == null || this.actionSource == null) {
            return item.amount();
        }
        long offered = item.amount();
        long inserted;
        try {
            inserted = this.networkStorage.insert(
                    item.key(),
                    offered,
                    Actionable.MODULATE,
                    this.actionSource);
        } catch (RuntimeException exception) {
            Data_Energistics.LOGGER.error(
                    "Failed to insert Trinity refund item {} into the selected AE network; trying player inventory",
                    item,
                    exception);
            return item.amount();
        }
        if (inserted < 0L || inserted > offered) {
            throw new IllegalStateException("AE storage accepted invalid Trinity refund amount " + inserted +
                    " for offer " + offered);
        }
        return offered - inserted;
    }

    private long insertIntoPlayerInventory(AEItemKey key, long amount) {
        if (amount <= 0L) {
            return 0L;
        }
        int maximumStackSize = key.toStack(1).getMaxStackSize();
        long remaining = amount;
        while (remaining > 0L) {
            int offered = (int) Math.min(remaining, maximumStackSize);
            ItemStack stack = key.toStack(offered);
            try {
                this.player.getInventory().add(stack);
            } catch (RuntimeException exception) {
                Data_Energistics.LOGGER.error(
                        "Failed to insert Trinity refund stack {} into player {} inventory; dropping its remainder",
                        stack,
                        this.player.getGameProfile().getName(),
                        exception);
                return remaining;
            }
            long inserted = offered - stack.getCount();
            remaining -= inserted;
            if (!stack.isEmpty()) {
                return remaining;
            }
        }
        return 0L;
    }

    private void dropRemainder(AEItemKey key, long amount) {
        long remaining = amount;
        while (remaining > 0L) {
            int count = (int) Math.min(remaining, Integer.MAX_VALUE);
            dropStack(key.toStack(count));
            remaining -= count;
        }
    }

    private void dropStack(ItemStack stack) {
        try {
            if (this.player.drop(stack.copy(), false) != null) {
                return;
            }
        } catch (RuntimeException exception) {
            Data_Energistics.LOGGER.error(
                    "Failed to drop Trinity refund stack {} for player {}; trying block drop fallback",
                    stack,
                    this.player.getGameProfile().getName(),
                    exception);
        }
        try {
            Block.popResource(this.player.level(), this.player.blockPosition(), stack.copy());
        } catch (RuntimeException exception) {
            Data_Energistics.LOGGER.error(
                    "Failed to place final Trinity refund stack {} into the world for player {}",
                    stack,
                    this.player.getGameProfile().getName(),
                    exception);
            throw new IllegalStateException("Unable to deliver final Trinity refund stack " + stack, exception);
        }
    }
}

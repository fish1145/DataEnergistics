package com.fish_dan_.data_energistics.common.trinity.pattern;

import com.fish_dan_.data_energistics.Data_Energistics;

import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

import appeng.api.config.Actionable;
import appeng.api.networking.security.IActionSource;
import appeng.api.stacks.AEItemKey;
import appeng.api.storage.MEStorage;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/** Default AE-network, player-inventory, then checked world-drop implementation for installed-pattern refunds. */
public final class PlayerPatternRefundDelivery implements TrinityPatternRefundDelivery {

    private final Player player;
    @Nullable
    private final MEStorage networkStorage;
    @Nullable
    private final IActionSource actionSource;
    private List<ItemStack> preparedPatterns = List.of();
    private boolean prepared;
    private boolean delivered;

    /**
     * Creates a delivery bound to the requesting player and an optional currently usable AE network.
     *
     * @param player         requesting player and final world-drop location
     * @param networkStorage lease-grid storage, or {@code null} when no AE network is available
     * @param actionSource   lease-grid permission source, or {@code null} with no AE network
     */
    public PlayerPatternRefundDelivery(Player player,
                                       @Nullable MEStorage networkStorage,
                                       @Nullable IActionSource actionSource) {
        this.player = player;
        this.networkStorage = networkStorage;
        this.actionSource = actionSource;
    }

    @Override
    public boolean prepare(List<ItemStack> patterns) {
        if (this.prepared || this.player.level().isClientSide() || patterns.isEmpty()) {
            return false;
        }
        this.preparedPatterns = copyPatterns(patterns);
        this.prepared = true;
        return true;
    }

    @Override
    public List<ItemStack> deliver(List<ItemStack> patterns) {
        if (!this.prepared || this.delivered || !matchesPreparedPatterns(patterns)) {
            throw new IllegalStateException("Trinity pattern refund delivery was not prepared for this aggregate");
        }
        this.delivered = true;
        for (int index = 0; index < patterns.size(); index++) {
            ItemStack pattern = patterns.get(index);
            ItemStack remainder = pattern.copy();
            insertIntoNetwork(remainder);
            try {
                if (!remainder.isEmpty()) {
                    this.player.getInventory().add(remainder);
                }
            } catch (RuntimeException exception) {
                Data_Energistics.LOGGER.error(
                        "Failed to insert Trinity installed-pattern refund {} into player {} inventory",
                        remainder,
                        this.player.getGameProfile().getName(),
                        exception);
            }
            if (!remainder.isEmpty() && !dropRemainder(remainder)) {
                return undeliveredPatterns(patterns, index, remainder);
            }
        }
        return List.of();
    }

    private void insertIntoNetwork(ItemStack remainder) {
        if (remainder.isEmpty() || this.networkStorage == null || this.actionSource == null) {
            return;
        }
        AEItemKey key = AEItemKey.of(remainder);
        if (key == null) {
            throw new IllegalArgumentException("Installed Trinity pattern refund requires a non-empty item stack");
        }
        long offered = remainder.getCount();
        long inserted;
        try {
            inserted = this.networkStorage.insert(key, offered, Actionable.MODULATE, this.actionSource);
        } catch (RuntimeException exception) {
            Data_Energistics.LOGGER.error(
                    "Failed to insert Trinity installed-pattern refund {} into the selected AE network; trying player inventory",
                    remainder,
                    exception);
            return;
        }
        if (inserted < 0L || inserted > offered) {
            throw new IllegalStateException("AE storage accepted invalid Trinity installed-pattern refund amount " +
                    inserted + " for offer " + offered);
        }
        remainder.shrink((int) inserted);
    }

    private boolean matchesPreparedPatterns(List<ItemStack> patterns) {
        if (this.preparedPatterns.size() != patterns.size()) {
            return false;
        }
        for (int index = 0; index < patterns.size(); index++) {
            if (!ItemStack.matches(this.preparedPatterns.get(index), patterns.get(index))) {
                return false;
            }
        }
        return true;
    }

    private static List<ItemStack> copyPatterns(List<ItemStack> patterns) {
        ArrayList<ItemStack> copies = new ArrayList<>(patterns.size());
        for (ItemStack pattern : patterns) {
            copies.add(pattern.copy());
        }
        return List.copyOf(copies);
    }

    private static List<ItemStack> undeliveredPatterns(List<ItemStack> patterns, int index, ItemStack remainder) {
        ArrayList<ItemStack> undelivered = new ArrayList<>(patterns.size() - index);
        undelivered.add(remainder.copy());
        for (int remainingIndex = index + 1; remainingIndex < patterns.size(); remainingIndex++) {
            undelivered.add(patterns.get(remainingIndex).copy());
        }
        return List.copyOf(undelivered);
    }

    private boolean dropRemainder(ItemStack remainder) {
        try {
            ItemEntity entity = new ItemEntity(
                    this.player.level(),
                    this.player.getX(),
                    this.player.getY(),
                    this.player.getZ(),
                    remainder.copy());
            if (this.player.level().addFreshEntity(entity)) {
                return true;
            }
            Data_Energistics.LOGGER.error(
                    "World rejected Trinity installed-pattern refund {} for player {}",
                    remainder,
                    this.player.getGameProfile().getName());
        } catch (RuntimeException exception) {
            Data_Energistics.LOGGER.error(
                    "Failed to create Trinity installed-pattern refund world drop {} for player {}",
                    remainder,
                    this.player.getGameProfile().getName(),
                    exception);
        }
        return false;
    }
}

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
    private List<ItemStack> preparedStacks = List.of();
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
    public boolean prepare(List<ItemStack> stacks) {
        if (this.prepared || this.player.level().isClientSide() || stacks.isEmpty()) {
            return false;
        }
        for (ItemStack stack : stacks) {
            if (stack.isEmpty()) {
                return false;
            }
        }
        this.preparedStacks = stacks.stream().map(ItemStack::copy).toList();
        this.prepared = true;
        return true;
    }

    @Override
    public void deliver(List<ItemStack> stacks) {
        if (!this.prepared || this.delivered || !stacksMatch(this.preparedStacks, stacks)) {
            throw new IllegalStateException("Trinity refund delivery was not prepared for this aggregate");
        }
        this.delivered = true;
        for (ItemStack stack : stacks) {
            deliverStack(stack.copy());
        }
    }

    private void deliverStack(ItemStack remaining) {
        insertIntoNetwork(remaining);
        insertIntoPlayerInventory(remaining);
        if (!remaining.isEmpty()) {
            dropRemainder(remaining);
        }
    }

    private void insertIntoNetwork(ItemStack remaining) {
        if (this.networkStorage == null || this.actionSource == null || remaining.isEmpty()) {
            return;
        }
        try {
            long offered = remaining.getCount();
            long inserted = this.networkStorage.insert(
                    AEItemKey.of(remaining),
                    offered,
                    Actionable.MODULATE,
                    this.actionSource);
            if (inserted < 0L || inserted > offered) {
                throw new IllegalStateException("AE storage accepted invalid Trinity refund amount " + inserted +
                        " for offer " + offered);
            }
            remaining.shrink(Math.toIntExact(inserted));
        } catch (RuntimeException exception) {
            Data_Energistics.LOGGER.error(
                    "Failed to insert Trinity refund stack {} into the selected AE network; trying player inventory",
                    remaining,
                    exception);
        }
    }

    private void insertIntoPlayerInventory(ItemStack remaining) {
        if (remaining.isEmpty()) {
            return;
        }
        try {
            this.player.getInventory().add(remaining);
        } catch (RuntimeException exception) {
            Data_Energistics.LOGGER.error(
                    "Failed to insert Trinity refund stack {} into player {} inventory; dropping it instead",
                    remaining,
                    this.player.getGameProfile().getName(),
                    exception);
        }
    }

    private void dropRemainder(ItemStack remaining) {
        ItemStack dropped = remaining.copy();
        try {
            if (this.player.drop(dropped, false) != null) {
                return;
            }
        } catch (RuntimeException exception) {
            Data_Energistics.LOGGER.error(
                    "Failed to drop Trinity refund stack {} for player {}; trying block drop fallback",
                    remaining,
                    this.player.getGameProfile().getName(),
                    exception);
        }
        try {
            Block.popResource(this.player.level(), this.player.blockPosition(), remaining.copy());
        } catch (RuntimeException exception) {
            Data_Energistics.LOGGER.error(
                    "Failed to place final Trinity refund stack {} into the world for player {}",
                    remaining,
                    this.player.getGameProfile().getName(),
                    exception);
        }
    }

    private static boolean stacksMatch(List<ItemStack> expected, List<ItemStack> actual) {
        if (expected.size() != actual.size()) {
            return false;
        }
        for (int index = 0; index < expected.size(); index++) {
            ItemStack expectedStack = expected.get(index);
            ItemStack actualStack = actual.get(index);
            if (expectedStack.getCount() != actualStack.getCount() ||
                    !ItemStack.isSameItemSameComponents(expectedStack, actualStack)) {
                return false;
            }
        }
        return true;
    }
}

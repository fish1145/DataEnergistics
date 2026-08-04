package com.fish_dan_.data_energistics.world;

import com.fish_dan_.data_energistics.network.DataMeteoriteCompassResponsePayload;
import com.fish_dan_.data_energistics.registry.ModItems;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ChunkPos;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.level.BlockEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;

import appeng.core.definitions.AEBlocks;

import java.util.ArrayDeque;
import java.util.Optional;
import java.util.Queue;

/**
 * Removes broken data meteorite centers and pushes a fresh compass target after the block is gone.
 */
public final class DataMeteoriteCompassTargetInvalidation {

    private static final int MAX_RETRIES = 3;
    private final Queue<PendingRefresh> pendingRefreshes = new ArrayDeque<>();

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public void onBlockBreak(BlockEvent.BreakEvent event) {
        if (event.isCanceled() || !(event.getLevel() instanceof ServerLevel level) || !event.getState().is(AEBlocks.MYSTERIOUS_CUBE.block())) {
            return;
        }

        BlockPos pos = event.getPos().immutable();
        if (DataMeteoriteSavedData.get(level).remove(pos)) {
            this.pendingRefreshes.add(new PendingRefresh(level, pos, MAX_RETRIES));
        }
    }

    @SubscribeEvent
    public void onServerTick(ServerTickEvent.Post event) {
        int pendingCount = this.pendingRefreshes.size();
        for (int i = 0; i < pendingCount; i++) {
            PendingRefresh pending = this.pendingRefreshes.remove();
            if (pending.level().getBlockState(pending.pos()).is(AEBlocks.MYSTERIOUS_CUBE.block())) {
                if (pending.retriesRemaining() > 0) {
                    this.pendingRefreshes.add(pending.retry());
                }
                continue;
            }

            this.refreshPlayers(pending.level());
        }
    }

    private void refreshPlayers(ServerLevel level) {
        for (ServerPlayer player : level.players()) {
            if (!hasCompass(player)) {
                continue;
            }

            ChunkPos requestedPos = new ChunkPos(player.blockPosition());
            Optional<BlockPos> closest = DataMeteoriteLocator.findOrDiscoverClosest(
                    level,
                    requestedPos,
                    player.blockPosition().getY());
            PacketDistributor.sendToPlayer(player, new DataMeteoriteCompassResponsePayload(requestedPos, closest));
        }
    }

    private static boolean hasCompass(ServerPlayer player) {
        if (isCompass(player.getMainHandItem()) || isCompass(player.getOffhandItem())) {
            return true;
        }

        for (ItemStack stack : player.getInventory().items) {
            if (isCompass(stack)) {
                return true;
            }
        }
        return false;
    }

    private static boolean isCompass(ItemStack stack) {
        return stack.is(ModItems.DATA_METEORITE_COMPASS.get());
    }

    private record PendingRefresh(ServerLevel level, BlockPos pos, int retriesRemaining) {

        private PendingRefresh retry() {
            return new PendingRefresh(this.level, this.pos, this.retriesRemaining - 1);
        }
    }
}

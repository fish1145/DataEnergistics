package com.fish_dan_.data_energistics.world.meteorite;

import com.fish_dan_.data_energistics.network.meteorite.DataMeteoriteCompassResponsePayload;
import com.fish_dan_.data_energistics.registry.DEBlocks;
import com.fish_dan_.data_energistics.registry.DEItems;

import appeng.core.definitions.AEBlocks;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.level.BlockEvent;
import net.neoforged.neoforge.event.level.ChunkEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.ArrayDeque;
import java.util.List;
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
        if (event.isCanceled() || !(event.getLevel() instanceof ServerLevel level) || !isDataMeteoriteCenter(event.getState())) {
            return;
        }

        BlockPos pos = event.getPos().immutable();
        if (DataMeteoriteSavedData.get(level).remove(pos)) {
            this.pendingRefreshes.add(new PendingRefresh(level, pos, MAX_RETRIES));
        }
    }

    @SubscribeEvent
    public void onChunkLoad(ChunkEvent.Load event) {
        if (event.isNewChunk() || !(event.getLevel() instanceof ServerLevel level)) {
            return;
        }

        List<BlockPos> legacyCenters = event.getChunk().getBlockEntitiesPos().stream()
                .filter(pos -> event.getChunk().getBlockState(pos).is(AEBlocks.MYSTERIOUS_CUBE.block()))
                .map(BlockPos::immutable)
                .toList();
        if (!legacyCenters.isEmpty()) {
            level.getServer().execute(() -> migrateLegacyCenters(level, legacyCenters));
        }
    }

    @SubscribeEvent
    public void onServerTick(ServerTickEvent.Post event) {
        int pendingCount = this.pendingRefreshes.size();
        for (int i = 0; i < pendingCount; i++) {
            PendingRefresh pending = this.pendingRefreshes.remove();
            if (isDataMeteoriteCenter(pending.level().getBlockState(pending.pos()))) {
                if (pending.retriesRemaining() > 0) {
                    this.pendingRefreshes.add(pending.retry());
                }
                continue;
            }

            this.refreshPlayers(pending.level());
        }
    }

    private static void migrateLegacyCenters(ServerLevel level, List<BlockPos> candidates) {
        DataMeteoriteSavedData savedData = DataMeteoriteSavedData.get(level);
        for (BlockPos pos : candidates) {
            if (level.isLoaded(pos) && savedData.contains(pos) && level.getBlockState(pos).is(AEBlocks.MYSTERIOUS_CUBE.block())) {
                level.setBlock(pos, DEBlocks.DATA_MYSTERIOUS_CUBE.get().defaultBlockState(), Block.UPDATE_ALL);
            }
        }
    }

    private static boolean isDataMeteoriteCenter(BlockState state) {
        return state.is(DEBlocks.DATA_MYSTERIOUS_CUBE.get()) || state.is(AEBlocks.MYSTERIOUS_CUBE.block());
    }

    private void refreshPlayers(ServerLevel level) {
        for (ServerPlayer player : level.players()) {
            if (!hasCompass(player)) {
                continue;
            }

            ChunkPos requestedPos = new ChunkPos(player.blockPosition());
            Optional<BlockPos> closest = Optional.ofNullable(
                    DataMeteoriteSavedData.get(level).findClosest(requestedPos));
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
        return stack.is(DEItems.DATA_METEORITE_COMPASS.get());
    }

    private record PendingRefresh(ServerLevel level, BlockPos pos, int retriesRemaining) {

        private PendingRefresh retry() {
            return new PendingRefresh(this.level, this.pos, this.retriesRemaining - 1);
        }
    }
}

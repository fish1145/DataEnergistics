package com.fish_dan_.data_energistics.world;

import com.fish_dan_.data_energistics.network.DataMeteoriteCompassResponsePayload;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.ArrayDeque;
import java.util.Optional;
import java.util.Queue;
import java.util.UUID;

public class DataMeteoritePreloader {

    private static final int INITIAL_DELAY_TICKS = 40;
    private static final int MIN_INTERVAL_TICKS = 100;
    private final Queue<Request> requests = new ArrayDeque<>();
    private int nextAllowedTick;

    @SubscribeEvent
    public void onPlayerLoggedIn(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer player && player.level() instanceof ServerLevel level) {
            this.queue(player, level);
        }
    }

    @SubscribeEvent
    public void onServerTick(ServerTickEvent.Post event) {
        if (this.requests.isEmpty()) {
            return;
        }

        if (this.nextAllowedTick > 0) {
            this.nextAllowedTick--;
            return;
        }

        Request request = this.requests.peek();
        if (request == null) {
            return;
        }

        if (request.delayTicks() > 0) {
            this.requests.poll();
            this.requests.add(request.withDelay(request.delayTicks() - 1));
            return;
        }

        this.requests.poll();
        ServerLevel level = request.server().getLevel(request.dimension());
        if (level != null) {
            Optional<BlockPos> closest = DataMeteoriteLocator.findOrDiscoverClosest(level, request.chunkPos(), request.y());
            ServerPlayer player = request.server().getPlayerList().getPlayer(request.playerId());
            if (player != null && player.level().dimension().equals(request.dimension())) {
                PacketDistributor.sendToPlayer(player, new DataMeteoriteCompassResponsePayload(request.chunkPos(), closest));
            }
        }
        this.nextAllowedTick = MIN_INTERVAL_TICKS;
    }

    private void queue(ServerPlayer player, ServerLevel level) {
        this.requests.add(new Request(
                level.getServer(),
                level.dimension(),
                player.getUUID(),
                new ChunkPos(player.blockPosition()),
                player.blockPosition().getY(),
                INITIAL_DELAY_TICKS));
    }

    private record Request(MinecraftServer server, ResourceKey<Level> dimension, UUID playerId, ChunkPos chunkPos,
                           int y, int delayTicks) {

        private Request withDelay(int delayTicks) {
            return new Request(this.server, this.dimension, this.playerId, this.chunkPos, this.y, delayTicks);
        }
    }
}

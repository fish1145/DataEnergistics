package com.fish_dan_.data_energistics.orbital.control;

import com.fish_dan_.data_energistics.network.orbital.control.OrbitalControlHudSnapshotPayload;
import com.fish_dan_.data_energistics.orbital.control.ui.OrbitalControlPresentation;

import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;

import it.unimi.dsi.fastutil.objects.Object2ObjectMap;
import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap;
import org.jspecify.annotations.Nullable;

import java.util.UUID;

/** Publishes bounded orbital status changes to players actively holding the control terminal. */
public final class OrbitalControlHudTicker {

    private static final long PUBLISH_INTERVAL = 5L;

    private final Object2ObjectMap<UUID, PublishedState> publishedStates = new Object2ObjectOpenHashMap<>();
    private @Nullable MinecraftServer trackedServer;

    public OrbitalControlHudTicker() {}

    @SubscribeEvent
    public void onServerTickPost(ServerTickEvent.Post event) {
        MinecraftServer server = event.getServer();
        if (this.trackedServer != server) {
            this.trackedServer = server;
            this.publishedStates.clear();
        }
        long gameTime = server.overworld().getGameTime();
        if (gameTime % PUBLISH_INTERVAL != 0L) {
            return;
        }

        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            PublishedState state = stateFor(server, player);
            PublishedState previous = this.publishedStates.put(player.getUUID(), state);
            if (!state.equals(previous)) {
                PacketDistributor.sendToPlayer(
                        player,
                        new OrbitalControlHudSnapshotPayload(
                                gameTime,
                                state.visible(),
                                state.status()));
            }
        }
        this.publishedStates.keySet().removeIf(id -> server.getPlayerList().getPlayer(id) == null);
    }

    private static PublishedState stateFor(MinecraftServer server, ServerPlayer player) {
        if (!holdsTerminal(player)) {
            return PublishedState.HIDDEN;
        }
        OrbitalControlTerminalSnapshot snapshot = OrbitalControlTerminalSnapshot.capture(server, player.getUUID());
        if (snapshot.selectedWeaponId() == null) {
            return PublishedState.HIDDEN;
        }
        return new PublishedState(true, OrbitalControlPresentation.hud(snapshot));
    }

    private static boolean holdsTerminal(ServerPlayer player) {
        return OrbitalControlTerminalAccess.hasTerminal(player);
    }

    private record PublishedState(boolean visible, Component status) {

        private static final PublishedState HIDDEN = new PublishedState(false, Component.empty());
    }
}

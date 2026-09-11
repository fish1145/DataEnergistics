package com.fish_dan_.data_energistics.client.render.orbital;

import com.fish_dan_.data_energistics.Data_Energistics;
import com.fish_dan_.data_energistics.network.orbital.projection.OrbitalProjectionVisualsPayload;
import com.fish_dan_.data_energistics.network.orbital.visual.OrbitalAttackVisualsPayload;
import com.fish_dan_.data_energistics.orbital.attack.OrbitalAttackMode;
import com.fish_dan_.data_energistics.orbital.attack.OrbitalAttackPhase;
import com.fish_dan_.data_energistics.orbital.attack.OrbitalAttackVisualSnapshot;
import com.fish_dan_.data_energistics.orbital.model.StellarErasureDeviceLifecycleState;
import com.fish_dan_.data_energistics.orbital.projection.OrbitalProjectionVisualSnapshot;

import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.storage.LevelResource;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;

import it.unimi.dsi.fastutil.objects.ObjectArrayList;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

/** Opt-in smoke fixture for an isolated copied GameTest world; never creates actual weapons or attacks. */
@EventBusSubscriber(modid = Data_Energistics.MODID, value = Dist.CLIENT)
public final class OrbitalWorldRenderPreview {

    private static final String[] VIEWS = { "deployed", "redeploying", "distant", "attack-echoes" };
    private static final AtomicInteger SAVED = new AtomicInteger();
    private static boolean initialized;
    private static boolean oldPauseOnLostFocus;
    private static boolean oldHideGui;
    private static boolean captured;
    private static int view;
    private static int ticks;
    private static long visualRevision;

    private OrbitalWorldRenderPreview() {}

    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        if (!"true".equals(System.getenv("DE_ORBITAL_WORLD_PREVIEW"))) {
            return;
        }
        Minecraft client = Minecraft.getInstance();
        if (client.level == null || client.player == null) {
            return;
        }
        if (!initialized) {
            MinecraftServer server = client.getSingleplayerServer();
            if (server == null || !server.getWorldPath(LevelResource.ROOT).normalize().getFileName().toString().equals("OrbitalRenderVerification")) {
                throw new IllegalStateException("Orbital world preview only accepts the isolated OrbitalRenderVerification save");
            }
            initialized = true;
            oldPauseOnLostFocus = client.options.pauseOnLostFocus;
            oldHideGui = client.options.hideGui;
            client.options.pauseOnLostFocus = false;
            client.options.hideGui = true;
            UUID playerId = client.player.getUUID();
            server.execute(() -> {
                ServerPlayer player = server.getPlayerList().getPlayer(playerId);
                if (player != null) {
                    player.setGameMode(GameType.SPECTATOR);
                    player.serverLevel().setDayTime(6000);
                }
            });
        }
        if (++ticks > 60 && view < VIEWS.length - 1 && captured) {
            view++;
            ticks = 0;
            captured = false;
        }
        int distance = view == 2 ? 6000 : 600;
        int projectionY = client.level.getMaxBuildHeight() + OrbitalProjectionVisualSnapshot.ALTITUDE_ABOVE_BUILD_LIMIT;
        if (view == 3) {
            distance = 450;
            projectionY = client.level.getMaxBuildHeight() + 96;
        }
        float pitch = (float) -Math.toDegrees(Math.atan2(projectionY - client.player.getEyeY(), distance));
        client.player.setYRot(0);
        client.player.yRotO = 0;
        client.player.setXRot(pitch);
        client.player.xRotO = pitch;
        BlockPos anchor = client.player.blockPosition().offset(0, 0, distance);
        // Scenario switches must reach the cache even while server world time stalls during loading.
        long revision = Math.max(visualRevision + 1, client.level.getGameTime() + 1_000_000);
        visualRevision = revision;
        var dimension = client.level.dimension().location();
        List<OrbitalProjectionVisualSnapshot> projections = view == 3 ? List.of() : List.of(
                new OrbitalProjectionVisualSnapshot(new UUID(0, 1), dimension, anchor, projectionY,
                        view == 1 ? StellarErasureDeviceLifecycleState.REDEPLOYING : StellarErasureDeviceLifecycleState.DEPLOYED,
                        view == 1 ? 1000 : 0, revision, 42));
        OrbitalProjectionVisualsPayload.batches(revision, dimension, projections).forEach(OrbitalProjectionVisualClientState::receive);
        List<OrbitalAttackVisualSnapshot> attacks = new ObjectArrayList<>();
        if (view == 3) {
            for (OrbitalAttackMode mode : OrbitalAttackMode.values()) {
                BlockPos target = anchor.offset((mode.ordinal() - 1) * 160, 0, 0);
                attacks.add(new OrbitalAttackVisualSnapshot(new UUID(0, mode.ordinal() + 2), mode, dimension,
                        target, target, 24, OrbitalAttackPhase.DELIVERY, ticks, 42, 0, 1));
            }
        }
        OrbitalAttackVisualsPayload.batches(revision, dimension, attacks).forEach(OrbitalAttackVisualClientState::receive);
        if (SAVED.get() == VIEWS.length) {
            client.options.pauseOnLostFocus = oldPauseOnLostFocus;
            client.options.hideGui = oldHideGui;
            client.stop();
        }
    }

    @SubscribeEvent
    public static void onRenderedWorld(RenderLevelStageEvent event) {
        if (!initialized || captured || ticks < (view == 0 ? 100 : 40) || event.getStage() != RenderLevelStageEvent.Stage.AFTER_LEVEL) {
            return;
        }
        Minecraft client = Minecraft.getInstance();
        captured = true;
        Screenshot.grab(client.gameDirectory, "orbital-world-" + VIEWS[view] + ".png", client.getMainRenderTarget(), message -> {
            Data_Energistics.LOGGER.info("Orbital world snapshot: {}", message.getString());
            SAVED.incrementAndGet();
        });
    }
}

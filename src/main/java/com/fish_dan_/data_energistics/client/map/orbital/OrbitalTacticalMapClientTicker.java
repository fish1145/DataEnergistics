package com.fish_dan_.data_energistics.client.map.orbital;

import com.fish_dan_.data_energistics.client.hud.orbital.OrbitalControlHudClientState;
import com.fish_dan_.data_energistics.network.orbital.map.OrbitalTacticalMapRequestPayload;

import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.ChunkPos;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;

import org.jspecify.annotations.Nullable;

import java.util.UUID;

/**
 * Drives the built-in tactical-map request loop while the server has exposed a selected orbital weapon in the HUD.
 * Requests are viewport intents only; the server still resolves the session token, endpoint and generated-chunk policy.
 */
@OnlyIn(Dist.CLIENT)
public final class OrbitalTacticalMapClientTicker {

    private static final int VIEWPORT_RADIUS = 3;
    private static final long MIN_REQUEST_INTERVAL = 20L;
    private static final long REFRESH_INTERVAL = 100L;
    private static final long SESSION_REFRESH_INTERVAL = 900L;

    private static long nonce;
    private static long lastRequestAt = Long.MIN_VALUE;
    private static long contextStartedAt = Long.MIN_VALUE;
    private static int lastRequestedChunkX;
    private static int lastRequestedChunkZ;
    private static @Nullable UUID contextWeaponId;
    private static @Nullable ResourceLocation contextDimension;

    private OrbitalTacticalMapClientTicker() {}

    /** Requests the current player viewport after movement, weapon changes or a bounded refresh interval. */
    public static void onClientTickPost(ClientTickEvent.Post event) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.isPaused() || minecraft.level == null || minecraft.player == null) {
            return;
        }

        UUID weaponId = OrbitalControlHudClientState.selectedWeaponId();
        if (!OrbitalControlHudClientState.visible() || weaponId == null) {
            if (contextWeaponId != null || OrbitalTacticalMapClientState.revision() >= 0L) {
                contextWeaponId = null;
                contextDimension = null;
                lastRequestAt = Long.MIN_VALUE;
                contextStartedAt = Long.MIN_VALUE;
                OrbitalTacticalMapClientState.clear();
            }
            return;
        }

        ResourceLocation dimensionId = minecraft.level.dimension().location();
        if (!weaponId.equals(contextWeaponId) || !dimensionId.equals(contextDimension)) {
            contextWeaponId = weaponId;
            contextDimension = dimensionId;
            lastRequestAt = Long.MIN_VALUE;
            contextStartedAt = minecraft.level.getGameTime();
            OrbitalTacticalMapClientState.clear();
        }

        ChunkPos center = minecraft.player.chunkPosition();
        long gameTime = minecraft.level.getGameTime();
        if (contextStartedAt != Long.MIN_VALUE
                && gameTime - contextStartedAt >= SESSION_REFRESH_INTERVAL) {
            contextStartedAt = gameTime;
            lastRequestAt = Long.MIN_VALUE;
            OrbitalTacticalMapClientState.clear();
        }
        boolean moved = center.x != lastRequestedChunkX || center.z != lastRequestedChunkZ;
        boolean refreshDue = lastRequestAt == Long.MIN_VALUE || gameTime - lastRequestAt >= REFRESH_INTERVAL;
        if (!moved && !refreshDue) {
            return;
        }
        if (lastRequestAt != Long.MIN_VALUE && gameTime - lastRequestAt < MIN_REQUEST_INTERVAL) {
            return;
        }

        UUID sessionToken = OrbitalTacticalMapClientState.sessionToken();
        PacketDistributor.sendToServer(new OrbitalTacticalMapRequestPayload(
                weaponId,
                sessionToken,
                dimensionId,
                center.x,
                center.z,
                VIEWPORT_RADIUS,
                nextNonce()));
        lastRequestedChunkX = center.x;
        lastRequestedChunkZ = center.z;
        lastRequestAt = gameTime;
    }

    private static long nextNonce() {
        nonce++;
        if (nonce <= 0L) {
            nonce = 1L;
        }
        return nonce;
    }
}

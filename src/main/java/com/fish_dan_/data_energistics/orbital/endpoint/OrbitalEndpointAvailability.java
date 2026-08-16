package com.fish_dan_.data_energistics.orbital.endpoint;

import com.fish_dan_.data_energistics.blockentity.orbital.OrbitalEndpointBlockEntity;

import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;

import java.util.UUID;

/**
 * Resolves whether a persisted endpoint currently provides an online uplink for its dimension.
 */
public final class OrbitalEndpointAvailability {

    private OrbitalEndpointAvailability() {}

    /**
     * Returns whether the endpoint's chunk is loaded, its physical binding still matches, and its AE grid is powered.
     *
     * <p>
     * Online status intentionally does not wait for pathing to finish or require an allocated channel. It unlocks a
     * dimension for preview and confirmation; callers that perform AE network I/O must additionally require the node
     * to be active. This check never loads a missing chunk because endpoint chunk tickets keep bound endpoints present.
     * </p>
     */
    public static boolean isOnline(
                                   MinecraftServer server,
                                   UUID weaponId,
                                   OrbitalEndpointRecord endpoint) {
        OrbitalEndpointLocation location = endpoint.location();
        ResourceKey<Level> dimension = ResourceKey.create(Registries.DIMENSION, location.dimensionId());
        ServerLevel level = server.getLevel(dimension);
        if (level == null || !level.hasChunkAt(location.pos())) {
            return false;
        }
        if (!(level.getBlockEntity(location.pos()) instanceof OrbitalEndpointBlockEntity blockEntity)) {
            return false;
        }
        return blockEntity.endpointKind() == endpoint.kind() && blockEntity.getWeaponId().filter(weaponId::equals).isPresent() && blockEntity.getMainNode().isPowered();
    }
}

package com.fish_dan_.data_energistics.orbital.endpoint;

import com.fish_dan_.data_energistics.Data_Energistics;
import com.fish_dan_.data_energistics.blockentity.orbital.OrbitalEndpointBlockEntity;
import com.fish_dan_.data_energistics.configuration.schema.DataEnergisticsConfiguration;
import com.fish_dan_.data_energistics.orbital.model.OrbitalWeaponRecord;
import com.fish_dan_.data_energistics.orbital.storage.OrbitalWeaponSavedData;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.common.world.chunk.RegisterTicketControllersEvent;
import net.neoforged.neoforge.common.world.chunk.TicketController;
import net.neoforged.neoforge.common.world.chunk.TicketHelper;
import net.neoforged.neoforge.common.world.chunk.TicketSet;
import net.neoforged.neoforge.event.server.ServerStartingEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Owns the persistent, ticking chunk ticket associated with each bound orbital endpoint.
 *
 * <p>
 * Tickets follow persistent placement state rather than instantaneous AE power. Startup reconciliation treats
 * {@link OrbitalWeaponSavedData} as authoritative, validates the physical block and weapon identity, restores missing
 * tickets, and removes stale endpoint records.
 * </p>
 */
public final class OrbitalEndpointChunkTickets {

    private static final TicketController CONTROLLER = new TicketController(
            ResourceLocation.fromNamespaceAndPath(Data_Energistics.MODID, "orbital_endpoints"),
            OrbitalEndpointChunkTickets::validatePersistedTickets);

    private boolean serverInitialized;
    private boolean appliedChunkLoadingEnabled;

    public OrbitalEndpointChunkTickets() {}

    /**
     * Registers the stable ticket-controller ID during the mod-bus registration phase.
     */
    public static void registerController(RegisterTicketControllersEvent event) {
        event.register(CONTROLLER);
    }

    /**
     * Ensures a bound endpoint owns a ticking ticket for exactly its own chunk when self-loading is enabled.
     */
    public static void retain(ServerLevel level, OrbitalEndpointLocation location) {
        requireMatchingDimension(level, location);
        ChunkPos chunk = new ChunkPos(location.pos());
        if (!DataEnergisticsConfiguration.INSTANCE.orbitalWeapon().endpointChunkLoadingEnabled()) {
            release(level, location);
            return;
        }
        CONTROLLER.forceChunk(level, location.pos(), chunk.x, chunk.z, true, true);
    }

    /**
     * Releases both current ticking and legacy non-ticking tickets owned by an endpoint at its own chunk.
     */
    public static void release(ServerLevel level, OrbitalEndpointLocation location) {
        requireMatchingDimension(level, location);
        ChunkPos chunk = new ChunkPos(location.pos());
        CONTROLLER.forceChunk(level, location.pos(), chunk.x, chunk.z, false, true);
        CONTROLLER.forceChunk(level, location.pos(), chunk.x, chunk.z, false, false);
    }

    @SubscribeEvent
    public void onServerStarting(ServerStartingEvent event) {
        reconcile(event.getServer());
        this.appliedChunkLoadingEnabled = chunkLoadingEnabled();
        this.serverInitialized = true;
    }

    @SubscribeEvent
    public void onServerTickPre(ServerTickEvent.Pre event) {
        boolean enabled = chunkLoadingEnabled();
        if (this.serverInitialized && enabled == this.appliedChunkLoadingEnabled) {
            return;
        }
        reconcile(event.getServer());
        this.appliedChunkLoadingEnabled = enabled;
        this.serverInitialized = true;
    }

    @SubscribeEvent
    public void onServerStopped(ServerStoppedEvent event) {
        this.serverInitialized = false;
    }

    private static void validatePersistedTickets(ServerLevel level, TicketHelper ticketHelper) {
        OrbitalWeaponSavedData data = OrbitalWeaponSavedData.get(level.getServer());
        Map<OrbitalEndpointLocation, UUID> bindings = data.endpointBindings();
        boolean enabled = chunkLoadingEnabled();
        for (Map.Entry<BlockPos, TicketSet> entry : ticketHelper.getBlockTickets().entrySet()) {
            BlockPos owner = entry.getKey();
            if (!enabled) {
                ticketHelper.removeAllTickets(owner);
                continue;
            }

            OrbitalEndpointLocation location = new OrbitalEndpointLocation(level.dimension().location(), owner);
            UUID weaponId = bindings.get(location);
            if (weaponId == null || !matchesWorldBinding(level, data, location, weaponId)) {
                ticketHelper.removeAllTickets(owner);
                if (weaponId != null) {
                    removeStaleBinding(level.getServer(), data, location, weaponId);
                }
                continue;
            }
            normalizeTicketSet(ticketHelper, owner, entry.getValue());
        }
    }

    private static void reconcile(MinecraftServer server) {
        OrbitalWeaponSavedData data = OrbitalWeaponSavedData.get(server);
        for (Map.Entry<OrbitalEndpointLocation, UUID> entry : data.endpointBindings().entrySet()) {
            OrbitalEndpointLocation location = entry.getKey();
            UUID weaponId = entry.getValue();
            ResourceKey<Level> dimension = ResourceKey.create(Registries.DIMENSION, location.dimensionId());
            ServerLevel level = server.getLevel(dimension);
            if (level == null) {
                removeStaleBinding(server, data, location, weaponId);
                continue;
            }

            try {
                if (!matchesWorldBinding(level, data, location, weaponId)) {
                    release(level, location);
                    removeStaleBinding(server, data, location, weaponId);
                } else if (chunkLoadingEnabled()) {
                    retain(level, location);
                } else {
                    release(level, location);
                }
            } catch (RuntimeException exception) {
                Data_Energistics.LOGGER.error(
                        "Failed to reconcile orbital endpoint chunk ticket {} for weapon {}",
                        location,
                        weaponId,
                        exception);
            }
        }
    }

    private static boolean matchesWorldBinding(
                                               ServerLevel level,
                                               OrbitalWeaponSavedData data,
                                               OrbitalEndpointLocation location,
                                               UUID weaponId) {
        Optional<OrbitalWeaponRecord> weapon = data.weaponAt(location);
        if (weapon.isEmpty()) {
            return false;
        }
        OrbitalWeaponRecord boundWeapon = weapon.orElseThrow();
        if (!boundWeapon.weaponId().equals(weaponId)) {
            return false;
        }
        OrbitalEndpointRecord record = boundWeapon.endpoints().get(location);
        if (record == null) {
            return false;
        }
        if (!(level.getBlockEntity(location.pos()) instanceof OrbitalEndpointBlockEntity endpoint)) {
            return false;
        }
        return endpoint.endpointKind() == record.kind() && endpoint.getWeaponId().filter(weaponId::equals).isPresent();
    }

    private static void normalizeTicketSet(TicketHelper ticketHelper, BlockPos owner, TicketSet tickets) {
        long expectedChunk = new ChunkPos(owner).toLong();
        for (long chunk : tickets.nonTicking()) {
            ticketHelper.removeTicket(owner, chunk, false);
        }
        for (long chunk : tickets.ticking()) {
            if (chunk != expectedChunk) {
                ticketHelper.removeTicket(owner, chunk, true);
            }
        }
    }

    private static void removeStaleBinding(
                                           MinecraftServer server,
                                           OrbitalWeaponSavedData data,
                                           OrbitalEndpointLocation location,
                                           UUID weaponId) {
        try {
            if (data.removeEndpoint(server, weaponId, location)) {
                Data_Energistics.LOGGER.warn(
                        "Removed stale orbital endpoint {} from weapon {} during chunk-ticket reconciliation",
                        location,
                        weaponId);
            }
        } catch (RuntimeException exception) {
            Data_Energistics.LOGGER.error(
                    "Failed to remove stale orbital endpoint {} from weapon {}",
                    location,
                    weaponId,
                    exception);
        }
    }

    private static boolean chunkLoadingEnabled() {
        return DataEnergisticsConfiguration.INSTANCE.orbitalWeapon().endpointChunkLoadingEnabled();
    }

    private static void requireMatchingDimension(ServerLevel level, OrbitalEndpointLocation location) {
        if (!level.dimension().location().equals(location.dimensionId())) {
            throw new IllegalArgumentException("Endpoint location does not belong to the supplied server level");
        }
    }
}

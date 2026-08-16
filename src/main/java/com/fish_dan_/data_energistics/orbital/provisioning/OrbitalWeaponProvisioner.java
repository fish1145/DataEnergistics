package com.fish_dan_.data_energistics.orbital.provisioning;

import com.fish_dan_.data_energistics.orbital.endpoint.OrbitalEndpointLocation;
import com.fish_dan_.data_energistics.orbital.model.OrbitalWeaponRecord;

import net.minecraft.server.MinecraftServer;

import java.util.UUID;

/**
 * Creates or reuses a player's orbital weapon while atomically attaching its first physical endpoint.
 *
 * <p>
 * This abstraction prevents control consoles and future launcher structures from independently constructing partial
 * weapon records. Calls are server-authoritative, must run on the Minecraft server thread, and are valid for the
 * lifetime of the supplied server. Inputs and the returned record are never {@code null}.
 * </p>
 */
public interface OrbitalWeaponProvisioner {

    /**
     * Provisions the weapon owned by {@code ownerId} at {@code sourceLocation}.
     *
     * <p>
     * If the player already owns a weapon, the endpoint is attached to that stable weapon identity. Delegated access
     * to another player's weapon is deliberately ignored when resolving ownership. The operation persists SavedData and
     * may throw {@link IllegalStateException} when called off-thread, when the location belongs to another weapon, or
     * when existing indexes are inconsistent.
     * </p>
     *
     * @param server         authoritative server whose overworld owns the SavedData
     * @param ownerId        UUID of the player placing the provisioning structure
     * @param sourceLocation dimension-qualified location of that structure
     * @return the immutable weapon snapshot after endpoint registration
     */
    OrbitalWeaponRecord provision(
                                  MinecraftServer server,
                                  UUID ownerId,
                                  OrbitalEndpointLocation sourceLocation);
}

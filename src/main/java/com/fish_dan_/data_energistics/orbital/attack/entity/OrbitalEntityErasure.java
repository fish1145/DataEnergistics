package com.fish_dan_.data_energistics.orbital.attack.entity;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Entity.RemovalReason;
import net.minecraft.world.entity.player.Player;
import net.neoforged.neoforge.entity.PartEntity;

import java.util.Set;
import java.util.UUID;

/** Server-thread entity erasure shared by orbital impacts, beams and payloads after their hit-volume checks. */
public final class OrbitalEntityErasure {

    private OrbitalEntityErasure() {}

    /**
     * Erases a hit entity without entering the damage pipeline. Multipart hits resolve to their owning entity;
     * repeated hits on an already erased owner do nothing. Frozen authorization exemptions and privileged players
     * remain protected. Players use the normal death notification and respawn lifecycle instead of entity removal.
     * Both arguments must be non-null, and the caller must run on the hit entity's server thread.
     */
    public static void eraseHit(Entity hit, Set<UUID> exemptions) {
        Entity target = hit;
        while (target instanceof PartEntity<?> part) {
            target = part.getParent();
        }
        if (target.isRemoved() || !target.isAlive() || exemptions.contains(target.getUUID())) {
            return;
        }
        if (target instanceof Player player && (player.isCreative() || player.isSpectator())) {
            return;
        }
        if (target instanceof ServerPlayer player) {
            if (player.hasPermissions(2)) {
                return;
            }
            DamageSource cause = player.damageSources().genericKill();
            player.getCombatTracker().recordDamage(cause, player.getHealth());
            player.setAbsorptionAmount(0);
            player.setHealth(0);
            player.die(cause);
        } else {
            // The final removal entry point also handles targets whose hurt/kill/remove overrides reject damage.
            target.setRemoved(RemovalReason.DISCARDED);
        }
    }
}

package com.fish_dan_.data_energistics.orbital.attack.entity;

import com.fish_dan_.data_energistics.Data_Energistics;
import com.fish_dan_.data_energistics.integration.draconic.orbital.DraconicGuardianErasureAdapter;
import com.fish_dan_.data_energistics.orbital.attack.entity.player.PlayerErasureExecutor;
import com.fish_dan_.data_energistics.orbital.attack.entity.strike.OrbitalErasureOutcome;
import com.fish_dan_.data_energistics.orbital.attack.entity.strike.OrbitalErasureStrike;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Entity.RemovalReason;
import net.minecraft.world.entity.player.Player;
import net.neoforged.neoforge.entity.PartEntity;

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
    public static OrbitalErasureOutcome eraseHit(Entity hit, OrbitalErasureStrike strike) {
        if (!(hit.level() instanceof ServerLevel level) || !level.getServer().isSameThread()) {
            throw new IllegalStateException("Orbital erasure must execute on the target's server thread");
        }
        Entity target = hit;
        while (target instanceof PartEntity<?> part) {
            target = part.getParent();
        }
        if (strike.exempts(target.getUUID())) {
            return OrbitalErasureOutcome.EXEMPT;
        }
        if (target instanceof Player player && (player.isCreative() || player.isSpectator())) {
            return OrbitalErasureOutcome.EXEMPT;
        }
        if (target instanceof ServerPlayer player && player.hasPermissions(2)) {
            return OrbitalErasureOutcome.EXEMPT;
        }
        if (target.isRemoved() || !target.isAlive()) {
            return OrbitalErasureOutcome.INACTIVE;
        }
        UUID execution = strike.begin(target.getUUID());
        if (execution == null) {
            return OrbitalErasureOutcome.ALREADY_HANDLED;
        }
        OrbitalErasureOutcome outcome;
        try {
            if (target instanceof ServerPlayer player) {
                outcome = PlayerErasureExecutor.execute(player, strike, execution);
            } else if (Data_Energistics.isModLoaded("draconicevolution") && DraconicGuardianErasureAdapter.supports(target)) {
                outcome = DraconicGuardianErasureAdapter.execute(target, strike, execution);
            } else {
                target.setRemoved(RemovalReason.DISCARDED);
                outcome = target.isRemoved() ? OrbitalErasureOutcome.ENTITY_REMOVED : OrbitalErasureOutcome.PARTIAL_FAILURE;
            }
        } catch (RuntimeException | LinkageError failure) {
            Data_Energistics.LOGGER.error("Orbital erasure {} for strike {} and subject {} at {} failed; the attempt will not replay",
                    execution, strike.strikeId(), target.getUUID(), target.blockPosition(), failure);
            outcome = OrbitalErasureOutcome.PARTIAL_FAILURE;
        }
        strike.complete(target.getUUID(), execution, outcome);
        return outcome;
    }
}

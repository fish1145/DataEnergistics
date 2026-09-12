package com.fish_dan_.data_energistics.orbital.attack.entity.player;

import com.fish_dan_.data_energistics.Data_Energistics;
import com.fish_dan_.data_energistics.orbital.attack.entity.lifecycle.OrbitalErasureAttachments;
import com.fish_dan_.data_energistics.orbital.attack.entity.lifecycle.OrbitalErasureContext;
import com.fish_dan_.data_energistics.orbital.attack.entity.lifecycle.OrbitalErasureState;
import com.fish_dan_.data_energistics.orbital.attack.entity.lifecycle.OrbitalErasureState.Phase;
import com.fish_dan_.data_energistics.orbital.attack.entity.strike.OrbitalErasureOutcome;
import com.fish_dan_.data_energistics.orbital.attack.entity.strike.OrbitalErasureStrike;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;

import org.jspecify.annotations.Nullable;

import java.util.UUID;

/** Terminates a current player life without damage, while preserving the actual death and respawn settlement. */
public final class PlayerErasureExecutor {

    private PlayerErasureExecutor() {}

    public static OrbitalErasureOutcome execute(ServerPlayer player, OrbitalErasureStrike strike, UUID execution) {
        if (OrbitalErasureAttachments.blocksRecovery(player)) {
            return OrbitalErasureOutcome.ALREADY_HANDLED;
        }
        float previousHealth = player.getHealth();
        float previousAbsorption = player.getAbsorptionAmount();
        boolean absorptionCleared = false;
        boolean healthCleared = false;
        boolean completed = false;
        OrbitalErasureState state = null;
        try (OrbitalErasureContext context = new OrbitalErasureContext(player, strike.strikeId(), execution, strike.initiatorId())) {
            state = context.state();
            DamageSource cause = player.damageSources().genericKill();
            player.setAbsorptionAmount(0);
            absorptionCleared = true;
            player.setHealth(0);
            healthCleared = player.getHealth() == 0;
            if (!healthCleared) {
                throw new IllegalStateException("The terminal health write did not commit");
            }
            player.die(cause);
            if (state.phase() != Phase.DEATH_COMPLETED || player.getHealth() != 0) {
                throw new IllegalStateException("The player death settlement did not complete");
            }
            completed = true;
            return OrbitalErasureOutcome.PLAYER_DEATH_COMPLETED;
        } catch (RuntimeException | LinkageError failure) {
            boolean committed = state != null && (state.phase() == Phase.DEATH_COMMITTED || state.phase() == Phase.DEATH_COMPLETED);
            releaseFailedLife(player, state);
            if (!committed) {
                // Restore only our confirmed writes, and only before any death side effect began.
                if (healthCleared && player.getHealth() == 0) {
                    player.setHealth(previousHealth);
                }
                if (absorptionCleared && player.getAbsorptionAmount() == 0) {
                    player.setAbsorptionAmount(previousAbsorption);
                }
            }
            Data_Energistics.LOGGER.error("Orbital player erasure {} for strike {} and player {} at {} ended {}",
                    execution, strike.strikeId(), player.getUUID(), player.blockPosition(),
                    committed ? "after death side effects began; no replay or inventory rollback was attempted" : "before death commitment; the life lock was released",
                    failure);
            return committed ? OrbitalErasureOutcome.PARTIAL_FAILURE : OrbitalErasureOutcome.FAILED_BEFORE_COMMIT;
        } finally {
            // Even an error propagated beyond this boundary must not leave equipment recovery indefinitely vetoed.
            if (!completed) {
                releaseFailedLife(player, state);
            }
        }
    }

    private static void releaseFailedLife(ServerPlayer player, @Nullable OrbitalErasureState state) {
        if (state != null) {
            state.fail();
            if (OrbitalErasureAttachments.find(player) == state) {
                OrbitalErasureAttachments.clear(player);
            }
        }
    }
}

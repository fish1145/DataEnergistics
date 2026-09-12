package com.fish_dan_.data_energistics.mixin.core.entity;

import com.fish_dan_.data_energistics.orbital.attack.entity.lifecycle.OrbitalErasureAttachments;
import com.fish_dan_.data_energistics.orbital.attack.entity.lifecycle.OrbitalErasureContext;
import com.fish_dan_.data_energistics.orbital.attack.entity.lifecycle.OrbitalErasureState;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.network.PacketSendListener;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.protocol.game.ClientboundPlayerCombatKillPacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.stats.Stats;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.scores.ScoreAccess;
import net.minecraft.world.scores.Team;
import net.minecraft.world.scores.criteria.ObjectiveCriteria;
import net.neoforged.neoforge.common.CommonHooks;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.mojang.authlib.GameProfile;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.gen.Invoker;

import java.util.Optional;

/** Routes only the claimed life through the verified 1.21.1 death settlement; ordinary deaths retain their chain. */
@Mixin(ServerPlayer.class)
public abstract class OrbitalPlayerDeathMixin extends Player {

    protected OrbitalPlayerDeathMixin(Level level, BlockPos position, float rotation, GameProfile profile) {
        super(level, position, rotation, profile);
    }

    @Invoker("tellNeutralMobsThatIDied")
    protected abstract void dataEnergistics$tellNeutralMobsThatIDied();

    @WrapMethod(method = "die")
    private void dataEnergistics$routeDeath(DamageSource cause, Operation<Void> original) {
        ServerPlayer player = (ServerPlayer) (Object) this;
        OrbitalErasureState state = OrbitalErasureAttachments.find(player);
        if (state == null || !state.blocksRecovery()) {
            original.call(cause);
            return;
        }
        OrbitalErasureContext context = OrbitalErasureContext.current(player, cause);
        if (context == null || !state.enterDeathCall()) {
            return;
        }
        try {
            dataEnergistics$settleErasureDeath(player, cause, context);
        } finally {
            state.exitDeathCall();
        }
    }

    /**
     * Keeps vanilla's notification, inventory/drop hooks, score, statistics and final death location. Calling the
     * wrapped original here would re-enter third-party cancellable HEAD injections before this body could run.
     */
    @Unique
    private void dataEnergistics$settleErasureDeath(ServerPlayer player, DamageSource cause, OrbitalErasureContext context) {
        OrbitalErasureState state = context.state();
        state.beginDeathSideEffects();
        this.getCombatTracker().recordDamage(cause, context.initialHealth());
        // Use the original Level event endpoint directly, rather than an equipment-controlled Entity shortcut.
        this.level().gameEvent(player, GameEvent.ENTITY_DIE, this.position());
        // Dispatch once for other death rules; equipment cancellation does not veto this owned termination.
        CommonHooks.onLivingDeath(player, cause);
        state.deathHookReturned();
        state.enterDeathBody();
        if (this.level().getGameRules().getBoolean(GameRules.RULE_SHOWDEATHMESSAGES)) {
            Component message = this.getCombatTracker().getDeathMessage();
            player.connection.send(new ClientboundPlayerCombatKillPacket(this.getId(), message),
                    PacketSendListener.exceptionallySend(() -> {
                        Component shortened = Component.translatable("death.attack.message_too_long",
                                Component.literal(message.getString(256)).withStyle(ChatFormatting.YELLOW));
                        Component fallback = Component.translatable("death.attack.even_more_magic", this.getDisplayName())
                                .withStyle(style -> style.withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, shortened)));
                        return new ClientboundPlayerCombatKillPacket(this.getId(), fallback);
                    }));
            Team team = this.getTeam();
            if (team == null || team.getDeathMessageVisibility() == Team.Visibility.ALWAYS) {
                player.getServer().getPlayerList().broadcastSystemMessage(message, false);
            } else if (team.getDeathMessageVisibility() == Team.Visibility.HIDE_FOR_OTHER_TEAMS) {
                player.getServer().getPlayerList().broadcastSystemToTeam(this, message);
            } else if (team.getDeathMessageVisibility() == Team.Visibility.HIDE_FOR_OWN_TEAM) {
                player.getServer().getPlayerList().broadcastSystemToAllExceptTeam(this, message);
            }
        } else {
            player.connection.send(new ClientboundPlayerCombatKillPacket(this.getId(), CommonComponents.EMPTY));
        }
        this.removeEntitiesOnShoulder();
        if (this.level().getGameRules().getBoolean(GameRules.RULE_FORGIVE_DEAD_PLAYERS)) {
            dataEnergistics$tellNeutralMobsThatIDied();
        }
        if (!this.isSpectator()) {
            this.dropAllDeathLoot(player.serverLevel(), cause);
        }
        this.getScoreboard().forAllObjectives(ObjectiveCriteria.DEATH_COUNT, this, ScoreAccess::increment);
        LivingEntity killer = context.initiatorId() == null ? null : player.getServer().getPlayerList().getPlayer(context.initiatorId());
        if (killer != null) {
            this.awardStat(Stats.ENTITY_KILLED_BY.get(killer.getType()));
            killer.awardKillScore(this, this.deathScore, cause);
            this.createWitherRose(killer);
        }
        this.level().broadcastEntityEvent(this, (byte) 3);
        this.awardStat(Stats.DEATHS);
        this.resetStat(Stats.CUSTOM.get(Stats.TIME_SINCE_DEATH));
        this.resetStat(Stats.CUSTOM.get(Stats.TIME_SINCE_REST));
        this.clearFire();
        this.setTicksFrozen(0);
        this.setSharedFlagOnFire(false);
        this.getCombatTracker().recheckStatus();
        this.setLastDeathLocation(Optional.of(GlobalPos.of(this.level().dimension(), this.blockPosition())));
        state.completeDeathBody();
    }

    @WrapMethod(method = "restoreFrom")
    private void dataEnergistics$releaseNewLife(ServerPlayer previous, boolean keepEverything, Operation<Void> original) {
        original.call(previous, keepEverything);
        // Do not clear the old object's lock: delayed callbacks must not resurrect that discarded instance.
        OrbitalErasureAttachments.clear(this);
    }
}

package com.fish_dan_.data_energistics.effect;

import com.fish_dan_.data_energistics.network.action.ChromaticGlowPayload;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.network.PacketDistributor;

/** Temporary outline tint; scoreboard membership and other glow durations remain untouched. */
public final class ChromaticGlow {

    private static final String COLOR = "DataEnergisticsGlowColor";
    private static final String UNTIL = "DataEnergisticsGlowUntil";

    public static void apply(LivingEntity target, int color) {
        long until = target.level().getGameTime() + 100;
        accept(target, color, until);
        target.addEffect(new MobEffectInstance(MobEffects.GLOWING, 100, 0));
        PacketDistributor.sendToPlayersTrackingEntityAndSelf(target, new ChromaticGlowPayload(target.getId(), color, until));
    }

    public static void accept(Entity entity, int color, long until) {
        entity.getPersistentData().putInt(COLOR, color);
        entity.getPersistentData().putLong(UNTIL, until);
    }

    public static int color(Entity entity) {
        return entity.getPersistentData().getLong(UNTIL) > entity.level().getGameTime() ? entity.getPersistentData().getInt(COLOR) : -1;
    }

    @SubscribeEvent
    public void tracking(PlayerEvent.StartTracking event) {
        Entity target = event.getTarget();
        if (event.getEntity() instanceof ServerPlayer player && color(target) >= 0) {
            PacketDistributor.sendToPlayer(player, new ChromaticGlowPayload(target.getId(), color(target), target.getPersistentData().getLong(UNTIL)));
        }
    }
}

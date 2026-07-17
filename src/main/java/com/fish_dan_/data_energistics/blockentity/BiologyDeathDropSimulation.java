package com.fish_dan_.data_energistics.blockentity;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;

/**
 * Produces biology-carrier drops without broadcasting a real entity death to unrelated world systems.
 *
 * <p>
 * A data mimetic field needs vanilla loot tables, equipment and custom drops, plus the standard living-drops event.
 * It does not represent an entity dying in the world, so the full death lifecycle must not run for every output roll.
 */
public interface BiologyDeathDropSimulation {

    /**
     * Runs the loot-producing phase of a simulated player-caused death.
     *
     * @param level      server level used to resolve loot and fire drop events
     * @param entity     initialized simulated entity
     * @param fakePlayer player context used by loot conditions
     */
    void generateDrops(ServerLevel level, LivingEntity entity, Player fakePlayer);
}

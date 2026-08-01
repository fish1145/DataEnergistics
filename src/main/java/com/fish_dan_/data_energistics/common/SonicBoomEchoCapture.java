package com.fish_dan_.data_energistics.common;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.warden.Warden;

/**
 * Captures the resource created when a real Warden sonic boom crosses eligible formation planes.
 *
 * <p>
 * This boundary keeps event recognition separate from the world scan and insertion mechanics so tests and other
 * server-side callers can exercise the same production path without synthesizing a damage event.
 */
public interface SonicBoomEchoCapture {

    /**
     * Scans the Warden-to-target sonic path and gives every distinct eligible physical formation plane an independent
     * ten-percent chance to insert one Echo.
     *
     * @param level  server level containing the already-loaded path chunks
     * @param warden direct Warden source of the sonic boom
     * @param target living entity whose eye position defines the original sonic target
     * @return number of Echo units successfully inserted
     */
    int capture(ServerLevel level, Warden warden, LivingEntity target);
}

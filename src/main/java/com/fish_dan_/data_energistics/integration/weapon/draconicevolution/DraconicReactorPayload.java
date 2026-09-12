package com.fish_dan_.data_energistics.integration.weapon.draconicevolution;

import com.fish_dan_.data_energistics.Data_Energistics;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.item.FallingBlockEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;

import com.brandon3055.brandonscore.handlers.IProcess;
import com.brandon3055.brandonscore.handlers.ProcessHandler;
import com.brandon3055.draconicevolution.DEConfig;
import com.brandon3055.draconicevolution.blocks.reactor.ProcessExplosion;
import com.brandon3055.draconicevolution.init.DEContent;

/** Optional Draconic Evolution adapter; callers must check mod presence before loading this class. */
public final class DraconicReactorPayload {

    // TileReactorCore maps a full 10,368-unit fuel load to this radius before applying the server multiplier.
    private static final int FULL_FUEL_RADIUS = 350;

    private DraconicReactorPayload() {}

    /** Accepts the reactor core block item, including its item components. Other Draconic cores are not ammunition. */
    public static boolean isReactorCore(ItemStack ammunition) {
        return ammunition.is(DEContent.REACTOR_CORE.get().asItem());
    }

    /**
     * Starts the full-fuel native reactor explosion on the server thread, with no additional countdown.
     * Terrain calculation and detonation are advanced by BrandonsCore's native process scheduler.
     * Invalid explosion multipliers are rejected; the caller owns the collision error boundary.
     */
    public static void detonate(ServerLevel level, Vec3 impact) {
        if (DEConfig.disableLargeReactorBoom) {
            detonateSmallReactor(level, impact);
            return;
        }
        double radius = FULL_FUEL_RADIUS * DEConfig.reactorExplosionScale;
        if (!Double.isFinite(radius) || radius < 1 || radius > Integer.MAX_VALUE) {
            throw new IllegalArgumentException("Invalid Draconic reactor explosion radius: " + radius);
        }
        BlockPos origin = BlockPos.containing(impact);
        ProcessExplosion explosion = new ProcessExplosion(origin, (int) radius, level, 0);
        ProcessHandler.addProcess(new ReactorExplosionProcess(explosion, level, origin));
    }

    /** Matches TileReactorCore's small explosion and ejected lava when large reactor explosions are disabled. */
    private static void detonateSmallReactor(ServerLevel level, Vec3 impact) {
        level.explode(null, impact.x, impact.y, impact.z, 8, Level.ExplosionInteraction.BLOCK);
        int lavaCount = 25 + level.random.nextInt(25);
        for (int i = 0; i < lavaCount; i++) {
            FallingBlockEntity lava = FallingBlockEntity.fall(level, BlockPos.containing(impact), Blocks.LAVA.defaultBlockState());
            lava.time = 1;
            lava.dropItem = false;
            double speed = 0.5 + 2 * level.random.nextDouble();
            lava.push((level.random.nextDouble() - 0.5) * speed, level.random.nextDouble() / 1.5 * speed,
                    (level.random.nextDouble() - 0.5) * speed);
        }
    }

    /** Isolates a failed native calculation to this shot; the native scheduler clears processes on server stop. */
    private record ReactorExplosionProcess(ProcessExplosion explosion, ServerLevel level, BlockPos origin) implements IProcess {

        @Override
        public void updateProcess() {
            try {
                this.explosion.updateProcess();
            } catch (RuntimeException exception) {
                this.explosion.isDead = true;
                Data_Energistics.LOGGER.error("Reactor core grenade explosion failed at {} in {}", this.origin,
                        this.level.dimension().location(), exception);
            }
        }

        @Override
        public boolean isDead() {
            return this.explosion.isDead();
        }
    }
}

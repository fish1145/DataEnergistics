package com.fish_dan_.data_energistics.blockentity;

import com.fish_dan_.data_energistics.Data_Energistics;
import com.fish_dan_.data_energistics.blockentity.DataMimeticFieldBlockEntity.GeneratedLoot;

import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.animal.Cow;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import net.neoforged.testframework.annotation.TestHolder;
import net.neoforged.testframework.gametest.EmptyTemplate;

import appeng.util.Platform;
import org.jetbrains.annotations.Nullable;

import java.util.Objects;

/**
 * Verifies that mimetic biology drops run only the loot-producing death phase.
 */
@GameTestHolder(Data_Energistics.MODID)
@PrefixGameTestTemplate(false)
public final class BiologyDeathDropSimulationImplTest {

    private BiologyDeathDropSimulationImplTest() {}

    /**
     * Guards against reintroducing the full entity death lifecycle for every simulated loot roll.
     *
     * @param helper game-test world access and assertions
     */
    @TestHolder("biology_death_drop_simulation_skips_full_death")
    @EmptyTemplate("5")
    @GameTest(template = "empty_5x5")
    public static void skipsFullDeathAndInvokesDropPhase(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        TrackingCow cow = new TrackingCow(level);
        Player fakePlayer = Platform.getFakePlayer(level, null);
        BiologyDeathDropSimulation simulation = new BiologyDeathDropSimulationImpl();

        simulation.generateDrops(level, cow, fakePlayer);

        helper.assertValueEqual(cow.dieCalls, 0, "Simulated drops must not run the full death lifecycle");
        helper.assertValueEqual(cow.dropCalls, 1, "Simulated drops must run the vanilla drop phase exactly once");
        helper.assertTrue(cow.dropLevel == level, "The drop phase must use the owning server level");
        helper.assertTrue(cow.dropSource != null && cow.dropSource.getEntity() == fakePlayer,
                "The drop phase must use a player-caused damage source");
        helper.assertTrue(cow.lastHurtByPlayer == fakePlayer,
                "Loot conditions must observe the simulated player kill");
        helper.assertValueEqual(cow.tickCount, 100, "The simulated kill must satisfy recent-player-hit timing");
        helper.assertTrue(cow.isMarkedDead(), "Drop listeners must observe vanilla death-phase entity state");
        helper.assertTrue(cow.wasExperienceConsumed(), "The drop phase must not spawn experience entities");
        helper.succeed();
    }

    /**
     * Verifies the real vanilla drop phase still reaches the mimetic-field capture listener.
     *
     * @param helper game-test world access and assertions
     */
    @TestHolder("biology_death_drop_simulation_captures_vanilla_drops_without_full_death")
    @EmptyTemplate("5")
    @GameTest(template = "empty_5x5")
    public static void capturesVanillaDropsWithoutFullDeathLifecycle(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        Cow cow = Objects.requireNonNull(EntityType.COW.create(level), "Cow entity type must create a living entity");
        cow.setPos(Vec3.atCenterOf(helper.absolutePos(new BlockPos(2, 2, 2))));
        Player fakePlayer = Platform.getFakePlayer(level, null);

        GeneratedLoot generated = DataMimeticFieldBlockEntity.simulateEntityDrops(level, cow, fakePlayer);

        helper.assertTrue(generated.stacks().stream().anyMatch(stack -> stack.is(Items.BEEF)),
                "The simulated vanilla cow drop phase must produce beef");
        AABB nearby = AABB.ofSize(cow.position(), 4.0D, 4.0D, 4.0D);
        helper.assertTrue(level.getEntitiesOfClass(ItemEntity.class, nearby).isEmpty(),
                "Captured simulated drops must not leak item entities into the world");
        helper.succeed();
    }

    /**
     * Records which death entry points the simulation invokes without producing world-side drops.
     */
    private static final class TrackingCow extends Cow {

        /** Number of full death lifecycle invocations. */
        private int dieCalls;

        /** Number of loot-producing death phase invocations. */
        private int dropCalls;

        /** Server level supplied to the loot-producing phase. */
        private ServerLevel dropLevel;

        /** Damage source supplied to the loot-producing phase. */
        private DamageSource dropSource;

        /** Player recorded as the simulated entity's most recent attacker. */
        private Player lastHurtByPlayer;

        /**
         * Creates an inert cow whose death entry points can be observed.
         *
         * @param level game-test level
         */
        private TrackingCow(Level level) {
            super(EntityType.COW, level);
        }

        /**
         * Records accidental full death lifecycle calls.
         *
         * @param damageSource simulated damage source
         */
        @Override
        public void die(DamageSource damageSource) {
            this.dieCalls++;
        }

        /**
         * Records the player-kill context while preserving vanilla loot state.
         *
         * @param player simulated attacking player
         */
        @Override
        public void setLastHurtByPlayer(@Nullable Player player) {
            super.setLastHurtByPlayer(player);
            this.lastHurtByPlayer = player;
        }

        /**
         * Records the intended vanilla loot-producing death phase.
         *
         * @param level        server level used for loot generation
         * @param damageSource simulated damage source
         */
        @Override
        protected void dropAllDeathLoot(ServerLevel level, DamageSource damageSource) {
            this.dropCalls++;
            this.dropLevel = level;
            this.dropSource = damageSource;
        }

        /**
         * Exposes the protected vanilla death-phase marker for direct behavior verification.
         *
         * @return true when the simulation marked this entity as dead
         */
        private boolean isMarkedDead() {
            return this.dead;
        }
    }
}

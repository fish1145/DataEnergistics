package com.fish_dan_.data_energistics.blockentity;

import com.fish_dan_.data_energistics.Data_Energistics;
import com.fish_dan_.data_energistics.blockentity.DataMimeticFieldBlockEntity.GeneratedLoot;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.animal.Cow;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.event.entity.living.LivingDropsEvent;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import net.neoforged.testframework.annotation.TestHolder;
import net.neoforged.testframework.gametest.EmptyTemplate;

import appeng.util.Platform;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Objects;

/**
 * Verifies that mimetic biology drops run only the loot-producing death phase.
 */
@GameTestHolder(Data_Energistics.MODID)
@PrefixGameTestTemplate(false)
public final class VanillaBiologyDeathDropSimulationTest {

    private VanillaBiologyDeathDropSimulationTest() {}

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
        VanillaBiologyDeathDropSimulation simulation = new VanillaBiologyDeathDropSimulation();

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
     * Verifies event-style drops spawned directly into the level are redirected into the simulated result.
     *
     * @param helper game-test world access and assertions
     */
    @TestHolder("biology_death_drop_simulation_captures_spawned_drops")
    @EmptyTemplate("5")
    @GameTest(template = "empty_5x5")
    public static void capturesSpawnedDropsWithoutLeakingCaptureState(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        ScopedSpawnedDropCow cow = new ScopedSpawnedDropCow(level);
        cow.setPos(Vec3.atCenterOf(helper.absolutePos(new BlockPos(2, 2, 2))));
        Player fakePlayer = Platform.getFakePlayer(level, null);

        GeneratedLoot generated = DataMimeticFieldBlockEntity.simulateEntityDrops(level, cow, fakePlayer);

        helper.assertTrue(generated.stacks().stream().anyMatch(stack -> stack.is(Items.BEEF)),
                "The simulated result must retain the vanilla living-drops collection");
        int spawnedDropCount = generated.stacks().stream()
                .filter(stack -> stack.is(Items.HEART_OF_THE_SEA))
                .mapToInt(ItemStack::getCount)
                .sum();
        helper.assertValueEqual(spawnedDropCount, 1,
                "The same item entity must be captured only once across living-drops and entity-join paths");
        helper.assertTrue(cow.syntheticLivingDropsCanceled,
                "The synthetic living-drops path must be redirected into the simulated result");
        helper.assertTrue(!cow.nearbyDropAdded,
                "The directly spawned nearby drop must be canceled before joining the level");
        helper.assertTrue(generated.stacks().stream().noneMatch(stack -> stack.is(Items.DIAMOND)),
                "A far-away item created while the simulation is active must not enter the simulated result");
        helper.assertTrue(generated.stacks().stream().noneMatch(stack -> stack.is(Items.EMERALD)),
                "A previously vetoed entity-join event must not enter the simulated result");
        helper.assertTrue(generated.stacks().stream().noneMatch(stack -> stack.is(Items.GOLD_INGOT)),
                "An item loaded from disk must not enter the simulated result");
        helper.assertTrue(cow.farDropAdded,
                "A far-away unrelated item must still be allowed to join the level during simulation");
        helper.assertTrue(!cow.emptyDropEventCanceled,
                "An empty item entity must not be intercepted by simulated-drop capture");
        helper.assertTrue(!cow.loadedDropEventCanceled,
                "An item loaded from disk must not be intercepted by simulated-drop capture");

        AABB nearby = AABB.ofSize(cow.position(), 4.0D, 4.0D, 4.0D);
        helper.assertTrue(level.getEntitiesOfClass(ItemEntity.class, nearby).isEmpty(),
                "Directly spawned simulated drops must not leak item entities into the world");
        ItemEntity farDrop = Objects.requireNonNull(cow.farDrop, "The scoped drop cow must create a far-away item");
        AABB aroundFarDrop = AABB.ofSize(farDrop.position(), 2.0D, 2.0D, 2.0D);
        helper.assertTrue(level.getEntitiesOfClass(ItemEntity.class, aroundFarDrop).contains(farDrop),
                "The far-away unrelated item must remain in the world");

        ItemEntity ordinaryDrop = new ItemEntity(level, cow.getX(), cow.getY(), cow.getZ(), new ItemStack(Items.DIAMOND));
        helper.assertTrue(level.addFreshEntity(ordinaryDrop),
                "Item entities created after a simulation must still be allowed to join the level");
        helper.assertTrue(level.getEntitiesOfClass(ItemEntity.class, nearby).contains(ordinaryDrop),
                "The simulated-drop capture context must be removed after the simulation");
        helper.succeed();
    }

    /**
     * Verifies a failed simulated drop phase cannot leave its capture context installed.
     *
     * @param helper game-test world access and assertions
     */
    @TestHolder("biology_death_drop_simulation_clears_capture_after_failure")
    @EmptyTemplate("5")
    @GameTest(template = "empty_5x5")
    public static void clearsCaptureStateAfterDropFailure(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        FailingDropCow cow = new FailingDropCow(level);
        cow.setPos(Vec3.atCenterOf(helper.absolutePos(new BlockPos(2, 2, 2))));
        Player fakePlayer = Platform.getFakePlayer(level, null);

        try {
            DataMimeticFieldBlockEntity.simulateEntityDrops(level, cow, fakePlayer);
            helper.fail("The injected drop failure must escape the simulation");
        } catch (SimulatedDropFailure expected) {
            helper.assertValueEqual(expected.getMessage(), SimulatedDropFailure.MESSAGE,
                    "The simulation must propagate the original drop failure");
        }

        ItemEntity ordinaryDrop = new ItemEntity(level, cow.getX(), cow.getY(), cow.getZ(), new ItemStack(Items.DIAMOND));
        helper.assertTrue(level.addFreshEntity(ordinaryDrop),
                "A failed simulation must remove the item-capture context in its finally block");
        AABB nearby = AABB.ofSize(cow.position(), 4.0D, 4.0D, 4.0D);
        helper.assertTrue(level.getEntitiesOfClass(ItemEntity.class, nearby).contains(ordinaryDrop),
                "Items created after a failed simulation must remain in the world");
        helper.succeed();
    }

    /**
     * Verifies the real Baubley Heart Canisters listener is captured without leaking its spawned heart entity.
     *
     * @param helper game-test world access and assertions
     */
    @TestHolder("biology_death_drop_simulation_captures_bhc_heart")
    @EmptyTemplate("5")
    @GameTest(template = "empty_5x5")
    public static void capturesRealBaubleyHeartCanistersDrop(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        LivingEntity wither = Objects.requireNonNull(
                EntityType.WITHER.create(level),
                "Wither entity type must create a living entity");
        wither.setPos(Vec3.atCenterOf(helper.absolutePos(new BlockPos(2, 2, 2))));
        Player fakePlayer = Platform.getFakePlayer(level, null);
        var yellowHeart = BuiltInRegistries.ITEM.getOptional(
                ResourceLocation.fromNamespaceAndPath("bhc", "yellow_heart"))
                .orElseThrow(() -> new IllegalStateException("Baubley Heart Canisters test dependency is not loaded"));

        GeneratedLoot generated = DataMimeticFieldBlockEntity.simulateEntityDrops(level, wither, fakePlayer);

        int heartCount = generated.stacks().stream()
                .filter(stack -> stack.is(yellowHeart))
                .mapToInt(ItemStack::getCount)
                .sum();
        helper.assertValueEqual(heartCount, 1,
                "The real BHC boss drop must be captured exactly once");
        helper.assertTrue(generated.stacks().stream().anyMatch(stack -> stack.is(Items.NETHER_STAR)),
                "Capturing the BHC heart must retain the Wither's ordinary nether-star drop");
        AABB nearby = AABB.ofSize(wither.position(), 4.0D, 4.0D, 4.0D);
        helper.assertTrue(level.getEntitiesOfClass(ItemEntity.class, nearby).isEmpty(),
                "The real BHC heart must not leak an item entity into the world");
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

    /**
     * Produces a direct level-spawned item after the standard living-drops event has completed.
     */
    private static final class ScopedSpawnedDropCow extends Cow {

        /** Whether the synthetic living-drops event was redirected. */
        private boolean syntheticLivingDropsCanceled;

        /** Whether the nearby duplicate drop was admitted to the level. */
        private boolean nearbyDropAdded;

        /** Whether the far-away unrelated drop was admitted to the level. */
        private boolean farDropAdded;

        /** Whether an empty item's admission event was intercepted. */
        private boolean emptyDropEventCanceled;

        /** Whether a disk-loaded item's admission event was intercepted. */
        private boolean loadedDropEventCanceled;

        /** Far-away item used to verify world admission during simulation. */
        private ItemEntity farDrop;

        /**
         * Creates a cow whose drop phase models mods that spawn additional item entities directly.
         *
         * @param level game-test level
         */
        private ScopedSpawnedDropCow(Level level) {
            super(EntityType.COW, level);
        }

        /**
         * Preserves vanilla drops and emits one additional item through the entity-join path.
         *
         * @param level        server level used for loot generation
         * @param damageSource simulated damage source
         */
        @Override
        protected void dropAllDeathLoot(ServerLevel level, DamageSource damageSource) {
            super.dropAllDeathLoot(level, damageSource);

            ItemEntity nearbyDrop = new ItemEntity(
                    level,
                    this.getX(),
                    this.getY(),
                    this.getZ(),
                    new ItemStack(Items.HEART_OF_THE_SEA));
            LivingDropsEvent livingDropsEvent = new LivingDropsEvent(
                    this,
                    damageSource,
                    List.of(nearbyDrop),
                    true);
            DataMimeticFieldBlockEntity.captureSimulatedDeathDrops(livingDropsEvent);
            this.syntheticLivingDropsCanceled = livingDropsEvent.isCanceled();
            this.nearbyDropAdded = level.addFreshEntity(nearbyDrop);

            this.farDrop = new ItemEntity(
                    level,
                    this.getX() + 8.0D,
                    this.getY(),
                    this.getZ(),
                    new ItemStack(Items.DIAMOND));
            this.farDropAdded = level.addFreshEntity(this.farDrop);

            EntityJoinLevelEvent emptyDropEvent = new EntityJoinLevelEvent(
                    new ItemEntity(level, this.getX(), this.getY(), this.getZ(), ItemStack.EMPTY),
                    level);
            DataMimeticFieldBlockEntity.captureSimulatedSpawnedDrops(emptyDropEvent);
            this.emptyDropEventCanceled = emptyDropEvent.isCanceled();

            EntityJoinLevelEvent loadedDropEvent = new EntityJoinLevelEvent(
                    new ItemEntity(level, this.getX(), this.getY(), this.getZ(), new ItemStack(Items.GOLD_INGOT)),
                    level,
                    true);
            DataMimeticFieldBlockEntity.captureSimulatedSpawnedDrops(loadedDropEvent);
            this.loadedDropEventCanceled = loadedDropEvent.isCanceled();

            EntityJoinLevelEvent vetoedDropEvent = new EntityJoinLevelEvent(
                    new ItemEntity(level, this.getX(), this.getY(), this.getZ(), new ItemStack(Items.EMERALD)),
                    level);
            vetoedDropEvent.setCanceled(true);
            DataMimeticFieldBlockEntity.captureSimulatedSpawnedDrops(vetoedDropEvent);
        }
    }

    /** Throws from the simulated drop phase to exercise capture-context cleanup. */
    private static final class FailingDropCow extends Cow {

        /**
         * Creates an entity whose drop phase always fails.
         *
         * @param level game-test level
         */
        private FailingDropCow(Level level) {
            super(EntityType.COW, level);
        }

        /**
         * Injects the expected failure while the simulated-drop context is active.
         *
         * @param level        server level used for loot generation
         * @param damageSource simulated damage source
         */
        @Override
        protected void dropAllDeathLoot(ServerLevel level, DamageSource damageSource) {
            throw new SimulatedDropFailure();
        }
    }

    /** Expected failure used to verify simulated-drop context cleanup. */
    private static final class SimulatedDropFailure extends RuntimeException {

        /** Stable failure detail used to verify propagation. */
        private static final String MESSAGE = "injected simulated drop failure";

        private SimulatedDropFailure() {
            super(MESSAGE);
        }
    }
}

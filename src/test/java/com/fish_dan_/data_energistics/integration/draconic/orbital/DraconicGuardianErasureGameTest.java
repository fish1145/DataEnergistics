package com.fish_dan_.data_energistics.integration.draconic.orbital;

import com.fish_dan_.data_energistics.Data_Energistics;
import com.fish_dan_.data_energistics.orbital.attack.entity.OrbitalEntityErasure;
import com.fish_dan_.data_energistics.orbital.attack.entity.strike.OrbitalErasureOutcome;
import com.fish_dan_.data_energistics.orbital.attack.entity.strike.OrbitalErasureStrike;

import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity.RemovalReason;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import net.neoforged.testframework.annotation.TestHolder;
import net.neoforged.testframework.gametest.EmptyTemplate;
import net.neoforged.testframework.gametest.ExtendedGameTestHelper;
import net.neoforged.testframework.gametest.GameTestPlayer;

import com.brandon3055.brandonscore.worldentity.WorldEntityHandler;
import com.brandon3055.draconicevolution.entity.guardian.DraconicGuardianEntity;
import com.brandon3055.draconicevolution.entity.guardian.GuardianFightManager;
import com.brandon3055.draconicevolution.entity.guardian.control.PhaseType;
import com.brandon3055.draconicevolution.init.DEContent;

import java.util.Objects;
import java.util.Set;
import java.util.UUID;

@GameTestHolder(Data_Energistics.MODID)
@PrefixGameTestTemplate(false)
public final class DraconicGuardianErasureGameTest {

    private DraconicGuardianErasureGameTest() {}

    @TestHolder("orbital_guardian_part_hit_finishes_encounter_once_and_allows_a_later_encounter")
    @EmptyTemplate("50x32x50")
    @GameTest(template = "empty_50x32x50", batch = "orbital_guardian_erasure")
    public static void partHitFinishesEncounterOnce(GameTestHelper helper) {
        Fixture fixture = fixture(helper, new GuardianFightManager(), true);
        OrbitalErasureStrike strike = strike();
        try {
            long before = hearts(helper.getLevel(), fixture.arena());
            helper.assertTrue(fixture.manager().getTrackedPlayers().contains(fixture.viewer()), "The fixture must expose an active real boss bar");
            var part = fixture.guardian().getParts()[0];
            helper.assertValueEqual(OrbitalEntityErasure.eraseHit(part, strike), OrbitalErasureOutcome.GUARDIAN_ENCOUNTER_COMPLETED,
                    "A part hit must terminate the guardian's own encounter lifecycle");
            helper.assertTrue(fixture.guardian().isRemoved() && fixture.manager().isRemoved(), "Both guardian and encounter must finish");
            helper.assertTrue(fixture.manager().getTrackedPlayers().isEmpty(), "The encounter must release every boss-bar viewer");
            helper.assertTrue(WorldEntityHandler.getWorldEntity(helper.getLevel(), fixture.manager().getUniqueID()) == null,
                    "The ended encounter must leave the world-entity registry");
            CompoundTag ended = new CompoundTag();
            fixture.manager().write(ended);
            helper.assertTrue(ended.getBoolean("guardian_killed"), "The real guardian settlement must record its defeated state");
            helper.assertValueEqual(hearts(helper.getLevel(), fixture.arena()), before + 1, "The kill entry point must produce one dragon heart");
            var generated = helper.getLevel().getEntitiesOfClass(ItemEntity.class, new AABB(fixture.arena().above(20)).inflate(2),
                    item -> strike.result(item.getUUID()).map(result -> result.outcome() == OrbitalErasureOutcome.EXEMPT).orElse(false));
            helper.assertValueEqual(generated.size(), 1, "The producing strike must protect exactly its newly generated reward");
            helper.assertValueEqual(OrbitalEntityErasure.eraseHit(generated.getFirst(), strike), OrbitalErasureOutcome.ALREADY_HANDLED,
                    "A continuing beam or sphere must not erase its own guardian reward");
            helper.assertFalse(generated.getFirst().isRemoved(), "The reward must survive subsequent contact from the same strike");
            for (var anotherPart : fixture.guardian().getParts()) {
                OrbitalEntityErasure.eraseHit(anotherPart, strike);
            }
            helper.assertValueEqual(hearts(helper.getLevel(), fixture.arena()), before + 1, "Further part hits must not repeat rewards");
            helper.assertTrue(strike.result(fixture.guardian().getUUID()).isPresent() && strike.result(part.getUUID()).isEmpty(),
                    "Deduplication must use the real guardian UUID rather than a part UUID");
            var restored = OrbitalErasureStrike.load(strike.strikeId(), Set.of(), strike.save());
            helper.assertFalse(restored.claimEncounter(fixture.manager().getUniqueID()), "The encounter claim must survive journal reload");
            Fixture next = fixture(helper, new GuardianFightManager(), true);
            try {
                helper.assertValueEqual(OrbitalEntityErasure.eraseHit(next.guardian(), strike()), OrbitalErasureOutcome.GUARDIAN_ENCOUNTER_COMPLETED,
                        "A new legitimate encounter at the same location must not inherit a permanent erasure ban");
                helper.assertValueEqual(hearts(helper.getLevel(), fixture.arena()), before + 2, "Each independent encounter keeps its own single reward");
            } finally {
                cleanup(next);
            }
        } finally {
            cleanup(fixture);
        }
        helper.succeed();
    }

    @TestHolder("orbital_guardian_restores_its_exact_uuid_link_before_ending_an_orphaned_encounter")
    @EmptyTemplate("50x32x50")
    @GameTest(template = "empty_50x32x50", batch = "orbital_guardian_erasure")
    public static void orphanedGuardianUsesItsExactPersistedEncounter(GameTestHelper helper) {
        Fixture fixture = fixture(helper, new GuardianFightManager(), false);
        try {
            helper.assertTrue(fixture.guardian().getFightManager() == null, "The fixture must start without an entity-side encounter link");
            helper.assertValueEqual(OrbitalEntityErasure.eraseHit(fixture.guardian(), strike()), OrbitalErasureOutcome.GUARDIAN_ENCOUNTER_COMPLETED,
                    "The adapter must recover the registered encounter by guardian UUID");
            helper.assertTrue(fixture.guardian().getFightManager() == fixture.manager() && fixture.manager().isRemoved(),
                    "The recovered exact encounter must complete, rather than leaving an orphaned manager");
        } finally {
            cleanup(fixture);
        }
        helper.succeed();
    }

    @TestHolder("orbital_guardian_partial_failure_releases_encounter_resources_without_replaying_rewards")
    @EmptyTemplate("50x32x50")
    @GameTest(template = "empty_50x32x50", batch = "orbital_guardian_erasure")
    public static void partialSettlementFailureReleasesEncounterResources(GameTestHelper helper) {
        Fixture fixture = fixture(helper, new FailingSettlement(), true);
        OrbitalErasureStrike strike = strike();
        try {
            long before = hearts(helper.getLevel(), fixture.arena());
            helper.assertValueEqual(OrbitalEntityErasure.eraseHit(fixture.guardian(), strike), OrbitalErasureOutcome.PARTIAL_FAILURE,
                    "A failed encounter callback must not be disguised as a completed settlement");
            helper.assertTrue(fixture.guardian().isRemoved() && fixture.manager().isRemoved(),
                    "Safe cleanup must prevent the failed old encounter from continuing to track a missing guardian");
            helper.assertTrue(fixture.manager().getTrackedPlayers().isEmpty(), "Partial failure must still release boss-bar viewers");
            helper.assertValueEqual(hearts(helper.getLevel(), fixture.arena()), before, "Failure cleanup must not invent or replay a reward");
            helper.assertValueEqual(strike.result(fixture.guardian().getUUID()).orElseThrow().outcome(), OrbitalErasureOutcome.PARTIAL_FAILURE,
                    "The strike journal must retain the real partial result");
        } finally {
            cleanup(fixture);
        }
        helper.succeed();
    }

    @TestHolder("orbital_guardian_ambiguous_uuid_binding_fails_before_removal")
    @EmptyTemplate("50x32x50")
    @GameTest(template = "empty_50x32x50", batch = "orbital_guardian_erasure")
    public static void ambiguousEncounterBindingDoesNotGuess(GameTestHelper helper) {
        Fixture fixture = fixture(helper, new GuardianFightManager(), false);
        GuardianFightManager duplicate = new GuardianFightManager();
        duplicate.setLevel(helper.getLevel());
        duplicate.read(encounterTag(fixture.guardian(), fixture.arena()));
        WorldEntityHandler.addWorldEntity(helper.getLevel(), duplicate);
        try {
            helper.assertValueEqual(OrbitalEntityErasure.eraseHit(fixture.guardian(), strike()), OrbitalErasureOutcome.FAILED_BEFORE_COMMIT,
                    "Multiple UUID matches must be reported instead of choosing a random encounter");
            helper.assertFalse(fixture.guardian().isRemoved(), "Ambiguous ownership must fail before guardian removal");
        } finally {
            ((GuardianEncounterCleanup) duplicate).dataEnergistics$releaseErasedEncounter();
            cleanup(fixture);
        }
        helper.succeed();
    }

    private static Fixture fixture(GameTestHelper helper, GuardianFightManager manager, boolean linked) {
        ServerLevel level = helper.getLevel();
        BlockPos arena = helper.absolutePos(new BlockPos(25, 5, 25));
        DraconicGuardianEntity guardian = Objects.requireNonNull(DEContent.ENTITY_DRACONIC_GUARDIAN.get().create(level));
        guardian.moveTo(Vec3.atBottomCenterOf(arena.above(5)));
        guardian.setNoAi(true);
        guardian.setInvulnerable(true);
        guardian.setShieldPower(10_000);
        guardian.setArenaOrigin(arena);
        guardian.getPhaseManager().setPhase(PhaseType.START);
        manager.setLevel(level);
        manager.read(encounterTag(guardian, arena));
        if (linked) {
            guardian.setFightManager(manager);
        }
        helper.assertTrue(level.addFreshEntity(guardian), "The real guardian must enter the server world");
        WorldEntityHandler.addWorldEntity(level, manager);
        GameTestPlayer viewer = new ExtendedGameTestHelper(helper.testInfo).makeTickingMockServerPlayerInLevel(GameType.SURVIVAL);
        viewer.moveTo(Vec3.atBottomCenterOf(arena.above(2)));
        viewer.setNoGravity(true);
        for (int tick = 0; tick < 20; tick++) {
            manager.tick();
        }
        return new Fixture(guardian, manager, arena, viewer);
    }

    private static CompoundTag encounterTag(DraconicGuardianEntity guardian, BlockPos arena) {
        CompoundTag tag = new CompoundTag();
        tag.putUUID("guardian", guardian.getUUID());
        tag.put("arena_origin", NbtUtils.writeBlockPos(arena));
        return tag;
    }

    private static long hearts(ServerLevel level, BlockPos arena) {
        return level.getEntitiesOfClass(ItemEntity.class, new AABB(arena.above(20)).inflate(2),
                item -> item.getItem().is(DEContent.DRAGON_HEART.get())).size();
    }

    private static void cleanup(Fixture fixture) {
        if (!fixture.manager().isRemoved()) {
            ((GuardianEncounterCleanup) fixture.manager()).dataEnergistics$releaseErasedEncounter();
        }
        if (!fixture.guardian().isRemoved()) {
            fixture.guardian().setRemoved(RemovalReason.DISCARDED);
        }
    }

    private static OrbitalErasureStrike strike() {
        return new OrbitalErasureStrike(UUID.randomUUID(), null, Set.of());
    }

    private record Fixture(DraconicGuardianEntity guardian, GuardianFightManager manager, BlockPos arena, GameTestPlayer viewer) {}

    private static final class FailingSettlement extends GuardianFightManager {

        @Override
        public void processDragonDeath(DraconicGuardianEntity guardian) {
            throw new IllegalStateException("Intentional guardian settlement callback failure");
        }
    }
}

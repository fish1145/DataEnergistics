package com.fish_dan_.data_energistics.orbital.attack;

import com.fish_dan_.data_energistics.Data_Energistics;
import com.fish_dan_.data_energistics.orbital.attack.entity.OrbitalEntityErasure;
import com.fish_dan_.data_energistics.orbital.attack.entity.strike.OrbitalErasureOutcome;
import com.fish_dan_.data_energistics.orbital.attack.work.OrbitalAttackWorkState;

import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import net.neoforged.testframework.annotation.TestHolder;
import net.neoforged.testframework.gametest.EmptyTemplate;

import java.util.Objects;
import java.util.UUID;

@GameTestHolder(Data_Energistics.MODID)
@PrefixGameTestTemplate(false)
public final class OrbitalErasureJournalGameTest {

    private OrbitalErasureJournalGameTest() {}

    @TestHolder("orbital_saved_attack_retains_entity_deduplication_across_journal_reload")
    @EmptyTemplate("50x32x50")
    @GameTest(template = "empty_50x32x50")
    public static void restoredAttackDoesNotEraseTheSameSubjectAgain(GameTestHelper helper) {
        UUID attackId = UUID.randomUUID();
        var level = helper.getLevel();
        var data = OrbitalAttackSavedData.load(root(attackTag(helper, attackId)), level.registryAccess());
        Zombie first = helper.spawn(EntityType.ZOMBIE, new BlockPos(15, 10, 15));
        UUID subject = first.getUUID();
        var journal = data.entityErasureFor(attackId);
        helper.assertValueEqual(OrbitalEntityErasure.eraseHit(first, journal), OrbitalErasureOutcome.ENTITY_REMOVED,
                "An old attack without a journal must still execute a valid first erasure");
        UUID uncertain = UUID.randomUUID();
        journal.begin(uncertain);
        var restored = OrbitalAttackSavedData.load(data.save(new CompoundTag(), level.registryAccess()), level.registryAccess());
        var resumed = restored.entityErasureFor(attackId);
        helper.assertValueEqual(resumed.result(uncertain).orElseThrow().outcome(), OrbitalErasureOutcome.PARTIAL_FAILURE,
                "An execution interrupted before recording its result must not resume as a fresh attempt");
        helper.startSequence().thenIdle(2).thenExecute(() -> {
            Zombie replacement = Objects.requireNonNull(EntityType.ZOMBIE.create(level));
            replacement.setUUID(subject);
            replacement.moveTo(Vec3.atBottomCenterOf(helper.absolutePos(new BlockPos(15, 10, 15))));
            replacement.setNoAi(true);
            helper.assertTrue(level.addFreshEntity(replacement), "A replacement entity with the same UUID must enter the real world");
            helper.assertValueEqual(OrbitalEntityErasure.eraseHit(replacement, resumed), OrbitalErasureOutcome.ALREADY_HANDLED,
                    "Reloaded SavedData must retain strike-level deduplication");
            helper.assertTrue(replacement.isAlive(), "The new object must survive a repeated hit from the restored strike");
        }).thenSucceed();
    }

    @TestHolder("orbital_invalid_erasure_journal_retains_escrow_and_disables_unsafe_retry")
    @EmptyTemplate("50x32x50")
    @GameTest(template = "empty_50x32x50")
    public static void invalidJournalFaultsOnlyItsAttack(GameTestHelper helper) {
        UUID attackId = UUID.randomUUID();
        CompoundTag attack = attackTag(helper, attackId);
        CompoundTag invalid = new CompoundTag();
        invalid.putInt("subjects", 7);
        attack.put("erasure_journal", invalid);
        var level = helper.getLevel();
        var data = OrbitalAttackSavedData.load(root(attack), level.registryAccess());
        var faulted = data.find(attackId).orElseThrow();
        helper.assertValueEqual(faulted.phase(), OrbitalAttackPhase.FAULTED, "Malformed deduplication must stop this attack without losing its record");
        helper.assertValueEqual(faulted.celestialEscrow(), 10L, "Faulting a journal must retain the original escrow");
        helper.assertFalse(data.retryFaulted(level.getServer(), attackId), "Unknown prior effects must not be automatically replayed");
        var restored = OrbitalAttackSavedData.load(data.save(new CompoundTag(), level.registryAccess()), level.registryAccess());
        helper.assertFalse(restored.retryFaulted(level.getServer(), attackId), "A subsequent load must not silently clear journal quarantine");
        helper.succeed();
    }

    private static CompoundTag attackTag(GameTestHelper helper, UUID attackId) {
        CompoundTag tag = new CompoundTag();
        tag.putUUID("attack_id", attackId);
        tag.putUUID("weapon_id", UUID.randomUUID());
        tag.putString("mode", OrbitalAttackMode.DIRECTED_ENERGY.name());
        tag.putString("phase", OrbitalAttackPhase.DELIVERY.name());
        tag.putLong("phase_started_at", 0);
        tag.putString("dimension", helper.getLevel().dimension().location().toString());
        tag.put("target", NbtUtils.writeBlockPos(helper.absolutePos(new BlockPos(15, 10, 15))));
        tag.putInt("geometry_radius", 1);
        tag.putString("geometry_depth", OrbitalDirectedEnergyDepth.DEPTH_32.name());
        tag.putInt("geometry_depth_blocks", 4);
        tag.putLong("configuration_revision", 0);
        tag.putInt("warning_ticks", 0);
        tag.putLong("work_cursor", 0);
        tag.putString("work_state", OrbitalAttackWorkState.WORKING.name());
        tag.putBoolean("payload_arrived", false);
        tag.putBoolean("impact_applied", false);
        tag.putInt("cooldown_ticks", 0);
        tag.putInt("cooldown_duration", 100);
        tag.putLong("celestial_escrow", 10);
        tag.putLong("ae_escrow", 20);
        tag.put("damage_exemptions", new ListTag());
        return tag;
    }

    private static CompoundTag root(CompoundTag attack) {
        CompoundTag tag = new CompoundTag();
        tag.putInt("schema_version", 1);
        ListTag attacks = new ListTag();
        attacks.add(attack);
        tag.put("attacks", attacks);
        return tag;
    }
}

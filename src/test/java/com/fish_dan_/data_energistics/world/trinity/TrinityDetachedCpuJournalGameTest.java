package com.fish_dan_.data_energistics.world.trinity;

import com.fish_dan_.data_energistics.Data_Energistics;
import com.fish_dan_.data_energistics.world.trinity.TrinityDataCoreStorageSavedData.RecoveryKey;
import com.fish_dan_.data_energistics.world.trinity.TrinityDataCoreStorageSavedData.RecoveryStatus;

import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import net.neoforged.testframework.annotation.TestHolder;
import net.neoforged.testframework.gametest.EmptyTemplate;

import java.util.UUID;

@GameTestHolder(Data_Energistics.MODID)
@PrefixGameTestTemplate(false)
public final class TrinityDetachedCpuJournalGameTest {

    private TrinityDetachedCpuJournalGameTest() {}

    @TestHolder("trinity_detached_cpu_journal_claims_once_and_retains_snapshot_after_restart")
    @EmptyTemplate("5")
    @GameTest(template = "empty_5x5")
    public static void claimsOnceAndRetainsSnapshotAfterRestart(GameTestHelper helper) {
        TrinityDataCoreStorageSavedData storage = new TrinityDataCoreStorageSavedData();
        RecoveryKey key = new RecoveryKey(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID());
        UUID owner = UUID.randomUUID();
        UUID claimant = UUID.randomUUID();
        CompoundTag snapshot = new CompoundTag();
        snapshot.putUUID("ledger_owner", owner);
        storage.storeDetachedRuntime(key, snapshot);
        storage.storeDetachedRuntime(key, snapshot.copy());
        snapshot.putUUID("ledger_owner", UUID.randomUUID());
        var result = storage.claimDetachedRuntime(key, claimant, actual -> {
            helper.assertValueEqual(storage.detachedRuntime(key).orElseThrow().status(), RecoveryStatus.CLAIMED,
                    "Claim journal is marked before the importer receives ownership");
            helper.assertValueEqual(actual.getUUID("ledger_owner"), owner, "Saved snapshot is isolated from later caller mutation");
            actual.remove("ledger_owner");
            return true;
        });
        helper.assertValueEqual(result.orElseThrow(), RecoveryStatus.RESTORED, "Verified claim records its completed receipt");
        CompoundTag saved = storage.save(new CompoundTag(), helper.getLevel().registryAccess());
        TrinityDataCoreStorageSavedData restored = TrinityDataCoreStorageSavedData.load(saved, helper.getLevel().registryAccess());
        var receipt = restored.detachedRuntime(key).orElseThrow();
        helper.assertValueEqual(receipt.runtime().getUUID("ledger_owner"), owner, "Successful claim retains the original forensic snapshot");
        helper.assertValueEqual(receipt.claimant(), claimant, "Claimant identity survives restart");
        helper.assertValueEqual(restored.claimDetachedRuntime(key, UUID.randomUUID(), ignored -> {
            helper.fail("Duplicate placement cannot invoke the importer a second time");
            return true;
        }).orElseThrow(), RecoveryStatus.RESTORED, "Journal recognizes the prior claim without granting again");
        RecoveryKey wrongPair = new RecoveryKey(key.hostId(), UUID.randomUUID(), key.removalToken());
        helper.assertTrue(restored.claimDetachedRuntime(wrongPair, claimant, ignored -> {
            helper.fail("A partial matching identity cannot claim CPU custody");
            return true;
        }).isEmpty(), "Full host and storage identity pair is required");
        helper.succeed();
    }

    @TestHolder("trinity_detached_cpu_journal_preserves_failed_and_unverified_imports")
    @EmptyTemplate("5")
    @GameTest(template = "empty_5x5")
    public static void preservesFailedAndUnverifiedImports(GameTestHelper helper) {
        TrinityDataCoreStorageSavedData storage = new TrinityDataCoreStorageSavedData();
        RecoveryKey failed = new RecoveryKey(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID());
        CompoundTag evidence = new CompoundTag();
        evidence.putString("reusable_sessions", "original uncertain custody bytes");
        storage.storeDetachedRuntime(failed, evidence);
        helper.assertValueEqual(storage.claimDetachedRuntime(failed, UUID.randomUUID(), ignored -> {
            throw new IllegalStateException("Intentional partial importer failure");
        }).orElseThrow(), RecoveryStatus.FAILED, "Importer failure remains explicit");
        RecoveryKey unverified = new RecoveryKey(failed.hostId(), failed.storageId(), UUID.randomUUID());
        storage.storeDetachedRuntime(unverified, evidence);
        helper.assertValueEqual(storage.claimDetachedRuntime(unverified, UUID.randomUUID(), ignored -> false).orElseThrow(),
                RecoveryStatus.UNVERIFIED, "A non-lossless restore is not accepted as complete");
        TrinityDataCoreStorageSavedData restored = TrinityDataCoreStorageSavedData.load(
                storage.save(new CompoundTag(), helper.getLevel().registryAccess()), helper.getLevel().registryAccess());
        for (RecoveryKey key : new RecoveryKey[] { failed, unverified }) {
            helper.assertValueEqual(restored.detachedRuntime(key).orElseThrow().runtime(), evidence, "Failure keeps exact recovery evidence");
            restored.claimDetachedRuntime(key, UUID.randomUUID(), ignored -> {
                helper.fail("Unknown prior side effects must not trigger automatic regrant");
                return true;
            });
        }
        helper.succeed();
    }

    @TestHolder("trinity_detached_cpu_journal_quarantines_changed_fingerprint")
    @EmptyTemplate("5")
    @GameTest(template = "empty_5x5")
    public static void quarantinesChangedFingerprint(GameTestHelper helper) {
        TrinityDataCoreStorageSavedData storage = new TrinityDataCoreStorageSavedData();
        RecoveryKey key = new RecoveryKey(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID());
        CompoundTag original = new CompoundTag();
        original.putLong("known_local_amount", 2);
        storage.storeDetachedRuntime(key, original);
        CompoundTag damaged = storage.save(new CompoundTag(), helper.getLevel().registryAccess());
        damaged.getList("detached_cpu_runtimes", Tag.TAG_COMPOUND).getCompound(0).getCompound("runtime").putLong("known_local_amount", 7);
        TrinityDataCoreStorageSavedData quarantined = TrinityDataCoreStorageSavedData.load(damaged, helper.getLevel().registryAccess());
        helper.assertValueEqual(quarantined.detachedRuntime(key).orElseThrow().status(), RecoveryStatus.UNVERIFIED,
                "Changed snapshot fingerprint cannot authorize asset recovery");
        helper.assertValueEqual(quarantined.detachedRuntime(key).orElseThrow().runtime().getLong("known_local_amount"), 7L,
                "Damaged snapshot is preserved as evidence rather than normalized into invented amounts");
        quarantined.claimDetachedRuntime(key, UUID.randomUUID(), ignored -> {
            helper.fail("Fingerprint mismatch cannot grant its payload");
            return true;
        });
        helper.succeed();
    }
}

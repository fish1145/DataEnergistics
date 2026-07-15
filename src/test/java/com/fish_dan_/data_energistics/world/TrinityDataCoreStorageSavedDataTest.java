package com.fish_dan_.data_energistics.world;

import com.fish_dan_.data_energistics.Data_Energistics;
import com.fish_dan_.data_energistics.ae2.DataKey;
import com.fish_dan_.data_energistics.common.trinity.TrinityDataCoreStorageProfile;
import com.fish_dan_.data_energistics.common.trinity.TrinityDataCoreStorageStatus;

import net.minecraft.core.HolderLookup;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.material.Fluids;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import net.neoforged.testframework.annotation.TestHolder;
import net.neoforged.testframework.gametest.EmptyTemplate;

import appeng.api.config.Actionable;
import appeng.api.stacks.AEFluidKey;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.KeyCounter;

import java.math.BigInteger;
import java.util.UUID;

@GameTestHolder(Data_Energistics.MODID)
@PrefixGameTestTemplate(false)
public final class TrinityDataCoreStorageSavedDataTest {

    private TrinityDataCoreStorageSavedDataTest() {}

    @TestHolder("trinity_data_core_storage_saved_data_saturates_network_counter")
    @EmptyTemplate("5")
    @GameTest(template = "empty_5x5")
    public static void storesBigIntegerAmountsAndSaturatesNetworkCounter(GameTestHelper helper) {
        TrinityDataCoreStorageSavedData data = new TrinityDataCoreStorageSavedData();
        UUID hostId = UUID.randomUUID();
        AEItemKey iron = AEItemKey.of(Items.IRON_INGOT);

        long simulated = data.insert(hostId, iron, Long.MAX_VALUE, Actionable.SIMULATE);
        helper.assertValueEqual(simulated, Long.MAX_VALUE, "Simulated insert should return the requested amount");
        helper.assertValueEqual(
                data.summary(hostId).totalAmount(),
                BigInteger.ZERO,
                "Simulated insert should not mutate storage");

        data.insert(hostId, iron, Long.MAX_VALUE, Actionable.MODULATE);
        data.insert(hostId, iron, 7L, Actionable.MODULATE);

        BigInteger expectedTotal = BigInteger.valueOf(Long.MAX_VALUE).add(BigInteger.valueOf(7L));
        helper.assertValueEqual(data.summary(hostId).typeCount(), 1, "Storage should keep one key type");
        helper.assertValueEqual(data.summary(hostId).totalAmount(), expectedTotal, "Storage should keep the BigInteger amount");

        KeyCounter counter = new KeyCounter();
        data.addAvailableStacks(hostId, counter);
        helper.assertValueEqual(counter.get(iron), Long.MAX_VALUE, "Network counter should saturate at Long.MAX_VALUE");
        helper.succeed();
    }

    @TestHolder("trinity_data_core_storage_saved_data_serializes_and_extracts")
    @EmptyTemplate("5")
    @GameTest(template = "empty_5x5")
    public static void serializesLoadsExtractsAndRemovesEmptyEntries(GameTestHelper helper) {
        TrinityDataCoreStorageSavedData data = new TrinityDataCoreStorageSavedData();
        UUID hostId = UUID.randomUUID();
        AEItemKey iron = AEItemKey.of(Items.IRON_INGOT);
        HolderLookup.Provider registries = helper.getLevel().registryAccess();

        data.insert(hostId, iron, Long.MAX_VALUE, Actionable.MODULATE);
        data.insert(hostId, iron, 7L, Actionable.MODULATE);

        CompoundTag tag = data.save(new CompoundTag(), registries);
        helper.assertValueEqual(tag.getInt("schema_version"), 1, "SavedData should write the current schema version");
        helper.assertTrue(
                tag.getList("hosts", Tag.TAG_COMPOUND).getCompound(0).hasUUID("host_id"),
                "SavedData should persist storage identities as UUID NBT");
        TrinityDataCoreStorageSavedData loaded = TrinityDataCoreStorageSavedData.load(tag, registries);
        helper.assertValueEqual(loaded.summary(hostId), data.summary(hostId), "Saved data should reload the same summary");

        helper.assertValueEqual(
                loaded.extract(hostId, iron, Long.MAX_VALUE, Actionable.MODULATE),
                Long.MAX_VALUE,
                "Extract should return the requested amount when enough BigInteger storage exists");
        helper.assertValueEqual(loaded.summary(hostId).totalAmount(), BigInteger.valueOf(7L), "Extract should leave the remainder");
        helper.assertValueEqual(
                loaded.extract(hostId, iron, 100L, Actionable.MODULATE),
                7L,
                "Extract should clamp to the remaining amount");
        helper.assertValueEqual(
                loaded.summary(hostId),
                TrinityDataCoreStorageSavedData.StorageSummary.EMPTY,
                "Storage should remove empty entries");
        helper.succeed();
    }

    @TestHolder("trinity_data_core_storage_saved_data_ignores_unsupported_schema")
    @EmptyTemplate("5")
    @GameTest(template = "empty_5x5")
    public static void ignoresSavedDataWithUnsupportedSchema(GameTestHelper helper) {
        HolderLookup.Provider registries = helper.getLevel().registryAccess();
        UUID storageId = UUID.randomUUID();
        AEItemKey iron = AEItemKey.of(Items.IRON_INGOT);
        TrinityDataCoreStorageSavedData current = new TrinityDataCoreStorageSavedData();
        current.insert(storageId, iron, 19L, Actionable.MODULATE);
        CompoundTag unsupported = current.save(new CompoundTag(), registries);
        unsupported.putInt("schema_version", 2);

        TrinityDataCoreStorageSavedData loaded = TrinityDataCoreStorageSavedData.load(unsupported, registries);
        helper.assertValueEqual(
                loaded.summary(storageId),
                TrinityDataCoreStorageSavedData.StorageSummary.EMPTY,
                "SavedData with an unsupported schema must not restore storage");
        helper.succeed();
    }

    @TestHolder("trinity_data_core_storage_saved_data_profile_limits_insert")
    @EmptyTemplate("5")
    @GameTest(template = "empty_5x5")
    public static void profileLimitsInsertedAmountAndTypes(GameTestHelper helper) {
        TrinityDataCoreStorageSavedData data = new TrinityDataCoreStorageSavedData();
        UUID hostId = UUID.randomUUID();
        AEItemKey iron = AEItemKey.of(Items.IRON_INGOT);
        AEItemKey gold = AEItemKey.of(Items.GOLD_INGOT);
        TrinityDataCoreStorageProfile profile = new TrinityDataCoreStorageProfile(
                BigInteger.TEN,
                1,
                1,
                2,
                false);

        helper.assertValueEqual(
                data.insert(hostId, iron, 7L, Actionable.MODULATE, profile),
                7L,
                "Finite profile should accept inserts below total capacity");
        helper.assertValueEqual(
                data.insert(hostId, gold, 1L, Actionable.SIMULATE, profile),
                0L,
                "Finite profile should reject new key types after the type capacity is full");
        helper.assertValueEqual(
                data.insert(hostId, iron, 10L, Actionable.SIMULATE, profile),
                3L,
                "Simulated insert should clamp to the remaining total capacity");
        helper.assertValueEqual(
                data.summary(hostId).totalAmount(),
                BigInteger.valueOf(7L),
                "Simulated insert should not mutate finite storage");
        helper.assertValueEqual(
                data.insert(hostId, iron, 10L, Actionable.MODULATE, profile),
                3L,
                "Modulated insert should clamp to the remaining total capacity");
        helper.assertValueEqual(data.summary(hostId).totalAmount(), BigInteger.TEN, "Finite profile should stop at total capacity");
        helper.succeed();
    }

    @TestHolder("trinity_data_core_storage_saved_data_summary_tracks_mutations")
    @EmptyTemplate("5")
    @GameTest(template = "empty_5x5")
    public static void summaryTracksModulatedMutationsAndIgnoresSimulations(GameTestHelper helper) {
        TrinityDataCoreStorageSavedData data = new TrinityDataCoreStorageSavedData();
        UUID hostId = UUID.randomUUID();
        AEItemKey iron = AEItemKey.of(Items.IRON_INGOT);
        AEItemKey gold = AEItemKey.of(Items.GOLD_INGOT);
        TrinityDataCoreStorageProfile profile = new TrinityDataCoreStorageProfile(
                BigInteger.TEN,
                2,
                2,
                3,
                false);

        data.insert(hostId, iron, 4L, Actionable.SIMULATE, profile);
        helper.assertValueEqual(
                data.summary(hostId),
                TrinityDataCoreStorageSavedData.StorageSummary.EMPTY,
                "Simulated insert should leave the summary empty");

        data.insert(hostId, iron, 4L, Actionable.MODULATE, profile);
        data.insert(hostId, gold, 3L, Actionable.MODULATE, profile);
        helper.assertValueEqual(
                data.summary(hostId),
                itemSummary(2, 7L),
                "Modulated inserts should update type count and total amount");

        data.extract(hostId, iron, 4L, Actionable.SIMULATE);
        helper.assertValueEqual(
                data.summary(hostId),
                itemSummary(2, 7L),
                "Simulated extraction should not update the summary");

        data.extract(hostId, iron, 4L, Actionable.MODULATE);
        helper.assertValueEqual(
                data.summary(hostId),
                itemSummary(1, 3L),
                "Removing a key should decrement the stored type count");
        helper.assertValueEqual(
                data.insert(hostId, iron, Long.MAX_VALUE, Actionable.MODULATE, profile),
                7L,
                "A removed key should free its type slot and the remaining total capacity");
        helper.assertValueEqual(
                data.summary(hostId),
                itemSummary(2, 10L),
                "Accepted insert should fill the total capacity exactly");
        helper.succeed();
    }

    @TestHolder("trinity_data_core_storage_saved_data_classifies_all_ae_keys")
    @EmptyTemplate("5")
    @GameTest(template = "empty_5x5")
    public static void classifiesItemsFluidsAndOtherKeysAcrossMutationsAndReload(GameTestHelper helper) {
        TrinityDataCoreStorageSavedData data = new TrinityDataCoreStorageSavedData();
        UUID hostId = UUID.randomUUID();
        AEItemKey iron = AEItemKey.of(Items.IRON_INGOT);
        AEFluidKey water = AEFluidKey.of(Fluids.WATER);
        DataKey otherKey = DataKey.of();
        HolderLookup.Provider registries = helper.getLevel().registryAccess();

        data.insert(hostId, iron, Long.MAX_VALUE, Actionable.SIMULATE);
        data.insert(hostId, water, 81_000L, Actionable.SIMULATE);
        data.insert(hostId, otherKey, 13L, Actionable.SIMULATE);
        helper.assertValueEqual(
                data.summary(hostId),
                TrinityDataCoreStorageSavedData.StorageSummary.EMPTY,
                "Simulated inserts must not update any category");

        data.insert(hostId, iron, Long.MAX_VALUE, Actionable.MODULATE);
        data.insert(hostId, iron, 7L, Actionable.MODULATE);
        data.insert(hostId, water, 81_000L, Actionable.MODULATE);
        data.insert(hostId, otherKey, 13L, Actionable.MODULATE);

        BigInteger itemAmount = BigInteger.valueOf(Long.MAX_VALUE).add(BigInteger.valueOf(7L));
        TrinityDataCoreStorageSavedData.StorageSummary expected = new TrinityDataCoreStorageSavedData.StorageSummary(
                3,
                itemAmount,
                BigInteger.valueOf(81_000L),
                BigInteger.valueOf(13L));
        helper.assertValueEqual(data.summary(hostId), expected, "Each AE key category should retain its exact amount");

        data.extract(hostId, water, 1_000L, Actionable.SIMULATE);
        helper.assertValueEqual(data.summary(hostId), expected, "Simulated extraction must not change category totals");
        data.extract(hostId, water, 1_000L, Actionable.MODULATE);
        TrinityDataCoreStorageSavedData.StorageSummary afterExtraction = new TrinityDataCoreStorageSavedData.StorageSummary(
                3,
                itemAmount,
                BigInteger.valueOf(80_000L),
                BigInteger.valueOf(13L));
        helper.assertValueEqual(
                data.summary(hostId),
                afterExtraction,
                "Modulated extraction should only reduce its key category");

        CompoundTag saved = data.save(new CompoundTag(), registries);
        CompoundTag savedHost = saved.getList("hosts", Tag.TAG_COMPOUND).getCompound(0);
        helper.assertFalse(
                savedHost.contains("item_amount") ||
                        savedHost.contains("fluid_amount") ||
                        savedHost.contains("other_key_amount"),
                "Category totals must remain derived and must not change the entries NBT schema");
        TrinityDataCoreStorageSavedData loaded = TrinityDataCoreStorageSavedData.load(saved, registries);
        helper.assertValueEqual(
                loaded.summary(hostId),
                afterExtraction,
                "Loading the unchanged entries schema should rebuild all category totals");

        TrinityDataCoreStorageProfile profile = new TrinityDataCoreStorageProfile(
                BigInteger.ONE.shiftLeft(200),
                64,
                1,
                2,
                false);
        TrinityDataCoreStorageStatus status = loaded.storageStatus(hostId, profile);
        helper.assertValueEqual(status.typeCount(), 3, "Storage status should expose the stored type count");
        helper.assertValueEqual(status.typeCapacity(), 64, "Storage status should expose the profile type capacity");
        helper.assertValueEqual(status.itemAmount(), itemAmount, "Storage status should expose the item amount");
        helper.assertValueEqual(
                status.fluidAmount(),
                BigInteger.valueOf(80_000L),
                "Storage status should expose the fluid amount");
        helper.assertValueEqual(
                status.otherKeyAmount(),
                BigInteger.valueOf(13L),
                "Storage status should expose other AE keys");
        helper.assertValueEqual(
                status.amountCapacity(),
                profile.totalCapacity(),
                "Storage status should expose exact capacity");
        helper.assertFalse(status.unlimited(), "Finite profiles must not be reported as unlimited");
        helper.succeed();
    }

    @TestHolder("trinity_data_core_storage_saved_data_duplicate_key_round_trip")
    @EmptyTemplate("5")
    @GameTest(template = "empty_5x5")
    public static void loadsDuplicateKeysLastWinsAndRoundTripsSummary(GameTestHelper helper) {
        UUID hostId = UUID.randomUUID();
        AEItemKey iron = AEItemKey.of(Items.IRON_INGOT);
        AEItemKey gold = AEItemKey.of(Items.GOLD_INGOT);
        HolderLookup.Provider registries = helper.getLevel().registryAccess();
        ListTag entries = new ListTag();
        entries.add(entryTag(iron, BigInteger.valueOf(4L), registries));
        entries.add(entryTag(gold, BigInteger.valueOf(3L), registries));
        entries.add(entryTag(iron, BigInteger.valueOf(9L), registries));

        CompoundTag host = new CompoundTag();
        host.putUUID("host_id", hostId);
        host.put("entries", entries);
        ListTag hosts = new ListTag();
        hosts.add(host);
        CompoundTag source = new CompoundTag();
        source.putInt("schema_version", 1);
        source.put("hosts", hosts);

        TrinityDataCoreStorageSavedData loaded = TrinityDataCoreStorageSavedData.load(source, registries);
        helper.assertValueEqual(loaded.amount(hostId, iron), BigInteger.valueOf(9L), "Last duplicate key should win");
        helper.assertValueEqual(
                loaded.summary(hostId),
                itemSummary(2, 12L),
                "Loaded summary should count each distinct key once and use its last amount");

        CompoundTag saved = loaded.save(new CompoundTag(), registries);
        TrinityDataCoreStorageSavedData reloaded = TrinityDataCoreStorageSavedData.load(saved, registries);
        helper.assertValueEqual(reloaded.amount(hostId, iron), BigInteger.valueOf(9L), "Round trip should retain iron");
        helper.assertValueEqual(reloaded.amount(hostId, gold), BigInteger.valueOf(3L), "Round trip should retain gold");
        helper.assertValueEqual(reloaded.summary(hostId), loaded.summary(hostId), "Round trip should retain summary");
        helper.succeed();
    }

    @TestHolder("trinity_data_core_storage_saved_data_keeps_storage_ids_isolated")
    @EmptyTemplate("5")
    @GameTest(template = "empty_5x5")
    public static void savesAndLoadsDistinctContentsForEachStorageId(GameTestHelper helper) {
        TrinityDataCoreStorageSavedData data = new TrinityDataCoreStorageSavedData();
        UUID firstStorageId = UUID.randomUUID();
        UUID secondStorageId = UUID.randomUUID();
        AEItemKey iron = AEItemKey.of(Items.IRON_INGOT);
        AEItemKey gold = AEItemKey.of(Items.GOLD_INGOT);
        HolderLookup.Provider registries = helper.getLevel().registryAccess();

        data.insert(firstStorageId, iron, 11L, Actionable.MODULATE);
        data.insert(secondStorageId, gold, 29L, Actionable.MODULATE);

        CompoundTag saved = data.save(new CompoundTag(), registries);
        TrinityDataCoreStorageSavedData loaded = TrinityDataCoreStorageSavedData.load(saved, registries);
        helper.assertValueEqual(
                loaded.amount(firstStorageId, iron),
                BigInteger.valueOf(11L),
                "First storage UUID should reload its own contents");
        helper.assertValueEqual(
                loaded.amount(firstStorageId, gold),
                BigInteger.ZERO,
                "First storage UUID must not see the second storage contents");
        helper.assertValueEqual(
                loaded.amount(secondStorageId, iron),
                BigInteger.ZERO,
                "Second storage UUID must not see the first storage contents");
        helper.assertValueEqual(
                loaded.amount(secondStorageId, gold),
                BigInteger.valueOf(29L),
                "Second storage UUID should reload its own contents");
        helper.assertValueEqual(
                loaded.summary(firstStorageId),
                itemSummary(1, 11L),
                "First storage summary should remain isolated after reload");
        helper.assertValueEqual(
                loaded.summary(secondStorageId),
                itemSummary(1, 29L),
                "Second storage summary should remain isolated after reload");
        helper.succeed();
    }

    @TestHolder("trinity_data_core_storage_saved_data_capacity_reduction_retains_extract")
    @EmptyTemplate("5")
    @GameTest(template = "empty_5x5")
    public static void capacityReductionRejectsInsertButRetainsExtraction(GameTestHelper helper) {
        TrinityDataCoreStorageSavedData data = new TrinityDataCoreStorageSavedData();
        UUID hostId = UUID.randomUUID();
        AEItemKey iron = AEItemKey.of(Items.IRON_INGOT);
        TrinityDataCoreStorageProfile highCapacity = new TrinityDataCoreStorageProfile(
                BigInteger.valueOf(20L),
                1,
                1,
                2,
                false);
        TrinityDataCoreStorageProfile reducedCapacity = new TrinityDataCoreStorageProfile(
                BigInteger.TEN,
                1,
                1,
                2,
                false);

        helper.assertValueEqual(
                data.insert(hostId, iron, 20L, Actionable.MODULATE, highCapacity),
                20L,
                "Higher capacity should accept the initial stored amount");
        helper.assertValueEqual(
                data.insert(hostId, iron, 1L, Actionable.SIMULATE, reducedCapacity),
                0L,
                "Reduced capacity must reject simulated inserts while existing contents exceed capacity");
        helper.assertValueEqual(
                data.insert(hostId, iron, 1L, Actionable.MODULATE, reducedCapacity),
                0L,
                "Reduced capacity must reject modulated inserts while existing contents exceed capacity");
        helper.assertValueEqual(
                data.extract(hostId, iron, 20L, Actionable.MODULATE),
                20L,
                "Capacity reduction must not prevent complete extraction of existing contents");
        helper.assertValueEqual(
                data.summary(hostId),
                TrinityDataCoreStorageSavedData.StorageSummary.EMPTY,
                "Complete extraction after capacity reduction should clear the storage entry");
        helper.succeed();
    }

    @TestHolder("trinity_data_core_storage_saved_data_unlimited_profile_accepts_all")
    @EmptyTemplate("5")
    @GameTest(template = "empty_5x5")
    public static void unlimitedProfileAcceptsAllTypesAndAmounts(GameTestHelper helper) {
        TrinityDataCoreStorageSavedData data = new TrinityDataCoreStorageSavedData();
        UUID hostId = UUID.randomUUID();
        AEItemKey iron = AEItemKey.of(Items.IRON_INGOT);
        AEItemKey gold = AEItemKey.of(Items.GOLD_INGOT);

        helper.assertValueEqual(
                data.insert(hostId, iron, Long.MAX_VALUE, Actionable.MODULATE, TrinityDataCoreStorageProfile.UNLIMITED),
                Long.MAX_VALUE,
                "Unlimited profile should accept the full requested amount");
        helper.assertValueEqual(
                data.insert(hostId, gold, 1L, Actionable.MODULATE, TrinityDataCoreStorageProfile.UNLIMITED),
                1L,
                "Unlimited profile should accept additional key types");
        helper.assertValueEqual(data.summary(hostId).typeCount(), 2, "Unlimited profile should store both key types");
        helper.succeed();
    }

    private static CompoundTag entryTag(AEItemKey key, BigInteger amount, HolderLookup.Provider registries) {
        CompoundTag entry = new CompoundTag();
        entry.put("key", key.toTagGeneric(registries));
        entry.putString("amount", amount.toString());
        return entry;
    }

    private static TrinityDataCoreStorageSavedData.StorageSummary itemSummary(int typeCount, long amount) {
        return new TrinityDataCoreStorageSavedData.StorageSummary(
                typeCount,
                BigInteger.valueOf(amount),
                BigInteger.ZERO,
                BigInteger.ZERO);
    }
}

package com.fish_dan_.data_energistics.world;

import com.fish_dan_.data_energistics.Data_Energistics;
import com.fish_dan_.data_energistics.common.trinity.TrinityDataCoreStorageProfile;

import net.minecraft.core.HolderLookup;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.item.Items;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import net.neoforged.testframework.annotation.TestHolder;
import net.neoforged.testframework.gametest.EmptyTemplate;

import appeng.api.config.Actionable;
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
        helper.assertValueEqual(data.summary(hostId).totalAmount(), "0", "Simulated insert should not mutate storage");

        data.insert(hostId, iron, Long.MAX_VALUE, Actionable.MODULATE);
        data.insert(hostId, iron, 7L, Actionable.MODULATE);

        String expectedTotal = BigInteger.valueOf(Long.MAX_VALUE).add(BigInteger.valueOf(7L)).toString();
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
        helper.assertValueEqual(loaded.summary(hostId).totalAmount(), "7", "Extract should leave the remainder");
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
                "7",
                "Simulated insert should not mutate finite storage");
        helper.assertValueEqual(
                data.insert(hostId, iron, 10L, Actionable.MODULATE, profile),
                3L,
                "Modulated insert should clamp to the remaining total capacity");
        helper.assertValueEqual(data.summary(hostId).totalAmount(), "10", "Finite profile should stop at total capacity");
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
}

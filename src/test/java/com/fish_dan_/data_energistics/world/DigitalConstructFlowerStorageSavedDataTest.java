package com.fish_dan_.data_energistics.world;

import com.fish_dan_.data_energistics.Data_Energistics;
import com.fish_dan_.data_energistics.common.trinity.DigitalConstructFlowerStorageProfile;

import net.minecraft.core.HolderLookup;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
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
public final class DigitalConstructFlowerStorageSavedDataTest {

    private DigitalConstructFlowerStorageSavedDataTest() {}

    @TestHolder("digital_construct_flower_storage_saved_data_saturates_network_counter")
    @EmptyTemplate("5")
    @GameTest(template = "empty_5x5")
    public static void storesBigIntegerAmountsAndSaturatesNetworkCounter(GameTestHelper helper) {
        DigitalConstructFlowerStorageSavedData data = new DigitalConstructFlowerStorageSavedData();
        UUID hostId = UUID.randomUUID();
        AEItemKey iron = AEItemKey.of(Items.IRON_INGOT);
        if (iron == null) {
            helper.fail("Iron item key should be available in the GameTest registry");
            return;
        }

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

    @TestHolder("digital_construct_flower_storage_saved_data_serializes_and_extracts")
    @EmptyTemplate("5")
    @GameTest(template = "empty_5x5")
    public static void serializesLoadsExtractsAndRemovesEmptyEntries(GameTestHelper helper) {
        DigitalConstructFlowerStorageSavedData data = new DigitalConstructFlowerStorageSavedData();
        UUID hostId = UUID.randomUUID();
        AEItemKey iron = AEItemKey.of(Items.IRON_INGOT);
        if (iron == null) {
            helper.fail("Iron item key should be available in the GameTest registry");
            return;
        }
        HolderLookup.Provider registries = helper.getLevel().registryAccess();

        data.insert(hostId, iron, Long.MAX_VALUE, Actionable.MODULATE);
        data.insert(hostId, iron, 7L, Actionable.MODULATE);

        CompoundTag tag = data.save(new CompoundTag(), registries);
        DigitalConstructFlowerStorageSavedData loaded = DigitalConstructFlowerStorageSavedData.load(tag, registries);
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
                DigitalConstructFlowerStorageSavedData.StorageSummary.EMPTY,
                "Storage should remove empty entries");
        helper.succeed();
    }

    @TestHolder("digital_construct_flower_storage_saved_data_profile_limits_insert")
    @EmptyTemplate("5")
    @GameTest(template = "empty_5x5")
    public static void profileLimitsInsertedAmountAndTypes(GameTestHelper helper) {
        DigitalConstructFlowerStorageSavedData data = new DigitalConstructFlowerStorageSavedData();
        UUID hostId = UUID.randomUUID();
        AEItemKey iron = AEItemKey.of(Items.IRON_INGOT);
        AEItemKey gold = AEItemKey.of(Items.GOLD_INGOT);
        if (iron == null || gold == null) {
            helper.fail("Item keys should be available in the GameTest registry");
            return;
        }
        DigitalConstructFlowerStorageProfile profile = new DigitalConstructFlowerStorageProfile(
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

    @TestHolder("digital_construct_flower_storage_saved_data_unlimited_profile_accepts_all")
    @EmptyTemplate("5")
    @GameTest(template = "empty_5x5")
    public static void unlimitedProfileAcceptsAllTypesAndAmounts(GameTestHelper helper) {
        DigitalConstructFlowerStorageSavedData data = new DigitalConstructFlowerStorageSavedData();
        UUID hostId = UUID.randomUUID();
        AEItemKey iron = AEItemKey.of(Items.IRON_INGOT);
        AEItemKey gold = AEItemKey.of(Items.GOLD_INGOT);
        if (iron == null || gold == null) {
            helper.fail("Item keys should be available in the GameTest registry");
            return;
        }

        helper.assertValueEqual(
                data.insert(hostId, iron, Long.MAX_VALUE, Actionable.MODULATE, DigitalConstructFlowerStorageProfile.UNLIMITED),
                Long.MAX_VALUE,
                "Unlimited profile should accept the full requested amount");
        helper.assertValueEqual(
                data.insert(hostId, gold, 1L, Actionable.MODULATE, DigitalConstructFlowerStorageProfile.UNLIMITED),
                1L,
                "Unlimited profile should accept additional key types");
        helper.assertValueEqual(data.summary(hostId).typeCount(), 2, "Unlimited profile should store both key types");
        helper.succeed();
    }
}

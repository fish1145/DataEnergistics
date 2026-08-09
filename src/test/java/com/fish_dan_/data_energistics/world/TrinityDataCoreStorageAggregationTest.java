package com.fish_dan_.data_energistics.world;

import com.fish_dan_.data_energistics.ae2.key.DataKey;
import com.fish_dan_.data_energistics.common.trinity.core.TrinityDataCoreStorageProfile;
import com.fish_dan_.data_energistics.common.trinity.host.TrinityDataCoreStorageStatus;

import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.material.Fluids;
import net.neoforged.fml.loading.LoadingModList;

import appeng.api.config.Actionable;
import appeng.api.stacks.AEFluidKey;
import appeng.api.stacks.AEItemKey;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.math.BigInteger;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

public final class TrinityDataCoreStorageAggregationTest {

    @BeforeAll
    static void bootstrapMinecraftRegistries() {
        if (LoadingModList.get() == null) {
            LoadingModList.of(List.of(), List.of(), List.of(), List.of(), Map.of());
        }
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    @Test
    void mutationsMaintainExclusiveExactCategoryTotals() {
        TrinityDataCoreStorageSavedData data = new TrinityDataCoreStorageSavedData();
        UUID storageId = UUID.randomUUID();
        AEItemKey itemKey = AEItemKey.of(Items.IRON_INGOT);
        AEFluidKey fluidKey = AEFluidKey.of(Fluids.WATER);
        DataKey otherKey = DataKey.of();

        data.insert(storageId, itemKey, Long.MAX_VALUE, Actionable.SIMULATE);
        data.insert(storageId, fluidKey, 81_000L, Actionable.SIMULATE);
        data.insert(storageId, otherKey, 13L, Actionable.SIMULATE);
        assertEquals(TrinityDataCoreStorageSavedData.StorageSummary.EMPTY, data.summary(storageId));

        data.insert(storageId, itemKey, Long.MAX_VALUE, Actionable.MODULATE);
        data.insert(storageId, itemKey, 7L, Actionable.MODULATE);
        data.insert(storageId, fluidKey, 81_000L, Actionable.MODULATE);
        data.insert(storageId, otherKey, 13L, Actionable.MODULATE);

        BigInteger itemAmount = BigInteger.valueOf(Long.MAX_VALUE).add(BigInteger.valueOf(7L));
        TrinityDataCoreStorageSavedData.StorageSummary expected = new TrinityDataCoreStorageSavedData.StorageSummary(
                3,
                itemAmount,
                BigInteger.valueOf(81_000L),
                BigInteger.valueOf(13L));
        assertEquals(expected, data.summary(storageId));

        assertEquals(81_000L, data.extract(storageId, fluidKey, Long.MAX_VALUE, Actionable.SIMULATE));
        assertEquals(expected, data.summary(storageId));
        assertEquals(81_000L, data.extract(storageId, fluidKey, Long.MAX_VALUE, Actionable.MODULATE));
        assertEquals(
                new TrinityDataCoreStorageSavedData.StorageSummary(
                        2,
                        itemAmount,
                        BigInteger.ZERO,
                        BigInteger.valueOf(13L)),
                data.summary(storageId));
    }

    @Test
    void storageStatusCombinesContentsWithTheCurrentProfile() {
        TrinityDataCoreStorageSavedData data = new TrinityDataCoreStorageSavedData();
        UUID storageId = UUID.randomUUID();
        data.insert(storageId, AEItemKey.of(Items.DIAMOND), 29L, Actionable.MODULATE);

        TrinityDataCoreStorageProfile finiteProfile = new TrinityDataCoreStorageProfile(
                BigInteger.ONE.shiftLeft(200),
                64,
                1,
                2,
                false);
        TrinityDataCoreStorageStatus finite = data.storageStatus(storageId, finiteProfile);
        assertEquals(1, finite.typeCount());
        assertEquals(64, finite.typeCapacity());
        assertEquals(BigInteger.valueOf(29L), finite.itemAmount());
        assertEquals(BigInteger.ZERO, finite.fluidAmount());
        assertEquals(BigInteger.ZERO, finite.otherKeyAmount());
        assertEquals(finiteProfile.totalCapacity(), finite.amountCapacity());
        assertFalse(finite.unlimited());

        TrinityDataCoreStorageStatus unlimited = data.storageStatus(
                storageId,
                TrinityDataCoreStorageProfile.UNLIMITED);
        assertEquals(BigInteger.valueOf(29L), unlimited.totalAmount());
        assertTrue(unlimited.unlimited());
    }
}

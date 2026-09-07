package com.fish_dan_.data_energistics.common.crafting.trinity.execution.cpu;

import com.fish_dan_.data_energistics.Data_Energistics;
import com.fish_dan_.data_energistics.api.crafting.dispatch.CountedCraftingTarget;
import com.fish_dan_.data_energistics.api.crafting.reusable.dispatch.ReusableCraftingRequest.Target;
import com.fish_dan_.data_energistics.blockentity.trinity.TrinityDataCoreBlockEntity;
import com.fish_dan_.data_energistics.common.crafting.trinity.capacity.TrinityCpuStorageCapacity;
import com.fish_dan_.data_energistics.common.crafting.trinity.planning.graph.TrinityPatternIdentity;
import com.fish_dan_.data_energistics.common.crafting.trinity.profile.TrinityDataCoreCpuPartitionProfile;
import com.fish_dan_.data_energistics.common.crafting.trinity.reusable.cpu.ReusableCpuSessionLedger;
import com.fish_dan_.data_energistics.common.crafting.trinity.reusable.cpu.ReusableCpuSessionLedgerNbtCodec;
import com.fish_dan_.data_energistics.common.crafting.trinity.serialization.TrinityBigIntegerEncoding;
import com.fish_dan_.data_energistics.registry.DEBlocks;
import com.fish_dan_.data_energistics.world.trinity.TrinityDataCoreStorageSavedData;
import com.fish_dan_.data_energistics.world.trinity.TrinityDataCoreStorageSavedData.RecoveryKey;
import com.fish_dan_.data_energistics.world.trinity.TrinityDataCoreStorageSavedData.RecoveryStatus;

import appeng.api.config.CpuSelectionMode;
import appeng.api.stacks.AEItemKey;

import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import net.neoforged.testframework.annotation.TestHolder;
import net.neoforged.testframework.gametest.EmptyTemplate;

import java.math.BigInteger;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@GameTestHolder(Data_Energistics.MODID)
@PrefixGameTestTemplate(false)
public final class TrinityDetachedCpuRecoveryGameTest {

    private TrinityDetachedCpuRecoveryGameTest() {}

    @TestHolder("trinity_host_removal_preserves_jobless_custody_without_copying_recovered_inventory")
    @EmptyTemplate("5")
    @GameTest(template = "empty_5x5")
    public static void removalPreservesJoblessCustodyWithoutCopyingRecoveredInventory(GameTestHelper helper) {
        TrinityDataCoreBlockEntity source = host(helper, BlockPos.ZERO);
        UUID owner = UUID.randomUUID();
        UUID session = UUID.randomUUID();
        ReusableCpuSessionLedger ledger = new ReusableCpuSessionLedger(owner);
        ledger.open(session, UUID.randomUUID(), new Target("executor:held-tool", CountedCraftingTarget.route("held-tool"), Optional.empty()),
                AEItemKey.of(Items.CRAFTING_TABLE), new TrinityPatternIdentity("definition", "publication"), List.of());
        CompoundTag runtime = runtime(source, ReusableCpuSessionLedgerNbtCodec.encode(ledger, helper.getLevel().registryAccess()), helper);
        source.getCraftingRuntime().readFromTag(runtime, helper.getLevel().registryAccess());
        helper.assertTrue(source.getCraftingRuntime().hasBusyJobs(), "Jobless external custody keeps its worker retained");
        ItemStack drop = new ItemStack(DEBlocks.TRINITY_DATA_CORE.get());
        source.saveIdentityToItem(drop);
        UUID token = drop.get(DataComponents.CUSTOM_DATA).copyTag().getUUID("trinity_cpu_removal_token");
        RecoveryKey key = new RecoveryKey(source.getHostId(), source.getStorageId(), token);
        TrinityDataCoreStorageSavedData storage = TrinityDataCoreStorageSavedData.get(helper.getLevel().getServer());
        helper.assertTrue(storage.detachedRuntime(key).isEmpty(), "Creating an item reference does not copy pre-recovery CPU inventory");
        source.onPermanentRemoval();
        source.onPermanentRemoval();
        helper.assertValueEqual(storage.amount(source.getStorageId(), AEItemKey.of(Items.DIAMOND)), BigInteger.valueOf(5),
                "Known local inventory enters durable storage once, even after repeated removal");
        var recovery = storage.detachedRuntime(key).orElseThrow();
        helper.assertValueEqual(recovery.status(), RecoveryStatus.AVAILABLE, "Post-recovery runtime is available for its item token");
        CompoundTag savedWorker = recovery.runtime().getList("partitions", Tag.TAG_COMPOUND).getCompound(0);
        helper.assertValueEqual(savedWorker.getInt("index"), 3, "Original worker number survives permanent removal");
        helper.assertTrue(savedWorker.getCompound("logic").getList("inventory", Tag.TAG_COMPOUND).isEmpty(),
                "Recovery snapshot excludes the inventory already written to storage");
        TrinityDataCoreBlockEntity destination = host(helper, new BlockPos(1, 0, 0));
        helper.assertTrue(destination.restoreIdentityFromItem(drop), "Complete identity pair restores the original host before claiming custody");
        CompoundTag restored = new CompoundTag();
        destination.getCraftingRuntime().writeToTag(restored, helper.getLevel().registryAccess());
        CompoundTag worker = restored.getList("partitions", Tag.TAG_COMPOUND).getCompound(0);
        ReusableCpuSessionLedger restoredLedger = ReusableCpuSessionLedgerNbtCodec.decode(worker.getCompound("logic").getCompound("reusable_sessions"),
                helper.getLevel().registryAccess());
        helper.assertValueEqual(restoredLedger.owner(), owner, "CPU owner UUID is not regenerated from the host identity");
        helper.assertTrue(restoredLedger.session(session) != null, "Original in-flight session identity is retained");
        helper.assertValueEqual(worker.getInt("index"), 3, "Restoration preserves the original worker number");
        helper.assertValueEqual(storage.detachedRuntime(key).orElseThrow().status(), RecoveryStatus.RESTORED, "Verified claim retains its journal receipt");
        TrinityDataCoreBlockEntity duplicate = host(helper, new BlockPos(2, 0, 0));
        duplicate.restoreIdentityFromItem(drop.copy());
        CompoundTag duplicateRuntime = new CompoundTag();
        duplicate.getCraftingRuntime().writeToTag(duplicateRuntime, helper.getLevel().registryAccess());
        helper.assertTrue(duplicateRuntime.getList("partitions", Tag.TAG_COMPOUND).isEmpty(), "Repeated item placement cannot grant the same CPU custody twice");
        helper.assertValueEqual(storage.amount(source.getStorageId(), AEItemKey.of(Items.DIAMOND)), BigInteger.valueOf(5),
                "Restoring CPU custody does not copy recovered local inventory back out of storage");
        helper.succeed();
    }

    @TestHolder("trinity_host_removal_keeps_unverified_inventory_with_quarantined_ledger")
    @EmptyTemplate("5")
    @GameTest(template = "empty_5x5")
    public static void removalKeepsUnverifiedInventoryWithQuarantinedLedger(GameTestHelper helper) {
        TrinityDataCoreBlockEntity source = host(helper, new BlockPos(3, 0, 0));
        CompoundTag damagedLedger = new CompoundTag();
        damagedLedger.putString("evidence", "missing owner must remain quarantined");
        source.getCraftingRuntime().readFromTag(runtime(source, damagedLedger, helper), helper.getLevel().registryAccess());
        ItemStack drop = new ItemStack(DEBlocks.TRINITY_DATA_CORE.get());
        source.saveIdentityToItem(drop);
        UUID token = drop.get(DataComponents.CUSTOM_DATA).copyTag().getUUID("trinity_cpu_removal_token");
        source.onPermanentRemoval();
        TrinityDataCoreStorageSavedData storage = TrinityDataCoreStorageSavedData.get(helper.getLevel().getServer());
        helper.assertValueEqual(storage.amount(source.getStorageId(), AEItemKey.of(Items.DIAMOND)), BigInteger.ZERO,
                "Unverified local assets are not dumped as newly spendable storage inventory");
        CompoundTag saved = storage.detachedRuntime(new RecoveryKey(source.getHostId(), source.getStorageId(), token)).orElseThrow().runtime();
        CompoundTag logic = saved.getList("partitions", Tag.TAG_COMPOUND).getCompound(0).getCompound("logic");
        helper.assertValueEqual(logic.getCompound("reusable_sessions"), damagedLedger, "Malformed custody evidence remains intact");
        helper.assertValueEqual(logic.getList("inventory", Tag.TAG_COMPOUND).getCompound(0).getLong("#"), 5L,
                "Remaining unverified local inventory stays inside the retained runtime snapshot");
        helper.succeed();
    }

    private static TrinityDataCoreBlockEntity host(GameTestHelper helper, BlockPos position) {
        TrinityDataCoreBlockEntity host = new TrinityDataCoreBlockEntity(position, DEBlocks.TRINITY_DATA_CORE.get().defaultBlockState());
        host.setLevel(helper.getLevel());
        return host;
    }

    private static CompoundTag runtime(TrinityDataCoreBlockEntity host, CompoundTag ledger, GameTestHelper helper) {
        TrinityDataCoreVirtualCpu cpu = new TrinityDataCoreVirtualCpu(host, host.getCraftingRuntime(),
                new TrinityDataCoreCpuPartitionProfile(3, 4, TrinityCpuStorageCapacity.finite(1024), 0, CpuSelectionMode.ANY));
        CompoundTag logic = cpu.logic().writeToTag(helper.getLevel().registryAccess());
        logic.put("reusable_sessions", ledger.copy());
        ListTag inventory = new ListTag();
        CompoundTag diamonds = AEItemKey.of(Items.DIAMOND).toTagGeneric(helper.getLevel().registryAccess());
        diamonds.putLong("#", 5);
        inventory.add(diamonds);
        logic.put("inventory", inventory);
        CompoundTag worker = new CompoundTag();
        worker.putInt("index", 3);
        worker.putInt("partition_count", 4);
        worker.putBoolean("storage_unlimited", false);
        worker.putByteArray("storage_capacity", TrinityBigIntegerEncoding.encode(BigInteger.valueOf(1024), "CPU storage capacity"));
        worker.putInt("co_processors", 0);
        worker.putString("selection_mode", CpuSelectionMode.ANY.name());
        worker.put("logic", logic);
        ListTag workers = new ListTag();
        workers.add(worker);
        CompoundTag runtime = new CompoundTag();
        host.getCraftingRuntime().writeToTag(runtime, helper.getLevel().registryAccess());
        runtime.put("partitions", workers);
        return runtime;
    }
}

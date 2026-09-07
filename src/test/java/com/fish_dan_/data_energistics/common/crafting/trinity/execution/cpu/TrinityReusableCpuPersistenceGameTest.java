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
import com.fish_dan_.data_energistics.registry.DEBlocks;

import appeng.api.config.CpuSelectionMode;
import appeng.api.stacks.AEItemKey;

import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.Items;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import net.neoforged.testframework.annotation.TestHolder;
import net.neoforged.testframework.gametest.EmptyTemplate;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@GameTestHolder(Data_Energistics.MODID)
@PrefixGameTestTemplate(false)
public final class TrinityReusableCpuPersistenceGameTest {

    private TrinityReusableCpuPersistenceGameTest() {}

    @TestHolder("trinity_reusable_cpu_custody_survives_without_job_and_prevents_worker_reuse")
    @EmptyTemplate("5")
    @GameTest(template = "empty_5x5")
    public static void custodySurvivesWithoutJobAndPreventsWorkerReuse(GameTestHelper helper) {
        TrinityDataCoreVirtualCpu cpu = cpu(helper);
        ReusableCpuSessionLedger ledger = new ReusableCpuSessionLedger(UUID.randomUUID());
        ledger.open(UUID.randomUUID(), UUID.randomUUID(),
                new Target("persistent-executor", CountedCraftingTarget.route("route"), Optional.empty()),
                AEItemKey.of(Items.CRAFTING_TABLE), new TrinityPatternIdentity("definition", "publication"), List.of());
        CompoundTag saved = cpu.logic().writeToTag(helper.getLevel().registryAccess());
        saved.put("reusable_sessions", ReusableCpuSessionLedgerNbtCodec.encode(ledger, helper.getLevel().registryAccess()));
        cpu.logic().readFromTag(saved, helper.getLevel().registryAccess());
        helper.assertTrue(!cpu.logic().hasJob() && cpu.isBusy(), "Jobless custody still occupies the CPU worker");
        helper.assertTrue(!cpu.canAcceptJob() && !cpu.isReleasable(), "Unsettled ownership cannot be reused by a different job");
        cpu.logic().discardPersistedState();
        CompoundTag restored = cpu.logic().writeToTag(helper.getLevel().registryAccess());
        helper.assertValueEqual(ReusableCpuSessionLedgerNbtCodec.decode(restored.getCompound("reusable_sessions"),
                helper.getLevel().registryAccess()).snapshot(), ledger.snapshot(),
                "Discarding unrelated failed job state cannot erase stable session ownership");
        helper.succeed();
    }

    @TestHolder("trinity_reusable_cpu_preserves_corrupt_custody_evidence")
    @EmptyTemplate("5")
    @GameTest(template = "empty_5x5")
    public static void preservesCorruptCustodyEvidence(GameTestHelper helper) {
        TrinityDataCoreVirtualCpu cpu = cpu(helper);
        CompoundTag saved = cpu.logic().writeToTag(helper.getLevel().registryAccess());
        CompoundTag damaged = saved.getCompound("reusable_sessions").copy();
        damaged.remove("owner");
        saved.put("reusable_sessions", damaged);
        cpu.logic().readFromTag(saved, helper.getLevel().registryAccess());
        helper.assertTrue(cpu.isBusy() && !cpu.isReleasable(), "Damaged custody is quarantined rather than treated as empty");
        helper.assertValueEqual(cpu.logic().writeToTag(helper.getLevel().registryAccess()).get("reusable_sessions"), damaged,
                "The original recovery evidence survives another save");
        helper.succeed();
    }

    private static TrinityDataCoreVirtualCpu cpu(GameTestHelper helper) {
        TrinityDataCoreBlockEntity host = new TrinityDataCoreBlockEntity(BlockPos.ZERO, DEBlocks.TRINITY_DATA_CORE.get().defaultBlockState());
        host.setLevel(helper.getLevel());
        return new TrinityDataCoreVirtualCpu(host, new TrinityDataCoreCraftingRuntime(host),
                new TrinityDataCoreCpuPartitionProfile(1, 1, TrinityCpuStorageCapacity.finite(1024), 0, CpuSelectionMode.ANY));
    }
}

package com.fish_dan_.data_energistics.common.crafting.trinity.execution.cpu;

import com.fish_dan_.data_energistics.Data_Energistics;
import com.fish_dan_.data_energistics.blockentity.trinity.TrinityDataCoreBlockEntity;
import com.fish_dan_.data_energistics.common.crafting.trinity.capacity.TrinityCpuStorageCapacity;
import com.fish_dan_.data_energistics.common.crafting.trinity.planning.CraftingQuantityMode;
import com.fish_dan_.data_energistics.common.crafting.trinity.planning.graph.TrinityPatternIdentity;
import com.fish_dan_.data_energistics.common.crafting.trinity.planning.plan.TrinityCraftingPlan;
import com.fish_dan_.data_energistics.common.crafting.trinity.planning.plan.TrinityPlanPatternFiring;
import com.fish_dan_.data_energistics.common.crafting.trinity.planning.plan.TrinityPlanStage;
import com.fish_dan_.data_energistics.common.crafting.trinity.planning.sameitem.TrinitySameItemPolicy;
import com.fish_dan_.data_energistics.common.crafting.trinity.profile.TrinityDataCoreCpuPartitionProfile;
import com.fish_dan_.data_energistics.registry.DEBlocks;

import appeng.api.config.CpuSelectionMode;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.GenericStack;
import appeng.crafting.CraftingLink;
import appeng.crafting.execution.CraftingCpuHelper;

import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import net.neoforged.testframework.annotation.TestHolder;
import net.neoforged.testframework.gametest.EmptyTemplate;

import java.math.BigInteger;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@GameTestHolder(Data_Energistics.MODID)
@PrefixGameTestTemplate(false)
public final class TrinityTargetPrincipalGameTest {

    private TrinityTargetPrincipalGameTest() {}

    @TestHolder("net_new_replanning_preserves_external_target_principal_and_counts_completion_once")
    @EmptyTemplate("5")
    @GameTest(template = "empty_5x5")
    public static void preservesExternalTargetPrincipalAndCountsCompletionOnce(GameTestHelper helper) {
        TrinityDataCoreVirtualCpu cpu = cpu(helper);
        AEItemKey target = AEItemKey.of(Items.DIAMOND);
        TrinityDataCoreExecutingCraftingJob job = job(cpu, CraftingQuantityMode.NET_NEW, BigInteger.ONE);
        helper.assertValueEqual(job.replanDemand(Map.of(target, BigInteger.valueOf(3L))).requested(), BigInteger.ONE,
                "Three owned targets include one original seed, leaving one net-new output still to produce");
        job.trinityExecution().borrowingLedger().reserve(target, 2L);
        job.trinityExecution().borrowingLedger().commit(target, 1L);
        helper.assertValueEqual(job.replanDemand(Map.of(target, BigInteger.valueOf(3L))).requested(), BigInteger.valueOf(3L),
                "Reserved and committed borrowed targets are external principal, not produced output");
        job.trinityExecution().borrowingLedger().release(target, 1L);
        job.recordAdditionalTargetPrincipal(BigInteger.valueOf(2L));
        helper.assertValueEqual(job.replanDemand(Map.of(target, BigInteger.valueOf(6L))).requested(), BigInteger.ONE,
                "Replacement acquisition increases principal while released borrowing is excluded");
        AEItemKey actual = namedTarget();
        job.trinityExecution().recordActualFinalOutput(actual, 1L);
        helper.assertTrue(job.replanDemand(Map.of(target, BigInteger.valueOf(6L))).noProduction(),
                "Isolated actual output counts toward existing production without entering working inventory");
        var work = job.trinityExecution().pollDispatchable(1L, Set.of(), ignored -> true, true).orElseThrow();
        job.trinityExecution().recordAccepted(work, 3L, 3L);
        job.trinityExecution().sealCompletion(2L);
        helper.assertValueEqual(job.replanDemand(Map.of(target, BigInteger.valueOf(3L))).requested(), BigInteger.ONE,
                "Sealed completion already includes the actual variant and must not count it twice");
        var restored = new TrinityDataCoreExecutingCraftingJob(job.writeToTag(helper.getLevel().registryAccess()),
                helper.getLevel().registryAccess(), ignored -> {}, cpu.logic());
        helper.assertValueEqual(restored.replanDemand(Map.of(target, BigInteger.valueOf(3L))),
                job.replanDemand(Map.of(target, BigInteger.valueOf(3L))), "Known principal and completion accounting survive job reload");
        helper.assertValueEqual(TrinityDataCoreExecutingCraftingJob.ownedTargetAmount(target,
                TrinitySameItemPolicy.ofRepresentatives(List.of(target)), Map.of(target, BigInteger.valueOf(2L), actual, BigInteger.valueOf(3L))),
                BigInteger.valueOf(5L), "Authorized target aliases are counted once within their logical domain");
        helper.assertValueEqual(TrinityDataCoreExecutingCraftingJob.ownedTargetAmount(target,
                TrinitySameItemPolicy.empty(), Map.of(target, BigInteger.valueOf(2L), actual, BigInteger.valueOf(3L))),
                BigInteger.valueOf(2L), "Unmarked target components remain exact");
        helper.succeed();
    }

    @TestHolder("final_total_replanning_uses_owned_targets_and_isolated_completion")
    @EmptyTemplate("5")
    @GameTest(template = "empty_5x5")
    public static void finalTotalUsesOwnedTargetsAndIsolatedCompletion(GameTestHelper helper) {
        TrinityDataCoreVirtualCpu cpu = cpu(helper);
        AEItemKey target = AEItemKey.of(Items.DIAMOND);
        TrinityDataCoreExecutingCraftingJob total = job(cpu, CraftingQuantityMode.FINAL_TOTAL, BigInteger.valueOf(10L));
        helper.assertValueEqual(total.replanDemand(Map.of(target, BigInteger.ONE)).requested(), BigInteger.valueOf(3L),
                "FINAL_TOTAL requests the final total, leaving existing owned target consumption to the solver");
        total.trinityExecution().recordActualFinalOutput(namedTarget(), 1L);
        helper.assertValueEqual(total.replanDemand(Map.of(target, BigInteger.ONE)).requested(), BigInteger.valueOf(2L),
                "Isolated completion is excluded from the new FINAL_TOTAL request");
        helper.assertTrue(total.replanDemand(Map.of(target, BigInteger.valueOf(2L))).noProduction(),
                "Owned stock plus isolated completion can finish without forcing one additional recipe");
        helper.succeed();
    }

    private static TrinityDataCoreExecutingCraftingJob job(TrinityDataCoreVirtualCpu cpu, CraftingQuantityMode mode, BigInteger principal) {
        AEItemKey input = AEItemKey.of(Items.PAPER);
        AEItemKey output = AEItemKey.of(Items.DIAMOND);
        BigInteger count = BigInteger.valueOf(3L);
        var identity = new TrinityPatternIdentity("principal", "recipe");
        var firing = new TrinityPlanPatternFiring(identity, output, 0, count, Map.of(input, BigInteger.ONE),
                Map.of(output, BigInteger.ONE), Map.of(), List.of());
        Map<AEKey, BigInteger> delta = Map.of(input, count.negate(), output, count);
        var stage = new TrinityPlanStage(0, false, Set.of(), List.of(firing), Map.of(input, count), delta);
        var plan = TrinityCraftingPlan.builder().finalOutput(new GenericStack(output, 3L)).bytes(BigInteger.ZERO)
                .catalogRevision(1L).quantityMode(mode).initialExpectedInputs(Map.of(input, count))
                .patternFirings(Map.of(identity, count)).stages(List.of(stage)).stageOrder(List.of(0)).targetNetChange(delta).build();
        CraftingLink link = new CraftingLink(CraftingCpuHelper.generateLinkData(UUID.randomUUID(), true, false), cpu);
        return new TrinityDataCoreExecutingCraftingJob(plan, ignored -> {}, link, null, principal);
    }

    private static AEItemKey namedTarget() {
        ItemStack stack = new ItemStack(Items.DIAMOND);
        stack.set(DataComponents.CUSTOM_NAME, Component.literal("already produced"));
        return AEItemKey.of(stack);
    }

    private static TrinityDataCoreVirtualCpu cpu(GameTestHelper helper) {
        TrinityDataCoreBlockEntity host = new TrinityDataCoreBlockEntity(BlockPos.ZERO, DEBlocks.TRINITY_DATA_CORE.get().defaultBlockState());
        host.setLevel(helper.getLevel());
        return new TrinityDataCoreVirtualCpu(host, new TrinityDataCoreCraftingRuntime(host),
                new TrinityDataCoreCpuPartitionProfile(1, 1, TrinityCpuStorageCapacity.finite(1024), 0, CpuSelectionMode.ANY));
    }
}

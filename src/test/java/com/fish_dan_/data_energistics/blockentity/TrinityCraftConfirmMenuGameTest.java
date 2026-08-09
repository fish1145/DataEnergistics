package com.fish_dan_.data_energistics.blockentity;

import com.fish_dan_.data_energistics.Data_Energistics;
import com.fish_dan_.data_energistics.common.crafting.trinity.execution.cpu.TrinityDataCoreVirtualCpu;
import com.fish_dan_.data_energistics.common.crafting.trinity.planning.CraftingQuantityMode;
import com.fish_dan_.data_energistics.common.crafting.trinity.planning.graph.TrinityPatternIdentity;
import com.fish_dan_.data_energistics.common.crafting.trinity.planning.plan.TrinityCraftingPlan;
import com.fish_dan_.data_energistics.common.crafting.trinity.planning.plan.TrinityPlanPatternFiring;
import com.fish_dan_.data_energistics.common.crafting.trinity.planning.plan.TrinityPlanStage;
import com.fish_dan_.data_energistics.menu.crafting.TrinityCraftConfirmMenuState;
import com.fish_dan_.data_energistics.part.UniversalTerminalPart;
import com.fish_dan_.data_energistics.registry.DEItems;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import net.neoforged.testframework.annotation.TestHolder;
import net.neoforged.testframework.gametest.EmptyTemplate;

import appeng.api.networking.GridHelper;
import appeng.api.networking.IGrid;
import appeng.api.networking.IGridNode;
import appeng.api.networking.crafting.CalculationStrategy;
import appeng.api.networking.crafting.ICraftingSubmitResult;
import appeng.api.parts.IPart;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.GenericStack;
import appeng.api.stacks.KeyCounter;
import appeng.blockentity.networking.CableBusBlockEntity;
import appeng.core.definitions.AEBlocks;
import appeng.crafting.CraftingPlan;
import appeng.menu.me.crafting.CraftConfirmMenu;

import java.math.BigInteger;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;

@GameTestHolder(Data_Energistics.MODID)
@PrefixGameTestTemplate(false)
public final class TrinityCraftConfirmMenuGameTest {

    private static final BlockPos TERMINAL_HOST_POS = new BlockPos(1, 1, 1);

    private TrinityCraftConfirmMenuGameTest() {}

    @TestHolder("trinity_craft_confirm_excludes_busy_worker_before_plan_finishes")
    @EmptyTemplate("50x32x50")
    @GameTest(template = "empty_50x32x50", timeoutTicks = 300)
    public static void keepsSubmissionAlignedWithPlanAndCpuEligibility(GameTestHelper helper) {
        TrinityDataCoreGameTestFixture fixture = TrinityDataCoreGameTestFixture.create(helper);
        UniversalTerminalPart terminal = placeTerminal(helper);

        helper.startSequence()
                .thenWaitUntil(() -> {
                    fixture.awaitOnline();
                    helper.assertTrue(terminal.getGridNode() != null,
                            "Universal terminal grid node is still initializing");
                })
                .thenExecute(() -> GridHelper.createConnection(
                        requireNode(terminal.getGridNode(), "Universal terminal"),
                        requireNode(fixture.accessHatches().getFirst().getMainNode().getNode(), "Trinity access hatch")))
                .thenWaitUntil(() -> helper.assertTrue(
                        terminal.getGridNode() != null && terminal.getGridNode().getGrid() == fixture.grid(),
                        "Universal terminal has not joined the Trinity AE grid"))
                .thenExecute(() -> assertMenuCpuSelection(helper, fixture, terminal))
                .thenSucceed();
    }

    private static void assertMenuCpuSelection(GameTestHelper helper,
                                               TrinityDataCoreGameTestFixture fixture,
                                               UniversalTerminalPart terminal) {
        IGrid grid = fixture.grid();
        TrinityDataCoreVirtualCpu coordinator = fixture.host().getCpuPartitions().getFirst();
        ICraftingSubmitResult submitResult = grid.getCraftingService().submitJob(
                emptyPlan(),
                null,
                coordinator,
                true,
                fixture.host().accessActionSource());
        helper.assertTrue(submitResult.successful(),
                "Trinity worker setup job should submit successfully: " + submitResult.errorCode());

        List<TrinityDataCoreVirtualCpu> busyWorkers = fixture.host().getCpuPartitions().stream()
                .filter(cpu -> cpu.number() != 0 && cpu.isBusy())
                .toList();
        helper.assertValueEqual(busyWorkers.size(), 1, "Test setup should publish exactly one busy Trinity worker");
        TrinityDataCoreVirtualCpu worker = busyWorkers.getFirst();
        helper.assertTrue(grid.getCraftingService().getCpus().contains(worker),
                "Crafting service should continue exposing the busy Trinity worker");

        Player player = helper.makeMockPlayer(GameType.CREATIVE);
        CraftConfirmMenu menu = new CraftConfirmMenu(1, player.getInventory(), terminal);
        helper.assertTrue(menu.getPlan() == null, "Craft confirmation plan should still be pending");
        menu.broadcastChanges();
        helper.assertFalse(menu.hasNoCPU(), "Idle Trinity coordinator should remain selectable");

        menu.cycleSelectedCPU(true);
        helper.assertValueEqual(menu.getName(), coordinator.getName(),
                "First explicit CPU selection should be the Trinity coordinator");
        menu.cycleSelectedCPU(true);
        helper.assertTrue(menu.getName() == null,
                "Cycling after the coordinator should return to automatic selection, not the busy worker");
        menu.cycleSelectedCPU(true);
        helper.assertValueEqual(menu.getName(), coordinator.getName(),
                "Only the Trinity coordinator should participate in explicit CPU selection");

        TrinityCraftConfirmMenuState state = (TrinityCraftConfirmMenuState) menu;
        menu.setJob(CompletableFuture.completedFuture(trinityPlan()));
        menu.broadcastChanges();
        helper.assertTrue(menu.getPlan() != null, "Completed plan should be visible after its first broadcast");
        helper.assertFalse(state.data_energistics$isPlanReady(),
                "Manual start must wait until CPU eligibility has observed the completed plan");
        menu.startJob();
        helper.assertValueEqual(busyWorkerCount(fixture), 1L,
                "An early start must not silently allocate a Trinity worker");

        menu.broadcastChanges();
        helper.assertTrue(state.data_energistics$isPlanReady(),
                "The next broadcast should publish plan and CPU eligibility as one ready state");
        menu.startJob();
        helper.assertValueEqual(busyWorkerCount(fixture), 2L,
                "A ready confirmation must allocate exactly one additional Trinity worker");

        helper.assertTrue(menu.planJob(
                AEItemKey.of(Items.DIAMOND),
                1,
                CalculationStrategy.CRAFT_LESS), "Replan should start on the connected grid");
        helper.assertTrue(menu.getPlan() == null, "Replan must clear the stale client-visible plan immediately");
        helper.assertFalse(state.data_energistics$isPlanReady(), "Replan must close the manual-start gate");
        menu.startJob();
        helper.assertValueEqual(busyWorkerCount(fixture), 2L,
                "Starting during replan must not reuse the stale result or allocate another worker");
        menu.removed(player);
        fixture.host().getCpuPartitions().stream()
                .filter(cpu -> cpu.number() != 0)
                .toList()
                .forEach(TrinityDataCoreVirtualCpu::cancelJob);
    }

    private static long busyWorkerCount(TrinityDataCoreGameTestFixture fixture) {
        return fixture.host().getCpuPartitions().stream()
                .filter(cpu -> cpu.number() != 0 && cpu.isBusy())
                .count();
    }

    private static TrinityCraftingPlan trinityPlan() {
        AEItemKey output = AEItemKey.of(Items.DIAMOND);
        TrinityPatternIdentity identity = new TrinityPatternIdentity("confirmation-test", "publication");
        TrinityPlanStage stage = new TrinityPlanStage(
                0,
                false,
                Set.of(),
                List.of(new TrinityPlanPatternFiring(identity, output, 0, BigInteger.ONE)),
                Map.of(),
                Map.of(output, BigInteger.ONE));
        return TrinityCraftingPlan.builder()
                .finalOutput(new GenericStack(output, 1L))
                .bytes(1L)
                .catalogRevision(1L)
                .quantityMode(CraftingQuantityMode.NET_NEW)
                .initialExpectedInputs(Map.of())
                .patternFirings(Map.of(identity, BigInteger.ONE))
                .stages(List.of(stage))
                .stageOrder(List.of(0))
                .cycleRepeatBlocks(List.of())
                .minimumSeed(Map.of())
                .targetNetChange(Map.of(output, BigInteger.ONE))
                .build();
    }

    private static CraftingPlan emptyPlan() {
        return new CraftingPlan(
                new GenericStack(AEItemKey.of(Items.DIAMOND), 1L),
                1L,
                false,
                false,
                new KeyCounter(),
                new KeyCounter(),
                new KeyCounter(),
                Map.of());
    }

    private static UniversalTerminalPart placeTerminal(GameTestHelper helper) {
        helper.setBlock(TERMINAL_HOST_POS, AEBlocks.CABLE_BUS.block().defaultBlockState());
        BlockEntity blockEntity = helper.getBlockEntity(TERMINAL_HOST_POS);
        if (!(blockEntity instanceof CableBusBlockEntity cableBus)) {
            throw new GameTestAssertException("Placed AE cable bus has no matching block entity");
        }
        IPart installedPart = cableBus.addPart(DEItems.UNIVERSAL_TERMINAL.get(), Direction.NORTH, null);
        if (installedPart instanceof UniversalTerminalPart terminal) {
            return terminal;
        }
        throw new GameTestAssertException("Failed to install a real universal terminal part");
    }

    private static IGridNode requireNode(IGridNode node, String owner) {
        if (node == null) {
            throw new GameTestAssertException(owner + " grid node is unavailable");
        }
        return node;
    }
}

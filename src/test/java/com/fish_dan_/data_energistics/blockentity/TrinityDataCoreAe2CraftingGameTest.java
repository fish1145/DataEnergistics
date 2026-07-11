package com.fish_dan_.data_energistics.blockentity;

import com.fish_dan_.data_energistics.Data_Energistics;
import com.fish_dan_.data_energistics.common.crafting.trinity.TrinityDataCoreVirtualCpu;
import com.fish_dan_.data_energistics.common.trinity.PatternRoute;
import com.fish_dan_.data_energistics.common.trinity.RoutedCraftingPatternDetails;
import com.fish_dan_.data_energistics.common.trinity.TrinityCraftingBatch;
import com.fish_dan_.data_energistics.common.trinity.TrinityPatternCatalog;
import com.fish_dan_.data_energistics.common.trinity.TrinityPatternCore;
import com.fish_dan_.data_energistics.world.TrinityDataCoreStorageSavedData;

import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import net.neoforged.testframework.annotation.TestHolder;
import net.neoforged.testframework.gametest.EmptyTemplate;

import appeng.api.config.Actionable;
import appeng.api.crafting.IPatternDetails;
import appeng.api.crafting.PatternDetailsHelper;
import appeng.api.networking.IGrid;
import appeng.api.networking.crafting.CalculationStrategy;
import appeng.api.networking.crafting.ICraftingPlan;
import appeng.api.networking.crafting.ICraftingService;
import appeng.api.networking.crafting.ICraftingSubmitResult;
import appeng.api.networking.security.IActionSource;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.AEKey;
import appeng.me.service.CraftingService;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

@GameTestHolder(Data_Energistics.MODID)
@PrefixGameTestTemplate(false)
public final class TrinityDataCoreAe2CraftingGameTest {

    private static final int TABLE_PATTERN_SLOT = 37;
    private static final int CAKE_PATTERN_SLOT = 38;

    private TrinityDataCoreAe2CraftingGameTest() {}

    @TestHolder("trinity_data_core_real_ae2_planning_routes_and_executes_crafting")
    @EmptyTemplate("50x32x50")
    @GameTest(template = "empty_50x32x50", timeoutTicks = 300)
    public static void realAe2PlanningRoutesAndExecutesCrafting(GameTestHelper helper) {
        TrinityDataCoreGameTestFixture fixture = TrinityDataCoreGameTestFixture.create(helper);
        TrinityDataCoreBlockEntity host = fixture.host();
        ServerLevel level = helper.getLevel();
        TrinityPatternCatalog.CoreMount mount = host.getPatternCatalog().mountedCores().getFirst();
        TrinityPatternCore core = mount.core();
        helper.assertTrue(core.patternCapacity() > CAKE_PATTERN_SLOT, "Selected P core should expose both test slots");

        ItemStack tablePattern = craftingTablePattern(level);
        ItemStack cakePattern = cakePattern(level);
        PatternRoute tableRoute = new PatternRoute(host.getHostId(), core.coreId(), TABLE_PATTERN_SLOT);
        PatternRoute cakeRoute = new PatternRoute(host.getHostId(), core.coreId(), CAKE_PATTERN_SLOT);
        TrinityDataCoreVirtualCpu cpu = host.getCpuPartitions().getFirst();
        PendingCraftingPlan tablePlan = new PendingCraftingPlan(level, AEItemKey.of(Items.CRAFTING_TABLE), 2L);
        PendingCraftingPlan cakePlan = new PendingCraftingPlan(level, AEItemKey.of(Items.CAKE), 1L);

        helper.startSequence()
                .thenWaitUntil(fixture::awaitOnline)
                .thenExecute(() -> {
                    helper.assertTrue(core.trySetPattern(TABLE_PATTERN_SLOT, tablePattern),
                            "Table pattern should install in its exact physical slot");
                    helper.assertTrue(core.trySetPattern(CAKE_PATTERN_SLOT, cakePattern),
                            "Cake pattern should install in its exact physical slot");
                    host.serverTick();
                    fixture.refreshAccessHatches();
                })
                .thenWaitUntil(() -> {
                    host.serverTick();
                    fixture.refreshAccessHatches();
                    assertPublishedRoute(helper, fixture.grid(), AEItemKey.of(Items.CRAFTING_TABLE), tableRoute);
                    assertPublishedRoute(helper, fixture.grid(), AEItemKey.of(Items.CAKE), cakeRoute);
                })
                .thenExecute(() -> {
                    insertIntoNetwork(helper, fixture, AEItemKey.of(Items.CRIMSON_PLANKS), 8L);
                    tablePlan.start(fixture.grid(), host.accessActionSource());
                })
                .thenWaitUntil(tablePlan::await)
                .thenExecute(() -> {
                    ICraftingPlan plan = tablePlan.plan();
                    assertPlan(helper, plan, tableRoute, AEItemKey.of(Items.CRAFTING_TABLE), 2L);
                    helper.assertValueEqual(
                            plan.usedItems().get(AEItemKey.of(Items.CRIMSON_PLANKS)),
                            8L,
                            "Real AE2 planning should select stored Crimson Planks as substitutes");
                    helper.assertValueEqual(
                            plan.usedItems().get(AEItemKey.of(Items.OAK_PLANKS)),
                            0L,
                            "Real AE2 planning should not require unavailable encoded Oak Planks");

                    submitAndDispatch(helper, fixture, cpu, plan);
                    long dispatchTick = level.getGameTime();
                    assertOnlyRouteQueued(helper, host, tableRoute, 2);
                    assertSubstitutedTableBatches(helper, core.queuedBatches(TABLE_PATTERN_SLOT), tableRoute, dispatchTick);
                    helper.assertValueEqual(
                            cpu.getWaitingFor(AEItemKey.of(Items.CRAFTING_TABLE)),
                            2L,
                            "Trinity CPU should wait for both routed table outputs");

                    host.serverTick();
                    assertOnlyRouteQueued(helper, host, tableRoute, 2);
                })
                .thenIdle(1)
                .thenExecute(() -> {
                    host.serverTick();
                    helper.assertValueEqual(core.queuedBatchCount(TABLE_PATTERN_SLOT), 0,
                            "Both same-slot batches should execute on the next tick");
                    helper.assertTrue(core.pendingOutputs(tableRoute).isEmpty(),
                            "All table outputs should leave the P core after CPU routing");
                    helper.assertFalse(cpu.isBusy(), "Trinity CPU should finish after both table outputs return");
                    assertHostStorage(helper, fixture, AEItemKey.of(Items.CRAFTING_TABLE), 2L);
                    assertHostStorage(helper, fixture, AEItemKey.of(Items.CRIMSON_PLANKS), 0L);

                    insertIntoNetwork(helper, fixture, AEItemKey.of(Items.MILK_BUCKET), 3L);
                    insertIntoNetwork(helper, fixture, AEItemKey.of(Items.SUGAR), 2L);
                    insertIntoNetwork(helper, fixture, AEItemKey.of(Items.EGG), 1L);
                    insertIntoNetwork(helper, fixture, AEItemKey.of(Items.WHEAT), 3L);
                    cakePlan.start(fixture.grid(), host.accessActionSource());
                })
                .thenWaitUntil(cakePlan::await)
                .thenExecute(() -> {
                    ICraftingPlan plan = cakePlan.plan();
                    assertPlan(helper, plan, cakeRoute, AEItemKey.of(Items.CAKE), 1L);
                    submitAndDispatch(helper, fixture, cpu, plan);
                    helper.assertValueEqual(core.queuedBatchCount(CAKE_PATTERN_SLOT), 1,
                            "Cake dispatch should enter its exact physical slot");
                    helper.assertValueEqual(
                            cpu.getWaitingFor(AEItemKey.of(Items.CAKE)),
                            1L,
                            "Trinity CPU should wait for the cake output");
                    helper.assertValueEqual(
                            cpu.getWaitingFor(AEItemKey.of(Items.BUCKET)),
                            3L,
                            "Trinity CPU should wait for all three container remainders");

                    host.serverTick();
                    helper.assertValueEqual(core.queuedBatchCount(CAKE_PATTERN_SLOT), 1,
                            "Cake batch should not execute during its enqueue tick");
                })
                .thenIdle(1)
                .thenExecute(() -> {
                    host.serverTick();
                    helper.assertValueEqual(core.queuedBatchCount(CAKE_PATTERN_SLOT), 0,
                            "Cake batch should execute on the next tick");
                    helper.assertTrue(core.pendingOutputs(cakeRoute).isEmpty(),
                            "Cake and buckets should leave the P core after CPU routing");
                    helper.assertFalse(cpu.isBusy(), "Trinity CPU should finish after cake and buckets return");
                    assertHostStorage(helper, fixture, AEItemKey.of(Items.CAKE), 1L);
                    assertHostStorage(helper, fixture, AEItemKey.of(Items.BUCKET), 3L);
                    assertHostStorage(helper, fixture, AEItemKey.of(Items.MILK_BUCKET), 0L);
                    assertHostStorage(helper, fixture, AEItemKey.of(Items.SUGAR), 0L);
                    assertHostStorage(helper, fixture, AEItemKey.of(Items.EGG), 0L);
                    assertHostStorage(helper, fixture, AEItemKey.of(Items.WHEAT), 0L);
                    assertHostStorage(helper, fixture, AEItemKey.of(Items.CRAFTING_TABLE), 2L);
                })
                .thenSucceed();
    }

    private static void assertPublishedRoute(GameTestHelper helper,
                                             IGrid grid,
                                             AEKey output,
                                             PatternRoute expectedRoute) {
        boolean published = grid.getCraftingService().getCraftingFor(output).stream()
                .filter(RoutedCraftingPatternDetails.class::isInstance)
                .map(RoutedCraftingPatternDetails.class::cast)
                .anyMatch(pattern -> pattern.route().equals(expectedRoute));
        helper.assertTrue(published, "AE2 crafting service should publish route " + expectedRoute);
    }

    private static void assertPlan(GameTestHelper helper,
                                   ICraftingPlan plan,
                                   PatternRoute expectedRoute,
                                   AEKey expectedOutput,
                                   long expectedTimes) {
        helper.assertFalse(plan.simulation(), "Real AE2 crafting plan should contain no missing ingredients");
        helper.assertTrue(plan.missingItems().isEmpty(), "Real AE2 crafting plan should report no missing keys");
        helper.assertValueEqual(plan.finalOutput().what(), expectedOutput, "Crafting plan should target the requested key");
        helper.assertValueEqual(
                plan.finalOutput().amount(),
                expectedTimes,
                "Crafting plan should preserve the requested output amount");
        helper.assertValueEqual(plan.patternTimes().size(), 1, "Crafting plan should use one exact routed pattern");

        Map.Entry<IPatternDetails, Long> entry = plan.patternTimes().entrySet().iterator().next();
        if (!(entry.getKey() instanceof RoutedCraftingPatternDetails routed)) {
            throw new GameTestAssertException("AE2 plan did not retain the Trinity routed pattern");
        }
        helper.assertValueEqual(routed.route(), expectedRoute, "AE2 plan should retain the exact P-core route");
        helper.assertValueEqual(entry.getValue(), expectedTimes, "AE2 plan should retain the expected pattern count");
    }

    private static void submitAndDispatch(GameTestHelper helper,
                                          TrinityDataCoreGameTestFixture fixture,
                                          TrinityDataCoreVirtualCpu cpu,
                                          ICraftingPlan plan) {
        IGrid grid = fixture.grid();
        ICraftingService craftingService = grid.getCraftingService();
        helper.assertTrue(craftingService.getCpus().contains(cpu), "AE2 should publish the selected Trinity CPU");
        helper.assertTrue(cpu.getCoProcessors() >= 1, "Test CPU should dispatch both table batches in one tick");
        ICraftingSubmitResult result = craftingService.submitJob(
                plan,
                null,
                cpu,
                true,
                fixture.host().accessActionSource());
        helper.assertTrue(result.successful(), "AE2 should submit the job to the explicit Trinity CPU: " +
                result.errorCode());
        helper.assertTrue(cpu.isBusy(), "Explicit Trinity CPU should own the submitted job");
        if (!(craftingService instanceof CraftingService concreteService)) {
            throw new IllegalStateException("Trinity CPU requires AE2 CraftingService for dispatch");
        }
        fixture.host().getCraftingRuntime().tick(grid.getEnergyService(), concreteService);
    }

    private static void assertOnlyRouteQueued(GameTestHelper helper,
                                              TrinityDataCoreBlockEntity host,
                                              PatternRoute expectedRoute,
                                              int expectedCount) {
        int aggregateCount = 0;
        for (TrinityPatternCatalog.CoreMount mount : host.getPatternCatalog().mountedCores()) {
            TrinityPatternCore mountedCore = mount.core();
            aggregateCount += mountedCore.queuedBatchCount();
            if (mountedCore.coreId().equals(expectedRoute.coreId())) {
                helper.assertValueEqual(
                        mountedCore.queuedBatchCount(expectedRoute.slot()),
                        expectedCount,
                        "Selected physical P-core slot should own every dispatched batch");
            }
        }
        helper.assertValueEqual(aggregateCount, expectedCount, "No other P-core slot should receive this route");
    }

    private static void assertSubstitutedTableBatches(GameTestHelper helper,
                                                      List<TrinityCraftingBatch> batches,
                                                      PatternRoute expectedRoute,
                                                      long dispatchTick) {
        helper.assertValueEqual(batches.size(), 2, "One CPU tick should enqueue both table batches");
        for (TrinityCraftingBatch batch : batches) {
            helper.assertValueEqual(batch.route(), expectedRoute, "Queued table batch should retain its exact route");
            helper.assertValueEqual(batch.queuedTick(), dispatchTick, "Both table batches should share one enqueue tick");
            long substitutedAmount = 0L;
            int nonEmptySlots = 0;
            for (ItemStack input : batch.inputs()) {
                if (input.isEmpty()) {
                    continue;
                }
                helper.assertTrue(input.is(Items.CRIMSON_PLANKS),
                        "Queued crafting grid should contain the actual substituted material");
                substitutedAmount += input.getCount();
                nonEmptySlots++;
            }
            helper.assertValueEqual(nonEmptySlots, 4, "Crafting table snapshot should populate four grid slots");
            helper.assertValueEqual(substitutedAmount, 4L, "Each table batch should consume four substituted planks");
        }
    }

    private static void insertIntoNetwork(GameTestHelper helper,
                                          TrinityDataCoreGameTestFixture fixture,
                                          AEKey key,
                                          long amount) {
        long inserted = fixture.grid().getStorageService().getInventory().insert(
                key,
                amount,
                Actionable.MODULATE,
                fixture.host().accessActionSource());
        helper.assertValueEqual(inserted, amount, "Trinity network storage should accept " + key);
    }

    private static void assertHostStorage(GameTestHelper helper,
                                          TrinityDataCoreGameTestFixture fixture,
                                          AEKey key,
                                          long expectedAmount) {
        BigInteger amount = TrinityDataCoreStorageSavedData.get(helper.getLevel().getServer())
                .amount(fixture.host().getStorageId(), key);
        helper.assertValueEqual(amount, BigInteger.valueOf(expectedAmount), "Trinity main storage amount for " + key);
    }

    private static ItemStack craftingTablePattern(ServerLevel level) {
        ArrayList<ItemStack> inputs = emptyCraftingGrid();
        inputs.set(0, new ItemStack(Items.OAK_PLANKS));
        inputs.set(1, new ItemStack(Items.OAK_PLANKS));
        inputs.set(3, new ItemStack(Items.OAK_PLANKS));
        inputs.set(4, new ItemStack(Items.OAK_PLANKS));
        return encodePattern(level, "crafting_table", inputs, new ItemStack(Items.CRAFTING_TABLE), true);
    }

    private static ItemStack cakePattern(ServerLevel level) {
        return encodePattern(
                level,
                "cake",
                List.of(
                        new ItemStack(Items.MILK_BUCKET),
                        new ItemStack(Items.MILK_BUCKET),
                        new ItemStack(Items.MILK_BUCKET),
                        new ItemStack(Items.SUGAR),
                        new ItemStack(Items.EGG),
                        new ItemStack(Items.SUGAR),
                        new ItemStack(Items.WHEAT),
                        new ItemStack(Items.WHEAT),
                        new ItemStack(Items.WHEAT)),
                new ItemStack(Items.CAKE),
                false);
    }

    private static ItemStack encodePattern(ServerLevel level,
                                           String recipePath,
                                           List<ItemStack> inputs,
                                           ItemStack output,
                                           boolean allowSubstitutes) {
        RecipeHolder<?> recipe = level.getRecipeManager()
                .byKey(ResourceLocation.withDefaultNamespace(recipePath))
                .orElseThrow(() -> new IllegalStateException("Missing crafting recipe: " + recipePath));
        if (!(recipe.value() instanceof CraftingRecipe craftingRecipe)) {
            throw new IllegalStateException("Recipe is not a crafting recipe: " + recipe.id());
        }
        RecipeHolder<CraftingRecipe> craftingRecipeHolder = new RecipeHolder<>(recipe.id(), craftingRecipe);
        return PatternDetailsHelper.encodeCraftingPattern(
                craftingRecipeHolder,
                inputs.toArray(ItemStack[]::new),
                output,
                allowSubstitutes,
                false);
    }

    private static ArrayList<ItemStack> emptyCraftingGrid() {
        ArrayList<ItemStack> inputs = new ArrayList<>(9);
        for (int slot = 0; slot < 9; slot++) {
            inputs.add(ItemStack.EMPTY);
        }
        return inputs;
    }

    private static final class PendingCraftingPlan {

        private final ServerLevel level;
        private final AEKey output;
        private final long amount;
        private Future<ICraftingPlan> future;
        private ICraftingPlan plan;

        private PendingCraftingPlan(ServerLevel level, AEKey output, long amount) {
            this.level = level;
            this.output = output;
            this.amount = amount;
        }

        private void start(IGrid grid, IActionSource actionSource) {
            if (this.future != null) {
                throw new IllegalStateException("Crafting plan was started more than once for " + this.output);
            }
            this.future = grid.getCraftingService().beginCraftingCalculation(
                    this.level,
                    () -> actionSource,
                    this.output,
                    this.amount,
                    CalculationStrategy.REPORT_MISSING_ITEMS);
        }

        private void await() {
            if (this.future == null) {
                throw new IllegalStateException("Crafting plan was not started for " + this.output);
            }
            try {
                this.plan = this.future.get(0L, TimeUnit.MILLISECONDS);
            } catch (TimeoutException exception) {
                throw new GameTestAssertException("AE2 crafting plan is still calculating " + this.output);
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException("Interrupted while calculating AE2 crafting plan for " + this.output,
                        exception);
            } catch (ExecutionException exception) {
                throw new IllegalStateException("AE2 crafting plan failed for " + this.output, exception.getCause());
            }
        }

        private ICraftingPlan plan() {
            if (this.plan == null) {
                throw new IllegalStateException("Crafting plan has not completed for " + this.output);
            }
            return this.plan;
        }
    }
}

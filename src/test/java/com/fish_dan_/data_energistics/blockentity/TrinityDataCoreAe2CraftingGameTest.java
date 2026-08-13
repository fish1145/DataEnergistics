package com.fish_dan_.data_energistics.blockentity;

import com.fish_dan_.data_energistics.Data_Energistics;
import com.fish_dan_.data_energistics.api.crafting.dispatch.CountedCraftingAdmission;
import com.fish_dan_.data_energistics.common.crafting.trinity.dispatch.budget.CraftingDispatchLimits;
import com.fish_dan_.data_energistics.common.crafting.trinity.dispatch.commit.CraftingDispatchWindow;
import com.fish_dan_.data_energistics.common.crafting.trinity.dispatch.provider.CountedCraftingProvider;
import com.fish_dan_.data_energistics.common.crafting.trinity.execution.cpu.TrinityDataCoreVirtualCpu;
import com.fish_dan_.data_energistics.common.crafting.trinity.planning.TrinityCraftingGraphAccess;
import com.fish_dan_.data_energistics.common.crafting.trinity.planning.graph.TrinityCraftingGraphSnapshot;
import com.fish_dan_.data_energistics.common.crafting.trinity.planning.plan.TrinityCraftingPlan;
import com.fish_dan_.data_energistics.common.trinity.pattern.PatternRoute;
import com.fish_dan_.data_energistics.common.trinity.pattern.RoutedCraftingPatternDetails;
import com.fish_dan_.data_energistics.common.trinity.pattern.TrinityPatternCore;
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
import appeng.api.networking.crafting.ICraftingProvider;
import appeng.api.networking.crafting.ICraftingService;
import appeng.api.networking.crafting.ICraftingSubmitResult;
import appeng.api.networking.security.IActionSource;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.KeyCounter;
import appeng.me.service.CraftingService;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicReference;

@GameTestHolder(Data_Energistics.MODID)
@PrefixGameTestTemplate(false)
public final class TrinityDataCoreAe2CraftingGameTest {

    private static final int REMOVAL_PATTERN_SLOT = 39;
    private static final int ADMISSION_INVALIDATION_PATTERN_SLOT = 42;
    private static final int GRAPH_SNAPSHOT_PATTERN_SLOT = 43;
    private static final int SELF_MULTIPLICATION_PATTERN_SLOT = 44;

    private TrinityDataCoreAe2CraftingGameTest() {}

    @TestHolder("trinity_grid_publishes_revision_consistent_crafting_graph")
    @EmptyTemplate("50x32x50")
    @GameTest(template = "empty_50x32x50", timeoutTicks = 300)
    public static void publishesRevisionConsistentGridCraftingGraph(GameTestHelper helper) {
        TrinityDataCoreGameTestFixture fixture = TrinityDataCoreGameTestFixture.create(helper);
        TrinityDataCoreBlockEntity host = fixture.host();
        TrinityPatternCore core = host.getPatternCatalog().mountedCores().getFirst().core();
        ServerLevel level = helper.getLevel();
        AEKey target = AEItemKey.of(Items.CRAFTING_TABLE);
        helper.assertTrue(
                core.patternCapacity() > GRAPH_SNAPSHOT_PATTERN_SLOT,
                "Selected P core should expose the graph snapshot test slot");

        helper.startSequence()
                .thenWaitUntil(fixture::awaitOnline)
                .thenExecute(() -> {
                    helper.assertTrue(
                            core.trySetPattern(GRAPH_SNAPSHOT_PATTERN_SLOT, craftingTablePattern(level)),
                            "Graph snapshot test pattern should install in its exact physical slot");
                    host.serverTick();
                    fixture.refreshPatternPublication();
                })
                .thenWaitUntil(() -> {
                    host.serverTick();
                    fixture.refreshPatternPublication();
                    IGrid grid = fixture.grid();
                    if (!(grid.getCraftingService() instanceof TrinityCraftingGraphAccess graphAccess)) {
                        throw new GameTestAssertException("AE2 crafting service does not expose the Trinity graph");
                    }
                    TrinityCraftingGraphSnapshot snapshot = graphAccess.data_energistics$trinityCraftingGraphSnapshot().orElse(null);
                    IPatternDetails decoded = core.decodedPattern(GRAPH_SNAPSHOT_PATTERN_SLOT);
                    if (snapshot == null || decoded == null || snapshot.patternsProducing(target).stream()
                            .noneMatch(pattern -> pattern.definition().equals(decoded.getDefinition()))) {
                        throw new GameTestAssertException(
                                "Trinity graph has not published the installed pattern yet");
                    }
                })
                .thenExecute(() -> {
                    TrinityCraftingGraphAccess graphAccess = (TrinityCraftingGraphAccess) fixture.grid().getCraftingService();
                    TrinityCraftingGraphSnapshot snapshot = graphAccess.data_energistics$trinityCraftingGraphSnapshot().orElseThrow();
                    helper.assertTrue(snapshot.revision() >= 0L,
                            "Published Trinity graph must carry a non-negative provider revision");
                    helper.assertTrue(!snapshot.patternsProducing(target).isEmpty(),
                            "Published Trinity graph must index the installed target pattern");
                })
                .thenSucceed();
    }

    @TestHolder("trinity_data_core_access_withdrawal_is_synchronous_with_storage")
    @EmptyTemplate("50x32x50")
    @GameTest(template = "empty_50x32x50", timeoutTicks = 300)
    public static void accessWithdrawalIsSynchronousWithStorage(GameTestHelper helper) {
        TrinityDataCoreGameTestFixture fixture = TrinityDataCoreGameTestFixture.create(helper);

        helper.startSequence()
                .thenWaitUntil(fixture::awaitOnline)
                .thenExecute(() -> {
                    TrinityDataCoreBlockEntity host = fixture.host();
                    TrinityAccessHatchBlockEntity initialHatch = fixture.accessHatches().stream()
                            .filter(host::isLeaseOwner)
                            .findFirst()
                            .orElseThrow(() -> new GameTestAssertException("Trinity fixture has no lease-owning hatch"));
                    IGrid grid = fixture.grid();

                    AEKey storedKey = AEItemKey.of(Items.AMETHYST_SHARD);
                    insertIntoNetwork(helper, fixture, storedKey, 3L);
                    TrinityDataCoreVirtualCpu reservedCpu = reservedCpu(host);
                    initialHatch.getMainNode().destroy();
                    helper.assertFalse(grid.getCraftingService().getCpus().contains(reservedCpu),
                            "Removing the lease node must immediately hide the Trinity CPU");

                    host.requestAccessLeaseReevaluation();
                    KeyCounter available = new KeyCounter();
                    grid.getStorageService().getInventory().getAvailableStacks(available);
                    helper.assertValueEqual(available.get(storedKey), 0L,
                            "Removing the sole access hatch must unmount Trinity storage in the same tick");
                    helper.assertTrue(host.accessGrid() == null,
                            "Removing the sole access hatch must leave the Trinity host offline");
                    helper.assertValueEqual(
                            fixture.accessHatches().stream().filter(host::isLeaseOwner).count(),
                            0L,
                            "Removing the sole access hatch must clear the network lease");
                })
                .thenSucceed();
    }

    @TestHolder("trinity_data_core_stale_access_hatch_admission_cannot_commit")
    @EmptyTemplate("50x32x50")
    @GameTest(template = "empty_50x32x50", timeoutTicks = 300)
    public static void staleAccessHatchAdmissionCannotCommit(GameTestHelper helper) {
        TrinityDataCoreGameTestFixture fixture = TrinityDataCoreGameTestFixture.create(helper);
        TrinityDataCoreBlockEntity host = fixture.host();
        TrinityPatternCore core = host.getPatternCatalog().mountedCores().getFirst().core();
        ServerLevel level = helper.getLevel();
        AEItemKey crimsonPlanks = AEItemKey.of(Items.CRIMSON_PLANKS);
        KeyCounter[] inputPrototype = { new KeyCounter() };
        inputPrototype[0].add(crimsonPlanks, 4L);
        helper.assertTrue(
                core.patternCapacity() > ADMISSION_INVALIDATION_PATTERN_SLOT,
                "Selected P core should expose the admission invalidation test slot");

        PatternRoute route = new PatternRoute(host.getHostId(), core.coreId(), ADMISSION_INVALIDATION_PATTERN_SLOT);

        helper.startSequence()
                .thenWaitUntil(fixture::awaitOnline)
                .thenExecute(() -> {
                    helper.assertTrue(
                            core.trySetPattern(ADMISSION_INVALIDATION_PATTERN_SLOT, craftingTablePattern(level)),
                            "Admission invalidation test pattern should install in its exact physical slot");
                    host.serverTick();
                    fixture.refreshPatternPublication();
                })
                .thenWaitUntil(() -> {
                    host.serverTick();
                    fixture.refreshPatternPublication();
                    assertPublishedRoute(helper, fixture.grid(), AEItemKey.of(Items.CRAFTING_TABLE), route);
                })
                .thenExecute(() -> {
                    TrinityAccessHatchBlockEntity hatch = fixture.accessHatches().stream()
                            .filter(host::isLeaseOwner)
                            .findFirst()
                            .orElseThrow(() -> new GameTestAssertException(
                                    "Trinity fixture has no lease-owning hatch for admission"));
                    var node = hatch.getMainNode().getNode();
                    if (node == null) {
                        throw new GameTestAssertException("Lease-owning Trinity hatch has no AE2 grid node");
                    }
                    ICraftingProvider registeredProvider = node.getService(ICraftingProvider.class);
                    if (!(registeredProvider instanceof CountedCraftingProvider provider)) {
                        throw new GameTestAssertException("Lease-owning Trinity hatch has no counted crafting provider");
                    }
                    RoutedCraftingPatternDetails pattern = requireSinglePublishedRoute(
                            helper,
                            fixture.grid(),
                            AEItemKey.of(Items.CRAFTING_TABLE),
                            route);
                    CountedCraftingAdmission admission = provider.prepareBatch(pattern, inputPrototype, 1L);
                    if (admission == null) {
                        throw new GameTestAssertException("Live Trinity hatch should admit its published routed pattern");
                    }
                    helper.assertValueEqual(admission.count(), 1L,
                            "Trinity hatch should admit exactly one valid routed craft");

                    hatch.getMainNode().destroy();
                    host.requestAccessLeaseReevaluation();
                    helper.assertTrue(host.accessGrid() == null,
                            "Destroying the admission hatch must withdraw the Trinity access lease");
                    helper.assertFalse(admission.commit(inputPrototype),
                            "Admission must reject after its access hatch leaves the selected AE2 grid");
                    helper.assertValueEqual(inputPrototype[0].get(crimsonPlanks), 4L,
                            "Rejected stale admission must retain every prepared input");
                    helper.assertValueEqual(core.queuedBatchCount(ADMISSION_INVALIDATION_PATTERN_SLOT), 0,
                            "Rejected stale admission must not enqueue a P-core batch");
                    helper.assertFalse(host.getPatternCatalog().hasWork(),
                            "Rejected stale admission must not leave work in the Trinity pattern catalog");
                })
                .thenSucceed();
    }

    @TestHolder("trinity_data_core_executes_single_pattern_self_multiplication")
    @EmptyTemplate("50x32x50")
    @GameTest(template = "empty_50x32x50", timeoutTicks = 400)
    public static void executesSinglePatternSelfMultiplication(GameTestHelper helper) {
        TrinityDataCoreGameTestFixture fixture = TrinityDataCoreGameTestFixture.create(helper);
        TrinityDataCoreBlockEntity host = fixture.host();
        ServerLevel level = helper.getLevel();
        TrinityPatternCore core = host.getPatternCatalog().mountedCores().getFirst().core();
        AEItemKey target = AEItemKey.of(Items.AMETHYST_SHARD);
        long requested = 31L;
        helper.assertTrue(
                core.patternCapacity() > SELF_MULTIPLICATION_PATTERN_SLOT,
                "Selected P core should expose the self-multiplication test slot");

        PatternRoute route = new PatternRoute(host.getHostId(), core.coreId(), SELF_MULTIPLICATION_PATTERN_SLOT);
        PendingCraftingPlan pendingPlan = new PendingCraftingPlan(level, target, requested);
        AtomicReference<TrinityDataCoreVirtualCpu> activeWorker = new AtomicReference<>();

        helper.startSequence()
                .thenWaitUntil(fixture::awaitOnline)
                .thenExecute(() -> {
                    helper.assertTrue(
                            core.trySetPattern(SELF_MULTIPLICATION_PATTERN_SLOT, selfMultiplicationPattern(level)),
                            "Self-multiplication pattern should install in its exact physical slot");
                    host.serverTick();
                    fixture.refreshPatternPublication();
                })
                .thenWaitUntil(() -> {
                    host.serverTick();
                    fixture.refreshPatternPublication();
                    assertPublishedRoute(helper, fixture.grid(), target, route);
                    assertGraphPatternPublished(
                            helper,
                            fixture.grid(),
                            core,
                            SELF_MULTIPLICATION_PATTERN_SLOT,
                            target);
                })
                .thenExecute(() -> {
                    insertIntoNetwork(helper, fixture, target, 1L);
                    pendingPlan.start(fixture.grid(), host.accessActionSource());
                })
                .thenWaitUntil(pendingPlan::await)
                .thenExecute(() -> {
                    if (!(pendingPlan.plan() instanceof TrinityCraftingPlan plan)) {
                        throw new GameTestAssertException(
                                "A productive self-cycle with one seed should produce a Trinity plan");
                    }
                    helper.assertValueEqual(
                            plan.initialExpectedInputs().get(target),
                            BigInteger.ONE,
                            "Self-multiplication should reserve exactly one initial seed");
                    helper.assertValueEqual(
                            plan.minimumSeed().get(target),
                            BigInteger.ONE,
                            "Self-multiplication should retain the one-item prefix seed");
                    helper.assertValueEqual(
                            plan.cycleRepeatBlocks().size(),
                            1,
                            "One self-cycle pattern should form one compact repeat block");
                    helper.assertValueEqual(
                            plan.cycleRepeatBlocks().getFirst().repetitions(),
                            BigInteger.valueOf(requested),
                            "NET_NEW self-multiplication should repeat once per requested net item");
                    activeWorker.set(submitJob(helper, fixture, plan));
                })
                .thenWaitUntil(() -> {
                    host.serverTick();
                    tickCraftingRuntime(fixture);
                    if (activeWorker.get().isBusy()) {
                        throw new GameTestAssertException("Trinity self-multiplication is still executing");
                    }
                })
                .thenExecute(() -> {
                    helper.assertValueEqual(
                            core.queuedBatchCount(SELF_MULTIPLICATION_PATTERN_SLOT),
                            0,
                            "Completed self-multiplication should leave no queued P-core batch");
                    assertHostStorage(helper, fixture, target, Math.addExact(requested, 1L));
                })
                .thenSucceed();
    }

    @TestHolder("trinity_data_core_permanent_removal_cancels_job_before_withdrawing_network")
    @EmptyTemplate("50x32x50")
    @GameTest(template = "empty_50x32x50", timeoutTicks = 300)
    public static void permanentRemovalCancelsJobBeforeWithdrawingNetwork(GameTestHelper helper) {
        TrinityDataCoreGameTestFixture fixture = TrinityDataCoreGameTestFixture.create(helper);
        TrinityDataCoreBlockEntity host = fixture.host();
        TrinityPatternCore core = host.getPatternCatalog().mountedCores().getFirst().core();
        PatternRoute route = new PatternRoute(host.getHostId(), core.coreId(), REMOVAL_PATTERN_SLOT);
        PendingCraftingPlan plan = new PendingCraftingPlan(
                helper.getLevel(),
                AEItemKey.of(Items.CRAFTING_TABLE),
                1L);
        ItemStack pattern = craftingTablePattern(helper.getLevel());
        AtomicReference<IGrid> removalGrid = new AtomicReference<>();
        AtomicReference<List<TrinityDataCoreVirtualCpu>> removalCpus = new AtomicReference<>();

        helper.startSequence()
                .thenWaitUntil(fixture::awaitOnline)
                .thenExecute(() -> {
                    helper.assertTrue(core.trySetPattern(REMOVAL_PATTERN_SLOT, pattern),
                            "Removal test pattern should install in its exact physical slot");
                    host.serverTick();
                    fixture.refreshPatternPublication();
                })
                .thenWaitUntil(() -> {
                    host.serverTick();
                    fixture.refreshPatternPublication();
                    assertPublishedRoute(
                            helper,
                            fixture.grid(),
                            AEItemKey.of(Items.CRAFTING_TABLE),
                            route);
                    assertGraphPatternPublished(
                            helper,
                            fixture.grid(),
                            core,
                            REMOVAL_PATTERN_SLOT,
                            AEItemKey.of(Items.CRAFTING_TABLE));
                })
                .thenExecute(() -> {
                    insertIntoNetwork(helper, fixture, AEItemKey.of(Items.CRIMSON_PLANKS), 4L);
                    plan.start(fixture.grid(), host.accessActionSource());
                })
                .thenWaitUntil(plan::await)
                .thenExecute(() -> {
                    IGrid grid = fixture.grid();
                    TrinityDataCoreVirtualCpu reserveCpu = reservedCpu(host);
                    ICraftingSubmitResult result = grid.getCraftingService().submitJob(
                            plan.plan(),
                            null,
                            reserveCpu,
                            true,
                            host.accessActionSource());
                    helper.assertTrue(result.successful(),
                            "Removal test should submit a real AE2 job: " + result.errorCode());
                    TrinityDataCoreVirtualCpu worker = busyWorker(host);
                    List<TrinityDataCoreVirtualCpu> publishedCpus = List.copyOf(host.getCpuPartitions());
                    helper.assertTrue(publishedCpus.contains(reserveCpu) && publishedCpus.contains(worker),
                            "Removal test should capture both reserved CPU 0 and its allocated worker");
                    removalCpus.set(publishedCpus);
                    helper.assertTrue(worker.isBusy(), "Submitted removal test worker should own an active job");
                    assertHostStorage(helper, fixture, AEItemKey.of(Items.CRIMSON_PLANKS), 0L);
                    removalGrid.set(grid);

                    helper.getLevel().destroyBlock(host.getBlockPos(), false);

                    helper.assertFalse(worker.isBusy(), "Permanent host removal should cancel its active worker job");
                    helper.assertFalse(host.isStorageAvailable(),
                            "Permanent host removal should withdraw its storage capability");
                    helper.assertTrue(host.accessGrid() == null,
                            "Permanent host removal should clear its network lease");
                    assertHostStorage(helper, fixture, AEItemKey.of(Items.CRIMSON_PLANKS), 4L);
                })
                .thenWaitUntil(() -> {
                    IGrid grid = removalGrid.get();
                    helper.assertTrue(removalCpus.get().stream()
                            .noneMatch(grid.getCraftingService().getCpus()::contains),
                            "Removed host reserved CPU and worker must not remain published on the old grid");
                    helper.assertTrue(grid.getCraftingService()
                            .getCraftingFor(AEItemKey.of(Items.CRAFTING_TABLE))
                            .stream()
                            .noneMatch(details -> details instanceof RoutedCraftingPatternDetails routed &&
                                    routed.route().equals(route)),
                            "Removed host pattern route must not remain published on the old grid");
                    KeyCounter oldGridContents = new KeyCounter();
                    grid.getStorageService().getInventory().getAvailableStacks(oldGridContents);
                    helper.assertValueEqual(
                            oldGridContents.get(AEItemKey.of(Items.CRIMSON_PLANKS)),
                            0L,
                            "Removed host storage must no longer be mounted on the old grid");
                    helper.assertTrue(fixture.accessHatches().stream()
                            .allMatch(hatch -> hatch.boundCraftingRuntime() == null &&
                                    hatch.accessGrid() == null &&
                                    hatch.terminalPartitions().isEmpty()),
                            "Every former access hatch should withdraw CPU, storage and terminal capabilities");
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

    private static void assertGraphPatternPublished(GameTestHelper helper,
                                                    IGrid grid,
                                                    TrinityPatternCore core,
                                                    int patternSlot,
                                                    AEKey output) {
        if (!(grid.getCraftingService() instanceof TrinityCraftingGraphAccess graphAccess)) {
            throw new GameTestAssertException("AE2 crafting service does not expose the Trinity graph");
        }
        TrinityCraftingGraphSnapshot snapshot = graphAccess.data_energistics$trinityCraftingGraphSnapshot().orElse(null);
        IPatternDetails decoded = core.decodedPattern(patternSlot);
        boolean published = snapshot != null &&
                decoded != null &&
                snapshot.patternsProducing(output).stream()
                        .anyMatch(pattern -> pattern.definition().equals(decoded.getDefinition()));
        helper.assertTrue(published, "Trinity graph should publish pattern slot " + patternSlot);
    }

    private static RoutedCraftingPatternDetails requireSinglePublishedRoute(GameTestHelper helper,
                                                                            IGrid grid,
                                                                            AEKey output,
                                                                            PatternRoute expectedRoute) {
        List<RoutedCraftingPatternDetails> published = grid.getCraftingService().getCraftingFor(output).stream()
                .filter(RoutedCraftingPatternDetails.class::isInstance)
                .map(RoutedCraftingPatternDetails.class::cast)
                .filter(pattern -> pattern.route().equals(expectedRoute))
                .toList();
        helper.assertValueEqual(
                published.size(),
                1,
                "AE2 crafting service must publish the exact Trinity route once: " + expectedRoute);
        return published.getFirst();
    }

    private static TrinityDataCoreVirtualCpu submitJob(GameTestHelper helper,
                                                       TrinityDataCoreGameTestFixture fixture,
                                                       ICraftingPlan plan) {
        IGrid grid = fixture.grid();
        ICraftingService craftingService = grid.getCraftingService();
        TrinityDataCoreVirtualCpu reserveCpu = reservedCpu(fixture.host());
        helper.assertTrue(craftingService.getCpus().contains(reserveCpu),
                "AE2 should publish reserved Trinity CPU 0");
        ICraftingSubmitResult result = craftingService.submitJob(
                plan,
                null,
                null,
                true,
                fixture.host().accessActionSource());
        helper.assertTrue(result.successful(), "AE2 should auto-submit the machine job to a Trinity CPU: " +
                result.errorCode());
        helper.assertFalse(reserveCpu.isBusy(), "Reserved Trinity CPU 0 must remain idle after job allocation");
        helper.assertTrue(reserveCpu.getJobStatus() == null,
                "Reserved Trinity CPU 0 must not retain the allocated worker job");
        TrinityDataCoreVirtualCpu worker = busyWorker(fixture.host());
        helper.assertTrue(craftingService.getCpus().contains(worker),
                "AE2 should publish the allocated busy Trinity worker");
        helper.assertTrue(worker.isBusy(), "Allocated Trinity worker should own the submitted job");
        return worker;
    }

    private static void tickCraftingRuntime(TrinityDataCoreGameTestFixture fixture) {
        IGrid grid = fixture.grid();
        if (!(grid.getCraftingService() instanceof CraftingService craftingService)) {
            throw new IllegalStateException("Trinity CPU requires AE2 CraftingService for dispatch");
        }
        fixture.host().getCraftingRuntime().tick(
                grid.getEnergyService(),
                craftingService,
                CraftingDispatchWindow.create(CraftingDispatchLimits.DEFAULT, () -> 0L));
    }

    private static TrinityDataCoreVirtualCpu reservedCpu(TrinityDataCoreBlockEntity host) {
        List<TrinityDataCoreVirtualCpu> published = host.getCpuPartitions();
        if (published.isEmpty() || published.getFirst().number() != 0) {
            throw new GameTestAssertException("Online Trinity runtime did not publish reserved CPU 0 first");
        }
        return published.getFirst();
    }

    private static TrinityDataCoreVirtualCpu busyWorker(TrinityDataCoreBlockEntity host) {
        List<TrinityDataCoreVirtualCpu> busyWorkers = host.getCpuPartitions().stream()
                .filter(cpu -> cpu.number() != 0 && cpu.isBusy())
                .toList();
        if (busyWorkers.size() != 1) {
            throw new GameTestAssertException(
                    "Expected exactly one busy Trinity worker, found " + busyWorkers.size());
        }
        return busyWorkers.getFirst();
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

    private static ItemStack selfMultiplicationPattern(ServerLevel level) {
        ArrayList<ItemStack> inputs = emptyCraftingGrid();
        inputs.set(0, new ItemStack(Items.AMETHYST_SHARD));
        return encodePattern(
                level,
                ResourceLocation.fromNamespaceAndPath(Data_Energistics.MODID, "trinity_self_multiplication"),
                inputs,
                new ItemStack(Items.AMETHYST_SHARD, 2),
                false);
    }

    private static ItemStack encodePattern(ServerLevel level,
                                           String recipePath,
                                           List<ItemStack> inputs,
                                           ItemStack output,
                                           boolean allowSubstitutes) {
        return encodePattern(
                level,
                ResourceLocation.withDefaultNamespace(recipePath),
                inputs,
                output,
                allowSubstitutes);
    }

    private static ItemStack encodePattern(ServerLevel level,
                                           ResourceLocation recipeId,
                                           List<ItemStack> inputs,
                                           ItemStack output,
                                           boolean allowSubstitutes) {
        RecipeHolder<?> recipe = level.getRecipeManager()
                .byKey(recipeId)
                .orElseThrow(() -> new IllegalStateException("Missing crafting recipe: " + recipeId));
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

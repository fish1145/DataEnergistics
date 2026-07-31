package com.fish_dan_.data_energistics.blockentity;

import com.fish_dan_.data_energistics.Data_Energistics;
import com.fish_dan_.data_energistics.common.crafting.trinity.CountedCraftingAdmission;
import com.fish_dan_.data_energistics.common.crafting.trinity.CountedCraftingProvider;
import com.fish_dan_.data_energistics.common.crafting.trinity.CraftingDispatchWindow;
import com.fish_dan_.data_energistics.common.crafting.trinity.TrinityDataCoreVirtualCpu;
import com.fish_dan_.data_energistics.common.crafting.trinity.planning.TrinityCraftingGraphAccess;
import com.fish_dan_.data_energistics.common.crafting.trinity.planning.graph.TrinityCraftingGraphSnapshot;
import com.fish_dan_.data_energistics.common.trinity.PatternRoute;
import com.fish_dan_.data_energistics.common.trinity.RoutedCraftingPatternDetails;
import com.fish_dan_.data_energistics.common.trinity.TrinityAutoBuildBlockMap;
import com.fish_dan_.data_energistics.common.trinity.TrinityAutoBuildOptions;
import com.fish_dan_.data_energistics.common.trinity.TrinityAutoBuildRequest;
import com.fish_dan_.data_energistics.common.trinity.TrinityCraftingBatch;
import com.fish_dan_.data_energistics.common.trinity.TrinityPatternCatalog;
import com.fish_dan_.data_energistics.common.trinity.TrinityPatternCore;
import com.fish_dan_.data_energistics.registry.ModBlocks;
import com.fish_dan_.data_energistics.world.TrinityDataCoreStorageSavedData;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.state.BlockState;
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
import java.util.Map;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicReference;

@GameTestHolder(Data_Energistics.MODID)
@PrefixGameTestTemplate(false)
public final class TrinityDataCoreAe2CraftingGameTest {

    private static final long COUNTED_BATCH_SIZE = 128L;
    private static final long LARGE_COUNTED_BATCH_SIZE = 10_000L;
    private static final int TABLE_PATTERN_SLOT = 37;
    private static final int CAKE_PATTERN_SLOT = 38;
    private static final int REMOVAL_PATTERN_SLOT = 39;
    private static final int STRUCTURE_PAUSE_PATTERN_SLOT = 40;
    private static final int SAME_TICK_STORAGE_PATTERN_SLOT = 41;
    private static final int ADMISSION_INVALIDATION_PATTERN_SLOT = 42;
    private static final int GRAPH_SNAPSHOT_PATTERN_SLOT = 43;

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
                    TrinityCraftingGraphSnapshot snapshot = graphAccess.trinityCraftingGraphSnapshot().orElse(null);
                    IPatternDetails decoded = core.decodedPattern(GRAPH_SNAPSHOT_PATTERN_SLOT);
                    if (snapshot == null || decoded == null || snapshot.patternsProducing(target).stream()
                            .noneMatch(pattern -> pattern.definition().equals(decoded.getDefinition()))) {
                        throw new GameTestAssertException(
                                "Trinity graph has not published the installed pattern yet");
                    }
                })
                .thenExecute(() -> {
                    TrinityCraftingGraphAccess graphAccess = (TrinityCraftingGraphAccess) fixture.grid().getCraftingService();
                    TrinityCraftingGraphSnapshot snapshot = graphAccess.trinityCraftingGraphSnapshot().orElseThrow();
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

    @TestHolder("trinity_data_core_same_tick_storage_io_preserves_single_crafting_publication")
    @EmptyTemplate("50x32x50")
    @GameTest(template = "empty_50x32x50", timeoutTicks = 300)
    public static void sameTickStorageIoPreservesSingleCraftingPublication(GameTestHelper helper) {
        TrinityDataCoreGameTestFixture fixture = TrinityDataCoreGameTestFixture.create(helper);
        TrinityDataCoreBlockEntity host = fixture.host();
        ServerLevel level = helper.getLevel();
        TrinityPatternCore core = host.getPatternCatalog().mountedCores().getFirst().core();
        helper.assertTrue(
                core.patternCapacity() > SAME_TICK_STORAGE_PATTERN_SLOT,
                "Selected P core should expose the same-tick test slot");

        PatternRoute route = new PatternRoute(host.getHostId(), core.coreId(), SAME_TICK_STORAGE_PATTERN_SLOT);
        PendingCraftingPlan plan = new PendingCraftingPlan(level, AEItemKey.of(Items.CRAFTING_TABLE), 1L);

        helper.startSequence()
                .thenWaitUntil(fixture::awaitOnline)
                .thenExecute(() -> {
                    helper.assertTrue(
                            core.trySetPattern(SAME_TICK_STORAGE_PATTERN_SLOT, craftingTablePattern(level)),
                            "Same-tick test pattern should install in its exact physical slot");
                    host.serverTick();
                    fixture.refreshPatternPublication();
                })
                .thenWaitUntil(() -> {
                    host.serverTick();
                    fixture.refreshPatternPublication();
                    assertPublishedRoute(helper, fixture.grid(), AEItemKey.of(Items.CRAFTING_TABLE), route);
                })
                .thenExecute(() -> {
                    insertIntoNetwork(helper, fixture, AEItemKey.of(Items.CRIMSON_PLANKS), 4L);
                    plan.start(fixture.grid(), host.accessActionSource());
                })
                .thenWaitUntil(plan::await)
                .thenExecute(() -> {
                    ICraftingPlan executablePlan = plan.plan();
                    assertPlan(helper, executablePlan, route, AEItemKey.of(Items.CRAFTING_TABLE), 1L);

                    IGrid grid = fixture.grid();
                    ICraftingService craftingService = grid.getCraftingService();
                    if (!(craftingService instanceof CraftingService concreteService)) {
                        throw new IllegalStateException("Same-tick Trinity test requires AE2 CraftingService");
                    }
                    TrinityDataCoreVirtualCpu reservedCpu = reservedCpu(host);
                    helper.assertValueEqual(
                            fixture.accessHatches().stream().filter(host::isLeaseOwner).count(),
                            1L,
                            "Same-grid Trinity access must retain exactly one lease owner");
                    assertCpuPublishedOnce(helper, craftingService, reservedCpu);
                    RoutedCraftingPatternDetails publishedBeforeStorage = requireSinglePublishedRoute(
                            helper,
                            grid,
                            AEItemKey.of(Items.CRAFTING_TABLE),
                            route);
                    helper.assertValueEqual(
                            providerCount(concreteService, publishedBeforeStorage),
                            1,
                            "The routed pattern must have exactly one Trinity provider before storage I/O");

                    long sameTick = level.getGameTime();
                    AEItemKey storageProbe = AEItemKey.of(Items.AMETHYST_SHARD);
                    var networkStorage = grid.getStorageService().getInventory();
                    helper.assertValueEqual(
                            networkStorage.insert(
                                    storageProbe,
                                    3L,
                                    Actionable.MODULATE,
                                    host.accessActionSource()),
                            3L,
                            "Same-tick storage probe must enter Trinity storage through the real AE grid");
                    KeyCounter availableAfterInsert = new KeyCounter();
                    networkStorage.getAvailableStacks(availableAfterInsert);
                    helper.assertValueEqual(
                            availableAfterInsert.get(storageProbe),
                            3L,
                            "Two same-grid hatches must expose the inserted storage probe exactly once");
                    helper.assertValueEqual(
                            networkStorage.extract(
                                    storageProbe,
                                    3L,
                                    Actionable.MODULATE,
                                    host.accessActionSource()),
                            3L,
                            "Same-tick storage probe must leave Trinity storage through the real AE grid");
                    KeyCounter availableAfterExtract = new KeyCounter();
                    networkStorage.getAvailableStacks(availableAfterExtract);
                    helper.assertValueEqual(
                            availableAfterExtract.get(storageProbe),
                            0L,
                            "Extracted storage probe must be absent from the single Trinity mount");
                    assertHostStorage(helper, fixture, storageProbe, 0L);

                    helper.assertValueEqual(
                            level.getGameTime(),
                            sameTick,
                            "Storage write and read must not advance the same-tick submit window");
                    assertCpuPublishedOnce(helper, craftingService, reservedCpu);
                    RoutedCraftingPatternDetails publishedAfterStorage = requireSinglePublishedRoute(
                            helper,
                            grid,
                            AEItemKey.of(Items.CRAFTING_TABLE),
                            route);
                    helper.assertValueEqual(
                            providerCount(concreteService, publishedAfterStorage),
                            1,
                            "Storage I/O must retain exactly one Trinity provider for the routed pattern");

                    TrinityDataCoreVirtualCpu worker = submitAndDispatch(helper, fixture, executablePlan);
                    helper.assertValueEqual(
                            core.queuedBatchCount(SAME_TICK_STORAGE_PATTERN_SLOT),
                            1,
                            "The same-tick CPU dispatch must reach the exact routed P-core slot");
                    TrinityCraftingBatch queued = core.queuedBatches(SAME_TICK_STORAGE_PATTERN_SLOT).getFirst();
                    helper.assertValueEqual(queued.route(), route, "The same-tick queued group must retain its route");
                    helper.assertValueEqual(
                            queued.queuedTick(),
                            sameTick,
                            "The provider must accept the CPU dispatch in the storage interaction tick");
                    helper.assertValueEqual(
                            worker.getWaitingFor(AEItemKey.of(Items.CRAFTING_TABLE)),
                            1L,
                            "The allocated Trinity worker must wait for the routed output");
                    assertHostStorage(helper, fixture, AEItemKey.of(Items.CRIMSON_PLANKS), 0L);

                    AEItemKey busyStorageProbe = AEItemKey.of(Items.REDSTONE);
                    helper.assertValueEqual(
                            networkStorage.insert(
                                    busyStorageProbe,
                                    2L,
                                    Actionable.MODULATE,
                                    host.accessActionSource()),
                            2L,
                            "Storage must remain writable while the Trinity CPU and pattern provider are active");
                    helper.assertValueEqual(
                            networkStorage.extract(
                                    busyStorageProbe,
                                    2L,
                                    Actionable.MODULATE,
                                    host.accessActionSource()),
                            2L,
                            "Storage must remain readable while a routed CPU job is waiting");
                    helper.assertTrue(
                            craftingService.getCpus().contains(worker),
                            "Busy storage I/O must not withdraw the allocated Trinity worker");
                    helper.assertValueEqual(
                            fixture.accessHatches().stream().filter(host::isLeaseOwner).count(),
                            1L,
                            "Busy storage I/O must retain the same unique lease publication");
                    assertHostStorage(helper, fixture, busyStorageProbe, 0L);
                    assertCpuPublishedOnce(helper, craftingService, reservedCpu);
                    helper.assertValueEqual(
                            providerCount(
                                    concreteService,
                                    requireSinglePublishedRoute(
                                            helper,
                                            grid,
                                            AEItemKey.of(Items.CRAFTING_TABLE),
                                            route)),
                            1,
                            "The real submit must retain exactly one routed Trinity provider");
                    helper.assertValueEqual(
                            level.getGameTime(),
                            sameTick,
                            "Storage I/O, CPU submit and provider dispatch must share one server tick");
                })
                .thenSucceed();
    }

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
        PendingCraftingPlan tablePlan = new PendingCraftingPlan(
                level,
                AEItemKey.of(Items.CRAFTING_TABLE),
                COUNTED_BATCH_SIZE);
        PendingCraftingPlan cakePlan = new PendingCraftingPlan(
                level,
                AEItemKey.of(Items.CAKE),
                LARGE_COUNTED_BATCH_SIZE);
        AtomicReference<TrinityDataCoreVirtualCpu> activeWorker = new AtomicReference<>();

        helper.startSequence()
                .thenWaitUntil(fixture::awaitOnline)
                .thenExecute(() -> {
                    helper.assertTrue(core.trySetPattern(TABLE_PATTERN_SLOT, tablePattern),
                            "Table pattern should install in its exact physical slot");
                    helper.assertTrue(core.trySetPattern(CAKE_PATTERN_SLOT, cakePattern),
                            "Cake pattern should install in its exact physical slot");
                    host.serverTick();
                    fixture.refreshPatternPublication();
                })
                .thenWaitUntil(() -> {
                    host.serverTick();
                    fixture.refreshPatternPublication();
                    assertPublishedRoute(helper, fixture.grid(), AEItemKey.of(Items.CRAFTING_TABLE), tableRoute);
                    assertPublishedRoute(helper, fixture.grid(), AEItemKey.of(Items.CAKE), cakeRoute);
                })
                .thenExecute(() -> {
                    insertIntoNetwork(helper, fixture, AEItemKey.of(Items.CRIMSON_PLANKS), 512L);
                    tablePlan.start(fixture.grid(), host.accessActionSource());
                })
                .thenWaitUntil(tablePlan::await)
                .thenExecute(() -> {
                    ICraftingPlan plan = tablePlan.plan();
                    assertPlan(helper, plan, tableRoute, AEItemKey.of(Items.CRAFTING_TABLE), COUNTED_BATCH_SIZE);
                    helper.assertValueEqual(
                            plan.usedItems().get(AEItemKey.of(Items.CRIMSON_PLANKS)),
                            512L,
                            "Real AE2 planning should select stored Crimson Planks as substitutes");
                    helper.assertValueEqual(
                            plan.usedItems().get(AEItemKey.of(Items.OAK_PLANKS)),
                            0L,
                            "Real AE2 planning should not require unavailable encoded Oak Planks");

                    long slotRevisionBeforeDispatch = core.patternSlot(TABLE_PATTERN_SLOT).revision();
                    TrinityDataCoreVirtualCpu worker = submitAndDispatch(helper, fixture, plan);
                    activeWorker.set(worker);
                    // One first enqueue records persistent state and enters the sparse work index.
                    helper.assertValueEqual(
                            core.patternSlot(TABLE_PATTERN_SLOT).revision(),
                            slotRevisionBeforeDispatch + 2L,
                            "A real counted table task must enqueue through the Provider exactly once");
                    long dispatchTick = level.getGameTime();
                    assertOnlyRouteQueued(helper, host, tableRoute, 1);
                    assertSubstitutedTableBatch(
                            helper,
                            core.queuedBatches(TABLE_PATTERN_SLOT),
                            tableRoute,
                            dispatchTick,
                            COUNTED_BATCH_SIZE);
                    helper.assertValueEqual(
                            worker.getWaitingFor(AEItemKey.of(Items.CRAFTING_TABLE)),
                            COUNTED_BATCH_SIZE,
                            "Trinity CPU should wait for every counted routed table output");

                    host.serverTick();
                    assertOnlyRouteQueued(helper, host, tableRoute, 1);
                })
                .thenIdle(1)
                .thenExecute(() -> {
                    host.serverTick();
                    helper.assertValueEqual(core.queuedBatchCount(TABLE_PATTERN_SLOT), 0,
                            "Both same-slot batches should execute on the next tick");
                    helper.assertTrue(core.pendingOutputs(tableRoute).isEmpty(),
                            "All table outputs should leave the P core after CPU routing");
                    helper.assertFalse(activeWorker.get().isBusy(),
                            "Trinity worker should finish after both table outputs return");
                    assertHostStorage(helper, fixture, AEItemKey.of(Items.CRAFTING_TABLE), COUNTED_BATCH_SIZE);
                    assertHostStorage(helper, fixture, AEItemKey.of(Items.CRIMSON_PLANKS), 0L);

                    insertIntoNetwork(
                            helper,
                            fixture,
                            AEItemKey.of(Items.MILK_BUCKET),
                            Math.multiplyExact(3L, LARGE_COUNTED_BATCH_SIZE));
                    insertIntoNetwork(
                            helper,
                            fixture,
                            AEItemKey.of(Items.SUGAR),
                            Math.multiplyExact(2L, LARGE_COUNTED_BATCH_SIZE));
                    insertIntoNetwork(helper, fixture, AEItemKey.of(Items.EGG), LARGE_COUNTED_BATCH_SIZE);
                    insertIntoNetwork(
                            helper,
                            fixture,
                            AEItemKey.of(Items.WHEAT),
                            Math.multiplyExact(3L, LARGE_COUNTED_BATCH_SIZE));
                    cakePlan.start(fixture.grid(), host.accessActionSource());
                })
                .thenWaitUntil(cakePlan::await)
                .thenExecute(() -> {
                    ICraftingPlan plan = cakePlan.plan();
                    assertPlan(helper, plan, cakeRoute, AEItemKey.of(Items.CAKE), LARGE_COUNTED_BATCH_SIZE);
                    long slotRevisionBeforeDispatch = core.patternSlot(CAKE_PATTERN_SLOT).revision();
                    TrinityDataCoreVirtualCpu worker = submitAndDispatch(helper, fixture, plan);
                    activeWorker.set(worker);
                    // A second provider call would add at least one more persistent revision.
                    helper.assertValueEqual(
                            core.patternSlot(CAKE_PATTERN_SLOT).revision(),
                            slotRevisionBeforeDispatch + 2L,
                            "A real counted cake task must enqueue through the Provider exactly once");
                    helper.assertValueEqual(core.queuedBatchCount(CAKE_PATTERN_SLOT), 1,
                            "Counted cake dispatch should enter one group in its exact physical slot");
                    TrinityCraftingBatch cakeBatch = core.queuedBatches(CAKE_PATTERN_SLOT).getFirst();
                    helper.assertValueEqual(
                            cakeBatch.count(),
                            LARGE_COUNTED_BATCH_SIZE,
                            "One cake queue group should retain all counted logical crafts");
                    helper.assertValueEqual(cakeBatch.route(), cakeRoute, "Counted cake group should retain its route");
                    helper.assertValueEqual(
                            worker.getWaitingFor(AEItemKey.of(Items.CAKE)),
                            LARGE_COUNTED_BATCH_SIZE,
                            "Trinity CPU should wait for every counted cake output");
                    helper.assertValueEqual(
                            worker.getWaitingFor(AEItemKey.of(Items.BUCKET)),
                            Math.multiplyExact(3L, LARGE_COUNTED_BATCH_SIZE),
                            "Trinity CPU should scale all three container remainders by the counted batch");

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
                    helper.assertFalse(activeWorker.get().isBusy(),
                            "Trinity worker should finish after cake and buckets return");
                    assertHostStorage(helper, fixture, AEItemKey.of(Items.CAKE), LARGE_COUNTED_BATCH_SIZE);
                    assertHostStorage(
                            helper,
                            fixture,
                            AEItemKey.of(Items.BUCKET),
                            Math.multiplyExact(3L, LARGE_COUNTED_BATCH_SIZE));
                    assertHostStorage(helper, fixture, AEItemKey.of(Items.MILK_BUCKET), 0L);
                    assertHostStorage(helper, fixture, AEItemKey.of(Items.SUGAR), 0L);
                    assertHostStorage(helper, fixture, AEItemKey.of(Items.EGG), 0L);
                    assertHostStorage(helper, fixture, AEItemKey.of(Items.WHEAT), 0L);
                    assertHostStorage(helper, fixture, AEItemKey.of(Items.CRAFTING_TABLE), COUNTED_BATCH_SIZE);
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

    @TestHolder("trinity_data_core_real_ae2_crafting_pauses_across_structure_failure")
    @EmptyTemplate("50x32x50")
    @GameTest(template = "empty_50x32x50", timeoutTicks = 300)
    public static void realAe2QueuedBatchSurvivesCraftingStructureFailure(GameTestHelper helper) {
        TrinityDataCoreGameTestFixture fixture = TrinityDataCoreGameTestFixture.create(helper);
        TrinityDataCoreBlockEntity host = fixture.host();
        ServerLevel level = helper.getLevel();
        TrinityPatternCatalog.CoreMount mount = host.getPatternCatalog().mountedCores().getFirst();
        TrinityPatternCore core = mount.core();
        PatternRoute route = new PatternRoute(host.getHostId(), core.coreId(), STRUCTURE_PAUSE_PATTERN_SLOT);
        ItemStack pattern = craftingTablePattern(level);
        PendingCraftingPlan plan = new PendingCraftingPlan(level, AEItemKey.of(Items.CRAFTING_TABLE), 1L);
        BlockPos interruptedPosition = findAdjacentCraftingFrame(level, host.getPatternCatalog().mountedCores());
        BlockState interruptedState = level.getBlockState(interruptedPosition);
        AtomicReference<TrinityDataCoreVirtualCpu> activeWorker = new AtomicReference<>();

        helper.startSequence()
                .thenWaitUntil(fixture::awaitOnline)
                .thenExecute(() -> {
                    helper.assertTrue(core.trySetPattern(STRUCTURE_PAUSE_PATTERN_SLOT, pattern),
                            "Pause test pattern should install in its exact physical slot");
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
                })
                .thenExecute(() -> {
                    insertIntoNetwork(helper, fixture, AEItemKey.of(Items.CRIMSON_PLANKS), 4L);
                    plan.start(fixture.grid(), host.accessActionSource());
                })
                .thenWaitUntil(plan::await)
                .thenExecute(() -> {
                    assertPlan(helper, plan.plan(), route, AEItemKey.of(Items.CRAFTING_TABLE), 1L);
                    TrinityDataCoreVirtualCpu worker = submitAndDispatch(helper, fixture, plan.plan());
                    activeWorker.set(worker);
                    helper.assertValueEqual(core.queuedBatchCount(STRUCTURE_PAUSE_PATTERN_SLOT), 1,
                            "Real AE2 dispatch should enqueue one routed batch before structure interruption");
                    helper.assertValueEqual(
                            worker.getWaitingFor(AEItemKey.of(Items.CRAFTING_TABLE)),
                            1L,
                            "Trinity CPU should wait for the interrupted routed output");

                    helper.assertTrue(level.destroyBlock(interruptedPosition, false),
                            "Pause test should physically remove one crafting frame block");
                    host.requestStructureRecheck();
                    host.serverTick();
                    fixture.refreshPatternPublication();

                    helper.assertFalse(host.isCraftingStructureFormed(),
                            "Removing a real crafting frame block should invalidate the crafting structure");
                    assertPausedRoutedBatch(helper, fixture, core, activeWorker.get(), route);
                })
                .thenIdle(2)
                .thenExecute(() -> {
                    host.serverTick();
                    fixture.refreshPatternPublication();
                    helper.assertFalse(host.isCraftingStructureFormed(),
                            "Crafting structure should remain invalid while its frame block is absent");
                    assertPausedRoutedBatch(helper, fixture, core, activeWorker.get(), route);

                    var rebuild = TrinityDataCoreBlockEntity.executeAutoBuild(
                            level,
                            helper.makeMockPlayer(GameType.CREATIVE),
                            host.getBlockPos(),
                            Direction.SOUTH,
                            false,
                            new TrinityAutoBuildRequest(
                                    TrinityAutoBuildRequest.CRAFTING_STRUCTURE_INDEX,
                                    new TrinityAutoBuildOptions(
                                            true,
                                            1,
                                            Map.of(TrinityAutoBuildBlockMap.PATTERN_PROCESSING_CORE, 1))));
                    helper.assertTrue(rebuild.success(),
                            "Crafting structure rebuild should restore the interrupted frame: " + rebuild.failure());
                    helper.assertValueEqual(rebuild.placed(), 1,
                            "Crafting structure rebuild should replace exactly the removed frame block");
                    helper.assertValueEqual(level.getBlockState(interruptedPosition), interruptedState,
                            "Crafting structure rebuild should restore the original frame state");
                    host.requestStructureRecheck();
                })
                .thenWaitUntil(() -> {
                    host.serverTick();
                    fixture.refreshPatternPublication();

                    helper.assertTrue(host.isCraftingStructureFormed(),
                            "Restored crafting structure should pass a real host recheck");
                    helper.assertValueEqual(core.queuedBatchCount(STRUCTURE_PAUSE_PATTERN_SLOT), 0,
                            "The same retained routed batch should execute after structure recovery");
                    helper.assertTrue(core.pendingOutputs(route).isEmpty(),
                            "Recovered routed output should leave the P core after CPU routing");
                    helper.assertFalse(activeWorker.get().isBusy(),
                            "Trinity worker should complete after the recovered routed output returns");
                    assertHostStorage(helper, fixture, AEItemKey.of(Items.CRIMSON_PLANKS), 0L);
                    assertHostStorage(helper, fixture, AEItemKey.of(Items.CRAFTING_TABLE), 1L);
                })
                .thenSucceed();
    }

    private static BlockPos findAdjacentCraftingFrame(ServerLevel level,
                                                      List<TrinityPatternCatalog.CoreMount> mounts) {
        for (TrinityPatternCatalog.CoreMount mount : mounts) {
            for (Direction direction : Direction.values()) {
                BlockPos candidate = mount.position().relative(direction);
                if (level.getBlockState(candidate).is(ModBlocks.DATA_FRAMEWORK.get())) {
                    return candidate.immutable();
                }
            }
        }
        throw new IllegalStateException("Formed Trinity crafting structure has no frame adjacent to a P core");
    }

    private static void assertPausedRoutedBatch(GameTestHelper helper,
                                                TrinityDataCoreGameTestFixture fixture,
                                                TrinityPatternCore core,
                                                TrinityDataCoreVirtualCpu cpu,
                                                PatternRoute route) {
        helper.assertValueEqual(core.queuedBatchCount(route.slot()), 1,
                "Invalid crafting structure must retain the routed P-core queue");
        helper.assertTrue(core.pendingOutputs(route).isEmpty(),
                "Invalid crafting structure must not execute the retained routed batch");
        helper.assertTrue(cpu.isBusy(),
                "Trinity CPU must remain busy while the routed P-core batch is paused");
        helper.assertValueEqual(
                cpu.getWaitingFor(AEItemKey.of(Items.CRAFTING_TABLE)),
                1L,
                "Paused Trinity CPU must retain its requested routed output");
        assertHostStorage(helper, fixture, AEItemKey.of(Items.CRIMSON_PLANKS), 0L);
        assertHostStorage(helper, fixture, AEItemKey.of(Items.CRAFTING_TABLE), 0L);
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

    private static void assertCpuPublishedOnce(GameTestHelper helper,
                                               ICraftingService craftingService,
                                               TrinityDataCoreVirtualCpu reservedCpu) {
        helper.assertValueEqual(
                craftingService.getCpus().stream().filter(cpu -> cpu == reservedCpu).count(),
                1L,
                "AE2 crafting service must publish Trinity CPU 0 exactly once");
    }

    private static int providerCount(CraftingService craftingService, IPatternDetails pattern) {
        int count = 0;
        for (var ignored : craftingService.getProviders(pattern)) {
            count = Math.incrementExact(count);
        }
        return count;
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

    private static TrinityDataCoreVirtualCpu submitAndDispatch(GameTestHelper helper,
                                                               TrinityDataCoreGameTestFixture fixture,
                                                               ICraftingPlan plan) {
        IGrid grid = fixture.grid();
        ICraftingService craftingService = grid.getCraftingService();
        TrinityDataCoreVirtualCpu reserveCpu = reservedCpu(fixture.host());
        helper.assertTrue(craftingService.getCpus().contains(reserveCpu),
                "AE2 should publish reserved Trinity CPU 0");
        helper.assertTrue(reserveCpu.getCoProcessors() >= 1,
                "Test CPU profile should dispatch both table batches in one tick");
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
        if (!(craftingService instanceof CraftingService concreteService)) {
            throw new IllegalStateException("Trinity CPU requires AE2 CraftingService for dispatch");
        }
        fixture.host().getCraftingRuntime().tick(
                grid.getEnergyService(),
                concreteService,
                CraftingDispatchWindow.create());
        return worker;
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

    private static void assertOnlyRouteQueued(GameTestHelper helper,
                                              TrinityDataCoreBlockEntity host,
                                              PatternRoute expectedRoute,
                                              int expectedGroupCount) {
        int aggregateGroupCount = 0;
        for (TrinityPatternCatalog.CoreMount mount : host.getPatternCatalog().mountedCores()) {
            TrinityPatternCore mountedCore = mount.core();
            aggregateGroupCount += mountedCore.queuedBatchCount();
            if (mountedCore.coreId().equals(expectedRoute.coreId())) {
                helper.assertValueEqual(
                        mountedCore.queuedBatchCount(expectedRoute.slot()),
                        expectedGroupCount,
                        "Selected physical P-core slot should own every dispatched queue group");
            }
        }
        helper.assertValueEqual(
                aggregateGroupCount,
                expectedGroupCount,
                "No other P-core slot should receive this route's queue groups");
    }

    private static void assertSubstitutedTableBatch(GameTestHelper helper,
                                                    List<TrinityCraftingBatch> batches,
                                                    PatternRoute expectedRoute,
                                                    long dispatchTick,
                                                    long expectedCount) {
        helper.assertValueEqual(batches.size(), 1, "A counted dispatch should create one homogeneous queue group");
        TrinityCraftingBatch batch = batches.getFirst();
        helper.assertValueEqual(batch.count(), expectedCount, "Table group should retain every logical craft");
        helper.assertValueEqual(batch.route(), expectedRoute, "Queued table group should retain its exact route");
        helper.assertValueEqual(batch.queuedTick(), dispatchTick, "Merged table group should retain its enqueue tick");
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
        helper.assertValueEqual(substitutedAmount, 4L, "Merged table group should retain one input-grid prototype");
        helper.assertValueEqual(
                Math.multiplyExact(substitutedAmount, batch.count()),
                Math.multiplyExact(4L, expectedCount),
                "Counted table group should account for every substituted input");
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

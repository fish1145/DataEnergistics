package com.fish_dan_.data_energistics.common.crafting.trinity.execution.cpu;

import com.fish_dan_.data_energistics.Data_Energistics;
import com.fish_dan_.data_energistics.api.crafting.dispatch.CountedCraftingAdmission;
import com.fish_dan_.data_energistics.api.crafting.dispatch.CountedCraftingTarget;
import com.fish_dan_.data_energistics.api.crafting.reusable.ReusableInputContext;
import com.fish_dan_.data_energistics.api.crafting.reusable.ReusableInputRule;
import com.fish_dan_.data_energistics.api.crafting.reusable.ReusableInputRuleAdapter;
import com.fish_dan_.data_energistics.api.crafting.reusable.dispatch.ReusableCraftingAdmission;
import com.fish_dan_.data_energistics.api.crafting.reusable.dispatch.ReusableCraftingCustodyCensus;
import com.fish_dan_.data_energistics.api.crafting.reusable.dispatch.ReusableCraftingProviderAdapter;
import com.fish_dan_.data_energistics.api.crafting.reusable.dispatch.ReusableCraftingRequest;
import com.fish_dan_.data_energistics.api.crafting.reusable.dispatch.ReusableCraftingRequest.Target;
import com.fish_dan_.data_energistics.api.crafting.reusable.dispatch.ReusableCraftingSessionView;
import com.fish_dan_.data_energistics.api.entrypoint.DataEnergisticsEntrypoint;
import com.fish_dan_.data_energistics.api.entrypoint.DataEnergisticsPlugin;
import com.fish_dan_.data_energistics.api.entrypoint.DataEnergisticsRegistry;
import com.fish_dan_.data_energistics.blockentity.trinity.TrinityDataCoreBlockEntity;
import com.fish_dan_.data_energistics.common.crafting.trinity.execution.route.TrinityCraftingExecutionRoute;
import com.fish_dan_.data_energistics.common.crafting.trinity.planning.CraftingQuantityMode;
import com.fish_dan_.data_energistics.common.crafting.trinity.planning.graph.TrinityBoundPatternInput;
import com.fish_dan_.data_energistics.common.crafting.trinity.planning.graph.TrinityPatternIdentity;
import com.fish_dan_.data_energistics.common.crafting.trinity.planning.plan.TrinityCraftingPlan;
import com.fish_dan_.data_energistics.common.crafting.trinity.planning.plan.TrinityPlanPatternFiring;
import com.fish_dan_.data_energistics.common.crafting.trinity.planning.plan.TrinityPlanStage;
import com.fish_dan_.data_energistics.common.crafting.trinity.profile.TrinityDataCoreCpuContribution;
import com.fish_dan_.data_energistics.common.crafting.trinity.reusable.endpoint.PersistentReusableCraftingEndpoint;
import com.fish_dan_.data_energistics.common.crafting.trinity.reusable.endpoint.PersistentReusableCraftingEndpoint.Binding;
import com.fish_dan_.data_energistics.common.crafting.trinity.reusable.endpoint.PersistentReusableCraftingEndpoint.NativeResult;
import com.fish_dan_.data_energistics.common.crafting.trinity.reusable.session.ReusableInputSession.Identity;
import com.fish_dan_.data_energistics.common.crafting.trinity.reusable.session.ReusableInputSession.Operation;
import com.fish_dan_.data_energistics.common.crafting.trinity.reusable.session.ReusableInputSession.ToolOutcome;
import com.fish_dan_.data_energistics.common.crafting.trinity.serialization.TrinityBigIntegerEncoding;
import com.fish_dan_.data_energistics.common.entrypoint.DataEnergisticsEntrypointLoader;
import com.fish_dan_.data_energistics.common.trinity.pattern.TrinityPatternPublicationSignature;
import com.fish_dan_.data_energistics.registry.DEBlocks;

import appeng.api.config.AccessRestriction;
import appeng.api.config.Actionable;
import appeng.api.config.PowerMultiplier;
import appeng.api.crafting.IPatternDetails;
import appeng.api.networking.GridHelper;
import appeng.api.networking.IGrid;
import appeng.api.networking.IManagedGridNode;
import appeng.api.networking.crafting.ICraftingProvider;
import appeng.api.networking.energy.IAEPowerStorage;
import appeng.api.networking.security.IActionSource;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.GenericStack;
import appeng.api.stacks.KeyCounter;
import appeng.api.storage.IStorageMounts;
import appeng.api.storage.IStorageProvider;
import appeng.api.storage.MEStorage;
import appeng.crafting.execution.CraftingCpuHelper;
import appeng.hooks.ticking.TickHandler;
import appeng.me.helpers.MachineSource;
import appeng.me.service.CraftingService;

import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import net.neoforged.testframework.annotation.TestHolder;
import net.neoforged.testframework.gametest.EmptyTemplate;

import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import it.unimi.dsi.fastutil.objects.ObjectOpenHashSet;
import org.jspecify.annotations.Nullable;

import java.math.BigInteger;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

@GameTestHolder(Data_Energistics.MODID)
@PrefixGameTestTemplate(false)
public final class TrinityReusableCpuDispatchGameTest {

    private static final ResourceLocation RULE = Data_Energistics.id("cpu_dispatch_fixture");
    private static final ResourceLocation MODE = Data_Energistics.id("cpu_dispatch_fixture_mode");

    private TrinityReusableCpuDispatchGameTest() {}

    @TestHolder("cpu_dispatch_reuses_one_tool_for_a_thousand_real_operations")
    @EmptyTemplate("5")
    @GameTest(template = "empty_5x5", timeoutTicks = 1800)
    public static void reusesOneToolForAThousandRealOperations(GameTestHelper helper) {
        run(helper, new Fixture(helper, 1000, 17, Scenario.CONTINUOUS));
    }

    @TestHolder("cpu_dispatch_cancelled_suffix_settles_and_replans_without_repeating_completed_output")
    @EmptyTemplate("5")
    @GameTest(template = "empty_5x5", timeoutTicks = 1800)
    public static void cancelledSuffixSettlesAndReplansWithoutRepeatingCompletedOutput(GameTestHelper helper) {
        run(helper, new Fixture(helper, 8, 4, Scenario.CANCEL));
    }

    @TestHolder("cpu_dispatch_older_cpu_snapshot_quarantines_remote_custody_without_replaying_assets")
    @EmptyTemplate("5")
    @GameTest(template = "empty_5x5", timeoutTicks = 1800)
    public static void olderCpuSnapshotQuarantinesRemoteCustodyWithoutReplayingAssets(GameTestHelper helper) {
        run(helper, new Fixture(helper, 8, 4, Scenario.OLDER_CPU_SNAPSHOT));
    }

    private enum Scenario {
        CONTINUOUS,
        CANCEL,
        OLDER_CPU_SNAPSHOT
    }

    private static void run(GameTestHelper helper, Fixture fixture) {
        helper.onEachTick(() -> {
            try {
                fixture.step();
            } catch (RuntimeException | Error failure) {
                fixture.close();
                throw failure;
            }
        });
    }

    /** Normal test-classpath plugin discovery; no mutable global registry or production test switch. */
    @DataEnergisticsEntrypoint
    public static final class FixtureRules implements DataEnergisticsPlugin {

        public FixtureRules() {}

        @Override
        public void register(DataEnergisticsRegistry registry) {
            registry.reusableInputs().register(new ReusableInputRuleAdapter() {

                @Override
                public ResourceLocation id() {
                    return RULE;
                }

                @Override
                public Optional<ReusableInputRule> resolve(ReusableInputContext context) {
                    return context.pattern() instanceof FixturePattern && context.inputSlot() == 0 &&
                            context.actualInput().what().equals(tool()) && context.machineMode().equals(Optional.of(MODE)) ?
                                    Optional.of(ReusableInputRule.unchanged(RULE, 1L, tool())) : Optional.empty();
                }
            });
        }
    }

    private static final class Fixture implements ICraftingProvider, ReusableCraftingProviderAdapter,
                                       PersistentReusableCraftingEndpoint.Host, IStorageProvider, MEStorage, IAEPowerStorage {

        @Override
        public ReusableCraftingCustodyCensus reusableCustody(String cpuOwner) {
            return endpoint.reusableCustody(cpuOwner);
        }

        private final GameTestHelper helper;
        private final int requested;
        private final int capacity;
        private final Scenario scenario;
        private final FixturePattern pattern = new FixturePattern();
        private final KeyCounter stock = new KeyCounter();
        private final KeyCounter pendingOutputs = new KeyCounter();
        private final ObjectOpenHashSet<UUID> settled = new ObjectOpenHashSet<>();
        private final ObjectOpenHashSet<UUID> sessions = new ObjectOpenHashSet<>();
        private final IManagedGridNode node;
        private final PersistentReusableCraftingEndpoint endpoint;
        private final Target target;
        private final TrinityPatternIdentity publication;
        private final FixtureHost host;
        private final TrinityDataCoreCraftingRuntime runtime;
        private @Nullable TrinityDataCoreVirtualCpu worker;
        private IGrid grid;
        private CraftingService crafting;
        private int ticks;
        private long executed;
        private long consumed;
        private long toolDelivered;
        private long materialDelivered;
        private long toolReturned;
        private long admissions;
        private boolean closedSuffix;
        private boolean cancellationChecked;
        private boolean finished;
        private double power = 1_000_000D;
        private double powerAtSubmit;
        private Optional<CompoundTag> preDispatchSnapshot = Optional.empty();
        private boolean restoredOlderSnapshot;
        private int quarantineReloadTick = -1;

        private Fixture(GameTestHelper helper, int requested, int capacity, Scenario scenario) {
            this.helper = helper;
            this.requested = requested;
            this.capacity = capacity;
            this.scenario = scenario;
            String identity = "cpu-dispatch-fixture-" + UUID.randomUUID();
            this.endpoint = new PersistentReusableCraftingEndpoint(identity);
            this.target = new Target(identity, CountedCraftingTarget.route("fixture"), Optional.of(MODE));
            this.publication = TrinityPatternIdentity.capture(TrinityPatternPublicationSignature.capture(pattern), helper.getLevel().registryAccess());
            stock.add(tool(), 1L);
            stock.add(material(), requested);
            node = GridHelper.createManagedNode(this, (owner, changed) -> {})
                    .setInWorldNode(false).setIdlePowerUsage(0D)
                    .addService(ICraftingProvider.class, this).addService(IStorageProvider.class, this)
                    .addService(IAEPowerStorage.class, this);
            node.create(helper.getLevel(), helper.absolutePos(BlockPos.ZERO));
            grid = node.getGrid();
            crafting = (CraftingService) grid.getCraftingService();
            host = new FixtureHost(helper.getLevel(), new TrinityCraftingExecutionRoute(grid, grid, 1L, 1L), node);
            runtime = new TrinityDataCoreCraftingRuntime(host);
        }

        private void step() {
            if (finished) return;
            if (++ticks > 1700) helper.fail("Real CPU dispatch did not finish: executed=" + executed + ", tool deliveries=" + toolDelivered);
            if (worker == null) {
                if (!node.isActive() || !crafting.getCraftingFor(product()).contains(pattern)) return;
                runtime.setContribution("fixture", TrinityDataCoreCpuContribution.of(1_000_000L, 0, 1));
                runtime.setMainStructureFormed(true);
                runtime.setPaused(false);
                ((TrinityCraftingRuntimeRegistry) crafting).data_energistics$publish(node.getNode(), runtime);
                ReusableInputContext context = ReusableInputContext.builder().pattern(pattern).actualInput(new GenericStack(tool(), 1L))
                        .exactInputs(List.of(new GenericStack(tool(), 1L), new GenericStack(material(), 1L))).inputSlot(0)
                        .ownership(ReusableInputContext.Ownership.CPU_SUPPLIED).actionSource(host.accessActionSource())
                        .level(helper.getLevel()).recipeId(Optional.empty()).machineMode(Optional.of(MODE)).target(target.route()).build();
                helper.assertTrue(DataEnergisticsEntrypointLoader.snapshot().reusableInputs().resolve(context).isPresent(),
                        "Test-only reusable rule plugin must be discovered by normal common-setup scanning");
                powerAtSubmit = power;
                var submitted = runtime.submitJob(grid, plan(requested, publication), host.accessActionSource(), null);
                helper.assertTrue(submitted.successful(), "A real runtime worker must accept the fixture plan: " + submitted.errorCode());
                worker = runtime.publishedCpus().stream().filter(cpu -> cpu.number() > 0).findFirst().orElseThrow();
                if (scenario == Scenario.OLDER_CPU_SNAPSHOT) {
                    helper.assertTrue(worker.logic().reusableLedger().sessions().isEmpty(), "Snapshot precedes any reusable admission");
                    helper.assertValueEqual(worker.getStored(tool()), BigInteger.ONE, "Pre-dispatch CPU owns the initial tool");
                    preDispatchSnapshot = Optional.of(worker.logic().writeToTag(helper.getLevel().registryAccess()));
                }
                return;
            }
            if (scenario == Scenario.OLDER_CPU_SNAPSHOT) {
                stepOlderCpuSnapshot();
                return;
            }
            endpoint.tick(TickHandler.instance().getCurrentTick(), scenario == Scenario.CANCEL && !closedSuffix ? 1 : capacity, this);
            flushOutputs();
            if (scenario == Scenario.CANCEL && !closedSuffix && executed == 2L) {
                UUID resident = endpoint.residentSessionId().orElseThrow();
                var view = endpoint.query(resident).orElseThrow();
                helper.assertTrue(view.accepted() > view.completed(), "Cancellation fixture must include genuinely accepted unexecuted work");
                closedSuffix = true;
                endpoint.close(resident, this);
            }
            endpoint.residentSessionId().flatMap(endpoint::query).ifPresent(view -> {
                if (view.failure().isPresent()) helper.fail("Native fixture was quarantined: " + view.failure().orElseThrow());
            });
            if (toolDelivered > toolReturned) {
                helper.assertValueEqual(worker.getStored(tool()), BigInteger.ZERO, "Resident tool must not also appear in consumable CPU inventory");
                helper.assertValueEqual(worker.getWaitingFor(tool()), BigInteger.ZERO, "Resident tool must not become an ordinary per-operation waiting output");
            }
            if (!worker.isBusy() && !endpoint.hasResidentSession() && pendingOutputs.isEmpty()) {
                helper.assertValueEqual(executed, (long) requested, "Only required physical operations execute, including after cancelled-suffix replanning");
                helper.assertValueEqual(consumed, (long) requested, "Material consumption matches actual operations");
                helper.assertValueEqual(stock.get(product()), (long) requested, "Only actually produced outputs reach final network storage");
                helper.assertValueEqual(stock.get(material()), 0L, "All and only the required material was consumed");
                helper.assertValueEqual(stock.get(tool()), 1L, "One actual tool returns after custody closes");
                helper.assertTrue(admissions > 1L, "The fixture must pass through partial-capacity appends");
                helper.assertValueEqual(toolDelivered, (long) sessions.size(), "Each continuous session receives its tool only once");
                helper.assertValueEqual(toolReturned, (long) sessions.size(), "Each session returns its tool only once");
                if (scenario == Scenario.CANCEL) helper.assertTrue(cancellationChecked && sessions.size() == 2, "Cancelled suffix must settle then resume in one new session");
                else helper.assertValueEqual(sessions.size(), 1, "All thousand operations share a single resident tool session");
                KeyCounter[] sample = { new KeyCounter(), new KeyCounter() };
                sample[0].add(tool(), 1L);
                sample[1].add(material(), 1L);
                double minimumCharge = PowerMultiplier.CONFIG.multiply(CraftingCpuHelper.calculatePatternPower(sample) * requested);
                helper.assertTrue(powerAtSubmit - power + 0.00001D >= minimumCharge,
                        "Resident optimization must not reduce per-operation CPU energy charges");
                close();
                helper.succeed();
            }
        }

        private void stepOlderCpuSnapshot() {
            if (admissions == 0L) return;
            UUID resident = endpoint.residentSessionId().orElseThrow();
            var remote = endpoint.query(resident).orElseThrow();
            helper.assertValueEqual(remote.accepted(), (long) capacity, "Remote endpoint genuinely accepted the first partial batch");
            helper.assertValueEqual(remote.completed(), 0L, "Snapshot fork happens before any native operation executes");
            helper.assertValueEqual(admissions, 1L, "The older CPU must not dispatch a second admission");
            helper.assertValueEqual(toolDelivered, 1L, "The older CPU must not deliver its duplicated snapshot tool");
            helper.assertValueEqual(materialDelivered, (long) capacity, "The older CPU must not resend snapshot materials");
            helper.assertTrue(settled.isEmpty() && toolReturned == 0L, "Unknown remote custody must not be automatically acknowledged or returned");
            if (!restoredOlderSnapshot) {
                String owner = worker.logic().reusableLedger().ownerIdentity();
                worker.logic().readFromTag(preDispatchSnapshot.orElseThrow(), helper.getLevel().registryAccess());
                restoredOlderSnapshot = true;
                helper.assertValueEqual(worker.logic().reusableLedger().ownerIdentity(), owner, "Current-format rollback preserves the same CPU owner");
                helper.assertTrue(worker.logic().reusableLedger().sessions().isEmpty(), "Earlier CPU snapshot has no local knowledge of the accepted session");
                helper.assertValueEqual(worker.getStored(tool()), BigInteger.ONE, "Earlier snapshot still contains the tool now physically held remotely");
                helper.assertValueEqual(worker.getStored(material()), BigInteger.valueOf(requested), "Earlier snapshot still contains the original materials");
                return;
            }
            var ledger = worker.logic().reusableLedger();
            helper.assertTrue(ledger.sessions().isEmpty(), "Census evidence cannot automatically adopt the unknown remote session");
            if (!ledger.hasRemoteEvidence()) return;
            var evidence = ledger.snapshot().remoteEvidence();
            helper.assertValueEqual(evidence.size(), 1, "One unknown remote session produces one retained ownership conflict");
            helper.assertValueEqual(evidence.getFirst().sessionId(), resident, "Quarantine identifies the actual remote session");
            helper.assertValueEqual(evidence.getFirst().targetIdentity(), target.persistentIdentity(), "Quarantine identifies the actual remote target");
            helper.assertValueEqual(evidence.getFirst().accepted(), remote.accepted(), "Quarantine preserves the remotely accepted count");
            helper.assertTrue(!evidence.getFirst().settlementAcknowledged() && ledger.hasUncertainOwnership(), "Unknown accepted custody remains unacknowledged and isolated");
            if (quarantineReloadTick < 0) {
                CompoundTag quarantined = worker.logic().writeToTag(helper.getLevel().registryAccess());
                worker.logic().readFromTag(quarantined, helper.getLevel().registryAccess());
                helper.assertValueEqual(worker.logic().reusableLedger().snapshot().remoteEvidence(), evidence, "Current-format CPU reload preserves the exact custody evidence");
                quarantineReloadTick = ticks;
                return;
            }
            if (ticks - quarantineReloadTick < 5) return;
            helper.assertValueEqual(executed, 0L, "No native execution is fabricated to reconcile the fork");
            helper.assertValueEqual(worker.getStored(tool()), BigInteger.ONE, "Quarantine preserves the ambiguous local snapshot without dispatching it");
            close();
            helper.succeed();
        }

        private void flushOutputs() {
            List<GenericStack> outputs = new ObjectArrayList<>();
            pendingOutputs.forEach(entry -> outputs.add(new GenericStack(entry.getKey(), entry.getLongValue())));
            for (var output : outputs) {
                long accepted = crafting.insertIntoCpus(output.what(), output.amount(), Actionable.MODULATE);
                pendingOutputs.remove(output.what(), accepted);
            }
            pendingOutputs.removeZeros();
        }

        private void close() {
            if (finished) return;
            finished = true;
            runtime.cancelAllJobs();
            var activeNode = node.getNode();
            if (activeNode != null) ((TrinityCraftingRuntimeRegistry) crafting).data_energistics$withdraw(activeNode);
            node.destroy();
        }

        @Override
        public List<IPatternDetails> getAvailablePatterns() {
            return List.of(pattern);
        }

        @Override
        public boolean isBusy() {
            return false;
        }

        @Override
        public boolean pushPattern(IPatternDetails pattern, KeyCounter[] inputs) {
            throw new IllegalStateException("Fixture must exercise reusable CPU dispatch");
        }

        @Override
        public @Nullable CountedCraftingAdmission prepareBatch(IPatternDetails pattern, KeyCounter[] inputs, long count) {
            return null;
        }

        @Override
        public List<Target> reusableTargets(IPatternDetails pattern, IActionSource source, ServerLevel level) {
            return List.of(target);
        }

        @Override
        public Optional<ReusableCraftingSessionView> reusableSession(UUID sessionId) {
            return endpoint.query(sessionId);
        }

        @Override
        public Optional<ReusableCraftingSessionView.AppendReceipt> reusableReceipt(UUID sessionId, long sequence) {
            return endpoint.receipt(sessionId, sequence);
        }

        @Override
        public void closeReusableSession(UUID sessionId) {
            endpoint.close(sessionId, this);
        }

        @Override
        public boolean requestReusableYield(ReusableCraftingRequest request) {
            return endpoint.requestYield(request, TickHandler.instance().getCurrentTick(), this);
        }

        @Override
        public @Nullable ReusableCraftingAdmission prepareReusable(ReusableCraftingRequest request) {
            long queued = endpoint.query(request.sessionId()).map(view -> view.accepted() - view.completed() - view.cancelled()).orElse(0L);
            long count = Math.min(request.requestedCount(), capacity - queued);
            if (count <= 0L) return null;
            ReusableCraftingRequest partial = new ReusableCraftingRequest(request.sessionId(), request.jobId(), request.cpuOwner(), request.sequence(),
                    request.target(), request.pattern(), request.inputs(), request.offeredTools(), count, request.recipeId(), request.actionSource(), request.level());
            ReusableCraftingAdmission admission = endpoint.prepare(partial, TickHandler.instance().getCurrentTick(), this);
            if (admission == null) return null;
            return new ReusableCraftingAdmission() {

                @Override
                public long count() {
                    return admission.count();
                }

                @Override
                public List<ReusableCraftingRequest.SlotStack> physicalInputs() {
                    return admission.physicalInputs();
                }

                @Override
                public boolean replay() {
                    return admission.replay();
                }

                @Override
                public boolean hasTransferredInputOwnership() {
                    return admission.hasTransferredInputOwnership();
                }

                @Override
                public boolean commit(KeyCounter[] delivery) {
                    boolean accepted = admission.commit(delivery);
                    if (accepted && !admission.replay()) {
                        admissions++;
                        sessions.add(request.sessionId());
                        for (var input : admission.physicalInputs()) {
                            if (input.stack().what().equals(tool())) toolDelivered += input.stack().amount();
                            if (input.stack().what().equals(material())) materialDelivered += input.stack().amount();
                        }
                    }
                    return accepted;
                }
            };
        }

        @Override
        public boolean settleReusableSession(UUID sessionId, ReturnReceiver receiver) {
            return endpoint.settle(sessionId, settlement -> {
                if (!receiver.receive(settlement)) return false;
                if (settled.add(sessionId)) {
                    for (GenericStack asset : settlement.returnedAssets()) if (asset.what().equals(tool())) toolReturned += asset.amount();
                    long cancelled = settlement.receipts().stream().mapToLong(ReusableCraftingSessionView.AppendReceipt::cancelled).sum();
                    if (cancelled > 0L) {
                        helper.assertValueEqual(worker.getWaitingFor(product()), BigInteger.ZERO, "CPU settlement removes only cancelled unexecuted waiting output");
                        CompoundTag job = worker.logic().writeToTag(helper.getLevel().registryAccess()).getCompound("job");
                        CompoundTag time = job.getCompound("time_tracker");
                        BigInteger started = TrinityBigIntegerEncoding.decode(time.getCompound("started_work").getByteArray(product().getType().getId().toString()), "test started work");
                        BigInteger completed = TrinityBigIntegerEncoding.decode(time.getCompound("completed_work").getByteArray(product().getType().getId().toString()), "test completed work");
                        helper.assertValueEqual(started, BigInteger.valueOf(requested - cancelled), "Cancelled accepted suffix reduces the original started baseline");
                        helper.assertValueEqual(completed, BigInteger.valueOf(executed), "Cancellation cannot become completed progress");
                        cancellationChecked = true;
                    }
                }
                return true;
            }, this);
        }

        @Override
        public boolean isAvailable(Binding binding) {
            return binding.publicationIdentity().equals(publication) && binding.identity().pattern().equals(pattern.getDefinition());
        }

        @Override
        public NativeResult execute(Binding binding, Operation operation) {
            if (operation.consumed().size() != 1 || !operation.consumed().getFirst().stack().equals(new GenericStack(material(), 1L)) ||
                    operation.tools().size() != 1 || !operation.tools().getFirst().stack().equals(new GenericStack(tool(), 1L))) {
                throw new IllegalStateException("Native fixture received incorrect physical material/tool escrow");
            }
            executed++;
            consumed += operation.consumed().getFirst().stack().amount();
            return new NativeResult(true, List.of(new ToolOutcome(0, List.of(operation.tools().getFirst().stack()), List.of())),
                    List.of(new GenericStack(product(), 1L)), Optional.empty());
        }

        @Override
        public void acceptOutputs(Identity identity, List<GenericStack> outputs) {
            outputs.forEach(stack -> pendingOutputs.add(stack.what(), stack.amount()));
        }

        @Override
        public void persistChanges() {
            host.setChanged();
        }

        @Override
        public void mountInventories(IStorageMounts mounts) {
            mounts.mount(this, 0);
        }

        @Override
        public Component getDescription() {
            return Component.literal("CPU dispatch fixture stock");
        }

        @Override
        public void getAvailableStacks(KeyCounter out) {
            out.addAll(stock);
        }

        @Override
        public long insert(AEKey key, long amount, Actionable mode, IActionSource source) {
            if (mode == Actionable.MODULATE) stock.add(key, amount);
            return amount;
        }

        @Override
        public long extract(AEKey key, long amount, Actionable mode, IActionSource source) {
            long taken = Math.min(stock.get(key), amount);
            if (mode == Actionable.MODULATE) stock.remove(key, taken);
            return taken;
        }

        @Override
        public double getAEMaxPower() {
            return 1_000_000D;
        }

        @Override
        public double getAECurrentPower() {
            return power;
        }

        @Override
        public boolean isAEPublicPowerStorage() {
            return true;
        }

        @Override
        public AccessRestriction getPowerFlow() {
            return AccessRestriction.READ_WRITE;
        }

        @Override
        public double injectAEPower(double amount, Actionable mode) {
            double accepted = Math.min(getAEMaxPower() - power, amount);
            if (mode == Actionable.MODULATE) power += accepted;
            return amount - accepted;
        }

        @Override
        public double extractAEPower(double amount, Actionable mode, PowerMultiplier multiplier) {
            double extracted = Math.min(power, multiplier.multiply(amount));
            if (mode == Actionable.MODULATE) power -= extracted;
            return multiplier.divide(extracted);
        }
    }

    private static final class FixtureHost extends TrinityDataCoreBlockEntity {

        private final TrinityCraftingExecutionRoute route;
        private final IActionSource actor;

        private FixtureHost(ServerLevel level, TrinityCraftingExecutionRoute route, IManagedGridNode node) {
            super(BlockPos.ZERO, DEBlocks.TRINITY_DATA_CORE.get().defaultBlockState());
            this.route = route;
            this.actor = new MachineSource(node::getNode);
            setLevel(level);
        }

        @Override
        public TrinityCraftingExecutionRoute craftingExecutionRoute() {
            return route;
        }

        @Override
        public IActionSource accessActionSource() {
            return actor;
        }
    }

    private record FixturePattern() implements IPatternDetails {

        @Override
        public AEItemKey getDefinition() {
            ItemStack definition = new ItemStack(Items.PAPER);
            definition.set(DataComponents.CUSTOM_NAME, Component.literal("cpu-dispatch-fixture-pattern"));
            return AEItemKey.of(definition);
        }

        @Override
        public IInput[] getInputs() {
            return new IInput[] { new FixtureInput(tool(), true), new FixtureInput(material(), false) };
        }

        @Override
        public List<GenericStack> getOutputs() {
            return List.of(new GenericStack(product(), 1L));
        }
    }

    private record FixtureInput(AEItemKey key, boolean retained) implements IPatternDetails.IInput {

        @Override
        public GenericStack[] getPossibleInputs() {
            return new GenericStack[] { new GenericStack(key, 1L) };
        }

        @Override
        public long getMultiplier() {
            return 1L;
        }

        @Override
        public boolean isValid(AEKey input, Level level) {
            return key.equals(input);
        }

        @Override
        public @Nullable AEKey getRemainingKey(AEKey template) {
            return retained ? key : null;
        }
    }

    private static TrinityCraftingPlan plan(int count, TrinityPatternIdentity identity) {
        BigInteger total = BigInteger.valueOf(count);
        ReusableInputRule rule = ReusableInputRule.unchanged(RULE, 1L, tool());
        List<TrinityBoundPatternInput> bindings = List.of(new TrinityBoundPatternInput(0, 0, new GenericStack(tool(), 1L), 1L, tool(), rule, List.of()),
                new TrinityBoundPatternInput(1, 0, new GenericStack(material(), 1L), 1L, null));
        var firing = new TrinityPlanPatternFiring(identity, product(), 0, total, Map.of(tool(), BigInteger.ONE, material(), BigInteger.ONE),
                Map.of(product(), BigInteger.ONE), Map.of(tool(), BigInteger.ONE), bindings);
        Map<AEKey, BigInteger> initial = Map.of(tool(), BigInteger.ONE, material(), total);
        Map<AEKey, BigInteger> delta = Map.of(material(), total.negate(), product(), total);
        var stage = new TrinityPlanStage(0, false, Set.of(), List.of(firing), initial, delta);
        return TrinityCraftingPlan.builder().finalOutput(new GenericStack(product(), count)).bytes(BigInteger.valueOf(1024L))
                .catalogRevision(1L).quantityMode(CraftingQuantityMode.NET_NEW).initialExpectedInputs(initial)
                .patternFirings(Map.of(identity, total)).stages(List.of(stage)).stageOrder(List.of(0)).targetNetChange(delta).build();
    }

    private static AEItemKey tool() {
        return AEItemKey.of(Items.DIAMOND_AXE);
    }

    private static AEItemKey material() {
        return AEItemKey.of(Items.COBBLESTONE);
    }

    private static AEItemKey product() {
        return AEItemKey.of(Items.DIAMOND);
    }
}

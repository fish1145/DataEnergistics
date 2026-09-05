package com.fish_dan_.data_energistics.common.crafting.trinity.reusable.planning;

import com.fish_dan_.data_energistics.Data_Energistics;
import com.fish_dan_.data_energistics.api.crafting.dispatch.CountedCraftingAdmission;
import com.fish_dan_.data_energistics.api.crafting.dispatch.CountedCraftingTarget;
import com.fish_dan_.data_energistics.api.crafting.reusable.ReusableInputRule;
import com.fish_dan_.data_energistics.api.crafting.reusable.dispatch.ReusableCraftingAdmission;
import com.fish_dan_.data_energistics.api.crafting.reusable.dispatch.ReusableCraftingProviderAdapter;
import com.fish_dan_.data_energistics.api.crafting.reusable.dispatch.ReusableCraftingRequest;
import com.fish_dan_.data_energistics.api.crafting.reusable.dispatch.ReusableCraftingRequest.Target;
import com.fish_dan_.data_energistics.api.crafting.reusable.dispatch.ReusableCraftingSessionView;
import com.fish_dan_.data_energistics.api.registry.reusable.ReusableInputRules;
import com.fish_dan_.data_energistics.common.crafting.dynamic.EncodedPatternDynamicOutput;
import com.fish_dan_.data_energistics.common.crafting.trinity.dispatch.provider.CraftingProviderPublicationIndex;
import com.fish_dan_.data_energistics.common.crafting.trinity.dispatch.provider.IdentityCraftingProviderPublicationIndex;
import com.fish_dan_.data_energistics.common.crafting.trinity.planning.TrinityPlanningDiagnosticCode;
import com.fish_dan_.data_energistics.common.crafting.trinity.planning.graph.TrinityCraftingGraphPattern;
import com.fish_dan_.data_energistics.common.crafting.trinity.planning.graph.TrinityCraftingGraphSnapshot;
import com.fish_dan_.data_energistics.common.crafting.trinity.planning.graph.TrinityPatternIdentity;
import com.fish_dan_.data_energistics.common.crafting.trinity.planning.request.TrinityPlanningLimits;
import com.fish_dan_.data_energistics.common.trinity.pattern.TrinityPatternPublicationSignature;

import appeng.api.crafting.IPatternDetails;
import appeng.api.networking.crafting.ICraftingProvider;
import appeng.api.networking.security.IActionSource;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.GenericStack;
import appeng.api.stacks.KeyCounter;
import appeng.me.helpers.BaseActionSource;

import net.minecraft.core.component.DataComponents;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import net.neoforged.testframework.annotation.TestHolder;
import net.neoforged.testframework.gametest.EmptyTemplate;

import it.unimi.dsi.fastutil.objects.ObjectOpenHashSet;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

@GameTestHolder(Data_Energistics.MODID)
@PrefixGameTestTemplate(false)
public final class ReusableInputGraphCaptureGameTest {

    private ReusableInputGraphCaptureGameTest() {}

    @TestHolder("reusable_graph_capture_defers_planning_merges_targets_and_restarts_changed_models")
    @EmptyTemplate("5")
    @GameTest(template = "empty_5x5")
    public static void defersPlanningMergesTargetsAndRestartsChangedModels(GameTestHelper helper) throws Exception {
        TestSource source = new TestSource(helper, false, false);
        AtomicLong clock = new AtomicLong();
        ReusableInputGraphCaptureService service = new ReusableInputGraphCaptureService(source, clock::getAndIncrement);
        var limits = new TrinityPlanningLimits(16, 8, 64, 1000);
        var actor = new BaseActionSource();
        var skipped = service.submit(helper.getLevel(), actor, AEItemKey.of(Items.DIAMOND), List.of(), limits);
        helper.assertTrue(skipped.isDone(), "No registered rules must retain the immediate legacy path");
        source.enabled = true;
        var capture = service.submit(helper.getLevel(), actor, AEItemKey.of(Items.DIAMOND), List.of(tool(1)), limits);
        AtomicInteger planningStarts = new AtomicInteger();
        var future = new CapturedPlanningFuture<>(capture, result -> {
            helper.assertTrue(result.successful(), "Capture should complete without truncating the model");
            planningStarts.incrementAndGet();
            return CompletableFuture.completedFuture(result.value());
        });
        helper.assertValueEqual(planningStarts.get(), 0, "Background planning cannot start with an incomplete capture");
        int advances = 0;
        while (source.callbacks == 0 && advances++ < 1000) {
            service.advance(8L);
        }
        helper.assertTrue(source.callbacks > 0 && !future.isDone(), "Tiny tick slices must retain an active partial capture");
        source.epoch++;
        while (!future.isDone() && advances++ < 5000) {
            service.advance(8L);
        }
        helper.assertTrue(future.isDone(), "Retained cursor progress must eventually finish across ticks");
        var graph = future.get();
        var bindings = graph.patterns().getFirst().reusableBindings();
        helper.assertValueEqual(bindings.size(), 2, "Equivalent models from two concrete targets are deduplicated");
        helper.assertValueEqual(source.modes.size(), 2, "Rules receive both actual target modes");
        for (var assignment : bindings) {
            helper.assertValueEqual(assignment.getFirst().reusableRule().revision(), 1L,
                    "A changed epoch must discard all model values captured before the restart");
        }
        helper.assertValueEqual(planningStarts.get(), 1, "Only the complete immutable graph starts planning");
        var cancelledCapture = service.submit(helper.getLevel(), actor, AEItemKey.of(Items.DIAMOND), List.of(), limits);
        var cancelled = new CapturedPlanningFuture<>(cancelledCapture, ignored -> {
            throw new IllegalStateException("Cancelled capture must never start planning");
        });
        helper.assertTrue(cancelled.cancel(false) && cancelledCapture.isCancelled(), "Cancellation reaches pending server capture");
        service.advance(8L);
        helper.succeed();
    }

    @TestHolder("reusable_graph_capture_fallback_discards_whole_pattern_and_retains_reason")
    @EmptyTemplate("5")
    @GameTest(template = "empty_5x5")
    public static void fallbackDiscardsWholePatternAndRetainsReason(GameTestHelper helper) throws Exception {
        for (boolean alias : new boolean[] { false, true }) {
            TestSource source = new TestSource(helper, alias, !alias);
            source.enabled = true;
            AtomicLong clock = new AtomicLong();
            var service = new ReusableInputGraphCaptureService(source, clock::getAndIncrement);
            var capture = service.submit(helper.getLevel(), new BaseActionSource(), AEItemKey.of(Items.DIAMOND), List.of(),
                    new TrinityPlanningLimits(16, alias ? 8 : 1, 64, 1000));
            for (int tick = 0; tick < 5000 && !capture.isDone(); tick++) {
                service.advance(8L);
            }
            helper.assertTrue(capture.isDone(), "A pattern fallback must let request capture finish");
            var result = capture.get();
            helper.assertTrue(result.successful(), "Expansion limits and alias mixtures downgrade only their pattern");
            var graph = result.value();
            helper.assertValueEqual(graph.patterns(), source.graph.patterns(), "No earlier target's partial expanded model may survive fallback");
            var identity = source.graph.patterns().getFirst().identity();
            var diagnostic = graph.reusableInputFallbacks().get(identity);
            helper.assertTrue(diagnostic != null, "Fallback must retain the immutable original pattern identity and reason");
            helper.assertValueEqual(diagnostic.code(), alias ? TrinityPlanningDiagnosticCode.UNSUPPORTED_PATTERN : TrinityPlanningDiagnosticCode.VARIANT_LIMIT,
                    "Alias fallback and binding limits retain distinct diagnostic codes");
            helper.assertValueEqual(diagnostic.metadata().get("reason"), alias ? "component_aliased_material" : "capture_binding_limit",
                    "Fallback cause is available after capture without relying on debug logs");
            helper.assertValueEqual(diagnostic.metadata().get("action"), "legacy_pattern", "Metadata distinguishes fallback from request rejection");
            helper.assertValueEqual(graph.reachableSubgraph(AEItemKey.of(Items.DIAMOND)).reusableInputFallbacks().get(identity), diagnostic,
                    "Reachable graph projection retains fallback evidence");
            if (!alias) {
                helper.assertValueEqual(source.modes.size(), 2, "A later target must erase the earlier target's accepted partial expansion");
            }
        }
        helper.succeed();
    }

    private static final class TestSource implements ReusableInputGraphCaptureService.Source {

        private final TestPattern pattern;
        private final IdentityCraftingProviderPublicationIndex publications = new IdentityCraftingProviderPublicationIndex();
        private final TrinityCraftingGraphSnapshot graph;
        private final ReusableInputRules rules;
        private final ObjectOpenHashSet<ResourceLocation> modes = new ObjectOpenHashSet<>();
        private boolean enabled;
        private long epoch;
        private int callbacks;

        private TestSource(GameTestHelper helper, boolean aliasMaterial, boolean firstTargetUnchanged) {
            pattern = new TestPattern(aliasMaterial);
            publications.publish(new TestProvider(pattern), List.of(pattern));
            var publication = TrinityPatternPublicationSignature.capture(pattern);
            graph = new TrinityCraftingGraphSnapshot(publications.publicationRevision(), List.of(new TrinityCraftingGraphPattern(
                    TrinityPatternIdentity.capture(publication, helper.getLevel().registryAccess()), publication)));
            rules = context -> {
                helper.assertTrue(helper.getLevel().getServer().isSameThread(), "All rule callbacks must remain on the server");
                helper.assertFalse(context.target().providerScoped(), "Capture must use a real recoverable target");
                modes.add(context.machineMode().orElseThrow());
                callbacks++;
                if (context.inputSlot() != 0) {
                    return Optional.empty();
                }
                ResourceLocation id = ResourceLocation.fromNamespaceAndPath(Data_Energistics.MODID, "capture_test");
                if (firstTargetUnchanged && context.machineMode().orElseThrow().getPath().equals("first")) {
                    return Optional.of(ReusableInputRule.unchanged(id, epoch, (AEItemKey) context.actualInput().what()));
                }
                return Optional.of(ReusableInputRule.fixedDamage(id,
                        epoch, (AEItemKey) context.actualInput().what(), 1, 2, List.of()));
            };
        }

        @Override
        public Optional<TrinityCraftingGraphSnapshot> graph() {
            return Optional.of(graph);
        }

        @Override
        public CraftingProviderPublicationIndex publications() {
            return publications;
        }

        @Override
        public List<IPatternDetails> patternsFor(AEKey primaryOutput) {
            return List.of(pattern);
        }

        @Override
        public List<AEItemKey> visibleItemKeys() {
            return List.of(tool(0));
        }

        @Override
        public ReusableInputRules rules() {
            return rules;
        }

        @Override
        public boolean hasRules() {
            return enabled;
        }

        @Override
        public long modelEpoch() {
            return epoch;
        }

        @Override
        public Optional<ResourceLocation> recipeId(IPatternDetails pattern) {
            return Optional.empty();
        }
    }

    private record TestProvider(IPatternDetails pattern) implements ICraftingProvider, ReusableCraftingProviderAdapter {

        @Override
        public List<IPatternDetails> getAvailablePatterns() {
            return List.of(pattern);
        }

        @Override
        public boolean isBusy() {
            return false;
        }

        @Override
        public List<Target> reusableTargets(IPatternDetails pattern, IActionSource source, ServerLevel level) {
            return List.of(target("second"), target("first"));
        }

        private static Target target(String mode) {
            return new Target(mode, CountedCraftingTarget.route(mode), Optional.of(ResourceLocation.fromNamespaceAndPath(Data_Energistics.MODID, mode)));
        }

        @Override
        public boolean pushPattern(IPatternDetails pattern, KeyCounter[] inputs) {
            throw mutation();
        }

        @Override
        public CountedCraftingAdmission prepareBatch(IPatternDetails pattern, KeyCounter[] prototype, long count) {
            throw mutation();
        }

        @Override
        public ReusableCraftingAdmission prepareReusable(ReusableCraftingRequest request) {
            throw mutation();
        }

        @Override
        public boolean requestReusableYield(ReusableCraftingRequest contender) {
            throw mutation();
        }

        @Override
        public Optional<ReusableCraftingSessionView> reusableSession(UUID sessionId) {
            throw mutation();
        }

        @Override
        public Optional<ReusableCraftingSessionView.AppendReceipt> reusableReceipt(UUID sessionId, long sequence) {
            throw mutation();
        }

        @Override
        public void closeReusableSession(UUID sessionId) {
            throw mutation();
        }

        @Override
        public boolean settleReusableSession(UUID sessionId, ReturnReceiver receiver) {
            throw mutation();
        }

        private static IllegalStateException mutation() {
            return new IllegalStateException("Planning capture must not mutate a provider");
        }
    }

    private record TestPattern(boolean aliasMaterial) implements IPatternDetails {

        @Override
        public AEItemKey getDefinition() {
            ItemStack definition = new ItemStack(Items.CRAFTING_TABLE);
            EncodedPatternDynamicOutput.apply(definition, aliasMaterial);
            return AEItemKey.of(definition);
        }

        @Override
        public List<GenericStack> getOutputs() {
            return List.of(new GenericStack(AEItemKey.of(Items.DIAMOND), 1L));
        }

        @Override
        public IInput[] getInputs() {
            return aliasMaterial ? new IInput[] { new ToolInput(), new MaterialInput() } : new IInput[] { new ToolInput() };
        }
    }

    private record ToolInput() implements IPatternDetails.IInput {

        @Override
        public GenericStack[] getPossibleInputs() {
            return new GenericStack[] { new GenericStack(tool(0), 1L) };
        }

        @Override
        public long getMultiplier() {
            return 1L;
        }

        @Override
        public boolean isValid(AEKey key, Level level) {
            return key instanceof AEItemKey item && item.getItem() == Items.WOODEN_AXE;
        }

        @Override
        public AEKey getRemainingKey(AEKey template) {
            return null;
        }
    }

    private record MaterialInput() implements IPatternDetails.IInput {

        @Override
        public GenericStack[] getPossibleInputs() {
            return new GenericStack[] { new GenericStack(AEItemKey.of(Items.DIAMOND), 1L) };
        }

        @Override
        public long getMultiplier() {
            return 1L;
        }

        @Override
        public boolean isValid(AEKey key, Level level) {
            return key.equals(AEItemKey.of(Items.DIAMOND));
        }

        @Override
        public AEKey getRemainingKey(AEKey template) {
            return null;
        }
    }

    private static AEItemKey tool(int damage) {
        ItemStack stack = new ItemStack(Items.WOODEN_AXE);
        stack.set(DataComponents.DAMAGE, damage);
        return AEItemKey.of(stack);
    }
}

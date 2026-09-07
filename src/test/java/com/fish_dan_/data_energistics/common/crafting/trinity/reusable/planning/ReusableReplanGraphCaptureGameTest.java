package com.fish_dan_.data_energistics.common.crafting.trinity.reusable.planning;

import com.fish_dan_.data_energistics.Data_Energistics;
import com.fish_dan_.data_energistics.api.crafting.reusable.ReusableInputRule;
import com.fish_dan_.data_energistics.common.crafting.trinity.planning.TrinityPlanningDiagnostic;
import com.fish_dan_.data_energistics.common.crafting.trinity.planning.TrinityPlanningDiagnosticCode;
import com.fish_dan_.data_energistics.common.crafting.trinity.planning.algorithm.TrinityAlgorithmResult;
import com.fish_dan_.data_energistics.common.crafting.trinity.planning.graph.TrinityBoundPatternInput;
import com.fish_dan_.data_energistics.common.crafting.trinity.planning.graph.TrinityCraftingGraphPattern;
import com.fish_dan_.data_energistics.common.crafting.trinity.planning.graph.TrinityCraftingGraphSnapshot;
import com.fish_dan_.data_energistics.common.crafting.trinity.planning.graph.TrinityPatternIdentity;
import com.fish_dan_.data_energistics.common.crafting.trinity.reusable.planning.ReusableReplanGraphCapture.Fault;
import com.fish_dan_.data_energistics.common.crafting.trinity.reusable.planning.ReusableReplanGraphCapture.Ready;
import com.fish_dan_.data_energistics.common.crafting.trinity.reusable.planning.ReusableReplanGraphCapture.Rejected;
import com.fish_dan_.data_energistics.common.crafting.trinity.reusable.planning.ReusableReplanGraphCapture.Waiting;
import com.fish_dan_.data_energistics.common.trinity.pattern.TrinityPatternPublicationSignature;
import com.fish_dan_.data_energistics.common.trinity.pattern.TrinityPatternPublicationSignature.Alternative;
import com.fish_dan_.data_energistics.common.trinity.pattern.TrinityPatternPublicationSignature.Input;

import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.GenericStack;

import net.minecraft.core.component.DataComponents;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import net.neoforged.testframework.annotation.TestHolder;
import net.neoforged.testframework.gametest.EmptyTemplate;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.function.Supplier;

@GameTestHolder(Data_Energistics.MODID)
@PrefixGameTestTemplate(false)
public final class ReusableReplanGraphCaptureGameTest {

    private static final ResourceLocation RULE = ResourceLocation.fromNamespaceAndPath(Data_Energistics.MODID, "replan_capture_tool");

    private ReusableReplanGraphCaptureGameTest() {}

    @TestHolder("reusable_replan_capture_waits_without_blocking_and_caches_complete_expanded_graph")
    @EmptyTemplate("5")
    @GameTest(template = "empty_5x5")
    public static void waitsWithoutBlockingAndCachesCompleteExpandedGraph(GameTestHelper helper) {
        ReusableReplanGraphCapture capture = new ReusableReplanGraphCapture();
        CompletableFuture<TrinityAlgorithmResult<TrinityCraftingGraphSnapshot>> future = new CompletableFuture<>();
        int[] requests = { 0 };
        Supplier<CompletableFuture<TrinityAlgorithmResult<TrinityCraftingGraphSnapshot>>> factory = () -> {
            requests[0]++;
            return future;
        };
        helper.assertTrue(capture.advance(1, 7, 0, 20, factory) instanceof Waiting, "Incomplete future does not block the first server tick");
        helper.assertTrue(capture.advance(1, 7, 1, 20, factory) instanceof Waiting, "No partial or raw graph is returned while capture is pending");
        helper.assertValueEqual(requests[0], 1, "CPU key supplier is called only when starting capture");
        TrinityCraftingGraphSnapshot expanded = graph(7, helper);
        future.complete(TrinityAlgorithmResult.success(expanded));
        var completed = capture.advance(1, 7, 2, 20, factory);
        helper.assertTrue(completed instanceof Ready ready && ready.snapshot() == expanded, "The exact complete expanded snapshot is published");
        helper.assertValueEqual(((Ready) completed).snapshot().patternsProducing(tool(2)).size(), 1,
                "Captured tool successor transitions remain available for remaining-plan calculation");
        helper.assertTrue(capture.advance(1, 7, 100, 1, factory) == completed, "Successful graph is cached even after retry settings change");
        helper.assertValueEqual(requests[0], 1, "Cached success does not recapture live CPU keys");
        helper.succeed();
    }

    @TestHolder("reusable_replan_capture_invalidates_pending_and_ready_results_on_revision_or_scope_change")
    @EmptyTemplate("5")
    @GameTest(template = "empty_5x5")
    public static void invalidatesPendingAndReadyResultsOnRevisionOrScopeChange(GameTestHelper helper) {
        ReusableReplanGraphCapture capture = new ReusableReplanGraphCapture();
        CompletableFuture<TrinityAlgorithmResult<TrinityCraftingGraphSnapshot>> old = new CompletableFuture<>();
        CompletableFuture<TrinityAlgorithmResult<TrinityCraftingGraphSnapshot>> next = new CompletableFuture<>();
        capture.advance(3, 1, 0, 20, () -> old);
        helper.assertTrue(capture.advance(3, 2, 1, 20, () -> next) instanceof Waiting, "New revision begins its own capture");
        helper.assertTrue(old.isCancelled(), "Revision change cancels the obsolete future");
        helper.assertTrue(!old.complete(TrinityAlgorithmResult.success(graph(1, helper))), "Late obsolete completion cannot become usable");
        TrinityCraftingGraphSnapshot revisionTwo = graph(2, helper);
        next.complete(TrinityAlgorithmResult.success(revisionTwo));
        Ready ready = (Ready) capture.advance(3, 2, 2, 20, () -> next);
        helper.assertTrue(ready.snapshot() == revisionTwo, "Only the new revision is installed");
        CompletableFuture<TrinityAlgorithmResult<TrinityCraftingGraphSnapshot>> otherScope = new CompletableFuture<>();
        helper.assertTrue(capture.advance(4, 2, 3, 20, () -> otherScope) instanceof Waiting,
                "Equal numeric revisions from another grid scope cannot reuse the prior graph");
        TrinityCraftingGraphSnapshot replacement = graph(2, helper);
        otherScope.complete(TrinityAlgorithmResult.success(replacement));
        Ready changed = (Ready) capture.advance(4, 2, 4, 20, () -> otherScope);
        helper.assertTrue(changed.snapshot() == replacement && changed.publicationScope() == 4, "Ready result belongs to the new publication scope");
        helper.succeed();
    }

    @TestHolder("reusable_replan_capture_cancel_discards_already_completed_unobserved_results_and_cached_success")
    @EmptyTemplate("5")
    @GameTest(template = "empty_5x5")
    public static void cancelDiscardsAlreadyCompletedUnobservedResultsAndCachedSuccess(GameTestHelper helper) {
        ReusableReplanGraphCapture capture = new ReusableReplanGraphCapture();
        CompletableFuture<TrinityAlgorithmResult<TrinityCraftingGraphSnapshot>> old = new CompletableFuture<>();
        capture.advance(1, 5, 0, 20, () -> old);
        old.complete(TrinityAlgorithmResult.success(graph(5, helper)));
        capture.cancel();
        CompletableFuture<TrinityAlgorithmResult<TrinityCraftingGraphSnapshot>> fresh = new CompletableFuture<>();
        helper.assertTrue(capture.advance(1, 5, 1, 20, () -> fresh) instanceof Waiting,
                "An old completed future cannot repopulate state cleared by cancel or plan installation");
        TrinityCraftingGraphSnapshot expected = graph(5, helper);
        fresh.complete(TrinityAlgorithmResult.success(expected));
        helper.assertTrue(((Ready) capture.advance(1, 5, 2, 20, () -> fresh)).snapshot() == expected, "New attempt publishes its own result");
        capture.cancel();
        CompletableFuture<TrinityAlgorithmResult<TrinityCraftingGraphSnapshot>> pending = new CompletableFuture<>();
        helper.assertTrue(capture.advance(1, 5, 3, 20, () -> pending) instanceof Waiting, "Cancellation also invalidates cached ready graphs");
        capture.cancel();
        helper.assertTrue(pending.isCancelled(), "Closing a CPU cancels any still-pending capture");
        helper.succeed();
    }

    @TestHolder("reusable_replan_capture_retains_diagnostics_and_retries_with_capped_backoff")
    @EmptyTemplate("5")
    @GameTest(template = "empty_5x5")
    public static void retainsDiagnosticsAndRetriesWithCappedBackoff(GameTestHelper helper) {
        ReusableReplanGraphCapture capture = new ReusableReplanGraphCapture();
        TrinityPlanningDiagnostic diagnostic = new TrinityPlanningDiagnostic(TrinityPlanningDiagnosticCode.PLANNER_QUEUE_FULL,
                Component.literal("capture queue is full"), Map.of("source", "controlled capture"));
        int[] requests = { 0 };
        Supplier<CompletableFuture<TrinityAlgorithmResult<TrinityCraftingGraphSnapshot>>> failing = () -> {
            requests[0]++;
            return CompletableFuture.completedFuture(TrinityAlgorithmResult.failure(diagnostic));
        };
        Rejected first = (Rejected) capture.advance(1, 1, 10, 4, failing);
        helper.assertValueEqual(first.retryAtTick(), 11L, "First retry waits one tick");
        helper.assertTrue(first.diagnostic() == diagnostic, "Original structured diagnostic is retained");
        helper.assertTrue(capture.advance(1, 1, 10, 4, failing) == first, "Cooldown returns the same failure identity without another request");
        Rejected second = (Rejected) capture.advance(1, 1, 11, 4, failing);
        helper.assertValueEqual(second.retryAtTick(), 13L, "Second failure waits two ticks");
        helper.assertTrue(capture.advance(1, 1, 12, 4, failing) == second, "Intermediate ticks cannot spin new capture attempts");
        Rejected third = (Rejected) capture.advance(1, 1, 13, 4, failing);
        helper.assertValueEqual(third.retryAtTick(), 17L, "Backoff grows to the configured cap");
        Rejected fourth = (Rejected) capture.advance(1, 1, 17, 4, failing);
        helper.assertValueEqual(fourth.retryAtTick(), 21L, "Further retries remain capped");
        helper.assertValueEqual(requests[0], 4, "Only due retries recapture CPU item candidates");
        var changed = capture.advance(1, 2, 18, 4, () -> CompletableFuture.completedFuture(TrinityAlgorithmResult.success(graph(2, helper))));
        helper.assertTrue(changed instanceof Ready, "Publication change clears an obsolete failure cooldown immediately");
        helper.succeed();
    }

    @TestHolder("reusable_replan_capture_exceptions_and_producer_cancellation_remain_explicit")
    @EmptyTemplate("5")
    @GameTest(template = "empty_5x5")
    public static void exceptionsAndProducerCancellationRemainExplicit(GameTestHelper helper) {
        ReusableReplanGraphCapture capture = new ReusableReplanGraphCapture();
        IllegalStateException supplierFailure = new IllegalStateException("CPU key capture failed");
        Fault first = (Fault) capture.advance(2, 1, 0, 4, () -> { throw supplierFailure; });
        helper.assertTrue(first.cause() == supplierFailure, "Supplier failure is returned without replacing its cause");
        IllegalArgumentException asyncFailure = new IllegalArgumentException("capture task failed");
        Fault second = (Fault) capture.advance(2, 1, 1, 4, () -> CompletableFuture.failedFuture(asyncFailure));
        helper.assertTrue(second.cause() == asyncFailure, "Exceptional completion exposes its original cause");
        CompletableFuture<TrinityAlgorithmResult<TrinityCraftingGraphSnapshot>> cancelled = new CompletableFuture<>();
        cancelled.cancel(false);
        Fault third = (Fault) capture.advance(2, 1, 3, 4, () -> cancelled);
        helper.assertTrue(third.cause() instanceof CancellationException, "Unexpected producer cancellation is an explicit retryable failure");
        helper.assertValueEqual(third.retryAtTick(), 7L, "Producer cancellation remains subject to capped retry backoff");
        helper.succeed();
    }

    @TestHolder("reusable_replan_capture_rejects_stale_success_and_keeps_cpu_owners_independent")
    @EmptyTemplate("5")
    @GameTest(template = "empty_5x5")
    public static void rejectsStaleSuccessAndKeepsCpuOwnersIndependent(GameTestHelper helper) {
        ReusableReplanGraphCapture first = new ReusableReplanGraphCapture();
        ReusableReplanGraphCapture second = new ReusableReplanGraphCapture();
        Rejected stale = (Rejected) first.advance(1, 4, 20, 20,
                () -> CompletableFuture.completedFuture(TrinityAlgorithmResult.success(graph(3, helper))));
        helper.assertValueEqual(stale.diagnostic().code(), TrinityPlanningDiagnosticCode.STALE_GRAPH, "Wrong-revision success is rejected explicitly");
        helper.assertValueEqual(stale.retryAtTick(), 21L, "Stale capture is retried after a bounded delay");
        TrinityCraftingGraphSnapshot secondGraph = graph(4, helper);
        Ready independent = (Ready) second.advance(1, 4, 20, 20,
                () -> CompletableFuture.completedFuture(TrinityAlgorithmResult.success(secondGraph)));
        helper.assertTrue(independent.snapshot() == secondGraph, "Another CPU can complete independently on the same publication");
        TrinityCraftingGraphSnapshot firstGraph = graph(4, helper);
        Ready retried = (Ready) first.advance(1, 4, 21, 20,
                () -> CompletableFuture.completedFuture(TrinityAlgorithmResult.success(firstGraph)));
        helper.assertTrue(retried.snapshot() == firstGraph && retried.snapshot() != independent.snapshot(), "Each CPU retains its own complete capture");
        helper.succeed();
    }

    private static TrinityCraftingGraphSnapshot graph(long revision, GameTestHelper helper) {
        ReusableInputRule zero = ReusableInputRule.fixedDamage(RULE, 1, tool(0), 1, 3, List.of());
        ReusableInputRule one = ReusableInputRule.fixedDamage(RULE, 1, tool(1), 1, 3, List.of());
        TrinityBoundPatternInput first = new TrinityBoundPatternInput(0, 0, new GenericStack(tool(0), 1), 1, tool(1), zero, List.of());
        TrinityBoundPatternInput second = new TrinityBoundPatternInput(0, 0, new GenericStack(tool(1), 1), 1, tool(2), one, List.of());
        TrinityPatternPublicationSignature signature = new TrinityPatternPublicationSignature(AEItemKey.of(Items.CRAFTING_TABLE),
                List.of(new Input(1, List.of(new Alternative(new GenericStack(tool(0), 1), null)))),
                List.of(new GenericStack(AEItemKey.of(Items.IRON_NUGGET), 1)), false);
        TrinityCraftingGraphPattern pattern = new TrinityCraftingGraphPattern(TrinityPatternIdentity.capture(signature, helper.getLevel().registryAccess()),
                signature, List.of(List.of(first), List.of(second)));
        return new TrinityCraftingGraphSnapshot(revision, List.of(pattern));
    }

    private static AEItemKey tool(int damage) {
        ItemStack stack = new ItemStack(Items.IRON_AXE);
        stack.set(DataComponents.DAMAGE, damage);
        return AEItemKey.of(stack);
    }
}

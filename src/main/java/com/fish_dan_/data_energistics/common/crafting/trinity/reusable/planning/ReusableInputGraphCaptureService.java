package com.fish_dan_.data_energistics.common.crafting.trinity.reusable.planning;

import com.fish_dan_.data_energistics.Data_Energistics;
import com.fish_dan_.data_energistics.api.crafting.reusable.ReusableInputContext;
import com.fish_dan_.data_energistics.api.crafting.reusable.dispatch.ReusableCraftingProviderAdapter;
import com.fish_dan_.data_energistics.api.crafting.reusable.dispatch.ReusableCraftingRequest.Target;
import com.fish_dan_.data_energistics.api.registry.reusable.ReusableInputRules;
import com.fish_dan_.data_energistics.common.crafting.trinity.dispatch.provider.CountedCraftingProviderAdapters;
import com.fish_dan_.data_energistics.common.crafting.trinity.dispatch.provider.CraftingProviderPublicationIndex;
import com.fish_dan_.data_energistics.common.crafting.trinity.planning.TrinityPlanningDiagnostic;
import com.fish_dan_.data_energistics.common.crafting.trinity.planning.TrinityPlanningDiagnosticCode;
import com.fish_dan_.data_energistics.common.crafting.trinity.planning.algorithm.TrinityAlgorithmResult;
import com.fish_dan_.data_energistics.common.crafting.trinity.planning.algorithm.TrinityPlanningControl;
import com.fish_dan_.data_energistics.common.crafting.trinity.planning.graph.TrinityBoundPatternInput;
import com.fish_dan_.data_energistics.common.crafting.trinity.planning.graph.TrinityCraftingGraphPattern;
import com.fish_dan_.data_energistics.common.crafting.trinity.planning.graph.TrinityCraftingGraphSnapshot;
import com.fish_dan_.data_energistics.common.crafting.trinity.planning.graph.TrinityPatternIdentity;
import com.fish_dan_.data_energistics.common.crafting.trinity.planning.request.TrinityPlanningLimits;
import com.fish_dan_.data_energistics.common.crafting.trinity.planning.sameitem.TrinitySameItemPolicy;
import com.fish_dan_.data_energistics.common.trinity.pattern.TrinityPatternPublicationSignature;

import appeng.api.crafting.IPatternDetails;
import appeng.api.networking.security.IActionSource;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.GenericStack;

import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;

import it.unimi.dsi.fastutil.objects.Object2ObjectLinkedOpenHashMap;
import it.unimi.dsi.fastutil.objects.ObjectArrayFIFOQueue;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import it.unimi.dsi.fastutil.objects.ObjectLinkedOpenHashSet;
import org.jspecify.annotations.Nullable;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.function.LongSupplier;

/** Per-grid fair queue of request-local rule captures. All world reads and future completion run on the server. */
public final class ReusableInputGraphCaptureService {

    /**
     * Live server-thread capture boundary. Every method is read-only and called on the owning server
     * thread. Returned pattern/provider references may survive only inside the active capture task;
     * returned graph and rule values must already be immutable. Model changes must advance modelEpoch
     * or replace the frozen rules lookup so a capture can reject mixed generations.
     */
    public interface Source {

        /** @return latest complete graph, or empty while no publication has finished rebuilding */
        Optional<TrinityCraftingGraphSnapshot> graph();

        /**
         * @return live identity index used to verify revisions and resolve providers without advancing routing cursors
         */
        CraftingProviderPublicationIndex publications();

        /** @return stable list snapshot of live patterns advertised for this exact primary output */
        List<IPatternDetails> patternsFor(AEKey primaryOutput);

        /**
         * @return snapshot of visible physical item keys, without asserting available quantity or extraction rights
         *         This enumeration occurs once per capture generation. Its current synchronous network-copy cost is
         *         separate from the resumable rule-expansion cursor and must be included in provider budget audits.
         */
        List<AEItemKey> visibleItemKeys();

        /** @return frozen rule lookup with stable object identity until registrations change */
        ReusableInputRules rules();

        /** @return whether the current frozen lookup contains any registered rule sources */
        boolean hasRules();

        /** @return monotonic generation covering recipe/tag and other rule-model changes */
        long modelEpoch();

        /** @return authoritative recipe identity for this live pattern, or empty when no resolver proves one */
        Optional<ResourceLocation> recipeId(IPatternDetails pattern);
    }

    private final Source source;
    private final LongSupplier nanoClock;
    private final ObjectArrayFIFOQueue<Task> pending = new ObjectArrayFIFOQueue<>();

    public ReusableInputGraphCaptureService(Source source, LongSupplier nanoClock) {
        this.source = source;
        this.nanoClock = nanoClock;
    }

    public CompletableFuture<TrinityAlgorithmResult<TrinityCraftingGraphSnapshot>> submit(
                                                                                          ServerLevel level, IActionSource actor, AEKey target, List<AEItemKey> additionalStates,
                                                                                          TrinityPlanningLimits limits) {
        Optional<TrinityCraftingGraphSnapshot> graph = source.graph();
        if (!source.hasRules() && graph.isPresent()) {
            return CompletableFuture.completedFuture(TrinityAlgorithmResult.success(graph.orElseThrow()));
        }
        Task task = new Task(level, actor, target, additionalStates, limits);
        pending.enqueue(task);
        return task.future;
    }

    /** Spends one shared tick allowance; unfinished requests rotate rather than restarting their cursor. */
    public void advance(long budgetNanos) {
        long started = nanoClock.getAsLong();
        int requests = pending.size();
        while (requests-- > 0 && !pending.isEmpty()) {
            long remaining = budgetNanos - (nanoClock.getAsLong() - started);
            if (remaining <= 0L) {
                return;
            }
            Task task = pending.dequeue();
            if (!task.future.isDone()) {
                try {
                    task.advance(remaining);
                } catch (RuntimeException exception) {
                    Data_Energistics.LOGGER.error("Reusable planning capture failed for {}", task.target, exception);
                    task.future.complete(failure(TrinityPlanningDiagnosticCode.INTERNAL_ERROR, "internal_error", exception.getClass().getSimpleName()));
                }
                if (!task.future.isDone()) {
                    pending.enqueue(task);
                }
            }
        }
    }

    /** Called when the grid unloads; no active task may later resurrect work in that grid. */
    public void clear() {
        while (!pending.isEmpty()) {
            pending.dequeue().future.cancel(false);
        }
    }

    private final class Task {

        private final ServerLevel level;
        private final IActionSource actor;
        private final AEKey target;
        private final List<AEItemKey> additionalStates;
        private final TrinityPlanningLimits limits;
        private final CompletableFuture<TrinityAlgorithmResult<TrinityCraftingGraphSnapshot>> future = new CompletableFuture<>();
        private final TrinityPlanningControl control;
        private @Nullable TrinityCraftingGraphSnapshot base;
        private ReusableInputRules rules;
        private long epoch;
        private List<AEItemKey> inventory = List.of();
        private boolean inventoryCaptured;
        private final List<TrinityCraftingGraphPattern> completed = new ObjectArrayList<>();
        private final List<List<Endpoint>> completedEndpoints = new ObjectArrayList<>();
        private final Map<TrinityPatternIdentity, TrinityPlanningDiagnostic> fallbacks = new Object2ObjectLinkedOpenHashMap<>();
        private final ObjectLinkedOpenHashSet<List<TrinityBoundPatternInput>> merged = new ObjectLinkedOpenHashSet<>();
        private List<Endpoint> endpoints = List.of();
        private int patternIndex;
        private int endpointIndex;
        private int expandedCount;
        private int validationIndex;
        private @Nullable ReusableInputPlanningCursor cursor;
        private @Nullable TrinityPlanningDiagnostic patternFallback;

        private Task(ServerLevel level, IActionSource actor, AEKey target, List<AEItemKey> additionalStates,
                     TrinityPlanningLimits limits) {
            this.level = level;
            this.actor = actor;
            this.target = target;
            this.additionalStates = List.copyOf(additionalStates);
            this.limits = limits;
            this.rules = source.rules();
            this.control = TrinityPlanningControl.create(future::isCancelled, nanoClock,
                    TimeUnit.MILLISECONDS.toNanos(limits.planningBudgetMs()));
        }

        private void advance(long slice) {
            long started = nanoClock.getAsLong();
            long remaining = slice;
            while (!future.isDone() && remaining > 0L) {
                var beforeBase = base;
                int beforePattern = patternIndex;
                int beforeEndpoint = endpointIndex;
                int beforeValidation = validationIndex;
                var beforeEndpoints = endpoints;
                var beforeCursor = cursor;
                step(remaining);
                remaining = slice - (nanoClock.getAsLong() - started);
                if (beforeBase == base && beforePattern == patternIndex && beforeEndpoint == endpointIndex &&
                        beforeValidation == validationIndex && beforeEndpoints == endpoints && beforeCursor == cursor) {
                    return;
                }
            }
        }

        private void step(long slice) {
            if (control.deadlineExceeded()) {
                future.complete(failure(TrinityPlanningDiagnosticCode.MIP_TIMEOUT, "timeout", "capture_deadline"));
                return;
            }
            Optional<TrinityCraftingGraphSnapshot> current = source.graph();
            if (current.isEmpty() || current.orElseThrow().revision() != source.publications().publicationRevision()) {
                return;
            }
            if (base == null || base.revision() != current.orElseThrow().revision() || epoch != source.modelEpoch() || rules != source.rules()) {
                restart(current.orElseThrow());
            }
            if (!source.hasRules()) {
                future.complete(TrinityAlgorithmResult.success(base));
                return;
            }
            if (patternIndex == base.patterns().size()) {
                if (validationIndex < completedEndpoints.size()) {
                    if (!completedEndpoints.get(validationIndex).equals(discover(base.patterns().get(validationIndex)))) {
                        restart(current.orElseThrow());
                    } else {
                        validationIndex++;
                    }
                    return;
                }
                future.complete(TrinityAlgorithmResult.success(new TrinityCraftingGraphSnapshot(base.revision(), completed, fallbacks)));
                return;
            }
            TrinityCraftingGraphPattern pattern = base.patterns().get(patternIndex);
            if (cursor != null) {
                ReusableInputPlanningExpansion.Result result = cursor.advance(slice, nanoClock);
                if (result != null) {
                    acceptCapture(result);
                }
                return;
            }
            if (endpointIndex < endpoints.size()) {
                if (!inventoryCaptured) {
                    ObjectLinkedOpenHashSet<AEItemKey> states = new ObjectLinkedOpenHashSet<>(source.visibleItemKeys());
                    states.addAll(additionalStates);
                    inventory = List.copyOf(states);
                    inventoryCaptured = true;
                    return;
                }
                Endpoint endpoint = endpoints.get(endpointIndex);
                List<GenericStack> actual = new ObjectArrayList<>(pattern.inputs().size());
                int firstItem = -1;
                for (int slot = 0; slot < pattern.inputs().size(); slot++) {
                    var input = pattern.inputs().get(slot);
                    GenericStack template = input.alternatives().getFirst().stack();
                    actual.add(new GenericStack(template.what(), Math.multiplyExact(template.amount(), input.multiplier())));
                    if (firstItem < 0 && template.what() instanceof AEItemKey) {
                        firstItem = slot;
                    }
                }
                if (firstItem < 0) {
                    endpointIndex = endpoints.size();
                    return;
                }
                cursor = new ReusableInputPlanningCursor(ReusableInputContext.builder()
                        .pattern(endpoint.pattern()).actualInput(actual.get(firstItem)).exactInputs(actual).inputSlot(firstItem)
                        .ownership(ReusableInputContext.Ownership.CPU_SUPPLIED).actionSource(actor).level(level)
                        .recipeId(endpoint.recipeId()).machineMode(endpoint.target().mode()).target(endpoint.target().route()).build(),
                        inventory, rules, limits.maxBindingVariants(), control);
                return;
            }
            if (endpoints.isEmpty() && endpointIndex == 0) {
                endpoints = discover(pattern);
                if (!endpoints.isEmpty()) {
                    return;
                }
            } else if (!endpoints.equals(discover(pattern))) {
                restart(current.orElseThrow());
                return;
            }
            if (merged.size() > limits.maxBindingVariants() - expandedCount) {
                retainLegacy(TrinityPlanningDiagnosticCode.VARIANT_LIMIT, "variant_limit", "request_binding_limit");
            }
            if (patternFallback != null) {
                fallbacks.put(pattern.identity(), patternFallback);
            }
            expandedCount += merged.size();
            completed.add(patternFallback != null ? new TrinityCraftingGraphPattern(pattern.identity(), pattern.publication()) :
                    merged.isEmpty() ? pattern : new TrinityCraftingGraphPattern(pattern.identity(), pattern.publication(), List.copyOf(merged)));
            completedEndpoints.add(endpoints);
            patternIndex++;
            endpoints = List.of();
            endpointIndex = 0;
            merged.clear();
            patternFallback = null;
        }

        private void restart(TrinityCraftingGraphSnapshot graph) {
            base = graph;
            rules = source.rules();
            epoch = source.modelEpoch();
            inventory = List.of();
            inventoryCaptured = false;
            completed.clear();
            completedEndpoints.clear();
            fallbacks.clear();
            merged.clear();
            endpoints = List.of();
            patternIndex = 0;
            endpointIndex = 0;
            expandedCount = 0;
            validationIndex = 0;
            cursor = null;
            patternFallback = null;
        }

        private void acceptCapture(ReusableInputPlanningExpansion.Result result) {
            cursor = null;
            endpointIndex++;
            if (result instanceof ReusableInputPlanningExpansion.Stopped stopped) {
                switch (stopped.reason()) {
                    case BINDING_LIMIT -> retainLegacy(TrinityPlanningDiagnosticCode.VARIANT_LIMIT, "variant_limit", "capture_binding_limit");
                    case DEADLINE -> future.complete(failure(TrinityPlanningDiagnosticCode.MIP_TIMEOUT, "timeout", "capture_deadline"));
                    case CANCELLED -> future.complete(failure(TrinityPlanningDiagnosticCode.CALCULATION_CANCELLED, "cancelled", "capture_cancelled"));
                }
                return;
            }
            var captured = (ReusableInputPlanningExpansion.Captured) result;
            if (captured.hasReusableInputs()) {
                TrinitySameItemPolicy policy = base.sameItemPolicy(target);
                for (List<TrinityBoundPatternInput> assignment : captured.bindings()) {
                    if (assignment.stream().anyMatch(input -> input.reusableRule() == null && policy.allowsSameItem(input.template().what()))) {
                        retainLegacy(TrinityPlanningDiagnosticCode.UNSUPPORTED_PATTERN, "unsupported_pattern", "component_aliased_material");
                        return;
                    }
                    merged.add(assignment);
                }
            }
        }

        private void retainLegacy(TrinityPlanningDiagnosticCode code, String translation, String reason) {
            patternFallback = new TrinityPlanningDiagnostic(code,
                    Component.translatable("gui.data_energistics.trinity_planning.diagnostic." + translation),
                    Map.of("phase", "reusable_input_capture", "reason", reason, "action", "legacy_pattern"));
            merged.clear();
            endpointIndex = endpoints.size();
        }

        private List<Endpoint> discover(TrinityCraftingGraphPattern pattern) {
            List<Endpoint> result = new ObjectArrayList<>();
            ObjectLinkedOpenHashSet<Target> targets = new ObjectLinkedOpenHashSet<>();
            for (IPatternDetails live : source.patternsFor(pattern.outputs().getFirst().what())) {
                if (!live.getDefinition().equals(pattern.definition()) ||
                        !TrinityPatternPublicationSignature.capture(live).equals(pattern.publication())) {
                    continue;
                }
                Optional<ResourceLocation> recipeId = source.recipeId(live);
                if (!rules.mayMatch(live, recipeId)) {
                    continue;
                }
                for (var providerId : source.publications().providerIdsFor(live)) {
                    var provider = source.publications().resolveLiveProvider(providerId);
                    if (provider == null) {
                        continue;
                    }
                    ReusableCraftingProviderAdapter supported = CountedCraftingProviderAdapters.reusableAdapter(provider);
                    if (supported == null) {
                        continue;
                    }
                    for (Target executionTarget : supported.reusableTargets(live, actor, level)) {
                        if (targets.add(executionTarget)) {
                            result.add(new Endpoint(live, supported, executionTarget, recipeId));
                        }
                    }
                }
            }
            result.sort(Comparator.comparing((Endpoint endpoint) -> endpoint.target().persistentIdentity())
                    .thenComparing(endpoint -> endpoint.target().route().stableIdentity())
                    .thenComparing(endpoint -> endpoint.target().mode().map(ResourceLocation::toString).orElse("")));
            return List.copyOf(result);
        }
    }

    private record Endpoint(IPatternDetails pattern, ReusableCraftingProviderAdapter adapter, Target target,
                            Optional<ResourceLocation> recipeId) {}

    private static TrinityAlgorithmResult<TrinityCraftingGraphSnapshot> failure(TrinityPlanningDiagnosticCode code,
                                                                                String translation, String reason) {
        return TrinityAlgorithmResult.failure(new TrinityPlanningDiagnostic(code,
                Component.translatable("gui.data_energistics.trinity_planning.diagnostic." + translation),
                Map.of("phase", "reusable_input_capture", "reason", reason)));
    }
}

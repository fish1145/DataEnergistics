package com.fish_dan_.data_energistics.common.crafting.trinity.planning.gateway;

import com.fish_dan_.data_energistics.Data_Energistics;
import com.fish_dan_.data_energistics.common.crafting.trinity.planning.TrinityPlanningDiagnostic;
import com.fish_dan_.data_energistics.common.crafting.trinity.planning.TrinityPlanningDiagnosticCode;
import com.fish_dan_.data_energistics.common.crafting.trinity.planning.algorithm.orchestration.TrinityGraphPlanner;
import com.fish_dan_.data_energistics.common.crafting.trinity.planning.cache.TrinityComputationCache;
import com.fish_dan_.data_energistics.common.crafting.trinity.planning.cache.TrinityPlanningComputation;
import com.fish_dan_.data_energistics.common.crafting.trinity.planning.cache.TrinityPlanningComputationResult;
import com.fish_dan_.data_energistics.common.crafting.trinity.planning.cache.TrinityPlanningInput;
import com.fish_dan_.data_energistics.configuration.schema.DataEnergisticsConfiguration.TrinityCraftingSchema;

import net.minecraft.network.chat.Component;

import appeng.api.networking.crafting.ICraftingPlan;
import appeng.api.stacks.GenericStack;
import org.jspecify.annotations.Nullable;

import java.util.Map;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.Callable;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;

/**
 * Runs initial requests and remaining-work replanning on isolated bounded execution lanes after the Trinity CPU gate
 * has passed.
 */
final class ConcurrentTrinityPlanningGateway implements TrinityPlanningGateway {

    private static final AtomicInteger THREAD_SEQUENCE = new AtomicInteger();

    private final ExecutorService initialPlannerExecutor;
    private final ExecutorService remainingPlannerExecutor;
    private final boolean ownsExecutors;
    private final TrinityComputationCache planningCache;
    private final TrinityPlanningComputation planningComputation;

    ConcurrentTrinityPlanningGateway(TrinityCraftingSchema settings) {
        this(createOwnedExecutors(settings));
    }

    private ConcurrentTrinityPlanningGateway(PlanningExecutors executors) {
        this(executors.initial(), executors.remaining(), true);
    }

    ConcurrentTrinityPlanningGateway(ExecutorService plannerExecutor, boolean ownsExecutor) {
        this(plannerExecutor, plannerExecutor, ownsExecutor);
    }

    ConcurrentTrinityPlanningGateway(
                                     ExecutorService initialPlannerExecutor,
                                     ExecutorService remainingPlannerExecutor,
                                     boolean ownsExecutors) {
        this.initialPlannerExecutor = initialPlannerExecutor;
        this.remainingPlannerExecutor = remainingPlannerExecutor;
        this.ownsExecutors = ownsExecutors;
        this.planningCache = TrinityComputationCache.create(initialPlannerExecutor);
        this.planningComputation = TrinityPlanningComputation.create(
                this.planningCache,
                TrinityGraphPlanner.pipeline());
    }

    private static PlanningExecutors createOwnedExecutors(TrinityCraftingSchema settings) {
        ExecutorService initial = createExecutor(
                settings.plannerThreads,
                settings.plannerQueueCapacity,
                "DataEnergistics-TrinityInitialPlanner-");
        ExecutorService remaining = createExecutor(
                settings.cpuPlannerThreads,
                settings.plannerQueueCapacity,
                "DataEnergistics-TrinityRemainingPlanner-");
        return new PlanningExecutors(initial, remaining);
    }

    private static ExecutorService createExecutor(int workerCount, int queueCapacity, String threadNamePrefix) {
        ThreadFactory threadFactory = task -> {
            Thread thread = new Thread(task, threadNamePrefix + THREAD_SEQUENCE.incrementAndGet());
            thread.setDaemon(true);
            return thread;
        };
        return new ThreadPoolExecutor(
                workerCount,
                workerCount,
                0L,
                TimeUnit.MILLISECONDS,
                new ArrayBlockingQueue<>(queueCapacity),
                threadFactory,
                new ThreadPoolExecutor.AbortPolicy());
    }

    private record PlanningExecutors(ExecutorService initial, ExecutorService remaining) {}

    private static void validatePlanningScope(long gridScope, long graphRevision) {
        if (gridScope < 0L || graphRevision < 0L) {
            throw new IllegalArgumentException("Trinity planning requires a non-negative Grid scope and graph revision");
        }
    }

    @Override
    public Future<ICraftingPlan> begin(
                                       boolean trinityPlanningAvailable,
                                       long gridScope,
                                       long graphRevision,
                                       GenericStack requestedOutput,
                                       Callable<TrinityPlanningAttempt> trinityCalculation,
                                       Supplier<Future<ICraftingPlan>> ae2Calculation) {
        if (!trinityPlanningAvailable) {
            try {
                return ae2Calculation.get();
            } catch (RuntimeException exception) {
                return CompletableFuture.failedFuture(exception);
            }
        }
        validatePlanningScope(gridScope, graphRevision);

        Future<TrinityPlanningAttempt> trinityFuture;
        try {
            trinityFuture = this.planningCache.submit(
                    this.initialPlannerExecutor,
                    gridScope,
                    trinityCalculation);
        } catch (RejectedExecutionException exception) {
            Data_Energistics.LOGGER.warn("Trinity planner queue rejected a calculation; publishing a terminal diagnostic",
                    exception);
            trinityFuture = CompletableFuture.completedFuture(
                    TrinityPlanningAttempt.failure(new TrinityPlanningDiagnostic(
                            TrinityPlanningDiagnosticCode.PLANNER_QUEUE_FULL,
                            Component.translatable(
                                    "gui.data_energistics.trinity_planning.diagnostic.planner_queue_full"),
                            Map.of("reason", exception.getClass().getSimpleName()))));
        }
        return new PreferredPlanningFuture(requestedOutput, trinityFuture);
    }

    @Override
    public Future<TrinityPlanningAttempt> beginTrinity(
                                                       long gridScope,
                                                       long graphRevision,
                                                       Callable<TrinityPlanningAttempt> trinityCalculation) {
        validatePlanningScope(gridScope, graphRevision);
        try {
            return this.planningCache.submit(
                    this.remainingPlannerExecutor,
                    gridScope,
                    trinityCalculation);
        } catch (RejectedExecutionException exception) {
            Data_Energistics.LOGGER.warn("Trinity planner queue rejected a continuation calculation", exception);
            return CompletableFuture.completedFuture(TrinityPlanningAttempt.failure(new TrinityPlanningDiagnostic(
                    TrinityPlanningDiagnosticCode.PLANNER_QUEUE_FULL,
                    Component.translatable(
                            "gui.data_energistics.trinity_planning.diagnostic.planner_queue_full_replan"),
                    Map.of("reason", exception.getClass().getSimpleName()))));
        }
    }

    @Override
    public TrinityPlanningComputationResult calculateTrinity(TrinityPlanningInput input)
                                                                                         throws InterruptedException, ExecutionException {
        return this.planningComputation.calculate(input);
    }

    @Override
    public TrinityPlanningComputationResult calculateRemainingTrinity(TrinityPlanningInput input)
                                                                                                  throws InterruptedException, ExecutionException {
        return this.planningComputation.calculate(input);
    }

    @Override
    public TrinityComputationCache computationCache() {
        return this.planningCache;
    }

    @Override
    public void clearGrid(long gridScope) {
        try {
            this.planningCache.clearGrid(gridScope);
        } finally {
            this.planningComputation.clearGrid(gridScope);
        }
    }

    @Override
    public void close() {
        try {
            this.planningCache.close();
        } finally {
            this.planningComputation.clear();
            if (this.ownsExecutors) {
                this.initialPlannerExecutor.shutdownNow();
                if (this.remainingPlannerExecutor != this.initialPlannerExecutor) {
                    this.remainingPlannerExecutor.shutdownNow();
                }
            }
        }
    }

    /**
     * Future implementation that waits directly on the selected Trinity calculation without occupying another worker.
     */
    private static final class PreferredPlanningFuture implements Future<ICraftingPlan> {

        private final GenericStack requestedOutput;
        private final Future<TrinityPlanningAttempt> trinity;
        private final long startedNanos;

        private @Nullable ICraftingPlan result;
        private boolean cancelled;

        private PreferredPlanningFuture(
                                        GenericStack requestedOutput,
                                        Future<TrinityPlanningAttempt> trinity) {
            this.requestedOutput = requestedOutput;
            this.trinity = trinity;
            this.startedNanos = System.nanoTime();
        }

        @Override
        public synchronized boolean cancel(boolean mayInterruptIfRunning) {
            if (this.cancelled || this.result != null || isDone()) {
                return false;
            }
            boolean trinityCancelled = this.trinity.cancel(mayInterruptIfRunning);
            if (trinityCancelled) {
                this.cancelled = true;
            }
            return trinityCancelled;
        }

        @Override
        public synchronized boolean isCancelled() {
            return this.cancelled;
        }

        @Override
        public synchronized boolean isDone() {
            if (this.cancelled || this.result != null) {
                return true;
            }
            return this.trinity.isDone();
        }

        @Override
        public ICraftingPlan get() throws InterruptedException, ExecutionException {
            try {
                return resolve(Long.MAX_VALUE);
            } catch (TimeoutException exception) {
                throw new AssertionError("An unbounded Future#get cannot time out", exception);
            }
        }

        @Override
        public ICraftingPlan get(long timeout, TimeUnit unit)
                                                              throws InterruptedException, ExecutionException, TimeoutException {
            if (timeout < 0L) {
                throw new IllegalArgumentException("Timeout must not be negative");
            }
            return resolve(saturatedAdd(System.nanoTime(), unit.toNanos(timeout)));
        }

        private ICraftingPlan resolve(long callerDeadlineNanos)
                                                                throws InterruptedException, ExecutionException, TimeoutException {
            synchronized (this) {
                if (this.cancelled) {
                    throw new CancellationException("The combined crafting calculation was cancelled");
                }
                if (this.result != null) {
                    return this.result;
                }
            }

            TrinityPlanningDiagnostic diagnostic;
            try {
                TrinityPlanningAttempt attempt = awaitTrinity(callerDeadlineNanos);
                if (attempt.successful()) {
                    return publish(attempt.plan());
                }
                if (attempt.authoritativeSimulation().isPresent()) {
                    return publish(attempt.authoritativeSimulation().orElseThrow());
                }
                diagnostic = attempt.diagnostic();
            } catch (ExecutionException exception) {
                Data_Energistics.LOGGER.error("Trinity planning calculation failed; publishing a terminal diagnostic",
                        exception.getCause());
                diagnostic = TrinityPlanningDiagnostic.ofTranslationKey(
                        TrinityPlanningDiagnosticCode.INTERNAL_ERROR,
                        "gui.data_energistics.trinity_planning.diagnostic.internal_error");
            } catch (CancellationException exception) {
                diagnostic = TrinityPlanningDiagnostic.ofTranslationKey(
                        TrinityPlanningDiagnosticCode.CALCULATION_CANCELLED,
                        "gui.data_energistics.trinity_planning.diagnostic.cancelled");
            } catch (RuntimeException exception) {
                Data_Energistics.LOGGER.error("Trinity planning returned an invalid result; publishing a terminal diagnostic",
                        exception);
                diagnostic = TrinityPlanningDiagnostic.ofTranslationKey(
                        TrinityPlanningDiagnosticCode.INTERNAL_ERROR,
                        "gui.data_energistics.trinity_planning.diagnostic.internal_error");
            }

            return publish(TrinityDiagnosedCraftingPlan.forDiagnostic(
                    this.requestedOutput,
                    diagnostic,
                    Math.max(0L, System.nanoTime() - this.startedNanos)));
        }

        private TrinityPlanningAttempt awaitTrinity(long callerDeadlineNanos)
                                                                              throws InterruptedException, ExecutionException, TimeoutException {
            if (callerDeadlineNanos == Long.MAX_VALUE) {
                return this.trinity.get();
            }
            long remaining = callerDeadlineNanos - System.nanoTime();
            if (remaining > 0L) {
                return this.trinity.get(remaining, TimeUnit.NANOSECONDS);
            }
            if (this.trinity.isDone()) {
                return this.trinity.get();
            }
            throw new TimeoutException("Caller timeout elapsed while awaiting Trinity planning");
        }

        private synchronized ICraftingPlan publish(ICraftingPlan selected) {
            if (this.cancelled) {
                throw new CancellationException("The combined crafting calculation was cancelled");
            }
            if (this.result == null) {
                this.result = selected;
            }
            return this.result;
        }

        private static long saturatedAdd(long left, long right) {
            if (right > 0L && left > Long.MAX_VALUE - right) {
                return Long.MAX_VALUE;
            }
            if (right < 0L && left < Long.MIN_VALUE - right) {
                return Long.MIN_VALUE;
            }
            return left + right;
        }
    }
}

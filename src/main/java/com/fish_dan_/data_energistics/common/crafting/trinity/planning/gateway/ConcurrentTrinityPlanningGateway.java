package com.fish_dan_.data_energistics.common.crafting.trinity.planning.gateway;

import com.fish_dan_.data_energistics.Data_Energistics;
import com.fish_dan_.data_energistics.common.crafting.trinity.planning.TrinityPlanningDiagnostic;
import com.fish_dan_.data_energistics.common.crafting.trinity.planning.TrinityPlanningDiagnosticCode;
import com.fish_dan_.data_energistics.common.crafting.trinity.planning.algorithm.orchestration.TrinityGraphPlanner;
import com.fish_dan_.data_energistics.common.crafting.trinity.planning.cache.TrinityComputationCache;
import com.fish_dan_.data_energistics.common.crafting.trinity.planning.cache.TrinityPlanningComputation;
import com.fish_dan_.data_energistics.common.crafting.trinity.planning.cache.TrinityPlanningComputationResult;
import com.fish_dan_.data_energistics.common.crafting.trinity.planning.cache.TrinityPlanningInput;
import com.fish_dan_.data_energistics.configuration.api.DataEnergisticsSettings.TrinityCrafting;

import net.minecraft.network.chat.Component;

import appeng.api.networking.crafting.ICraftingPlan;
import appeng.api.stacks.GenericStack;

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
 * Bounded-executor implementation that runs Trinity exclusively after the Trinity CPU gate has passed.
 */
final class ConcurrentTrinityPlanningGateway implements TrinityPlanningGateway {

    private static final AtomicInteger THREAD_SEQUENCE = new AtomicInteger();

    private final ExecutorService plannerExecutor;
    private final boolean ownsExecutor;
    private final TrinityComputationCache computationCache;
    private final TrinityPlanningComputation planningComputation;

    ConcurrentTrinityPlanningGateway(TrinityCrafting settings) {
        this(createExecutor(settings), true);
    }

    ConcurrentTrinityPlanningGateway(ExecutorService plannerExecutor, boolean ownsExecutor) {
        if (plannerExecutor == null) {
            throw new IllegalArgumentException("A Trinity planning gateway requires an executor");
        }
        this.plannerExecutor = plannerExecutor;
        this.ownsExecutor = ownsExecutor;
        this.computationCache = TrinityComputationCache.create(plannerExecutor);
        this.planningComputation = TrinityPlanningComputation.create(
                this.computationCache,
                TrinityGraphPlanner.pipeline());
    }

    private static ExecutorService createExecutor(TrinityCrafting settings) {
        ThreadFactory threadFactory = task -> {
            Thread thread = new Thread(task, "DataEnergistics-TrinityPlanner-" + THREAD_SEQUENCE.incrementAndGet());
            thread.setDaemon(true);
            return thread;
        };
        return new ThreadPoolExecutor(
                settings.plannerThreads(),
                settings.plannerThreads(),
                0L,
                TimeUnit.MILLISECONDS,
                new ArrayBlockingQueue<>(settings.plannerQueueCapacity()),
                threadFactory,
                new ThreadPoolExecutor.AbortPolicy());
    }

    @Override
    public Future<ICraftingPlan> begin(
                                       boolean qualifiedTrinityCpu,
                                       long gridScope,
                                       long graphRevision,
                                       GenericStack requestedOutput,
                                       Callable<TrinityPlanningAttempt> trinityCalculation,
                                       Supplier<Future<ICraftingPlan>> ae2Calculation) {
        if (!qualifiedTrinityCpu) {
            try {
                Future<ICraftingPlan> ae2Future = ae2Calculation.get();
                if (ae2Future == null) {
                    throw new IllegalStateException("AE2 planning factory returned no future");
                }
                return ae2Future;
            } catch (RuntimeException exception) {
                return CompletableFuture.failedFuture(exception);
            }
        }

        Future<TrinityPlanningAttempt> trinityFuture;
        try {
            trinityFuture = this.computationCache.submit(gridScope, graphRevision, trinityCalculation);
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
        try {
            return this.computationCache.submit(gridScope, graphRevision, trinityCalculation);
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
    public TrinityComputationCache computationCache() {
        return this.computationCache;
    }

    @Override
    public void clearGrid(long gridScope) {
        this.computationCache.clearGrid(gridScope);
    }

    @Override
    public void close() {
        try {
            this.computationCache.close();
        } finally {
            if (this.ownsExecutor) {
                this.plannerExecutor.shutdownNow();
            }
        }
    }

    /**
     * Future implementation that waits directly on the selected Trinity calculation without occupying another worker.
     */
    private static final class PreferredPlanningFuture implements Future<ICraftingPlan> {

        private final GenericStack requestedOutput;
        private final Future<TrinityPlanningAttempt> trinity;

        private ICraftingPlan result;
        private boolean cancelled;

        private PreferredPlanningFuture(
                                        GenericStack requestedOutput,
                                        Future<TrinityPlanningAttempt> trinity) {
            this.requestedOutput = requestedOutput;
            this.trinity = trinity;
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

            return publish(TrinityDiagnosedCraftingPlan.forDiagnostic(this.requestedOutput, diagnostic));
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

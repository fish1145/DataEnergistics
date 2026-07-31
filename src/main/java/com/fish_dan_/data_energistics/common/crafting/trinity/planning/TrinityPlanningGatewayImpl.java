package com.fish_dan_.data_energistics.common.crafting.trinity.planning;

import com.fish_dan_.data_energistics.Data_Energistics;
import com.fish_dan_.data_energistics.config.TrinityCraftingConfig;

import net.minecraft.network.chat.Component;

import appeng.api.networking.crafting.ICraftingPlan;
import org.jetbrains.annotations.NotNull;

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
 * Bounded implementation that waits only for the configured Trinity budget before adopting the AE2 result.
 */
final class TrinityPlanningGatewayImpl implements TrinityPlanningGateway {

    private static final AtomicInteger THREAD_SEQUENCE = new AtomicInteger();

    private final ExecutorService plannerExecutor;
    private final long trinityWaitNanos;
    private final boolean ownsExecutor;

    TrinityPlanningGatewayImpl(TrinityCraftingConfig.Settings settings) {
        this(createExecutor(settings), TimeUnit.MILLISECONDS.toNanos(settings.mipTimeoutMs()), true);
    }

    TrinityPlanningGatewayImpl(ExecutorService plannerExecutor, long trinityWaitNanos, boolean ownsExecutor) {
        if (plannerExecutor == null || trinityWaitNanos <= 0L) {
            throw new IllegalArgumentException("A Trinity planning gateway requires an executor and positive timeout");
        }
        this.plannerExecutor = plannerExecutor;
        this.trinityWaitNanos = trinityWaitNanos;
        this.ownsExecutor = ownsExecutor;
    }

    private static ExecutorService createExecutor(TrinityCraftingConfig.Settings settings) {
        if (settings == null) {
            throw new IllegalArgumentException("Trinity planning settings are required");
        }
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
                                       Callable<TrinityPlanningAttempt> trinityCalculation,
                                       Supplier<Future<ICraftingPlan>> ae2Calculation) {
        if (trinityCalculation == null || ae2Calculation == null) {
            throw new IllegalArgumentException("Both Trinity and AE2 planning factories are required");
        }

        Future<TrinityPlanningAttempt> trinityFuture = null;
        if (qualifiedTrinityCpu) {
            try {
                trinityFuture = this.plannerExecutor.submit(trinityCalculation);
            } catch (RejectedExecutionException exception) {
                Data_Energistics.LOGGER.warn("Trinity planner queue rejected a calculation; falling back to AE2",
                        exception);
                trinityFuture = CompletableFuture.completedFuture(TrinityPlanningAttempt.failure(
                        new TrinityPlanningDiagnostic(
                                TrinityPlanningDiagnosticCode.PLANNER_QUEUE_FULL,
                                Component.literal("Trinity planner queue is full; using the AE2 calculation"),
                                Map.of("reason", exception.getClass().getSimpleName()))));
            }
        }

        Future<ICraftingPlan> ae2Future;
        try {
            ae2Future = ae2Calculation.get();
            if (ae2Future == null) {
                throw new IllegalStateException("AE2 planning factory returned no future");
            }
        } catch (RuntimeException exception) {
            ae2Future = CompletableFuture.failedFuture(exception);
        }

        if (!qualifiedTrinityCpu) {
            return ae2Future;
        }
        return new PreferredPlanningFuture(trinityFuture, ae2Future, this.trinityWaitNanos);
    }

    @Override
    public void close() {
        if (this.ownsExecutor) {
            this.plannerExecutor.shutdownNow();
        }
    }

    /**
     * Future implementation that does not occupy an additional worker while the two calculations are running.
     */
    private static final class PreferredPlanningFuture implements Future<ICraftingPlan> {

        private final Future<TrinityPlanningAttempt> trinity;
        private final Future<ICraftingPlan> ae2;
        private final long trinityDeadlineNanos;

        private ICraftingPlan result;
        private boolean cancelled;

        private PreferredPlanningFuture(
                                        Future<TrinityPlanningAttempt> trinity,
                                        Future<ICraftingPlan> ae2,
                                        long trinityWaitNanos) {
            this.trinity = trinity;
            this.ae2 = ae2;
            this.trinityDeadlineNanos = saturatedAdd(System.nanoTime(), trinityWaitNanos);
        }

        @Override
        public synchronized boolean cancel(boolean mayInterruptIfRunning) {
            if (this.cancelled || this.result != null || isDone()) {
                return false;
            }
            boolean trinityCancelled = this.trinity.cancel(mayInterruptIfRunning);
            boolean ae2Cancelled = this.ae2.cancel(mayInterruptIfRunning);
            boolean cancelledAny = trinityCancelled || ae2Cancelled;
            if (cancelledAny) {
                this.cancelled = true;
            }
            return cancelledAny;
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
            long now = System.nanoTime();
            if (!this.trinity.isDone() && now < this.trinityDeadlineNanos) {
                return false;
            }
            if (this.trinity.isDone() && hasSuccessfulTrinityResult(this.trinity)) {
                return true;
            }
            return this.ae2.isDone();
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
        public ICraftingPlan get(long timeout, @NotNull TimeUnit unit)
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
            Throwable trinityFailure = null;
            TrinityPlanningAttempt attempt = null;
            try {
                attempt = awaitTrinity(callerDeadlineNanos);
                if (attempt == null) {
                    throw new IllegalStateException("Trinity planning returned no attempt");
                }
                diagnostic = attempt.diagnostic();
            } catch (ExecutionException exception) {
                trinityFailure = exception.getCause();
                Data_Energistics.LOGGER.error("Trinity planning calculation failed; falling back to AE2",
                        trinityFailure);
                diagnostic = TrinityPlanningDiagnostic.of(
                        TrinityPlanningDiagnosticCode.INTERNAL_ERROR,
                        "Trinity planning failed internally; using the AE2 calculation");
            } catch (CancellationException exception) {
                trinityFailure = exception;
                diagnostic = TrinityPlanningDiagnostic.of(
                        TrinityPlanningDiagnosticCode.CALCULATION_CANCELLED,
                        "Trinity planning was cancelled; using the AE2 calculation");
            } catch (RuntimeException exception) {
                trinityFailure = exception;
                Data_Energistics.LOGGER.error("Trinity planning returned an invalid result; falling back to AE2",
                        exception);
                diagnostic = TrinityPlanningDiagnostic.of(
                        TrinityPlanningDiagnosticCode.INTERNAL_ERROR,
                        "Trinity planning returned an invalid result; using the AE2 calculation");
            }

            if (attempt != null && attempt.successful()) {
                this.ae2.cancel(true);
                return publish(attempt.plan());
            }

            try {
                ICraftingPlan ae2Plan = awaitAe2(callerDeadlineNanos);
                if (ae2Plan == null) {
                    throw new ExecutionException(new IllegalStateException("AE2 planning returned no plan"));
                }
                ICraftingPlan selected = ae2Plan.simulation() ? new TrinityDiagnosedCraftingPlan(ae2Plan, diagnostic) : ae2Plan;
                return publish(selected);
            } catch (ExecutionException exception) {
                if (diagnostic != null) {
                    exception.addSuppressed(new TrinityPlanningFallbackException(diagnostic, trinityFailure));
                }
                throw exception;
            }
        }

        private TrinityPlanningAttempt awaitTrinity(long callerDeadlineNanos)
                                                                              throws InterruptedException, ExecutionException, TimeoutException {
            if (this.trinity.isDone()) {
                return this.trinity.get();
            }

            long deadline = Math.min(this.trinityDeadlineNanos, callerDeadlineNanos);
            long remaining = deadline - System.nanoTime();
            if (remaining > 0L) {
                try {
                    return this.trinity.get(remaining, TimeUnit.NANOSECONDS);
                } catch (TimeoutException timeout) {
                    if (callerDeadlineNanos < this.trinityDeadlineNanos) {
                        throw timeout;
                    }
                }
            }

            if (callerDeadlineNanos < this.trinityDeadlineNanos) {
                throw new TimeoutException("Caller timeout elapsed while awaiting Trinity planning");
            }
            this.trinity.cancel(true);
            Data_Energistics.LOGGER.warn("Trinity planning exceeded its configured wait budget; falling back to AE2");
            return TrinityPlanningAttempt.failure(TrinityPlanningDiagnostic.of(
                    TrinityPlanningDiagnosticCode.MIP_TIMEOUT,
                    "Trinity planning exceeded its configured budget; using the AE2 calculation"));
        }

        private ICraftingPlan awaitAe2(long callerDeadlineNanos)
                                                                 throws InterruptedException, ExecutionException, TimeoutException {
            if (callerDeadlineNanos == Long.MAX_VALUE) {
                return this.ae2.get();
            }
            long remaining = callerDeadlineNanos - System.nanoTime();
            if (remaining <= 0L) {
                if (this.ae2.isDone()) {
                    return this.ae2.get();
                }
                throw new TimeoutException("Caller timeout elapsed while awaiting AE2 planning");
            }
            return this.ae2.get(remaining, TimeUnit.NANOSECONDS);
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

        private static boolean hasSuccessfulTrinityResult(Future<TrinityPlanningAttempt> future) {
            try {
                TrinityPlanningAttempt attempt = future.get();
                return attempt != null && attempt.successful();
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
                return false;
            } catch (ExecutionException | CancellationException exception) {
                return false;
            }
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

    /**
     * Adds Trinity context without replacing the original AE2 planning exception.
     */
    private static final class TrinityPlanningFallbackException extends Exception {

        private TrinityPlanningFallbackException(
                                                 TrinityPlanningDiagnostic diagnostic,
                                                 Throwable cause) {
            super(diagnostic.code() + ": " + diagnostic.message().getString(), cause);
        }
    }
}

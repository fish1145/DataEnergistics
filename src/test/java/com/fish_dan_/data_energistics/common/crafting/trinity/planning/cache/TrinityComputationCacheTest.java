package com.fish_dan_.data_energistics.common.crafting.trinity.planning.cache;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class TrinityComputationCacheTest {

    private static final long TIMEOUT_SECONDS = 5L;

    private final List<ExecutorService> executors = new ArrayList<>();
    private final List<TrinityComputationCache> caches = new ArrayList<>();

    @AfterEach
    void closeResources() {
        this.caches.forEach(TrinityComputationCache::close);
        this.executors.forEach(ExecutorService::shutdownNow);
    }

    @Test
    void concurrentCallersShareOneCalculationButCancelIndependently() throws Exception {
        ExecutorService executor = executor(2);
        TrinityComputationCache cache = cache(executor, 4);
        CountDownLatch entered = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        AtomicInteger calculations = new AtomicInteger();

        TrinityComputationLookup<String> first = cache.compute(
                1L,
                TrinityComputationNamespace.SOLVED_PLAN,
                7L,
                "same",
                () -> {
                    calculations.incrementAndGet();
                    entered.countDown();
                    assertTrue(release.await(TIMEOUT_SECONDS, TimeUnit.SECONDS));
                    return TrinityCachedComputation.cacheable("result");
                });
        assertTrue(entered.await(TIMEOUT_SECONDS, TimeUnit.SECONDS));
        TrinityComputationLookup<String> second = cache.compute(
                1L,
                TrinityComputationNamespace.SOLVED_PLAN,
                7L,
                "same",
                () -> TrinityCachedComputation.cacheable("unexpected"));

        assertFalse(first.cacheHit());
        assertTrue(second.cacheHit());
        assertTrue(first.future().cancel(true));
        release.countDown();

        assertThrows(CancellationException.class, first.future()::get);
        assertEquals("result", get(second.future()));
        assertEquals(1, calculations.get());
    }

    @Test
    void accessOrderEvictsOnlyTheLeastRecentlyUsedCompletedEntry() throws Exception {
        ExecutorService executor = executor(1);
        TrinityComputationCache cache = cache(executor, 2);
        AtomicInteger calculations = new AtomicInteger();

        assertEquals("a", get(compute(cache, "a", calculations).future()));
        assertEquals("b", get(compute(cache, "b", calculations).future()));
        assertTrue(compute(cache, "a", calculations).cacheHit());
        assertEquals("c", get(compute(cache, "c", calculations).future()));

        TrinityComputationLookup<String> reloadedB = compute(cache, "b", calculations);
        assertFalse(reloadedB.cacheHit());
        assertEquals("b", get(reloadedB.future()));
        assertEquals(4, calculations.get());
    }

    @Test
    void allInflightPartitionBypassesRegistrationWithoutGrowingTheLru() throws Exception {
        ExecutorService executor = executor(3);
        TrinityComputationCache cache = cache(executor, 2);
        CountDownLatch entered = new CountDownLatch(2);
        CountDownLatch release = new CountDownLatch(1);

        TrinityComputationLookup<String> first = blocking(cache, "first", entered, release);
        TrinityComputationLookup<String> second = blocking(cache, "second", entered, release);
        assertTrue(entered.await(TIMEOUT_SECONDS, TimeUnit.SECONDS));

        CountDownLatch bypassEntered = new CountDownLatch(1);
        CountDownLatch bypassRelease = new CountDownLatch(1);
        AtomicInteger bypassCalculations = new AtomicInteger();
        TrinityComputationLookup<String> bypass = cache.compute(
                1L,
                TrinityComputationNamespace.SOLVED_PLAN,
                1L,
                "bypass",
                () -> {
                    int attempt = bypassCalculations.incrementAndGet();
                    bypassEntered.countDown();
                    assertTrue(bypassRelease.await(TIMEOUT_SECONDS, TimeUnit.SECONDS));
                    return TrinityCachedComputation.cacheable("bypass-" + attempt);
                });
        assertFalse(bypass.registered());
        assertTrue(bypassEntered.await(TIMEOUT_SECONDS, TimeUnit.SECONDS));

        TrinityComputationLookup<String> repeated = cache.compute(
                1L,
                TrinityComputationNamespace.SOLVED_PLAN,
                1L,
                "bypass",
                () -> TrinityCachedComputation.cacheable("unexpected"));
        assertTrue(repeated.cacheHit());
        assertFalse(repeated.registered());
        bypassRelease.countDown();
        assertEquals("bypass-1", get(bypass.future()));
        assertEquals("bypass-1", get(repeated.future()));
        assertEquals(1, bypassCalculations.get());

        TrinityComputationLookup<String> afterCompletion = cache.compute(
                1L,
                TrinityComputationNamespace.SOLVED_PLAN,
                1L,
                "bypass",
                () -> TrinityCachedComputation.cacheable("bypass-" + bypassCalculations.incrementAndGet()));
        assertFalse(afterCompletion.cacheHit());
        assertFalse(afterCompletion.registered());
        assertEquals("bypass-2", get(afterCompletion.future()));

        release.countDown();
        assertEquals("first", get(first.future()));
        assertEquals("second", get(second.future()));
    }

    @Test
    void transientValuesAndFailuresAreNotRetained() throws Exception {
        ExecutorService executor = executor(1);
        TrinityComputationCache cache = cache(executor, 2);
        AtomicInteger transientCalculations = new AtomicInteger();

        for (int expected = 1; expected <= 2; expected++) {
            TrinityComputationLookup<Integer> lookup = cache.compute(
                    1L,
                    TrinityComputationNamespace.SOLVED_PLAN,
                    1L,
                    "transient",
                    () -> TrinityCachedComputation.transientValue(transientCalculations.incrementAndGet()));
            assertFalse(lookup.cacheHit());
            assertEquals(expected, get(lookup.future()));
        }

        AtomicInteger failures = new AtomicInteger();
        for (int expected = 1; expected <= 2; expected++) {
            TrinityComputationLookup<String> lookup = cache.compute(
                    1L,
                    TrinityComputationNamespace.SOLVED_PLAN,
                    1L,
                    "failure",
                    () -> {
                        int attempt = failures.incrementAndGet();
                        throw new IllegalStateException("failure-" + attempt);
                    });
            ExecutionException failure = assertThrows(ExecutionException.class, lookup.future()::get);
            assertEquals("failure-" + expected, failure.getCause().getMessage());
        }
    }

    @Test
    void revisionInvalidationRetainsSemanticEntriesAndGridScopesRemainIsolated() throws Exception {
        ExecutorService executor = executor(2);
        TrinityComputationCache cache = cache(executor, 8);
        AtomicInteger solvedCalculations = new AtomicInteger();
        AtomicInteger compiledCalculations = new AtomicInteger();

        assertEquals("solved-1", get(cache.compute(
                10L,
                TrinityComputationNamespace.SOLVED_PLAN,
                1L,
                "plan",
                () -> TrinityCachedComputation.cacheable("solved-" + solvedCalculations.incrementAndGet())).future()));
        assertEquals("compiled-1", get(cache.compute(
                10L,
                TrinityComputationNamespace.COMPILED_GRAPH,
                TrinityComputationCache.SEMANTIC_REVISION,
                "graph",
                () -> TrinityCachedComputation.cacheable("compiled-" + compiledCalculations.incrementAndGet())).future()));

        cache.invalidateRevision(10L, 2L);

        assertEquals("solved-2", get(cache.compute(
                10L,
                TrinityComputationNamespace.SOLVED_PLAN,
                2L,
                "plan",
                () -> TrinityCachedComputation.cacheable("solved-" + solvedCalculations.incrementAndGet())).future()));
        cache.invalidateRevision(10L, 1L);
        TrinityComputationLookup<String> currentRevision = cache.compute(
                10L,
                TrinityComputationNamespace.SOLVED_PLAN,
                2L,
                "plan",
                () -> TrinityCachedComputation.cacheable("unexpected"));
        assertTrue(currentRevision.cacheHit());
        assertEquals("solved-2", get(currentRevision.future()));

        TrinityComputationLookup<String> oldRevision = cache.compute(
                10L,
                TrinityComputationNamespace.SOLVED_PLAN,
                1L,
                "plan",
                () -> TrinityCachedComputation.cacheable("solved-" + solvedCalculations.incrementAndGet()));
        assertFalse(oldRevision.cacheHit());
        assertFalse(oldRevision.registered());
        assertThrows(CancellationException.class, oldRevision.future()::get);
        assertEquals(2, solvedCalculations.get());
        TrinityComputationLookup<String> compiled = cache.compute(
                10L,
                TrinityComputationNamespace.COMPILED_GRAPH,
                TrinityComputationCache.SEMANTIC_REVISION,
                "graph",
                () -> TrinityCachedComputation.cacheable("compiled-" + compiledCalculations.incrementAndGet()));
        assertTrue(compiled.cacheHit());
        assertEquals("compiled-1", get(compiled.future()));

        TrinityComputationLookup<String> otherGrid = cache.compute(
                11L,
                TrinityComputationNamespace.COMPILED_GRAPH,
                TrinityComputationCache.SEMANTIC_REVISION,
                "graph",
                () -> TrinityCachedComputation.cacheable("compiled-" + compiledCalculations.incrementAndGet()));
        assertFalse(otherGrid.cacheHit());
        assertEquals("compiled-2", get(otherGrid.future()));
    }

    @Test
    void clearingOneGridCancelsItsBottomCalculationWithoutAffectingAnotherGrid() throws Exception {
        ExecutorService executor = executor(2);
        TrinityComputationCache cache = cache(executor, 4);
        CountDownLatch entered = new CountDownLatch(2);
        CountDownLatch release = new CountDownLatch(1);
        TrinityComputationLookup<String> firstGrid = blocking(cache, 1L, "first", entered, release);
        TrinityComputationLookup<String> secondGrid = blocking(cache, 2L, "second", entered, release);
        assertTrue(entered.await(TIMEOUT_SECONDS, TimeUnit.SECONDS));

        cache.clearGrid(1L);
        assertThrows(CancellationException.class, firstGrid.future()::get);
        release.countDown();
        assertEquals("second", get(secondGrid.future()));
    }

    @Test
    void rejectedSubmissionIsRemovedBeforeTheSameKeyRetries() throws Exception {
        AtomicBoolean rejectNext = new AtomicBoolean(true);
        TrinityComputationCache cache = TrinityComputationCache.create(command -> {
            if (rejectNext.getAndSet(false)) {
                throw new RejectedExecutionException("expected rejection");
            }
            command.run();
        }, 2);
        this.caches.add(cache);

        assertThrows(RejectedExecutionException.class, () -> cache.compute(
                1L,
                TrinityComputationNamespace.SOLVED_PLAN,
                1L,
                "retry",
                () -> TrinityCachedComputation.cacheable("rejected")));

        TrinityComputationLookup<String> retried = cache.compute(
                1L,
                TrinityComputationNamespace.SOLVED_PLAN,
                1L,
                "retry",
                () -> TrinityCachedComputation.cacheable("accepted"));
        assertFalse(retried.cacheHit());
        assertEquals("accepted", get(retried.future()));
    }

    @Test
    void closingCacheCancelsAllGridCalculationsAndRejectsNewWork() throws Exception {
        ExecutorService executor = executor(2);
        TrinityComputationCache cache = cache(executor, 4);
        CountDownLatch entered = new CountDownLatch(2);
        CountDownLatch release = new CountDownLatch(1);
        TrinityComputationLookup<String> first = blocking(cache, 1L, "first", entered, release);
        TrinityComputationLookup<String> second = blocking(cache, 2L, "second", entered, release);
        assertTrue(entered.await(TIMEOUT_SECONDS, TimeUnit.SECONDS));

        cache.close();

        assertThrows(CancellationException.class, first.future()::get);
        assertThrows(CancellationException.class, second.future()::get);
        assertThrows(IllegalStateException.class, () -> cache.compute(
                1L,
                TrinityComputationNamespace.SOLVED_PLAN,
                1L,
                "closed",
                () -> TrinityCachedComputation.cacheable("closed")));
    }

    @Test
    void inlineSingleFlightDoesNotDeadlockASingleThreadExecutor() throws Exception {
        ExecutorService executor = executor(1);
        TrinityComputationCache cache = cache(executor, 4);
        AtomicInteger calculations = new AtomicInteger();

        Future<String> submitted = cache.submit(1L, 1L, () -> cache.computeInline(
                1L,
                TrinityComputationNamespace.COMPILED_GRAPH,
                TrinityComputationCache.SEMANTIC_REVISION,
                "graph",
                () -> TrinityCachedComputation.cacheable("compiled-" + calculations.incrementAndGet())).value());

        assertEquals("compiled-1", get(submitted));
        assertEquals(1, calculations.get());
    }

    @Test
    void cancellingOneDetachedCallerDoesNotInterruptSharedInlineCalculation() throws Exception {
        ExecutorService executor = executor(2);
        TrinityComputationCache cache = cache(executor, 8);
        CountDownLatch entered = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        AtomicInteger calculations = new AtomicInteger();

        Future<String> first = cache.submit(1L, 1L, () -> cache.computeInline(
                1L,
                TrinityComputationNamespace.COMPILED_GRAPH,
                TrinityComputationCache.SEMANTIC_REVISION,
                "shared",
                () -> {
                    calculations.incrementAndGet();
                    entered.countDown();
                    assertTrue(release.await(TIMEOUT_SECONDS, TimeUnit.SECONDS));
                    return TrinityCachedComputation.cacheable("compiled");
                }).value());
        assertTrue(entered.await(TIMEOUT_SECONDS, TimeUnit.SECONDS));
        Future<String> second = cache.submit(1L, 1L, () -> cache.computeInline(
                1L,
                TrinityComputationNamespace.COMPILED_GRAPH,
                TrinityComputationCache.SEMANTIC_REVISION,
                "shared",
                () -> TrinityCachedComputation.cacheable("unexpected")).value());

        assertTrue(first.cancel(true));
        release.countDown();

        assertThrows(CancellationException.class, first::get);
        assertEquals("compiled", get(second));
        assertEquals(1, calculations.get());
    }

    private TrinityComputationCache cache(ExecutorService executor, int entryLimit) {
        TrinityComputationCache cache = TrinityComputationCache.create(executor, entryLimit);
        this.caches.add(cache);
        return cache;
    }

    private ExecutorService executor(int threads) {
        ExecutorService executor = Executors.newFixedThreadPool(threads);
        this.executors.add(executor);
        return executor;
    }

    private static TrinityComputationLookup<String> compute(
                                                               TrinityComputationCache cache,
                                                               String key,
                                                               AtomicInteger calculations) {
        return cache.compute(
                1L,
                TrinityComputationNamespace.SOLVED_PLAN,
                1L,
                key,
                () -> {
                    calculations.incrementAndGet();
                    return TrinityCachedComputation.cacheable(key);
                });
    }

    private static TrinityComputationLookup<String> blocking(
                                                                TrinityComputationCache cache,
                                                                String key,
                                                                CountDownLatch entered,
                                                                CountDownLatch release) {
        return blocking(cache, 1L, key, entered, release);
    }

    private static TrinityComputationLookup<String> blocking(
                                                                TrinityComputationCache cache,
                                                                long gridScope,
                                                                String key,
                                                                CountDownLatch entered,
                                                                CountDownLatch release) {
        return cache.compute(
                gridScope,
                TrinityComputationNamespace.SOLVED_PLAN,
                1L,
                key,
                () -> {
                    entered.countDown();
                    assertTrue(release.await(TIMEOUT_SECONDS, TimeUnit.SECONDS));
                    return TrinityCachedComputation.cacheable(key);
                });
    }

    private static <V> V get(Future<V> future) throws Exception {
        return future.get(TIMEOUT_SECONDS, TimeUnit.SECONDS);
    }
}

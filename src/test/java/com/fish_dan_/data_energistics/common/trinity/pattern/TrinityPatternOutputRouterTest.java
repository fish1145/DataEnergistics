package com.fish_dan_.data_energistics.common.trinity.pattern;

import com.fish_dan_.data_energistics.Data_Energistics;

import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import net.neoforged.testframework.annotation.TestHolder;
import net.neoforged.testframework.gametest.EmptyTemplate;

import appeng.api.config.Actionable;
import appeng.api.stacks.AEItemKey;

import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import java.util.ListIterator;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;

@PrefixGameTestTemplate(false)
@GameTestHolder(Data_Energistics.MODID)
public final class TrinityPatternOutputRouterTest {

    private TrinityPatternOutputRouterTest() {}

    @TestHolder("trinity_pattern_output_router_retains_cpu_requests")
    @EmptyTemplate("5")
    @GameTest(template = "empty_5x5")
    public static void retainsUnacceptedCpuRequestAndStoresOnlyNonRequestedAmount(GameTestHelper helper) {
        TrinityPatternOutputRouter router = new TrinityPatternOutputRouter();
        FakePendingOutputCursor pending = cursor(entry(Items.DIAMOND, 10L));
        AtomicLong largestStorageOffer = new AtomicLong();
        AtomicLong inserted = new AtomicLong();

        TrinityPatternOutputRouter.RouteResult result = router.route(
                pending,
                key -> 5L,
                (key, amount, mode) -> {
                    long accepted = Math.min(amount, 3L);
                    if (mode == Actionable.MODULATE) {
                        inserted.addAndGet(accepted);
                    }
                    return accepted;
                },
                (key, amount, mode) -> {
                    largestStorageOffer.accumulateAndGet(amount, Math::max);
                    long accepted = Math.min(amount, 4L);
                    if (mode == Actionable.MODULATE) {
                        inserted.addAndGet(accepted);
                    }
                    return accepted;
                });

        assertTrue(result.progressed());
        assertTrue(result.storageChanged());
        assertEquals(7L, inserted.get());
        assertEquals(5L, largestStorageOffer.get());
        assertEntries(pending.snapshot(), entry(Items.DIAMOND, 3L));
        assertEquals(2L, pending.checkpointCount());
        helper.succeed();
    }

    @TestHolder("trinity_pattern_output_router_does_not_leak_cpu_requests")
    @EmptyTemplate("5")
    @GameTest(template = "empty_5x5")
    public static void neverLeaksTemporarilyRejectedRequestedItemsIntoStorage(GameTestHelper helper) {
        TrinityPatternOutputRouter router = new TrinityPatternOutputRouter();
        FakePendingOutputCursor pending = cursor(entry(Items.DIAMOND, 10L));
        AtomicLong storageModulated = new AtomicLong();

        TrinityPatternOutputRouter.RouteResult result = router.route(
                pending,
                key -> 8L,
                (key, amount, mode) -> 0L,
                (key, amount, mode) -> {
                    if (mode == Actionable.MODULATE) {
                        storageModulated.addAndGet(amount);
                    }
                    return amount;
                });

        assertTrue(result.progressed());
        assertTrue(result.storageChanged());
        assertEquals(2L, storageModulated.get());
        assertEntries(pending.snapshot(), entry(Items.DIAMOND, 8L));
        helper.succeed();
    }

    @TestHolder("trinity_pattern_output_router_reports_cpu_only_progress_without_storage_change")
    @EmptyTemplate("5")
    @GameTest(template = "empty_5x5")
    public static void reportsCpuOnlyProgressWithoutStorageChange(GameTestHelper helper) {
        TrinityPatternOutputRouter router = new TrinityPatternOutputRouter();
        FakePendingOutputCursor pending = cursor(entry(Items.DIAMOND, 5L));
        AtomicBoolean storageTouched = new AtomicBoolean();

        TrinityPatternOutputRouter.RouteResult result = router.route(
                pending,
                key -> 5L,
                (key, amount, mode) -> amount,
                (key, amount, mode) -> {
                    storageTouched.set(true);
                    return amount;
                });

        assertTrue(result.progressed());
        assertFalse(result.storageChanged());
        assertFalse(storageTouched.get());
        assertTrue(pending.snapshot().isEmpty());
        helper.succeed();
    }

    @TestHolder("trinity_pattern_output_router_blocks_later_outputs_behind_cpu_request")
    @EmptyTemplate("5")
    @GameTest(template = "empty_5x5")
    public static void blocksLaterOutputsUntilEarlierCpuRequestIsAccepted(GameTestHelper helper) {
        TrinityPatternOutputRouter router = new TrinityPatternOutputRouter();
        FakePendingOutputCursor pending = cursor(entry(Items.BUCKET, 1L), entry(Items.DIAMOND, 1L));
        AtomicLong bucketRequested = new AtomicLong(1L);
        AtomicBoolean acceptBucket = new AtomicBoolean();
        AtomicBoolean diamondCpuTouched = new AtomicBoolean();
        AtomicBoolean diamondStorageTouched = new AtomicBoolean();

        TrinityPatternOutputRouter.RouteResult blocked = router.route(
                pending,
                key -> key.is(Items.BUCKET) ? bucketRequested.get() : 0L,
                (key, amount, mode) -> {
                    if (key.is(Items.DIAMOND)) {
                        diamondCpuTouched.set(true);
                    }
                    if (!key.is(Items.BUCKET) || !acceptBucket.get()) {
                        return 0L;
                    }
                    if (mode == Actionable.MODULATE) {
                        bucketRequested.addAndGet(-amount);
                    }
                    return amount;
                },
                (key, amount, mode) -> {
                    if (key.is(Items.DIAMOND)) {
                        diamondStorageTouched.set(true);
                    }
                    return amount;
                });

        assertFalse(blocked.progressed());
        assertFalse(blocked.storageChanged());
        assertFalse(diamondCpuTouched.get());
        assertFalse(diamondStorageTouched.get());
        assertEquals(0L, pending.checkpointCount());
        assertEntries(pending.snapshot(), entry(Items.BUCKET, 1L), entry(Items.DIAMOND, 1L));

        acceptBucket.set(true);
        pending = cursor(pending.snapshot());
        TrinityPatternOutputRouter.RouteResult completed = router.route(
                pending,
                key -> key.is(Items.BUCKET) ? bucketRequested.get() : 0L,
                (key, amount, mode) -> {
                    if (key.is(Items.DIAMOND)) {
                        diamondCpuTouched.set(true);
                    }
                    if (!key.is(Items.BUCKET)) {
                        return 0L;
                    }
                    if (mode == Actionable.MODULATE) {
                        bucketRequested.addAndGet(-amount);
                    }
                    return amount;
                },
                (key, amount, mode) -> {
                    if (key.is(Items.DIAMOND)) {
                        diamondStorageTouched.set(true);
                    }
                    return amount;
                });

        assertTrue(completed.progressed());
        assertTrue(completed.storageChanged());
        assertFalse(diamondCpuTouched.get());
        assertTrue(diamondStorageTouched.get());
        assertEquals(2L, pending.checkpointCount());
        assertTrue(pending.snapshot().isEmpty());
        helper.succeed();
    }

    @TestHolder("trinity_pattern_output_router_preserves_storage_remainder")
    @EmptyTemplate("5")
    @GameTest(template = "empty_5x5")
    public static void preservesExactRemainderWhenStorageCapacityIsExhausted(GameTestHelper helper) {
        TrinityPatternOutputRouter router = new TrinityPatternOutputRouter();
        FakePendingOutputCursor pending = cursor(entry(Items.DIAMOND, 6L), entry(Items.GOLD_INGOT, 2L));

        TrinityPatternOutputRouter.RouteResult result = router.route(
                pending,
                key -> 0L,
                (key, amount, mode) -> 0L,
                (key, amount, mode) -> 0L);

        assertFalse(result.progressed());
        assertFalse(result.storageChanged());
        assertEntries(pending.snapshot(), entry(Items.DIAMOND, 6L), entry(Items.GOLD_INGOT, 2L));
        helper.succeed();
    }

    @TestHolder("trinity_pattern_output_router_storage_remainder_does_not_block_later_stack")
    @EmptyTemplate("5")
    @GameTest(template = "empty_5x5")
    public static void storageRemainderDoesNotBlockLaterStack(GameTestHelper helper) {
        TrinityPatternOutputRouter router = new TrinityPatternOutputRouter();
        FakePendingOutputCursor pending = cursor(entry(Items.DIAMOND, 1L), entry(Items.GOLD_INGOT, 1L));
        AtomicBoolean goldReachedStorage = new AtomicBoolean();

        TrinityPatternOutputRouter.RouteResult result = router.route(
                pending,
                key -> 0L,
                (key, amount, mode) -> 0L,
                (key, amount, mode) -> {
                    if (key.is(Items.GOLD_INGOT)) {
                        goldReachedStorage.set(true);
                        return amount;
                    }
                    return 0L;
                });

        assertTrue(result.progressed());
        assertTrue(result.storageChanged());
        assertTrue(goldReachedStorage.get());
        assertEntries(pending.snapshot(), entry(Items.DIAMOND, 1L));
        helper.succeed();
    }

    @TestHolder("trinity_pattern_output_router_checkpoints_before_later_failure")
    @EmptyTemplate("5")
    @GameTest(template = "empty_5x5")
    public static void checkpointsCompletedEntryBeforeLaterSinkFailure(GameTestHelper helper) {
        TrinityPatternOutputRouter router = new TrinityPatternOutputRouter();
        FakePendingOutputCursor firstPending = cursor(entry(Items.DIAMOND, 1L), entry(Items.GOLD_INGOT, 1L));
        AtomicLong insertedDiamonds = new AtomicLong();

        assertIllegalState(() -> router.route(
                firstPending,
                key -> 0L,
                (key, amount, mode) -> 0L,
                (key, amount, mode) -> {
                    if (key.is(Items.GOLD_INGOT)) {
                        throw new IllegalStateException("expected second-entry failure");
                    }
                    if (mode == Actionable.MODULATE) {
                        insertedDiamonds.addAndGet(amount);
                    }
                    return amount;
                }));

        assertEquals(1L, insertedDiamonds.get());
        assertEntries(firstPending.snapshot(), entry(Items.GOLD_INGOT, 1L));

        FakePendingOutputCursor retryPending = cursor(firstPending.snapshot());
        router.route(
                retryPending,
                key -> 0L,
                (key, amount, mode) -> 0L,
                (key, amount, mode) -> amount);
        assertEquals(1L, insertedDiamonds.get());
        assertTrue(retryPending.snapshot().isEmpty());
        helper.succeed();
    }

    @TestHolder("trinity_pattern_output_router_checkpoints_cpu_before_storage_failure")
    @EmptyTemplate("5")
    @GameTest(template = "empty_5x5")
    public static void checkpointsCpuInsertionBeforeSameEntryStorageFailure(GameTestHelper helper) {
        TrinityPatternOutputRouter router = new TrinityPatternOutputRouter();
        FakePendingOutputCursor firstPending = cursor(entry(Items.DIAMOND, 10L));
        AtomicLong requested = new AtomicLong(3L);
        AtomicLong insertedIntoCpu = new AtomicLong();
        AtomicBoolean failStorage = new AtomicBoolean(true);

        assertIllegalState(() -> router.route(
                firstPending,
                key -> requested.get(),
                (key, amount, mode) -> {
                    if (mode == Actionable.MODULATE) {
                        requested.addAndGet(-amount);
                        insertedIntoCpu.addAndGet(amount);
                    }
                    return amount;
                },
                (key, amount, mode) -> {
                    if (failStorage.get()) {
                        throw new IllegalStateException("expected same-entry storage failure");
                    }
                    return amount;
                }));

        assertEquals(3L, insertedIntoCpu.get());
        assertEntries(firstPending.snapshot(), entry(Items.DIAMOND, 7L));

        failStorage.set(false);
        FakePendingOutputCursor retryPending = cursor(firstPending.snapshot());
        router.route(
                retryPending,
                key -> requested.get(),
                (key, amount, mode) -> {
                    throw new GameTestAssertException("CPU must not receive the already completed amount again");
                },
                (key, amount, mode) -> amount);
        assertEquals(3L, insertedIntoCpu.get());
        assertTrue(retryPending.snapshot().isEmpty());
        helper.succeed();
    }

    @TestHolder("trinity_pattern_output_router_checkpoints_before_next_mutation")
    @EmptyTemplate("5")
    @GameTest(template = "empty_5x5")
    public static void checkpointsEveryInsertionBeforeStartingTheNextMutation(GameTestHelper helper) {
        TrinityPatternOutputRouter router = new TrinityPatternOutputRouter();
        List<String> events = new ArrayList<>();
        FakePendingOutputCursor pending = cursor(
                remaining -> events.add("checkpoint-" + totalAmount(remaining)),
                entry(Items.DIAMOND, 10L));

        TrinityPatternOutputRouter.RouteResult result;
        try (pending) {
            result = router.route(
                    pending,
                    key -> 4L,
                    (key, amount, mode) -> {
                        events.add(mode == Actionable.SIMULATE ? "cpu-simulate" : "cpu-modulate");
                        return amount;
                    },
                    (key, amount, mode) -> {
                        events.add(mode == Actionable.SIMULATE ? "storage-simulate" : "storage-modulate");
                        return amount;
                    });
        }

        assertTrue(result.progressed());
        assertTrue(result.storageChanged());
        assertTrue(pending.closed());
        assertTrue(pending.snapshot().isEmpty());
        assertListEquals(
                List.of(
                        "cpu-simulate",
                        "cpu-modulate",
                        "checkpoint-6",
                        "storage-simulate",
                        "storage-modulate",
                        "checkpoint-0"),
                events);
        helper.succeed();
    }

    @TestHolder("trinity_pattern_output_router_rejects_out_of_range_sink_results")
    @EmptyTemplate("5")
    @GameTest(template = "empty_5x5")
    public static void rejectsEveryOutOfRangeSinkResult(GameTestHelper helper) {
        TrinityPatternOutputRouter router = new TrinityPatternOutputRouter();

        assertIllegalStateMessage(
                () -> router.route(
                        cursor(entry(Items.DIAMOND, 1L)),
                        key -> 1L,
                        (key, amount, mode) -> -1L,
                        (key, amount, mode) -> {
                            throw new GameTestAssertException("Storage must not run after invalid CPU simulation");
                        }),
                "Trinity output crafting CPU returned invalid simulate insertion -1 for offer 1");
        assertIllegalStateMessage(
                () -> router.route(
                        cursor(entry(Items.DIAMOND, 1L)),
                        key -> 1L,
                        (key, amount, mode) -> mode == Actionable.SIMULATE ? amount : amount + 1L,
                        (key, amount, mode) -> {
                            throw new GameTestAssertException("Storage must not run after invalid CPU modulation");
                        }),
                "Trinity output crafting CPU returned invalid modulate insertion 2 for offer 1");
        assertIllegalStateMessage(
                () -> router.route(
                        cursor(entry(Items.DIAMOND, 1L)),
                        key -> 0L,
                        (key, amount, mode) -> {
                            throw new GameTestAssertException("CPU must not run without a request");
                        },
                        (key, amount, mode) -> -1L),
                "Trinity output main storage returned invalid simulate insertion -1 for offer 1");
        assertIllegalStateMessage(
                () -> router.route(
                        cursor(entry(Items.DIAMOND, 1L)),
                        key -> 0L,
                        (key, amount, mode) -> {
                            throw new GameTestAssertException("CPU must not run without a request");
                        },
                        (key, amount, mode) -> mode == Actionable.SIMULATE ? amount : amount + 1L),
                "Trinity output main storage returned invalid modulate insertion 2 for offer 1");
        helper.succeed();
    }

    @TestHolder("trinity_pattern_output_router_handles_partial_modulation")
    @EmptyTemplate("5")
    @GameTest(template = "empty_5x5")
    public static void checkpointsOrderedRemaindersAfterPartialModulation(GameTestHelper helper) {
        TrinityPatternOutputRouter router = new TrinityPatternOutputRouter();
        List<Long> checkpointTotals = new ArrayList<>();
        FakePendingOutputCursor pending = cursor(
                remaining -> checkpointTotals.add(totalAmount(remaining)),
                entry(Items.DIAMOND, 10L),
                entry(Items.GOLD_INGOT, 1L));
        AtomicBoolean laterEntryTouched = new AtomicBoolean();

        TrinityPatternOutputRouter.RouteResult result = router.route(
                pending,
                key -> key.is(Items.DIAMOND) ? 5L : 0L,
                (key, amount, mode) -> {
                    if (key.is(Items.GOLD_INGOT)) {
                        laterEntryTouched.set(true);
                    }
                    return mode == Actionable.SIMULATE ? amount : Math.min(amount, 2L);
                },
                (key, amount, mode) -> {
                    if (key.is(Items.GOLD_INGOT)) {
                        laterEntryTouched.set(true);
                    }
                    return mode == Actionable.SIMULATE ? amount : Math.min(amount, 3L);
                });

        assertTrue(result.progressed());
        assertTrue(result.storageChanged());
        assertFalse(laterEntryTouched.get());
        assertLongListEquals(List.of(9L, 6L), checkpointTotals);
        assertEntries(pending.snapshot(), entry(Items.DIAMOND, 5L), entry(Items.GOLD_INGOT, 1L));
        helper.succeed();
    }

    @TestHolder("trinity_pattern_output_router_routes_multiple_long_max_entries")
    @EmptyTemplate("5")
    @GameTest(template = "empty_5x5")
    public static void routesMultipleLongMaxEntriesWithoutAggregatingTheirAmounts(GameTestHelper helper) {
        TrinityPatternOutputRouter router = new TrinityPatternOutputRouter();
        FakePendingOutputCursor pending = cursor(
                entry(Items.DIAMOND, Long.MAX_VALUE),
                entry(Items.GOLD_INGOT, Long.MAX_VALUE));
        List<Long> storageOffers = new ArrayList<>();

        TrinityPatternOutputRouter.RouteResult result = router.route(
                pending,
                key -> 0L,
                (key, amount, mode) -> 0L,
                (key, amount, mode) -> {
                    if (mode == Actionable.MODULATE) {
                        storageOffers.add(amount);
                    }
                    return amount;
                });

        assertTrue(result.progressed());
        assertTrue(result.storageChanged());
        assertLongListEquals(List.of(Long.MAX_VALUE, Long.MAX_VALUE), storageOffers);
        assertEquals(2L, pending.checkpointCount());
        assertTrue(pending.snapshot().isEmpty());
        helper.succeed();
    }

    private static TrinityItemAmount entry(Item item, long amount) {
        return new TrinityItemAmount(AEItemKey.of(item), amount);
    }

    private static FakePendingOutputCursor cursor(TrinityItemAmount... entries) {
        return cursor(ignored -> {}, entries);
    }

    private static FakePendingOutputCursor cursor(List<TrinityItemAmount> entries) {
        return new FakePendingOutputCursor(entries, ignored -> {});
    }

    private static FakePendingOutputCursor cursor(Consumer<List<TrinityItemAmount>> checkpoint,
                                                  TrinityItemAmount... entries) {
        return new FakePendingOutputCursor(List.of(entries), checkpoint);
    }

    private static long totalAmount(List<TrinityItemAmount> entries) {
        long total = 0L;
        for (TrinityItemAmount entry : entries) {
            total = Math.addExact(total, entry.amount());
        }
        return total;
    }

    private static void assertEntries(List<TrinityItemAmount> actual, TrinityItemAmount... expected) {
        if (!actual.equals(List.of(expected))) {
            throw new GameTestAssertException("Expected " + List.of(expected) + " but got " + actual);
        }
    }

    private static void assertTrue(boolean value) {
        if (!value) {
            throw new GameTestAssertException("Expected condition to be true");
        }
    }

    private static void assertFalse(boolean value) {
        if (value) {
            throw new GameTestAssertException("Expected condition to be false");
        }
    }

    private static void assertEquals(long expected, long actual) {
        if (expected != actual) {
            throw new GameTestAssertException("Expected " + expected + " but got " + actual);
        }
    }

    private static void assertListEquals(List<String> expected, List<String> actual) {
        if (!expected.equals(actual)) {
            throw new GameTestAssertException("Expected " + expected + " but got " + actual);
        }
    }

    private static void assertLongListEquals(List<Long> expected, List<Long> actual) {
        if (!expected.equals(actual)) {
            throw new GameTestAssertException("Expected " + expected + " but got " + actual);
        }
    }

    private static void assertIllegalState(Runnable action) {
        try {
            action.run();
        } catch (IllegalStateException exception) {
            return;
        }
        throw new GameTestAssertException("Expected IllegalStateException");
    }

    private static void assertIllegalStateMessage(Runnable action, String expectedMessage) {
        try {
            action.run();
        } catch (IllegalStateException exception) {
            if (!expectedMessage.equals(exception.getMessage())) {
                throw new GameTestAssertException(
                        "Expected IllegalStateException message '" + expectedMessage + "' but got '" +
                                exception.getMessage() + "'");
            }
            return;
        }
        throw new GameTestAssertException("Expected IllegalStateException: " + expectedMessage);
    }

    /** Mutable route state that checkpoints each consumed amount before allowing the router to continue. */
    private static final class FakePendingOutputCursor implements TrinityPatternOutputRouter.PendingOutputCursor {

        private final LinkedList<TrinityItemAmount> entries;
        private final ListIterator<TrinityItemAmount> iterator;
        private final Consumer<List<TrinityItemAmount>> checkpoint;
        private TrinityItemAmount current;
        private long checkpointCount;
        private boolean closed;

        private FakePendingOutputCursor(List<TrinityItemAmount> entries,
                                        Consumer<List<TrinityItemAmount>> checkpoint) {
            this.entries = new LinkedList<>(entries);
            this.iterator = this.entries.listIterator();
            this.checkpoint = checkpoint;
        }

        @Override
        public boolean advance() {
            if (!this.iterator.hasNext()) {
                return false;
            }
            this.current = this.iterator.next();
            return true;
        }

        @Override
        public TrinityItemAmount current() {
            return this.current;
        }

        @Override
        public void consumeCurrent(long amount) {
            if (amount <= 0L || amount > this.current.amount()) {
                throw new IllegalArgumentException("Invalid fake cursor consumption: " + amount);
            }
            long remaining = this.current.amount() - amount;
            if (remaining == 0L) {
                this.iterator.remove();
            } else {
                this.current = this.current.withAmount(remaining);
                this.iterator.set(this.current);
            }
            this.checkpointCount++;
            this.checkpoint.accept(snapshot());
        }

        @Override
        public void close() {
            this.closed = true;
        }

        private List<TrinityItemAmount> snapshot() {
            return List.copyOf(this.entries);
        }

        private long checkpointCount() {
            return this.checkpointCount;
        }

        private boolean closed() {
            return this.closed;
        }
    }
}

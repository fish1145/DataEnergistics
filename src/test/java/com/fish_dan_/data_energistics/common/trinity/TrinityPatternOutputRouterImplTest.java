package com.fish_dan_.data_energistics.common.trinity;

import com.fish_dan_.data_energistics.Data_Energistics;

import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import net.neoforged.testframework.annotation.TestHolder;
import net.neoforged.testframework.gametest.EmptyTemplate;

import appeng.api.config.Actionable;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

@PrefixGameTestTemplate(false)
@GameTestHolder(Data_Energistics.MODID)
public final class TrinityPatternOutputRouterImplTest {

    private TrinityPatternOutputRouterImplTest() {}

    @TestHolder("trinity_pattern_output_router_retains_cpu_requests")
    @EmptyTemplate("5")
    @GameTest(template = "empty_5x5")
    public static void retainsUnacceptedCpuRequestAndStoresOnlyNonRequestedAmount(GameTestHelper helper) {
        TrinityPatternOutputRouter router = new TrinityPatternOutputRouterImpl();
        AtomicLong largestStorageOffer = new AtomicLong();

        TrinityPatternOutputRouter.RoutingResult result = router.route(
                List.of(new ItemStack(Items.DIAMOND, 10)),
                key -> 5L,
                (key, amount, mode) -> Math.min(amount, 3L),
                (key, amount, mode) -> {
                    largestStorageOffer.accumulateAndGet(amount, Math::max);
                    return Math.min(amount, 4L);
                },
                remaining -> {});

        assertEquals(7L, result.inserted());
        assertEquals(5L, largestStorageOffer.get());
        assertEquals(1, result.remaining().size());
        assertEquals(3, result.remaining().getFirst().getCount());
        helper.succeed();
    }

    @TestHolder("trinity_pattern_output_router_does_not_leak_cpu_requests")
    @EmptyTemplate("5")
    @GameTest(template = "empty_5x5")
    public static void neverLeaksTemporarilyRejectedRequestedItemsIntoStorage(GameTestHelper helper) {
        TrinityPatternOutputRouter router = new TrinityPatternOutputRouterImpl();
        AtomicLong storageModulated = new AtomicLong();

        TrinityPatternOutputRouter.RoutingResult result = router.route(
                List.of(new ItemStack(Items.DIAMOND, 10)),
                key -> 8L,
                (key, amount, mode) -> 0L,
                (key, amount, mode) -> {
                    if (mode == Actionable.MODULATE) {
                        storageModulated.addAndGet(amount);
                    }
                    return amount;
                },
                remaining -> {});

        assertEquals(2L, result.inserted());
        assertEquals(2L, storageModulated.get());
        assertEquals(8, result.remaining().getFirst().getCount());
        helper.succeed();
    }

    @TestHolder("trinity_pattern_output_router_blocks_later_outputs_behind_cpu_request")
    @EmptyTemplate("5")
    @GameTest(template = "empty_5x5")
    public static void blocksLaterOutputsUntilEarlierCpuRequestIsAccepted(GameTestHelper helper) {
        TrinityPatternOutputRouter router = new TrinityPatternOutputRouterImpl();
        AtomicReference<List<ItemStack>> persisted = new AtomicReference<>(List.of(
                new ItemStack(Items.BUCKET),
                new ItemStack(Items.DIAMOND)));
        AtomicLong bucketRequested = new AtomicLong(1L);
        AtomicLong checkpointCount = new AtomicLong();
        AtomicBoolean acceptBucket = new AtomicBoolean();
        AtomicBoolean diamondCpuTouched = new AtomicBoolean();
        AtomicBoolean diamondStorageTouched = new AtomicBoolean();

        TrinityPatternOutputRouter.RoutingResult blocked = router.route(
                persisted.get(),
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
                },
                remaining -> {
                    checkpointCount.incrementAndGet();
                    persisted.set(remaining.stream().map(ItemStack::copy).toList());
                });

        assertEquals(0L, blocked.inserted());
        assertFalse(diamondCpuTouched.get());
        assertFalse(diamondStorageTouched.get());
        assertEquals(1L, checkpointCount.get());
        assertStacks(persisted.get(), Items.BUCKET, Items.DIAMOND);
        assertStacks(blocked.remaining(), Items.BUCKET, Items.DIAMOND);

        acceptBucket.set(true);
        checkpointCount.set(0L);
        TrinityPatternOutputRouter.RoutingResult completed = router.route(
                persisted.get(),
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
                },
                remaining -> {
                    checkpointCount.incrementAndGet();
                    persisted.set(remaining.stream().map(ItemStack::copy).toList());
                });

        assertEquals(2L, completed.inserted());
        assertFalse(diamondCpuTouched.get());
        assertTrue(diamondStorageTouched.get());
        assertEquals(2L, checkpointCount.get());
        assertTrue(persisted.get().isEmpty());
        assertTrue(completed.remaining().isEmpty());
        helper.succeed();
    }

    @TestHolder("trinity_pattern_output_router_preserves_storage_remainder")
    @EmptyTemplate("5")
    @GameTest(template = "empty_5x5")
    public static void preservesExactRemainderWhenStorageCapacityIsExhausted(GameTestHelper helper) {
        TrinityPatternOutputRouter router = new TrinityPatternOutputRouterImpl();

        TrinityPatternOutputRouter.RoutingResult result = router.route(
                List.of(new ItemStack(Items.DIAMOND, 6), new ItemStack(Items.GOLD_INGOT, 2)),
                key -> 0L,
                (key, amount, mode) -> 0L,
                (key, amount, mode) -> 0L,
                remaining -> {});

        assertEquals(0L, result.inserted());
        assertEquals(2, result.remaining().size());
        assertEquals(6, result.remaining().get(0).getCount());
        assertEquals(2, result.remaining().get(1).getCount());
        assertTrue(result.remaining().get(0).is(Items.DIAMOND));
        assertTrue(result.remaining().get(1).is(Items.GOLD_INGOT));
        helper.succeed();
    }

    @TestHolder("trinity_pattern_output_router_storage_remainder_does_not_block_later_stack")
    @EmptyTemplate("5")
    @GameTest(template = "empty_5x5")
    public static void storageRemainderDoesNotBlockLaterStack(GameTestHelper helper) {
        TrinityPatternOutputRouter router = new TrinityPatternOutputRouterImpl();
        AtomicBoolean goldReachedStorage = new AtomicBoolean();

        TrinityPatternOutputRouter.RoutingResult result = router.route(
                List.of(new ItemStack(Items.DIAMOND), new ItemStack(Items.GOLD_INGOT)),
                key -> 0L,
                (key, amount, mode) -> 0L,
                (key, amount, mode) -> {
                    if (key.is(Items.GOLD_INGOT)) {
                        goldReachedStorage.set(true);
                        return amount;
                    }
                    return 0L;
                },
                remaining -> {});

        assertTrue(goldReachedStorage.get());
        assertEquals(1L, result.inserted());
        assertStacks(result.remaining(), Items.DIAMOND);
        helper.succeed();
    }

    @TestHolder("trinity_pattern_output_router_checkpoints_before_later_failure")
    @EmptyTemplate("5")
    @GameTest(template = "empty_5x5")
    public static void checkpointsCompletedPrefixBeforeLaterSinkFailure(GameTestHelper helper) {
        TrinityPatternOutputRouter router = new TrinityPatternOutputRouterImpl();
        AtomicReference<List<ItemStack>> persisted = new AtomicReference<>(List.of(
                new ItemStack(Items.DIAMOND),
                new ItemStack(Items.GOLD_INGOT)));
        AtomicLong insertedDiamonds = new AtomicLong();
        boolean failed = false;
        try {
            router.route(
                    persisted.get(),
                    key -> 0L,
                    (key, amount, mode) -> 0L,
                    (key, amount, mode) -> {
                        if (key.is(Items.GOLD_INGOT)) {
                            throw new IllegalStateException("expected second-stack failure");
                        }
                        if (mode == Actionable.MODULATE) {
                            insertedDiamonds.addAndGet(amount);
                        }
                        return amount;
                    },
                    remaining -> persisted.set(remaining.stream().map(ItemStack::copy).toList()));
        } catch (IllegalStateException exception) {
            failed = true;
        }

        assertTrue(failed);
        assertEquals(1L, insertedDiamonds.get());
        assertEquals(1, persisted.get().size());
        assertTrue(persisted.get().getFirst().is(Items.GOLD_INGOT));

        router.route(
                persisted.get(),
                key -> 0L,
                (key, amount, mode) -> 0L,
                (key, amount, mode) -> amount,
                remaining -> persisted.set(remaining.stream().map(ItemStack::copy).toList()));
        assertEquals(1L, insertedDiamonds.get());
        assertTrue(persisted.get().isEmpty());
        helper.succeed();
    }

    @TestHolder("trinity_pattern_output_router_checkpoints_cpu_before_storage_failure")
    @EmptyTemplate("5")
    @GameTest(template = "empty_5x5")
    public static void checkpointsCpuInsertionBeforeSameStackStorageFailure(GameTestHelper helper) {
        TrinityPatternOutputRouter router = new TrinityPatternOutputRouterImpl();
        AtomicReference<List<ItemStack>> persisted = new AtomicReference<>(
                List.of(new ItemStack(Items.DIAMOND, 10)));
        AtomicLong requested = new AtomicLong(3L);
        AtomicLong insertedIntoCpu = new AtomicLong();
        AtomicBoolean failStorage = new AtomicBoolean(true);
        boolean failed = false;
        try {
            router.route(
                    persisted.get(),
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
                            throw new IllegalStateException("expected same-stack storage failure");
                        }
                        return amount;
                    },
                    remaining -> persisted.set(remaining.stream().map(ItemStack::copy).toList()));
        } catch (IllegalStateException exception) {
            failed = true;
        }

        assertTrue(failed);
        assertEquals(3L, insertedIntoCpu.get());
        assertEquals(1, persisted.get().size());
        assertEquals(7, persisted.get().getFirst().getCount());

        failStorage.set(false);
        router.route(
                persisted.get(),
                key -> requested.get(),
                (key, amount, mode) -> {
                    throw new GameTestAssertException("CPU must not receive the already completed amount again");
                },
                (key, amount, mode) -> amount,
                remaining -> persisted.set(remaining.stream().map(ItemStack::copy).toList()));
        assertEquals(3L, insertedIntoCpu.get());
        assertTrue(persisted.get().isEmpty());
        helper.succeed();
    }

    @TestHolder("trinity_pattern_output_router_checkpoints_before_next_mutation")
    @EmptyTemplate("5")
    @GameTest(template = "empty_5x5")
    public static void checkpointsEveryInsertionBeforeStartingTheNextMutation(GameTestHelper helper) {
        TrinityPatternOutputRouter router = new TrinityPatternOutputRouterImpl();
        List<String> events = new ArrayList<>();

        TrinityPatternOutputRouter.RoutingResult result = router.route(
                List.of(new ItemStack(Items.DIAMOND, 10)),
                key -> 4L,
                (key, amount, mode) -> {
                    events.add(mode == Actionable.SIMULATE ? "cpu-simulate" : "cpu-modulate");
                    return amount;
                },
                (key, amount, mode) -> {
                    events.add(mode == Actionable.SIMULATE ? "storage-simulate" : "storage-modulate");
                    return amount;
                },
                remaining -> events.add("checkpoint-" + remaining.stream().mapToInt(ItemStack::getCount).sum()));

        assertEquals(10L, result.inserted());
        assertTrue(result.remaining().isEmpty());
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
        TrinityPatternOutputRouter router = new TrinityPatternOutputRouterImpl();
        List<ItemStack> pending = List.of(new ItemStack(Items.DIAMOND));

        assertIllegalState(
                () -> router.route(
                        pending,
                        key -> 1L,
                        (key, amount, mode) -> -1L,
                        (key, amount, mode) -> {
                            throw new GameTestAssertException("Storage must not run after invalid CPU simulation");
                        },
                        remaining -> {}),
                "Trinity output crafting CPU returned invalid simulate insertion -1 for offer 1");
        assertIllegalState(
                () -> router.route(
                        pending,
                        key -> 1L,
                        (key, amount, mode) -> mode == Actionable.SIMULATE ? amount : amount + 1L,
                        (key, amount, mode) -> {
                            throw new GameTestAssertException("Storage must not run after invalid CPU modulation");
                        },
                        remaining -> {}),
                "Trinity output crafting CPU returned invalid modulate insertion 2 for offer 1");
        assertIllegalState(
                () -> router.route(
                        pending,
                        key -> 0L,
                        (key, amount, mode) -> {
                            throw new GameTestAssertException("CPU must not run without a request");
                        },
                        (key, amount, mode) -> -1L,
                        remaining -> {}),
                "Trinity output main storage returned invalid simulate insertion -1 for offer 1");
        assertIllegalState(
                () -> router.route(
                        pending,
                        key -> 0L,
                        (key, amount, mode) -> {
                            throw new GameTestAssertException("CPU must not run without a request");
                        },
                        (key, amount, mode) -> mode == Actionable.SIMULATE ? amount : amount + 1L,
                        remaining -> {}),
                "Trinity output main storage returned invalid modulate insertion 2 for offer 1");
        helper.succeed();
    }

    @TestHolder("trinity_pattern_output_router_handles_partial_modulation")
    @EmptyTemplate("5")
    @GameTest(template = "empty_5x5")
    public static void checkpointsOrderedRemaindersAfterPartialModulation(GameTestHelper helper) {
        TrinityPatternOutputRouter router = new TrinityPatternOutputRouterImpl();
        AtomicReference<List<ItemStack>> persisted = new AtomicReference<>(List.of(
                new ItemStack(Items.DIAMOND, 10),
                new ItemStack(Items.GOLD_INGOT)));
        List<Integer> checkpointTotals = new ArrayList<>();
        AtomicBoolean laterStackTouched = new AtomicBoolean();

        TrinityPatternOutputRouter.RoutingResult result = router.route(
                persisted.get(),
                key -> key.is(Items.DIAMOND) ? 5L : 0L,
                (key, amount, mode) -> {
                    if (key.is(Items.GOLD_INGOT)) {
                        laterStackTouched.set(true);
                    }
                    return mode == Actionable.SIMULATE ? amount : Math.min(amount, 2L);
                },
                (key, amount, mode) -> {
                    if (key.is(Items.GOLD_INGOT)) {
                        laterStackTouched.set(true);
                    }
                    return mode == Actionable.SIMULATE ? amount : Math.min(amount, 3L);
                },
                remaining -> {
                    List<ItemStack> copied = remaining.stream().map(ItemStack::copy).toList();
                    persisted.set(copied);
                    checkpointTotals.add(copied.stream().mapToInt(ItemStack::getCount).sum());
                });

        assertEquals(5L, result.inserted());
        assertFalse(laterStackTouched.get());
        assertIntegerListEquals(List.of(9, 6), checkpointTotals);
        assertEquals(2L, result.remaining().size());
        assertEquals(5L, result.remaining().getFirst().getCount());
        assertTrue(result.remaining().getFirst().is(Items.DIAMOND));
        assertTrue(result.remaining().get(1).is(Items.GOLD_INGOT));
        assertEquals(2L, persisted.get().size());
        assertEquals(5L, persisted.get().getFirst().getCount());
        assertTrue(persisted.get().getFirst().is(Items.DIAMOND));
        assertTrue(persisted.get().get(1).is(Items.GOLD_INGOT));
        helper.succeed();
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

    private static void assertIntegerListEquals(List<Integer> expected, List<Integer> actual) {
        if (!expected.equals(actual)) {
            throw new GameTestAssertException("Expected " + expected + " but got " + actual);
        }
    }

    private static void assertIllegalState(Runnable action, String expectedMessage) {
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

    private static void assertStacks(List<ItemStack> actual, Item... expected) {
        assertEquals(expected.length, actual.size());
        for (int index = 0; index < expected.length; index++) {
            assertTrue(actual.get(index).is(expected[index]));
            assertEquals(1L, actual.get(index).getCount());
        }
    }
}

package com.fish_dan_.data_energistics.blockentity;

import com.fish_dan_.data_energistics.Data_Energistics;

import net.minecraft.core.component.DataComponents;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.ListTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import net.neoforged.testframework.annotation.TestHolder;
import net.neoforged.testframework.gametest.EmptyTemplate;

import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.GenericStack;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Verifies that mimetic output remains authoritative while external inventories apply backpressure.
 */
@GameTestHolder(Data_Energistics.MODID)
@PrefixGameTestTemplate(false)
public final class MimeticPendingOutputImplTest {

    /** Utility test holder has no instances. */
    private MimeticPendingOutputImplTest() {}

    /**
     * Proves output volume is not constrained by the former 64-slot hidden inventory.
     *
     * @param helper game-test assertions
     */
    @TestHolder("mimetic_pending_output_flushes_more_than_sixty_four_stacks")
    @EmptyTemplate("5")
    @GameTest(template = "empty_5x5")
    public static void flushesMoreThanSixtyFourStacks(GameTestHelper helper) {
        AtomicInteger changes = new AtomicInteger();
        MimeticPendingOutput pending = new MimeticPendingOutputImpl(changes::incrementAndGet);
        List<ItemStack> generated = new ArrayList<>();
        for (int index = 0; index < 65; index++) {
            generated.add(new ItemStack(Items.DIAMOND, 64));
        }
        pending.append(generated);

        AtomicInteger offers = new AtomicInteger();
        AtomicLong accepted = new AtomicLong();
        long flushed = pending.flush(stack -> {
            helper.assertValueEqual(stack.getCount(), 64, "Every offered diamond stack must be a legal maximum stack");
            offers.incrementAndGet();
            accepted.addAndGet(stack.getCount());
            return stack.getCount();
        }, 65);

        helper.assertValueEqual(flushed, 4_160L, "All generated items must flush");
        helper.assertValueEqual(accepted.get(), 4_160L, "The sink must receive all generated items");
        helper.assertValueEqual(offers.get(), 65, "The ledger must not stop at 64 output stacks");
        helper.assertTrue(pending.isEmpty(), "A fully accepted ledger must become empty");
        helper.assertValueEqual(changes.get(), 66, "Append and every accepted output chunk must notify persistence");
        helper.succeed();
    }

    /**
     * Verifies only actually accepted items leave the authoritative balance and later retries resume exactly.
     *
     * @param helper game-test assertions
     */
    @TestHolder("mimetic_pending_output_retains_partial_remainder")
    @EmptyTemplate("5")
    @GameTest(template = "empty_5x5")
    public static void retainsPartialRemainderForRetry(GameTestHelper helper) {
        AtomicInteger changes = new AtomicInteger();
        MimeticPendingOutput pending = new MimeticPendingOutputImpl(changes::incrementAndGet);
        AEItemKey diamond = AEItemKey.of(Items.DIAMOND);
        pending.append(List.of(new ItemStack(Items.DIAMOND, 64), new ItemStack(Items.DIAMOND, 64),
                new ItemStack(Items.DIAMOND, 2)));

        long firstFlush = pending.flush(stack -> 17, 1);

        helper.assertValueEqual(firstFlush, 17L, "A partial sink acceptance must be reported exactly");
        helper.assertValueEqual(pending.amount(diamond), 113L, "Only the 17 accepted items may leave the ledger");
        helper.assertValueEqual(changes.get(), 2, "Append and partial consumption must each notify persistence");

        long retryFlush = pending.flush(ItemStack::getCount, 2);

        helper.assertValueEqual(retryFlush, 113L, "A retry must flush the exact retained remainder");
        helper.assertTrue(pending.isEmpty(), "The successful retry must empty the ledger");
        helper.assertValueEqual(changes.get(), 4, "The two accepted retry chunks must each notify persistence");
        helper.succeed();
    }

    /**
     * Ensures a completely blocked container cannot erase or mutate pending output.
     *
     * @param helper game-test assertions
     */
    @TestHolder("mimetic_pending_output_preserves_fully_blocked_output")
    @EmptyTemplate("5")
    @GameTest(template = "empty_5x5")
    public static void preservesFullyBlockedOutput(GameTestHelper helper) {
        AtomicInteger changes = new AtomicInteger();
        MimeticPendingOutput pending = new MimeticPendingOutputImpl(changes::incrementAndGet);
        AEItemKey pearl = AEItemKey.of(Items.ENDER_PEARL);
        pending.append(List.of(new ItemStack(Items.ENDER_PEARL, 16)));
        AtomicInteger offers = new AtomicInteger();

        long flushed = pending.flush(stack -> {
            offers.incrementAndGet();
            return 0;
        }, 10);

        helper.assertValueEqual(flushed, 0L, "A blocked sink must accept nothing");
        helper.assertValueEqual(offers.get(), 1, "A blocked key must not spin within one flush");
        helper.assertValueEqual(pending.amount(pearl), 16L, "Blocked items must remain authoritative");
        helper.assertValueEqual(changes.get(), 1, "A zero acceptance must not report a state change");
        helper.succeed();
    }

    /**
     * Guards item data components and first-seen key order while equal keys aggregate.
     *
     * @param helper game-test assertions
     */
    @TestHolder("mimetic_pending_output_preserves_components_and_order")
    @EmptyTemplate("5")
    @GameTest(template = "empty_5x5")
    public static void preservesComponentsAndFirstSeenOrder(GameTestHelper helper) {
        MimeticPendingOutput pending = new MimeticPendingOutputImpl(() -> {});
        ItemStack namedDiamond = namedStack(Items.DIAMOND.getDefaultInstance(), "variant-a", 3);
        AEItemKey plainDiamondKey = AEItemKey.of(Items.DIAMOND);
        AEItemKey goldKey = AEItemKey.of(Items.GOLD_INGOT);
        AEItemKey namedDiamondKey = AEItemKey.of(namedDiamond);
        pending.append(List.of(new ItemStack(Items.DIAMOND, 2), new ItemStack(Items.GOLD_INGOT, 4), namedDiamond,
                new ItemStack(Items.DIAMOND, 5)));

        helper.assertValueEqual(pending.amount(plainDiamondKey), 7L, "Equal plain keys must aggregate");
        helper.assertValueEqual(pending.amount(goldKey), 4L, "A second item key must retain its amount");
        helper.assertValueEqual(pending.amount(namedDiamondKey), 3L, "Component-bearing keys must remain distinct");
        List<AEItemKey> order = new ArrayList<>();
        pending.flush(stack -> {
            order.add(AEItemKey.of(stack));
            return stack.getCount();
        }, 3);

        helper.assertValueEqual(order.size(), 3, "Each distinct key must be offered once for these amounts");
        helper.assertTrue(order.get(0).equals(plainDiamondKey), "The first plain key must remain first");
        helper.assertTrue(order.get(1).equals(goldKey), "The first gold key must remain second");
        helper.assertTrue(order.get(2).equals(namedDiamondKey), "The component-bearing key must remain third");
        helper.succeed();
    }

    /**
     * Requires sink contract violations to fail before corrupting authoritative balances.
     *
     * @param helper game-test assertions
     */
    @TestHolder("mimetic_pending_output_rejects_invalid_sink_results")
    @EmptyTemplate("5")
    @GameTest(template = "empty_5x5")
    public static void rejectsInvalidSinkResults(GameTestHelper helper) {
        AtomicInteger changes = new AtomicInteger();
        MimeticPendingOutput pending = new MimeticPendingOutputImpl(changes::incrementAndGet);
        AEItemKey diamond = AEItemKey.of(Items.DIAMOND);
        pending.append(List.of(new ItemStack(Items.DIAMOND, 5)));

        assertIllegalState(() -> pending.flush(stack -> -1, 1));
        helper.assertValueEqual(pending.amount(diamond), 5L, "A negative sink result must not mutate the ledger");
        assertIllegalState(() -> pending.flush(stack -> stack.getCount() + 1, 1));
        helper.assertValueEqual(pending.amount(diamond), 5L, "An excessive sink result must not mutate the ledger");
        helper.assertValueEqual(changes.get(), 1, "Invalid sink results must not report consumption");
        helper.succeed();
    }

    /**
     * Verifies GenericStack NBT preserves long balances, components, aggregation saturation, and order.
     *
     * @param helper game-test assertions and registry access
     */
    @TestHolder("mimetic_pending_output_round_trips_generic_stack_nbt")
    @EmptyTemplate("5")
    @GameTest(template = "empty_5x5")
    public static void roundTripsGenericStackNbt(GameTestHelper helper) {
        MimeticPendingOutput source = new MimeticPendingOutputImpl(() -> {});
        ItemStack namedPearl = namedStack(Items.ENDER_PEARL.getDefaultInstance(), "persisted-variant", 7);
        AEItemKey namedPearlKey = AEItemKey.of(namedPearl);
        AEItemKey diamondKey = AEItemKey.of(Items.DIAMOND);
        source.append(List.of(namedPearl, new ItemStack(Items.DIAMOND, 64), new ItemStack(Items.DIAMOND, 6)));
        ListTag saved = source.writeToNbt(helper.getLevel().registryAccess());

        MimeticPendingOutput restored = new MimeticPendingOutputImpl(() -> {});
        restored.readFromNbt(helper.getLevel().registryAccess(), saved);

        helper.assertValueEqual(restored.amount(namedPearlKey), 7L, "NBT must preserve item components and amount");
        helper.assertValueEqual(restored.amount(diamondKey), 70L, "NBT must preserve aggregated amounts above one stack");
        List<AEItemKey> restoredOrder = new ArrayList<>();
        restored.flush(stack -> {
            AEItemKey key = AEItemKey.of(stack);
            if (restoredOrder.isEmpty() || !restoredOrder.getLast().equals(key)) {
                restoredOrder.add(key);
            }
            return stack.getCount();
        }, 3);
        helper.assertTrue(restoredOrder.equals(List.of(namedPearlKey, diamondKey)),
                "NBT must retain first-seen key order");
        helper.succeed();
    }

    /**
     * Bounds work even when one persisted balance would otherwise require effectively unbounded stack offers.
     *
     * @param helper game-test assertions and registry access
     */
    @TestHolder("mimetic_pending_output_bounds_long_max_value_progress")
    @EmptyTemplate("5")
    @GameTest(template = "empty_5x5")
    public static void boundsLongMaxValueProgress(GameTestHelper helper) {
        AtomicInteger changes = new AtomicInteger();
        AtomicInteger offers = new AtomicInteger();
        MimeticPendingOutput pending = new MimeticPendingOutputImpl(changes::incrementAndGet);
        AEItemKey diamond = AEItemKey.of(Items.DIAMOND);
        ListTag persisted = new ListTag();
        persisted.add(GenericStack.writeTag(helper.getLevel().registryAccess(),
                new GenericStack(diamond, Long.MAX_VALUE)));
        pending.readFromNbt(helper.getLevel().registryAccess(), persisted);

        long flushed = pending.flush(stack -> {
            offers.incrementAndGet();
            return stack.getCount();
        }, 3);

        helper.assertValueEqual(offers.get(), 3, "A flush must not exceed its explicit offer budget");
        helper.assertValueEqual(flushed, 192L, "Three legal diamond offers must make bounded progress");
        helper.assertValueEqual(pending.amount(diamond), Long.MAX_VALUE - 192L,
                "Only actually accepted items may leave a maximum long balance");
        helper.assertValueEqual(changes.get(), 3, "Every accepted offer must notify persistence");
        helper.succeed();
    }

    /**
     * Proves blocked keys rotate across flush calls instead of allowing the first key to starve later keys.
     *
     * @param helper game-test assertions
     */
    @TestHolder("mimetic_pending_output_rotates_blocked_keys_across_flushes")
    @EmptyTemplate("5")
    @GameTest(template = "empty_5x5")
    public static void rotatesBlockedKeysAcrossFlushes(GameTestHelper helper) {
        AtomicInteger changes = new AtomicInteger();
        MimeticPendingOutput pending = new MimeticPendingOutputImpl(changes::incrementAndGet);
        AEItemKey diamond = AEItemKey.of(Items.DIAMOND);
        AEItemKey gold = AEItemKey.of(Items.GOLD_INGOT);
        AEItemKey emerald = AEItemKey.of(Items.EMERALD);
        pending.append(List.of(new ItemStack(Items.DIAMOND), new ItemStack(Items.GOLD_INGOT),
                new ItemStack(Items.EMERALD)));
        List<AEItemKey> offeredKeys = new ArrayList<>();

        for (int flush = 0; flush < 3; flush++) {
            long accepted = pending.flush(stack -> {
                offeredKeys.add(AEItemKey.of(stack));
                return 0;
            }, 1);
            helper.assertValueEqual(accepted, 0L, "A blocked sink must report no accepted items");
        }

        helper.assertTrue(offeredKeys.equals(List.of(diamond, gold, emerald)),
                "Successive bounded flushes must offer every blocked key fairly");
        offeredKeys.clear();

        pending.flush(stack -> {
            offeredKeys.add(AEItemKey.of(stack));
            return 0;
        }, 10);

        helper.assertTrue(offeredKeys.equals(List.of(diamond, gold, emerald)),
                "A flush must stop after one fully rejected round even when budget remains");
        helper.assertValueEqual(changes.get(), 1, "Scheduling blocked keys must not report balance changes");
        helper.succeed();
    }

    /**
     * Keeps flush work proportional to a small budget while a large blocked ledger advances fairly across calls.
     *
     * @param helper game-test assertions
     */
    @TestHolder("mimetic_pending_output_bounds_large_blocked_ledger_work")
    @EmptyTemplate("5")
    @GameTest(template = "empty_5x5")
    public static void boundsLargeBlockedLedgerWork(GameTestHelper helper) {
        AtomicInteger changes = new AtomicInteger();
        MimeticPendingOutput pending = new MimeticPendingOutputImpl(changes::incrementAndGet);
        List<ItemStack> generated = new ArrayList<>();
        List<AEItemKey> expectedOrder = new ArrayList<>();
        for (int index = 0; index < 256; index++) {
            ItemStack stack = namedStack(Items.DIAMOND.getDefaultInstance(), "blocked-" + index, 1);
            generated.add(stack);
            expectedOrder.add(AEItemKey.of(stack));
        }
        pending.append(generated);
        List<AEItemKey> offeredKeys = new ArrayList<>();

        for (int flush = 0; flush < 2; flush++) {
            long accepted = pending.flush(stack -> {
                offeredKeys.add(AEItemKey.of(stack));
                return 0;
            }, 3);
            helper.assertValueEqual(accepted, 0L, "A blocked sink must report no accepted items");
        }

        helper.assertValueEqual(offeredKeys.size(), 6,
                "Two flushes must invoke the sink only within their combined budgets");
        helper.assertTrue(offeredKeys.equals(expectedOrder.subList(0, 6)),
                "A later bounded flush must continue after the previously offered keys");
        helper.assertValueEqual(changes.get(), 1, "Blocked scheduling must not report balance mutations");
        helper.succeed();
    }

    /**
     * Rejects budgets that cannot permit a sink offer before invoking or mutating anything.
     *
     * @param helper game-test assertions
     */
    @TestHolder("mimetic_pending_output_rejects_invalid_offer_budgets")
    @EmptyTemplate("5")
    @GameTest(template = "empty_5x5")
    public static void rejectsInvalidOfferBudgets(GameTestHelper helper) {
        AtomicInteger changes = new AtomicInteger();
        AtomicInteger offers = new AtomicInteger();
        MimeticPendingOutput pending = new MimeticPendingOutputImpl(changes::incrementAndGet);
        AEItemKey diamond = AEItemKey.of(Items.DIAMOND);
        pending.append(List.of(new ItemStack(Items.DIAMOND, 5)));

        assertIllegalArgument(() -> pending.flush(stack -> offers.incrementAndGet(), 0));
        assertIllegalArgument(() -> pending.flush(stack -> offers.incrementAndGet(), -1));

        helper.assertValueEqual(offers.get(), 0, "An invalid budget must fail before invoking the sink");
        helper.assertValueEqual(pending.amount(diamond), 5L, "An invalid budget must preserve the ledger");
        helper.assertValueEqual(changes.get(), 1, "An invalid budget must not report a state change");
        helper.succeed();
    }

    /**
     * Makes an overflowing append atomic even when a preceding new key could otherwise be inserted first.
     *
     * @param helper game-test assertions and registry access
     */
    @TestHolder("mimetic_pending_output_rejects_append_overflow_atomically")
    @EmptyTemplate("5")
    @GameTest(template = "empty_5x5")
    public static void rejectsAppendOverflowAtomically(GameTestHelper helper) {
        AtomicInteger changes = new AtomicInteger();
        MimeticPendingOutput pending = new MimeticPendingOutputImpl(changes::incrementAndGet);
        AEItemKey diamond = AEItemKey.of(Items.DIAMOND);
        AEItemKey emerald = AEItemKey.of(Items.EMERALD);
        ListTag persisted = new ListTag();
        persisted.add(GenericStack.writeTag(helper.getLevel().registryAccess(),
                new GenericStack(diamond, Long.MAX_VALUE)));
        pending.readFromNbt(helper.getLevel().registryAccess(), persisted);

        assertArithmetic(() -> pending.append(
                List.of(new ItemStack(Items.EMERALD), new ItemStack(Items.DIAMOND))));

        helper.assertValueEqual(pending.amount(diamond), Long.MAX_VALUE,
                "An overflowing append must preserve the existing maximum balance");
        helper.assertValueEqual(pending.amount(emerald), 0L,
                "An overflowing append must not retain keys staged before the overflow");
        List<AEItemKey> offeredAfterFailure = new ArrayList<>();
        pending.flush(stack -> {
            offeredAfterFailure.add(AEItemKey.of(stack));
            return 0;
        }, 1);
        helper.assertTrue(offeredAfterFailure.equals(List.of(diamond)),
                "An overflowing append must preserve the existing scheduling state");
        helper.assertValueEqual(changes.get(), 0, "A rejected append must not notify persistence");
        helper.succeed();
    }

    /**
     * Makes duplicate-key NBT aggregation atomic when persisted amounts exceed the long range.
     *
     * @param helper game-test assertions and registry access
     */
    @TestHolder("mimetic_pending_output_rejects_nbt_aggregation_overflow_atomically")
    @EmptyTemplate("5")
    @GameTest(template = "empty_5x5")
    public static void rejectsNbtAggregationOverflowAtomically(GameTestHelper helper) {
        AtomicInteger changes = new AtomicInteger();
        MimeticPendingOutput pending = new MimeticPendingOutputImpl(changes::incrementAndGet);
        AEItemKey gold = AEItemKey.of(Items.GOLD_INGOT);
        AEItemKey diamond = AEItemKey.of(Items.DIAMOND);
        pending.append(List.of(new ItemStack(Items.GOLD_INGOT, 4)));
        ListTag overflowing = new ListTag();
        overflowing.add(GenericStack.writeTag(helper.getLevel().registryAccess(),
                new GenericStack(diamond, Long.MAX_VALUE)));
        overflowing.add(GenericStack.writeTag(helper.getLevel().registryAccess(), new GenericStack(diamond, 1L)));

        assertArithmetic(() -> pending.readFromNbt(helper.getLevel().registryAccess(), overflowing));

        helper.assertValueEqual(pending.amount(gold), 4L,
                "Rejected NBT must preserve every pre-existing balance");
        helper.assertValueEqual(pending.amount(diamond), 0L,
                "Rejected NBT must not install its partially aggregated entries");
        List<AEItemKey> offeredAfterFailure = new ArrayList<>();
        pending.flush(stack -> {
            offeredAfterFailure.add(AEItemKey.of(stack));
            return 0;
        }, 1);
        helper.assertTrue(offeredAfterFailure.equals(List.of(gold)),
                "Rejected NBT must preserve the existing scheduling state");
        helper.assertValueEqual(changes.get(), 1, "Rejected NBT must not report a runtime mutation");
        helper.succeed();
    }

    /**
     * Ensures block destruction can materialize every practical balance as legal stacks before clearing it.
     *
     * @param helper game-test assertions
     */
    @TestHolder("mimetic_pending_output_materializes_legal_item_stacks")
    @EmptyTemplate("5")
    @GameTest(template = "empty_5x5")
    public static void materializesLegalItemStacksAndClears(GameTestHelper helper) {
        AtomicInteger changes = new AtomicInteger();
        MimeticPendingOutput pending = new MimeticPendingOutputImpl(changes::incrementAndGet);
        AEItemKey pearl = AEItemKey.of(Items.ENDER_PEARL);
        AEItemKey diamond = AEItemKey.of(Items.DIAMOND);
        pending.append(List.of(new ItemStack(Items.ENDER_PEARL, 16), new ItemStack(Items.ENDER_PEARL, 16),
                new ItemStack(Items.ENDER_PEARL, 3), new ItemStack(Items.DIAMOND, 64),
                new ItemStack(Items.DIAMOND, 64), new ItemStack(Items.DIAMOND, 2)));

        List<ItemStack> materialized = pending.toItemStacks();

        helper.assertValueEqual(materialized.size(), 6, "Both keys must split into their legal stack counts");
        for (ItemStack stack : materialized) {
            helper.assertTrue(!stack.isEmpty(), "Materialized output cannot contain empty stacks");
            helper.assertTrue(stack.getCount() <= stack.getMaxStackSize(),
                    "Materialized output cannot exceed its component-aware maximum stack size");
        }
        helper.assertValueEqual(totalFor(materialized, pearl), 35L, "All pearls must materialize");
        helper.assertValueEqual(totalFor(materialized, diamond), 130L, "All diamonds must materialize");
        helper.assertValueEqual(pending.amount(pearl), 35L, "Materialization must not consume the ledger before drops spawn");
        pending.clear();
        helper.assertTrue(pending.isEmpty(), "Explicit clear must remove all materialized balances");
        helper.assertValueEqual(changes.get(), 2, "Append and non-empty clear must notify persistence once each");
        pending.clear();
        helper.assertValueEqual(changes.get(), 2, "Clearing an already empty ledger must not notify again");
        helper.succeed();
    }

    /**
     * Creates a component-bearing stack without mutating shared item defaults.
     *
     * @param stack source stack
     * @param name  custom name text
     * @param count desired count
     * @return component-bearing stack
     */
    private static ItemStack namedStack(ItemStack stack, String name, int count) {
        stack.set(DataComponents.CUSTOM_NAME, Component.literal(name));
        stack.setCount(count);
        return stack;
    }

    /**
     * Adds counts matching one full AE item key.
     *
     * @param stacks materialized stacks
     * @param key    expected key including components
     * @return matching item count
     */
    private static long totalFor(List<ItemStack> stacks, AEItemKey key) {
        return stacks.stream().filter(stack -> key.equals(AEItemKey.of(stack))).mapToLong(ItemStack::getCount).sum();
    }

    /**
     * Asserts fail-fast handling without relying on reflection or source inspection.
     *
     * @param action operation expected to reject invalid sink behavior
     */
    private static void assertIllegalState(Runnable action) {
        try {
            action.run();
        } catch (IllegalStateException exception) {
            return;
        }
        throw new GameTestAssertException("Expected IllegalStateException");
    }

    /**
     * Asserts fail-fast handling of an invalid caller argument.
     *
     * @param action operation expected to reject its argument
     */
    private static void assertIllegalArgument(Runnable action) {
        try {
            action.run();
        } catch (IllegalArgumentException exception) {
            return;
        }
        throw new GameTestAssertException("Expected IllegalArgumentException");
    }

    /**
     * Asserts fail-fast handling of exact long aggregation overflow.
     *
     * @param action operation expected to exceed the long range
     */
    private static void assertArithmetic(Runnable action) {
        try {
            action.run();
        } catch (ArithmeticException exception) {
            return;
        }
        throw new GameTestAssertException("Expected ArithmeticException");
    }
}

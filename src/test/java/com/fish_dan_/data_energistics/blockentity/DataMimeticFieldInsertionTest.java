package com.fish_dan_.data_energistics.blockentity;

import com.fish_dan_.data_energistics.Data_Energistics;

import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import net.neoforged.neoforge.items.IItemHandler;
import net.neoforged.testframework.annotation.TestHolder;
import net.neoforged.testframework.gametest.EmptyTemplate;

import appeng.api.stacks.AEItemKey;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Verifies that external output failures cannot erase or replay already confirmed mimetic items. */
@GameTestHolder(Data_Energistics.MODID)
@PrefixGameTestTemplate(false)
public final class DataMimeticFieldInsertionTest {

    private DataMimeticFieldInsertionTest() {}

    /**
     * Confirms a later failing slot does not hide an earlier slot's successful insertion from the ledger.
     *
     * @param helper game-test assertions
     */
    @TestHolder("data_mimetic_field_retains_only_unconfirmed_remainder_after_slot_failure")
    @EmptyTemplate("5")
    @GameTest(template = "empty_5x5")
    public static void retainsOnlyUnconfirmedRemainderAfterSlotFailure(GameTestHelper helper) {
        MimeticPendingOutput pending = new MimeticPendingOutputImpl(() -> {});
        AEItemKey diamond = AEItemKey.of(Items.DIAMOND);
        pending.append(List.of(new ItemStack(Items.DIAMOND, 8)));
        PartiallyFailingItemHandler handler = new PartiallyFailingItemHandler();

        long flushed = DataMimeticFieldBlockEntity.flushIntoAdjacentContainers(
                pending,
                Map.of(Direction.NORTH, handler),
                new HashMap<>(),
                2);

        helper.assertValueEqual(flushed, 3L, "The ledger must confirm only the completed first-slot insertion");
        helper.assertValueEqual(handler.storedCount(), 3, "The first slot must retain its completed insertion");
        helper.assertValueEqual(pending.amount(diamond), 5L, "The unconfirmed remainder must stay pending for retry");
        helper.succeed();
    }

    /**
     * Confirms an exception before any insertion leaves the authoritative balance untouched.
     *
     * @param helper game-test assertions
     */
    @TestHolder("data_mimetic_field_preserves_offer_when_first_slot_fails")
    @EmptyTemplate("5")
    @GameTest(template = "empty_5x5")
    public static void preservesOfferWhenFirstSlotFails(GameTestHelper helper) {
        MimeticPendingOutput pending = new MimeticPendingOutputImpl(() -> {});
        AEItemKey diamond = AEItemKey.of(Items.DIAMOND);
        pending.append(List.of(new ItemStack(Items.DIAMOND, 8)));

        long flushed = DataMimeticFieldBlockEntity.flushIntoAdjacentContainers(
                pending,
                Map.of(Direction.NORTH, new ImmediatelyFailingItemHandler()),
                new HashMap<>(),
                1);

        helper.assertValueEqual(flushed, 0L, "A first-slot exception must report no confirmed insertion");
        helper.assertValueEqual(pending.amount(diamond), 8L, "A first-slot exception must preserve the complete offer");
        helper.succeed();
    }

    /**
     * Bounds a huge rejecting handler to the configured number of real slot calls.
     *
     * @param helper game-test assertions
     */
    @TestHolder("data_mimetic_field_bounds_container_slot_visits")
    @EmptyTemplate("5")
    @GameTest(template = "empty_5x5")
    public static void boundsContainerSlotVisits(GameTestHelper helper) {
        MimeticPendingOutput pending = new MimeticPendingOutputImpl(() -> {});
        AEItemKey diamond = AEItemKey.of(Items.DIAMOND);
        pending.append(List.of(new ItemStack(Items.DIAMOND)));
        HugeRejectingItemHandler handler = new HugeRejectingItemHandler();

        long flushed = DataMimeticFieldBlockEntity.flushIntoAdjacentContainers(
                pending,
                Map.of(Direction.NORTH, handler),
                new HashMap<>(),
                64);

        helper.assertValueEqual(flushed, 0L, "A rejecting handler must not consume pending output");
        helper.assertValueEqual(handler.insertCalls(), 64, "The real slot-call count must equal the fixed offer budget");
        helper.assertValueEqual(handler.firstSlot(), 0, "The bounded scan must start at the first slot");
        helper.assertValueEqual(handler.lastSlot(), 63, "The bounded scan must resume rather than rescan slot zero");
        helper.assertValueEqual(pending.amount(diamond), 1L, "Rejected output must remain pending");
        helper.succeed();
    }

    /**
     * Keeps independent item cursors from phase-locking against type-restricted slots.
     *
     * @param helper game-test assertions
     */
    @TestHolder("data_mimetic_field_avoids_container_cursor_phase_lock")
    @EmptyTemplate("5")
    @GameTest(template = "empty_5x5")
    public static void avoidsContainerCursorPhaseLock(GameTestHelper helper) {
        MimeticPendingOutput pending = new MimeticPendingOutputImpl(() -> {});
        pending.append(List.of(new ItemStack(Items.DIAMOND), new ItemStack(Items.GOLD_INGOT)));
        TypeRestrictedItemHandler handler = new TypeRestrictedItemHandler();

        long flushed = DataMimeticFieldBlockEntity.flushIntoAdjacentContainers(
                pending,
                Map.of(Direction.NORTH, handler),
                new HashMap<>(),
                4);

        helper.assertValueEqual(flushed, 2L, "Both type-restricted pending items must reach their matching slot");
        helper.assertValueEqual(handler.diamonds(), 1, "The diamond must reach slot one");
        helper.assertValueEqual(handler.goldIngots(), 1, "The gold ingot must reach slot zero");
        helper.assertTrue(pending.isEmpty(), "Both independently scheduled keys must be drained");
        helper.succeed();
    }

    /**
     * Rejects impossible AE return values before they can mutate a pending balance.
     *
     * @param helper game-test assertions
     */
    @TestHolder("data_mimetic_field_rejects_invalid_ae_acceptance")
    @EmptyTemplate("5")
    @GameTest(template = "empty_5x5")
    public static void rejectsInvalidAeAcceptance(GameTestHelper helper) {
        helper.assertValueEqual(
                DataMimeticFieldBlockEntity.requireValidAcceptedAmount(3L, 5L, "test AE storage"),
                3L,
                "A legal partial AE acceptance must pass through unchanged");
        assertIllegalState(() -> DataMimeticFieldBlockEntity.requireValidAcceptedAmount(-1L, 5L, "test AE storage"));
        assertIllegalState(() -> DataMimeticFieldBlockEntity.requireValidAcceptedAmount(6L, 5L, "test AE storage"));
        helper.succeed();
    }

    private static void assertIllegalState(Runnable action) {
        try {
            action.run();
        } catch (IllegalStateException exception) {
            return;
        }
        throw new GameTestAssertException("Expected IllegalStateException");
    }

    private static final class PartiallyFailingItemHandler implements IItemHandler {

        private int storedCount;

        @Override
        public int getSlots() {
            return 2;
        }

        @Override
        public ItemStack getStackInSlot(int slot) {
            return slot == 0 && this.storedCount > 0 ? new ItemStack(Items.DIAMOND, this.storedCount) : ItemStack.EMPTY;
        }

        @Override
        public ItemStack insertItem(int slot, ItemStack stack, boolean simulate) {
            if (slot == 1) {
                throw new SimulatedInsertionFailure();
            }
            int accepted = Math.min(3, stack.getCount());
            if (!simulate) {
                this.storedCount += accepted;
            }
            ItemStack remaining = stack.copy();
            remaining.shrink(accepted);
            return remaining;
        }

        @Override
        public ItemStack extractItem(int slot, int amount, boolean simulate) {
            return ItemStack.EMPTY;
        }

        @Override
        public int getSlotLimit(int slot) {
            return 64;
        }

        @Override
        public boolean isItemValid(int slot, ItemStack stack) {
            return stack.is(Items.DIAMOND);
        }

        private int storedCount() {
            return this.storedCount;
        }
    }

    private static final class ImmediatelyFailingItemHandler implements IItemHandler {

        @Override
        public int getSlots() {
            return 1;
        }

        @Override
        public ItemStack getStackInSlot(int slot) {
            return ItemStack.EMPTY;
        }

        @Override
        public ItemStack insertItem(int slot, ItemStack stack, boolean simulate) {
            throw new SimulatedInsertionFailure();
        }

        @Override
        public ItemStack extractItem(int slot, int amount, boolean simulate) {
            return ItemStack.EMPTY;
        }

        @Override
        public int getSlotLimit(int slot) {
            return 64;
        }

        @Override
        public boolean isItemValid(int slot, ItemStack stack) {
            return true;
        }
    }

    private static final class HugeRejectingItemHandler implements IItemHandler {

        private int insertCalls;
        private int firstSlot = -1;
        private int lastSlot = -1;

        @Override
        public int getSlots() {
            return Integer.MAX_VALUE;
        }

        @Override
        public ItemStack getStackInSlot(int slot) {
            return ItemStack.EMPTY;
        }

        @Override
        public ItemStack insertItem(int slot, ItemStack stack, boolean simulate) {
            if (this.firstSlot < 0) {
                this.firstSlot = slot;
            }
            this.lastSlot = slot;
            this.insertCalls++;
            return stack;
        }

        @Override
        public ItemStack extractItem(int slot, int amount, boolean simulate) {
            return ItemStack.EMPTY;
        }

        @Override
        public int getSlotLimit(int slot) {
            return 0;
        }

        @Override
        public boolean isItemValid(int slot, ItemStack stack) {
            return false;
        }

        private int insertCalls() {
            return this.insertCalls;
        }

        private int firstSlot() {
            return this.firstSlot;
        }

        private int lastSlot() {
            return this.lastSlot;
        }
    }

    private static final class TypeRestrictedItemHandler implements IItemHandler {

        private int diamonds;
        private int goldIngots;

        @Override
        public int getSlots() {
            return 2;
        }

        @Override
        public ItemStack getStackInSlot(int slot) {
            return ItemStack.EMPTY;
        }

        @Override
        public ItemStack insertItem(int slot, ItemStack stack, boolean simulate) {
            if (slot == 0 && stack.is(Items.GOLD_INGOT)) {
                if (!simulate) {
                    this.goldIngots += stack.getCount();
                }
                return ItemStack.EMPTY;
            }
            if (slot == 1 && stack.is(Items.DIAMOND)) {
                if (!simulate) {
                    this.diamonds += stack.getCount();
                }
                return ItemStack.EMPTY;
            }
            return stack;
        }

        @Override
        public ItemStack extractItem(int slot, int amount, boolean simulate) {
            return ItemStack.EMPTY;
        }

        @Override
        public int getSlotLimit(int slot) {
            return 64;
        }

        @Override
        public boolean isItemValid(int slot, ItemStack stack) {
            return slot == 0 && stack.is(Items.GOLD_INGOT) || slot == 1 && stack.is(Items.DIAMOND);
        }

        private int diamonds() {
            return this.diamonds;
        }

        private int goldIngots() {
            return this.goldIngots;
        }
    }

    private static final class SimulatedInsertionFailure extends RuntimeException {

        private static final long serialVersionUID = 1L;
    }
}

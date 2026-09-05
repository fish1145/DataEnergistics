package com.fish_dan_.data_energistics.common.crafting.trinity.execution.cpu;

import com.fish_dan_.data_energistics.Data_Energistics;
import com.fish_dan_.data_energistics.common.crafting.trinity.execution.cpu.DynamicCraftingOutputLedger.Match;
import com.fish_dan_.data_energistics.common.crafting.trinity.execution.cpu.DynamicCraftingOutputLedger.Registration;
import com.fish_dan_.data_energistics.common.crafting.trinity.execution.cpu.DynamicCraftingOutputLedger.Route;

import appeng.api.stacks.AEFluidKey;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.AEKeyTypes;

import net.minecraft.core.component.DataComponents;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.material.Fluids;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import net.neoforged.testframework.annotation.TestHolder;
import net.neoforged.testframework.gametest.EmptyTemplate;

import java.math.BigInteger;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

@GameTestHolder(Data_Energistics.MODID)
@PrefixGameTestTemplate(false)
public final class CancelledCraftingAccountingGameTest {

    private CancelledCraftingAccountingGameTest() {}

    @TestHolder("dynamic_output_cancellation_is_exact_atomic_and_preserves_received_aliases")
    @EmptyTemplate("5")
    @GameTest(template = "empty_5x5")
    public static void dynamicOutputCancellationIsExactAtomicAndPreservesReceivedAliases(GameTestHelper helper) {
        var registries = helper.getLevel().registryAccess();
        AEItemKey key = AEItemKey.of(Items.PAPER);
        ResourceLocation first = Data_Energistics.id("cancel_first");
        ResourceLocation second = Data_Energistics.id("cancel_second");
        DynamicCraftingOutputLedger ledger = new DynamicCraftingOutputLedger();
        ledger.register(List.of(new Registration(key, 5L, Route.INVENTORY, first),
                new Registration(key, 7L, Route.INVENTORY, second)));
        ledger.consume(new Match(key, 2L, Route.INVENTORY, first), 2L);
        ItemStack received = new ItemStack(Items.PAPER);
        received.set(DataComponents.CUSTOM_NAME, Component.literal("already received"));
        AEItemKey actual = AEItemKey.of(received);
        ledger.recordInputAlias(actual, 2L);

        assertRejectedAtomically(helper, () -> ledger.prepareWithdrawal(List.of(
                new Registration(key, 1L, Route.INVENTORY, first),
                new Registration(key, 1L, Route.FINAL_OUTPUT, second))), () -> ledger.writeToTag(registries));
        assertRejectedAtomically(helper, () -> ledger.prepareWithdrawal(List.of(
                new Registration(key, 2L, Route.INVENTORY, first),
                new Registration(key, 2L, Route.INVENTORY, first))), () -> ledger.writeToTag(registries));
        assertRejectedAtomically(helper, () -> ledger.prepareWithdrawal(List.of(
                new Registration(key, 1L, Route.INVENTORY, Data_Energistics.id("unknown_source")))), () -> ledger.writeToTag(registries));

        CompoundTag beforePrepared = ledger.writeToTag(registries);
        Runnable withdrawal = ledger.prepareWithdrawal(List.of(new Registration(key, 1L, Route.INVENTORY, first),
                new Registration(key, 2L, Route.INVENTORY, first)));
        helper.assertValueEqual(ledger.writeToTag(registries), beforePrepared, "Preparing a valid withdrawal must not mutate the ledger");
        withdrawal.run();
        assertRejectedAtomically(helper, withdrawal, () -> ledger.writeToTag(registries));
        DynamicCraftingOutputLedger expected = new DynamicCraftingOutputLedger();
        expected.register(List.of(new Registration(key, 7L, Route.INVENTORY, second)));
        expected.recordInputAlias(actual, 2L);
        helper.assertValueEqual(ledger.writeToTag(registries), expected.writeToTag(registries),
                "Cancelling the uncompleted prefix must leave the other source and received physical alias untouched");
        var restored = DynamicCraftingOutputLedger.readFromTag(ledger.writeToTag(registries), registries);
        restored.prepareWithdrawal(List.of(new Registration(key, 7L, Route.INVENTORY, second))).run();
        helper.assertTrue(restored.isEmpty() && restored.isInputAlias(actual),
                "After reload, withdrawing the last expectation still must not erase received inventory ownership");
        helper.succeed();
    }

    @TestHolder("cancelled_started_work_is_removed_without_counting_it_as_completed")
    @EmptyTemplate("5")
    @GameTest(template = "empty_5x5")
    public static void cancelledStartedWorkIsRemovedWithoutCountingItAsCompleted(GameTestHelper helper) {
        AEItemKey paper = AEItemKey.of(Items.PAPER);
        AEItemKey diamond = AEItemKey.of(Items.DIAMOND);
        AEFluidKey water = AEFluidKey.of(Fluids.WATER);
        TrinityDataCoreElapsedTimeTracker tracker = new TrinityDataCoreElapsedTimeTracker();
        tracker.initializePlanBaseline(Map.of(paper, BigInteger.valueOf(5L), diamond, BigInteger.valueOf(5L),
                water, BigInteger.valueOf(1000L)));
        tracker.decrementItems(3L, paper.getType());
        tracker.decrementItems(250L, water.getType());
        CompoundTag completedBefore = tracker.writeToTag();
        assertRejectedAtomically(helper, () -> tracker.prepareUncompletedWithdrawal(Map.of(
                paper, BigInteger.ONE, water, BigInteger.valueOf(751L))), tracker::writeToTag);

        CompoundTag beforePrepared = tracker.writeToTag();
        Runnable withdrawal = tracker.prepareUncompletedWithdrawal(Map.of(paper, BigInteger.valueOf(2L), diamond, BigInteger.valueOf(5L),
                water, BigInteger.valueOf(500L)));
        helper.assertValueEqual(tracker.writeToTag(), beforePrepared, "Preparing a valid cancellation must not mutate progress or elapsed time");
        withdrawal.run();
        assertRejectedAtomically(helper, withdrawal, tracker::writeToTag);
        CompoundTag state = tracker.writeToTag();
        assertCompletedWorkEqual(helper, state, completedBefore,
                "Cancellation must neither increment nor decrement completed work");
        helper.assertValueEqual(work(state, "started_work", paper.getType().getId()), BigInteger.valueOf(3L),
                "Different item keys are aggregated in their shared item-type units before withdrawal");
        helper.assertValueEqual(work(state, "started_work", water.getType().getId()), BigInteger.valueOf(500L),
                "Fluid cancellation stays in native amount units rather than GUI display units");
        helper.assertTrue(tracker.remainingItemCount() > 0L, "Uncompleted fluid output must remain in visible progress");
        TrinityDataCoreElapsedTimeTracker restored = new TrinityDataCoreElapsedTimeTracker(state);
        restored.prepareUncompletedWithdrawal(Map.of(water, BigInteger.valueOf(250L))).run();
        helper.assertValueEqual(restored.remainingItemCount(), 0L, "Withdrawing exactly all uncompleted work leaves only real completion");
        assertCompletedWorkEqual(helper, restored.writeToTag(), completedBefore,
                "Reload does not turn cancellation into a completion receipt");
        assertRejectedAtomically(helper, () -> restored.prepareUncompletedWithdrawal(Map.of(paper, BigInteger.ONE)), restored::writeToTag);
        helper.succeed();
    }

    private static BigInteger work(CompoundTag state, String bucket, ResourceLocation type) {
        CompoundTag values = state.getCompound(bucket);
        return values.contains(type.toString()) ? new BigInteger(values.getByteArray(type.toString())) : BigInteger.ZERO;
    }

    private static void assertCompletedWorkEqual(GameTestHelper helper, CompoundTag actual, CompoundTag expected, String message) {
        for (var type : AEKeyTypes.getAll()) {
            helper.assertValueEqual(work(actual, "completed_work", type.getId()), work(expected, "completed_work", type.getId()),
                    message + " (" + type.getId() + ")");
        }
    }

    private static void assertRejectedAtomically(GameTestHelper helper, Runnable action, Supplier<CompoundTag> state) {
        CompoundTag before = state.get();
        try {
            action.run();
        } catch (IllegalStateException expected) {
            helper.assertValueEqual(state.get(), before, "Rejected cancellation must preserve all ledger/progress state");
            return;
        }
        helper.fail("Invalid cancellation must reject instead of silently consuming another registration or completed work");
    }
}

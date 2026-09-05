package com.fish_dan_.data_energistics.common.crafting.trinity.execution.runtime;

import com.fish_dan_.data_energistics.Data_Energistics;
import com.fish_dan_.data_energistics.common.crafting.trinity.planning.sameitem.TrinitySameItemPolicy;

import appeng.api.config.Actionable;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.GenericStack;
import appeng.api.stacks.KeyCounter;
import appeng.crafting.inv.ListCraftingInventory;

import net.minecraft.core.component.DataComponents;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import net.neoforged.testframework.annotation.TestHolder;
import net.neoforged.testframework.gametest.EmptyTemplate;

import java.util.List;

@GameTestHolder(Data_Energistics.MODID)
@PrefixGameTestTemplate(false)
public final class TrinitySameItemRuntimeInventoryGameTest {

    private TrinitySameItemRuntimeInventoryGameTest() {}

    @TestHolder("same_item_runtime_candidates_deduplicate_owned_and_network_amounts")
    @EmptyTemplate("5")
    @GameTest(template = "empty_5x5")
    public static void candidatesDeduplicateOwnedAndNetworkAmounts(GameTestHelper helper) {
        AEItemKey target = AEItemKey.of(Items.PAPER);
        AEItemKey actual = namedPaper("actual");
        KeyCounter owned = counter(new GenericStack(actual, 2L));
        KeyCounter network = counter(new GenericStack(actual, 99L));
        int[] simulations = { 0 };
        TrinitySameItemInputInventory inventory = new TrinitySameItemInputInventory(
                policy(target),
                owned,
                network,
                key -> {
                    simulations[0]++;
                    return key.equals(actual) ? 3L : 0L;
                });

        List<GenericStack> candidates = inventory.candidates(target);

        helper.assertValueEqual(candidates.size(), 1, "One physical key must appear only once across owned and network");
        assertStack(helper, candidates.getFirst(), actual, 5L, "Owned and permission-checked network amounts must sum");
        helper.assertValueEqual(simulations[0], 1, "A deduplicated network key must be simulated once");
        helper.succeed();
    }

    @TestHolder("same_item_runtime_empty_policy_keeps_components_exact")
    @EmptyTemplate("5")
    @GameTest(template = "empty_5x5")
    public static void emptyPolicyKeepsComponentsExact(GameTestHelper helper) {
        AEItemKey target = AEItemKey.of(Items.PAPER);
        AEItemKey actual = namedPaper("actual");
        TrinitySameItemInputInventory inventory = new TrinitySameItemInputInventory(
                TrinitySameItemPolicy.empty(),
                counter(new GenericStack(actual, 2L)),
                counter(new GenericStack(actual, 2L)),
                ignored -> 2L);

        helper.assertTrue(
                inventory.candidates(target).isEmpty(),
                "An empty policy must not expose another component variant");
        helper.succeed();
    }

    @TestHolder("same_item_runtime_network_candidate_requires_extract_permission")
    @EmptyTemplate("5")
    @GameTest(template = "empty_5x5")
    public static void networkCandidateRequiresExtractPermission(GameTestHelper helper) {
        AEItemKey target = AEItemKey.of(Items.PAPER);
        AEItemKey actual = namedPaper("denied");
        TrinitySameItemInputInventory inventory = new TrinitySameItemInputInventory(
                policy(target),
                new KeyCounter(),
                counter(new GenericStack(actual, 8L)),
                ignored -> 0L);

        helper.assertTrue(
                inventory.candidates(target).isEmpty(),
                "A listed network variant with zero simulatable extraction must remain unavailable");
        helper.succeed();
    }

    @TestHolder("same_item_completion_preserves_mixed_components_and_target_remainder")
    @EmptyTemplate("5")
    @GameTest(template = "empty_5x5")
    public static void completionPreservesMixedComponentsAndTargetRemainder(GameTestHelper helper) {
        AEItemKey target = AEItemKey.of(Items.PAPER);
        AEItemKey first = namedPaper("first");
        AEItemKey second = namedPaper("second");
        ListCraftingInventory inventory = inventory(
                new GenericStack(target, 2L),
                new GenericStack(first, 3L),
                new GenericStack(second, 4L));

        List<GenericStack> extracted = TrinityCompletionInputExtractor.extract(
                policy(target),
                target,
                7L,
                inventory);

        helper.assertTrue(extracted != null, "The complete mixed-component delivery must be available");
        helper.assertValueEqual(extracted.size(), 3, "Delivery must retain all physical component slices");
        assertStack(helper, extracted.get(0), target, 2L, "Exact target components must be delivered first");
        KeyCounter extractedByKey = new KeyCounter();
        long extractedTotal = 0L;
        for (GenericStack slice : extracted) {
            helper.assertTrue(
                    slice.what().equals(target) || slice.what().equals(first) || slice.what().equals(second),
                    "Completion must not introduce an unrelated physical key");
            extractedByKey.add(slice.what(), slice.amount());
            extractedTotal = Math.addExact(extractedTotal, slice.amount());
        }
        helper.assertValueEqual(extractedTotal, 7L, "Completion must extract exactly the target remainder");
        helper.assertValueEqual(
                extractedByKey.get(first) + inventory.list.get(first),
                3L,
                "First actual component quantity must be conserved");
        helper.assertValueEqual(
                extractedByKey.get(second) + inventory.list.get(second),
                4L,
                "Second actual component quantity must be conserved");
        helper.assertValueEqual(
                inventory.list.get(first) + inventory.list.get(second),
                2L,
                "All units beyond the target remainder must stay in CPU inventory");
        for (var remaining : inventory.list) {
            helper.assertTrue(
                    remaining.getKey().equals(first) || remaining.getKey().equals(second),
                    "CPU remainder must not contain an unrelated physical key");
        }
        helper.assertValueEqual(inventory.list.get(target), 0L, "Delivered exact units must leave CPU inventory");
        helper.succeed();
    }

    @TestHolder("same_item_completion_shortage_rolls_back_every_slice")
    @EmptyTemplate("5")
    @GameTest(template = "empty_5x5")
    public static void completionShortageRollsBackEverySlice(GameTestHelper helper) {
        AEItemKey target = AEItemKey.of(Items.PAPER);
        AEItemKey actual = namedPaper("actual");
        ListCraftingInventory inventory = inventory(
                new GenericStack(target, 1L),
                new GenericStack(actual, 1L));

        List<GenericStack> extracted = TrinityCompletionInputExtractor.extract(
                policy(target),
                target,
                3L,
                inventory);

        helper.assertTrue(extracted == null, "An incomplete final delivery must be rejected atomically");
        helper.assertValueEqual(inventory.list.get(target), 1L, "Exact target extraction must roll back on shortage");
        helper.assertValueEqual(inventory.list.get(actual), 1L, "Actual variant extraction must roll back on shortage");
        helper.succeed();
    }

    @TestHolder("same_item_completion_zero_request_does_not_consume_inventory")
    @EmptyTemplate("5")
    @GameTest(template = "empty_5x5")
    public static void completionZeroRequestDoesNotConsumeInventory(GameTestHelper helper) {
        AEItemKey target = AEItemKey.of(Items.PAPER);
        AEItemKey actual = namedPaper("actual");
        ListCraftingInventory inventory = inventory(
                new GenericStack(target, 2L),
                new GenericStack(actual, 3L));

        List<GenericStack> extracted = TrinityCompletionInputExtractor.extract(
                policy(target),
                target,
                0L,
                inventory);

        helper.assertTrue(extracted != null && extracted.isEmpty(), "A zero delivery request must complete with no slices");
        helper.assertValueEqual(inventory.list.get(target), 2L, "A zero request must preserve exact target inventory");
        helper.assertValueEqual(inventory.list.get(actual), 3L, "A zero request must preserve actual variant inventory");
        helper.succeed();
    }

    private static TrinitySameItemPolicy policy(AEItemKey representative) {
        return TrinitySameItemPolicy.ofRepresentatives(List.of(representative));
    }

    private static KeyCounter counter(GenericStack... stacks) {
        KeyCounter counter = new KeyCounter();
        for (GenericStack stack : stacks) {
            counter.add(stack.what(), stack.amount());
        }
        return counter;
    }

    private static ListCraftingInventory inventory(GenericStack... stacks) {
        ListCraftingInventory inventory = new ListCraftingInventory(ignored -> {});
        for (GenericStack stack : stacks) {
            inventory.insert(stack.what(), stack.amount(), Actionable.MODULATE);
        }
        return inventory;
    }

    private static AEItemKey namedPaper(String name) {
        ItemStack stack = new ItemStack(Items.PAPER);
        stack.set(DataComponents.CUSTOM_NAME, Component.literal(name));
        return AEItemKey.of(stack);
    }

    private static void assertStack(GameTestHelper helper,
                                    GenericStack stack,
                                    AEKey expectedKey,
                                    long expectedAmount,
                                    String message) {
        helper.assertValueEqual(stack.what(), expectedKey, message + " (key)");
        helper.assertValueEqual(stack.amount(), expectedAmount, message + " (amount)");
    }
}

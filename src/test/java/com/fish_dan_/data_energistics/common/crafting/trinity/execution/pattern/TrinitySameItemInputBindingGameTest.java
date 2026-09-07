package com.fish_dan_.data_energistics.common.crafting.trinity.execution.pattern;

import com.fish_dan_.data_energistics.Data_Energistics;

import appeng.api.config.Actionable;
import appeng.api.crafting.IPatternDetails;
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
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import net.neoforged.testframework.annotation.TestHolder;
import net.neoforged.testframework.gametest.EmptyTemplate;

import org.jspecify.annotations.Nullable;

import java.util.List;

@GameTestHolder(Data_Energistics.MODID)
@PrefixGameTestTemplate(false)
public final class TrinitySameItemInputBindingGameTest {

    private TrinitySameItemInputBindingGameTest() {}

    @TestHolder("same_item_input_combines_distinct_components_for_one_craft")
    @EmptyTemplate("5")
    @GameTest(template = "empty_5x5")
    public static void combinesDistinctComponentsForOneCraft(GameTestHelper helper) {
        AEItemKey first = namedPaper("first");
        AEItemKey second = namedPaper("second");
        ListCraftingInventory inventory = inventory(new GenericStack(first, 1), new GenericStack(second, 1));
        var selected = select(new ExactPattern(2), inventory, List.of(new GenericStack(first, 1), new GenericStack(second, 1)));
        helper.assertValueEqual(selected.maximumCrafts(), 1L, "Two variants must satisfy one two-item craft");
        KeyCounter[] inputs = ((TrinityBoundPatternDetails) selected.extractionPattern())
                .extractInputs(inventory, new KeyCounter(), new KeyCounter());
        helper.assertTrue(inputs != null, "Bound physical extraction must succeed");
        helper.assertValueEqual(inputs[0].get(first), 1L, "First component variant must remain intact");
        helper.assertValueEqual(inputs[0].get(second), 1L, "Second component variant must remain intact");
        helper.assertTrue(inventory.list.isEmpty(), "Exactly the allocated items must be consumed");
        helper.succeed();
    }

    @TestHolder("same_item_input_reserves_each_actual_unit_only_once")
    @EmptyTemplate("5")
    @GameTest(template = "empty_5x5")
    public static void reservesEachActualUnitOnlyOnce(GameTestHelper helper) {
        AEItemKey first = namedPaper("first");
        AEItemKey second = namedPaper("second");
        ListCraftingInventory inventory = inventory(new GenericStack(first, 1), new GenericStack(second, 1));
        var selected = select(new ExactPattern(1, 1), inventory, List.of(new GenericStack(first, 1), new GenericStack(second, 1)));
        KeyCounter[] inputs = ((TrinityBoundPatternDetails) selected.extractionPattern())
                .extractInputs(inventory, new KeyCounter(), new KeyCounter());
        helper.assertTrue(inputs != null, "Independent input slots must not reserve the same physical unit twice");
        helper.assertValueEqual(inputs[0].get(first) + inputs[1].get(first), 1L, "First variant has one owned unit");
        helper.assertValueEqual(inputs[0].get(second) + inputs[1].get(second), 1L, "Second variant has one owned unit");
        helper.succeed();
    }

    @TestHolder("same_item_input_combines_exact_and_authorized_variant")
    @EmptyTemplate("5")
    @GameTest(template = "empty_5x5")
    public static void combinesExactAndAuthorizedVariant(GameTestHelper helper) {
        AEItemKey exact = AEItemKey.of(Items.PAPER);
        AEItemKey actual = namedPaper("actual");
        ListCraftingInventory inventory = inventory(new GenericStack(exact, 1), new GenericStack(actual, 1));
        var selected = select(new ExactPattern(2), inventory, List.of(new GenericStack(actual, 1)));
        helper.assertValueEqual(selected.maximumCrafts(), 1L, "Exact and actual quantities must combine");
        helper.assertValueEqual(selected.inputsPerCraft().size(), 2, "Both physical keys must be retained");
        helper.succeed();
    }

    @TestHolder("same_item_input_keeps_unmarked_requirements_exact")
    @EmptyTemplate("5")
    @GameTest(template = "empty_5x5")
    public static void keepsUnmarkedRequirementsExact(GameTestHelper helper) {
        AEItemKey actual = namedPaper("actual");
        ListCraftingInventory inventory = inventory(new GenericStack(actual, 2));
        var result = TrinityPatternSelector.create().select(new ExactPattern(2), 0, false, 1,
                inventory.list::get, ignored -> 0L, ignored -> List.of(), 8);
        helper.assertTrue(result instanceof TrinityPatternSelector.Unavailable, "Unmarked inputs must not accept another component variant");
        helper.assertValueEqual(inventory.list.get(actual), 2L, "Selection must not mutate physical inventory");
        helper.succeed();
    }

    @TestHolder("same_item_input_rolls_back_when_one_slice_disappears")
    @EmptyTemplate("5")
    @GameTest(template = "empty_5x5")
    public static void rollsBackWhenOneSliceDisappears(GameTestHelper helper) {
        AEItemKey first = namedPaper("first");
        AEItemKey second = namedPaper("second");
        ListCraftingInventory inventory = inventory(new GenericStack(first, 1), new GenericStack(second, 1));
        var selected = select(new ExactPattern(2), inventory, List.of(new GenericStack(first, 1), new GenericStack(second, 1)));
        inventory.extract(second, 1, Actionable.MODULATE);
        KeyCounter outputs = new KeyCounter();
        KeyCounter remainders = new KeyCounter();
        KeyCounter[] inputs = ((TrinityBoundPatternDetails) selected.extractionPattern()).extractInputs(inventory, outputs, remainders);
        helper.assertTrue(inputs == null, "Disappeared physical stock must reject the whole extraction");
        helper.assertValueEqual(inventory.list.get(first), 1L, "Earlier physical extraction must roll back");
        helper.assertTrue(outputs.isEmpty() && remainders.isEmpty(), "Failed extraction must not register outputs");
        helper.succeed();
    }

    private static TrinityPatternSelector.Selected select(IPatternDetails pattern, ListCraftingInventory inventory,
                                                          List<GenericStack> aliases) {
        var result = TrinityPatternSelector.create().select(pattern, 0, false, 10,
                inventory.list::get, ignored -> 0L, ignored -> aliases, 8);
        if (result instanceof TrinityPatternSelector.Selected selected) {
            return selected;
        }
        throw new IllegalStateException("Expected a physical same-item binding, got " + result);
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

    private static final class ExactPattern implements IPatternDetails {

        private final IInput[] inputs;

        private ExactPattern(long... amounts) {
            this.inputs = new IInput[amounts.length];
            for (int i = 0; i < amounts.length; i++) {
                this.inputs[i] = new ExactInput(AEItemKey.of(Items.PAPER), amounts[i]);
            }
        }

        @Override
        public AEItemKey getDefinition() {
            return AEItemKey.of(Items.CRAFTING_TABLE);
        }

        @Override
        public IInput[] getInputs() {
            return this.inputs.clone();
        }

        @Override
        public List<GenericStack> getOutputs() {
            return List.of(new GenericStack(AEItemKey.of(Items.BOOK), 1));
        }
    }

    private record ExactInput(AEKey key, long multiplier) implements IPatternDetails.IInput {

        @Override
        public GenericStack[] getPossibleInputs() {
            return new GenericStack[] { new GenericStack(this.key, 1) };
        }

        @Override
        public long getMultiplier() {
            return this.multiplier;
        }

        @Override
        public boolean isValid(AEKey input, Level level) {
            return this.key.equals(input);
        }

        @Override
        public @Nullable AEKey getRemainingKey(AEKey template) {
            return null;
        }
    }
}

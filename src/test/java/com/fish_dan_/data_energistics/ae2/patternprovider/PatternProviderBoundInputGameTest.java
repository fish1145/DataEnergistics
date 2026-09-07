package com.fish_dan_.data_energistics.ae2.patternprovider;

import com.fish_dan_.data_energistics.Data_Energistics;
import com.fish_dan_.data_energistics.common.crafting.dynamic.BoundPatternInputEmitter;
import com.fish_dan_.data_energistics.common.crafting.trinity.dispatch.model.CraftingDispatchTarget;

import appeng.api.crafting.IPatternDetails;
import appeng.api.crafting.PatternDetailsHelper;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.GenericStack;
import appeng.api.stacks.KeyCounter;
import appeng.crafting.pattern.AEProcessingPattern;

import net.minecraft.core.Direction;
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
public final class PatternProviderBoundInputGameTest {

    private PatternProviderBoundInputGameTest() {}

    @TestHolder("pattern_provider_expands_authorized_components_in_sparse_order")
    @EmptyTemplate("5")
    @GameTest(template = "empty_5x5")
    public static void expandsAuthorizedComponentsInSparseOrder(GameTestHelper helper) {
        AEItemKey plannedPaper = AEItemKey.of(Items.PAPER);
        AEItemKey book = AEItemKey.of(Items.BOOK);
        AEItemKey firstActualPaper = namedPaper("first");
        AEItemKey secondActualPaper = namedPaper("second");
        IPatternDetails registered = processingPattern(plannedPaper, book);
        IPatternDetails emissionDetails = new BoundEmissionDetails(
                registered,
                List.of(new GenericStack(plannedPaper, 1L), new GenericStack(book, 1L)));
        boolean strictRejected = false;
        try {
            PatternProviderBatching.expandPatternInputs(
                    registered,
                    actualInputs(firstActualPaper, secondActualPaper, book),
                    1L);
        } catch (RuntimeException expected) {
            strictRejected = true;
        }
        helper.assertTrue(
                strictRejected,
                "Unbound AE2 processing input must retain exact component matching");

        List<GenericStack> expanded = PatternProviderBatching.expandPatternInputs(
                emissionDetails,
                actualInputs(firstActualPaper, secondActualPaper, book),
                3L);

        helper.assertValueEqual(expanded.size(), 3, "Expanded provider input must retain all sparse slices");
        assertStack(helper, expanded.get(1), book, 3L, "Registered middle input must retain sparse position");
        KeyCounter actualSlices = new KeyCounter();
        for (int index : new int[] { 0, 2 }) {
            GenericStack slice = expanded.get(index);
            helper.assertTrue(
                    slice.what().equals(firstActualPaper) || slice.what().equals(secondActualPaper),
                    "Each outer sparse slot must retain one authorized component variant");
            helper.assertValueEqual(slice.amount(), 3L, "Each expanded component slice must retain the batch amount");
            actualSlices.add(slice.what(), slice.amount());
        }
        helper.assertValueEqual(actualSlices.get(firstActualPaper), 3L, "First component variant must be expanded once");
        helper.assertValueEqual(actualSlices.get(secondActualPaper), 3L, "Second component variant must be expanded once");
        helper.succeed();
    }

    @TestHolder("pattern_provider_limits_locked_bound_input_batch_to_one_craft")
    @EmptyTemplate("5")
    @GameTest(template = "empty_5x5")
    public static void limitsLockedBoundInputBatchToOneCraft(GameTestHelper helper) {
        helper.assertValueEqual(
                PatternProviderBatching.boundInputBatchLimit(true, 64L),
                1L,
                "A lock-sensitive authorized binding must retain single-craft semantics");
        helper.assertValueEqual(
                PatternProviderBatching.boundInputBatchLimit(false, 64L),
                64L,
                "An unlocked authorized binding must retain the admitted batch size");
        helper.succeed();
    }

    @TestHolder("pattern_provider_maps_locked_bound_input_to_captured_provider_route")
    @EmptyTemplate("5")
    @GameTest(template = "empty_5x5")
    public static void mapsLockedBoundInputToCapturedProviderRoute(GameTestHelper helper) {
        CraftingDispatchTarget provider = CraftingDispatchTarget.provider();
        CraftingDispatchTarget locked = PatternProviderBatching.externalInventoryDispatchTarget(
                true,
                Direction.NORTH);
        CraftingDispatchTarget unlocked = PatternProviderBatching.externalInventoryDispatchTarget(
                false,
                Direction.NORTH);

        helper.assertValueEqual(
                locked,
                provider,
                "A lock-sensitive external inventory must match the captured provider route");
        helper.assertValueEqual(
                unlocked.stableIdentity(),
                "side:north",
                "An unlocked external inventory must retain its exact physical side route");
        helper.assertFalse(
                unlocked.equals(provider),
                "An unlocked side must not collapse into the provider-level route");
        helper.succeed();
    }

    private static IPatternDetails processingPattern(AEItemKey paper, AEItemKey book) {
        ItemStack encoded = PatternDetailsHelper.encodeProcessingPattern(
                List.of(
                        new GenericStack(paper, 1L),
                        new GenericStack(book, 1L),
                        new GenericStack(paper, 1L)),
                List.of(new GenericStack(AEItemKey.of(Items.DIAMOND), 1L)));
        return new AEProcessingPattern(AEItemKey.of(encoded));
    }

    private static KeyCounter[] actualInputs(AEItemKey firstPaper, AEItemKey secondPaper, AEItemKey book) {
        KeyCounter paperInputs = new KeyCounter();
        paperInputs.add(firstPaper, 1L);
        paperInputs.add(secondPaper, 1L);
        KeyCounter bookInputs = new KeyCounter();
        bookInputs.add(book, 1L);
        return new KeyCounter[] { paperInputs, bookInputs };
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

    private record BoundEmissionDetails(IPatternDetails registered,
                                        List<GenericStack> selectedPlannedTemplates)
            implements IPatternDetails {

        private BoundEmissionDetails {
            selectedPlannedTemplates = List.copyOf(selectedPlannedTemplates);
        }

        @Override
        public AEItemKey getDefinition() {
            return this.registered.getDefinition();
        }

        @Override
        public IInput[] getInputs() {
            return this.registered.getInputs();
        }

        @Override
        public List<GenericStack> getOutputs() {
            return this.registered.getOutputs();
        }

        @Override
        public void pushInputsToExternalInventory(KeyCounter[] inputHolder, PatternInputSink inputSink) {
            BoundPatternInputEmitter.emit(
                    this.registered,
                    this.selectedPlannedTemplates,
                    inputHolder,
                    inputSink);
        }
    }
}

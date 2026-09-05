package com.fish_dan_.data_energistics.common.crafting.dynamic;

import com.fish_dan_.data_energistics.Data_Energistics;

import appeng.api.crafting.IPatternDetails;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.GenericStack;
import appeng.api.stacks.KeyCounter;

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

import it.unimi.dsi.fastutil.objects.ObjectArrayList;

import java.util.List;

@GameTestHolder(Data_Energistics.MODID)
@PrefixGameTestTemplate(false)
public final class BoundPatternInputEmitterGameTest {

    private BoundPatternInputEmitterGameTest() {}

    @TestHolder("bound_pattern_input_emitter_preserves_sparse_order_and_actual_components")
    @EmptyTemplate("5")
    @GameTest(template = "empty_5x5")
    public static void preservesSparseOrderAndActualComponents(GameTestHelper helper) {
        AEItemKey plannedPaper = AEItemKey.of(Items.PAPER);
        AEItemKey book = AEItemKey.of(Items.BOOK);
        AEItemKey firstActualPaper = namedPaper("first");
        AEItemKey secondActualPaper = namedPaper("second");
        SparsePatternDetails pattern = new SparsePatternDetails(plannedPaper, book);

        KeyCounter paperInputs = new KeyCounter();
        paperInputs.add(firstActualPaper, 1L);
        paperInputs.add(secondActualPaper, 1L);
        KeyCounter bookInputs = new KeyCounter();
        bookInputs.add(book, 1L);
        ObjectArrayList<GenericStack> emitted = new ObjectArrayList<>();

        BoundPatternInputEmitter.emit(
                pattern,
                List.of(new GenericStack(plannedPaper, 1L), new GenericStack(book, 1L)),
                new KeyCounter[] { paperInputs, bookInputs },
                (key, amount) -> emitted.add(new GenericStack(key, amount)));

        helper.assertValueEqual(emitted.size(), 3, "Sparse replay must emit all three original input slices");
        assertStack(helper, emitted.get(1), book, 1L, "Middle sparse slot must retain the registered book key");
        KeyCounter actualSlices = new KeyCounter();
        for (int index : new int[] { 0, 2 }) {
            GenericStack slice = emitted.get(index);
            helper.assertTrue(
                    slice.what().equals(firstActualPaper) || slice.what().equals(secondActualPaper),
                    "Each outer sparse slot must retain one authorized component variant");
            helper.assertValueEqual(slice.amount(), 1L, "Each outer sparse slot must retain its amount");
            actualSlices.add(slice.what(), slice.amount());
        }
        helper.assertValueEqual(actualSlices.get(firstActualPaper), 1L, "First component variant must be emitted once");
        helper.assertValueEqual(actualSlices.get(secondActualPaper), 1L, "Second component variant must be emitted once");
        helper.succeed();
    }

    @TestHolder("bound_pattern_input_emitter_rejects_unauthorized_registered_item")
    @EmptyTemplate("5")
    @GameTest(template = "empty_5x5")
    public static void rejectsUnauthorizedRegisteredItem(GameTestHelper helper) {
        AEItemKey plannedPaper = AEItemKey.of(Items.PAPER);
        AEItemKey book = AEItemKey.of(Items.BOOK);
        SparsePatternDetails pattern = new SparsePatternDetails(plannedPaper, book);
        KeyCounter invalidPaperInputs = new KeyCounter();
        invalidPaperInputs.add(AEItemKey.of(Items.STONE), 2L);
        KeyCounter bookInputs = new KeyCounter();
        bookInputs.add(book, 1L);

        try {
            BoundPatternInputEmitter.emit(
                    pattern,
                    List.of(new GenericStack(plannedPaper, 1L), new GenericStack(book, 1L)),
                    new KeyCounter[] { invalidPaperInputs, bookInputs },
                    (key, amount) -> {});
        } catch (IllegalArgumentException expected) {
            helper.succeed();
            return;
        }
        helper.fail("A different registered item must not pass a same-item binding");
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

    private static final class SparsePatternDetails implements IPatternDetails {

        private final AEItemKey paper;
        private final AEItemKey book;
        private final IInput[] inputs;

        private SparsePatternDetails(AEItemKey paper, AEItemKey book) {
            this.paper = paper;
            this.book = book;
            this.inputs = new IInput[] {
                    new ExactInput(new GenericStack(paper, 1L), 2L),
                    new ExactInput(new GenericStack(book, 1L), 1L)
            };
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
            return List.of(new GenericStack(AEItemKey.of(Items.DIAMOND), 1L));
        }

        @Override
        public void pushInputsToExternalInventory(KeyCounter[] inputHolder, PatternInputSink inputSink) {
            inputSink.pushInput(this.paper, 1L);
            inputSink.pushInput(this.book, 1L);
            inputSink.pushInput(this.paper, 1L);
        }
    }

    private record ExactInput(GenericStack template, long multiplier) implements IPatternDetails.IInput {

        @Override
        public GenericStack[] getPossibleInputs() {
            return new GenericStack[] { this.template };
        }

        @Override
        public long getMultiplier() {
            return this.multiplier;
        }

        @Override
        public boolean isValid(AEKey input, Level level) {
            return this.template.what().equals(input);
        }

        @Override
        public AEKey getRemainingKey(AEKey template) {
            return null;
        }
    }
}

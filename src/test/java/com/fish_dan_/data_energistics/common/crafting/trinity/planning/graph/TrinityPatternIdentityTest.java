package com.fish_dan_.data_energistics.common.crafting.trinity.planning.graph;

import com.fish_dan_.data_energistics.common.trinity.TrinityPatternPublicationSignature;

import net.minecraft.core.RegistryAccess;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.ItemLike;
import net.minecraft.world.level.Level;

import appeng.api.crafting.IPatternDetails;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.GenericStack;
import org.jetbrains.annotations.Nullable;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

public final class TrinityPatternIdentityTest {

    @BeforeAll
    static void bootstrapRegistries() {
        TrinityPlanningGraphTestBootstrap.initialize();
    }

    @Test
    void canonicalNbtSortsCompoundKeysRecursively() {
        CompoundTag firstNested = new CompoundTag();
        firstNested.putInt("z", 3);
        firstNested.putString("a", "value");
        CompoundTag first = new CompoundTag();
        first.putLong("later", 9L);
        first.put("nested", firstNested);

        CompoundTag secondNested = new CompoundTag();
        secondNested.putString("a", "value");
        secondNested.putInt("z", 3);
        CompoundTag second = new CompoundTag();
        second.put("nested", secondNested);
        second.putLong("later", 9L);

        assertEquals(TrinityCanonicalNbt.encode(first), TrinityCanonicalNbt.encode(second));
    }

    @Test
    void captureDefensivelyCopiesPatternCollectionsAndCollapsesExactAlternativeDuplicates() {
        GenericStack iron = stack(Items.IRON_INGOT, 1L);
        GenericStack gold = stack(Items.GOLD_INGOT, 1L);
        GenericStack[] alternatives = { iron, iron, gold };
        HashMap<AEKey, AEKey> remainingKeys = new HashMap<>();
        remainingKeys.put(iron.what(), AEItemKey.of(Items.BUCKET));
        IPatternDetails.IInput[] inputs = { new TestInput(2L, alternatives, remainingKeys) };
        ArrayList<GenericStack> outputs = new ArrayList<>(List.of(stack(Items.DIAMOND, 3L)));
        TestPattern pattern = new TestPattern(key(new ItemStack(Items.PAPER)), inputs, outputs, true);

        TrinityPatternPublicationSignature captured = TrinityPatternPublicationSignature.capture(pattern);
        alternatives[0] = stack(Items.COAL, 1L);
        inputs[0] = new TestInput(1L, new GenericStack[] { stack(Items.REDSTONE, 1L) });
        remainingKeys.put(iron.what(), AEItemKey.of(Items.GLASS_BOTTLE));
        outputs.clear();

        assertEquals(List.of(iron, gold), captured.inputs().getFirst().possibleInputs());
        assertEquals(AEItemKey.of(Items.BUCKET), captured.inputs().getFirst().alternatives().getFirst().remainingKey());
        assertEquals(2L, captured.inputs().getFirst().multiplier());
        assertEquals(List.of(stack(Items.DIAMOND, 3L)), captured.outputs());
        assertThrows(UnsupportedOperationException.class, () -> captured.inputs().clear());
        assertThrows(UnsupportedOperationException.class, () -> captured.outputs().clear());
        assertThrows(
                UnsupportedOperationException.class,
                () -> captured.inputs().getFirst().alternatives().clear());
        assertThrows(
                UnsupportedOperationException.class,
                () -> captured.inputs().getFirst().possibleInputs().clear());
    }

    @Test
    void identityIsDeterministicAndDistinguishesDefinitionComponents() {
        ItemStack firstDefinition = new ItemStack(Items.PAPER);
        firstDefinition.set(DataComponents.CUSTOM_NAME, Component.literal("first"));
        ItemStack sameDefinition = firstDefinition.copy();
        ItemStack secondDefinition = new ItemStack(Items.PAPER);
        secondDefinition.set(DataComponents.CUSTOM_NAME, Component.literal("second"));

        TrinityPatternIdentity first = identity(pattern(firstDefinition));
        TrinityPatternIdentity equalCapture = identity(pattern(sameDefinition));
        TrinityPatternIdentity second = identity(pattern(secondDefinition));

        assertEquals(first, equalCapture);
        assertEquals(0, first.compareTo(equalCapture));
        assertNotEquals(first.definitionEncoding(), second.definitionEncoding());
        assertNotEquals(first, second);
        assertNotEquals(0, first.compareTo(second));
    }

    @Test
    void remainingKeyParticipatesInStablePublicationIdentity() {
        ItemStack definition = new ItemStack(Items.PAPER);
        TrinityPatternIdentity bucket = identity(patternWithRemaining(
                definition,
                AEItemKey.of(Items.BUCKET)));
        TrinityPatternIdentity bottle = identity(patternWithRemaining(
                definition.copy(),
                AEItemKey.of(Items.GLASS_BOTTLE)));

        assertEquals(bucket.definitionEncoding(), bottle.definitionEncoding());
        assertNotEquals(bucket.publicationEncoding(), bottle.publicationEncoding());
        assertNotEquals(bucket, bottle);
    }

    @Test
    void publicationAndSnapshotRejectInvalidOrDuplicateSemantics() {
        GenericStack iron = stack(Items.IRON_INGOT, 1L);
        assertThrows(
                IllegalArgumentException.class,
                () -> new TrinityPatternPublicationSignature.Input(
                        0L,
                        List.of(new TrinityPatternPublicationSignature.Alternative(iron, null))));
        assertThrows(
                IllegalArgumentException.class,
                () -> new TrinityPatternPublicationSignature.Input(1L, List.of()));
        assertThrows(
                IllegalArgumentException.class,
                () -> new TrinityPatternPublicationSignature.Input(
                        1L,
                        List.of(new TrinityPatternPublicationSignature.Alternative(
                                stack(Items.IRON_INGOT, 0L),
                                null))));
        assertThrows(
                IllegalArgumentException.class,
                () -> new TrinityPatternPublicationSignature(
                        key(new ItemStack(Items.PAPER)),
                        List.of(),
                        List.of(),
                        true));

        TrinityPatternPublicationSignature publication = TrinityPatternPublicationSignature.capture(patternWithRemaining(
                new ItemStack(Items.PAPER),
                AEItemKey.of(Items.BUCKET)));
        TrinityCraftingGraphPattern graphPattern = new TrinityCraftingGraphPattern(
                TrinityPatternIdentity.capture(publication, RegistryAccess.EMPTY),
                publication);
        ArrayList<TrinityCraftingGraphPattern> mutable = new ArrayList<>(List.of(graphPattern));
        TrinityCraftingGraphSnapshot snapshot = new TrinityCraftingGraphSnapshot(4L, mutable);
        mutable.clear();

        assertEquals(List.of(graphPattern), snapshot.patterns());
        assertThrows(UnsupportedOperationException.class, () -> snapshot.patterns().clear());
        assertThrows(UnsupportedOperationException.class, () -> snapshot.keys().clear());
        assertThrows(UnsupportedOperationException.class, () -> snapshot.patternsByOutput().clear());
        assertEquals(List.of(graphPattern), snapshot.patternsProducing(AEItemKey.of(Items.BUCKET)));
        assertThrows(
                IllegalArgumentException.class,
                () -> new TrinityCraftingGraphSnapshot(4L, List.of(graphPattern, graphPattern)));
    }

    private static TrinityPatternIdentity identity(TestPattern pattern) {
        return TrinityPatternIdentity.capture(
                TrinityPatternPublicationSignature.capture(pattern),
                RegistryAccess.EMPTY);
    }

    private static TestPattern pattern(ItemStack definition) {
        return patternWithRemaining(definition, null);
    }

    private static TestPattern patternWithRemaining(ItemStack definition, @Nullable AEKey remainingKey) {
        HashMap<AEKey, AEKey> remainingKeys = new HashMap<>();
        if (remainingKey != null) {
            remainingKeys.put(AEItemKey.of(Items.IRON_INGOT), remainingKey);
        }
        return new TestPattern(
                key(definition),
                new IPatternDetails.IInput[] {
                        new TestInput(2L, new GenericStack[] {
                                stack(Items.IRON_INGOT, 1L),
                                stack(Items.GOLD_INGOT, 1L)
                        }, remainingKeys)
                },
                new ArrayList<>(List.of(stack(Items.DIAMOND, 1L))),
                false);
    }

    private static GenericStack stack(ItemLike item, long amount) {
        return new GenericStack(AEItemKey.of(item), amount);
    }

    private static AEItemKey key(ItemStack stack) {
        AEItemKey key = AEItemKey.of(stack);
        if (key == null) {
            throw new IllegalArgumentException("Test pattern definition cannot be empty");
        }
        return key;
    }

    private static final class TestPattern implements IPatternDetails {

        private final AEItemKey definition;
        private final IPatternDetails.IInput[] inputs;
        private final List<GenericStack> outputs;
        private final boolean pushesInputs;

        private TestPattern(AEItemKey definition,
                            IPatternDetails.IInput[] inputs,
                            List<GenericStack> outputs,
                            boolean pushesInputs) {
            this.definition = definition;
            this.inputs = inputs;
            this.outputs = outputs;
            this.pushesInputs = pushesInputs;
        }

        @Override
        public AEItemKey getDefinition() {
            return this.definition;
        }

        @Override
        public IPatternDetails.IInput[] getInputs() {
            return this.inputs;
        }

        @Override
        public List<GenericStack> getOutputs() {
            return this.outputs;
        }

        @Override
        public boolean supportsPushInputsToExternalInventory() {
            return this.pushesInputs;
        }
    }

    private record TestInput(long multiplier,
                             GenericStack[] alternatives,
                             Map<AEKey, AEKey> remainingKeys)
            implements IPatternDetails.IInput {

        private TestInput(long multiplier, GenericStack[] alternatives) {
            this(multiplier, alternatives, Map.of());
        }

        @Override
        public GenericStack[] getPossibleInputs() {
            return this.alternatives;
        }

        @Override
        public long getMultiplier() {
            return this.multiplier;
        }

        @Override
        public boolean isValid(AEKey input, Level level) {
            for (GenericStack alternative : this.alternatives) {
                if (alternative.what().equals(input)) {
                    return true;
                }
            }
            return false;
        }

        @Nullable
        @Override
        public AEKey getRemainingKey(AEKey template) {
            return this.remainingKeys.get(template);
        }
    }
}

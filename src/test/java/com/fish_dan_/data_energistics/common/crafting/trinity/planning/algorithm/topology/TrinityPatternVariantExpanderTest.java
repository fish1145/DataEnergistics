package com.fish_dan_.data_energistics.common.crafting.trinity.planning.algorithm.topology;

import com.fish_dan_.data_energistics.common.crafting.trinity.planning.TrinityPlanningDiagnosticCode;
import com.fish_dan_.data_energistics.common.crafting.trinity.planning.algorithm.TrinityAlgorithmResult;
import com.fish_dan_.data_energistics.common.crafting.trinity.planning.graph.TrinityCraftingGraphPattern;
import com.fish_dan_.data_energistics.common.crafting.trinity.planning.graph.TrinityCraftingGraphSnapshot;
import com.fish_dan_.data_energistics.common.crafting.trinity.planning.graph.TrinityPatternIdentity;
import com.fish_dan_.data_energistics.common.crafting.trinity.planning.graph.TrinityPatternVariant;
import com.fish_dan_.data_energistics.common.crafting.trinity.planning.graph.TrinityPlanningGraphTestBootstrap;
import com.fish_dan_.data_energistics.common.trinity.TrinityPatternPublicationSignature;

import net.minecraft.world.item.Items;
import net.minecraft.world.level.ItemLike;

import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.GenericStack;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.math.BigInteger;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

public final class TrinityPatternVariantExpanderTest {

    @BeforeAll
    static void bootstrapRegistries() {
        TrinityPlanningGraphTestBootstrap.initialize();
    }

    @Test
    void expandsOrderedCartesianBindingsWithExactMultiplierAndRemainders() {
        TrinityCraftingGraphSnapshot snapshot = new TrinityCraftingGraphSnapshot(3L, List.of(pattern(
                new TrinityPatternIdentity("definition", "publication"),
                List.of(
                        input(2L,
                                alternative(Items.IRON_INGOT, 3L, Items.BUCKET),
                                alternative(Items.GOLD_INGOT, 4L, Items.GLASS_BOTTLE)),
                        input(3L,
                                alternative(Items.REDSTONE, 5L, null),
                                alternative(Items.COAL, 7L, null))),
                stack(Items.DIAMOND, 11L))));

        TrinityAlgorithmResult<List<TrinityPatternVariant>> result = TrinityPatternVariantExpander.create().expand(snapshot, 4);

        assertTrue(result.successful());
        assertEquals(4, result.value().size());
        assertEquals(List.of(0, 0), result.value().get(0).alternativeOrdinals());
        assertEquals(List.of(0, 1), result.value().get(1).alternativeOrdinals());
        assertEquals(List.of(1, 0), result.value().get(2).alternativeOrdinals());
        assertEquals(List.of(1, 1), result.value().get(3).alternativeOrdinals());

        TrinityPatternVariant first = result.value().getFirst();
        assertEquals(AEItemKey.of(Items.DIAMOND), first.primaryOutput());
        assertEquals(BigInteger.valueOf(6L), first.inputs().get(AEItemKey.of(Items.IRON_INGOT)));
        assertEquals(BigInteger.valueOf(15L), first.inputs().get(AEItemKey.of(Items.REDSTONE)));
        assertEquals(BigInteger.valueOf(2L), first.outputs().get(AEItemKey.of(Items.BUCKET)));
        assertEquals(BigInteger.valueOf(11L), first.outputs().get(AEItemKey.of(Items.DIAMOND)));
        assertEquals(BigInteger.valueOf(-6L), first.netChange().get(AEItemKey.of(Items.IRON_INGOT)));
        assertEquals(BigInteger.valueOf(2L), first.netChange().get(AEItemKey.of(Items.BUCKET)));
    }

    @Test
    void checksGlobalVariantLimitBeforeMaterializingCartesianProduct() {
        TrinityCraftingGraphSnapshot snapshot = new TrinityCraftingGraphSnapshot(3L, List.of(pattern(
                new TrinityPatternIdentity("definition", "publication"),
                List.of(
                        input(1L,
                                alternative(Items.IRON_INGOT, 1L, null),
                                alternative(Items.GOLD_INGOT, 1L, null)),
                        input(1L,
                                alternative(Items.REDSTONE, 1L, null),
                                alternative(Items.COAL, 1L, null))),
                stack(Items.DIAMOND, 1L))));

        TrinityAlgorithmResult<List<TrinityPatternVariant>> result = TrinityPatternVariantExpander.create().expand(snapshot, 3);

        assertFalse(result.successful());
        assertEquals(TrinityPlanningDiagnosticCode.VARIANT_LIMIT, result.diagnostic().code());
        assertEquals("4", result.diagnostic().metadata().get("required"));
    }

    @Test
    void canonicalBindingsDoNotConsumeTheAdditionalBranchBudget() {
        TrinityCraftingGraphSnapshot snapshot = new TrinityCraftingGraphSnapshot(3L, List.of(
                pattern(
                        new TrinityPatternIdentity("definition-0", "publication-0"),
                        List.of(input(1L, alternative(Items.IRON_INGOT, 1L, null))),
                        stack(Items.DIAMOND, 1L)),
                pattern(
                        new TrinityPatternIdentity("definition-1", "publication-1"),
                        List.of(input(1L, alternative(Items.GOLD_INGOT, 1L, null))),
                        stack(Items.DIAMOND, 1L)),
                pattern(
                        new TrinityPatternIdentity("definition-2", "publication-2"),
                        List.of(input(1L, alternative(Items.REDSTONE, 1L, null))),
                        stack(Items.DIAMOND, 1L)),
                pattern(
                        new TrinityPatternIdentity("definition-3", "publication-3"),
                        List.of(input(1L, alternative(Items.COAL, 1L, null))),
                        stack(Items.DIAMOND, 1L)),
                pattern(
                        new TrinityPatternIdentity("definition-4", "publication-4"),
                        List.of(
                                input(1L,
                                        alternative(Items.IRON_INGOT, 1L, null),
                                        alternative(Items.GOLD_INGOT, 1L, null)),
                                input(1L,
                                        alternative(Items.REDSTONE, 1L, null),
                                        alternative(Items.COAL, 1L, null))),
                        stack(Items.DIAMOND, 1L))));

        TrinityAlgorithmResult<List<TrinityPatternVariant>> result = TrinityPatternVariantExpander.create()
                .expand(snapshot, 4);

        assertTrue(result.successful());
        assertEquals(8, result.value().size());
        assertEquals("publication-0", result.value().getFirst().patternIdentity().publicationEncoding());
        assertEquals("publication-4", result.value().getLast().patternIdentity().publicationEncoding());
    }

    @Test
    void retainsProductsBeyondLongWithoutIntermediateOverflow() {
        TrinityCraftingGraphSnapshot snapshot = new TrinityCraftingGraphSnapshot(3L, List.of(pattern(
                new TrinityPatternIdentity("definition", "publication"),
                List.of(input(
                        Long.MAX_VALUE,
                        new TrinityPatternPublicationSignature.Alternative(
                                stack(Items.IRON_INGOT, Long.MAX_VALUE),
                                AEItemKey.of(Items.BUCKET)))),
                stack(Items.DIAMOND, 1L))));

        TrinityPatternVariant variant = TrinityPatternVariantExpander.create()
                .expand(snapshot, 1)
                .value()
                .getFirst();

        assertEquals(
                BigInteger.valueOf(Long.MAX_VALUE).multiply(BigInteger.valueOf(Long.MAX_VALUE)),
                variant.inputs().get(AEItemKey.of(Items.IRON_INGOT)));
        assertEquals(BigInteger.valueOf(Long.MAX_VALUE), variant.outputs().get(AEItemKey.of(Items.BUCKET)));
    }

    private static TrinityCraftingGraphPattern pattern(TrinityPatternIdentity identity,
                                                       List<TrinityPatternPublicationSignature.Input> inputs,
                                                       GenericStack output) {
        return new TrinityCraftingGraphPattern(
                identity,
                new TrinityPatternPublicationSignature(
                        AEItemKey.of(Items.PAPER),
                        inputs,
                        List.of(output),
                        true));
    }

    private static TrinityPatternPublicationSignature.Input input(
                                                                  long multiplier,
                                                                  TrinityPatternPublicationSignature.Alternative... alternatives) {
        return new TrinityPatternPublicationSignature.Input(multiplier, List.of(alternatives));
    }

    private static TrinityPatternPublicationSignature.Alternative alternative(
                                                                              ItemLike item,
                                                                              long amount,
                                                                              ItemLike remainder) {
        return new TrinityPatternPublicationSignature.Alternative(
                stack(item, amount),
                remainder == null ? null : AEItemKey.of(remainder));
    }

    private static GenericStack stack(ItemLike item, long amount) {
        return new GenericStack(AEItemKey.of(item), amount);
    }
}

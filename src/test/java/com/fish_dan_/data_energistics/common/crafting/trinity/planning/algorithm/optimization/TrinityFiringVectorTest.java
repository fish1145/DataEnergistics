package com.fish_dan_.data_energistics.common.crafting.trinity.planning.algorithm.optimization;

import com.fish_dan_.data_energistics.common.crafting.trinity.planning.graph.TrinityPatternVariant;
import com.fish_dan_.data_energistics.common.crafting.trinity.planning.graph.TrinityPlanningGraphTestBootstrap;

import net.minecraft.world.item.Items;

import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.AEKey;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.math.BigInteger;
import java.util.List;
import java.util.Map;

import static com.fish_dan_.data_energistics.common.crafting.trinity.planning.algorithm.TrinityAlgorithmTestPatterns.amounts;
import static com.fish_dan_.data_energistics.common.crafting.trinity.planning.algorithm.TrinityAlgorithmTestPatterns.variant;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

public final class TrinityFiringVectorTest {

    @BeforeAll
    static void bootstrapRegistries() {
        TrinityPlanningGraphTestBootstrap.initialize();
    }

    @Test
    void completesStableVariantDomainWithZeroesAndComparesExactBigIntegerCounts() {
        AEKey key = AEItemKey.of(Items.IRON_INGOT);
        TrinityPatternVariant first = variant(
                "a",
                amounts(key, BigInteger.ONE),
                amounts(key, BigInteger.TWO));
        TrinityPatternVariant second = variant(
                "b",
                amounts(key, BigInteger.ONE),
                amounts(key, BigInteger.TWO));
        BigInteger exactLarge = new BigInteger("1000000000000000000000000000000");

        TrinityFiringVector large = TrinityFiringVector.from(
                List.of(second, first),
                Map.of(first, exactLarge));
        TrinityFiringVector small = TrinityFiringVector.from(
                List.of(first, second),
                Map.of(first, BigInteger.TWO));
        TrinityLexicographicObjective largeIdentity = new TrinityLexicographicObjective(
                BigInteger.ONE,
                BigInteger.ONE,
                BigInteger.ONE,
                large);
        TrinityLexicographicObjective smallIdentity = new TrinityLexicographicObjective(
                BigInteger.ONE,
                BigInteger.ONE,
                BigInteger.ONE,
                small);

        assertEquals(List.of(first, second), large.variants());
        assertEquals(List.of(exactLarge, BigInteger.ZERO), large.counts());
        assertTrue(large.compareTo(small) > 0);
        assertTrue(largeIdentity.compareTo(smallIdentity) < 0);
    }
}

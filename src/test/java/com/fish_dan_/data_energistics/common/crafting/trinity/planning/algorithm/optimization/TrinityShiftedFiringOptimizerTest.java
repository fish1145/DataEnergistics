package com.fish_dan_.data_energistics.common.crafting.trinity.planning.algorithm.optimization;

import com.fish_dan_.data_energistics.common.crafting.trinity.planning.algorithm.TrinityPlanningControl;
import com.fish_dan_.data_energistics.common.crafting.trinity.planning.algorithm.cycle.TrinityCycleDemand;
import com.fish_dan_.data_energistics.common.crafting.trinity.planning.algorithm.opportunity.TrinityPlanningAttempt;
import com.fish_dan_.data_energistics.common.crafting.trinity.planning.algorithm.topology.TrinityStronglyConnectedComponent;
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
import java.util.Set;

import static com.fish_dan_.data_energistics.common.crafting.trinity.planning.algorithm.TrinityAlgorithmTestPatterns.amounts;
import static com.fish_dan_.data_energistics.common.crafting.trinity.planning.algorithm.TrinityAlgorithmTestPatterns.variant;
import static org.junit.jupiter.api.Assertions.assertEquals;

public final class TrinityShiftedFiringOptimizerTest {

    @BeforeAll
    static void bootstrapRegistries() {
        TrinityPlanningGraphTestBootstrap.initialize();
    }

    @Test
    void structuralReductionBoundMayBeTheExactIntegerOptimum() {
        AEKey internal = AEItemKey.of(Items.IRON_INGOT);
        AEKey boundary = AEItemKey.of(Items.GOLD_INGOT);
        TrinityPatternVariant required = variant(
                "a",
                amounts(internal, BigInteger.ONE),
                amounts(internal, BigInteger.TWO));
        TrinityPatternVariant removable = variant(
                "b",
                amounts(internal, BigInteger.ONE),
                amounts(internal, BigInteger.ONE, boundary, BigInteger.ONE));
        TrinityStronglyConnectedComponent component = new TrinityStronglyConnectedComponent(
                0,
                List.of(internal),
                true,
                List.of(required, removable),
                List.of(),
                List.of());

        TrinityPlanningAttempt<Map<TrinityPatternVariant, BigInteger>> attempt = TrinityShiftedFiringOptimizer
                .create()
                .optimize(
                        component,
                        new TrinityCycleDemand(Map.of(), Map.of(internal, BigInteger.ONE)),
                        Map.of(internal, BigInteger.ONE),
                        Set.of(),
                        Map.of(required, BigInteger.TEN, removable, BigInteger.valueOf(7L)),
                        TrinityPlanningControl.create(() -> false, () -> 0L, Long.MAX_VALUE));

        assertEquals(TrinityPlanningAttempt.Kind.PROVED_OPTIMAL, attempt.kind());
        assertEquals(Map.of(required, BigInteger.ONE), attempt.value());
    }
}

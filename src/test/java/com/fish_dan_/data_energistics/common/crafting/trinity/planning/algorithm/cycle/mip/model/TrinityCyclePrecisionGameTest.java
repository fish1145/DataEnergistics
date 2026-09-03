package com.fish_dan_.data_energistics.common.crafting.trinity.planning.algorithm.cycle.mip.model;

import com.fish_dan_.data_energistics.Data_Energistics;
import com.fish_dan_.data_energistics.common.crafting.trinity.planning.TrinityPlanningDiagnosticCode;
import com.fish_dan_.data_energistics.common.crafting.trinity.planning.algorithm.TrinityPlanningControl;
import com.fish_dan_.data_energistics.common.crafting.trinity.planning.algorithm.TrinityPlanningMode;
import com.fish_dan_.data_energistics.common.crafting.trinity.planning.algorithm.cycle.TrinityCycleDemand;
import com.fish_dan_.data_energistics.common.crafting.trinity.planning.algorithm.cycle.mip.template.TrinityMipCoefficientTemplate;
import com.fish_dan_.data_energistics.common.crafting.trinity.planning.algorithm.optimization.TrinityExactConservationVerifier;
import com.fish_dan_.data_energistics.common.crafting.trinity.planning.algorithm.optimization.TrinityIntegerResultVerifier;
import com.fish_dan_.data_energistics.common.crafting.trinity.planning.graph.TrinityBoundPatternInput;
import com.fish_dan_.data_energistics.common.crafting.trinity.planning.graph.TrinityPatternIdentity;
import com.fish_dan_.data_energistics.common.crafting.trinity.planning.graph.TrinityPatternVariant;
import com.fish_dan_.data_energistics.common.crafting.trinity.planning.plan.TrinityPlanQuality;

import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.item.Items;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import net.neoforged.testframework.annotation.TestHolder;
import net.neoforged.testframework.gametest.EmptyTemplate;

import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.GenericStack;

import java.math.BigInteger;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

@PrefixGameTestTemplate(false)
@GameTestHolder(Data_Energistics.MODID)
public final class TrinityCyclePrecisionGameTest {

    private TrinityCyclePrecisionGameTest() {}

    @TestHolder("trinity_cycle_precision_expands_without_skipping_ordinary_domain")
    @EmptyTemplate("5")
    @GameTest(template = "empty_5x5")
    public static void expandsWithoutSkippingOrdinaryDomain(GameTestHelper helper) {
        AEKey first = AEItemKey.of(Items.IRON_INGOT);
        AEKey second = AEItemKey.of(Items.GOLD_INGOT);
        TrinityPatternVariant forward = conversion("forward", first, 4, second, 5);
        TrinityPatternVariant reverse = conversion("reverse", second, 6, first, 5);
        List<TrinityPatternVariant> variants = List.of(forward, reverse);
        BigInteger requested = BigInteger.valueOf(1_000_000_000L);
        TrinityCycleDemand demand = new TrinityCycleDemand(
                Map.of(), Map.of(), Map.of(first, requested), Set.of(first));
        TrinityCycleFeasibilityRequest request = new TrinityCycleFeasibilityRequest(
                variants, Set.of(first, second), demand, Map.of(first, BigInteger.TEN), Set.of(),
                Map.of(forward, TrinityFiringBounds.full(), reverse, TrinityFiringBounds.full()),
                Optional.empty(), BigInteger.ZERO, BigInteger.ZERO, false, 0,
                TrinityMipCoefficientTemplate.create(variants, List.of(first, second)));

        // Settling B requires six forward and five reverse firings per net A. The first two compact
        // domains cannot hold that vector; doubling reaches it without squaring past ordinary precision.
        var expanded = TrinityCycleFeasibilityModel.create().solve(
                request.withOpenFiringUpper(requested.shiftLeft(3)),
                TrinityPlanningMode.FIRST_FEASIBLE, TrinityPlanningControl.unbounded());
        if (!expanded.successful()) {
            helper.fail("The fresh expanded domain must be feasible: " + expanded.diagnostic());
        }
        var solved = TrinityCycleFeasibilityModel.create().solve(
                request, TrinityPlanningMode.FIRST_FEASIBLE, TrinityPlanningControl.unbounded());
        if (!solved.successful()) {
            helper.fail("The expanded cycle must have an exact feasible firing vector: " + solved.diagnostic());
        }
        helper.assertFalse(solved.value().radix(), "A representable ordinary domain must not be skipped");
        BigInteger forwardCount = solved.value().firings().get(forward);
        BigInteger reverseCount = solved.value().firings().get(reverse);
        helper.assertValueEqual(forwardCount.multiply(BigInteger.valueOf(5)),
                reverseCount.multiply(BigInteger.valueOf(6)), "The internal B balance must settle exactly");
        helper.assertTrue(reverseCount.multiply(BigInteger.valueOf(5))
                .subtract(forwardCount.multiply(BigInteger.valueOf(4))).compareTo(requested) >= 0,
                "The firing vector must supply the complete billion-unit net demand");
        helper.succeed();
    }

    private static TrinityPatternVariant conversion(String name, AEKey input, long consumed, AEKey output, long produced) {
        return TrinityPatternVariant.create(new TrinityPatternIdentity(name, name), output, 0, List.of(0),
                List.of(new TrinityBoundPatternInput(0, 0, new GenericStack(input, consumed), 1, null)),
                List.of(new GenericStack(output, produced)));
    }

    @TestHolder("trinity_cycle_precision_preserves_integer_branch_seed_boundaries")
    @EmptyTemplate("5")
    @GameTest(template = "empty_5x5")
    public static void preservesIntegerBranchSeedBoundaries(GameTestHelper helper) {
        AEKey key = AEItemKey.of(Items.IRON_INGOT);
        TrinityPatternVariant variant = conversion("seed_boundary", key, 1, key, 2);
        List<TrinityPatternVariant> variants = List.of(variant);
        for (long amount = Integer.MAX_VALUE - 1L; amount <= Integer.MAX_VALUE + 1L; amount++) {
            BigInteger seed = BigInteger.valueOf(amount);
            TrinityCycleFeasibilityRequest request = new TrinityCycleFeasibilityRequest(
                    variants, Set.of(key), new TrinityCycleDemand(Map.of(), Map.of(), Map.of(key, BigInteger.ONE), Set.of(key)),
                    Map.of(key, seed), Set.of(), Map.of(variant, TrinityFiringBounds.fixed(BigInteger.ONE)),
                    Optional.empty(), seed, BigInteger.ZERO, false, 0,
                    TrinityMipCoefficientTemplate.create(variants, List.of(key)));
            var solved = TrinityCycleFeasibilityModel.create().solve(
                    request, TrinityPlanningMode.OPTIMAL, TrinityPlanningControl.unbounded());
            if (!solved.successful()) {
                helper.fail("The integer branch boundary must preserve exact seed " + seed + ": " + solved.diagnostic());
            }
            helper.assertValueEqual(solved.value().modelSeed().get(key), seed, "Seed must not truncate or become unbounded");
            if (amount >= Integer.MAX_VALUE) {
                helper.assertValueEqual(solved.value().quality(), TrinityPlanQuality.VERIFIED_FEASIBLE,
                        "A relaxed integer candidate must not inherit an LP optimality claim");
            }
        }
        helper.succeed();
    }

    @TestHolder("trinity_cycle_precision_rejects_fractional_settlement_without_false_infeasibility")
    @EmptyTemplate("5")
    @GameTest(template = "empty_5x5")
    public static void rejectsFractionalSettlementWithoutFalseInfeasibility(GameTestHelper helper) {
        AEKey first = AEItemKey.of(Items.IRON_INGOT);
        AEKey second = AEItemKey.of(Items.GOLD_INGOT);
        TrinityPatternVariant forward = conversion("fractional_forward", first, 3, second, 5);
        TrinityPatternVariant reverse = conversion("fractional_reverse", second, 6, first, 4);
        List<TrinityPatternVariant> variants = List.of(forward, reverse);
        BigInteger requested = BigInteger.valueOf(1_000_000_003L);
        TrinityCycleFeasibilityRequest request = new TrinityCycleFeasibilityRequest(
                variants, Set.of(first, second),
                new TrinityCycleDemand(Map.of(), Map.of(), Map.of(first, requested), Set.of(first)),
                Map.of(first, BigInteger.TEN, second, BigInteger.TEN), Set.of(),
                Map.of(forward, TrinityFiringBounds.fixed(requested.multiply(BigInteger.valueOf(3))),
                        reverse, new TrinityFiringBounds(BigInteger.ZERO, requested.shiftLeft(3))),
                Optional.empty(), BigInteger.ZERO, BigInteger.ZERO, false, 0,
                TrinityMipCoefficientTemplate.create(variants, List.of(first, second)));
        TrinityCycleFeasibilityModel ordinary = new TrinityOrdinaryCycleFeasibilityModel(
                TrinityIntegerResultVerifier.create(), TrinityExactConservationVerifier.create());
        var solved = ordinary.solve(request, TrinityPlanningMode.FIRST_FEASIBLE, TrinityPlanningControl.unbounded());
        helper.assertFalse(solved.successful(), "Rounding must not turn a half-firing into an executable integer order");
        helper.assertValueEqual(solved.diagnostic().code(), TrinityPlanningDiagnosticCode.ORDER_SEARCH_LIMIT,
                "The candidate probe must defer to exact solving instead of asserting global infeasibility");
        helper.succeed();
    }
}

package com.fish_dan_.data_energistics.common.crafting.trinity.execution.admission;

import com.fish_dan_.data_energistics.common.crafting.trinity.planning.CraftingQuantityMode;
import com.fish_dan_.data_energistics.common.crafting.trinity.planning.graph.TrinityPatternIdentity;
import com.fish_dan_.data_energistics.common.crafting.trinity.planning.graph.TrinityPlanningGraphTestBootstrap;
import com.fish_dan_.data_energistics.common.crafting.trinity.planning.plan.TrinityCraftingPlan;
import com.fish_dan_.data_energistics.common.crafting.trinity.planning.plan.TrinityCraftingPlanImpl;
import com.fish_dan_.data_energistics.common.crafting.trinity.planning.plan.TrinityPlanPatternFiring;
import com.fish_dan_.data_energistics.common.crafting.trinity.planning.plan.TrinityPlanStage;

import net.minecraft.world.level.material.Fluids;

import appeng.api.crafting.IPatternDetails;
import appeng.api.networking.crafting.ICraftingPlan;
import appeng.api.stacks.AEFluidKey;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.GenericStack;
import appeng.api.stacks.KeyCounter;
import appeng.crafting.CraftingPlan;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.math.BigInteger;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;

final class TrinityPlanAdmissionTest {

    private static AEKey input;
    private static AEKey output;

    @BeforeAll
    static void initialize() {
        TrinityPlanningGraphTestBootstrap.initialize();
        input = AEFluidKey.of(Fluids.LAVA);
        output = AEFluidKey.of(Fluids.WATER);
    }

    @Test
    void acceptsNativeAe2Plan() {
        assertEquals(
                TrinityPlanAdmission.Decision.SUBMIT_TO_TRINITY,
                TrinityPlanAdmission.create().decide(nativePlan(), TrinityPlanAdmission.Route.AUTOMATIC_SELECTION));
    }

    @Test
    void acceptsDataEnergisticsTrinityPlan() {
        assertEquals(
                TrinityPlanAdmission.Decision.SUBMIT_TO_TRINITY,
                TrinityPlanAdmission.create().decide(trinityPlan(), TrinityPlanAdmission.Route.DIRECT_CPU));
    }

    @Test
    void acceptsThirdPartyPlanOnlyThroughExplicitCompatibilityContract() {
        assertEquals(
                TrinityPlanAdmission.Decision.SUBMIT_TO_TRINITY,
                TrinityPlanAdmission.create().decide(
                        new CompatiblePlan(nativePlan()),
                        TrinityPlanAdmission.Route.FALLBACK));
    }

    @Test
    void rejectsLoopPlanForExplicitTrinityTarget() {
        assertEquals(
                TrinityPlanAdmission.Decision.REJECT_TRINITY,
                TrinityPlanAdmission.create().decide(
                        new LoopCraftingPlan(nativePlan()),
                        TrinityPlanAdmission.Route.EXPLICIT_TARGET));
    }

    @Test
    void defersLoopPlanDuringAutomaticSelection() {
        assertEquals(
                TrinityPlanAdmission.Decision.DEFER_TO_ORIGINAL,
                TrinityPlanAdmission.create().decide(
                        new LoopCraftingPlan(nativePlan()),
                        TrinityPlanAdmission.Route.AUTOMATIC_SELECTION));
    }

    @Test
    void defersLoopPlanDuringNoCpuFallback() {
        assertEquals(
                TrinityPlanAdmission.Decision.DEFER_TO_ORIGINAL,
                TrinityPlanAdmission.create().decide(
                        new LoopCraftingPlan(nativePlan()),
                        TrinityPlanAdmission.Route.FALLBACK));
    }

    private static CraftingPlan nativePlan() {
        return new CraftingPlan(
                new GenericStack(output, 1L),
                1L,
                false,
                false,
                new KeyCounter(),
                new KeyCounter(),
                new KeyCounter(),
                Map.of());
    }

    private static TrinityCraftingPlan trinityPlan() {
        TrinityPatternIdentity identity = new TrinityPatternIdentity("definition", "publication");
        TrinityPlanStage stage = new TrinityPlanStage(
                0,
                false,
                Set.of(),
                List.of(new TrinityPlanPatternFiring(identity, output, 0, BigInteger.ONE)),
                Map.of(input, BigInteger.ONE),
                Map.of(input, BigInteger.ONE.negate(), output, BigInteger.ONE));
        return TrinityCraftingPlanImpl.builder()
                .finalOutput(new GenericStack(output, 1L))
                .bytes(1L)
                .catalogRevision(1L)
                .quantityMode(CraftingQuantityMode.NET_NEW)
                .initialExpectedInputs(Map.of(input, BigInteger.ONE))
                .patternFirings(Map.of(identity, BigInteger.ONE))
                .stages(List.of(stage))
                .stageOrder(List.of(0))
                .cycleRepeatBlocks(List.of())
                .minimumSeed(Map.of())
                .targetNetChange(Map.of(input, BigInteger.ONE.negate(), output, BigInteger.ONE))
                .build();
    }

    private record LoopCraftingPlan(ICraftingPlan delegate) implements ICraftingPlan {

        @Override
        public GenericStack finalOutput() {
            return this.delegate.finalOutput();
        }

        @Override
        public long bytes() {
            return this.delegate.bytes();
        }

        @Override
        public boolean simulation() {
            return this.delegate.simulation();
        }

        @Override
        public boolean multiplePaths() {
            return this.delegate.multiplePaths();
        }

        @Override
        public KeyCounter usedItems() {
            return this.delegate.usedItems();
        }

        @Override
        public KeyCounter emittedItems() {
            return this.delegate.emittedItems();
        }

        @Override
        public KeyCounter missingItems() {
            return this.delegate.missingItems();
        }

        @Override
        public Map<IPatternDetails, Long> patternTimes() {
            return this.delegate.patternTimes();
        }
    }

    private record CompatiblePlan(ICraftingPlan delegate) implements TrinityCpuExecutablePlan {

        @Override
        public GenericStack finalOutput() {
            return this.delegate.finalOutput();
        }

        @Override
        public long bytes() {
            return this.delegate.bytes();
        }

        @Override
        public boolean simulation() {
            return this.delegate.simulation();
        }

        @Override
        public boolean multiplePaths() {
            return this.delegate.multiplePaths();
        }

        @Override
        public KeyCounter usedItems() {
            return this.delegate.usedItems();
        }

        @Override
        public KeyCounter emittedItems() {
            return this.delegate.emittedItems();
        }

        @Override
        public KeyCounter missingItems() {
            return this.delegate.missingItems();
        }

        @Override
        public Map<IPatternDetails, Long> patternTimes() {
            return this.delegate.patternTimes();
        }
    }
}

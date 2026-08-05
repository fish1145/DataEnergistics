package com.fish_dan_.data_energistics.common.crafting.trinity.execution.state;

import com.fish_dan_.data_energistics.common.crafting.trinity.planning.CraftingQuantityMode;
import com.fish_dan_.data_energistics.common.crafting.trinity.planning.graph.TrinityPatternIdentity;
import com.fish_dan_.data_energistics.common.crafting.trinity.planning.plan.TrinityCraftingPlan;
import com.fish_dan_.data_energistics.common.crafting.trinity.planning.plan.TrinityCraftingPlanImpl;
import com.fish_dan_.data_energistics.common.crafting.trinity.planning.plan.TrinityCycleRepeatBlock;
import com.fish_dan_.data_energistics.common.crafting.trinity.planning.plan.TrinityPlanPatternFiring;
import com.fish_dan_.data_energistics.common.crafting.trinity.planning.plan.TrinityPlanStage;

import net.minecraft.SharedConstants;
import net.minecraft.core.Registry;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.level.material.Fluids;
import net.neoforged.fml.loading.LoadingModList;
import net.neoforged.neoforge.registries.RegistryBuilder;

import appeng.api.stacks.AEFluidKey;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.AEKeyType;
import appeng.api.stacks.AEKeyTypesInternal;
import appeng.api.stacks.GenericStack;

import java.math.BigInteger;
import java.util.List;
import java.util.Map;
import java.util.Set;

final class TrinityExecutionStateTestSupport {

    static final TrinityPatternIdentity FIRST = new TrinityPatternIdentity("first-definition", "first-publication");
    static final TrinityPatternIdentity SECOND = new TrinityPatternIdentity("second-definition", "second-publication");

    private TrinityExecutionStateTestSupport() {}

    @SuppressWarnings("UnstableApiUsage")
    static void initialize() {
        if (LoadingModList.get() == null) {
            LoadingModList.of(List.of(), List.of(), List.of(), List.of(), Map.of());
        }
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
        synchronized (AEKeyTypesInternal.class) {
            boolean initialized;
            try {
                initialized = AEKeyTypesInternal.getRegistry() != null;
            } catch (IllegalStateException notInitialized) {
                initialized = false;
            }
            if (!initialized) {
                Registry<AEKeyType> registry = new RegistryBuilder<>(AEKeyType.REGISTRY_KEY)
                        .disableRegistrationCheck()
                        .create();
                AEKeyTypesInternal.setRegistry(registry);
                Registry.register(registry, AEKeyType.items().getId(), AEKeyType.items());
                Registry.register(registry, AEKeyType.fluids().getId(), AEKeyType.fluids());
                registry.freeze();
            }
        }
    }

    static AEKey data() {
        return AEFluidKey.of(Fluids.WATER);
    }

    static AEKey flow() {
        return AEFluidKey.of(Fluids.LAVA);
    }

    static AEKey echo() {
        return AEFluidKey.of(Fluids.FLOWING_WATER);
    }

    static TrinityCraftingPlan dagPlan(long count) {
        AEKey input = flow();
        AEKey target = data();
        BigInteger amount = BigInteger.valueOf(count);
        TrinityPlanStage stage = new TrinityPlanStage(
                0,
                false,
                Set.of(),
                List.of(new TrinityPlanPatternFiring(FIRST, target, 0, amount)),
                Map.of(input, amount),
                Map.of(input, amount.negate(), target, amount));
        return TrinityCraftingPlanImpl.builder()
                .finalOutput(new GenericStack(target, count))
                .bytes(16L)
                .catalogRevision(7L)
                .quantityMode(CraftingQuantityMode.NET_NEW)
                .initialExpectedInputs(Map.of(input, amount))
                .patternFirings(Map.of(FIRST, amount))
                .stages(List.of(stage))
                .stageOrder(List.of(0))
                .cycleRepeatBlocks(List.of())
                .minimumSeed(Map.of())
                .targetNetChange(Map.of(input, amount.negate(), target, amount))
                .build();
    }

    static TrinityCraftingPlan multiFiringDagPlan() {
        AEKey input = echo();
        AEKey intermediate = flow();
        AEKey target = data();
        TrinityPlanStage stage = new TrinityPlanStage(
                0,
                false,
                Set.of(),
                List.of(
                        new TrinityPlanPatternFiring(FIRST, intermediate, 0, BigInteger.TWO),
                        new TrinityPlanPatternFiring(SECOND, target, 1, BigInteger.valueOf(3L))),
                Map.of(input, BigInteger.ONE),
                Map.of(input, BigInteger.ONE.negate(), target, BigInteger.ONE));
        return TrinityCraftingPlanImpl.builder()
                .finalOutput(new GenericStack(target, 1L))
                .bytes(16L)
                .catalogRevision(8L)
                .quantityMode(CraftingQuantityMode.NET_NEW)
                .initialExpectedInputs(Map.of(input, BigInteger.ONE))
                .patternFirings(Map.of(FIRST, BigInteger.TWO, SECOND, BigInteger.valueOf(3L)))
                .stages(List.of(stage))
                .stageOrder(List.of(0))
                .cycleRepeatBlocks(List.of())
                .minimumSeed(Map.of())
                .targetNetChange(Map.of(input, BigInteger.ONE.negate(), target, BigInteger.ONE))
                .build();
    }

    static TrinityCraftingPlan selfCyclePlan(long repetitions) {
        AEKey target = data();
        BigInteger repeated = BigInteger.valueOf(repetitions);
        TrinityPlanStage stage = new TrinityPlanStage(
                0,
                true,
                Set.of(),
                List.of(new TrinityPlanPatternFiring(
                        FIRST,
                        target,
                        0,
                        BigInteger.ONE,
                        Map.of(target, BigInteger.TWO))),
                Map.of(target, BigInteger.ONE),
                Map.of(target, BigInteger.ONE));
        TrinityCycleRepeatBlock block = new TrinityCycleRepeatBlock(
                0,
                List.of(0),
                repeated,
                Map.of(target, BigInteger.ONE),
                Map.of(target, repeated));
        return TrinityCraftingPlanImpl.builder()
                .finalOutput(new GenericStack(target, repetitions))
                .bytes(24L)
                .catalogRevision(9L)
                .quantityMode(CraftingQuantityMode.NET_NEW)
                .initialExpectedInputs(Map.of(target, BigInteger.ONE))
                .patternFirings(Map.of(FIRST, repeated))
                .stages(List.of(stage))
                .stageOrder(List.of(0))
                .cycleRepeatBlocks(List.of(block))
                .minimumSeed(Map.of(target, BigInteger.ONE))
                .targetNetChange(Map.of(target, repeated))
                .build();
    }

    static TrinityCraftingPlan multiStepCyclePlan() {
        long repetitions = 4L;
        AEKey seed = data();
        AEKey intermediate = flow();
        BigInteger repeated = BigInteger.valueOf(repetitions);
        TrinityPlanStage first = new TrinityPlanStage(
                0,
                true,
                Set.of(),
                List.of(new TrinityPlanPatternFiring(FIRST, intermediate, 0, BigInteger.ONE)),
                Map.of(seed, BigInteger.ONE),
                Map.of(seed, BigInteger.ONE.negate(), intermediate, BigInteger.ONE));
        TrinityPlanStage second = new TrinityPlanStage(
                1,
                true,
                Set.of(0),
                List.of(new TrinityPlanPatternFiring(
                        SECOND,
                        seed,
                        0,
                        BigInteger.ONE,
                        Map.of(seed, BigInteger.TWO))),
                Map.of(intermediate, BigInteger.ONE),
                Map.of(intermediate, BigInteger.ONE.negate(), seed, BigInteger.TWO));
        TrinityCycleRepeatBlock block = new TrinityCycleRepeatBlock(
                0,
                List.of(0, 1),
                repeated,
                Map.of(seed, BigInteger.ONE),
                Map.of(seed, repeated));
        return TrinityCraftingPlanImpl.builder()
                .finalOutput(new GenericStack(seed, repetitions))
                .bytes(32L)
                .catalogRevision(10L)
                .quantityMode(CraftingQuantityMode.NET_NEW)
                .initialExpectedInputs(Map.of(seed, BigInteger.ONE))
                .patternFirings(Map.of(FIRST, repeated, SECOND, repeated))
                .stages(List.of(first, second))
                .stageOrder(List.of(0, 1))
                .cycleRepeatBlocks(List.of(block))
                .minimumSeed(Map.of(seed, BigInteger.ONE))
                .targetNetChange(Map.of(seed, repeated))
                .build();
    }

    static TrinityCraftingPlan sharedSeedCyclePlan() {
        AEKey seed = data();
        AEKey intermediate = flow();
        AEKey target = echo();
        BigInteger repetitions = BigInteger.TWO;
        TrinityPlanStage first = new TrinityPlanStage(
                0,
                true,
                Set.of(),
                List.of(new TrinityPlanPatternFiring(
                        FIRST,
                        intermediate,
                        0,
                        BigInteger.ONE,
                        Map.of(intermediate, BigInteger.ONE))),
                Map.of(seed, BigInteger.ONE),
                Map.of(seed, BigInteger.ONE.negate(), intermediate, BigInteger.ONE));
        TrinityPlanStage second = new TrinityPlanStage(
                1,
                true,
                Set.of(0),
                List.of(new TrinityPlanPatternFiring(
                        SECOND,
                        target,
                        0,
                        BigInteger.ONE,
                        Map.of(seed, BigInteger.TWO, target, BigInteger.ONE))),
                Map.of(seed, BigInteger.ONE, intermediate, BigInteger.ONE),
                Map.of(seed, BigInteger.ONE, intermediate, BigInteger.ONE.negate(), target, BigInteger.ONE));
        TrinityCycleRepeatBlock block = new TrinityCycleRepeatBlock(
                0,
                List.of(0, 1),
                repetitions,
                Map.of(seed, BigInteger.TWO),
                Map.of(target, repetitions));
        return TrinityCraftingPlanImpl.builder()
                .finalOutput(new GenericStack(target, repetitions.longValueExact()))
                .bytes(32L)
                .catalogRevision(11L)
                .quantityMode(CraftingQuantityMode.NET_NEW)
                .initialExpectedInputs(Map.of(seed, BigInteger.TWO))
                .patternFirings(Map.of(FIRST, repetitions, SECOND, repetitions))
                .stages(List.of(first, second))
                .stageOrder(List.of(0, 1))
                .cycleRepeatBlocks(List.of(block))
                .minimumSeed(Map.of(seed, BigInteger.TWO))
                .targetNetChange(Map.of(target, repetitions))
                .build();
    }
}

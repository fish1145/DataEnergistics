package com.fish_dan_.data_energistics.common.crafting.trinity.execution.state;

import net.minecraft.core.RegistryAccess;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;

import appeng.api.stacks.AEKey;
import appeng.api.stacks.GenericStack;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

public final class TrinityPlanExecutionTest {

    @BeforeAll
    static void initializeRegistries() {
        TrinityExecutionStateTestSupport.initialize();
    }

    @Test
    void unrelatedKeysDoNotWakeAndReadyQueueDeduplicatesRepeatedEvents() {
        AEKey iron = TrinityExecutionStateTestSupport.flow();
        AEKey gold = TrinityExecutionStateTestSupport.echo();
        TrinityPlanExecution execution = TrinityPlanExecution.create(
                TrinityExecutionStateTestSupport.dagPlan(2L),
                0L);
        TrinityPlanExecution.Work work = execution.poll(0L).orElseThrow();

        execution.deferInput(work, Set.of(iron));

        assertEquals(TrinityPlanExecution.Status.WAITING_INPUT, execution.status());
        assertFalse(execution.wake(gold));
        assertEquals(0, execution.queuedStageCount());
        assertTrue(execution.wake(iron));
        assertFalse(execution.wake(iron));
        assertEquals(1, execution.queuedStageCount());
        assertEquals(work.patternIdentity(), execution.poll(0L).orElseThrow().patternIdentity());
    }

    @Test
    void providerAndDynamicRetriesUseIndependentExponentialBackoff() {
        TrinityPlanExecution provider = TrinityPlanExecution.create(
                TrinityExecutionStateTestSupport.dagPlan(1L),
                0L);
        TrinityPlanExecution.Work providerWork = provider.poll(0L).orElseThrow();
        provider.deferProvider(providerWork, 0L, 200);
        assertEquals(TrinityPlanExecution.Status.WAITING_PROVIDER, provider.status());
        assertTrue(provider.poll(0L).isEmpty());
        providerWork = provider.poll(1L).orElseThrow();
        provider.deferProvider(providerWork, 1L, 200);
        assertTrue(provider.poll(2L).isEmpty());
        assertTrue(provider.poll(3L).isPresent());

        AEKey redstone = TrinityExecutionStateTestSupport.data();
        TrinityPlanExecution dynamic = TrinityPlanExecution.create(
                TrinityExecutionStateTestSupport.selfCyclePlan(2L),
                10L);
        TrinityPlanExecution.Work dynamicWork = dynamic.poll(10L).orElseThrow();
        dynamic.deferDynamicInput(dynamicWork, Set.of(redstone), 10L, 200);
        assertEquals(TrinityPlanExecution.Status.WAITING_DYNAMIC_INPUT, dynamic.status());
        assertTrue(dynamic.poll(10L).isEmpty());
        dynamicWork = dynamic.poll(11L).orElseThrow();
        dynamic.deferDynamicInput(dynamicWork, Set.of(redstone), 11L, 200);
        assertTrue(dynamic.poll(12L).isEmpty());
        assertTrue(dynamic.poll(13L).isPresent());
    }

    @Test
    void timedRetriesRebaseAcrossServerRestartAndLegacyDeadlinesExpireOnce() {
        long previousSessionTick = 500_000L;
        TrinityPlanExecution provider = TrinityPlanExecution.create(
                TrinityExecutionStateTestSupport.dagPlan(1L),
                previousSessionTick);
        provider.deferProvider(provider.poll(previousSessionTick).orElseThrow(), previousSessionTick, 200);
        CompoundTag providerTag = provider.save(RegistryAccess.EMPTY, previousSessionTick);
        TrinityPlanExecution restoredProvider = TrinityPlanExecution.restore(
                providerTag,
                RegistryAccess.EMPTY,
                0L);
        assertEquals(TrinityPlanExecution.Status.WAITING_PROVIDER, restoredProvider.status());
        assertTrue(restoredProvider.poll(0L).isEmpty());
        assertTrue(restoredProvider.poll(1L).isPresent());

        TrinityPlanExecution dynamic = TrinityPlanExecution.create(
                TrinityExecutionStateTestSupport.selfCyclePlan(1L),
                previousSessionTick);
        dynamic.deferDynamicInput(
                dynamic.poll(previousSessionTick).orElseThrow(),
                Set.of(TrinityExecutionStateTestSupport.data()),
                previousSessionTick,
                200);
        TrinityPlanExecution restoredDynamic = TrinityPlanExecution.restore(
                dynamic.save(RegistryAccess.EMPTY, previousSessionTick),
                RegistryAccess.EMPTY,
                0L);
        assertTrue(restoredDynamic.poll(0L).isEmpty());
        assertTrue(restoredDynamic.poll(1L).isPresent());

        TrinityPlanExecution budgeted = TrinityPlanExecution.create(
                TrinityExecutionStateTestSupport.dagPlan(1L),
                previousSessionTick);
        budgeted.markBudgetExhausted(
                budgeted.poll(previousSessionTick).orElseThrow(),
                previousSessionTick);
        TrinityPlanExecution restoredBudget = TrinityPlanExecution.restore(
                budgeted.save(RegistryAccess.EMPTY, previousSessionTick),
                RegistryAccess.EMPTY,
                0L);
        assertTrue(restoredBudget.poll(0L).isEmpty());
        assertTrue(restoredBudget.poll(1L).isPresent());

        CompoundTag clockSchemaTag = providerTag.copy();
        clockSchemaTag.putInt("schema_version", 3);
        removeFiringOutputs(clockSchemaTag);
        TrinityPlanExecution migratedClock = TrinityPlanExecution.restore(
                clockSchemaTag,
                RegistryAccess.EMPTY,
                0L);
        assertTrue(migratedClock.pendingOutputs().isEmpty());
        assertTrue(migratedClock.poll(0L).isEmpty());
        assertTrue(migratedClock.poll(1L).isPresent());

        providerTag.putInt("schema_version", 2);
        providerTag.remove("saved_at_tick");
        removeFiringOutputs(providerTag);
        TrinityPlanExecution migratedProvider = TrinityPlanExecution.restore(
                providerTag,
                RegistryAccess.EMPTY,
                0L);
        assertTrue(migratedProvider.poll(0L).isPresent());
    }

    @Test
    void pendingOutputsTrackDagAndCycleCursorsAcrossReload() {
        AEKey target = TrinityExecutionStateTestSupport.data();
        TrinityPlanExecution execution = TrinityPlanExecution.create(
                TrinityExecutionStateTestSupport.dagPlan(2L),
                0L);

        assertEquals(2L, execution.pendingOutputs().get(target));
        execution.recordAccepted(execution.poll(0L).orElseThrow(), 1L);
        assertEquals(1L, execution.pendingOutputs().get(target));

        TrinityPlanExecution restored = roundTrip(execution, 0L);
        assertEquals(1L, restored.pendingOutputs().get(target));
        restored.recordAccepted(restored.poll(0L).orElseThrow(), 1L);
        assertTrue(restored.pendingOutputs().isEmpty());

        TrinityPlanExecution selfCycle = TrinityPlanExecution.create(
                TrinityExecutionStateTestSupport.selfCyclePlan(7L),
                0L);
        assertEquals(Map.of(target, 14L), selfCycle.pendingOutputs());
        selfCycle.recordAccepted(selfCycle.poll(0L).orElseThrow(), 1L);
        assertEquals(Map.of(target, 12L), selfCycle.pendingOutputs());
        selfCycle.recordAccepted(selfCycle.poll(0L).orElseThrow(), 2L);
        assertEquals(Map.of(target, 8L), roundTrip(selfCycle, 0L).pendingOutputs());

        AEKey intermediate = TrinityExecutionStateTestSupport.flow();
        TrinityPlanExecution multiStep = TrinityPlanExecution.create(
                TrinityExecutionStateTestSupport.multiStepCyclePlan(),
                0L);
        assertEquals(Map.of(target, 8L, intermediate, 4L), multiStep.pendingOutputs());
        multiStep.recordAccepted(multiStep.poll(0L).orElseThrow(), 2L);
        TrinityPlanExecution restoredMultiStep = roundTrip(multiStep, 0L);
        assertEquals(Map.of(target, 8L, intermediate, 2L), restoredMultiStep.pendingOutputs());
        restoredMultiStep.recordAccepted(restoredMultiStep.poll(0L).orElseThrow(), 2L);
        assertEquals(Map.of(target, 4L, intermediate, 2L), restoredMultiStep.pendingOutputs());
    }

    @Test
    void budgetGateAndReplanningPreserveWorkWithoutAcceptingStaleOffers() {
        TrinityPlanExecution budgeted = TrinityPlanExecution.create(
                TrinityExecutionStateTestSupport.dagPlan(2L),
                5L);
        TrinityPlanExecution.Work exhausted = budgeted.poll(5L).orElseThrow();
        budgeted.markBudgetExhausted(exhausted, 5L);
        assertEquals(TrinityPlanExecution.Status.BUDGET_EXHAUSTED, budgeted.status());
        assertTrue(budgeted.poll(5L).isEmpty());

        TrinityPlanExecution restoredBudget = roundTrip(budgeted, 5L);
        assertEquals(TrinityPlanExecution.Status.BUDGET_EXHAUSTED, restoredBudget.status());
        assertEquals(2L, restoredBudget.poll(6L).orElseThrow().maximumLogicalFirings());

        TrinityPlanExecution replanned = TrinityPlanExecution.create(
                TrinityExecutionStateTestSupport.dagPlan(2L),
                0L);
        TrinityPlanExecution.Work stale = replanned.poll(0L).orElseThrow();
        replanned.markPlanning(stale);
        assertEquals(TrinityPlanExecution.Status.PLANNING, replanned.status());
        replanned.replaceRemainingPlan(TrinityExecutionStateTestSupport.dagPlan(2L), 0L);
        TrinityPlanExecution.Work replacement = replanned.poll(0L).orElseThrow();
        assertEquals(1L, replacement.generation());
        assertThrows(IllegalStateException.class, () -> replanned.recordAccepted(stale, 1L));
    }

    @Test
    void selfCycleAdvancesInCountedOneTwoFourWaves() {
        TrinityPlanExecution execution = TrinityPlanExecution.create(
                TrinityExecutionStateTestSupport.selfCyclePlan(7L),
                0L);

        TrinityPlanExecution.Work first = execution.poll(0L).orElseThrow();
        assertTrue(first.cycle());
        assertEquals(7L, first.maximumLogicalFirings());
        execution.recordAccepted(first, 1L);

        TrinityPlanExecution.Work second = execution.poll(0L).orElseThrow();
        assertEquals(6L, second.maximumLogicalFirings());
        execution.recordAccepted(second, 2L);

        TrinityPlanExecution.Work third = execution.poll(0L).orElseThrow();
        assertEquals(4L, third.maximumLogicalFirings());
        execution.recordAccepted(third, 4L);

        assertTrue(execution.productionComplete());
        assertEquals(TrinityPlanExecution.Status.COMPLETED, execution.status());
    }

    @Test
    void cycleSeedLimitPreventsTheFirstPathFromConsumingSharedSeedAndSurvivesRoundTrip() {
        AEKey seed = TrinityExecutionStateTestSupport.data();
        TrinityPlanExecution execution = TrinityPlanExecution.create(
                TrinityExecutionStateTestSupport.sharedSeedCyclePlan(),
                0L);
        TrinityPlanExecution.Work first = execution.poll(0L).orElseThrow();
        long durableRevision = execution.durableRevision();

        assertEquals(2L, first.maximumLogicalFirings());
        TrinityPlanExecution.CycleWaveLimit initialLimit = execution.maximumCycleLogicalFirings(
                first,
                key -> key.equals(seed) ? 2L : 0L);
        assertEquals(1L, initialLimit.maximumLogicalFirings());
        assertEquals(Set.of(seed), initialLimit.observedKeys());
        assertEquals(0L, execution.maximumCycleLogicalFirings(first, ignored -> 0L).maximumLogicalFirings());
        assertEquals(durableRevision, execution.durableRevision());

        TrinityPlanExecution restored = roundTrip(execution, 0L);
        TrinityPlanExecution.Work restoredFirst = restored.poll(0L).orElseThrow();
        assertEquals(1L, restored.maximumCycleLogicalFirings(
                restoredFirst,
                key -> key.equals(seed) ? 2L : 0L).maximumLogicalFirings());

        CompoundTag clockSchemaTag = execution.save(RegistryAccess.EMPTY, 0L);
        clockSchemaTag.putInt("schema_version", 3);
        removeFiringOutputs(clockSchemaTag);
        TrinityPlanExecution clockSchemaRestored = TrinityPlanExecution.restore(
                clockSchemaTag,
                RegistryAccess.EMPTY,
                0L);
        assertEquals(1L, clockSchemaRestored.maximumCycleLogicalFirings(
                clockSchemaRestored.poll(0L).orElseThrow(),
                key -> key.equals(seed) ? 2L : 0L).maximumLogicalFirings());

        CompoundTag legacySchemaTag = clockSchemaTag.copy();
        legacySchemaTag.putInt("schema_version", 2);
        legacySchemaTag.remove("saved_at_tick");
        TrinityPlanExecution legacySchemaRestored = TrinityPlanExecution.restore(
                legacySchemaTag,
                RegistryAccess.EMPTY,
                0L);
        assertEquals(1L, legacySchemaRestored.maximumCycleLogicalFirings(
                legacySchemaRestored.poll(0L).orElseThrow(),
                key -> key.equals(seed) ? 2L : 0L).maximumLogicalFirings());

        restored.recordAccepted(restoredFirst, 1L);
        TrinityPlanExecution.Work establishedWave = restored.poll(0L).orElseThrow();
        assertEquals(1, establishedWave.stageIndex());
        TrinityPlanExecution.CycleWaveLimit establishedLimit = restored.maximumCycleLogicalFirings(
                establishedWave,
                ignored -> 0L);
        assertEquals(1L, establishedLimit.maximumLogicalFirings());
        assertEquals(Set.of(seed), establishedLimit.observedKeys());
    }

    @Test
    void multiStepCycleCarriesEstablishedWaveAcrossStages() {
        TrinityPlanExecution execution = TrinityPlanExecution.create(
                TrinityExecutionStateTestSupport.multiStepCyclePlan(),
                0L);

        TrinityPlanExecution.Work firstStage = execution.poll(0L).orElseThrow();
        assertEquals(0, firstStage.stageIndex());
        execution.recordAccepted(firstStage, 2L);

        TrinityPlanExecution.Work secondStage = execution.poll(0L).orElseThrow();
        assertEquals(1, secondStage.stageIndex());
        assertEquals(2L, secondStage.maximumLogicalFirings());
        execution.recordAccepted(secondStage, 2L);

        TrinityPlanExecution.Work nextWave = execution.poll(0L).orElseThrow();
        assertEquals(0, nextWave.stageIndex());
        assertEquals(2L, nextWave.maximumLogicalFirings());
    }

    @Test
    void genericSingleAcceptanceAndMultipleStageFiringsAdvanceExactly() {
        TrinityPlanExecution generic = TrinityPlanExecution.create(
                TrinityExecutionStateTestSupport.selfCyclePlan(3L),
                0L);
        for (long maximum = 3L; maximum >= 1L; maximum--) {
            TrinityPlanExecution.Work work = generic.poll(0L).orElseThrow();
            assertEquals(maximum, work.maximumLogicalFirings());
            generic.recordAccepted(work, 1L);
        }
        assertTrue(generic.productionComplete());

        TrinityPlanExecution multiple = TrinityPlanExecution.create(
                TrinityExecutionStateTestSupport.multiFiringDagPlan(),
                0L);
        TrinityPlanExecution.Work first = multiple.poll(0L).orElseThrow();
        assertEquals(0, first.firingIndex());
        assertEquals(2L, first.maximumLogicalFirings());
        multiple.recordAccepted(first, 1L);
        first = multiple.poll(0L).orElseThrow();
        assertEquals(1L, first.maximumLogicalFirings());
        multiple.recordAccepted(first, 1L);
        TrinityPlanExecution.Work second = multiple.poll(0L).orElseThrow();
        assertEquals(1, second.firingIndex());
        assertEquals(3L, second.maximumLogicalFirings());
        multiple.recordAccepted(second, 3L);
        assertTrue(multiple.productionComplete());
    }

    @Test
    void completionBufferIsolatedAndPartialDeliveryDeductsOnlyAcceptedAmount() {
        AEKey target = TrinityExecutionStateTestSupport.data();
        TrinityPlanExecution execution = TrinityPlanExecution.create(
                TrinityExecutionStateTestSupport.dagPlan(4L),
                0L);
        TrinityPlanExecution.Work work = execution.poll(0L).orElseThrow();
        execution.recordAccepted(work, 4L);

        execution.sealCompletion(4L);
        assertEquals(new GenericStack(target, 4L), execution.completionOffer().orElseThrow());
        execution.recordDelivered(1L);
        assertEquals(new GenericStack(target, 3L), execution.completionOffer().orElseThrow());
        assertEquals(3L, execution.deliveryRemaining());
        assertThrows(IllegalArgumentException.class, () -> execution.recordDelivered(4L));
        assertEquals(new GenericStack(target, 3L), execution.releaseCompletionForStandalone().orElseThrow());
        assertEquals(0L, execution.deliveryRemaining());
        assertTrue(execution.completionOffer().isEmpty());
    }

    @Test
    void deadlockPredicateRejectsEveryKnownProgressOrWaitSource() {
        AEKey iron = TrinityExecutionStateTestSupport.flow();
        TrinityPlanExecution execution = TrinityPlanExecution.create(
                TrinityExecutionStateTestSupport.dagPlan(1L),
                0L);
        assertFalse(execution.deadlocked(false));
        TrinityPlanExecution.Work work = execution.poll(0L).orElseThrow();
        assertFalse(execution.deadlocked(false));
        execution.deferInput(work, Set.of(iron));
        assertFalse(execution.deadlocked(false));
        assertFalse(execution.deadlocked(true));
        execution.wake(iron);
        work = execution.poll(0L).orElseThrow();
        execution.markPlanning(work);
        assertFalse(execution.deadlocked(false));
    }

    @Test
    void ordinaryWaitRoundTripRebuildsReadyQueueAndInputIndex() {
        AEKey iron = TrinityExecutionStateTestSupport.flow();
        AEKey gold = TrinityExecutionStateTestSupport.echo();
        TrinityPlanExecution execution = TrinityPlanExecution.create(
                TrinityExecutionStateTestSupport.dagPlan(2L),
                5L);
        execution.deferInput(execution.poll(5L).orElseThrow(), Set.of(iron));

        TrinityPlanExecution restored = roundTrip(execution, 5L);

        assertEquals(TrinityPlanExecution.Status.WAITING_INPUT, restored.status());
        assertFalse(restored.wake(gold));
        assertTrue(restored.wake(iron));
        assertEquals(1, restored.queuedStageCount());
        assertEquals(2L, restored.poll(5L).orElseThrow().maximumLogicalFirings());
    }

    @Test
    void cycleBorrowingAndCompletionRoundTripsRetainExactCursorsAndOwnership() {
        AEKey redstone = TrinityExecutionStateTestSupport.data();
        AEKey iron = TrinityExecutionStateTestSupport.echo();
        TrinityPlanExecution cycle = TrinityPlanExecution.create(
                TrinityExecutionStateTestSupport.multiStepCyclePlan(),
                0L);
        TrinityPlanExecution.Work first = cycle.poll(0L).orElseThrow();
        cycle.recordAccepted(first, 2L);
        cycle.borrowingLedger().reserve(iron, 5L);
        cycle.borrowingLedger().commit(iron, 2L);
        cycle.borrowingLedger().release(iron, 1L);

        TrinityPlanExecution restoredCycle = roundTrip(cycle, 0L);

        TrinityPlanExecution.Work second = restoredCycle.poll(0L).orElseThrow();
        assertEquals(1, second.stageIndex());
        assertEquals(2L, second.maximumLogicalFirings());
        assertEquals(2L, restoredCycle.borrowingLedger().amount(iron, TrinityBorrowingLedger.State.RESERVED));
        assertEquals(2L, restoredCycle.borrowingLedger().amount(iron, TrinityBorrowingLedger.State.COMMITTED));
        assertEquals(1L, restoredCycle.borrowingLedger().amount(iron, TrinityBorrowingLedger.State.RELEASED));
        assertEquals(1L, restoredCycle.seedReserve().get(redstone));

        TrinityPlanExecution completed = TrinityPlanExecution.create(
                TrinityExecutionStateTestSupport.dagPlan(3L),
                0L);
        completed.recordAccepted(completed.poll(0L).orElseThrow(), 3L);
        completed.sealCompletion(3L);
        completed.recordDelivered(1L);
        TrinityPlanExecution restoredCompletion = roundTrip(completed, 0L);
        assertEquals(2L, restoredCompletion.deliveryRemaining());
        assertEquals(2L, restoredCompletion.completionOffer().orElseThrow().amount());
    }

    @Test
    void currentSchemaRejectsUnknownAndDamagedFields() {
        TrinityPlanExecution execution = TrinityPlanExecution.create(
                TrinityExecutionStateTestSupport.dagPlan(1L),
                0L);
        CompoundTag unknownField = execution.save(RegistryAccess.EMPTY, 0L);
        unknownField.putBoolean("unexpected", true);
        assertThrows(IllegalArgumentException.class,
                () -> TrinityPlanExecution.restore(unknownField, RegistryAccess.EMPTY, 0L));

        CompoundTag unknownStatus = execution.save(RegistryAccess.EMPTY, 0L);
        unknownStatus.putString("status", "UNKNOWN");
        assertThrows(IllegalArgumentException.class,
                () -> TrinityPlanExecution.restore(unknownStatus, RegistryAccess.EMPTY, 0L));

        CompoundTag damagedFiring = execution.save(RegistryAccess.EMPTY, 0L);
        ListTag stages = damagedFiring.getList("stages", CompoundTag.TAG_COMPOUND);
        stages.getCompound(0).getList("firings", CompoundTag.TAG_COMPOUND)
                .getCompound(0).putLong("planned_count", 0L);
        assertThrows(IllegalArgumentException.class,
                () -> TrinityPlanExecution.restore(damagedFiring, RegistryAccess.EMPTY, 0L));
    }

    private static TrinityPlanExecution roundTrip(TrinityPlanExecution execution, long currentTick) {
        return TrinityPlanExecution.restore(
                execution.save(RegistryAccess.EMPTY, currentTick),
                RegistryAccess.EMPTY,
                currentTick);
    }

    private static void removeFiringOutputs(CompoundTag executionTag) {
        for (Tag stageTag : executionTag.getList("stages", Tag.TAG_COMPOUND)) {
            for (Tag firingTag : ((CompoundTag) stageTag).getList("firings", Tag.TAG_COMPOUND)) {
                ((CompoundTag) firingTag).remove("outputs");
            }
        }
    }
}

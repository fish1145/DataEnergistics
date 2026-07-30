package com.fish_dan_.data_energistics.common.crafting.trinity.dispatch;

import appeng.api.config.CpuSelectionMode;
import appeng.api.networking.crafting.CraftingSubmitErrorCode;
import appeng.api.networking.crafting.UnsuitableCpus;
import appeng.crafting.execution.CraftingSubmitResult;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

public final class CraftingCpuCandidateSelectorTest {

    private final CraftingCpuCandidateSelector selector = CraftingCpuCandidateSelector.create();

    @Test
    void filtersAvailabilitySharingStorageAndSourceBeforeOrdering() {
        CraftingCpuCandidate eligibleAny = candidate("eligible-any");
        CraftingCpuCandidate preferredMachine = candidate("preferred-machine", builder()
                .selectionMode(CpuSelectionMode.MACHINE_ONLY));
        CraftingCpuCandidate supportedExternal = candidate("supported-external", builder()
                .kind(CraftingCpuKind.SUPPORTED_EXTERNAL));
        CraftingCpuCandidate offline = candidate("offline", builder().online(false));
        CraftingCpuCandidate busy = candidate("busy", builder().acceptsJob(false));
        CraftingCpuCandidate shared = candidate("shared", builder().shared(true));
        CraftingCpuCandidate tooSmall = candidate("too-small", builder().storageBytes(3L));
        CraftingCpuCandidate wrongSource = candidate("player-only", builder()
                .selectionMode(CpuSelectionMode.PLAYER_ONLY));

        List<CraftingCpuCandidate> selected = this.selector.select(
                List.of(eligibleAny, offline, busy, shared, tooSmall, wrongSource, supportedExternal, preferredMachine),
                request(4L, false, true, Map.of()));

        assertEquals(
                List.of("preferred-machine", "eligible-any", "supported-external"),
                identities(selected));
    }

    @Test
    void reportsAggregateUnsuitableReasonsBeforeSubmission() {
        CraftingCpuCandidateSelection selection = this.selector.evaluate(
                List.of(
                        candidate("offline", builder().online(false)),
                        candidate("busy", builder().acceptsJob(false)),
                        candidate("too-small", builder().storageBytes(3L)),
                        candidate("shared", builder().shared(true)),
                        candidate("wrong-source", builder().selectionMode(CpuSelectionMode.PLAYER_ONLY)),
                        candidate("eligible")),
                request(4L, false, true, Map.of()));

        assertEquals(List.of("eligible"), identities(selection.candidates()));
        assertEquals(new UnsuitableCpus(1, 1, 1, 2), selection.unsuitableCpus());
        assertTrue(selection.hasUnsuitableCpus());
        assertFalse(this.selector.evaluate(
                List.of(candidate("eligible")),
                request(1L, false, true, Map.of())).hasUnsuitableCpus());
    }

    @Test
    void preservesAe2PowerPreferenceBeforeLoadOrdering() {
        CraftingCpuCandidate lowPower = candidate("low", builder()
                .coProcessors(1)
                .storageBytes(16L));
        CraftingCpuCandidate highPower = candidate("high", builder()
                .coProcessors(4)
                .storageBytes(64L)
                .activeJobs(20)
                .recentOperationLoad(20L));

        assertEquals(
                List.of("high", "low"),
                identities(this.selector.select(
                        List.of(lowPower, highPower),
                        request(1L, false, true, Map.of()))));
        assertEquals(
                List.of("low", "high"),
                identities(this.selector.select(
                        List.of(highPower, lowPower),
                        request(1L, false, false, Map.of()))));
    }

    @Test
    void filtersAndPrefersSelectionModeForPlayerRequests() {
        CraftingCpuCandidate any = candidate("any");
        CraftingCpuCandidate playerOnly = candidate("player", builder()
                .selectionMode(CpuSelectionMode.PLAYER_ONLY));
        CraftingCpuCandidate machineOnly = candidate("machine", builder()
                .selectionMode(CpuSelectionMode.MACHINE_ONLY));

        assertEquals(
                List.of("player", "any"),
                identities(this.selector.select(
                        List.of(any, machineOnly, playerOnly),
                        request(1L, true, true, Map.of()))));
        assertEquals(
                List.of("machine", "any"),
                identities(this.selector.select(
                        List.of(playerOnly, any, machineOnly),
                        request(1L, false, true, Map.of()))));
    }

    @Test
    void ordersEqualHardwareByJobsRecentLoadGroupCursorAndStableIdentity() {
        assertEquals(
                List.of("idle", "loaded"),
                identities(this.selector.select(
                        List.of(
                                candidate("loaded", builder().activeJobs(2)),
                                candidate("idle")),
                        request(1L, false, true, Map.of()))));
        assertEquals(
                List.of("cool", "hot"),
                identities(this.selector.select(
                        List.of(
                                candidate("hot", builder().recentOperationLoad(4L)),
                                candidate("cool")),
                        request(1L, false, true, Map.of()))));

        CraftingCpuCandidate alpha = candidate("alpha");
        CraftingCpuCandidate zeta = candidate("zeta");
        CraftingCpuSelectionGroup group = this.selector.group(alpha, false);
        assertEquals(
                List.of("zeta", "alpha"),
                identities(this.selector.select(
                        List.of(alpha, zeta),
                        request(1L, false, true, Map.of(group, "zeta")))));
        assertEquals(
                List.of("alpha", "zeta"),
                identities(this.selector.select(
                        List.of(zeta, alpha),
                        request(1L, false, true, Map.of()))));
    }

    @Test
    void independentHardwareGroupsUseIndependentRoundRobinStarts() {
        CraftingCpuCandidate groupAFirst = candidate("a-first");
        CraftingCpuCandidate groupASecond = candidate("a-second");
        CraftingCpuCandidate groupBFirst = candidate("b-first", builder().coProcessors(4));
        CraftingCpuCandidate groupBSecond = candidate("b-second", builder().coProcessors(4));
        Map<CraftingCpuSelectionGroup, String> starts = Map.of(
                this.selector.group(groupAFirst, false),
                "a-second",
                this.selector.group(groupBFirst, false),
                "b-first");

        List<CraftingCpuCandidate> selected = this.selector.select(
                List.of(groupBSecond, groupAFirst, groupBFirst, groupASecond),
                request(1L, false, false, starts));

        assertEquals(List.of("a-second", "a-first", "b-first", "b-second"), identities(selected));
    }

    @Test
    void playerAndMachineModesKeepIndependentRoundRobinStarts() {
        CraftingCpuCandidate firstPlayer = candidate("player-first", builder()
                .selectionMode(CpuSelectionMode.PLAYER_ONLY));
        CraftingCpuCandidate secondPlayer = candidate("player-second", builder()
                .selectionMode(CpuSelectionMode.PLAYER_ONLY));
        CraftingCpuCandidate firstMachine = candidate("machine-first", builder()
                .selectionMode(CpuSelectionMode.MACHINE_ONLY));
        CraftingCpuCandidate secondMachine = candidate("machine-second", builder()
                .selectionMode(CpuSelectionMode.MACHINE_ONLY));
        Map<CraftingCpuSelectionGroup, String> starts = Map.of(
                this.selector.group(firstPlayer, true),
                "player-second",
                this.selector.group(firstMachine, false),
                "machine-second");

        assertEquals(
                List.of("player-second", "player-first"),
                identities(this.selector.select(
                        List.of(firstPlayer, secondPlayer),
                        request(1L, true, true, starts))));
        assertEquals(
                List.of("machine-second", "machine-first"),
                identities(this.selector.select(
                        List.of(firstMachine, secondMachine),
                        request(1L, false, true, starts))));
    }

    @Test
    void retriesOnlySubmissionTimeAvailabilityFailures() {
        assertTrue(this.selector.isRetryable(CraftingSubmitResult.CPU_BUSY));
        assertTrue(this.selector.isRetryable(CraftingSubmitResult.CPU_OFFLINE));
        assertTrue(this.selector.isRetryable(CraftingSubmitResult.CPU_TOO_SMALL));
        assertFalse(this.selector.isRetryable(
                CraftingSubmitResult.simpleError(CraftingSubmitErrorCode.MISSING_INGREDIENT)));
        assertFalse(this.selector.isRetryable(CraftingSubmitResult.NO_CPU_FOUND));
        assertFalse(this.selector.isRetryable(
                CraftingSubmitResult.noSuitableCpu(new UnsuitableCpus(1, 1, 1, 1))));
        assertFalse(this.selector.isRetryable(CraftingSubmitResult.INCOMPLETE_PLAN));
        assertFalse(this.selector.isRetryable(CraftingSubmitResult.successful(null)));
    }

    @Test
    void rejectsIncompleteInvalidOrDuplicateCandidateFacts() {
        assertThrows(IllegalStateException.class, () -> CraftingCpuCandidate.builder().build());
        assertThrows(
                IllegalArgumentException.class,
                () -> builder().stableIdentity("negative-load").recentOperationLoad(-1L).build());
        assertThrows(
                IllegalArgumentException.class,
                () -> this.selector.select(
                        List.of(candidate("duplicate"), candidate("duplicate")),
                        request(1L, false, true, Map.of())));
        assertThrows(
                IllegalArgumentException.class,
                () -> request(-1L, false, true, Map.of()));
        assertThrows(
                IllegalArgumentException.class,
                () -> new CraftingCpuSelectionGroup(null, false, 0, 0L));
    }

    private static List<String> identities(List<CraftingCpuCandidate> candidates) {
        return candidates.stream().map(CraftingCpuCandidate::stableIdentity).toList();
    }

    private static CraftingCpuCandidate candidate(String identity) {
        return candidate(identity, builder());
    }

    private static CraftingCpuCandidate candidate(String identity, CraftingCpuCandidate.Builder builder) {
        return builder.stableIdentity(identity).build();
    }

    private static CraftingCpuCandidate.Builder builder() {
        return CraftingCpuCandidate.builder()
                .kind(CraftingCpuKind.TRINITY)
                .selectionMode(CpuSelectionMode.ANY)
                .online(true)
                .acceptsJob(true)
                .shared(false)
                .storageBytes(64L)
                .coProcessors(2)
                .activeJobs(0)
                .recentOperationLoad(0L);
    }

    private static CraftingCpuSelectionRequest request(
                                                       long requiredBytes,
                                                       boolean playerRequest,
                                                       boolean prioritizePower,
                                                       Map<CraftingCpuSelectionGroup, String> starts) {
        return new CraftingCpuSelectionRequest(requiredBytes, playerRequest, prioritizePower, starts);
    }
}

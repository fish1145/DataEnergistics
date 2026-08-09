package com.fish_dan_.data_energistics.common.crafting.trinity.dispatch.selection;

import appeng.api.config.CpuSelectionMode;
import appeng.api.networking.crafting.CraftingSubmitErrorCode;
import appeng.api.networking.crafting.ICraftingSubmitResult;
import appeng.api.networking.crafting.UnsuitableCpus;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Filters and deterministically orders immutable crafting CPU facts before server-thread submission.
 * <p>
 * Default synchronous candidate policy matching AE2 source and hardware preferences with Trinity load fairness.
 */
public final class CraftingCpuCandidateSelector {

    /**
     * Creates the synchronous Phase 1 candidate policy.
     *
     * @return independent stateless selector
     */
    public static CraftingCpuCandidateSelector create() {
        return new CraftingCpuCandidateSelector();
    }

    /**
     * Filters unavailable candidates and applies source, hardware, load, group cursor and stable-identity ordering.
     *
     * @param candidates complete explicitly supported candidate facts
     * @param request    immutable selection request
     * @return immutable ordered eligible candidates
     */
    public List<CraftingCpuCandidate> select(
                                             List<CraftingCpuCandidate> candidates,
                                             CraftingCpuSelectionRequest request) {
        return evaluate(candidates, request).candidates();
    }

    /**
     * Evaluates unavailable candidates and applies source, hardware, load, group cursor and stable-identity ordering.
     *
     * @param candidates complete explicitly supported candidate facts
     * @param request    immutable selection request
     * @return immutable ordered candidates and aggregate pre-submission diagnostics
     */
    public CraftingCpuCandidateSelection evaluate(
                                                  List<CraftingCpuCandidate> candidates,
                                                  CraftingCpuSelectionRequest request) {
        Set<String> identities = new HashSet<>();
        ArrayList<CraftingCpuCandidate> eligible = new ArrayList<>(candidates.size());
        int offline = 0;
        int busy = 0;
        int tooSmall = 0;
        int excluded = 0;
        for (CraftingCpuCandidate candidate : candidates) {
            if (!identities.add(candidate.stableIdentity())) {
                throw new IllegalArgumentException(
                        "Duplicate crafting CPU stable identity: " + candidate.stableIdentity());
            }
            if (!candidate.online()) {
                offline = Math.addExact(offline, 1);
                continue;
            }
            if (!candidate.acceptsJob()) {
                busy = Math.addExact(busy, 1);
                continue;
            }
            if (candidate.storageBytes() < request.requiredBytes()) {
                tooSmall = Math.addExact(tooSmall, 1);
                continue;
            }
            if (candidate.shared() || !allowsSource(candidate.selectionMode(), request.playerRequest())) {
                excluded = Math.addExact(excluded, 1);
                continue;
            }
            eligible.add(candidate);
        }
        Map<String, Integer> roundRobinRanks = roundRobinRanks(eligible, request);
        eligible.sort(order(request, roundRobinRanks));
        return new CraftingCpuCandidateSelection(
                eligible,
                new UnsuitableCpus(offline, busy, tooSmall, excluded));
    }

    /**
     * Resolves the hardware-equivalent cursor group for one candidate and request source.
     *
     * @param candidate     immutable candidate facts
     * @param playerRequest whether the action source contains a player
     * @return independent successful-submission cursor group
     */
    public CraftingCpuSelectionGroup group(CraftingCpuCandidate candidate, boolean playerRequest) {
        return new CraftingCpuSelectionGroup(
                candidate.selectionMode(),
                preferredFor(candidate.selectionMode(), playerRequest),
                candidate.coProcessors(),
                candidate.storageBytes());
    }

    /**
     * Returns whether a submission-time availability failure can safely continue without repeating ingredient
     * extraction.
     *
     * @param result failed submit result
     * @return whether the next ordered candidate may be attempted
     */
    public boolean isRetryable(ICraftingSubmitResult result) {
        CraftingSubmitErrorCode errorCode = result.errorCode();
        return errorCode == CraftingSubmitErrorCode.CPU_BUSY ||
                errorCode == CraftingSubmitErrorCode.CPU_OFFLINE ||
                errorCode == CraftingSubmitErrorCode.CPU_TOO_SMALL;
    }

    private static boolean allowsSource(CpuSelectionMode selectionMode, boolean playerRequest) {
        return switch (selectionMode) {
            case ANY -> true;
            case PLAYER_ONLY -> playerRequest;
            case MACHINE_ONLY -> !playerRequest;
        };
    }

    private static boolean preferredFor(CpuSelectionMode selectionMode, boolean playerRequest) {
        return switch (selectionMode) {
            case ANY -> false;
            case PLAYER_ONLY -> playerRequest;
            case MACHINE_ONLY -> !playerRequest;
        };
    }

    private Map<String, Integer> roundRobinRanks(
                                                 List<CraftingCpuCandidate> candidates,
                                                 CraftingCpuSelectionRequest request) {
        Map<CraftingCpuSelectionGroup, List<CraftingCpuCandidate>> groups = new HashMap<>();
        for (CraftingCpuCandidate candidate : candidates) {
            groups.computeIfAbsent(group(candidate, request.playerRequest()), ignored -> new ArrayList<>())
                    .add(candidate);
        }

        Map<String, Integer> ranks = new HashMap<>();
        for (Map.Entry<CraftingCpuSelectionGroup, List<CraftingCpuCandidate>> entry : groups.entrySet()) {
            List<CraftingCpuCandidate> groupCandidates = entry.getValue();
            groupCandidates.sort(Comparator.comparing(CraftingCpuCandidate::stableIdentity));
            String startIdentity = request.roundRobinStarts().get(entry.getKey());
            int start = indexOfIdentity(groupCandidates, startIdentity);
            for (int offset = 0; offset < groupCandidates.size(); offset++) {
                CraftingCpuCandidate candidate = groupCandidates.get((start + offset) % groupCandidates.size());
                ranks.put(candidate.stableIdentity(), offset);
            }
        }
        return Map.copyOf(ranks);
    }

    private static int indexOfIdentity(List<CraftingCpuCandidate> candidates, String identity) {
        if (identity == null) {
            return 0;
        }
        for (int index = 0; index < candidates.size(); index++) {
            if (candidates.get(index).stableIdentity().equals(identity)) {
                return index;
            }
        }
        return 0;
    }

    private static Comparator<CraftingCpuCandidate> order(
                                                          CraftingCpuSelectionRequest request,
                                                          Map<String, Integer> roundRobinRanks) {
        return (first, second) -> {
            int result = Boolean.compare(
                    preferredFor(second.selectionMode(), request.playerRequest()),
                    preferredFor(first.selectionMode(), request.playerRequest()));
            if (result != 0) {
                return result;
            }
            result = request.prioritizePower() ?
                    Integer.compare(second.coProcessors(), first.coProcessors()) :
                    Integer.compare(first.coProcessors(), second.coProcessors());
            if (result != 0) {
                return result;
            }
            result = Long.compare(first.storageBytes(), second.storageBytes());
            if (result != 0) {
                return result;
            }
            result = Integer.compare(first.activeJobs(), second.activeJobs());
            if (result != 0) {
                return result;
            }
            result = Long.compare(first.recentOperationLoad(), second.recentOperationLoad());
            if (result != 0) {
                return result;
            }
            result = Integer.compare(
                    roundRobinRanks.get(first.stableIdentity()),
                    roundRobinRanks.get(second.stableIdentity()));
            if (result != 0) {
                return result;
            }
            return first.stableIdentity().compareTo(second.stableIdentity());
        };
    }
}

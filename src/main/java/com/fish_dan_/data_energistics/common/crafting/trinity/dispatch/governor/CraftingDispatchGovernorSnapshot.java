package com.fish_dan_.data_energistics.common.crafting.trinity.dispatch.governor;

/**
 * Read-only Governor state used by diagnostics and deterministic tests.
 *
 * @param state                  current transient state
 * @param budget                 currently published immutable budget
 * @param observedTicks          total accepted metric samples
 * @param completedWindows       total complete metric windows
 * @param tickEwmaNanos          complete server tick EWMA
 * @param lastQueueRatio         average queue utilization in the last complete window
 * @param lastStaleRatio         aggregate stale ratio in the last complete window
 * @param lastAcceptanceRatio    aggregate provider acceptance ratio in the last complete window
 * @param lastBusiestWorkerShare largest observed worker share in the last complete window
 * @param lastProposalFailures   isolated proposal calculation failures in the last complete window
 */
public record CraftingDispatchGovernorSnapshot(
                                               CraftingDispatchGovernorState state,
                                               CraftingDispatchBudget budget,
                                               long observedTicks,
                                               long completedWindows,
                                               double tickEwmaNanos,
                                               double lastQueueRatio,
                                               double lastStaleRatio,
                                               double lastAcceptanceRatio,
                                               double lastBusiestWorkerShare,
                                               int lastProposalFailures) {}

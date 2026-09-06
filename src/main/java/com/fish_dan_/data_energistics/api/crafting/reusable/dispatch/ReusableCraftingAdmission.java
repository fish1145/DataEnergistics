package com.fish_dan_.data_energistics.api.crafting.reusable.dispatch;

import com.fish_dan_.data_energistics.api.crafting.reusable.dispatch.ReusableCraftingRequest.SlotStack;

import appeng.api.stacks.KeyCounter;

import java.util.List;

/**
 * One-shot server-thread admission for an exact open or append sequence. Unlike legacy counted admissions,
 * physicalInputs is the complete transfer, not a per-craft prototype. Preparing or inspecting it cannot reserve assets.
 */
public interface ReusableCraftingAdmission {

    /** @return positive accepted logical count, never greater than the request */
    long count();

    /**
     * @return immutable total CPU-owned inputs to transfer for this sequence; replay admissions return an empty list
     */
    List<SlotStack> physicalInputs();

    /** @return true only when this exact sequence has already been accepted, without requiring another transfer */
    boolean replay();

    /** @return monotonic ownership boundary, set before the first irreversible external mutation */
    boolean hasTransferredInputOwnership();

    /**
     * Commits once using exact, already-extracted counters in original slot order. Before ownership transfer,
     * rejection must leave all counters unchanged. After ownership transfer, exceptions never authorize CPU refunds.
     * On acceptance the implementation consumes all transferred counters and persists the accepted sequence.
     *
     * @param delivery total physical quantities, not multiplied prototypes
     * @return whether the complete prepared sequence is accepted
     */
    boolean commit(KeyCounter[] delivery);
}

package com.fish_dan_.data_energistics.common.trinity;

import com.fish_dan_.data_energistics.Data_Energistics;

import appeng.api.config.Actionable;
import appeng.api.stacks.AEItemKey;

/** Default two-phase, ordered implementation of {@link TrinityPatternOutputRouter}. */
public final class TrinityPatternOutputRouterImpl implements TrinityPatternOutputRouter {

    @Override
    public boolean route(PendingOutputCursor pending,
                         RequestedAmount requestedAmount,
                         OutputSink cpuSink,
                         OutputSink storageSink) {
        boolean changed = false;
        while (pending.advance()) {
            TrinityItemAmount output = pending.current();
            AEItemKey key = output.key();
            long amount = output.amount();
            long requestedBefore = checkedRequestedAmount(requestedAmount.get(key));
            long cpuOffer = Math.min(amount, requestedBefore);
            long insertedIntoCpu = insertTwoPhase(cpuSink, key, cpuOffer, "crafting CPU");
            long requestedRemainder = cpuOffer - insertedIntoCpu;
            if (insertedIntoCpu > 0L) {
                pending.consumeCurrent(insertedIntoCpu);
                changed = true;
            }

            long storageOffer = amount - cpuOffer;
            long insertedIntoStorage = insertTwoPhase(storageSink, key, storageOffer, "main storage");
            if (insertedIntoStorage > 0L) {
                pending.consumeCurrent(insertedIntoStorage);
                changed = true;
            }
            if (requestedRemainder > 0L) {
                return changed;
            }
        }
        return changed;
    }

    private static long checkedRequestedAmount(long requested) {
        if (requested < 0L) {
            throw new IllegalStateException("Crafting CPU request amount must not be negative: " + requested);
        }
        return requested;
    }

    private static long insertTwoPhase(OutputSink sink, AEItemKey key, long amount, String destination) {
        if (amount <= 0L) {
            return 0L;
        }
        long simulated = checkedInsertion(sink.insert(key, amount, Actionable.SIMULATE), amount, destination, "simulate");
        if (simulated <= 0L) {
            return 0L;
        }
        long inserted = checkedInsertion(sink.insert(key, simulated, Actionable.MODULATE), simulated, destination, "modulate");
        if (inserted != simulated) {
            Data_Energistics.LOGGER.warn(
                    "Trinity output {} accepted {} during simulation but {} during modulation for {}",
                    destination,
                    simulated,
                    inserted,
                    key);
        }
        return inserted;
    }

    private static long checkedInsertion(long inserted, long offered, String destination, String phase) {
        if (inserted < 0L || inserted > offered) {
            throw new IllegalStateException(
                    "Trinity output " + destination + " returned invalid " + phase + " insertion " + inserted +
                            " for offer " + offered);
        }
        return inserted;
    }
}

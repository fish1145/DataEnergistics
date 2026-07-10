package com.fish_dan_.data_energistics.common.trinity;

import com.fish_dan_.data_energistics.Data_Energistics;

import net.minecraft.world.item.ItemStack;

import appeng.api.config.Actionable;
import appeng.api.stacks.AEItemKey;

import java.util.ArrayList;
import java.util.List;

/** Default two-phase, ordered implementation of {@link TrinityPatternOutputRouter}. */
public final class TrinityPatternOutputRouterImpl implements TrinityPatternOutputRouter {

    @Override
    public RoutingResult route(List<ItemStack> pending,
                               RequestedAmount requestedAmount,
                               OutputSink cpuSink,
                               OutputSink storageSink,
                               OutputCheckpoint checkpoint) {
        List<ItemStack> snapshot = pending.stream().map(ItemStack::copy).toList();
        ArrayList<ItemStack> retainedPrefix = new ArrayList<>();
        long totalInserted = 0L;
        for (int index = 0; index < snapshot.size(); index++) {
            ItemStack output = snapshot.get(index);
            if (output.isEmpty()) {
                checkpoint.replace(checkpointState(retainedPrefix, snapshot, index + 1));
                continue;
            }
            AEItemKey key = AEItemKey.of(output);

            long amount = output.getCount();
            long requestedBefore = checkedRequestedAmount(requestedAmount.get(key));
            long cpuOffer = Math.min(amount, requestedBefore);
            long insertedIntoCpu = insertTwoPhase(cpuSink, key, cpuOffer, "crafting CPU");
            long requestedRemainder = cpuOffer - insertedIntoCpu;
            if (insertedIntoCpu > 0L) {
                checkpoint.replace(checkpointStateWithCurrent(
                        retainedPrefix,
                        output,
                        amount - insertedIntoCpu,
                        snapshot,
                        index + 1));
            }

            long storageOffer = amount - cpuOffer;
            long insertedIntoStorage = insertTwoPhase(storageSink, key, storageOffer, "main storage");
            long storageRemainder = storageOffer - insertedIntoStorage;
            long retained = requestedRemainder + storageRemainder;
            long inserted = insertedIntoCpu + insertedIntoStorage;
            totalInserted += inserted;
            if (retained > 0L) {
                ItemStack retainedStack = output.copy();
                retainedStack.setCount(Math.toIntExact(retained));
                retainedPrefix.add(retainedStack);
            }
            if (insertedIntoStorage > 0L) {
                checkpoint.replace(checkpointState(retainedPrefix, snapshot, index + 1));
            }
            if (requestedRemainder > 0L) {
                List<ItemStack> blockedState = checkpointState(retainedPrefix, snapshot, index + 1);
                if (insertedIntoCpu == 0L && insertedIntoStorage == 0L) {
                    checkpoint.replace(blockedState);
                }
                return new RoutingResult(blockedState, totalInserted);
            }
        }
        return new RoutingResult(retainedPrefix, totalInserted);
    }

    private static List<ItemStack> checkpointStateWithCurrent(List<ItemStack> retainedPrefix,
                                                              ItemStack current,
                                                              long retainedCurrentAmount,
                                                              List<ItemStack> pending,
                                                              int unprocessedStart) {
        ArrayList<ItemStack> currentPrefix = new ArrayList<>(retainedPrefix.size() + 1);
        for (ItemStack retained : retainedPrefix) {
            currentPrefix.add(retained.copy());
        }
        if (retainedCurrentAmount > 0L) {
            ItemStack retainedCurrent = current.copy();
            retainedCurrent.setCount(Math.toIntExact(retainedCurrentAmount));
            currentPrefix.add(retainedCurrent);
        }
        return checkpointState(currentPrefix, pending, unprocessedStart);
    }

    private static List<ItemStack> checkpointState(List<ItemStack> retainedPrefix,
                                                   List<ItemStack> pending,
                                                   int unprocessedStart) {
        ArrayList<ItemStack> state = new ArrayList<>(retainedPrefix.size() + pending.size() - unprocessedStart);
        for (ItemStack retained : retainedPrefix) {
            state.add(retained.copy());
        }
        for (int index = unprocessedStart; index < pending.size(); index++) {
            ItemStack unprocessed = pending.get(index);
            if (!unprocessed.isEmpty()) {
                state.add(unprocessed.copy());
            }
        }
        return List.copyOf(state);
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

package com.fish_dan_.data_energistics.common.crafting.trinity.execution.cpu;

import appeng.api.stacks.AEKey;

import it.unimi.dsi.fastutil.ints.Int2ObjectAVLTreeMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.ints.IntArrayList;
import it.unimi.dsi.fastutil.ints.IntLists;
import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.objects.ObjectOpenHashSet;

import java.math.BigInteger;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Indexes the output amounts awaited by Trinity CPU workers so network return paths do not scan every worker.
 *
 * <p>
 * The index owns only rebuildable runtime state. CPU job NBT remains the authoritative persisted source.
 * </p>
 *
 */
final class TrinityCpuWaitingIndex {

    /**
     * Public AE2 APIs expose requested amounts as non-negative longs.
     */
    private static final BigInteger MAX_PUBLIC_AMOUNT = BigInteger.valueOf(Long.MAX_VALUE);

    /**
     * Per-key entries retain exact totals and workers in routing order.
     */
    private final Map<AEKey, WaitingEntry> entries = new Object2ObjectOpenHashMap<>();

    /**
     * Reverse membership makes worker removal proportional to that worker's requested key count.
     */
    private final Int2ObjectOpenHashMap<Set<AEKey>> keysByWorker = new Int2ObjectOpenHashMap<>();

    /**
     * Replaces one worker's requested amount for a key.
     *
     * @param workerNumber    stable positive worker number
     * @param what            requested AE2 key
     * @param requestedAmount current non-negative amount requested by the worker
     */
    public void update(int workerNumber, AEKey what, BigInteger requestedAmount) {
        if (workerNumber <= 0) {
            throw new IllegalArgumentException("Trinity waiting index worker number must be positive");
        }
        if (requestedAmount.signum() < 0) {
            throw new IllegalArgumentException("Trinity waiting index amount must not be negative");
        }

        WaitingEntry entry = this.entries.get(what);
        if (requestedAmount.signum() == 0) {
            if (entry != null) {
                removeRequest(workerNumber, what, entry);
            }
            return;
        }

        if (entry == null) {
            entry = new WaitingEntry();
            this.entries.put(what, entry);
        }
        BigInteger previousAmount = entry.amountsByWorker.put(workerNumber, requestedAmount);
        if (previousAmount != null) {
            entry.exactTotal = entry.exactTotal.subtract(previousAmount);
        } else {
            entry.rebuildWorkerNumbers();
        }
        entry.exactTotal = entry.exactTotal.add(requestedAmount);
        this.keysByWorker.computeIfAbsent(workerNumber, ignored -> new ObjectOpenHashSet<>()).add(what);
    }

    /**
     * Removes every request owned by a worker when that runtime worker is released or rebuilt.
     *
     * @param workerNumber stable positive worker number
     */
    public void removeWorker(int workerNumber) {
        Set<AEKey> workerKeys = this.keysByWorker.remove(workerNumber);
        if (workerKeys == null) {
            return;
        }
        for (AEKey what : workerKeys) {
            WaitingEntry entry = this.entries.get(what);
            BigInteger removedAmount = entry.amountsByWorker.remove(workerNumber);
            entry.exactTotal = entry.exactTotal.subtract(removedAmount);
            if (entry.amountsByWorker.isEmpty()) {
                this.entries.remove(what);
            } else {
                entry.rebuildWorkerNumbers();
            }
        }
    }

    /**
     * Clears all rebuildable request state before a runtime load or complete rebuild.
     */
    public void clear() {
        this.entries.clear();
        this.keysByWorker.clear();
    }

    /**
     * Returns the aggregate requested amount, saturated only at the public long boundary.
     *
     * @param what requested AE2 key
     * @return exact aggregate when representable, otherwise {@link Long#MAX_VALUE}
     */
    public long requestedAmount(AEKey what) {
        WaitingEntry entry = this.entries.get(what);
        if (entry == null) {
            return 0L;
        }
        return entry.exactTotal.compareTo(MAX_PUBLIC_AMOUNT) >= 0 ? Long.MAX_VALUE : entry.exactTotal.longValueExact();
    }

    /**
     * Adds the indexed key set to AE2's request watcher destination.
     *
     * @param destination mutable destination set
     */
    public void addWaitingKeys(Set<AEKey> destination) {
        destination.addAll(this.entries.keySet());
    }

    /**
     * Returns a stable snapshot of workers waiting for one key in ascending worker-number order.
     *
     * @param what requested AE2 key
     * @return immutable ordered worker-number snapshot
     */
    public List<Integer> waitingWorkerNumbers(AEKey what) {
        WaitingEntry entry = this.entries.get(what);
        return entry == null ? List.of() : entry.workerNumbers;
    }

    /**
     * Removes one key membership while keeping both forward and reverse indexes consistent.
     */
    private void removeRequest(int workerNumber, AEKey what, WaitingEntry entry) {
        BigInteger removedAmount = entry.amountsByWorker.remove(workerNumber);
        if (removedAmount == null) {
            return;
        }
        entry.exactTotal = entry.exactTotal.subtract(removedAmount);
        Set<AEKey> workerKeys = this.keysByWorker.get(workerNumber);
        workerKeys.remove(what);
        if (workerKeys.isEmpty()) {
            this.keysByWorker.remove(workerNumber);
        }
        if (entry.amountsByWorker.isEmpty()) {
            this.entries.remove(what);
        } else {
            entry.rebuildWorkerNumbers();
        }
    }

    /**
     * Mutable aggregate isolated behind immutable index query results.
     */
    private static final class WaitingEntry {

        /**
         * Worker amounts are ordered so returned outputs always visit the lowest CPU number first.
         */
        private final Int2ObjectAVLTreeMap<BigInteger> amountsByWorker = new Int2ObjectAVLTreeMap<>();

        /**
         * Exact aggregate prevents saturation from losing information needed by later removals.
         */
        private BigInteger exactTotal = BigInteger.ZERO;

        /**
         * Immutable routing snapshot is replaced only when worker membership changes.
         */
        private List<Integer> workerNumbers = List.of();

        /**
         * Rebuilds ordered routing after a worker enters or leaves this key.
         */
        private void rebuildWorkerNumbers() {
            this.workerNumbers = IntLists.unmodifiable(new IntArrayList(this.amountsByWorker.keySet()));
        }
    }
}

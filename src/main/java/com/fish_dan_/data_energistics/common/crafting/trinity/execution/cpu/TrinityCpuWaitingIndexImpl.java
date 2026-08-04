package com.fish_dan_.data_energistics.common.crafting.trinity.execution.cpu;

import appeng.api.stacks.AEKey;

import java.math.BigInteger;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.NavigableMap;
import java.util.Set;
import java.util.TreeMap;

/** Exact in-memory {@link TrinityCpuWaitingIndex} implementation with ordered worker routing. */
final class TrinityCpuWaitingIndexImpl implements TrinityCpuWaitingIndex {

    /** Public AE2 APIs expose requested amounts as non-negative longs. */
    private static final BigInteger MAX_PUBLIC_AMOUNT = BigInteger.valueOf(Long.MAX_VALUE);

    /** Per-key entries retain exact totals and workers in routing order. */
    private final Map<AEKey, WaitingEntry> entries = new HashMap<>();

    /** Reverse membership makes worker removal proportional to that worker's requested key count. */
    private final Map<Integer, Set<AEKey>> keysByWorker = new HashMap<>();

    @Override
    public void update(int workerNumber, AEKey what, long requestedAmount) {
        if (workerNumber <= 0) {
            throw new IllegalArgumentException("Trinity waiting index worker number must be positive");
        }
        if (requestedAmount < 0L) {
            throw new IllegalArgumentException("Trinity waiting index amount must not be negative");
        }

        WaitingEntry entry = this.entries.get(what);
        if (requestedAmount == 0L) {
            if (entry != null) {
                removeRequest(workerNumber, what, entry);
            }
            return;
        }

        if (entry == null) {
            entry = new WaitingEntry();
            this.entries.put(what, entry);
        }
        Long previousAmount = entry.amountsByWorker.put(workerNumber, requestedAmount);
        if (previousAmount != null) {
            entry.exactTotal = entry.exactTotal.subtract(BigInteger.valueOf(previousAmount));
        } else {
            entry.rebuildWorkerNumbers();
        }
        entry.exactTotal = entry.exactTotal.add(BigInteger.valueOf(requestedAmount));
        this.keysByWorker.computeIfAbsent(workerNumber, ignored -> new HashSet<>()).add(what);
    }

    @Override
    public void removeWorker(int workerNumber) {
        Set<AEKey> workerKeys = this.keysByWorker.remove(workerNumber);
        if (workerKeys == null) {
            return;
        }
        for (AEKey what : workerKeys) {
            WaitingEntry entry = this.entries.get(what);
            long removedAmount = entry.amountsByWorker.remove(workerNumber);
            entry.exactTotal = entry.exactTotal.subtract(BigInteger.valueOf(removedAmount));
            if (entry.amountsByWorker.isEmpty()) {
                this.entries.remove(what);
            } else {
                entry.rebuildWorkerNumbers();
            }
        }
    }

    @Override
    public void clear() {
        this.entries.clear();
        this.keysByWorker.clear();
    }

    @Override
    public long requestedAmount(AEKey what) {
        WaitingEntry entry = this.entries.get(what);
        if (entry == null) {
            return 0L;
        }
        return entry.exactTotal.compareTo(MAX_PUBLIC_AMOUNT) >= 0 ? Long.MAX_VALUE : entry.exactTotal.longValueExact();
    }

    @Override
    public void addWaitingKeys(Set<AEKey> destination) {
        destination.addAll(this.entries.keySet());
    }

    @Override
    public List<Integer> waitingWorkerNumbers(AEKey what) {
        WaitingEntry entry = this.entries.get(what);
        return entry == null ? List.of() : entry.workerNumbers;
    }

    /** Removes one key membership while keeping both forward and reverse indexes consistent. */
    private void removeRequest(int workerNumber, AEKey what, WaitingEntry entry) {
        Long removedAmount = entry.amountsByWorker.remove(workerNumber);
        if (removedAmount == null) {
            return;
        }
        entry.exactTotal = entry.exactTotal.subtract(BigInteger.valueOf(removedAmount));
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

    /** Mutable aggregate isolated behind immutable index query results. */
    private static final class WaitingEntry {

        /** Worker amounts are ordered so returned outputs always visit the lowest CPU number first. */
        private final NavigableMap<Integer, Long> amountsByWorker = new TreeMap<>();

        /** Exact aggregate prevents saturation from losing information needed by later removals. */
        private BigInteger exactTotal = BigInteger.ZERO;

        /** Immutable routing snapshot is replaced only when worker membership changes. */
        private List<Integer> workerNumbers = List.of();

        /** Rebuilds ordered routing after a worker enters or leaves this key. */
        private void rebuildWorkerNumbers() {
            this.workerNumbers = List.copyOf(this.amountsByWorker.navigableKeySet());
        }
    }
}

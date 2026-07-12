package com.fish_dan_.data_energistics.common.crafting.trinity;

import appeng.api.stacks.AEKey;

import java.util.List;
import java.util.Set;

/**
 * Indexes the output amounts awaited by Trinity CPU workers so network return paths do not scan every worker.
 *
 * <p>The index owns only rebuildable runtime state. CPU job NBT remains the authoritative persisted source.</p>
 */
interface TrinityCpuWaitingIndex {

    /**
     * Replaces one worker's requested amount for a key.
     *
     * @param workerNumber stable positive worker number
     * @param what requested AE2 key
     * @param requestedAmount current non-negative amount requested by the worker
     */
    void update(int workerNumber, AEKey what, long requestedAmount);

    /**
     * Removes every request owned by a worker when that runtime worker is released or rebuilt.
     *
     * @param workerNumber stable positive worker number
     */
    void removeWorker(int workerNumber);

    /** Clears all rebuildable request state before a runtime load or complete rebuild. */
    void clear();

    /**
     * Returns the aggregate requested amount, saturated only at the public long boundary.
     *
     * @param what requested AE2 key
     * @return exact aggregate when representable, otherwise {@link Long#MAX_VALUE}
     */
    long requestedAmount(AEKey what);

    /**
     * Adds the indexed key set to AE2's request watcher destination.
     *
     * @param destination mutable destination set
     */
    void addWaitingKeys(Set<AEKey> destination);

    /**
     * Returns a stable snapshot of workers waiting for one key in ascending worker-number order.
     *
     * @param what requested AE2 key
     * @return immutable ordered worker-number snapshot
     */
    List<Integer> waitingWorkerNumbers(AEKey what);
}

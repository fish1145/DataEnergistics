package com.fish_dan_.data_energistics.common.crafting.trinity.planning.algorithm.cycle.joint.search.cut;

import com.fish_dan_.data_energistics.common.crafting.trinity.planning.algorithm.cycle.joint.search.TrinityFiringBox;

import appeng.api.stacks.AEKey;

import java.math.BigInteger;
import java.util.Optional;
import java.util.Set;

/**
 * Derives sound box cuts from optimistic external-key reachability without expanding logical firing counts.
 */
public interface TrinityExternalPrefixCut {

    /**
     * @return stateless dependency-closure cut generator
     */
    static TrinityExternalPrefixCut create() {
        return new TrinityExternalPrefixCutImpl();
    }

    /**
     * Every external key is optimistically granted {@code cap} units, internal seed is unlimited, consumption is not
     * deducted, and reachable outputs saturate downstream input thresholds. A transition absent even from this
     * over-approximation is therefore impossible in every real schedule within the cap.
     *
     * @return exact box partition, or empty when the closure proves no transition unreachable
     */
    Optional<TrinityExternalPrefixPartition> partition(
                                                       TrinityFiringBox box,
                                                       Set<AEKey> internalKeys,
                                                       BigInteger cap);
}

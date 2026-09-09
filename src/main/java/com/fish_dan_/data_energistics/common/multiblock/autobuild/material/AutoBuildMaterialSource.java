package com.fish_dan_.data_energistics.common.multiblock.autobuild.material;

import appeng.api.stacks.AEKey;

/**
 * One bound inventory slot or ME storage used by a single server-thread build transaction.
 * Sources separate storage access from reservation accounting. A source must recheck its binding before access;
 * detached sources return no material. All amounts are nonnegative, and mutations return actual transferred counts.
 */
interface AutoBuildMaterialSource {

    /** Simulates extraction up to the requested amount, respecting indivisible containers such as buckets. */
    long available(AEKey key, long amount);

    /** Extracts at most the positive requested amount. Zero means the bound source can no longer supply it. */
    long extract(AEKey key, long amount);

    /** Returns how much exact-key material was reinserted; called only for previously extracted material. */
    long refund(AEKey key, long amount);
}

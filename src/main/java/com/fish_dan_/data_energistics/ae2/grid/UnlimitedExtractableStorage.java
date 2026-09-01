package com.fish_dan_.data_energistics.ae2.grid;

import appeng.api.networking.security.IActionSource;
import appeng.api.stacks.AEKey;

/**
 * Explicit marker for storage whose extraction of supported exact keys is non-consuming.
 *
 * <p>
 * This contract exists because a finite mount may legitimately report {@link Integer#MAX_VALUE} or
 * {@link Long#MAX_VALUE}. Numeric listings therefore cannot identify creative storage. Implementations are queried
 * on the server thread and must honour the supplied AE2 action source without retaining it.
 * </p>
 */
public interface UnlimitedExtractableStorage {

    /** Returns whether this concrete mount can supply any requested long amount of the exact key. */
    boolean supportsUnlimitedExtraction(AEKey key, IActionSource source);
}

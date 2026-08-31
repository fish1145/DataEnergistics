package com.fish_dan_.data_energistics.ae2.grid;

import appeng.api.networking.security.IActionSource;
import appeng.api.stacks.AEKey;

import java.math.BigInteger;

/** Explicit exact-amount capability for finite mounts whose total can exceed one AE2 long operation. */
public interface ExactExtractableStorage {

    /** Returns the finite amount currently extractable for the exact key without retaining the action source. */
    BigInteger exactAvailable(AEKey key, IActionSource source);
}

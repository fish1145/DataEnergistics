package com.fish_dan_.data_energistics.client.emi;

import appeng.api.stacks.AEKey;

/**
 * Isolates the EMI identity of codec-backed AE2 keys from converters that use AEKey subclasses as inputs.
 */
record GenericAeKeyEmiKey(AEKey aeKey) {}

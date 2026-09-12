package com.fish_dan_.data_energistics.orbital.storage;

import java.util.UUID;

/**
 * Server-issued, one-shot ownership-transfer offer. The transfer UUID is the capability presented to the recipient;
 * it is never derived from a client-supplied weapon identity and expires against the server game clock.
 */
public record OrbitalOwnershipTransfer(
                                       UUID transferId,
                                       UUID weaponId,
                                       UUID currentOwnerId,
                                       UUID recipientId,
                                       long expiresAtGameTime) {

    public OrbitalOwnershipTransfer {
        if (currentOwnerId.equals(recipientId)) {
            throw new IllegalArgumentException("An orbital weapon cannot be transferred to its current owner");
        }
        if (expiresAtGameTime <= 0L) {
            throw new IllegalArgumentException("An orbital ownership transfer must have a positive expiry time");
        }
    }

    /** Returns whether the server-side offer is no longer valid at the supplied game time. */
    public boolean expired(long gameTime) {
        return gameTime >= this.expiresAtGameTime;
    }
}

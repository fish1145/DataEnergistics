package com.fish_dan_.data_energistics.common.crafting.trinity.status;

import com.fish_dan_.data_energistics.common.crafting.trinity.planning.plan.projection.TrinityAe2AmountProjection;

import appeng.api.stacks.AEKey;
import appeng.menu.me.crafting.CraftingStatusEntry;

import org.jspecify.annotations.Nullable;

import java.math.BigInteger;

/** Exact status quantities; inherited long values are only AE2's non-authoritative UI compatibility view. */
public final class TrinityCraftingStatusEntry extends CraftingStatusEntry {

    private final BigInteger stored;
    private final BigInteger active;
    private final BigInteger pending;
    private final BigInteger resident;

    /** A null key is allowed only in an incremental update whose serial already belongs to the receiving screen. */
    public TrinityCraftingStatusEntry(long serial, @Nullable AEKey key, BigInteger stored, BigInteger active,
                                      BigInteger pending, BigInteger resident) {
        super(serial, key, TrinityAe2AmountProjection.toAe2Amount(stored),
                TrinityAe2AmountProjection.toAe2Amount(active), TrinityAe2AmountProjection.toAe2Amount(pending));
        if (serial < 0 || resident.signum() < 0) {
            throw new IllegalArgumentException("A crafting status entry requires non-negative serial and resident amount");
        }
        this.stored = stored;
        this.active = active;
        this.pending = pending;
        this.resident = resident;
    }

    public BigInteger stored() {
        return this.stored;
    }

    public BigInteger active() {
        return this.active;
    }

    public BigInteger pending() {
        return this.pending;
    }

    public BigInteger resident() {
        return this.resident;
    }

    @Override
    public boolean isDeleted() {
        return super.isDeleted() && this.resident.signum() == 0;
    }

    @Override
    public int compareTo(CraftingStatusEntry other) {
        BigInteger otherWork = other instanceof TrinityCraftingStatusEntry exact ?
                exact.active.add(exact.pending) : BigInteger.valueOf(other.getActiveAmount()).add(BigInteger.valueOf(other.getPendingAmount()));
        int byWork = otherWork.compareTo(this.active.add(this.pending));
        if (byWork != 0) {
            return byWork;
        }
        BigInteger otherStored = other instanceof TrinityCraftingStatusEntry exact ? exact.stored.add(exact.resident) : BigInteger.valueOf(other.getStoredAmount());
        return otherStored.compareTo(this.stored.add(this.resident));
    }
}

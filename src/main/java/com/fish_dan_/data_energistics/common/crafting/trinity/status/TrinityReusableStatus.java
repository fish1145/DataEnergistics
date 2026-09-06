package com.fish_dan_.data_energistics.common.crafting.trinity.status;

import java.math.BigInteger;
import java.util.Locale;

/** Client-facing observation, never an inventory credit or permission to recover unknown assets. */
public record TrinityReusableStatus(Phase phase, int sessions, BigInteger heldTools, BigInteger spareTools, String diagnostic) {

    public static final TrinityReusableStatus EMPTY = new TrinityReusableStatus(Phase.NONE, 0, BigInteger.ZERO, BigInteger.ZERO, "");
    public static final int MAX_DIAGNOSTIC_LENGTH = 1024;

    public TrinityReusableStatus {
        if (sessions < 0 || heldTools.signum() < 0 || spareTools.signum() < 0 || spareTools.compareTo(heldTools) > 0) {
            throw new IllegalArgumentException("Invalid reusable crafting status quantities");
        }
        if (diagnostic.length() > MAX_DIAGNOSTIC_LENGTH) {
            diagnostic = diagnostic.substring(0, MAX_DIAGNOSTIC_LENGTH - 1) + "…";
        }
    }

    /** Higher ordinal wins when multiple resident sessions are in different observable states. */
    public enum Phase {

        NONE,
        RUNNING,
        WAITING_INPUT,
        WAITING_RETURN,
        TOOLS_EXHAUSTED,
        UNREACHABLE,
        RECONCILIATION;

        public String translationKey() {
            return "gui.data_energistics.reusable_status." + name().toLowerCase(Locale.ROOT);
        }
    }
}

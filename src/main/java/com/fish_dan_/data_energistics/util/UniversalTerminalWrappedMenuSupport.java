package com.fish_dan_.data_energistics.util;

import com.fish_dan_.data_energistics.part.UniversalTerminalPart;

public final class UniversalTerminalWrappedMenuSupport {

    private UniversalTerminalWrappedMenuSupport() {}

    public static UniversalTerminalPart requireUniversalTerminalHost(Object host) {
        if (host instanceof UniversalTerminalPart part) {
            return part;
        }

        if (host instanceof UniversalTerminalHostAccessor accessor) {
            return accessor.getUniversalTerminalPart();
        }

        throw new IllegalStateException("Wrapped terminal host does not expose UniversalTerminalPart: " + host);
    }
}

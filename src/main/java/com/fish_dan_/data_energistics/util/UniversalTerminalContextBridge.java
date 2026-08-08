package com.fish_dan_.data_energistics.util;

import com.fish_dan_.data_energistics.api.registry.terminal.UniversalTerminalContext;
import com.fish_dan_.data_energistics.part.UniversalTerminalPart;

/**
 * Internal compatibility view that lets the deprecated adapter contract receive the concrete part it historically
 * exposed while the public API sees only {@link UniversalTerminalContext}.
 */
interface UniversalTerminalContextBridge extends UniversalTerminalContext {

    /**
     * @return concrete part required only by the deprecated adapter bridge
     */
    UniversalTerminalPart part();
}

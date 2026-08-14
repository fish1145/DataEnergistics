package com.fish_dan_.data_energistics.gui.ldlib2.host.window;

import com.fish_dan_.data_energistics.gui.ldlib2.host.protocol.HostUiKey;

/**
 * Factory that creates a new, independently owned child UI instance on every successful reopen.
 */
public interface HostSubUiProvider {

    /**
     * Returns the identity used for registration, toggling, and position retention.
     *
     * @return stable provider identity
     */
    HostUiKey key();

    /**
     * Creates a fresh element tree for one opening.
     *
     * @param context host lifecycle and window actions for the new instance
     * @return fresh root and its title drag handle
     */
    HostSubUi create(HostSubUiContext context);
}

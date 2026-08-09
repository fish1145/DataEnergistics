package com.fish_dan_.data_energistics.menu.patternencoding;

/**
 * Exposes the connection-scoped preference protocol state owned by a pattern encoding menu.
 */
public interface PatternEncodingPreferenceMenu {

    /**
     * Returns the state for this exact live menu instance.
     */
    default PatternEncodingPreferenceSession data_energistics$getPreferenceSession() {
        return PatternEncodingPreferenceSession.forMenu(this);
    }
}

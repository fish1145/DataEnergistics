package com.fish_dan_.data_energistics.api.registry.provider;

/**
 * Result of one provider-group menu-open adapter.
 */
public enum PatternProviderMenuOpenResult {

    /** Continue with the next adapter or default resolver. */
    PASS,
    /** The adapter opened a menu successfully. */
    OPENED,
    /** The adapter recognized the group and explicitly denied opening it. */
    DENIED
}

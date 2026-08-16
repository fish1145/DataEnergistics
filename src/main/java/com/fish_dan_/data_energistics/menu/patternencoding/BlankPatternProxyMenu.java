package com.fish_dan_.data_energistics.menu.patternencoding;

/**
 * Exposes the optional network-backed blank-pattern slot used by pattern encoding menus.
 *
 * <p>
 * The enabled state is synchronized by the menu and is therefore server-authoritative. Client screens must query
 * this contract instead of reading their local early configuration.
 */
public interface BlankPatternProxyMenu {

    /** Returns whether this open menu proxies its blank-pattern slot to AE storage. */
    boolean data_energistics$usesNetworkBackedBlankPatternSlot();

    void data_energistics$depositCarriedBlankPatterns(boolean single);

    void data_energistics$pickupBlankPatterns(boolean single);
}

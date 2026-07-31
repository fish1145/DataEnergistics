package com.fish_dan_.data_energistics.client.screen;

/**
 * Describes which localized pattern names participate in access-terminal search text.
 *
 * <p>
 * The mode is independent from screen widgets so callers can cycle and test the same search semantics without
 * loading client rendering classes.
 * </p>
 */
public enum TrinityPatternSearchMode {

    /** Searches only the names of pattern inputs. */
    INPUT,

    /** Searches only the names of pattern outputs. */
    OUTPUT,

    /** Searches input names first and then output names. */
    INPUT_OUTPUT;

    /**
     * Advances to the next user-selectable search scope.
     *
     * @return the following mode, wrapping from {@link #INPUT_OUTPUT} to {@link #INPUT}
     */
    public TrinityPatternSearchMode next() {
        return switch (this) {
            case INPUT -> OUTPUT;
            case OUTPUT -> INPUT_OUTPUT;
            case INPUT_OUTPUT -> INPUT;
        };
    }
}

package com.fish_dan_.data_energistics.client.screen.patternencoding;

/**
 * Identifies whether an amount sub-screen was opened for the first processing output slot.
 */
public interface ProcessingPatternAmountContext {

    /**
     * @return true only for the middle-click that targeted processing output slot zero
     */
    boolean data_energistics$isProcessingOutputAmountTarget();
}

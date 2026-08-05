package com.fish_dan_.data_energistics.integration.extendedaeplus;

/**
 * Optional handoff boundary between Data Energistics' pattern encoder and ExtendedAE-Plus.
 *
 * <p>The interface intentionally contains no ExtendedAE-Plus types. The core encoder can therefore invoke the
 * boundary through an optional capability check while remaining loadable when ExtendedAE-Plus is absent.</p>
 */
public interface EaepPatternEncodingHandoff {

    /**
     * Starts one encode handoff and consumes the one-shot ExtendedAE-Plus Shift marker.
     *
     * @param dataEnergisticsUploadEnabled whether Data Energistics owns the encoded-pattern upload
     */
    void beginEaepEncodeHandoff(boolean dataEnergisticsUploadEnabled);

    /**
     * Completes the handoff for the current encode attempt.
     *
     * @param encodedSuccessfully whether Data Energistics produced and stored an encoded pattern
     */
    void finishEaepEncodeHandoff(boolean encodedSuccessfully);
}

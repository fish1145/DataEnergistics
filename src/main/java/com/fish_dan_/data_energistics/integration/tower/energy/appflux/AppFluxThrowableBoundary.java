package com.fish_dan_.data_energistics.integration.tower.energy.appflux;

import com.fish_dan_.data_energistics.Data_Energistics;
import com.fish_dan_.data_energistics.util.ThrowableIsolation;

/**
 * Isolates recoverable failures thrown across the optional AppFlux integration boundary.
 *
 * <p>
 * This helper deliberately has no AppFlux type references, so its failure policy remains directly testable when the
 * optional mod is absent from the unit-test runtime. Fatal JVM failures retain their original propagation semantics.
 */
final class AppFluxThrowableBoundary {

    private AppFluxThrowableBoundary() {}

    /**
     * Executes an AppFlux extraction and converts a recoverable failure into zero progress.
     *
     * @param operation third-party extraction call
     * @return extracted energy, or zero after a recoverable failure
     */
    static long isolateExtraction(EnergyOperation operation) {
        return isolate(operation, false);
    }

    /**
     * Executes an AppFlux restoration and converts a recoverable failure into zero progress.
     *
     * @param operation third-party restoration call
     * @return restored energy, or zero after a recoverable failure
     */
    static long isolateRestoration(EnergyOperation operation) {
        return isolate(operation, true);
    }

    private static long isolate(EnergyOperation operation, boolean restoration) {
        try {
            return operation.execute();
        } catch (Throwable throwable) {
            ThrowableIsolation.rethrowIfFatal(throwable);
            if (restoration) {
                Data_Energistics.LOGGER.error("Failed to restore AppFlux energy to AE network", throwable);
            } else {
                Data_Energistics.LOGGER.debug("Failed to extract AppFlux energy from AE network", throwable);
            }
            return 0L;
        }
    }

    /** Executes one AppFlux call behind the shared fatal-failure boundary. */
    @FunctionalInterface
    interface EnergyOperation {

        /**
         * Invokes the third-party energy operation.
         *
         * @return transferred energy
         */
        long execute();
    }
}

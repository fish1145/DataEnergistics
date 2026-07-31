package com.fish_dan_.data_energistics.integration.ae2lt;

import java.util.Objects;

/**
 * Loads every AE2LT runtime capability independently so one optional hook cannot disable unrelated integration paths.
 */
final class Ae2LtRuntimeBootstrap {

    /**
     * Runtime capability names used in diagnostics and direct bootstrap tests.
     */
    enum Capability {
        MACHINE,
        ENERGY,
        SMART_DOUBLING,
        ADVANCED_BLOCKING,
        EJECT
    }

    /**
     * Resolves one capability and may report the checked lookup failures produced by the MethodHandle boundary.
     *
     * @param <T> immutable access object produced by the loader
     */
    @FunctionalInterface
    interface Loader<T> {

        /**
         * Resolves one complete capability.
         *
         * @return resolved access object
         * @throws ReflectiveOperationException when a required class, member, or public access path is unavailable
         */
        T load() throws ReflectiveOperationException;
    }

    /**
     * Receives a single diagnostic for a capability that could not be linked.
     */
    @FunctionalInterface
    interface DiagnosticSink {

        /**
         * Reports an expected compatibility-boundary failure.
         *
         * @param capability capability being disabled
         * @param exception  precise lookup or linkage failure
         */
        void unavailable(Capability capability, Throwable exception);
    }

    /**
     * Groups the five independent loaders without exposing AE2LT implementation types to the bootstrap algorithm.
     */
    record Loaders<M, E, S, A, J>(Loader<M> machine,
                                  Loader<E> energy,
                                  Loader<S> smartDoubling,
                                  Loader<A> advancedBlocking,
                                  Loader<J> eject) {}

    /**
     * Immutable publication snapshot. A {@code null} member means only that capability is unavailable.
     */
    record Capabilities<M, E, S, A, J>(M machine,
                                       E energy,
                                       S smartDoubling,
                                       A advancedBlocking,
                                       J eject) {}

    private Ae2LtRuntimeBootstrap() {}

    /**
     * Attempts all known capability loaders in a fixed order and isolates only known lookup/linkage failures.
     *
     * @param loaders     independent capability loaders
     * @param diagnostics failure receiver
     * @return fully constructed immutable capability snapshot
     */
    static <M, E, S, A, J> Capabilities<M, E, S, A, J> initialize(
                                                                  Loaders<M, E, S, A, J> loaders,
                                                                  DiagnosticSink diagnostics) {
        M machine = load(Capability.MACHINE, loaders.machine(), diagnostics);
        E energy = load(Capability.ENERGY, loaders.energy(), diagnostics);
        S smartDoubling = load(Capability.SMART_DOUBLING, loaders.smartDoubling(), diagnostics);
        A advancedBlocking = load(Capability.ADVANCED_BLOCKING, loaders.advancedBlocking(), diagnostics);
        J eject = load(Capability.EJECT, loaders.eject(), diagnostics);
        return new Capabilities<>(machine, energy, smartDoubling, advancedBlocking, eject);
    }

    /**
     * Loads one capability, preserving fail-fast behavior for unknown runtime failures and non-linkage errors.
     */
    private static <T> T load(Capability capability, Loader<T> loader, DiagnosticSink diagnostics) {
        try {
            return Objects.requireNonNull(loader.load(), capability + " loader returned null");
        } catch (ReflectiveOperationException | LinkageError | SecurityException exception) {
            diagnostics.unavailable(capability, exception);
            return null;
        }
    }
}

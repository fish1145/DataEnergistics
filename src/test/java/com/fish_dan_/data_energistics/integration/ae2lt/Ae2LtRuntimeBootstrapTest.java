package com.fish_dan_.data_energistics.integration.ae2lt;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

final class Ae2LtRuntimeBootstrapTest {

    @Test
    void optionalCapabilitiesInitializeIndependently() {
        List<Ae2LtRuntimeBootstrap.Capability> diagnostics = new ArrayList<>();
        var missingSmart = initialize(
                () -> { throw new ClassNotFoundException("SmartDoublingCompat"); },
                () -> "advanced",
                diagnostics);
        assertEquals("machine", missingSmart.machine());
        assertEquals("energy", missingSmart.energy());
        assertNull(missingSmart.smartDoubling());
        assertEquals("advanced", missingSmart.advancedBlocking());
        assertEquals("eject", missingSmart.eject());

        var missingAdvanced = initialize(
                () -> "smart",
                () -> { throw new NoSuchMethodException("shouldBypassBlocking"); },
                diagnostics);
        assertEquals("smart", missingAdvanced.smartDoubling());
        assertNull(missingAdvanced.advancedBlocking());
        assertEquals("eject", missingAdvanced.eject());

        var bothPresent = initialize(() -> "smart", () -> "advanced", diagnostics);
        assertEquals("smart", bothPresent.smartDoubling());
        assertEquals("advanced", bothPresent.advancedBlocking());

        var initializerFailure = initialize(
                () -> { throw new ExceptionInInitializerError("broken optional hook"); },
                () -> "advanced",
                diagnostics);
        assertNull(initializerFailure.smartDoubling());
        assertEquals("advanced", initializerFailure.advancedBlocking());
        assertEquals("eject", initializerFailure.eject());
        assertEquals(List.of(
                Ae2LtRuntimeBootstrap.Capability.SMART_DOUBLING,
                Ae2LtRuntimeBootstrap.Capability.ADVANCED_BLOCKING,
                Ae2LtRuntimeBootstrap.Capability.SMART_DOUBLING), diagnostics);

        assertThrows(IllegalStateException.class, () -> initialize(
                () -> { throw new IllegalStateException("unexpected bootstrap bug"); },
                () -> "advanced",
                new ArrayList<>()));
    }

    private static Ae2LtRuntimeBootstrap.Capabilities<String, String, String, String, String> initialize(
                                                                                                         Ae2LtRuntimeBootstrap.Loader<String> smartDoubling,
                                                                                                         Ae2LtRuntimeBootstrap.Loader<String> advancedBlocking,
                                                                                                         List<Ae2LtRuntimeBootstrap.Capability> diagnostics) {
        return Ae2LtRuntimeBootstrap.initialize(
                new Ae2LtRuntimeBootstrap.Loaders<>(
                        () -> "machine",
                        () -> "energy",
                        smartDoubling,
                        advancedBlocking,
                        () -> "eject"),
                (capability, exception) -> diagnostics.add(capability));
    }
}

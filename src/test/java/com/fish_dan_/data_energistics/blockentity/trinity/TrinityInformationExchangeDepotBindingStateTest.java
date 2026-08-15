package com.fish_dan_.data_energistics.blockentity.trinity;

import com.fish_dan_.data_energistics.common.compartment.CompartmentHost;
import com.fish_dan_.data_energistics.common.compartment.CompartmentPart;

import org.junit.jupiter.api.Test;

import java.util.Collection;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Verifies that a Trinity access hatch binding identity changes phase without splitting its host and structure name.
 */
public final class TrinityInformationExchangeDepotBindingStateTest {

    @Test
    void releasePhaseRetainsTheCompleteBindingIdentity() {
        CompartmentHost host = emptyHost();
        TrinityInformationExchangeDepotBindingState active = TrinityInformationExchangeDepotBindingState.active("main", host);

        TrinityInformationExchangeDepotBindingState releasing = active.releasing();

        assertSame(active, releasing);
        assertSame(host, releasing.host());
        assertTrue(releasing.matches(host, "main"));
        assertEquals(TrinityInformationExchangeDepotBindingState.Phase.RELEASING, releasing.phase());
        assertTrue(releasing.isReleasing());
        assertFalse(releasing.isActive());

        releasing.beginReleaseAttempt();
        assertTrue(releasing.isReleaseInProgress());
        releasing.finishReleaseAttempt();
        assertFalse(releasing.isReleaseInProgress());
        assertSame(releasing, releasing.releasing());
    }

    @Test
    void blankStructureNameFailsBeforeItCanBeCreated() {
        CompartmentHost host = emptyHost();

        assertThrows(IllegalArgumentException.class, () -> TrinityInformationExchangeDepotBindingState.active("", host));
        assertThrows(IllegalArgumentException.class, () -> TrinityInformationExchangeDepotBindingState.active(null, host));
        assertThrows(IllegalArgumentException.class, () -> TrinityInformationExchangeDepotBindingState.active("main", null));
    }

    private static CompartmentHost emptyHost() {
        return new CompartmentHost() {

            @Override
            public void compartmentHost$addCompartment(String structureName, CompartmentPart part) {}

            @Override
            public void compartmentHost$removeCompartment(String structureName, CompartmentPart part) {}

            @Override
            public Collection<CompartmentPart> compartmentHost$getCompartments(String structureName) {
                return List.of();
            }
        };
    }
}

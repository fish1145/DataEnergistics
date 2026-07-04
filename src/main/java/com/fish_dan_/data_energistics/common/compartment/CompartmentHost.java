package com.fish_dan_.data_energistics.common.compartment;

import java.util.Collection;

/**
 * Multiblock controller contract for structures that accept compartment parts.
 */
public interface CompartmentHost {

    /**
     * Registers a compartment after a named structure forms.
     */
    void compartmentHost$addCompartment(String structureName, CompartmentPart part);

    /**
     * Unregisters a compartment when a named structure invalidates.
     */
    void compartmentHost$removeCompartment(String structureName, CompartmentPart part);

    /**
     * Returns currently registered compartments for a named structure.
     */
    Collection<CompartmentPart> compartmentHost$getCompartments(String structureName);
}

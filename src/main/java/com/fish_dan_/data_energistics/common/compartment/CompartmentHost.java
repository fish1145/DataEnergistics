package com.fish_dan_.data_energistics.common.compartment;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Objects;

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

    /**
     * Returns registered compartments for a named structure that match the requested role.
     */
    default Collection<CompartmentPart> compartmentHost$getCompartments(String structureName, CompartmentType type) {
        Objects.requireNonNull(type, "type");
        return compartmentHost$getCompartments(structureName).stream()
                .filter(part -> part.compartmentType() == type)
                .toList();
    }

    /**
     * Returns structure-facing storages that can provide input contents.
     */
    default Collection<CompartmentStorage> compartmentHost$getInputStorages(String structureName) {
        return compartmentHost$getCompartments(structureName).stream()
                .filter(part -> part.compartmentType() == CompartmentType.INPUT ||
                        part.compartmentType() == CompartmentType.ME_INPUT)
                .map(CompartmentPart::compartmentStorage)
                .toList();
    }

    /**
     * Returns a dynamic aggregate input view for main structure logic without depending on concrete block entities.
     */
    default CompartmentStorage compartmentHost$inputStorage(String structureName) {
        return new CompartmentStorageGroup(() -> compartmentHost$getInputStorages(structureName));
    }

    /**
     * Returns structure-facing storages that can receive output contents.
     */
    default Collection<CompartmentStorage> compartmentHost$getOutputStorages(String structureName) {
        return compartmentHost$getCompartments(structureName).stream()
                .filter(part -> part.compartmentType() == CompartmentType.OUTPUT ||
                        part.compartmentType() == CompartmentType.ME_OUTPUT)
                .map(CompartmentPart::compartmentStorage)
                .toList();
    }

    /**
     * Returns a dynamic aggregate output view for main structure logic without depending on concrete block entities.
     */
    default CompartmentStorage compartmentHost$outputStorage(String structureName) {
        return new CompartmentStorageGroup(() -> compartmentHost$getOutputStorages(structureName));
    }

    /**
     * Returns pattern buffer compartments that expose pattern-buffer-specific storage roles.
     */
    default Collection<PatternBufferCompartmentPart> compartmentHost$getPatternBuffers(String structureName) {
        List<PatternBufferCompartmentPart> patternBuffers = new ArrayList<>();
        for (CompartmentPart part : compartmentHost$getCompartments(structureName, CompartmentType.PATTERN_BUFFER)) {
            if (part instanceof PatternBufferCompartmentPart patternBuffer) {
                patternBuffers.add(patternBuffer);
            }
        }
        return List.copyOf(patternBuffers);
    }

    /**
     * Returns a dynamic aggregate pattern-buffer storage view for main structure business to read and write pattern
     * buffer data without depending on concrete block entities.
     */
    default CompartmentStorage compartmentHost$patternBufferStorage(String structureName) {
        return new CompartmentStorageGroup(() -> compartmentHost$getPatternBuffers(structureName).stream()
                .map(PatternBufferCompartmentPart::patternAggregateStorage)
                .toList());
    }
}

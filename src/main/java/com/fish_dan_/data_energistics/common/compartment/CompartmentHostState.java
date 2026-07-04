package com.fish_dan_.data_energistics.common.compartment;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Reusable runtime state for multiblock controllers that accept compartment parts.
 */
public final class CompartmentHostState {

    private final Map<String, List<CompartmentPart>> compartments = new LinkedHashMap<>();

    public void addCompartment(String structureName, CompartmentPart part) {
        Objects.requireNonNull(structureName, "structureName");
        Objects.requireNonNull(part, "part");
        List<CompartmentPart> parts = this.compartments.computeIfAbsent(structureName, ignored -> new ArrayList<>());
        if (!parts.contains(part)) {
            parts.add(part);
        }
    }

    public void removeCompartment(String structureName, CompartmentPart part) {
        Objects.requireNonNull(structureName, "structureName");
        Objects.requireNonNull(part, "part");
        List<CompartmentPart> parts = this.compartments.get(structureName);
        if (parts == null) {
            return;
        }
        parts.remove(part);
        if (parts.isEmpty()) {
            this.compartments.remove(structureName);
        }
    }

    public Collection<CompartmentPart> compartments(String structureName) {
        List<CompartmentPart> parts = this.compartments.get(structureName);
        return parts == null ? List.of() : List.copyOf(parts);
    }

    public void clear(String structureName) {
        this.compartments.remove(structureName);
    }

    public void clearAll() {
        this.compartments.clear();
    }
}

package com.fish_dan_.data_energistics.common.multiblock.vertical;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;

/**
 * Result and diagnostics for one vertical multiblock scan.
 *
 * <p>
 * The context is created per scan attempt. Successful contexts expose the origin, height, direction, and matched
 * positions needed to bind controller and parts. Failed contexts keep the first error message for diagnostics.
 *
 * @param <S> block state representation used by the caller
 */
public final class VerticalMultiBlockContext<S> {

    private final VerticalMultiBlockDefinition<S> definition;
    private final VerticalMultiBlockPos origin;
    private final VerticalMultiBlockPos controllerPos;
    private final VerticalMultiBlockDirection direction;
    private final int height;
    private final LinkedHashSet<VerticalMultiBlockPos> matchedPositions = new LinkedHashSet<>();
    private String error = "";

    public VerticalMultiBlockContext(VerticalMultiBlockDefinition<S> definition,
                                     VerticalMultiBlockPos origin,
                                     VerticalMultiBlockPos controllerPos,
                                     VerticalMultiBlockDirection direction,
                                     int height) {
        this.definition = definition;
        this.origin = origin;
        this.controllerPos = controllerPos;
        this.direction = direction;
        this.height = height;
    }

    public VerticalMultiBlockDefinition<S> definition() {
        return this.definition;
    }

    public VerticalMultiBlockPos origin() {
        return this.origin;
    }

    public VerticalMultiBlockPos controllerPos() {
        return this.controllerPos;
    }

    public VerticalMultiBlockDirection direction() {
        return this.direction;
    }

    public int height() {
        return this.height;
    }

    public Set<VerticalMultiBlockPos> matchedPositions() {
        return Collections.unmodifiableSet(new LinkedHashSet<>(this.matchedPositions));
    }

    public String error() {
        return this.error;
    }

    void addMatchedPosition(VerticalMultiBlockPos pos) {
        this.matchedPositions.add(pos);
    }

    void fail(String error) {
        if (this.error.isBlank()) {
            this.error = error;
        }
        this.matchedPositions.clear();
    }
}

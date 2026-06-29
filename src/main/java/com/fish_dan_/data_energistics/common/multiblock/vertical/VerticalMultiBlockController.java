package com.fish_dan_.data_energistics.common.multiblock.vertical;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Runtime host for a vertical multiblock structure.
 *
 * <p>
 * A controller owns the formed state and is responsible for requesting scans when nearby blocks may have changed.
 * The interface intentionally contains only lifecycle state, leaving machine-specific behavior in the implementing
 * block entity.
 */
public interface VerticalMultiBlockController {

    /**
     * @return structure id this controller is allowed to form
     */
    String verticalMultiBlock$getDefinitionId();

    /**
     * Called when a scan successfully forms the structure.
     *
     * @param context completed scan context
     */
    void verticalMultiBlock$onStructureFormed(VerticalMultiBlockContext<?> context);

    /**
     * Called when a scan successfully forms a named structure.
     *
     * @param structureName formed structure name from the matched definition
     * @param context       completed scan context
     */
    default void verticalMultiBlock$onStructureFormed(String structureName, VerticalMultiBlockContext<?> context) {
        verticalMultiBlock$onStructureFormed(context);
    }

    /**
     * Called when a previously formed structure is no longer valid.
     *
     * @param reason human-readable reason for logs or diagnostics
     */
    void verticalMultiBlock$onStructureInvalid(String reason);

    /**
     * Called when a previously formed named structure is no longer valid.
     *
     * @param structureName invalidated structure name from the previous runtime state
     * @param reason        human-readable reason for logs or diagnostics
     */
    default void verticalMultiBlock$onStructureInvalid(String structureName, String reason) {
        verticalMultiBlock$onStructureInvalid(reason);
    }

    /**
     * Requests a fresh scan around this controller.
     *
     * <p>
     * Implementations should call this from block placement, removal, load, or neighbor-change paths.
     */
    void verticalMultiBlock$requestRecheck();

    /**
     * Returns the current runtime state stored by the controller implementation.
     *
     * <p>
     * Implementations should initialize the state with an unformed record and keep it updated through the runtime
     * coordinator.
     *
     * @return current runtime state
     */
    VerticalMultiBlockRuntimeState verticalMultiBlock$getRuntimeState();

    /**
     * Stores a new runtime state.
     *
     * @param state new runtime state
     */
    void verticalMultiBlock$setRuntimeState(VerticalMultiBlockRuntimeState state);

    /**
     * Returns the runtime state for one named structure.
     *
     * <p>
     * The default implementation preserves legacy single-state controllers by returning the legacy active state only
     * when that active state already has the requested structure name. Controllers that can form multiple structures
     * at once should override this with independent state storage.
     *
     * @param structureName structure name to query
     * @return runtime state for the named structure, or an unformed state when it is not active
     */
    default VerticalMultiBlockRuntimeState verticalMultiBlock$getRuntimeState(String structureName) {
        structureName = requireStructureName(structureName);
        VerticalMultiBlockRuntimeState state = verticalMultiBlock$getRuntimeState();
        if (state.formed() && structureName.equals(state.structureName())) {
            return state;
        }
        return VerticalMultiBlockRuntimeState.unformed();
    }

    /**
     * Stores the runtime state for one named structure.
     *
     * <p>
     * The default implementation writes formed states through to the legacy single-state slot, and only clears that
     * slot when the legacy active state has the requested structure name. Multi-structure controllers should override
     * this with map-backed storage.
     *
     * @param structureName structure name to update
     * @param state         new runtime state for the named structure
     */
    default void verticalMultiBlock$setRuntimeState(String structureName, VerticalMultiBlockRuntimeState state) {
        structureName = requireStructureName(structureName);
        Objects.requireNonNull(state, "state");
        if (state.formed()) {
            verticalMultiBlock$setRuntimeState(state);
            return;
        }

        VerticalMultiBlockRuntimeState currentState = verticalMultiBlock$getRuntimeState();
        if (currentState.formed() && structureName.equals(currentState.structureName())) {
            verticalMultiBlock$setRuntimeState(state);
        }
    }

    /**
     * @return names of structures currently formed by this controller
     */
    default Set<String> verticalMultiBlock$getFormedStructureNames() {
        VerticalMultiBlockRuntimeState state = verticalMultiBlock$getRuntimeState();
        return state.formed() ? Set.of(state.structureName()) : Set.of();
    }

    /**
     * Returns all active named runtime states.
     *
     * @return map keyed by structure name; unformed structures are omitted
     */
    default Map<String, VerticalMultiBlockRuntimeState> verticalMultiBlock$getRuntimeStates() {
        VerticalMultiBlockRuntimeState state = verticalMultiBlock$getRuntimeState();
        return state.formed() ? Map.of(state.structureName(), state) : Map.of();
    }

    /**
     * @return whether the controller is currently formed
     */
    default boolean verticalMultiBlock$isFormed() {
        return verticalMultiBlock$getRuntimeState().formed();
    }

    /**
     * @param structureName structure name to query
     * @return whether the named structure is currently formed
     */
    default boolean verticalMultiBlock$isFormed(String structureName) {
        return verticalMultiBlock$getRuntimeState(structureName).formed();
    }

    /**
     * @return current formed height, or {@code 0} when unformed
     */
    default int verticalMultiBlock$getCurrentHeight() {
        return verticalMultiBlock$getRuntimeState().height();
    }

    /**
     * @param structureName structure name to query
     * @return current formed height for the named structure, or {@code 0} when unformed
     */
    default int verticalMultiBlock$getCurrentHeight(String structureName) {
        return verticalMultiBlock$getRuntimeState(structureName).height();
    }

    /**
     * @return current formed structure name, or blank when unformed
     */
    default String verticalMultiBlock$getCurrentStructureName() {
        return verticalMultiBlock$getRuntimeState().structureName();
    }

    /**
     * @return current formed section name, or blank when unformed
     */
    default String verticalMultiBlock$getCurrentSectionName() {
        return verticalMultiBlock$getCurrentStructureName();
    }

    /**
     * @return matched structure positions for the current state
     */
    default List<VerticalMultiBlockPos> verticalMultiBlock$getMatchedPositions() {
        return verticalMultiBlock$getRuntimeState().matchedPositions();
    }

    /**
     * @param structureName structure name to query
     * @return matched structure positions for the named state
     */
    default List<VerticalMultiBlockPos> verticalMultiBlock$getMatchedPositions(String structureName) {
        return verticalMultiBlock$getRuntimeState(structureName).matchedPositions();
    }

    private static String requireStructureName(String structureName) {
        if (structureName == null || structureName.isBlank()) {
            throw new IllegalArgumentException("Vertical multiblock structure name must not be blank");
        }
        return structureName;
    }
}

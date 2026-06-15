package com.fish_dan_.data_energistics.common.multiblock.vertical;

import java.util.List;

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
     * Called when a previously formed structure is no longer valid.
     *
     * @param reason human-readable reason for logs or diagnostics
     */
    void verticalMultiBlock$onStructureInvalid(String reason);

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
     * @return whether the controller is currently formed
     */
    default boolean verticalMultiBlock$isFormed() {
        return verticalMultiBlock$getRuntimeState().formed();
    }

    /**
     * @return current formed height, or {@code 0} when unformed
     */
    default int verticalMultiBlock$getCurrentHeight() {
        return verticalMultiBlock$getRuntimeState().height();
    }

    /**
     * @return matched structure positions for the current state
     */
    default List<VerticalMultiBlockPos> verticalMultiBlock$getMatchedPositions() {
        return verticalMultiBlock$getRuntimeState().matchedPositions();
    }
}

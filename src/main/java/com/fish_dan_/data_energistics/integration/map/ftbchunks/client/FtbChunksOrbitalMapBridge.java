package com.fish_dan_.data_energistics.integration.map.ftbchunks.client;

/**
 * Names the two Mixin capabilities required by the FTB Chunks orbital map adapter.
 *
 * <p>
 * The adapter checks both markers on the Minecraft client thread before calling FTB's map-opening API. The contracts
 * contain no state and exist only for the lifetime of the optional FTB client integration; server and common code must
 * not reference them.
 * </p>
 */
public interface FtbChunksOrbitalMapBridge {

    /** Marks a target whose orbital click and popup hooks were injected; it has no methods or side effects. */
    interface Input extends FtbChunksOrbitalMapBridge {}

    /** Marks a target whose exact-version coordinate/drag accessors were applied; it has no methods or side effects. */
    interface Access extends FtbChunksOrbitalMapBridge {}
}

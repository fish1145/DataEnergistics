package com.fish_dan_.data_energistics.common.multiblock;

import net.minecraft.core.BlockPos;

import org.jspecify.annotations.Nullable;

/**
 * Shared status contract for multiblock hosts that need to expose runtime state to integrations.
 * <p>
 * This interface exists so Jade and future display integrations can read multiblock state without
 * hardcoding one specific machine implementation.
 */
public interface MultiBlockStatusProvider {

    /**
     * Returns whether the host's backing machine or network node is currently online.
     */
    boolean multiBlock$isOnline();

    /**
     * Returns whether the host currently has its primary multiblock structure formed.
     */
    boolean multiBlock$isFormed();

    /**
     * Returns whether this block entity is acting as the formed structure controller.
     */
    boolean multiBlock$isController();

    /**
     * Returns the formed vertical height, or {@code 0} when the multiblock is not vertical or is unformed.
     */
    int multiBlock$getHeight();

    /**
     * Returns the number of matched blocks in the current formed structure.
     */
    int multiBlock$getMatchedBlockCount();

    /**
     * Returns the last unformed diagnostic message, or a blank string when no diagnostic is available.
     */
    String multiBlock$getLastFailureReason();

    /**
     * Returns the last diagnostic position, or {@code null} when no position is available.
     */
    @Nullable
    BlockPos multiBlock$getLastFailurePosition();
}

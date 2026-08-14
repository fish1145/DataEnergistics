package com.fish_dan_.data_energistics.gui.ldlib2.trinity.pattern.aggregate;

import org.jetbrains.annotations.ApiStatus;

import java.util.List;

/** Sends one ordered batch of aggregate pattern slots selected by a Shift-drag gesture. */
@ApiStatus.Internal
@FunctionalInterface
public interface TrinityPatternQuickMoveSender {

    /** Submits the stable layout slots captured by one complete client gesture. */
    boolean send(long generation, long layoutRevision, List<Integer> globalSlots);
}

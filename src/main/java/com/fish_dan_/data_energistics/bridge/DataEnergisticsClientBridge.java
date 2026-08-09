package com.fish_dan_.data_energistics.bridge;

import com.fish_dan_.data_energistics.gui.ldlib2.multiblock.StructurePreviewSceneBinder;
import com.fish_dan_.data_energistics.network.PatternEncodingPreferencesAckPayload;
import com.fish_dan_.data_energistics.network.PatternUploadSucceededPayload;
import com.fish_dan_.data_energistics.network.meteorite.DataMeteoriteCompassResponsePayload;
import com.fish_dan_.data_energistics.network.ui.UniversalTerminalStateSyncPayload;
import com.fish_dan_.data_energistics.recipe.reassembler.DataRipperReassemblerRecipe;

import net.minecraft.world.entity.player.Player;

import guideme.document.block.LytBlock;

/**
 * Common-side contract for actions that must be implemented by the client distribution.
 * <p>
 * The bridge exists so common code can request client-only behavior without string-based
 * reflection or direct imports of client-only classes.
 */
public interface DataEnergisticsClientBridge {

    /**
     * Checks whether the current thread is the Minecraft client thread.
     *
     * @return {@code true} when execution is on the client thread.
     */
    boolean isClientThread();

    /**
     * Returns the client adapter that installs an independent LDLib2 world renderer on a common scene shell.
     *
     * @return stateless binder whose every bind call creates a fresh rendering lifetime
     */
    StructurePreviewSceneBinder structurePreviewSceneBinder();

    /**
     * Refreshes the open data ripper screen after the common menu state changes.
     */
    void refreshDataRipperScreen();

    /**
     * Refreshes the open data distribution tower screen after the common menu state changes.
     */
    void refreshDataDistributionTowerScreen();

    /**
     * Caches universal terminal state received from the server on the client UI side.
     *
     * @param payload synced terminal state payload.
     */
    void cacheSyncedTerminalState(UniversalTerminalStateSyncPayload payload);

    /**
     * Caches the meteorite compass response received from the server on the client side.
     *
     * @param payload synced compass response payload.
     */
    void cacheSyncedCompassResult(DataMeteoriteCompassResponsePayload payload);

    /**
     * Applies a pattern-encoding preference acknowledgement to the current client menu.
     *
     * @param payload acknowledged preference values and migration mask.
     * @param player  client player that owns the current menu.
     */
    void handlePatternEncodingPreferencesAck(PatternEncodingPreferencesAckPayload payload, Player player);

    /**
     * Applies authoritative upload history and, for this mod's uploads, displays the success message.
     *
     * @param payload confirmed upload event from the server.
     * @param player  client player receiving the event.
     */
    void handlePatternUploadSucceeded(PatternUploadSucceededPayload payload, Player player);

    /**
     * Creates the client GuideME body for a data ripper reassembler recipe.
     *
     * @param recipe recipe displayed by the guide body.
     * @return rendered guide body.
     */
    LytBlock createDataRipperReassemblerGuideRecipeBody(DataRipperReassemblerRecipe recipe);

    /**
     * Replaces an AE2WTLib wireless pattern encoding screen with this mod's preview screen when possible.
     *
     * @param currentScreen    screen currently opened by Minecraft.
     * @param applyImmediately whether the replacement should be installed immediately.
     * @return replacement screen, or {@code null} when no replacement applies.
     */
    Object maybeReplaceWirelessPatternEncodingScreen(Object currentScreen, boolean applyImmediately);
}

package com.fish_dan_.data_energistics.client;

import com.fish_dan_.data_energistics.bridge.DataEnergisticsClientBridge;
import com.fish_dan_.data_energistics.client.gui.ldlib2.multiblock.LdlibStructurePreviewSceneBinder;
import com.fish_dan_.data_energistics.client.guideme.DataRipperReassemblerGuideRecipeBody;
import com.fish_dan_.data_energistics.client.integration.Ae2WtLibClientCompat;
import com.fish_dan_.data_energistics.client.preferences.PatternEncodingPreferencesAckClientHandler;
import com.fish_dan_.data_energistics.client.preferences.PatternUploadSucceededClientHandler;
import com.fish_dan_.data_energistics.client.screen.MenuClientRefreshHandler;
import com.fish_dan_.data_energistics.client.screen.terminal.UniversalTerminalStateSyncClientHandler;
import com.fish_dan_.data_energistics.gui.ldlib2.multiblock.preview.scene.StructurePreviewSceneBinder;
import com.fish_dan_.data_energistics.integration.ModFlags;
import com.fish_dan_.data_energistics.network.meteorite.DataMeteoriteCompassResponsePayload;
import com.fish_dan_.data_energistics.network.patternencoding.PatternEncodingPreferencesAckPayload;
import com.fish_dan_.data_energistics.network.patternencoding.PatternUploadSucceededPayload;
import com.fish_dan_.data_energistics.network.ui.UniversalTerminalStateSyncPayload;
import com.fish_dan_.data_energistics.recipe.reassembler.DataRipperReassemblerRecipe;

import net.minecraft.world.entity.player.Player;

import guideme.document.block.LytBlock;

public final class ClientRuntimeBridge implements DataEnergisticsClientBridge {

    /**
     * Stateless factory retained so every preview consumer enters the same audited client adapter.
     */
    private final StructurePreviewSceneBinder structurePreviewSceneBinder = new LdlibStructurePreviewSceneBinder();

    @Override
    public boolean isClientThread() {
        return ClientThreadHelper.isClientThread();
    }

    @Override
    public StructurePreviewSceneBinder structurePreviewSceneBinder() {
        return this.structurePreviewSceneBinder;
    }

    @Override
    public void refreshDataRipperScreen() {
        MenuClientRefreshHandler.refreshDataRipperScreen();
    }

    @Override
    public void refreshDataDistributionTowerScreen() {
        MenuClientRefreshHandler.refreshDataDistributionTowerScreen();
    }

    @Override
    public void cacheSyncedTerminalState(UniversalTerminalStateSyncPayload payload) {
        UniversalTerminalStateSyncClientHandler.cacheSyncedTerminalState(payload);
    }

    @Override
    public void cacheSyncedCompassResult(DataMeteoriteCompassResponsePayload payload) {
        DataMeteoriteCompassClientCache.cacheSyncedCompassResult(payload);
    }

    @Override
    public void handlePatternEncodingPreferencesAck(PatternEncodingPreferencesAckPayload payload, Player player) {
        PatternEncodingPreferencesAckClientHandler.handle(payload, player);
    }

    @Override
    public void handlePatternUploadSucceeded(PatternUploadSucceededPayload payload, Player player) {
        PatternUploadSucceededClientHandler.handle(payload, player);
    }

    @Override
    public LytBlock createDataRipperReassemblerGuideRecipeBody(DataRipperReassemblerRecipe recipe) {
        return new DataRipperReassemblerGuideRecipeBody(recipe);
    }

    @Override
    public Object maybeReplaceWirelessPatternEncodingScreen(Object currentScreen, boolean applyImmediately) {
        if (!ModFlags.isAe2WtLibWirelessPatternEncodingSupportLoaded()) {
            return null;
        }
        return Ae2WtLibClientCompatHolder.maybeReplaceWirelessPatternEncodingScreen(currentScreen, applyImmediately);
    }

    private static final class Ae2WtLibClientCompatHolder {

        private Ae2WtLibClientCompatHolder() {}

        private static Object maybeReplaceWirelessPatternEncodingScreen(Object currentScreen, boolean applyImmediately) {
            return Ae2WtLibClientCompat.maybeReplaceWirelessPatternEncodingScreen(currentScreen, applyImmediately);
        }
    }
}

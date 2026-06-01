package com.fish_dan_.data_energistics.client.screen;

import com.fish_dan_.data_energistics.network.UniversalTerminalStateSyncPayload;

public final class UniversalTerminalStateSyncClientHandler {

    private UniversalTerminalStateSyncClientHandler() {
    }

    public static void cacheSyncedTerminalState(UniversalTerminalStateSyncPayload payload) {
        UniversalTerminalClientHelper.cacheSyncedTerminalState(
                payload.installedTerminalNames(),
                payload.activeTerminalName());
    }
}

package com.fish_dan_.data_energistics.menu.universal;

import com.fish_dan_.data_energistics.part.UniversalTerminalPart;

public interface UniversalTerminalMenuBridge {

    int getAvailableTerminalMask();

    int getActiveTerminalIndex();

    UniversalTerminalPart getUniversalTerminalHost();

    void sendCycleTerminal(boolean reverse);
}

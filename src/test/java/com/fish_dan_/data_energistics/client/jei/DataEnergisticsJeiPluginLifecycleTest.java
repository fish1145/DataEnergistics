package com.fish_dan_.data_energistics.client.jei;

import appeng.client.gui.Icon;
import mezz.jei.api.gui.drawable.IDrawable;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

class DataEnergisticsJeiPluginLifecycleTest {

    private static final IDrawable TEST_ICON = new JeiIconDrawable(Icon.TOOLBAR_BUTTON_BACKGROUND);

    @Test
    void runtimeUnavailableAllowsTheSamePluginInstanceToRegisterAReplacementCategory() {
        DataEnergisticsJeiPlugin plugin = new DataEnergisticsJeiPlugin();
        TrinityMultiblockJeiCategory first = category();

        assertSame(first, plugin.installTrinityMultiblockCategory(first));
        assertSame(first, plugin.currentTrinityMultiblockCategory());
        assertThrows(IllegalStateException.class, () -> plugin.installTrinityMultiblockCategory(category()));

        plugin.releaseTrinityMultiblockCategory();

        assertNull(plugin.currentTrinityMultiblockCategory());
        TrinityMultiblockJeiCategory replacement = category();
        assertSame(replacement, plugin.installTrinityMultiblockCategory(replacement));
        assertSame(replacement, plugin.currentTrinityMultiblockCategory());

        plugin.releaseTrinityMultiblockCategory();
        assertNull(plugin.currentTrinityMultiblockCategory());
    }

    private static TrinityMultiblockJeiCategory category() {
        return new TrinityMultiblockJeiCategory(TEST_ICON, (recipe, composition, change) -> {});
    }
}

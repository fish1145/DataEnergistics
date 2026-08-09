package com.fish_dan_.data_energistics.registry;

import com.fish_dan_.data_energistics.api.entrypoint.DataEnergisticsEntrypoint;
import com.fish_dan_.data_energistics.api.entrypoint.DataEnergisticsPlugin;
import com.fish_dan_.data_energistics.api.entrypoint.DataEnergisticsRegistry;
import com.fish_dan_.data_energistics.api.registry.terminal.UniversalTerminalConfigurationProfile;
import com.fish_dan_.data_energistics.api.registry.terminal.UniversalTerminalRegistration;
import com.fish_dan_.data_energistics.api.registry.terminal.UniversalTerminalRegistry;
import com.fish_dan_.data_energistics.menu.universal.UniversalTerminalMenuLocator;
import com.fish_dan_.data_energistics.util.UniversalTerminalData;

import net.minecraft.world.item.ItemStack;

import appeng.api.config.Settings;
import appeng.api.config.ShowPatternProviders;
import appeng.api.util.IConfigManager;
import appeng.core.definitions.AEParts;

import java.util.List;

@DataEnergisticsEntrypoint
public final class UniversalTerminalAdapters implements DataEnergisticsPlugin {

    /**
     * Public constructor required by the common entrypoint scanner.
     */
    public UniversalTerminalAdapters() {}

    /**
     * Stages Data Energistics' built-in terminal adapters in the unified registry transaction.
     */
    @Override
    public void register(DataEnergisticsRegistry registry) {
        UniversalTerminalRegistry terminals = registry.universalTerminals();

        terminals.registerTerminal(
                UniversalTerminalData.TERMINAL_ITEM,
                AEParts.TERMINAL::is,
                () -> new ItemStack(AEParts.TERMINAL.asItem()),
                DEMenus.UNIVERSAL_ME_STORAGE::get);
        terminals.registerTerminal(
                UniversalTerminalData.TERMINAL_CRAFTING,
                AEParts.CRAFTING_TERMINAL::is,
                () -> new ItemStack(AEParts.CRAFTING_TERMINAL.asItem()),
                DEMenus.UNIVERSAL_CRAFTING_TERM::get);
        terminals.registerTerminal(
                UniversalTerminalData.TERMINAL_PATTERN_ACCESS,
                AEParts.PATTERN_ACCESS_TERMINAL::is,
                () -> new ItemStack(AEParts.PATTERN_ACCESS_TERMINAL.asItem()),
                DEMenus.UNIVERSAL_PATTERN_ACCESS_TERM::get,
                UniversalTerminalConfigurationProfile.PATTERN_ACCESS,
                false,
                UniversalTerminalAdapters::createPatternAccessConfigManager);
        terminals.registerTerminal(
                UniversalTerminalData.TERMINAL_PATTERN_ENCODING,
                AEParts.PATTERN_ENCODING_TERMINAL::is,
                () -> new ItemStack(AEParts.PATTERN_ENCODING_TERMINAL.asItem()),
                DEMenus.UNIVERSAL_PATTERN_ENCODING_TERM::get);
    }

    /**
     * Publishes the frozen terminal snapshot to the runtime data facade after common setup.
     */
    public static void install(List<UniversalTerminalRegistration> definitions) {
        UniversalTerminalMenuLocator.init();
        UniversalTerminalData.installDefinitions(definitions);
    }

    private static IConfigManager createPatternAccessConfigManager(Runnable saveAction) {
        return IConfigManager.builder(saveAction)
                .registerSetting(Settings.TERMINAL_SHOW_PATTERN_PROVIDERS, ShowPatternProviders.VISIBLE)
                .build();
    }
}

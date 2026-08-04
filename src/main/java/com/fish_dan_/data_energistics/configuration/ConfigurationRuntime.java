package com.fish_dan_.data_energistics.configuration;

import com.fish_dan_.data_energistics.configuration.ConfigurationSnapshot.DataDistributionTowerSettings;
import com.fish_dan_.data_energistics.configuration.ConfigurationSnapshot.DataExtractorSettings;
import com.fish_dan_.data_energistics.configuration.ConfigurationSnapshot.DataNukeSettings;
import com.fish_dan_.data_energistics.configuration.ConfigurationSnapshot.DataRipperSettings;
import com.fish_dan_.data_energistics.configuration.ConfigurationSnapshot.DataSanctumInterfaceSettings;
import com.fish_dan_.data_energistics.configuration.ConfigurationSnapshot.FlatteningTntSettings;
import com.fish_dan_.data_energistics.configuration.ConfigurationSnapshot.SolarPanelSettings;

import java.nio.file.Path;
import java.util.concurrent.atomic.AtomicReference;

/** Owns the single atomically published root snapshot read by gameplay code. */
public final class ConfigurationRuntime {

    private static final AtomicReference<ConfigurationSnapshot> CURRENT = new AtomicReference<>(createDefaults());

    private ConfigurationRuntime() {}

    public static ConfigurationSnapshot current() {
        return CURRENT.get();
    }

    public static DataRipperSettings dataRipper() {
        return current().dataRipper();
    }

    public static DataDistributionTowerSettings dataDistributionTower() {
        return current().dataDistributionTower();
    }

    public static DataSanctumInterfaceSettings dataSanctumInterface() {
        return current().dataSanctumInterface();
    }

    public static DataExtractorSettings dataExtractor() {
        return current().dataExtractor();
    }

    public static FlatteningTntSettings flatteningTnt() {
        return current().flatteningTnt();
    }

    public static DataNukeSettings dataNuke() {
        return current().dataNuke();
    }

    public static SolarPanelSettings solarPanel() {
        return current().solarPanel();
    }

    public static TrinityCraftingSettings trinityCrafting() {
        return current().trinityCrafting();
    }

    public static TrinityDispatchSettings trinityDispatch() {
        return current().trinityDispatch();
    }

    static void publish(ConfigurationSnapshot candidate) {
        CURRENT.updateAndGet(current -> {
            if (candidate.revision() <= current.revision()) {
                throw new IllegalStateException(
                        "Configuration revision must increase: current=" + current.revision() +
                                ", candidate=" + candidate.revision());
            }
            return candidate;
        });
    }

    static void restoreForTest(ConfigurationSnapshot snapshot) {
        CURRENT.set(snapshot);
    }

    private static ConfigurationSnapshot createDefaults() {
        try {
            return SnapshotAssembler.assemble(
                    new DataEnergisticsConfiguration(),
                    Path.of("<built-in-defaults>"),
                    0L);
        } catch (InvalidConfigurationException exception) {
            throw new ExceptionInInitializerError(exception);
        }
    }
}

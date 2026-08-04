package com.fish_dan_.data_energistics.configuration.runtime;

import com.fish_dan_.data_energistics.common.crafting.trinity.dispatch.governor.CraftingDispatchGovernor;
import com.fish_dan_.data_energistics.configuration.api.DataEnergisticsSettings;
import com.fish_dan_.data_energistics.configuration.snapshot.TrinityDispatchSettings;

/** Immutable revision binding for one grid's transient dispatch Governor and its configuration. */
public record TrinityDispatchGovernorState(
                                           long revision,
                                           TrinityDispatchSettings settings,
                                           CraftingDispatchGovernor governor) {

    public static TrinityDispatchGovernorState capture(DataEnergisticsSettings configuration) {
        TrinityDispatchSettings settings = TrinityDispatchSettings.copyOf(configuration.trinityDispatch());
        return new TrinityDispatchGovernorState(
                configuration.revision(),
                settings,
                CraftingDispatchGovernor.create(settings.governorSettings()));
    }

    public TrinityDispatchGovernorState refresh(DataEnergisticsSettings configuration) {
        if (configuration.revision() == this.revision) {
            return this;
        }
        TrinityDispatchSettings nextSettings = TrinityDispatchSettings.copyOf(configuration.trinityDispatch());
        if (nextSettings.equals(this.settings)) {
            return new TrinityDispatchGovernorState(configuration.revision(), this.settings, this.governor);
        }
        return new TrinityDispatchGovernorState(
                configuration.revision(),
                nextSettings,
                CraftingDispatchGovernor.create(nextSettings.governorSettings()));
    }
}

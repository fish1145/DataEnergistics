package com.fish_dan_.data_energistics.integration.weapon.appflux;

import appeng.api.stacks.AEKey;

import com.glodblock.github.appflux.common.me.key.FluxKey;
import com.glodblock.github.appflux.common.me.key.type.EnergyType;

/** Only load after the Applied Flux presence check. */
public final class FluxAmmunition {

    private FluxAmmunition() {}

    public static AEKey key() {
        return FluxKey.of(EnergyType.FE);
    }
}

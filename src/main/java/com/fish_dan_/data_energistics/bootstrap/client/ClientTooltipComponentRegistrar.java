package com.fish_dan_.data_energistics.bootstrap.client;

import com.fish_dan_.data_energistics.client.render.DigitalStorageDepotClientTooltipComponent;
import com.fish_dan_.data_energistics.item.DigitalStorageDepotTooltipComponent;

import net.neoforged.neoforge.client.event.RegisterClientTooltipComponentFactoriesEvent;

final class ClientTooltipComponentRegistrar {

    private ClientTooltipComponentRegistrar() {}

    static void register(RegisterClientTooltipComponentFactoriesEvent event) {
        event.register(DigitalStorageDepotTooltipComponent.class, DigitalStorageDepotClientTooltipComponent::new);
    }
}

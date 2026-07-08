package com.fish_dan_.data_energistics.bootstrap.client;

import com.fish_dan_.data_energistics.client.ModFluidClientExtensions;
import com.fish_dan_.data_energistics.client.render.MeVacuumItemRenderer;
import com.fish_dan_.data_energistics.registry.ModItems;

import net.minecraft.client.renderer.BlockEntityWithoutLevelRenderer;
import net.neoforged.neoforge.client.extensions.common.IClientItemExtensions;
import net.neoforged.neoforge.client.extensions.common.RegisterClientExtensionsEvent;

final class ClientExtensionRegistrar {

    private ClientExtensionRegistrar() {}

    static void register(RegisterClientExtensionsEvent event) {
        ModFluidClientExtensions.register(event);
        event.registerItem(new IClientItemExtensions() {

            private BlockEntityWithoutLevelRenderer renderer;

            @Override
            public BlockEntityWithoutLevelRenderer getCustomRenderer() {
                if (this.renderer == null) {
                    this.renderer = MeVacuumItemRenderer.create();
                }
                return this.renderer;
            }
        }, ModItems.ME_VACUUM.get());
    }
}

package com.fish_dan_.data_energistics.bootstrap.client;

import com.fish_dan_.data_energistics.client.registry.DEFluidClientExtensions;
import com.fish_dan_.data_energistics.client.render.item.MeVacuumItemRenderer;
import com.fish_dan_.data_energistics.client.render.item.OrderPackageItemRenderer;
import com.fish_dan_.data_energistics.registry.DEItems;

import net.minecraft.client.renderer.BlockEntityWithoutLevelRenderer;
import net.neoforged.neoforge.client.extensions.common.IClientItemExtensions;
import net.neoforged.neoforge.client.extensions.common.RegisterClientExtensionsEvent;

final class ClientExtensionRegistrar {

    private ClientExtensionRegistrar() {}

    static void register(RegisterClientExtensionsEvent event) {
        DEFluidClientExtensions.register(event);
        event.registerItem(new IClientItemExtensions() {

            private BlockEntityWithoutLevelRenderer renderer;

            @Override
            public BlockEntityWithoutLevelRenderer getCustomRenderer() {
                if (this.renderer == null) {
                    this.renderer = MeVacuumItemRenderer.create();
                }
                return this.renderer;
            }
        }, DEItems.ME_VACUUM.get());
        event.registerItem(new IClientItemExtensions() {

            private BlockEntityWithoutLevelRenderer renderer;

            @Override
            public BlockEntityWithoutLevelRenderer getCustomRenderer() {
                if (this.renderer == null) {
                    this.renderer = OrderPackageItemRenderer.create();
                }
                return this.renderer;
            }
        }, DEItems.ORDER_PACKAGE.get());
    }
}

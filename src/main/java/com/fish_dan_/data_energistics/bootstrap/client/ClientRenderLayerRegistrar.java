package com.fish_dan_.data_energistics.bootstrap.client;

import com.fish_dan_.data_energistics.registry.DEFluids;

import net.minecraft.client.renderer.ItemBlockRenderTypes;
import net.minecraft.client.renderer.RenderType;

final class ClientRenderLayerRegistrar {

    private ClientRenderLayerRegistrar() {}

    static void register() {
        RenderType translucent = RenderType.translucent();
        ItemBlockRenderTypes.setRenderLayer(DEFluids.ENDER.get(), translucent);
        ItemBlockRenderTypes.setRenderLayer(DEFluids.FLOWING_ENDER.get(), translucent);
        ItemBlockRenderTypes.setRenderLayer(DEFluids.DATA_CORROSION_LIQUID.get(), translucent);
        ItemBlockRenderTypes.setRenderLayer(DEFluids.FLOWING_DATA_CORROSION_LIQUID.get(), translucent);
    }
}

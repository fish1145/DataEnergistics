package com.fish_dan_.data_energistics.bootstrap.client;

import com.fish_dan_.data_energistics.client.render.DataChargerRenderer;
import com.fish_dan_.data_energistics.client.render.DataDistributionTowerRenderer;
import com.fish_dan_.data_energistics.client.render.DataExtractorRenderer;
import com.fish_dan_.data_energistics.client.render.DataMimeticFieldRenderer;
import com.fish_dan_.data_energistics.client.render.DataSanctumRenderer;
import com.fish_dan_.data_energistics.client.render.DataSanctumReturnPortalRenderer;
import com.fish_dan_.data_energistics.client.render.DispersingDataRenderer;
import com.fish_dan_.data_energistics.client.render.LightBladeChargeRenderer;
import com.fish_dan_.data_energistics.client.render.MatterConvergingBoltRenderer;
import com.fish_dan_.data_energistics.client.render.ThrownLightSaberRenderer;
import com.fish_dan_.data_energistics.registry.ModBlockEntities;
import com.fish_dan_.data_energistics.registry.ModEntities;

import net.minecraft.client.renderer.entity.TntRenderer;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;

final class ClientRendererRegistrar {

    private ClientRendererRegistrar() {}

    static void register(EntityRenderersEvent.RegisterRenderers event) {
        event.registerBlockEntityRenderer(ModBlockEntities.DATA_EXTRACTOR_BLOCK_ENTITY.get(), DataExtractorRenderer::new);
        event.registerBlockEntityRenderer(ModBlockEntities.DATA_DISTRIBUTION_TOWER_BLOCK_ENTITY.get(), DataDistributionTowerRenderer::new);
        event.registerBlockEntityRenderer(ModBlockEntities.DATA_MIMETIC_FIELD_BLOCK_ENTITY.get(), DataMimeticFieldRenderer::new);
        event.registerBlockEntityRenderer(ModBlockEntities.DATA_SANCTUM_BLOCK_ENTITY.get(), DataSanctumRenderer::new);
        event.registerBlockEntityRenderer(ModBlockEntities.DATA_SANCTUM_RETURN_PORTAL_BLOCK_ENTITY.get(), DataSanctumReturnPortalRenderer::new);
        event.registerBlockEntityRenderer(ModBlockEntities.DATA_CHARGER_BLOCK_ENTITY.get(), DataChargerRenderer::new);
        event.registerEntityRenderer(ModEntities.DISPERSING_DATA.get(), DispersingDataRenderer::new);
        event.registerEntityRenderer(ModEntities.LIGHT_BLADE_CHARGE.get(), LightBladeChargeRenderer::new);
        event.registerEntityRenderer(ModEntities.MATTER_CONVERGING_BOLT.get(), MatterConvergingBoltRenderer::new);
        event.registerEntityRenderer(ModEntities.THROWN_LIGHT_SABER.get(), ThrownLightSaberRenderer::new);
        event.registerEntityRenderer(ModEntities.TNT_CONFIGURABLE_PRIMED.get(), TntRenderer::new);
        event.registerEntityRenderer(ModEntities.DATA_NUKE_PRIMED.get(), TntRenderer::new);
    }
}

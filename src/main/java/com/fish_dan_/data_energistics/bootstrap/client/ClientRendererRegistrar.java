package com.fish_dan_.data_energistics.bootstrap.client;

import com.fish_dan_.data_energistics.client.render.blockentity.DataChargerRenderer;
import com.fish_dan_.data_energistics.client.render.blockentity.DataDistributionTowerRenderer;
import com.fish_dan_.data_energistics.client.render.blockentity.DataExtractorRenderer;
import com.fish_dan_.data_energistics.client.render.blockentity.DataMimeticFieldRenderer;
import com.fish_dan_.data_energistics.client.render.blockentity.DataSanctumRenderer;
import com.fish_dan_.data_energistics.client.render.blockentity.DataSanctumReturnPortalRenderer;
import com.fish_dan_.data_energistics.client.render.entity.DataNukeRenderer;
import com.fish_dan_.data_energistics.client.render.entity.DispersingDataRenderer;
import com.fish_dan_.data_energistics.client.render.entity.LightBladeChargeRenderer;
import com.fish_dan_.data_energistics.client.render.entity.MatterConvergingBoltRenderer;
import com.fish_dan_.data_energistics.client.render.entity.ThrownLightSaberRenderer;
import com.fish_dan_.data_energistics.registry.DEBlockEntities;
import com.fish_dan_.data_energistics.registry.DEEntities;

import net.minecraft.client.renderer.entity.TntRenderer;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;

final class ClientRendererRegistrar {

    private ClientRendererRegistrar() {}

    static void register(EntityRenderersEvent.RegisterRenderers event) {
        event.registerBlockEntityRenderer(DEBlockEntities.DATA_EXTRACTOR_BLOCK_ENTITY.get(), DataExtractorRenderer::new);
        event.registerBlockEntityRenderer(DEBlockEntities.DATA_DISTRIBUTION_TOWER_BLOCK_ENTITY.get(), DataDistributionTowerRenderer::new);
        event.registerBlockEntityRenderer(DEBlockEntities.DATA_MIMETIC_FIELD_BLOCK_ENTITY.get(), DataMimeticFieldRenderer::new);
        event.registerBlockEntityRenderer(DEBlockEntities.DATA_SANCTUM_BLOCK_ENTITY.get(), DataSanctumRenderer::new);
        event.registerBlockEntityRenderer(DEBlockEntities.DATA_SANCTUM_RETURN_PORTAL_BLOCK_ENTITY.get(), DataSanctumReturnPortalRenderer::new);
        event.registerBlockEntityRenderer(DEBlockEntities.DATA_CHARGER_BLOCK_ENTITY.get(), DataChargerRenderer::new);
        event.registerEntityRenderer(DEEntities.DISPERSING_DATA.get(), DispersingDataRenderer::new);
        event.registerEntityRenderer(DEEntities.LIGHT_BLADE_CHARGE.get(), LightBladeChargeRenderer::new);
        event.registerEntityRenderer(DEEntities.MATTER_CONVERGING_BOLT.get(), MatterConvergingBoltRenderer::new);
        event.registerEntityRenderer(DEEntities.THROWN_LIGHT_SABER.get(), ThrownLightSaberRenderer::new);
        event.registerEntityRenderer(DEEntities.TNT_CONFIGURABLE_PRIMED.get(), TntRenderer::new);
        event.registerEntityRenderer(DEEntities.DATA_NUKE_PRIMED.get(), DataNukeRenderer::new);
    }
}

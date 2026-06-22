package com.fish_dan_.data_energistics.bootstrap.client;

import com.fish_dan_.data_energistics.Data_Energistics;
import com.fish_dan_.data_energistics.client.render.DataMeteoriteCompassBakedModel;
import com.fish_dan_.data_energistics.client.render.DataSanctumRenderer;

import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.client.resources.model.ModelResourceLocation;
import net.neoforged.neoforge.client.event.ModelEvent;

import appeng.client.render.model.MeteoriteCompassBakedModel;

final class ClientModelRegistrar {

    private ClientModelRegistrar() {}

    static void registerAdditionalModels(ModelEvent.RegisterAdditional event) {
        event.register(ModelResourceLocation.standalone(Data_Energistics.id("block/drive/cells/mob_data_carrier")));
        event.register(ModelResourceLocation.standalone(Data_Energistics.id("block/drive/cells/ore_data_carrier")));
        event.register(ModelResourceLocation.standalone(Data_Energistics.id("block/drive/cells/crop_data_carrier")));
        event.register(ModelResourceLocation.standalone(Data_Energistics.id("block/data_distribution_tower_crystal_off")));
        event.register(ModelResourceLocation.standalone(Data_Energistics.id("block/data_distribution_tower_crystal_on")));
        event.register(ModelResourceLocation.standalone(Data_Energistics.id("item/data_meteorite_compass_base")));
        event.register(ModelResourceLocation.standalone(Data_Energistics.id("item/data_meteorite_compass_pointer")));
        event.register(DataSanctumRenderer.BLACK_HOLE_MODEL);
        event.register(DataSanctumRenderer.PORTAL_MODEL);
    }

    static void modifyBakingResult(ModelEvent.ModifyBakingResult event) {
        ModelResourceLocation compass = ModelResourceLocation.inventory(Data_Energistics.id("data_meteorite_compass"));
        ModelResourceLocation base = ModelResourceLocation.standalone(Data_Energistics.id("item/data_meteorite_compass_base"));
        ModelResourceLocation pointer = ModelResourceLocation.standalone(Data_Energistics.id("item/data_meteorite_compass_pointer"));

        BakedModel baseModel = event.getModels().get(base);
        BakedModel pointerModel = event.getModels().get(pointer);
        if (baseModel != null && pointerModel != null) {
            event.getModels().put(compass, new DataMeteoriteCompassBakedModel(
                    new MeteoriteCompassBakedModel(baseModel, pointerModel)));
        }
    }
}

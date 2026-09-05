package com.fish_dan_.data_energistics.bootstrap.client;

import com.fish_dan_.data_energistics.Data_Energistics;
import com.fish_dan_.data_energistics.client.render.blockentity.DataSanctumRenderer;
import com.fish_dan_.data_energistics.client.render.item.DataMeteoriteCompassBakedModel;
import com.fish_dan_.data_energistics.client.render.item.MeVacuumBakedModel;
import com.fish_dan_.data_energistics.client.render.item.OrderPackageBakedModel;
import com.fish_dan_.data_energistics.client.render.item.OrderPackageItemRenderer;
import com.fish_dan_.data_energistics.registry.DEStorageCells;

import appeng.api.client.StorageCellModels;
import appeng.client.render.model.MeteoriteCompassBakedModel;

import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.client.resources.model.ModelResourceLocation;
import net.neoforged.neoforge.client.event.ModelEvent;

final class ClientModelRegistrar {

    private ClientModelRegistrar() {}

    static void registerAdditionalModels(ModelEvent.RegisterAdditional event) {
        event.register(ModelResourceLocation.standalone(StorageCellModels.getDefaultModel()));
        StorageCellModels.models().values()
                .forEach(model -> event.register(ModelResourceLocation.standalone(model)));
        event.register(ModelResourceLocation.standalone(DEStorageCells.DRIVE_1K));
        event.register(ModelResourceLocation.standalone(DEStorageCells.DRIVE_4K));
        event.register(ModelResourceLocation.standalone(DEStorageCells.DRIVE_16K));
        event.register(ModelResourceLocation.standalone(DEStorageCells.DRIVE_64K));
        event.register(ModelResourceLocation.standalone(DEStorageCells.DRIVE_256K));
        event.register(ModelResourceLocation.standalone(DEStorageCells.DRIVE_INFINITY));
        event.register(ModelResourceLocation.standalone(Data_Energistics.id("block/drive/cells/mob_data_carrier")));
        event.register(ModelResourceLocation.standalone(Data_Energistics.id("block/drive/cells/ore_data_carrier")));
        event.register(ModelResourceLocation.standalone(Data_Energistics.id("block/drive/cells/crop_data_carrier")));
        event.register(ModelResourceLocation.standalone(Data_Energistics.id("block/data_distribution_tower_crystal_off")));
        event.register(ModelResourceLocation.standalone(Data_Energistics.id("block/data_distribution_tower_crystal_on")));
        event.register(ModelResourceLocation.standalone(Data_Energistics.id("item/data_meteorite_compass_base")));
        event.register(ModelResourceLocation.standalone(Data_Energistics.id("item/data_meteorite_compass_pointer")));
        event.register(DataSanctumRenderer.BLACK_HOLE_MODEL);
        event.register(DataSanctumRenderer.PORTAL_MODEL);
        event.register(OrderPackageItemRenderer.MARKED_BADGE_MODEL);
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

        ModelResourceLocation meVacuum = ModelResourceLocation.inventory(Data_Energistics.id("me_vacuum"));
        BakedModel meVacuumModel = event.getModels().get(meVacuum);
        if (meVacuumModel != null && !(meVacuumModel instanceof MeVacuumBakedModel)) {
            event.getModels().put(meVacuum, new MeVacuumBakedModel(meVacuumModel));
        }

        ModelResourceLocation orderPackage = ModelResourceLocation.inventory(Data_Energistics.id("order_package"));
        BakedModel orderPackageModel = event.getModels().get(orderPackage);
        if (orderPackageModel != null && !(orderPackageModel instanceof OrderPackageBakedModel)) {
            event.getModels().put(orderPackage, new OrderPackageBakedModel(orderPackageModel));
        }
    }
}

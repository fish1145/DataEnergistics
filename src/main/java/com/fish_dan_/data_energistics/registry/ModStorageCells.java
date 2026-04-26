package com.fish_dan_.data_energistics.registry;

import com.fish_dan_.data_energistics.Data_Energistics;
import appeng.api.client.StorageCellModels;
import net.minecraft.resources.ResourceLocation;

public final class ModStorageCells {
    public static final ResourceLocation DRIVE_1K = model("1k");
    public static final ResourceLocation DRIVE_4K = model("4k");
    public static final ResourceLocation DRIVE_16K = model("16k");
    public static final ResourceLocation DRIVE_64K = model("64k");
    public static final ResourceLocation DRIVE_256K = model("256k");

    private ModStorageCells() {
    }

    public static void registerClientModels() {
        StorageCellModels.registerModel(ModItems.DATA_FLOW_CELL_1K.get(), DRIVE_1K);
        StorageCellModels.registerModel(ModItems.DATA_FLOW_CELL_4K.get(), DRIVE_4K);
        StorageCellModels.registerModel(ModItems.DATA_FLOW_CELL_16K.get(), DRIVE_16K);
        StorageCellModels.registerModel(ModItems.DATA_FLOW_CELL_64K.get(), DRIVE_64K);
        StorageCellModels.registerModel(ModItems.DATA_FLOW_CELL_256K.get(), DRIVE_256K);

        StorageCellModels.registerModel(ModItems.PORTABLE_DATA_FLOW_CELL_1K.get(), DRIVE_1K);
        StorageCellModels.registerModel(ModItems.PORTABLE_DATA_FLOW_CELL_4K.get(), DRIVE_4K);
        StorageCellModels.registerModel(ModItems.PORTABLE_DATA_FLOW_CELL_16K.get(), DRIVE_16K);
        StorageCellModels.registerModel(ModItems.PORTABLE_DATA_FLOW_CELL_64K.get(), DRIVE_64K);
        StorageCellModels.registerModel(ModItems.PORTABLE_DATA_FLOW_CELL_256K.get(), DRIVE_256K);
    }

    private static ResourceLocation model(String tier) {
        return ResourceLocation.fromNamespaceAndPath(Data_Energistics.MODID, "block/drive/cells/" + tier + "_data_flow_cell");
    }
}

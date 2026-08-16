package com.fish_dan_.data_energistics.registry;

import com.fish_dan_.data_energistics.Data_Energistics;

import net.minecraft.resources.ResourceLocation;

import appeng.api.client.StorageCellModels;

public final class DEStorageCells {

    public static final ResourceLocation DRIVE_1K = model("1k");
    public static final ResourceLocation DRIVE_4K = model("4k");
    public static final ResourceLocation DRIVE_16K = model("16k");
    public static final ResourceLocation DRIVE_64K = model("64k");
    public static final ResourceLocation DRIVE_256K = model("256k");
    public static final ResourceLocation DRIVE_1M = model("1m");
    public static final ResourceLocation DRIVE_4M = model("4m");
    public static final ResourceLocation DRIVE_16M = model("16m");
    public static final ResourceLocation DRIVE_64M = model("64m");
    public static final ResourceLocation DRIVE_256M = model("256m");
    public static final ResourceLocation DRIVE_INFINITY = ResourceLocation.fromNamespaceAndPath(Data_Energistics.MODID, "block/drive/cells/data_cell_infinity");

    private DEStorageCells() {}

    public static void registerClientModels() {
        StorageCellModels.registerModel(DEItems.DIGITAL_STORAGE_CELL_1K.get(), DRIVE_1K);
        StorageCellModels.registerModel(DEItems.DIGITAL_STORAGE_CELL_4K.get(), DRIVE_4K);
        StorageCellModels.registerModel(DEItems.DIGITAL_STORAGE_CELL_16K.get(), DRIVE_16K);
        StorageCellModels.registerModel(DEItems.DIGITAL_STORAGE_CELL_64K.get(), DRIVE_64K);
        StorageCellModels.registerModel(DEItems.DIGITAL_STORAGE_CELL_256K.get(), DRIVE_256K);
        StorageCellModels.registerModel(DEItems.DIGITAL_STORAGE_CELL_1M.get(), DRIVE_1M);
        StorageCellModels.registerModel(DEItems.DIGITAL_STORAGE_CELL_4M.get(), DRIVE_4M);
        StorageCellModels.registerModel(DEItems.DIGITAL_STORAGE_CELL_16M.get(), DRIVE_16M);
        StorageCellModels.registerModel(DEItems.DIGITAL_STORAGE_CELL_64M.get(), DRIVE_64M);
        StorageCellModels.registerModel(DEItems.DIGITAL_STORAGE_CELL_256M.get(), DRIVE_256M);
        StorageCellModels.registerModel(DEItems.DATA_CELL_INFINITY.get(), DRIVE_INFINITY);

        StorageCellModels.registerModel(DEItems.PORTABLE_DIGITAL_STORAGE_CELL_1K.get(), DRIVE_1K);
        StorageCellModels.registerModel(DEItems.PORTABLE_DIGITAL_STORAGE_CELL_4K.get(), DRIVE_4K);
        StorageCellModels.registerModel(DEItems.PORTABLE_DIGITAL_STORAGE_CELL_16K.get(), DRIVE_16K);
        StorageCellModels.registerModel(DEItems.PORTABLE_DIGITAL_STORAGE_CELL_64K.get(), DRIVE_64K);
        StorageCellModels.registerModel(DEItems.PORTABLE_DIGITAL_STORAGE_CELL_256K.get(), DRIVE_256K);
        StorageCellModels.registerModel(DEItems.PORTABLE_DIGITAL_STORAGE_CELL_1M.get(), DRIVE_1M);
        StorageCellModels.registerModel(DEItems.PORTABLE_DIGITAL_STORAGE_CELL_4M.get(), DRIVE_4M);
        StorageCellModels.registerModel(DEItems.PORTABLE_DIGITAL_STORAGE_CELL_16M.get(), DRIVE_16M);
        StorageCellModels.registerModel(DEItems.PORTABLE_DIGITAL_STORAGE_CELL_64M.get(), DRIVE_64M);
        StorageCellModels.registerModel(DEItems.PORTABLE_DIGITAL_STORAGE_CELL_256M.get(), DRIVE_256M);
    }

    private static ResourceLocation model(String tier) {
        return ResourceLocation.fromNamespaceAndPath(Data_Energistics.MODID, "block/drive/cells/" + tier + "_digital_storage_cell");
    }
}

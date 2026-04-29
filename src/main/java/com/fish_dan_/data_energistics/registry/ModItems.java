package com.fish_dan_.data_energistics.registry;

import com.fish_dan_.data_energistics.ae2.DataFlowKey;
import com.fish_dan_.data_energistics.Data_Energistics;
import com.fish_dan_.data_energistics.item.BiologyDataCarrierItem;
import com.fish_dan_.data_energistics.item.DataFlowPortableCellItem;
import com.fish_dan_.data_energistics.item.DataFlowStorageCellItem;
import com.fish_dan_.data_energistics.item.DataRipperPartItem;
import appeng.api.stacks.GenericStack;
import appeng.items.storage.StorageTier;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class ModItems {
    public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(Data_Energistics.MODID);

    public static final DeferredItem<DataFlowStorageCellItem> DATA_FLOW_CELL_1K = registerDataFlowCell("data_flow_cell_1k", 0.5, 1);
    public static final DeferredItem<DataFlowStorageCellItem> DATA_FLOW_CELL_4K = registerDataFlowCell("data_flow_cell_4k", 1.0, 4);
    public static final DeferredItem<DataFlowStorageCellItem> DATA_FLOW_CELL_16K = registerDataFlowCell("data_flow_cell_16k", 1.5, 16);
    public static final DeferredItem<DataFlowStorageCellItem> DATA_FLOW_CELL_64K = registerDataFlowCell("data_flow_cell_64k", 2.5, 65);
    public static final DeferredItem<DataFlowStorageCellItem> DATA_FLOW_CELL_256K = registerDataFlowCell("data_flow_cell_256k", 3.0, 262);

    public static final DeferredItem<DataFlowPortableCellItem> PORTABLE_DATA_FLOW_CELL_1K =
            registerPortableDataFlowCell("portable_data_flow_cell_1k", StorageTier.SIZE_1K, 0x4FD8FF);
    public static final DeferredItem<DataFlowPortableCellItem> PORTABLE_DATA_FLOW_CELL_4K =
            registerPortableDataFlowCell("portable_data_flow_cell_4k", StorageTier.SIZE_4K, 0x56F0B5);
    public static final DeferredItem<DataFlowPortableCellItem> PORTABLE_DATA_FLOW_CELL_16K =
            registerPortableDataFlowCell("portable_data_flow_cell_16k", StorageTier.SIZE_16K, 0xA0EE68);
    public static final DeferredItem<DataFlowPortableCellItem> PORTABLE_DATA_FLOW_CELL_64K =
            registerPortableDataFlowCell("portable_data_flow_cell_64k", StorageTier.SIZE_64K, 0xFF9B5C);
    public static final DeferredItem<DataFlowPortableCellItem> PORTABLE_DATA_FLOW_CELL_256K =
            registerPortableDataFlowCell("portable_data_flow_cell_256k", StorageTier.SIZE_256K, 0xFF72C8);

    public static final DeferredItem<BlockItem> DATA_FLOW_GENERATOR = ITEMS.registerSimpleBlockItem(ModBlocks.DATA_FLOW_GENERATOR);
    public static final DeferredItem<BlockItem> DATA_EXTRACTOR = ITEMS.registerSimpleBlockItem(ModBlocks.DATA_EXTRACTOR);
    public static final DeferredItem<BlockItem> DATA_FRAMEWORK = ITEMS.registerSimpleBlockItem(ModBlocks.DATA_FRAMEWORK);
    public static final DeferredItem<BlockItem> DATA_DISTRIBUTION_TOWER = ITEMS.registerSimpleBlockItem(ModBlocks.DATA_DISTRIBUTION_TOWER);
    public static final DeferredItem<Item> REDSTONE_ALLOY = ITEMS.registerSimpleItem("redstone_alloy");
    public static final DeferredItem<Item> SOLIDIFIED_OBSIDIAN = ITEMS.registerSimpleItem("solidified_obsidian");
    public static final DeferredItem<Item> MIXED_REDSTONE_DUST = ITEMS.registerSimpleItem("mixed_redstone_dust");
    public static final DeferredItem<Item> OBSIDIAN_DUST = ITEMS.registerSimpleItem("obsidian_dust");
    public static final DeferredItem<Item> DATA_CARRIER = ITEMS.register("data_carrier",
            () -> new BiologyDataCarrierItem(new Item.Properties(), false));
    public static final DeferredItem<Item> BIOLOGY_DATA_CARRIER = ITEMS.register("biology_data_carrier",
            () -> new BiologyDataCarrierItem(new Item.Properties(), true));
    public static final DeferredItem<Item> TIME_CORE = ITEMS.registerSimpleItem("time_core");
    public static final DeferredItem<Item> DATA_FLOW_COMPONENT_HOUSING = ITEMS.registerSimpleItem("data_flow_component_housing");
    public static final DeferredItem<Item> DATA_INSCRIBER_TEMPLATE = ITEMS.registerSimpleItem("data_inscriber_template");
    public static final DeferredItem<Item> DATA_CIRCUIT_BOARD = ITEMS.registerSimpleItem("data_circuit_board");
    public static final DeferredItem<Item> DATA_PROCESSOR = ITEMS.registerSimpleItem("data_processor");
    public static final DeferredItem<Item> DATA_STORAGE_COMPONENT_1K = ITEMS.registerSimpleItem("data_storage_component_1k");
    public static final DeferredItem<Item> DATA_STORAGE_COMPONENT_4K = ITEMS.registerSimpleItem("data_storage_component_4k");
    public static final DeferredItem<Item> DATA_STORAGE_COMPONENT_16K = ITEMS.registerSimpleItem("data_storage_component_16k");
    public static final DeferredItem<Item> DATA_STORAGE_COMPONENT_64K = ITEMS.registerSimpleItem("data_storage_component_64k");
    public static final DeferredItem<Item> DATA_STORAGE_COMPONENT_256K = ITEMS.registerSimpleItem("data_storage_component_256k");
    public static final DeferredItem<DataRipperPartItem> DATA_RIPPER = ITEMS.register("data_ripper",
            () -> new DataRipperPartItem(new Item.Properties()));

    private ModItems() {
    }

    public static void register(IEventBus modEventBus) {
        ITEMS.register(modEventBus);
    }

    public static ItemStack wrappedDataFlow() {
        return GenericStack.wrapInItemStack(DataFlowKey.of(), 1);
    }

    private static DeferredItem<DataFlowStorageCellItem> registerDataFlowCell(String id, double idleDrain, int bytes) {
        return ITEMS.register(id, () -> new DataFlowStorageCellItem(new Item.Properties(), idleDrain, bytes));
    }

    private static DeferredItem<DataFlowPortableCellItem> registerPortableDataFlowCell(String id, StorageTier tier, int color) {
        return ITEMS.register(id, () -> new DataFlowPortableCellItem(tier, new Item.Properties(), color));
    }
}

package com.fish_dan_.data_energistics.registry;

import com.fish_dan_.data_energistics.ae2.DataFlowKey;
import com.fish_dan_.data_energistics.Data_Energistics;
import com.fish_dan_.data_energistics.item.DataFlowPortableCellItem;
import com.fish_dan_.data_energistics.item.DataFlowStorageCellItem;
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

    public static final DeferredItem<DataFlowStorageCellItem> DATA_FLOW_CELL_1K = registerDataFlowCell("data_flow_cell_1k", 0.5, 1024);
    public static final DeferredItem<DataFlowStorageCellItem> DATA_FLOW_CELL_4K = registerDataFlowCell("data_flow_cell_4k", 1.0, 4096);
    public static final DeferredItem<DataFlowStorageCellItem> DATA_FLOW_CELL_16K = registerDataFlowCell("data_flow_cell_16k", 1.5, 16384);
    public static final DeferredItem<DataFlowStorageCellItem> DATA_FLOW_CELL_64K = registerDataFlowCell("data_flow_cell_64k", 2.5, 65536);
    public static final DeferredItem<DataFlowStorageCellItem> DATA_FLOW_CELL_256K = registerDataFlowCell("data_flow_cell_256k", 3.0, 262144);

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
    public static final DeferredItem<BlockItem> DATA_FRAMEWORK = ITEMS.registerSimpleBlockItem(ModBlocks.DATA_FRAMEWORK);
    public static final DeferredItem<Item> SOLIDIFY_DATA = ITEMS.registerSimpleItem("solidify_data");
    public static final DeferredItem<Item> DATA_CARRIER = ITEMS.registerSimpleItem("data_carrier");

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

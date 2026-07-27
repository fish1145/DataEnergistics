package com.fish_dan_.data_energistics.common;

import com.fish_dan_.data_energistics.block.DataDistributionTowerBlock;
import com.fish_dan_.data_energistics.block.DataSanctumBlock;
import com.fish_dan_.data_energistics.blockentity.DataDistributionTowerBlockEntity;
import com.fish_dan_.data_energistics.blockentity.DataSanctumBlockEntity;
import com.fish_dan_.data_energistics.integration.ModFlags;
import com.fish_dan_.data_energistics.integration.appmek.AppMekCompat;
import com.fish_dan_.data_energistics.item.DigitalStorageDepotBlockItem;
import com.fish_dan_.data_energistics.item.DigitalStorageDepotFluidHandlerItem;
import com.fish_dan_.data_energistics.item.PoweredItemEnergyStorage;
import com.fish_dan_.data_energistics.part.AdaptivePatternProviderPart;
import com.fish_dan_.data_energistics.registry.ModBlockEntities;
import com.fish_dan_.data_energistics.registry.ModBlocks;
import com.fish_dan_.data_energistics.registry.ModItems;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent;

import appeng.api.AECapabilities;
import appeng.api.implementations.items.IAEItemPowerStorage;
import appeng.blockentity.grid.AENetworkedPoweredBlockEntity;
import appeng.blockentity.networking.CableBusBlockEntity;
import appeng.blockentity.networking.ControllerBlockEntity;
import appeng.core.definitions.AEBlockEntities;

final class CommonCapabilityRegistrar {

    private CommonCapabilityRegistrar() {}

    static void register(RegisterCapabilitiesEvent event) {
        event.registerBlockEntity(
                AECapabilities.IN_WORLD_GRID_NODE_HOST,
                ModBlockEntities.DATA_SOLAR_PANEL_BLOCK_ENTITY.get(),
                (blockEntity, context) -> blockEntity);
        event.registerBlockEntity(
                AECapabilities.IN_WORLD_GRID_NODE_HOST,
                ModBlockEntities.DATA_CHARGER_BLOCK_ENTITY.get(),
                (blockEntity, context) -> blockEntity);
        event.registerBlockEntity(
                Capabilities.ItemHandler.BLOCK,
                ModBlockEntities.DATA_CHARGER_BLOCK_ENTITY.get(),
                (blockEntity, context) -> blockEntity.getExternalItemHandler());
        event.registerBlockEntity(
                Capabilities.EnergyStorage.BLOCK,
                ModBlockEntities.DATA_CHARGER_BLOCK_ENTITY.get(),
                (blockEntity, context) -> blockEntity.getEnergyStorage(context));
        event.registerBlockEntity(
                AECapabilities.IN_WORLD_GRID_NODE_HOST,
                ModBlockEntities.DIGITAL_STORAGE_DEPOT_BLOCK_ENTITY.get(),
                (blockEntity, context) -> blockEntity);
        event.registerBlockEntity(
                AECapabilities.GENERIC_INTERNAL_INV,
                ModBlockEntities.DIGITAL_STORAGE_DEPOT_BLOCK_ENTITY.get(),
                (blockEntity, context) -> blockEntity.getExternalKeyInventory());
        event.registerBlockEntity(
                AECapabilities.IN_WORLD_GRID_NODE_HOST,
                ModBlockEntities.DATA_EXTRACTOR_BLOCK_ENTITY.get(),
                (blockEntity, context) -> blockEntity);
        event.registerBlockEntity(
                AECapabilities.IN_WORLD_GRID_NODE_HOST,
                ModBlockEntities.DATA_RIPPER_REASSEMBLER_BLOCK_ENTITY.get(),
                (blockEntity, context) -> blockEntity);
        event.registerBlockEntity(
                AECapabilities.CRAFTING_MACHINE,
                ModBlockEntities.DATA_RIPPER_REASSEMBLER_BLOCK_ENTITY.get(),
                (blockEntity, context) -> blockEntity);
        event.registerBlockEntity(
                AECapabilities.ME_STORAGE,
                ModBlockEntities.DATA_RIPPER_REASSEMBLER_BLOCK_ENTITY.get(),
                (blockEntity, context) -> blockEntity.getExternalPatternInputStorage());
        event.registerBlockEntity(
                AECapabilities.GENERIC_INTERNAL_INV,
                ModBlockEntities.DATA_RIPPER_REASSEMBLER_BLOCK_ENTITY.get(),
                (blockEntity, context) -> blockEntity.getExternalKeyInventory());
        event.registerBlockEntity(
                Capabilities.ItemHandler.BLOCK,
                ModBlockEntities.DIGITAL_STORAGE_DEPOT_BLOCK_ENTITY.get(),
                (blockEntity, context) -> blockEntity.getExternalItemHandler());
        event.registerBlockEntity(
                Capabilities.FluidHandler.BLOCK,
                ModBlockEntities.DIGITAL_STORAGE_DEPOT_BLOCK_ENTITY.get(),
                (blockEntity, context) -> blockEntity.getExternalFluidHandler());
        event.registerItem(
                Capabilities.FluidHandler.ITEM,
                (stack, context) -> DigitalStorageDepotBlockItem.isBucketMode(stack) && !DigitalStorageDepotBlockItem.isKeySlotMarked(stack) ? new DigitalStorageDepotFluidHandlerItem(stack) : null,
                ModItems.DIGITAL_STORAGE_DEPOT.get());
        registerPoweredItemEnergyStorage(event, ModItems.DATA_CRYSTAL_SWORD.get());
        registerPoweredItemEnergyStorage(event, ModItems.DATA_CRYSTAL_AXE.get());
        registerPoweredItemEnergyStorage(event, ModItems.DATA_CRYSTAL_PICKAXE.get());
        registerPoweredItemEnergyStorage(event, ModItems.DATA_CRYSTAL_HOE.get());
        registerPoweredItemEnergyStorage(event, ModItems.DATA_CRYSTAL_SHOVEL.get());
        registerPoweredItemEnergyStorage(event, ModItems.DATA_CRYSTAL_CUTTING_KNIFE.get());
        registerPoweredItemEnergyStorage(event, ModItems.DATA_LIGHT_SABER.get());
        registerPoweredItemEnergyStorage(event, ModItems.DATA_SANCTIFIER.get());
        registerPoweredItemEnergyStorage(event, ModItems.PORTABLE_DATA_FLOW_CELL_1K.get());
        registerPoweredItemEnergyStorage(event, ModItems.PORTABLE_DATA_FLOW_CELL_4K.get());
        registerPoweredItemEnergyStorage(event, ModItems.PORTABLE_DATA_FLOW_CELL_16K.get());
        registerPoweredItemEnergyStorage(event, ModItems.PORTABLE_DATA_FLOW_CELL_64K.get());
        registerPoweredItemEnergyStorage(event, ModItems.PORTABLE_DATA_FLOW_CELL_256K.get());
        registerPoweredItemEnergyStorage(event, ModItems.PORTABLE_DATA_FLOW_CELL_1M.get());
        registerPoweredItemEnergyStorage(event, ModItems.PORTABLE_DATA_FLOW_CELL_4M.get());
        registerPoweredItemEnergyStorage(event, ModItems.PORTABLE_DATA_FLOW_CELL_16M.get());
        registerPoweredItemEnergyStorage(event, ModItems.PORTABLE_DATA_FLOW_CELL_64M.get());
        registerPoweredItemEnergyStorage(event, ModItems.PORTABLE_DATA_FLOW_CELL_256M.get());
        registerPoweredItemEnergyStorage(event, ModItems.DATA_CAPTURE_BALL.get());
        registerPoweredItemEnergyStorage(event, ModItems.MATTER_CONVERGING_CROSSBOW.get());
        event.registerBlockEntity(
                Capabilities.ItemHandler.BLOCK,
                ModBlockEntities.DATA_EXTRACTOR_BLOCK_ENTITY.get(),
                (blockEntity, context) -> blockEntity.getExternalInventory().toItemHandler());
        event.registerBlockEntity(
                Capabilities.ItemHandler.BLOCK,
                ModBlockEntities.DATA_RIPPER_REASSEMBLER_BLOCK_ENTITY.get(),
                (blockEntity, context) -> blockEntity.getExternalInventory().toItemHandler());
        event.registerBlockEntity(
                Capabilities.FluidHandler.BLOCK,
                ModBlockEntities.DATA_RIPPER_REASSEMBLER_BLOCK_ENTITY.get(),
                (blockEntity, context) -> blockEntity.getExternalFluidHandler());
        event.registerBlockEntity(
                Capabilities.ItemHandler.BLOCK,
                ModBlockEntities.DATA_MIMETIC_FIELD_BLOCK_ENTITY.get(),
                (blockEntity, context) -> blockEntity.getInternalInventory().toItemHandler());
        event.registerBlockEntity(
                AECapabilities.GENERIC_INTERNAL_INV,
                ModBlockEntities.DATA_MIMETIC_FIELD_BLOCK_ENTITY.get(),
                (blockEntity, context) -> blockEntity.getExternalKeyInventory());
        event.registerBlockEntity(
                AECapabilities.IN_WORLD_GRID_NODE_HOST,
                ModBlockEntities.DATA_MIMETIC_FIELD_BLOCK_ENTITY.get(),
                (blockEntity, context) -> blockEntity);
        event.registerBlockEntity(
                AECapabilities.IN_WORLD_GRID_NODE_HOST,
                ModBlockEntities.DATA_DISTRIBUTION_TOWER_BLOCK_ENTITY.get(),
                (blockEntity, context) -> blockEntity);
        event.registerBlockEntity(
                AECapabilities.IN_WORLD_GRID_NODE_HOST,
                ModBlockEntities.DATA_TELEPORT_ANCHOR_BLOCK_ENTITY.get(),
                (blockEntity, context) -> blockEntity);
        event.registerBlock(
                AECapabilities.IN_WORLD_GRID_NODE_HOST,
                (level, pos, state, blockEntity, context) -> {
                    if (!(state.getBlock() instanceof DataSanctumBlock)) {
                        return null;
                    }

                    if (!DataSanctumBlockEntity.isNetworkPortPart(state)) {
                        return null;
                    }

                    DataSanctumBlockEntity sanctum = DataSanctumBlock.getMainBlockEntity(level, pos, state);
                    return sanctum != null ? sanctum.createNetworkPortHost() : null;
                },
                ModBlocks.DATA_SANCTUM.get());
        event.registerBlockEntity(
                AECapabilities.GENERIC_INTERNAL_INV,
                ModBlockEntities.DATA_SANCTUM_BLOCK_ENTITY.get(),
                (blockEntity, context) -> blockEntity.getReturnInventory());
        event.registerBlockEntity(
                AECapabilities.IN_WORLD_GRID_NODE_HOST,
                ModBlockEntities.DATA_SANCTUM_INTERFACE_BLOCK_ENTITY.get(),
                (blockEntity, context) -> blockEntity);
        event.registerBlockEntity(
                AECapabilities.GENERIC_INTERNAL_INV,
                ModBlockEntities.DATA_SANCTUM_INTERFACE_BLOCK_ENTITY.get(),
                (blockEntity, context) -> blockEntity.getReturnInventory());
        event.registerBlockEntity(
                AECapabilities.GENERIC_INTERNAL_INV,
                ModBlockEntities.ADAPTIVE_PATTERN_PROVIDER_BLOCK_ENTITY.get(),
                (blockEntity, context) -> {
                    var logic = blockEntity.getLogic();
                    return logic != null ? logic.getReturnInv() : null;
                });
        event.registerBlockEntity(
                AECapabilities.IN_WORLD_GRID_NODE_HOST,
                ModBlockEntities.ADAPTIVE_PATTERN_PROVIDER_BLOCK_ENTITY.get(),
                (blockEntity, context) -> blockEntity);
        event.registerBlockEntity(
                AECapabilities.IN_WORLD_GRID_NODE_HOST,
                ModBlockEntities.ME_COMPOSITE_INPUT_WAREHOUSE_BLOCK_ENTITY.get(),
                (blockEntity, context) -> blockEntity);
        event.registerBlockEntity(
                AECapabilities.IN_WORLD_GRID_NODE_HOST,
                ModBlockEntities.ME_COMPOSITE_OUTPUT_WAREHOUSE_BLOCK_ENTITY.get(),
                (blockEntity, context) -> blockEntity);
        event.registerBlockEntity(
                AECapabilities.IN_WORLD_GRID_NODE_HOST,
                ModBlockEntities.TRINITY_ACCESS_HATCH_BLOCK_ENTITY.get(),
                (blockEntity, context) -> blockEntity);
        event.registerBlockEntity(
                AECapabilities.ME_STORAGE,
                ModBlockEntities.ME_COMPOSITE_OUTPUT_WAREHOUSE_BLOCK_ENTITY.get(),
                (blockEntity, context) -> blockEntity.outputStorage());
        event.registerBlockEntity(
                Capabilities.ItemHandler.BLOCK,
                ModBlockEntities.ADAPTIVE_PATTERN_PROVIDER_BLOCK_ENTITY.get(),
                (blockEntity, context) -> blockEntity.getExternalReturnItemHandler(context));
        event.registerBlockEntity(
                Capabilities.FluidHandler.BLOCK,
                ModBlockEntities.ADAPTIVE_PATTERN_PROVIDER_BLOCK_ENTITY.get(),
                (blockEntity, context) -> blockEntity.getExternalReturnFluidHandler(context));
        if (ModFlags.isAppMekChemicalSupportLoaded()) {
            AppMekCompat.registerChemicalBlockEntityCapabilities(event);
        }
        event.registerBlockEntity(
                Capabilities.ItemHandler.BLOCK,
                AEBlockEntities.CABLE_BUS.get(),
                (CableBusBlockEntity blockEntity, Direction context) -> {
                    if (context == null) {
                        return null;
                    }

                    var part = blockEntity.getPart(context);
                    if (part instanceof AdaptivePatternProviderPart adaptivePart) {
                        return adaptivePart.getExternalReturnItemHandler();
                    }

                    return null;
                });
        event.registerBlockEntity(
                Capabilities.FluidHandler.BLOCK,
                AEBlockEntities.CABLE_BUS.get(),
                (CableBusBlockEntity blockEntity, Direction context) -> {
                    if (context == null) {
                        return null;
                    }

                    var part = blockEntity.getPart(context);
                    if (part instanceof AdaptivePatternProviderPart adaptivePart) {
                        return adaptivePart.getExternalReturnFluidHandler();
                    }

                    return null;
                });
        if (ModFlags.isAppMekChemicalSupportLoaded()) {
            AppMekCompat.registerChemicalCableBusCapabilities(event);
        }
        event.registerBlockEntity(
                AECapabilities.CRANKABLE,
                AEBlockEntities.CONTROLLER.get(),
                (ControllerBlockEntity blockEntity, Direction context) -> context != null ? ((AENetworkedPoweredBlockEntity) blockEntity).new Crankable() : null);
        event.registerBlock(
                Capabilities.EnergyStorage.BLOCK,
                (level, pos, state, blockEntity, context) -> {
                    if (!(state.getBlock() instanceof DataDistributionTowerBlock)) {
                        return null;
                    }

                    BlockPos basePos = DataDistributionTowerBlock.getBasePos(pos, state);
                    BlockState baseState = level.getBlockState(basePos);
                    if (!(baseState.getBlock() instanceof DataDistributionTowerBlock) || !(level.getBlockEntity(basePos) instanceof DataDistributionTowerBlockEntity tower)) {
                        return null;
                    }

                    return tower.getEnergyStorageForQuery(pos, context);
                },
                ModBlocks.DATA_DISTRIBUTION_TOWER.get());
    }

    private static void registerPoweredItemEnergyStorage(RegisterCapabilitiesEvent event, Item item) {
        event.registerItem(
                Capabilities.EnergyStorage.ITEM,
                (stack, context) -> item instanceof IAEItemPowerStorage powerStorage ? new PoweredItemEnergyStorage(stack, powerStorage) : null,
                item);
    }
}

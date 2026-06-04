package com.fish_dan_.data_energistics.common;

import com.fish_dan_.data_energistics.Data_Energistics;
import com.fish_dan_.data_energistics.ae2.ModAE2Keys;
import com.fish_dan_.data_energistics.ae2.GenericKeyItemExportStrategy;
import com.fish_dan_.data_energistics.block.DataDistributionTowerBlock;
import com.fish_dan_.data_energistics.blockentity.DataDistributionTowerBlockEntity;
import com.fish_dan_.data_energistics.blockentity.DataTeleportAnchorBlockEntity;
import com.fish_dan_.data_energistics.integration.AppMekCompat;
import com.fish_dan_.data_energistics.item.DataCrystalSwordAiStripLogic;
import com.fish_dan_.data_energistics.item.DigitalStorageDepotBlockItem;
import com.fish_dan_.data_energistics.item.DigitalStorageDepotFluidHandlerItem;
import com.fish_dan_.data_energistics.item.DigitalStorageDepotKeyContainerItemStrategy;
import com.fish_dan_.data_energistics.item.PersistentFarmlandLogic;
import com.fish_dan_.data_energistics.item.PoweredAxeItem;
import com.fish_dan_.data_energistics.item.PoweredEnergyItem;
import com.fish_dan_.data_energistics.item.PoweredHoeItem;
import com.fish_dan_.data_energistics.item.PoweredPickaxeItem;
import com.fish_dan_.data_energistics.item.PoweredShovelItem;
import com.fish_dan_.data_energistics.item.PoweredSwordItem;
import com.fish_dan_.data_energistics.network.ModPayloads;
import com.fish_dan_.data_energistics.part.AdaptivePatternProviderPart;
import com.fish_dan_.data_energistics.recipe.DataCaptureBallRightClickRecipeLogic;
import com.fish_dan_.data_energistics.recipe.TimeShiftTransformLogic;
import com.fish_dan_.data_energistics.registry.ModBlockEntities;
import com.fish_dan_.data_energistics.registry.ModBlocks;
import com.fish_dan_.data_energistics.registry.ModCreativeTabs;
import com.fish_dan_.data_energistics.registry.ModDataComponents;
import com.fish_dan_.data_energistics.registry.ModEntities;
import com.fish_dan_.data_energistics.registry.ModFluids;
import com.fish_dan_.data_energistics.registry.ModItems;
import com.fish_dan_.data_energistics.registry.ModMenus;
import com.fish_dan_.data_energistics.registry.ModRecipes;
import com.fish_dan_.data_energistics.registry.ModStructures;
import com.fish_dan_.data_energistics.registry.ModUpgrades;
import com.fish_dan_.data_energistics.registry.UniversalTerminalAdapters;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.repository.Pack;
import net.minecraft.server.packs.repository.PackSource;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.EquipmentSlotGroup;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.AddPackFindersEvent;
import net.neoforged.neoforge.event.ItemAttributeModifierEvent;
import net.neoforged.neoforge.event.server.ServerStartingEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.registries.RegisterEvent;

import appeng.api.AECapabilities;
import appeng.blockentity.grid.AENetworkedPoweredBlockEntity;
import appeng.blockentity.networking.CableBusBlockEntity;
import appeng.blockentity.networking.ControllerBlockEntity;
import appeng.core.definitions.AEBlockEntities;

public class CommonProxy {

    private static final ResourceLocation MODPACK_FIXES_PACK = ResourceLocation.fromNamespaceAndPath(Data_Energistics.MODID, "resourcepacks/modpack_fixes");
    private static final ResourceLocation POWERED_TOOL_SPEED_CARD_ATTACK_SPEED_ID = ResourceLocation.fromNamespaceAndPath(Data_Energistics.MODID, "powered_tool_speed_card_attack_speed");
    private static final ResourceLocation POWERED_TOOL_SABER_ENERGY_ATTACK_DAMAGE_ID = ResourceLocation.fromNamespaceAndPath(Data_Energistics.MODID, "powered_tool_saber_energy_attack_damage");

    public static void init(IEventBus modEventBus) {
        CommonProxy instance = new CommonProxy();

        ModFluids.register(modEventBus);
        ModDataComponents.register(modEventBus);
        ModBlocks.register(modEventBus);
        ModItems.register(modEventBus);
        ModEntities.register(modEventBus);
        ModBlockEntities.register(modEventBus);
        ModCreativeTabs.register(modEventBus);
        ModMenus.register(modEventBus);
        ModRecipes.register(modEventBus);
        ModStructures.register(modEventBus);
        UniversalTerminalAdapters.init();

        modEventBus.addListener(instance::commonSetup);
        modEventBus.addListener(EventPriority.LOWEST, instance::registerDepotContainerItemStrategies);
        modEventBus.addListener(EventPriority.LOWEST, instance::registerGenericKeyWorldExportStrategies);
        modEventBus.addListener(instance::registerAe2KeyTypes);
        modEventBus.addListener(instance::registerCapabilities);
        modEventBus.addListener(instance::registerPayloadHandlers);
        modEventBus.addListener(instance::registerBuiltinDataPacks);

        NeoForge.EVENT_BUS.register(instance);
        NeoForge.EVENT_BUS.register(new TimeShiftTransformLogic());
        NeoForge.EVENT_BUS.register(new DataCaptureBallRightClickRecipeLogic());
        NeoForge.EVENT_BUS.register(new DataCrystalSwordAiStripLogic());
        NeoForge.EVENT_BUS.register(new PersistentFarmlandLogic());
    }

    private void registerBuiltinDataPacks(AddPackFindersEvent event) {
        event.addPackFinders(
                MODPACK_FIXES_PACK,
                PackType.SERVER_DATA,
                Component.literal("Data Energistics Modpack Fixes"),
                PackSource.BUILT_IN,
                true,
                Pack.Position.TOP);
    }

    private void commonSetup(final FMLCommonSetupEvent event) {
        event.enqueueWork(ModUpgrades::init);
    }

    private void registerDepotContainerItemStrategies(final FMLCommonSetupEvent event) {
        event.enqueueWork(DigitalStorageDepotKeyContainerItemStrategy::registerMissingStrategies);
    }

    private void registerGenericKeyWorldExportStrategies(final FMLCommonSetupEvent event) {
        event.enqueueWork(GenericKeyItemExportStrategy::registerMissingStrategies);
    }

    private void registerAe2KeyTypes(final RegisterEvent event) {
        ModAE2Keys.register(event);
    }

    private void registerCapabilities(final RegisterCapabilitiesEvent event) {
        event.registerBlockEntity(
                AECapabilities.IN_WORLD_GRID_NODE_HOST,
                ModBlockEntities.DATA_SOLAR_PANEL_BLOCK_ENTITY.get(),
                (blockEntity, context) -> blockEntity);
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
                Capabilities.ItemHandler.BLOCK,
                ModBlockEntities.ADAPTIVE_PATTERN_PROVIDER_BLOCK_ENTITY.get(),
                (blockEntity, context) -> blockEntity.getExternalReturnItemHandler(context));
        event.registerBlockEntity(
                Capabilities.FluidHandler.BLOCK,
                ModBlockEntities.ADAPTIVE_PATTERN_PROVIDER_BLOCK_ENTITY.get(),
                (blockEntity, context) -> blockEntity.getExternalReturnFluidHandler(context));
        AppMekCompat.registerChemicalBlockEntityCapabilities(event);
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
        AppMekCompat.registerChemicalCableBusCapabilities(event);
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

    private void registerPayloadHandlers(final RegisterPayloadHandlersEvent event) {
        ModPayloads.register(event);
    }

    @SubscribeEvent
    public void onServerStarting(ServerStartingEvent event) {}

    @SubscribeEvent
    public void onServerStopped(ServerStoppedEvent event) {
        DataTeleportAnchorBlockEntity.clearRuntimeAnchorCache();
    }

    @SubscribeEvent
    public void onItemAttributeModifiers(ItemAttributeModifierEvent event) {
        ItemStack stack = event.getItemStack();
        if (!(stack.getItem() instanceof PoweredEnergyItem poweredEnergyItem)) {
            return;
        }

        if (!(stack.getItem() instanceof PoweredSwordItem || stack.getItem() instanceof PoweredAxeItem || stack.getItem() instanceof PoweredPickaxeItem || stack.getItem() instanceof PoweredHoeItem || stack.getItem() instanceof PoweredShovelItem)) {
            return;
        }

        double attackSpeedBonus = poweredEnergyItem.getSpeedCardAttackSpeedBonus(stack);
        if (attackSpeedBonus > 0.0D) {
            event.addModifier(
                    Attributes.ATTACK_SPEED,
                    new AttributeModifier(
                            POWERED_TOOL_SPEED_CARD_ATTACK_SPEED_ID,
                            attackSpeedBonus,
                            AttributeModifier.Operation.ADD_VALUE),
                    EquipmentSlotGroup.MAINHAND);
        }

        double baseAttackDamage = event.getModifiers().stream()
                .filter(entry -> entry.attribute().equals(Attributes.ATTACK_DAMAGE))
                .filter(entry -> entry.slot().test(EquipmentSlot.MAINHAND))
                .mapToDouble(entry -> entry.modifier().amount())
                .sum();
        double saberEnergyAttackDamageBonus = poweredEnergyItem.getSaberEnergyAttackDamageBonus(stack, baseAttackDamage);
        if (saberEnergyAttackDamageBonus > 0.0D) {
            event.addModifier(
                    Attributes.ATTACK_DAMAGE,
                    new AttributeModifier(
                            POWERED_TOOL_SABER_ENERGY_ATTACK_DAMAGE_ID,
                            saberEnergyAttackDamageBonus,
                            AttributeModifier.Operation.ADD_VALUE),
                    EquipmentSlotGroup.MAINHAND);
        }
    }
}

package com.fish_dan_.data_energistics;

import appeng.api.AECapabilities;
import appeng.api.upgrades.Upgrades;
import appeng.blockentity.AEBaseBlockEntity;
import appeng.core.definitions.AEItems;
import appeng.init.client.InitScreens;
import appeng.items.parts.PartModelsHelper;
import com.fish_dan_.data_energistics.block.DataDistributionTowerBlock;
import com.fish_dan_.data_energistics.ae2.DataFlowBusStrategies;
import com.fish_dan_.data_energistics.ae2.ModAE2Keys;
import com.fish_dan_.data_energistics.blockentity.DataDistributionTowerBlockEntity;
import com.fish_dan_.data_energistics.client.render.DataExtractorRenderer;
import com.fish_dan_.data_energistics.client.render.DataDistributionTowerRenderer;
import com.fish_dan_.data_energistics.client.screen.DataDistributionTowerScreen;
import com.fish_dan_.data_energistics.client.screen.DataExtractorScreen;
import com.fish_dan_.data_energistics.client.screen.DataMimeticFieldScreen;
import com.fish_dan_.data_energistics.client.ModItemColors;
import com.fish_dan_.data_energistics.client.screen.DataRipperScreen;
import com.fish_dan_.data_energistics.registry.ModBlockEntities;
import com.fish_dan_.data_energistics.registry.ModBlocks;
import com.fish_dan_.data_energistics.registry.ModCreativeTabs;
import com.fish_dan_.data_energistics.registry.ModItems;
import com.fish_dan_.data_energistics.registry.ModMenus;
import com.fish_dan_.data_energistics.registry.ModRecipes;
import com.fish_dan_.data_energistics.registry.ModStorageCells;
import com.fish_dan_.data_energistics.recipe.TimeShiftTransformLogic;
import com.mojang.logging.LogUtils;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent;
import net.neoforged.neoforge.client.event.RegisterColorHandlersEvent;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import net.neoforged.neoforge.client.event.RegisterMenuScreensEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.server.ServerStartingEvent;
import net.neoforged.neoforge.registries.RegisterEvent;
import org.slf4j.Logger;

// The value here should match an entry in the META-INF/neoforge.mods.toml file
@Mod(Data_Energistics.MODID)
public class Data_Energistics {
    // Define mod id in a common place for everything to reference
    public static final String MODID = "data_energistics";
    // Directly reference a slf4j logger
    private static final Logger LOGGER = LogUtils.getLogger();

    public Data_Energistics(IEventBus modEventBus, ModContainer modContainer) {
        ModBlocks.register(modEventBus);
        ModItems.register(modEventBus);
        ModBlockEntities.register(modEventBus);
        ModCreativeTabs.register(modEventBus);
        ModMenus.register(modEventBus);
        ModRecipes.register(modEventBus);

        modEventBus.addListener(this::commonSetup);
        modEventBus.addListener(this::registerAe2KeyTypes);
        modEventBus.addListener(this::registerCapabilities);
        NeoForge.EVENT_BUS.register(this);
        NeoForge.EVENT_BUS.register(new TimeShiftTransformLogic());
        modContainer.registerConfig(ModConfig.Type.COMMON, Config.SPEC);
    }

    private void commonSetup(final FMLCommonSetupEvent event) {
        event.enqueueWork(() -> {
            DataFlowBusStrategies.register();
            AEBaseBlockEntity.registerBlockEntityItem(ModBlockEntities.DATA_FLOW_GENERATOR_BLOCK_ENTITY.get(), ModBlocks.DATA_FLOW_GENERATOR.get().asItem());
            AEBaseBlockEntity.registerBlockEntityItem(ModBlockEntities.DATA_EXTRACTOR_BLOCK_ENTITY.get(), ModBlocks.DATA_EXTRACTOR.get().asItem());
            AEBaseBlockEntity.registerBlockEntityItem(ModBlockEntities.DATA_FRAMEWORK_BLOCK_ENTITY.get(), ModBlocks.DATA_FRAMEWORK.get().asItem());
            AEBaseBlockEntity.registerBlockEntityItem(ModBlockEntities.DATA_DISTRIBUTION_TOWER_BLOCK_ENTITY.get(), ModBlocks.DATA_DISTRIBUTION_TOWER.get().asItem());
            AEBaseBlockEntity.registerBlockEntityItem(ModBlockEntities.DATA_MIMETIC_FIELD_BLOCK_ENTITY.get(), ModBlocks.DATA_MIMETIC_FIELD.get().asItem());
            Upgrades.add(AEItems.ENERGY_CARD, ModItems.DATA_RIPPER.get(), 8, "item.data_energistics.data_ripper");
            Upgrades.add(AEItems.SPEED_CARD, ModItems.DATA_RIPPER.get(), 4, "item.data_energistics.data_ripper");
            Upgrades.add(AEItems.ENERGY_CARD, ModBlocks.DATA_EXTRACTOR.get(), 6, "block.data_energistics.data_extractor");
            Upgrades.add(AEItems.CAPACITY_CARD, ModBlocks.DATA_EXTRACTOR.get(), 6, "block.data_energistics.data_extractor");
            Upgrades.add(AEItems.SPEED_CARD, ModBlocks.DATA_EXTRACTOR.get(), 5, "block.data_energistics.data_extractor");
            Upgrades.add(AEItems.CAPACITY_CARD, ModBlocks.DATA_MIMETIC_FIELD.get(), 3, "block.data_energistics.data_mimetic_field");
            Upgrades.add(AEItems.SPEED_CARD, ModBlocks.DATA_MIMETIC_FIELD.get(), 4, "block.data_energistics.data_mimetic_field");
            appeng.api.parts.PartModels.registerModels(
                    PartModelsHelper.createModels(ModItems.DATA_RIPPER.get().getPartClass())
            );
        });
    }

    private void registerAe2KeyTypes(final RegisterEvent event) {
        ModAE2Keys.register(event);
    }

    private void registerCapabilities(final RegisterCapabilitiesEvent event) {
        event.registerBlockEntity(
                AECapabilities.IN_WORLD_GRID_NODE_HOST,
                ModBlockEntities.DATA_FLOW_GENERATOR_BLOCK_ENTITY.get(),
                (blockEntity, context) -> blockEntity
        );
        event.registerBlockEntity(
                AECapabilities.IN_WORLD_GRID_NODE_HOST,
                ModBlockEntities.DATA_EXTRACTOR_BLOCK_ENTITY.get(),
                (blockEntity, context) -> blockEntity
        );
        event.registerBlockEntity(
                Capabilities.ItemHandler.BLOCK,
                ModBlockEntities.DATA_EXTRACTOR_BLOCK_ENTITY.get(),
                (blockEntity, context) -> blockEntity.getExternalInventory().toItemHandler()
        );
        event.registerBlockEntity(
                Capabilities.ItemHandler.BLOCK,
                ModBlockEntities.DATA_MIMETIC_FIELD_BLOCK_ENTITY.get(),
                (blockEntity, context) -> blockEntity.getInternalInventory().toItemHandler()
        );
        event.registerBlockEntity(
                AECapabilities.IN_WORLD_GRID_NODE_HOST,
                ModBlockEntities.DATA_MIMETIC_FIELD_BLOCK_ENTITY.get(),
                (blockEntity, context) -> blockEntity
        );
        event.registerBlockEntity(
                AECapabilities.IN_WORLD_GRID_NODE_HOST,
                ModBlockEntities.DATA_DISTRIBUTION_TOWER_BLOCK_ENTITY.get(),
                (blockEntity, context) -> blockEntity
        );

        event.registerBlock(
                Capabilities.EnergyStorage.BLOCK,
                (level, pos, state, blockEntity, context) -> {
                    if (!(state.getBlock() instanceof DataDistributionTowerBlock)) {
                        return null;
                    }

                    BlockPos basePos = DataDistributionTowerBlock.getBasePos(pos, state);
                    BlockState baseState = level.getBlockState(basePos);
                    if (!(baseState.getBlock() instanceof DataDistributionTowerBlock)
                            || !(level.getBlockEntity(basePos) instanceof DataDistributionTowerBlockEntity tower)) {
                        return null;
                    }

                    return tower.getEnergyStorageForQuery(pos, context);
                },
                ModBlocks.DATA_DISTRIBUTION_TOWER.get()
        );

    }

    // You can use SubscribeEvent and let the Event Bus discover methods to call
    @SubscribeEvent
    public void onServerStarting(ServerStartingEvent event) {
    }

    // You can use EventBusSubscriber to automatically register all static methods in the class annotated with @SubscribeEvent
    @SuppressWarnings("removal")
    @EventBusSubscriber(modid = MODID, bus = EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
    public static class ClientModEvents {
        @SubscribeEvent
        public static void onRegisterItemColors(RegisterColorHandlersEvent.Item event) {
            ModItemColors.register(event);
        }

        @SubscribeEvent
        public static void onClientSetup(FMLClientSetupEvent event) {
            event.enqueueWork(() -> {
                ModAE2Keys.registerClient();
                ModStorageCells.registerClientModels();
            });
        }

        @SubscribeEvent
        public static void onRegisterScreens(RegisterMenuScreensEvent event) {
            InitScreens.register(event, ModMenus.DATA_RIPPER.get(), DataRipperScreen::new, "/screens/data_ripper.json");
            InitScreens.register(event, ModMenus.DATA_DISTRIBUTION_TOWER.get(), DataDistributionTowerScreen::new, "/screens/data_distribution_tower.json");
            InitScreens.register(event, ModMenus.DATA_EXTRACTOR.get(), DataExtractorScreen::new, "/screens/data_extractor.json");
            InitScreens.register(event, ModMenus.DATA_MIMETIC_FIELD.get(), DataMimeticFieldScreen::new, "/screens/data_mimetic_field.json");
        }

        @SubscribeEvent
        public static void onRegisterRenderers(EntityRenderersEvent.RegisterRenderers event) {
            event.registerBlockEntityRenderer(ModBlockEntities.DATA_EXTRACTOR_BLOCK_ENTITY.get(), DataExtractorRenderer::new);
            event.registerBlockEntityRenderer(ModBlockEntities.DATA_DISTRIBUTION_TOWER_BLOCK_ENTITY.get(), DataDistributionTowerRenderer::new);
        }
    }
}

package com.fish_dan_.data_energistics;

import com.fish_dan_.data_energistics.ae2.DataFlowBusStrategies;
import com.fish_dan_.data_energistics.ae2.InfiniteDataCellHandler;
import com.fish_dan_.data_energistics.ae2.ModAE2Keys;
import com.fish_dan_.data_energistics.block.AdaptivePatternProviderBlock;
import com.fish_dan_.data_energistics.block.DataDistributionTowerBlock;
import com.fish_dan_.data_energistics.blockentity.DataDistributionTowerBlockEntity;
import com.fish_dan_.data_energistics.client.ClientAeKeyRenderers;
import com.fish_dan_.data_energistics.client.ModFluidClientExtensions;
import com.fish_dan_.data_energistics.client.ModItemColors;
import com.fish_dan_.data_energistics.client.ModKeyMappings;
import com.fish_dan_.data_energistics.client.render.DataDistributionTowerRenderer;
import com.fish_dan_.data_energistics.client.render.DataExtractorRenderer;
import com.fish_dan_.data_energistics.client.render.DataMimeticFieldRenderer;
import com.fish_dan_.data_energistics.client.render.DispersingDataRenderer;
import com.fish_dan_.data_energistics.client.render.LightBladeChargeRenderer;
import com.fish_dan_.data_energistics.client.render.MatterConvergingBoltRenderer;
import com.fish_dan_.data_energistics.client.render.ThrownLightSaberRenderer;
import com.fish_dan_.data_energistics.client.screen.AdaptivePatternProviderScreen;
import com.fish_dan_.data_energistics.client.screen.Ae2TerminalKeyOverlay;
import com.fish_dan_.data_energistics.client.screen.DataDistributionTowerScreen;
import com.fish_dan_.data_energistics.client.screen.DataExtractorScreen;
import com.fish_dan_.data_energistics.client.screen.DataMimeticFieldScreen;
import com.fish_dan_.data_energistics.client.screen.DataRipperReassemblerScreen;
import com.fish_dan_.data_energistics.client.screen.DataRipperScreen;
import com.fish_dan_.data_energistics.client.screen.DataSolarPanelScreen;
import com.fish_dan_.data_energistics.client.screen.DataTeleportAnchorScreen;
import com.fish_dan_.data_energistics.client.screen.NativePatternEncodingTermScreen;
import com.fish_dan_.data_energistics.client.screen.PatternEncodingPreviewScreen;
import com.fish_dan_.data_energistics.client.screen.UniversalCraftingTermScreen;
import com.fish_dan_.data_energistics.client.screen.UniversalMEStorageScreen;
import com.fish_dan_.data_energistics.client.screen.UniversalPatternAccessTermScreen;
import com.fish_dan_.data_energistics.client.screen.UniversalPatternEncodingTermScreen;
import com.fish_dan_.data_energistics.client.screen.UniversalTerminalScreenHook;
import com.fish_dan_.data_energistics.integration.Ae2WtLibCompat;
import com.fish_dan_.data_energistics.integration.AppMekCompat;
import com.fish_dan_.data_energistics.item.DataCaptureBallItem;
import com.fish_dan_.data_energistics.item.DataCrystalSwordAiStripLogic;
import com.fish_dan_.data_energistics.item.MatterConvergingCrossbowItem;
import com.fish_dan_.data_energistics.item.PersistentFarmlandLogic;
import com.fish_dan_.data_energistics.item.PoweredEnergyItem;
import com.fish_dan_.data_energistics.menu.common.PatternEncodingPreviewMenu;
import com.fish_dan_.data_energistics.network.ModPayloads;
import com.fish_dan_.data_energistics.part.AdaptivePatternProviderPart;
import com.fish_dan_.data_energistics.recipe.DataCaptureBallRightClickRecipeLogic;
import com.fish_dan_.data_energistics.recipe.TimeShiftTransformLogic;
import com.fish_dan_.data_energistics.registry.ModBlockEntities;
import com.fish_dan_.data_energistics.registry.ModBlocks;
import com.fish_dan_.data_energistics.registry.ModCreativeTabs;
import com.fish_dan_.data_energistics.registry.ModEntities;
import com.fish_dan_.data_energistics.registry.ModFluids;
import com.fish_dan_.data_energistics.registry.ModItems;
import com.fish_dan_.data_energistics.registry.ModMenus;
import com.fish_dan_.data_energistics.registry.ModRecipes;
import com.fish_dan_.data_energistics.registry.ModStorageCells;
import com.fish_dan_.data_energistics.registry.ModStructures;
import com.fish_dan_.data_energistics.registry.UniversalTerminalAdapters;
import com.fish_dan_.data_energistics.util.LightSaberColorData;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.renderer.entity.TntRenderer;
import net.minecraft.client.renderer.item.ItemProperties;
import net.minecraft.client.resources.model.ModelResourceLocation;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.repository.Pack;
import net.minecraft.server.packs.repository.PackSource;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EquipmentSlotGroup;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.ChargedProjectiles;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;
import net.neoforged.fml.event.lifecycle.FMLLoadCompleteEvent;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import net.neoforged.neoforge.client.event.ModelEvent;
import net.neoforged.neoforge.client.event.RegisterColorHandlersEvent;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import net.neoforged.neoforge.client.event.RegisterMenuScreensEvent;
import net.neoforged.neoforge.client.event.ScreenEvent;
import net.neoforged.neoforge.client.extensions.common.RegisterClientExtensionsEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.AddPackFindersEvent;
import net.neoforged.neoforge.event.ItemAttributeModifierEvent;
import net.neoforged.neoforge.event.server.ServerStartingEvent;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.registries.RegisterEvent;

import appeng.api.AECapabilities;
import appeng.api.storage.StorageCells;
import appeng.api.upgrades.Upgrades;
import appeng.blockentity.AEBaseBlockEntity;
import appeng.blockentity.grid.AENetworkedPoweredBlockEntity;
import appeng.blockentity.networking.CableBusBlockEntity;
import appeng.blockentity.networking.ControllerBlockEntity;
import appeng.client.gui.me.items.PatternEncodingTermScreen;
import appeng.client.gui.style.StyleManager;
import appeng.core.definitions.AEBlockEntities;
import appeng.core.definitions.AEBlocks;
import appeng.core.definitions.AEItems;
import appeng.init.client.InitScreens;
import appeng.items.misc.PaintBallItem;
import appeng.items.parts.PartModelsHelper;
import com.mojang.logging.LogUtils;
import org.joml.Vector3f;
import org.slf4j.Logger;

// The value here should match an entry in the META-INF/neoforge.mods.toml file
@Mod(Data_Energistics.MODID)
public class Data_Energistics {

    // Define mod id in a common place for everything to reference
    public static final String MODID = "data_energistics";
    private static final String ADAPTIVE_PATTERN_PROVIDER_UPGRADE_TOOLTIP_GROUP = "block.data_energistics.adaptive_pattern_provider";
    private static final ResourceLocation MODPACK_FIXES_PACK = ResourceLocation.fromNamespaceAndPath(MODID, "resourcepacks/modpack_fixes");
    private static final ResourceLocation POWERED_TOOL_SPEED_CARD_ATTACK_SPEED_ID = ResourceLocation.fromNamespaceAndPath(MODID, "powered_tool_speed_card_attack_speed");
    private static final ResourceLocation POWERED_TOOL_SABER_ENERGY_ATTACK_DAMAGE_ID = ResourceLocation.fromNamespaceAndPath(MODID, "powered_tool_saber_energy_attack_damage");
    private static final String[][] STARTUP_SHUTDOWN_LOG_PAIRS = {
            { "Ciallo～(∠・ω< )⌒☆", "柚子厨真恶心！" },
            { "原神启动！", "前面的区域以后再探索吧" }
    };
    // Directly reference a slf4j logger
    private static final Logger LOGGER = LogUtils.getLogger();

    public Data_Energistics(IEventBus modEventBus, ModContainer modContainer) {
        ModFluids.register(modEventBus);
        ModBlocks.register(modEventBus);
        ModItems.register(modEventBus);
        ModEntities.register(modEventBus);
        ModBlockEntities.register(modEventBus);
        ModCreativeTabs.register(modEventBus);
        ModMenus.register(modEventBus);
        ModRecipes.register(modEventBus);
        ModStructures.register(modEventBus);
        UniversalTerminalAdapters.init();

        modEventBus.addListener(this::commonSetup);
        modEventBus.addListener(this::registerAe2KeyTypes);
        modEventBus.addListener(this::registerCapabilities);
        modEventBus.addListener(this::registerPayloadHandlers);
        modEventBus.addListener(this::registerBuiltinDataPacks);
        NeoForge.EVENT_BUS.register(this);
        NeoForge.EVENT_BUS.register(new TimeShiftTransformLogic());
        NeoForge.EVENT_BUS.register(new DataCaptureBallRightClickRecipeLogic());
        NeoForge.EVENT_BUS.register(new DataCrystalSwordAiStripLogic());
        NeoForge.EVENT_BUS.register(new PersistentFarmlandLogic());
        modContainer.registerConfig(ModConfig.Type.COMMON, Config.SPEC);
        modContainer.registerConfig(ModConfig.Type.COMMON, FlatteningTntConfig.SPEC, "data_energistics-tnt.toml");
        modContainer.registerConfig(ModConfig.Type.COMMON, DataExtractorConfig.SPEC, "data_energistics-data_extractor.toml");
        modContainer.registerConfig(ModConfig.Type.COMMON, SolarPanelConfig.SPEC, "data_energistics-solar_panel.toml");
        String[] selectedLogPair = STARTUP_SHUTDOWN_LOG_PAIRS[net.minecraft.util.RandomSource.create().nextInt(STARTUP_SHUTDOWN_LOG_PAIRS.length)];
        LOGGER.info(selectedLogPair[0]);
        Runtime.getRuntime().addShutdownHook(new Thread(() -> LOGGER.info(selectedLogPair[1]),
                "data-energistics-shutdown-log"));
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
        event.enqueueWork(() -> {
            DataExtractorRuleTable.load();
            UniversalTerminalAdapters.discoverFromRegisteredItems();
            DataFlowBusStrategies.register();
            ((AdaptivePatternProviderBlock) ModBlocks.ADAPTIVE_PATTERN_PROVIDER.get()).bindBlockEntity();
            AEBaseBlockEntity.registerBlockEntityItem(ModBlockEntities.DATA_SOLAR_PANEL_BLOCK_ENTITY.get(), ModBlocks.DATA_SOLAR_PANEL.get().asItem());
            AEBaseBlockEntity.registerBlockEntityItem(ModBlockEntities.DATA_EXTRACTOR_BLOCK_ENTITY.get(), ModBlocks.DATA_EXTRACTOR.get().asItem());
            AEBaseBlockEntity.registerBlockEntityItem(ModBlockEntities.DATA_RIPPER_REASSEMBLER_BLOCK_ENTITY.get(), ModBlocks.DATA_RIPPER_REASSEMBLER.get().asItem());
            AEBaseBlockEntity.registerBlockEntityItem(ModBlockEntities.DATA_FRAMEWORK_BLOCK_ENTITY.get(), ModBlocks.DATA_FRAMEWORK.get().asItem());
            AEBaseBlockEntity.registerBlockEntityItem(ModBlockEntities.DATA_DISTRIBUTION_TOWER_BLOCK_ENTITY.get(), ModBlocks.DATA_DISTRIBUTION_TOWER.get().asItem());
            AEBaseBlockEntity.registerBlockEntityItem(ModBlockEntities.DATA_MIMETIC_FIELD_BLOCK_ENTITY.get(), ModBlocks.DATA_MIMETIC_FIELD.get().asItem());
            AEBaseBlockEntity.registerBlockEntityItem(ModBlockEntities.DATA_TELEPORT_ANCHOR_BLOCK_ENTITY.get(), ModBlocks.DATA_TELEPORT_ANCHOR.get().asItem());
            AEBaseBlockEntity.registerBlockEntityItem(ModBlockEntities.ADAPTIVE_PATTERN_PROVIDER_BLOCK_ENTITY.get(), ModBlocks.ADAPTIVE_PATTERN_PROVIDER.get().asItem());
            Upgrades.add(AEItems.ENERGY_CARD, ModItems.DATA_RIPPER.get(), 8, "item.data_energistics.data_ripper");
            Upgrades.add(AEItems.SPEED_CARD, ModItems.DATA_RIPPER.get(), 4, "item.data_energistics.data_ripper");
            Upgrades.add(AEItems.ENERGY_CARD, ModItems.PORTABLE_DATA_FLOW_CELL_1K.get(), 3,
                    "item.data_energistics.portable_data_flow_cell_1k");
            Upgrades.add(AEItems.ENERGY_CARD, ModItems.PORTABLE_DATA_FLOW_CELL_4K.get(), 3,
                    "item.data_energistics.portable_data_flow_cell_4k");
            Upgrades.add(AEItems.ENERGY_CARD, ModItems.PORTABLE_DATA_FLOW_CELL_16K.get(), 3,
                    "item.data_energistics.portable_data_flow_cell_16k");
            Upgrades.add(AEItems.ENERGY_CARD, ModItems.PORTABLE_DATA_FLOW_CELL_64K.get(), 3,
                    "item.data_energistics.portable_data_flow_cell_64k");
            Upgrades.add(AEItems.ENERGY_CARD, ModItems.PORTABLE_DATA_FLOW_CELL_256K.get(), 3,
                    "item.data_energistics.portable_data_flow_cell_256k");
            Upgrades.add(AEItems.ENERGY_CARD, ModItems.DATA_CAPTURE_BALL.get(), 3,
                    "item.data_energistics.data_capture_ball");
            Upgrades.add(AEItems.ENERGY_CARD, ModItems.DATA_CRYSTAL_SWORD.get(), 3,
                    "item.data_energistics.data_crystal_sword");
            Upgrades.add(AEItems.SPEED_CARD, ModItems.DATA_CRYSTAL_SWORD.get(), 3,
                    "item.data_energistics.data_crystal_sword");
            Upgrades.add(ModItems.CARD_SABER_ENERGY.get(), ModItems.DATA_CRYSTAL_SWORD.get(), 1,
                    "item.data_energistics.data_crystal_sword");
            Upgrades.add(AEItems.ENERGY_CARD, ModItems.DATA_CRYSTAL_AXE.get(), 3,
                    "item.data_energistics.data_crystal_axe");
            Upgrades.add(AEItems.SPEED_CARD, ModItems.DATA_CRYSTAL_AXE.get(), 3,
                    "item.data_energistics.data_crystal_axe");
            Upgrades.add(ModItems.CARD_SABER_ENERGY.get(), ModItems.DATA_CRYSTAL_AXE.get(), 1,
                    "item.data_energistics.data_crystal_axe");
            Upgrades.add(AEItems.ENERGY_CARD, ModItems.DATA_CRYSTAL_PICKAXE.get(), 3,
                    "item.data_energistics.data_crystal_pickaxe");
            Upgrades.add(AEItems.SPEED_CARD, ModItems.DATA_CRYSTAL_PICKAXE.get(), 3,
                    "item.data_energistics.data_crystal_pickaxe");
            Upgrades.add(ModItems.CARD_SABER_ENERGY.get(), ModItems.DATA_CRYSTAL_PICKAXE.get(), 1,
                    "item.data_energistics.data_crystal_pickaxe");
            Upgrades.add(AEItems.ENERGY_CARD, ModItems.DATA_CRYSTAL_HOE.get(), 3,
                    "item.data_energistics.data_crystal_hoe");
            Upgrades.add(AEItems.SPEED_CARD, ModItems.DATA_CRYSTAL_HOE.get(), 3,
                    "item.data_energistics.data_crystal_hoe");
            Upgrades.add(ModItems.CARD_SABER_ENERGY.get(), ModItems.DATA_CRYSTAL_HOE.get(), 1,
                    "item.data_energistics.data_crystal_hoe");
            Upgrades.add(AEItems.ENERGY_CARD, ModItems.DATA_CRYSTAL_SHOVEL.get(), 3,
                    "item.data_energistics.data_crystal_shovel");
            Upgrades.add(AEItems.SPEED_CARD, ModItems.DATA_CRYSTAL_SHOVEL.get(), 3,
                    "item.data_energistics.data_crystal_shovel");
            Upgrades.add(ModItems.CARD_SABER_ENERGY.get(), ModItems.DATA_CRYSTAL_SHOVEL.get(), 1,
                    "item.data_energistics.data_crystal_shovel");
            Upgrades.add(AEItems.ENERGY_CARD, ModItems.DATA_CRYSTAL_CUTTING_KNIFE.get(), 3,
                    "item.data_energistics.data_crystal_cutting_knife");
            Upgrades.add(ModItems.CARD_SABER_ENERGY.get(), ModItems.DATA_CRYSTAL_CUTTING_KNIFE.get(), 1,
                    "item.data_energistics.data_crystal_cutting_knife");
            Upgrades.add(AEItems.ENERGY_CARD, ModItems.DATA_LIGHT_SABER.get(), 3,
                    "item.data_energistics.data_light_saber");
            Upgrades.add(AEItems.SPEED_CARD, ModItems.DATA_LIGHT_SABER.get(), 3,
                    "item.data_energistics.data_light_saber");
            Upgrades.add(ModItems.CARD_SABER_ENERGY.get(), ModItems.DATA_LIGHT_SABER.get(), 1,
                    "item.data_energistics.data_light_saber");
            Upgrades.add(AEItems.ENERGY_CARD, ModItems.DATA_SANCTIFIER.get(), 3,
                    "item.data_energistics.data_sanctifier");
            Upgrades.add(AEItems.SPEED_CARD, ModItems.DATA_SANCTIFIER.get(), 3,
                    "item.data_energistics.data_sanctifier");
            Upgrades.add(ModItems.CARD_SABER_ENERGY.get(), ModItems.DATA_SANCTIFIER.get(), 1,
                    "item.data_energistics.data_sanctifier");
            Upgrades.add(AEItems.ENERGY_CARD, ModBlocks.DATA_EXTRACTOR.get(), 6, "block.data_energistics.data_extractor");
            Upgrades.add(AEItems.CAPACITY_CARD, ModBlocks.DATA_EXTRACTOR.get(), 6, "block.data_energistics.data_extractor");
            Upgrades.add(AEItems.SPEED_CARD, ModBlocks.DATA_EXTRACTOR.get(), 5, "block.data_energistics.data_extractor");
            Upgrades.add(AEItems.SPEED_CARD, ModBlocks.DATA_RIPPER_REASSEMBLER.get(), 4, "block.data_energistics.data_reassembler");
            Upgrades.add(AEItems.SPEED_CARD, ModBlocks.DATA_SOLAR_PANEL.get(), 3, "block.data_energistics.me_solar_panel");
            Upgrades.add(AEItems.ENERGY_CARD, ModBlocks.DATA_SOLAR_PANEL.get(), 3, "block.data_energistics.me_solar_panel");
            Upgrades.add(AEItems.SPEED_CARD, ModItems.ME_SOLAR_PANEL_PART.get(), 3, "item.data_energistics.me_solar_panel_part");
            Upgrades.add(AEItems.ENERGY_CARD, ModItems.ME_SOLAR_PANEL_PART.get(), 3, "item.data_energistics.me_solar_panel_part");
            Upgrades.add(AEItems.CAPACITY_CARD, ModBlocks.DATA_MIMETIC_FIELD.get(), 1, "block.data_energistics.data_mimetic_field");
            Upgrades.add(AEItems.SPEED_CARD, ModBlocks.DATA_MIMETIC_FIELD.get(), 4, "block.data_energistics.data_mimetic_field");
            Upgrades.add(AEItems.CAPACITY_CARD, ModBlocks.ADAPTIVE_PATTERN_PROVIDER.get(), 3, ADAPTIVE_PATTERN_PROVIDER_UPGRADE_TOOLTIP_GROUP);
            Upgrades.add(AEItems.CAPACITY_CARD, ModItems.ADAPTIVE_PATTERN_PROVIDER_PART.get(), 3, ADAPTIVE_PATTERN_PROVIDER_UPGRADE_TOOLTIP_GROUP);
            Upgrades.add(AEItems.SPEED_CARD, ModBlocks.ADAPTIVE_PATTERN_PROVIDER.get(), 4, ADAPTIVE_PATTERN_PROVIDER_UPGRADE_TOOLTIP_GROUP);
            Upgrades.add(AEItems.SPEED_CARD, ModItems.ADAPTIVE_PATTERN_PROVIDER_PART.get(), 4, ADAPTIVE_PATTERN_PROVIDER_UPGRADE_TOOLTIP_GROUP);
            Upgrades.add(AEItems.ENERGY_CARD, ModItems.MATTER_CONVERGING_CROSSBOW.get(), 2,
                    "item.data_energistics.matter_converging_crossbow");
            Upgrades.add(AEItems.FUZZY_CARD, ModItems.MATTER_CONVERGING_CROSSBOW.get(), 1,
                    "item.data_energistics.matter_converging_crossbow");
            Upgrades.add(AEItems.INVERTER_CARD, ModItems.MATTER_CONVERGING_CROSSBOW.get(), 1,
                    "item.data_energistics.matter_converging_crossbow");
            Upgrades.add(AEItems.VOID_CARD, ModItems.MATTER_CONVERGING_CROSSBOW.get(), 1,
                    "item.data_energistics.matter_converging_crossbow");
            Upgrades.add(AEItems.SPEED_CARD, ModItems.MATTER_CONVERGING_CROSSBOW.get(), 4,
                    "item.data_energistics.matter_converging_crossbow");
            Upgrades.add(ModItems.CARD_SABER_ENERGY.get(), ModItems.MATTER_CONVERGING_CROSSBOW.get(), 2,
                    "item.data_energistics.matter_converging_crossbow");
            Upgrades.add(ModItems.REDSTONE_TUNING_CARD.get(), ModItems.MATTER_CONVERGING_CROSSBOW.get(), 1,
                    "item.data_energistics.matter_converging_crossbow");
            Upgrades.add(ModItems.REDSTONE_TUNING_CARD.get(), AEBlocks.PATTERN_PROVIDER.block(), 1, "block.ae2.pattern_provider");
            Upgrades.add(ModItems.REDSTONE_TUNING_CARD.get(), ModBlocks.ADAPTIVE_PATTERN_PROVIDER.get(), 1, ADAPTIVE_PATTERN_PROVIDER_UPGRADE_TOOLTIP_GROUP);
            Upgrades.add(ModItems.REDSTONE_TUNING_CARD.get(), ModItems.ADAPTIVE_PATTERN_PROVIDER_PART.get(), 1,
                    ADAPTIVE_PATTERN_PROVIDER_UPGRADE_TOOLTIP_GROUP);
            registerExternalRedstoneTuningCardCompat();
            registerAppliedFluxAdaptivePatternProviderCompat();
            registerAe2CrystalScienceAdaptivePatternProviderCompat();
            appeng.api.parts.PartModels.registerModels(
                    PartModelsHelper.createModels(ModItems.DATA_RIPPER.get().getPartClass()));
            appeng.api.parts.PartModels.registerModels(
                    PartModelsHelper.createModels(ModItems.ADAPTIVE_PATTERN_PROVIDER_PART.get().getPartClass()));
            appeng.api.parts.PartModels.registerModels(
                    PartModelsHelper.createModels(ModItems.ME_SOLAR_PANEL_PART.get().getPartClass()));
            appeng.api.parts.PartModels.registerModels(
                    PartModelsHelper.createModels(ModItems.UNIVERSAL_TERMINAL.get().getPartClass()));
            StorageCells.addCellHandler(InfiniteDataCellHandler.INSTANCE);
        });
    }

    private void registerAppliedFluxAdaptivePatternProviderCompat() {
        Item inductionCard = BuiltInRegistries.ITEM.get(ResourceLocation.fromNamespaceAndPath("appflux", "induction_card"));
        if (inductionCard == null || inductionCard == Items.AIR) {
            return;
        }

        Upgrades.add(inductionCard, ModBlocks.ADAPTIVE_PATTERN_PROVIDER.get(), 1, ADAPTIVE_PATTERN_PROVIDER_UPGRADE_TOOLTIP_GROUP);
        Upgrades.add(inductionCard, ModItems.ADAPTIVE_PATTERN_PROVIDER_PART.get(), 1, ADAPTIVE_PATTERN_PROVIDER_UPGRADE_TOOLTIP_GROUP);
    }

    private void registerAe2CrystalScienceAdaptivePatternProviderCompat() {
        Item crystalGrowthCard = BuiltInRegistries.ITEM.get(ResourceLocation.fromNamespaceAndPath("ae2cs", "crystal_growth_card"));
        if (crystalGrowthCard == null || crystalGrowthCard == Items.AIR) {
            return;
        }

        Upgrades.add(crystalGrowthCard, ModBlocks.ADAPTIVE_PATTERN_PROVIDER.get(), 1,
                ADAPTIVE_PATTERN_PROVIDER_UPGRADE_TOOLTIP_GROUP);
        Upgrades.add(crystalGrowthCard, ModItems.ADAPTIVE_PATTERN_PROVIDER_PART.get(), 1,
                ADAPTIVE_PATTERN_PROVIDER_UPGRADE_TOOLTIP_GROUP);
    }

    private static void registerExternalRedstoneTuningCardCompat() {
        registerExternalRedstoneTuningTarget("advanced_ae", "small_adv_pattern_provider", "block.advanced_ae.small_adv_pattern_provider");
        registerExternalRedstoneTuningTarget("advanced_ae", "adv_pattern_provider", "block.advanced_ae.adv_pattern_provider");
        registerExternalRedstoneTuningItemTarget("advanced_ae", "small_adv_pattern_provider_part",
                "block.advanced_ae.small_adv_pattern_provider");
        registerExternalRedstoneTuningItemTarget("advanced_ae", "adv_pattern_provider_part",
                "block.advanced_ae.adv_pattern_provider");
        registerExternalRedstoneTuningTarget("ae2lt", "overloaded_pattern_provider", "block.ae2lt.overloaded_pattern_provider");
        registerExternalRedstoneTuningTarget("ae2cs", "simple_pattern_provider", "block.ae2cs.simple_pattern_provider");
        registerExternalRedstoneTuningTarget("ae2cs", "resonating_pattern_provider", "block.ae2cs.resonating_pattern_provider");
        registerExternalRedstoneTuningTarget("ae2cs", "extended_resonating_pattern_provider",
                "block.ae2cs.resonating_pattern_provider");
        registerExternalRedstoneTuningTarget("ae2cs", "ex_resonating_pattern_provider",
                "block.ae2cs.resonating_pattern_provider");
        registerExternalRedstoneTuningTarget("ae2cs", "meteorite_pattern_provider", "block.ae2cs.meteorite_pattern_provider");
        registerExternalRedstoneTuningItemTarget("ae2cs", "simple_pattern_provider_part",
                "block.ae2cs.simple_pattern_provider");
        registerExternalRedstoneTuningItemTarget("ae2cs", "resonating_pattern_provider_part",
                "block.ae2cs.resonating_pattern_provider");
        registerExternalRedstoneTuningItemTarget("ae2cs", "extended_resonating_pattern_provider_part",
                "block.ae2cs.resonating_pattern_provider");
        registerExternalRedstoneTuningItemTarget("ae2cs", "ex_resonating_pattern_provider_part",
                "block.ae2cs.resonating_pattern_provider");
        registerExternalRedstoneTuningItemTarget("ae2cs", "meteorite_pattern_provider_part",
                "block.ae2cs.meteorite_pattern_provider");
        registerExternalRedstoneTuningTarget("appliedcreate", "andesite_pattern_provider", "block.appliedcreate.andesite_pattern_provider");
        registerExternalRedstoneTuningTarget("appliedcreate", "brass_pattern_provider", "block.appliedcreate.brass_pattern_provider");
        registerExternalRedstoneTuningItemTarget("appliedcreate", "andesite_pattern_provider_part",
                "block.appliedcreate.andesite_pattern_provider");
        registerExternalRedstoneTuningItemTarget("appliedcreate", "brass_pattern_provider_part",
                "block.appliedcreate.brass_pattern_provider");
        registerExternalRedstoneTuningTarget("extendedae", "ex_pattern_provider", "block.extendedae.ex_pattern_provider");
        registerExternalRedstoneTuningItemTarget("extendedae", "ex_pattern_provider_part",
                "block.extendedae.ex_pattern_provider");
        registerExternalRedstoneTuningTarget("megacells", "mega_pattern_provider", "block.megacells.mega_pattern_provider");
        registerExternalRedstoneTuningTarget("extendedae_plus", "mirror_pattern_provider", "block.extendedae_plus.mirror_pattern_provider");
    }

    private static void registerExternalRedstoneTuningTarget(String namespace, String path, String tooltipKey) {
        var block = BuiltInRegistries.BLOCK.get(ResourceLocation.fromNamespaceAndPath(namespace, path));
        if (block == null || block == Blocks.AIR) {
            return;
        }
        Upgrades.add(ModItems.REDSTONE_TUNING_CARD.get(), block, 1, tooltipKey);
    }

    private static void registerExternalRedstoneTuningItemTarget(String namespace, String path, String tooltipKey) {
        var item = BuiltInRegistries.ITEM.get(ResourceLocation.fromNamespaceAndPath(namespace, path));
        if (item == null || item == Items.AIR) {
            return;
        }
        Upgrades.add(ModItems.REDSTONE_TUNING_CARD.get(), item, 1, tooltipKey);
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

    public static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath(MODID, path);
    }

    // You can use SubscribeEvent and let the Event Bus discover methods to call
    @SubscribeEvent
    public void onServerStarting(ServerStartingEvent event) {}

    @SubscribeEvent
    public void onItemAttributeModifiers(ItemAttributeModifierEvent event) {
        ItemStack stack = event.getItemStack();
        if (!(stack.getItem() instanceof PoweredEnergyItem poweredEnergyItem)) {
            return;
        }

        if (!(stack.getItem() instanceof com.fish_dan_.data_energistics.item.PoweredSwordItem || stack.getItem() instanceof com.fish_dan_.data_energistics.item.PoweredAxeItem || stack.getItem() instanceof com.fish_dan_.data_energistics.item.PoweredPickaxeItem || stack.getItem() instanceof com.fish_dan_.data_energistics.item.PoweredHoeItem || stack.getItem() instanceof com.fish_dan_.data_energistics.item.PoweredShovelItem)) {
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
                .filter(entry -> entry.slot().test(net.minecraft.world.entity.EquipmentSlot.MAINHAND))
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

    // You can use EventBusSubscriber to automatically register all static methods in the class annotated with
    // @SubscribeEvent
    @SuppressWarnings("removal")
    @EventBusSubscriber(modid = MODID, bus = EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
    public static class ClientModEvents {

        @SubscribeEvent
        public static void onRegisterItemColors(RegisterColorHandlersEvent.Item event) {
            ModItemColors.register(event);
        }

        @SubscribeEvent
        public static void onRegisterClientExtensions(RegisterClientExtensionsEvent event) {
            ModFluidClientExtensions.register(event);
        }

        @SubscribeEvent
        public static void onClientSetup(FMLClientSetupEvent event) {
            event.enqueueWork(() -> {
                ClientAeKeyRenderers.register();
                ModStorageCells.registerClientModels();
                registerMatterConvergingCrossbowProperties();
                registerDataCaptureBallProperties();
                registerLightSaberProperties();
                NeoForge.EVENT_BUS.addListener(ClientModEvents::onClientTickPost);
                NeoForge.EVENT_BUS.addListener(ClientModEvents::onScreenOpening);
                NeoForge.EVENT_BUS.addListener(ClientModEvents::onScreenInitPost);
                NeoForge.EVENT_BUS.addListener(ClientModEvents::onScreenRenderPost);
            });
        }

        @SubscribeEvent
        public static void onLoadComplete(FMLLoadCompleteEvent event) {
            event.enqueueWork(ClientAeKeyRenderers::reregister);
        }

        @SubscribeEvent
        public static void onRegisterKeyMappings(RegisterKeyMappingsEvent event) {
            event.register(ModKeyMappings.OPEN_PATTERN_PROVIDER);
            event.register(ModKeyMappings.RENAME_PATTERN_PROVIDER);
        }

        @SubscribeEvent
        public static void onRegisterScreens(RegisterMenuScreensEvent event) {
            InitScreens.register(event, ModMenus.DATA_RIPPER.get(), DataRipperScreen::new, "/screens/data_ripper.json");
            InitScreens.register(event, ModMenus.DATA_DISTRIBUTION_TOWER.get(), DataDistributionTowerScreen::new, "/screens/data_distribution_tower.json");
            InitScreens.register(event, ModMenus.DATA_EXTRACTOR.get(), DataExtractorScreen::new, "/screens/data_extractor.json");
            InitScreens.register(event, ModMenus.DATA_RIPPER_REASSEMBLER.get(), DataRipperReassemblerScreen::new, "/screens/data_reassembler.json");
            InitScreens.register(event, ModMenus.DATA_MIMETIC_FIELD.get(), DataMimeticFieldScreen::new, "/screens/data_mimetic_field.json");
            InitScreens.register(event, ModMenus.DATA_SOLAR_PANEL.get(), DataSolarPanelScreen::new, "/screens/me_solar_panel.json");
            InitScreens.register(event, ModMenus.DATA_TELEPORT_ANCHOR.get(), DataTeleportAnchorScreen::new, "/screens/data_teleport_anchor.json");
            InitScreens.register(event, ModMenus.ADAPTIVE_PATTERN_PROVIDER.get(), AdaptivePatternProviderScreen::new, "/screens/adaptive_pattern_provider.json");
            InitScreens.register(event, ModMenus.UNIVERSAL_ME_STORAGE.get(), UniversalMEStorageScreen::new, "/screens/universal_me_storage_terminal.json");
            InitScreens.register(event, ModMenus.UNIVERSAL_CRAFTING_TERM.get(), UniversalCraftingTermScreen::new, "/screens/universal_crafting_terminal.json");
            InitScreens.register(event, ModMenus.UNIVERSAL_PATTERN_ENCODING_TERM.get(), UniversalPatternEncodingTermScreen::new, "/screens/universal_pattern_encoding_terminal.json");
            InitScreens.register(event, ModMenus.UNIVERSAL_PATTERN_ACCESS_TERM.get(), UniversalPatternAccessTermScreen::new, "/screens/universal_pattern_access_terminal.json");
        }

        @SubscribeEvent
        public static void onRegisterRenderers(EntityRenderersEvent.RegisterRenderers event) {
            event.registerBlockEntityRenderer(ModBlockEntities.DATA_EXTRACTOR_BLOCK_ENTITY.get(), DataExtractorRenderer::new);
            event.registerBlockEntityRenderer(ModBlockEntities.DATA_DISTRIBUTION_TOWER_BLOCK_ENTITY.get(), DataDistributionTowerRenderer::new);
            event.registerBlockEntityRenderer(ModBlockEntities.DATA_MIMETIC_FIELD_BLOCK_ENTITY.get(), DataMimeticFieldRenderer::new);
            event.registerEntityRenderer(ModEntities.DISPERSING_DATA.get(), DispersingDataRenderer::new);
            event.registerEntityRenderer(ModEntities.LIGHT_BLADE_CHARGE.get(), LightBladeChargeRenderer::new);
            event.registerEntityRenderer(ModEntities.MATTER_CONVERGING_BOLT.get(), MatterConvergingBoltRenderer::new);
            event.registerEntityRenderer(ModEntities.THROWN_LIGHT_SABER.get(), ThrownLightSaberRenderer::new);
            event.registerEntityRenderer(ModEntities.TNT_CONFIGURABLE_PRIMED.get(), TntRenderer::new);
        }

        @SubscribeEvent
        public static void onRegisterAdditionalModels(ModelEvent.RegisterAdditional event) {
            event.register(ModelResourceLocation.standalone(Data_Energistics.id("block/drive/cells/mob_data_carrier")));
            event.register(ModelResourceLocation.standalone(Data_Energistics.id("block/drive/cells/ore_data_carrier")));
            event.register(ModelResourceLocation.standalone(Data_Energistics.id("block/drive/cells/crop_data_carrier")));
            event.register(ModelResourceLocation.standalone(Data_Energistics.id("block/data_distribution_tower_crystal_off")));
            event.register(ModelResourceLocation.standalone(Data_Energistics.id("block/data_distribution_tower_crystal_on")));
        }

        private static void registerMatterConvergingCrossbowProperties() {
            var item = ModItems.MATTER_CONVERGING_CROSSBOW.get();
            ItemProperties.register(item, Data_Energistics.id("loaded_special_light_saber"),
                    (stack, level, entity, seed) -> {
                        ChargedProjectiles charged = stack.getOrDefault(DataComponents.CHARGED_PROJECTILES, ChargedProjectiles.EMPTY);
                        return !charged.isEmpty() && MatterConvergingCrossbowItem.isSpecialLightSaberAmmo(charged.getItems().getFirst()) ? 1.0F : 0.0F;
                    });
            ItemProperties.register(item, Data_Energistics.id("load_stage"),
                    (stack, level, entity, seed) -> {
                        if (net.minecraft.world.item.CrossbowItem.isCharged(stack)) {
                            return 0.67F;
                        }
                        if (entity == null || !entity.isUsingItem() || entity.getUseItem() != stack) {
                            return 0.0F;
                        }

                        float progress = (float) (stack.getUseDuration(entity) - entity.getUseItemRemainingTicks()) / (float) MatterConvergingCrossbowItem.getChargeDuration(stack, entity);
                        progress = Mth.clamp(progress, 0.0F, 1.0F);
                        if (progress < 1.0F / 3.0F) {
                            return 0.0F;
                        }
                        if (progress >= 2.0F / 3.0F) {
                            return 0.67F;
                        }
                        return 0.42F;
                    });
            ItemProperties.register(item, ResourceLocation.withDefaultNamespace("pull"),
                    (stack, level, entity, seed) -> {
                        if (entity == null) {
                            return 0.0F;
                        }
                        if (net.minecraft.world.item.CrossbowItem.isCharged(stack)) {
                            return 0.0F;
                        }
                        return entity.getUseItem() != stack ? 0.0F : (float) (stack.getUseDuration(entity) - entity.getUseItemRemainingTicks()) / (float) MatterConvergingCrossbowItem.getChargeDuration(stack, entity);
                    });
            ItemProperties.register(item, ResourceLocation.withDefaultNamespace("pulling"),
                    (stack, level, entity, seed) -> entity != null && entity.isUsingItem() && entity.getUseItem() == stack && !net.minecraft.world.item.CrossbowItem.isCharged(stack) ? 1.0F : 0.0F);
            ItemProperties.register(item, ResourceLocation.withDefaultNamespace("charged"),
                    (stack, level, entity, seed) -> net.minecraft.world.item.CrossbowItem.isCharged(stack) ? 1.0F : 0.0F);
            ItemProperties.register(item, ResourceLocation.withDefaultNamespace("firework"),
                    (stack, level, entity, seed) -> {
                        var charged = stack.get(net.minecraft.core.component.DataComponents.CHARGED_PROJECTILES);
                        return charged != null && charged.contains(Items.FIREWORK_ROCKET) ? 1.0F : 0.0F;
                    });
        }

        private static void registerDataCaptureBallProperties() {
            ItemProperties.register(ModItems.DATA_CAPTURE_BALL.get(), Data_Energistics.id("fill_level"),
                    (stack, level, entity, seed) -> DataCaptureBallItem.getFillModelValue(stack));
        }

        private static void registerLightSaberProperties() {
            ItemProperties.register(ModItems.DATA_LIGHT_SABER.get(), Data_Energistics.id("powered"),
                    (stack, level, entity, seed) -> isPowered(stack) ? 1.0F : 0.0F);
            ItemProperties.register(ModItems.DATA_LIGHT_SABER.get(), Data_Energistics.id("light_saber_color"),
                    (stack, level, entity, seed) -> LightSaberColorData.getModelValue(stack));
            ItemProperties.register(ModItems.DATA_SANCTIFIER.get(), Data_Energistics.id("powered"),
                    (stack, level, entity, seed) -> isPowered(stack) ? 1.0F : 0.0F);
        }

        private static boolean isPowered(ItemStack stack) {
            return stack.getItem() instanceof PoweredEnergyItem poweredEnergyItem && poweredEnergyItem.getAECurrentPower(stack) > 0.0D;
        }

        public static void onClientTickPost(ClientTickEvent.Post event) {
            Minecraft minecraft = Minecraft.getInstance();
            if (minecraft.isPaused() || minecraft.level == null || minecraft.player == null) {
                return;
            }

            if ((minecraft.player.tickCount & 1) != 0) {
                return;
            }

            spawnMatterConvergingCrossbowParticles(minecraft, InteractionHand.MAIN_HAND);
            spawnMatterConvergingCrossbowParticles(minecraft, InteractionHand.OFF_HAND);
        }

        private static void spawnMatterConvergingCrossbowParticles(Minecraft minecraft, InteractionHand hand) {
            var player = minecraft.player;
            ItemStack stack = player.getItemInHand(hand);
            if (!stack.is(ModItems.MATTER_CONVERGING_CROSSBOW.get())) {
                return;
            }

            ChargedProjectiles charged = stack.getOrDefault(DataComponents.CHARGED_PROJECTILES, ChargedProjectiles.EMPTY);
            if (charged.isEmpty()) {
                return;
            }

            ItemStack ammo = charged.getItems().getFirst();
            Vec3 look = player.getViewVector(1.0F).normalize();
            Vec3 worldUp = new Vec3(0.0D, 1.0D, 0.0D);
            Vec3 right = look.cross(worldUp);
            if (right.lengthSqr() < 1.0E-6D) {
                right = new Vec3(1.0D, 0.0D, 0.0D);
            } else {
                right = right.normalize();
            }
            Vec3 up = right.cross(look).normalize();

            double side = getHandSide(player.getMainArm(), hand);
            Vec3 base = player.getEyePosition()
                    .add(look.scale(0.78D))
                    .add(right.scale(0D * side))
                    .add(up.scale(-0.30D));
            Vec3 velocity = look.scale(0.02D).add(up.scale(0.002D));

            Integer color = getMatterBallParticleColor(ammo);
            if (color == null) {
                return;
            }

            Vector3f rgb = new Vector3f(
                    ((color >> 16) & 0xFF) / 255.0F,
                    ((color >> 8) & 0xFF) / 255.0F,
                    (color & 0xFF) / 255.0F);
            DustParticleOptions particle = new DustParticleOptions(rgb, 0.85F);
            if (ammo.is(AEItems.SINGULARITY.asItem())) {
                Vec3 singularityBase = base.add(up.scale(-0.05D));
                minecraft.level.addParticle(particle,
                        singularityBase.x, singularityBase.y, singularityBase.z,
                        velocity.x, velocity.y, velocity.z);
                minecraft.level.addParticle(ParticleTypes.DRAGON_BREATH,
                        singularityBase.x, singularityBase.y, singularityBase.z,
                        velocity.x * 0.2D, velocity.y * 0.2D, velocity.z * 0.2D);
                return;
            }
            minecraft.level.addParticle(particle, base.x, base.y, base.z, velocity.x, velocity.y, velocity.z);
        }

        private static double getHandSide(HumanoidArm mainArm, InteractionHand hand) {
            boolean isRight = hand == InteractionHand.MAIN_HAND ? mainArm == HumanoidArm.RIGHT : mainArm != HumanoidArm.RIGHT;
            return isRight ? 1.0D : -1.0D;
        }

        private static Integer getMatterBallParticleColor(ItemStack ammo) {
            Item item = ammo.getItem();
            if (item instanceof PaintBallItem paintBallItem) {
                return paintBallItem.getColor().mediumVariant;
            }
            if (ammo.is(AEItems.SINGULARITY.asItem())) {
                return 0x7A3DFF;
            }
            if (item == AEItems.MATTER_BALL.asItem()) {
                return 0xD8D8D8;
            }
            return null;
        }

        public static void onScreenInitPost(ScreenEvent.Init.Post event) {
            maybeReplaceNativePatternEncodingScreen(event.getScreen(), true);
            Ae2WtLibCompat.maybeReplaceWirelessPatternEncodingScreen(event.getScreen(), true);
            UniversalTerminalScreenHook.onScreenInitPost(event);
        }

        public static void onScreenOpening(ScreenEvent.Opening event) {
            Screen replacement = maybeReplaceNativePatternEncodingScreen(event.getCurrentScreen(), false);
            if (replacement == null) {
                replacement = Ae2WtLibCompat.maybeReplaceWirelessPatternEncodingScreen(event.getCurrentScreen(), false);
            }
            if (replacement != null) {
                event.setNewScreen(replacement);
            }
        }

        public static void onScreenRenderPost(ScreenEvent.Render.Post event) {
            UniversalTerminalScreenHook.onScreenRenderPost(event);
            Ae2TerminalKeyOverlay.onScreenRenderPost(event);
        }

        private static Screen maybeReplaceNativePatternEncodingScreen(Screen currentScreen, boolean applyImmediately) {
            if (!(currentScreen instanceof PatternEncodingTermScreen<?> screen)) {
                return null;
            }

            if (currentScreen instanceof PatternEncodingPreviewScreen<?>) {
                return null;
            }

            if (!(screen.getMenu() instanceof PatternEncodingPreviewMenu)) {
                return null;
            }

            if (currentScreen.getClass() != PatternEncodingTermScreen.class) {
                return null;
            }

            Screen replacement = new NativePatternEncodingTermScreen(
                    screen.getMenu(),
                    screen.getMenu().getPlayerInventory(),
                    screen.getTitle(),
                    StyleManager.loadStyleDoc("/screens/terminals/pattern_encoding_terminal.json"));

            if (applyImmediately) {
                Minecraft minecraft = Minecraft.getInstance();
                if (minecraft.screen == currentScreen) {
                    minecraft.setScreen(replacement);
                }
            }

            return replacement;
        }
    }
}

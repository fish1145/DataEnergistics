package com.fish_dan_.data_energistics.bootstrap.common;

import com.fish_dan_.data_energistics.Data_Energistics;
import com.fish_dan_.data_energistics.ae2.DEAE2Keys;
import com.fish_dan_.data_energistics.ae2.dataflow.GenericKeyItemExportStrategy;
import com.fish_dan_.data_energistics.ae2.patternprovider.adaptive.AdaptivePatternProviderResolver;
import com.fish_dan_.data_energistics.blockentity.tower.network.domain.TowerGridServices;
import com.fish_dan_.data_energistics.common.crafting.dynamic.DynamicCraftingOutputAdapters;
import com.fish_dan_.data_energistics.common.crafting.virtual.VirtualCraftingOutputAdapters;
import com.fish_dan_.data_energistics.common.entrypoint.DataEnergisticsEntrypointLoader;
import com.fish_dan_.data_energistics.common.entrypoint.DataEnergisticsRegistrySnapshot;
import com.fish_dan_.data_energistics.common.entrypoint.provider.PatternProviderRuntimeBindings;
import com.fish_dan_.data_energistics.integration.ModFlags;
import com.fish_dan_.data_energistics.integration.curios.CuriosDataDistributionConnectorAccess;
import com.fish_dan_.data_energistics.integration.curios.CuriosOrbitalControlTerminalAccess;
import com.fish_dan_.data_energistics.integration.ftb.ultimine.DataCrystalPickaxeFtbUltimineCompat;
import com.fish_dan_.data_energistics.integration.map.ftbchunks.FtbChunksOrbitalClaimHints;
import com.fish_dan_.data_energistics.item.depot.DigitalStorageDepotKeyContainerItemStrategy;
import com.fish_dan_.data_energistics.orbital.control.ui.OrbitalControlPlayerMenu;
import com.fish_dan_.data_energistics.orbital.endpoint.OrbitalEndpointChunkTickets;
import com.fish_dan_.data_energistics.orbital.map.OrbitalClaimHints;
import com.fish_dan_.data_energistics.registry.DEBlockEntities;
import com.fish_dan_.data_energistics.registry.DEBlocks;
import com.fish_dan_.data_energistics.registry.DECreativeTabs;
import com.fish_dan_.data_energistics.registry.DEDataComponents;
import com.fish_dan_.data_energistics.registry.DEEntities;
import com.fish_dan_.data_energistics.registry.DEFluids;
import com.fish_dan_.data_energistics.registry.DEGameEvents;
import com.fish_dan_.data_energistics.registry.DEItems;
import com.fish_dan_.data_energistics.registry.DELegacyRegistryAliases;
import com.fish_dan_.data_energistics.registry.DEMenus;
import com.fish_dan_.data_energistics.registry.DEMobEffects;
import com.fish_dan_.data_energistics.registry.DEParticles;
import com.fish_dan_.data_energistics.registry.DERecipes;
import com.fish_dan_.data_energistics.registry.DEStructures;
import com.fish_dan_.data_energistics.registry.DEUpgrades;
import com.fish_dan_.data_energistics.registry.DEVerticalMultiBlocks;
import com.fish_dan_.data_energistics.registry.UniversalTerminalAdapters;

import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;
import net.neoforged.neoforge.registries.RegisterEvent;

public class CommonProxy {

    public static void init(IEventBus modEventBus) {
        CommonProxy instance = new CommonProxy();

        TowerGridServices.init();

        DEFluids.register(modEventBus);
        DEGameEvents.register(modEventBus);
        DEDataComponents.register(modEventBus);
        DEBlocks.register(modEventBus);
        DEItems.register(modEventBus);
        DEMobEffects.register(modEventBus);
        DEParticles.register(modEventBus);
        DEEntities.register(modEventBus);
        DEBlockEntities.register(modEventBus);
        DELegacyRegistryAliases.register();
        DECreativeTabs.register(modEventBus);
        DEMenus.register(modEventBus);
        DERecipes.register(modEventBus);
        DEStructures.register(modEventBus);
        DEVerticalMultiBlocks.init();
        DEUpgrades.registerPartModels();
        modEventBus.addListener(instance::commonSetup);
        modEventBus.addListener(EventPriority.LOWEST, instance::registerDepotContainerItemStrategies);
        modEventBus.addListener(EventPriority.LOWEST, instance::registerGenericKeyWorldExportStrategies);
        modEventBus.addListener(instance::registerAe2KeyTypes);
        modEventBus.addListener(CommonCapabilityRegistrar::register);
        modEventBus.addListener(CommonCapabilityRegistrar::registerPartCapabilities);
        modEventBus.addListener(CommonPayloadRegistrar::register);
        modEventBus.addListener(BuiltinDataPackRegistrar::register);
        modEventBus.addListener(OrbitalEndpointChunkTickets::registerController);

        CommonEventRegistrar.register();
    }

    private void commonSetup(final FMLCommonSetupEvent event) {
        event.enqueueWork(() -> {
            DataEnergisticsRegistrySnapshot snapshot = DataEnergisticsEntrypointLoader.initialize();
            UniversalTerminalAdapters.install(snapshot.universalTerminalRegistrations());
            VirtualCraftingOutputAdapters.install(snapshot.virtualCraftingOutputAdapters());
            DynamicCraftingOutputAdapters.install(snapshot.dynamicCraftingOutputAdapters());
            PatternProviderRuntimeBindings.install(snapshot.patternProviderRegistrations());
            AdaptivePatternProviderResolver.install(snapshot.adaptivePatternProviderRegistrations());
            DEUpgrades.init();
            OrbitalControlPlayerMenu.register();
            if (ModFlags.isCuriosLoaded()) {
                CuriosDataDistributionConnectorAccess.register();
                CuriosOrbitalControlTerminalAccess.register();
            }
            if (ModFlags.isFtbChunksLoaded()) {
                try {
                    OrbitalClaimHints.install(FtbChunksOrbitalClaimHints::isClaimed);
                    Data_Energistics.LOGGER.info("Registered FTB Chunks orbital tactical-map claim hints");
                } catch (RuntimeException | LinkageError exception) {
                    Data_Energistics.LOGGER.error(
                            "Could not register FTB Chunks orbital tactical-map claim hints",
                            exception);
                }
            }
            if (Data_Energistics.isModLoaded("ftbultimine")) {
                Data_Energistics.LOGGER.info("Registering Data Crystal Pickaxe FTB Ultimine duplicate ore integration");
                DataCrystalPickaxeFtbUltimineCompat.init();
            }
        });
    }

    private void registerDepotContainerItemStrategies(final FMLCommonSetupEvent event) {
        event.enqueueWork(DigitalStorageDepotKeyContainerItemStrategy::registerMissingStrategies);
    }

    private void registerGenericKeyWorldExportStrategies(final FMLCommonSetupEvent event) {
        event.enqueueWork(GenericKeyItemExportStrategy::registerMissingStrategies);
    }

    private void registerAe2KeyTypes(final RegisterEvent event) {
        DEAE2Keys.register(event);
    }
}

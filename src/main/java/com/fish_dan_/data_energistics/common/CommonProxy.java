package com.fish_dan_.data_energistics.common;

import com.fish_dan_.data_energistics.Data_Energistics;
import com.fish_dan_.data_energistics.ae2.DEAE2Keys;
import com.fish_dan_.data_energistics.ae2.dataflow.GenericKeyItemExportStrategy;
import com.fish_dan_.data_energistics.ae2.patternprovider.adaptive.AdaptivePatternProviderResolver;
import com.fish_dan_.data_energistics.blockentity.tower.network.domain.TowerGridServices;
import com.fish_dan_.data_energistics.common.crafting.virtual.VirtualCraftingOutputAdapters;
import com.fish_dan_.data_energistics.common.entrypoint.DataEnergisticsEntrypointLoader;
import com.fish_dan_.data_energistics.common.entrypoint.DataEnergisticsRegistrySnapshot;
import com.fish_dan_.data_energistics.common.entrypoint.provider.PatternProviderRuntimeBindings;
import com.fish_dan_.data_energistics.configuration.runtime.HolderFingerprintBridge;
import com.fish_dan_.data_energistics.integration.ModFlags;
import com.fish_dan_.data_energistics.integration.curios.CuriosDataDistributionConnectorAccess;
import com.fish_dan_.data_energistics.integration.ftbultimine.DataCrystalPickaxeFtbUltimineCompat;
import com.fish_dan_.data_energistics.item.depot.DigitalStorageDepotKeyContainerItemStrategy;
import com.fish_dan_.data_energistics.registry.DEBlockEntities;
import com.fish_dan_.data_energistics.registry.DEBlocks;
import com.fish_dan_.data_energistics.registry.DECreativeTabs;
import com.fish_dan_.data_energistics.registry.DEDataComponents;
import com.fish_dan_.data_energistics.registry.DEEntities;
import com.fish_dan_.data_energistics.registry.DEFluids;
import com.fish_dan_.data_energistics.registry.DEItems;
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

    public static void init(IEventBus modEventBus, HolderFingerprintBridge configurationReload) {
        CommonProxy instance = new CommonProxy();

        TowerGridServices.init();

        DEFluids.register(modEventBus);
        DEDataComponents.register(modEventBus);
        DEBlocks.register(modEventBus);
        DEItems.register(modEventBus);
        DEMobEffects.register(modEventBus);
        DEParticles.register(modEventBus);
        DEEntities.register(modEventBus);
        DEBlockEntities.register(modEventBus);
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

        CommonEventRegistrar.register(configurationReload);
    }

    private void commonSetup(final FMLCommonSetupEvent event) {
        event.enqueueWork(() -> {
            DataEnergisticsRegistrySnapshot snapshot = DataEnergisticsEntrypointLoader.initialize();
            UniversalTerminalAdapters.install(snapshot.universalTerminalRegistrations());
            VirtualCraftingOutputAdapters.install(snapshot.virtualCraftingOutputAdapters());
            PatternProviderRuntimeBindings.install(snapshot.patternProviderRegistrations());
            AdaptivePatternProviderResolver.install(snapshot.adaptivePatternProviderRegistrations());
            DEUpgrades.init();
            if (ModFlags.isCuriosLoaded()) {
                CuriosDataDistributionConnectorAccess.register();
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

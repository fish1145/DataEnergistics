package com.fish_dan_.data_energistics.common;

import com.fish_dan_.data_energistics.Data_Energistics;
import com.fish_dan_.data_energistics.ae2.AdaptivePatternProviderResolver;
import com.fish_dan_.data_energistics.ae2.GenericKeyItemExportStrategy;
import com.fish_dan_.data_energistics.ae2.ModAE2Keys;
import com.fish_dan_.data_energistics.blockentity.tower.network.domain.TowerGridServices;
import com.fish_dan_.data_energistics.common.crafting.virtual.VirtualCraftingOutputAdapters;
import com.fish_dan_.data_energistics.common.entrypoint.DataEnergisticsEntrypointLoader;
import com.fish_dan_.data_energistics.common.entrypoint.DataEnergisticsRegistrySnapshot;
import com.fish_dan_.data_energistics.common.entrypoint.provider.PatternProviderRuntimeBindings;
import com.fish_dan_.data_energistics.configuration.runtime.HolderFingerprintBridge;
import com.fish_dan_.data_energistics.integration.ModFlags;
import com.fish_dan_.data_energistics.integration.curios.CuriosDataDistributionConnectorAccess;
import com.fish_dan_.data_energistics.integration.ftbultimine.DataCrystalPickaxeFtbUltimineCompat;
import com.fish_dan_.data_energistics.item.DigitalStorageDepotKeyContainerItemStrategy;
import com.fish_dan_.data_energistics.registry.ModBlockEntities;
import com.fish_dan_.data_energistics.registry.ModBlocks;
import com.fish_dan_.data_energistics.registry.ModCreativeTabs;
import com.fish_dan_.data_energistics.registry.ModDataComponents;
import com.fish_dan_.data_energistics.registry.ModEntities;
import com.fish_dan_.data_energistics.registry.ModFluids;
import com.fish_dan_.data_energistics.registry.ModItems;
import com.fish_dan_.data_energistics.registry.ModMenus;
import com.fish_dan_.data_energistics.registry.ModMobEffects;
import com.fish_dan_.data_energistics.registry.ModParticles;
import com.fish_dan_.data_energistics.registry.ModRecipes;
import com.fish_dan_.data_energistics.registry.ModStructures;
import com.fish_dan_.data_energistics.registry.ModUpgrades;
import com.fish_dan_.data_energistics.registry.ModVerticalMultiBlocks;
import com.fish_dan_.data_energistics.registry.UniversalTerminalAdapters;

import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;
import net.neoforged.neoforge.registries.RegisterEvent;

public class CommonProxy {

    public static void init(IEventBus modEventBus, HolderFingerprintBridge configurationReload) {
        CommonProxy instance = new CommonProxy();

        TowerGridServices.init();

        ModFluids.register(modEventBus);
        ModDataComponents.register(modEventBus);
        ModBlocks.register(modEventBus);
        ModItems.register(modEventBus);
        ModMobEffects.register(modEventBus);
        ModParticles.register(modEventBus);
        ModEntities.register(modEventBus);
        ModBlockEntities.register(modEventBus);
        ModCreativeTabs.register(modEventBus);
        ModMenus.register(modEventBus);
        ModRecipes.register(modEventBus);
        ModStructures.register(modEventBus);
        ModVerticalMultiBlocks.init();
        ModUpgrades.registerPartModels();
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
            ModUpgrades.init();
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
        ModAE2Keys.register(event);
    }
}

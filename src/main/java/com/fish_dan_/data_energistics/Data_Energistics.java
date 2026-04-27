package com.fish_dan_.data_energistics;

import appeng.api.upgrades.Upgrades;
import appeng.core.definitions.AEItems;
import appeng.init.client.InitScreens;
import appeng.items.parts.PartModelsHelper;
import com.fish_dan_.data_energistics.ae2.DataFlowBusStrategies;
import com.fish_dan_.data_energistics.ae2.ModAE2Keys;
import com.fish_dan_.data_energistics.client.ModItemColors;
import com.fish_dan_.data_energistics.client.screen.DataRipperScreen;
import com.fish_dan_.data_energistics.item.EntityAccelerationCardItem;
import com.fish_dan_.data_energistics.registry.ModBlockEntities;
import com.fish_dan_.data_energistics.registry.ModBlocks;
import com.fish_dan_.data_energistics.registry.ModCreativeTabs;
import com.fish_dan_.data_energistics.registry.ModItems;
import com.fish_dan_.data_energistics.registry.ModMenus;
import com.fish_dan_.data_energistics.registry.ModStorageCells;
import com.mojang.logging.LogUtils;
import net.minecraft.client.renderer.item.ItemProperties;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;
import net.neoforged.neoforge.client.event.RegisterColorHandlersEvent;
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

        modEventBus.addListener(this::commonSetup);
        modEventBus.addListener(this::registerAe2KeyTypes);
        NeoForge.EVENT_BUS.register(this);
        modContainer.registerConfig(ModConfig.Type.COMMON, Config.SPEC);
    }

    private void commonSetup(final FMLCommonSetupEvent event) {
        event.enqueueWork(() -> {
            DataFlowBusStrategies.register();
            Upgrades.add(AEItems.ENERGY_CARD, ModItems.DATA_RIPPER.get(), 8, "item.data_energistics.data_ripper");
            Upgrades.add(ModItems.ENTITY_SPEED_CARD.get(), ModItems.DATA_RIPPER.get(), 4, "item.data_energistics.data_ripper");
            appeng.api.parts.PartModels.registerModels(
                    PartModelsHelper.createModels(ModItems.DATA_RIPPER.get().getPartClass())
            );
        });
    }

    private void registerAe2KeyTypes(final RegisterEvent event) {
        ModAE2Keys.register(event);
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
                ItemProperties.register(
                        ModItems.ENTITY_SPEED_CARD.get(),
                        ResourceLocation.fromNamespaceAndPath(MODID, "mult"),
                        (stack, level, entity, seed) -> EntityAccelerationCardItem.readMultiplier(stack)
                );
            });
        }

        @SubscribeEvent
        public static void onRegisterScreens(RegisterMenuScreensEvent event) {
            InitScreens.register(event, ModMenus.DATA_RIPPER.get(), DataRipperScreen::new, "/screens/data_ripper.json");
        }
    }
}

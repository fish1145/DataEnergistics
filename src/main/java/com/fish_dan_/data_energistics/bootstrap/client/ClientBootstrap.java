package com.fish_dan_.data_energistics.bootstrap.client;

import com.fish_dan_.data_energistics.bridge.DataEnergisticsClientBridgeAccess;
import com.fish_dan_.data_energistics.client.gui.DataEnergisticsTextureEditorResources;
import com.fish_dan_.data_energistics.client.hud.orbital.OrbitalControlHudLayer;
import com.fish_dan_.data_energistics.client.runtime.ClientRuntimeBridge;

import net.neoforged.bus.api.IEventBus;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import net.neoforged.fml.event.lifecycle.FMLLoadCompleteEvent;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import net.neoforged.neoforge.client.event.ModelEvent;
import net.neoforged.neoforge.client.event.RegisterClientTooltipComponentFactoriesEvent;
import net.neoforged.neoforge.client.event.RegisterColorHandlersEvent;
import net.neoforged.neoforge.client.event.RegisterGuiLayersEvent;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import net.neoforged.neoforge.client.event.RegisterMenuScreensEvent;
import net.neoforged.neoforge.client.event.RegisterParticleProvidersEvent;
import net.neoforged.neoforge.client.extensions.common.RegisterClientExtensionsEvent;

public final class ClientBootstrap {

    private ClientBootstrap() {}

    public static void init(IEventBus modEventBus) {
        DataEnergisticsClientBridgeAccess.register(new ClientRuntimeBridge());
        DataEnergisticsTextureEditorResources.register(modEventBus);
        modEventBus.register(ClientModEvents.class);
    }

    public static final class ClientModEvents {

        private ClientModEvents() {}

        @SubscribeEvent
        public static void onRegisterItemColors(RegisterColorHandlersEvent.Item event) {
            ClientItemColorRegistrar.register(event);
        }

        @SubscribeEvent
        public static void onRegisterClientExtensions(RegisterClientExtensionsEvent event) {
            ClientExtensionRegistrar.register(event);
        }

        @SubscribeEvent
        public static void onClientSetup(FMLClientSetupEvent event) {
            event.enqueueWork(ClientSetupRegistrar::register);
        }

        @SubscribeEvent
        public static void onLoadComplete(FMLLoadCompleteEvent event) {
            event.enqueueWork(ClientAeKeyRendererRegistrar::reregister);
        }

        @SubscribeEvent
        public static void onRegisterKeyMappings(RegisterKeyMappingsEvent event) {
            ClientKeyMappingRegistrar.register(event);
        }

        @SubscribeEvent
        public static void onRegisterScreens(RegisterMenuScreensEvent event) {
            ClientScreenRegistrar.register(event);
        }

        @SubscribeEvent
        public static void onRegisterGuiLayers(RegisterGuiLayersEvent event) {
            OrbitalControlHudLayer.register(event);
        }

        @SubscribeEvent
        public static void onRegisterClientTooltipComponents(RegisterClientTooltipComponentFactoriesEvent event) {
            ClientTooltipComponentRegistrar.register(event);
        }

        @SubscribeEvent
        public static void onRegisterParticleProviders(RegisterParticleProvidersEvent event) {
            ClientParticleProviderRegistrar.register(event);
        }

        @SubscribeEvent
        public static void onRegisterRenderers(EntityRenderersEvent.RegisterRenderers event) {
            ClientRendererRegistrar.register(event);
        }

        @SubscribeEvent
        public static void onRegisterAdditionalModels(ModelEvent.RegisterAdditional event) {
            ClientModelRegistrar.registerAdditionalModels(event);
        }

        @SubscribeEvent
        public static void onModifyBakingResult(ModelEvent.ModifyBakingResult event) {
            ClientModelRegistrar.modifyBakingResult(event);
        }
    }
}

package com.fish_dan_.data_energistics.bootstrap.client;

import com.fish_dan_.data_energistics.Data_Energistics;
import com.fish_dan_.data_energistics.client.ClientAeKeyRenderers;
import com.fish_dan_.data_energistics.client.ModFluidClientExtensions;
import com.fish_dan_.data_energistics.client.ModItemColors;
import com.fish_dan_.data_energistics.client.ModKeyMappings;
import com.fish_dan_.data_energistics.client.integration.CuriosDollRendererRegistry;
import com.fish_dan_.data_energistics.client.render.DataDistributionTowerRenderer;
import com.fish_dan_.data_energistics.client.render.DataExtractorRenderer;
import com.fish_dan_.data_energistics.client.render.DataMimeticFieldRenderer;
import com.fish_dan_.data_energistics.client.render.DataSanctumRenderer;
import com.fish_dan_.data_energistics.client.render.DigitalStorageDepotClientTooltipComponent;
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
import com.fish_dan_.data_energistics.client.screen.DataSanctumStatusScreen;
import com.fish_dan_.data_energistics.client.screen.DataSolarPanelScreen;
import com.fish_dan_.data_energistics.client.screen.DataTeleportAnchorScreen;
import com.fish_dan_.data_energistics.client.screen.DigitalStorageDepotScreen;
import com.fish_dan_.data_energistics.client.screen.PatternEncodingScreenRouter;
import com.fish_dan_.data_energistics.client.screen.UniversalCraftingTermScreen;
import com.fish_dan_.data_energistics.client.screen.UniversalMEStorageScreen;
import com.fish_dan_.data_energistics.client.screen.UniversalPatternAccessTermScreen;
import com.fish_dan_.data_energistics.client.screen.UniversalPatternEncodingTermScreen;
import com.fish_dan_.data_energistics.client.screen.UniversalTerminalScreenHook;
import com.fish_dan_.data_energistics.integration.ModFlags;
import com.fish_dan_.data_energistics.item.DataCaptureBallItem;
import com.fish_dan_.data_energistics.item.DigitalStorageDepotBlockItem;
import com.fish_dan_.data_energistics.item.DigitalStorageDepotTooltipComponent;
import com.fish_dan_.data_energistics.item.MatterConvergingCrossbowItem;
import com.fish_dan_.data_energistics.item.PoweredEnergyItem;
import com.fish_dan_.data_energistics.network.DigitalStorageDepotBucketModePayload;
import com.fish_dan_.data_energistics.network.DigitalStorageDepotScrollPayload;
import com.fish_dan_.data_energistics.registry.ModBlockEntities;
import com.fish_dan_.data_energistics.registry.ModEntities;
import com.fish_dan_.data_energistics.registry.ModFluids;
import com.fish_dan_.data_energistics.registry.ModItems;
import com.fish_dan_.data_energistics.registry.ModMenus;
import com.fish_dan_.data_energistics.registry.ModStorageCells;
import com.fish_dan_.data_energistics.util.LightSaberColorData;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.renderer.ItemBlockRenderTypes;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.TntRenderer;
import net.minecraft.client.renderer.item.ItemProperties;
import net.minecraft.client.resources.model.ModelResourceLocation;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.ChargedProjectiles;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import net.neoforged.fml.event.lifecycle.FMLLoadCompleteEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import net.neoforged.neoforge.client.event.InputEvent;
import net.neoforged.neoforge.client.event.ModelEvent;
import net.neoforged.neoforge.client.event.RegisterClientTooltipComponentFactoriesEvent;
import net.neoforged.neoforge.client.event.RegisterColorHandlersEvent;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import net.neoforged.neoforge.client.event.RegisterMenuScreensEvent;
import net.neoforged.neoforge.client.event.ScreenEvent;
import net.neoforged.neoforge.client.extensions.common.RegisterClientExtensionsEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.network.PacketDistributor;

import appeng.core.definitions.AEItems;
import appeng.init.client.InitScreens;
import appeng.items.misc.PaintBallItem;
import org.joml.Vector3f;

public final class ClientBootstrap {

    private ClientBootstrap() {}

    public static void init(IEventBus modEventBus) {
        modEventBus.register(ClientModEvents.class);
    }

    public static final class ClientModEvents {

        private ClientModEvents() {}

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
                registerFluidRenderLayers();
                registerMatterConvergingCrossbowProperties();
                registerDataCaptureBallProperties();
                registerLightSaberProperties();
                if (ModFlags.isCuriosLoaded()) {
                    CuriosDollRendererRegistry.register();
                }
                NeoForge.EVENT_BUS.addListener(ClientModEvents::onClientTickPost);
                NeoForge.EVENT_BUS.addListener(ClientModEvents::onMouseScroll);
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
            event.register(ModKeyMappings.TOGGLE_DIGITAL_STORAGE_DEPOT_BUCKET_MODE);
        }

        @SubscribeEvent
        public static void onRegisterScreens(RegisterMenuScreensEvent event) {
            InitScreens.register(event, ModMenus.DATA_RIPPER.get(), DataRipperScreen::new, "/screens/data_ripper.json");
            InitScreens.register(event, ModMenus.DATA_DISTRIBUTION_TOWER.get(), DataDistributionTowerScreen::new, "/screens/data_distribution_tower.json");
            InitScreens.register(event, ModMenus.DATA_EXTRACTOR.get(), DataExtractorScreen::new, "/screens/data_extractor.json");
            InitScreens.register(event, ModMenus.DATA_RIPPER_REASSEMBLER.get(), DataRipperReassemblerScreen::new, "/screens/data_reassembler.json");
            InitScreens.register(event, ModMenus.DATA_MIMETIC_FIELD.get(), DataMimeticFieldScreen::new, "/screens/data_mimetic_field.json");
            InitScreens.register(event, ModMenus.DATA_SOLAR_PANEL.get(), DataSolarPanelScreen::new, "/screens/me_solar_panel.json");
            InitScreens.register(event, ModMenus.DIGITAL_STORAGE_DEPOT.get(), DigitalStorageDepotScreen::new, "/screens/digital_storage_depot.json");
            InitScreens.register(event, ModMenus.DATA_TELEPORT_ANCHOR.get(), DataTeleportAnchorScreen::new, "/screens/data_teleport_anchor.json");
            InitScreens.register(event, ModMenus.DATA_SANCTUM_STATUS.get(), DataSanctumStatusScreen::new, "/screens/data_sanctum_status.json");
            InitScreens.register(event, ModMenus.ADAPTIVE_PATTERN_PROVIDER.get(), AdaptivePatternProviderScreen::new, "/screens/adaptive_pattern_provider.json");
            InitScreens.register(event, ModMenus.UNIVERSAL_ME_STORAGE.get(), UniversalMEStorageScreen::new, "/screens/universal_me_storage_terminal.json");
            InitScreens.register(event, ModMenus.UNIVERSAL_CRAFTING_TERM.get(), UniversalCraftingTermScreen::new, "/screens/universal_crafting_terminal.json");
            InitScreens.register(event, ModMenus.UNIVERSAL_PATTERN_ENCODING_TERM.get(), UniversalPatternEncodingTermScreen::new, "/screens/universal_pattern_encoding_terminal.json");
            InitScreens.register(event, ModMenus.UNIVERSAL_PATTERN_ACCESS_TERM.get(), UniversalPatternAccessTermScreen::new, "/screens/universal_pattern_access_terminal.json");
        }

        @SubscribeEvent
        public static void onRegisterClientTooltipComponents(RegisterClientTooltipComponentFactoriesEvent event) {
            event.register(DigitalStorageDepotTooltipComponent.class, DigitalStorageDepotClientTooltipComponent::new);
        }

        @SubscribeEvent
        public static void onRegisterRenderers(EntityRenderersEvent.RegisterRenderers event) {
            event.registerBlockEntityRenderer(ModBlockEntities.DATA_EXTRACTOR_BLOCK_ENTITY.get(), DataExtractorRenderer::new);
            event.registerBlockEntityRenderer(ModBlockEntities.DATA_DISTRIBUTION_TOWER_BLOCK_ENTITY.get(), DataDistributionTowerRenderer::new);
            event.registerBlockEntityRenderer(ModBlockEntities.DATA_MIMETIC_FIELD_BLOCK_ENTITY.get(), DataMimeticFieldRenderer::new);
            event.registerBlockEntityRenderer(ModBlockEntities.DATA_SANCTUM_BLOCK_ENTITY.get(), DataSanctumRenderer::new);
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
            event.register(DataSanctumRenderer.BLACK_HOLE_MODEL);
            event.register(DataSanctumRenderer.PORTAL_MODEL);
        }

        private static void registerFluidRenderLayers() {
            RenderType translucent = RenderType.translucent();
            ItemBlockRenderTypes.setRenderLayer(ModFluids.ENDER.get(), translucent);
            ItemBlockRenderTypes.setRenderLayer(ModFluids.FLOWING_ENDER.get(), translucent);
            ItemBlockRenderTypes.setRenderLayer(ModFluids.DATA_CORROSION_LIQUID.get(), translucent);
            ItemBlockRenderTypes.setRenderLayer(ModFluids.FLOWING_DATA_CORROSION_LIQUID.get(), translucent);
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
                        var charged = stack.get(DataComponents.CHARGED_PROJECTILES);
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

            while (ModKeyMappings.TOGGLE_DIGITAL_STORAGE_DEPOT_BUCKET_MODE.consumeClick()) {
                toggleDepotBucketMode(minecraft);
            }

            if ((minecraft.player.tickCount & 1) != 0) {
                return;
            }

            spawnMatterConvergingCrossbowParticles(minecraft, InteractionHand.MAIN_HAND);
            spawnMatterConvergingCrossbowParticles(minecraft, InteractionHand.OFF_HAND);
        }

        public static void onMouseScroll(InputEvent.MouseScrollingEvent event) {
            Minecraft minecraft = Minecraft.getInstance();
            if (minecraft.player == null || minecraft.screen != null) {
                return;
            }

            boolean controlDown = Screen.hasControlDown();
            boolean altDown = Screen.hasAltDown();
            if (controlDown == altDown) {
                return;
            }

            ItemStack mainHand = minecraft.player.getMainHandItem();
            ItemStack offHand = minecraft.player.getOffhandItem();
            boolean useMainHand = DigitalStorageDepotBlockItem.isDepotStack(mainHand);
            boolean useOffHand = !useMainHand && DigitalStorageDepotBlockItem.isDepotStack(offHand);
            if (!useMainHand && !useOffHand) {
                return;
            }

            double delta = event.getScrollDeltaY();
            if (delta == 0) {
                return;
            }

            PacketDistributor.sendToServer(new DigitalStorageDepotScrollPayload(delta < 0, useOffHand, altDown));
            event.setCanceled(true);
        }

        private static void toggleDepotBucketMode(Minecraft minecraft) {
            if (minecraft.screen != null || minecraft.player == null) {
                return;
            }

            ItemStack mainHand = minecraft.player.getMainHandItem();
            ItemStack offHand = minecraft.player.getOffhandItem();
            boolean useMainHand = DigitalStorageDepotBlockItem.isDepotStack(mainHand);
            boolean useOffHand = !useMainHand && DigitalStorageDepotBlockItem.isDepotStack(offHand);
            if (!useMainHand && !useOffHand) {
                return;
            }

            PacketDistributor.sendToServer(new DigitalStorageDepotBucketModePayload(useOffHand));
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

            Vec3 base = player.getEyePosition()
                    .add(look.scale(0.78D))
                    .add(right.scale(0D * getHandSide(player.getMainArm(), hand)))
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
            boolean isRight = (hand == InteractionHand.MAIN_HAND) == (mainArm == HumanoidArm.RIGHT);
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
            PatternEncodingScreenRouter.onScreenInitPost(event);
            UniversalTerminalScreenHook.onScreenInitPost(event);
        }

        public static void onScreenOpening(ScreenEvent.Opening event) {
            Screen replacement = PatternEncodingScreenRouter.routeOpeningScreen(event.getCurrentScreen());
            if (replacement != null) {
                event.setNewScreen(replacement);
            }
        }

        public static void onScreenRenderPost(ScreenEvent.Render.Post event) {
            UniversalTerminalScreenHook.onScreenRenderPost(event);
            Ae2TerminalKeyOverlay.onScreenRenderPost(event);
        }
    }
}

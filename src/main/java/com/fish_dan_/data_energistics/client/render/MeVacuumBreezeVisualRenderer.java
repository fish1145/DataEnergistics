package com.fish_dan_.data_energistics.client.render;

import com.fish_dan_.data_energistics.Data_Energistics;
import com.fish_dan_.data_energistics.registry.ModItems;

import net.minecraft.client.Minecraft;
import net.minecraft.client.model.BreezeModel;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.BreezeRenderer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.monster.breeze.Breeze;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;

@EventBusSubscriber(modid = Data_Energistics.MODID, value = Dist.CLIENT)
public final class MeVacuumBreezeVisualRenderer {

    private static final ResourceLocation BREEZE_WIND_TEXTURE = ResourceLocation.withDefaultNamespace("textures/entity/breeze/breeze_wind.png");
    private static final double FORWARD_DISTANCE = 1.55D;
    private static final float MODEL_SCALE = 0.72F;

    private static BreezeModel<Breeze> windModel;

    private MeVacuumBreezeVisualRenderer() {}

    @SubscribeEvent
    public static void onRenderLevelStage(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_PARTICLES) {
            return;
        }

        Minecraft minecraft = Minecraft.getInstance();
        Player player = minecraft.player;
        if (player == null || minecraft.level == null || !player.isUsingItem() || player.isShiftKeyDown()) {
            return;
        }

        ItemStack usedItem = player.getUseItem();
        if (!usedItem.is(ModItems.ME_VACUUM.get())) {
            return;
        }

        renderWind(event, minecraft, player);
    }

    private static void renderWind(RenderLevelStageEvent event, Minecraft minecraft, Player player) {
        BreezeModel<Breeze> model = getWindModel(minecraft);
        Vec3 partialEye = player.getEyePosition(event.getPartialTick().getGameTimeDeltaPartialTick(true));
        Vec3 look = player.getLookAngle().normalize();
        Vec3 position = partialEye.add(look.scale(FORWARD_DISTANCE));
        Vec3 camera = event.getCamera().getPosition();
        float age = player.tickCount + event.getPartialTick().getGameTimeDeltaPartialTick(true);
        float yaw = (float) Math.toDegrees(Math.atan2(look.x, look.z));
        RenderType renderType = RenderType.breezeWind(BREEZE_WIND_TEXTURE, age * 0.02F % 1.0F, 0.0F);

        model.root().getAllParts().forEach(part -> {
            part.resetPose();
            part.visible = false;
        });
        model.wind().visible = true;

        PoseStack poseStack = event.getPoseStack();
        poseStack.pushPose();
        poseStack.translate(position.x - camera.x, position.y - camera.y, position.z - camera.z);
        poseStack.mulPose(Axis.YP.rotationDegrees(180.0F - yaw));
        poseStack.mulPose(Axis.YP.rotationDegrees(age * 14.0F));
        poseStack.scale(-MODEL_SCALE, -MODEL_SCALE, MODEL_SCALE);
        poseStack.translate(0.0D, -1.5D, 0.0D);

        MultiBufferSource.BufferSource buffer = minecraft.renderBuffers().bufferSource();
        VertexConsumer consumer = buffer.getBuffer(renderType);
        BreezeRenderer.enable(model, model.wind()).renderToBuffer(poseStack, consumer, LightTexture.FULL_BRIGHT,
                OverlayTexture.NO_OVERLAY);
        buffer.endBatch(renderType);
        poseStack.popPose();
    }

    private static BreezeModel<Breeze> getWindModel(Minecraft minecraft) {
        if (windModel == null) {
            windModel = new BreezeModel<>(minecraft.getEntityModels().bakeLayer(ModelLayers.BREEZE_WIND));
        }
        return windModel;
    }
}

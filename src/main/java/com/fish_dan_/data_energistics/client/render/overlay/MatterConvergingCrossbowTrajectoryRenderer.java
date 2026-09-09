package com.fish_dan_.data_energistics.client.render.overlay;

import com.fish_dan_.data_energistics.Data_Energistics;
import com.fish_dan_.data_energistics.item.powered.MatterConvergingCrossbowItem;
import com.fish_dan_.data_energistics.item.powered.MatterConvergingCrossbowMode;
import com.fish_dan_.data_energistics.registry.DEDataComponents;

import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent.Stage;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;

@EventBusSubscriber(modid = Data_Energistics.MODID, value = Dist.CLIENT)
public final class MatterConvergingCrossbowTrajectoryRenderer {

    private static final int SEGMENTS = 24;
    private static final double MAX_DISTANCE = 32.0D;
    private static final double GRAVITY = 0.045D;

    private MatterConvergingCrossbowTrajectoryRenderer() {}

    @SubscribeEvent
    public static void render(RenderLevelStageEvent event) {
        if (event.getStage() != Stage.AFTER_TRANSLUCENT_BLOCKS) return;
        Minecraft minecraft = Minecraft.getInstance();
        Player player = minecraft.player;
        if (player == null || minecraft.level == null || !player.isUsingItem()) return;
        InteractionHand hand = player.getUsedItemHand();
        ItemStack stack = player.getItemInHand(hand);
        if (!(stack.getItem() instanceof MatterConvergingCrossbowItem) || MatterConvergingCrossbowMode.fromId(stack.getOrDefault(DEDataComponents.MATTER_CONVERGING_CROSSBOW_MODE.get(), MatterConvergingCrossbowMode.GRENADE.id())) != MatterConvergingCrossbowMode.GRENADE) return;
        float progress = Math.clamp((float) (stack.getUseDuration(player) - player.getUseItemRemainingTicks()) / MatterConvergingCrossbowItem.getChargeDuration(stack, player), 0.0F, 1.0F);
        double distance = 4.0D + 28.0D * progress;
        Vec3 start = player.getEyePosition().add(player.getViewVector(1.0F).scale(0.65D));
        Vec3 direction = player.getViewVector(1.0F).normalize();
        Vec3 end = start.add(direction.scale(distance));
        HitResult hit = minecraft.level.clip(new ClipContext(start, end, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, player));
        if (hit.getType() != HitResult.Type.MISS) end = hit.getLocation();

        Camera camera = event.getCamera();
        Vec3 cameraPos = camera.getPosition();
        PoseStack poseStack = event.getPoseStack();
        poseStack.pushPose();
        poseStack.translate(-cameraPos.x, -cameraPos.y, -cameraPos.z);
        MultiBufferSource.BufferSource buffer = minecraft.renderBuffers().bufferSource();
        VertexConsumer vertices = buffer.getBuffer(RenderType.lines());
        Vec3 previous = start;
        double flight = distance / 3.15D;
        for (int segment = 1; segment <= SEGMENTS; segment++) {
            double ratio = segment / (double) SEGMENTS;
            Vec3 point = start.add(direction.scale(distance * ratio));
            point = point.add(0.0D, -GRAVITY * flight * flight * ratio * ratio * 0.5D, 0.0D);
            line(vertices, poseStack.last(), previous, point, 0.18F, 0.92F, 1.0F, 0.72F);
            previous = point;
        }
        poseStack.popPose();
        buffer.endBatch(RenderType.lines());
    }

    private static void line(VertexConsumer vertices, PoseStack.Pose pose, Vec3 start, Vec3 end,
                             float red, float green, float blue, float alpha) {
        Vec3 normal = end.subtract(start).normalize();
        vertices.addVertex(pose, (float) start.x, (float) start.y, (float) start.z)
                .setColor(red, green, blue, alpha).setNormal(pose, (float) normal.x, (float) normal.y, (float) normal.z);
        vertices.addVertex(pose, (float) end.x, (float) end.y, (float) end.z)
                .setColor(red, green, blue, alpha).setNormal(pose, (float) normal.x, (float) normal.y, (float) normal.z);
    }
}

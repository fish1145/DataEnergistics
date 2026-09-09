package com.fish_dan_.data_energistics.client.render.overlay;

import com.fish_dan_.data_energistics.Data_Energistics;
import com.fish_dan_.data_energistics.client.render.item.crossbow.CrossbowTrajectorySpace;
import com.fish_dan_.data_energistics.item.powered.MatterConvergingCrossbowItem;
import com.fish_dan_.data_energistics.item.powered.MatterConvergingCrossbowMode;
import com.fish_dan_.data_energistics.registry.DEDataComponents;

import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent.Stage;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.jspecify.annotations.Nullable;

@EventBusSubscriber(modid = Data_Energistics.MODID, value = Dist.CLIENT)
public final class MatterConvergingCrossbowTrajectoryRenderer {

    private static final double GRAVITY = 0.045D;
    private static @Nullable Matrix4f worldProjection;

    private MatterConvergingCrossbowTrajectoryRenderer() {}

    @SubscribeEvent
    public static void captureWorldProjection(RenderLevelStageEvent event) {
        if (event.getStage() == Stage.AFTER_LEVEL) {
            worldProjection = new Matrix4f(event.getProjectionMatrix());
        }
    }

    /** Runs while the held model's current transform is available, before ItemRenderer's -0.5 recenter. */
    public static void renderFromModel(LivingEntity entity, InteractionHand hand, ItemStack stack,
                                       ItemDisplayContext context, PoseStack poseStack, Matrix4f root) {
        Minecraft minecraft = Minecraft.getInstance();
        if (entity != minecraft.player || minecraft.level == null || !entity.isUsingItem() || entity.getUsedItemHand() != hand || minecraft.screen != null || MatterConvergingCrossbowMode.fromId(stack.getOrDefault(DEDataComponents.MATTER_CONVERGING_CROSSBOW_MODE.get(),
                MatterConvergingCrossbowMode.GRENADE.id())) != MatterConvergingCrossbowMode.GRENADE) {
            return;
        }
        // Do not draw a third-person copy during first-person shadow/entity passes.
        if (context.firstPerson() != minecraft.options.getCameraType().isFirstPerson()) {
            return;
        }
        Matrix4f projection = context.firstPerson() ? worldProjection : RenderSystem.getProjectionMatrix();
        if (projection == null) {
            return;
        }
        Camera camera = minecraft.gameRenderer.getMainCamera();
        Matrix4f modelToRender = new Matrix4f(poseStack.last().pose()).translate(-0.5F, -0.5F, -0.5F).mul(root);
        CrossbowTrajectorySpace space = new CrossbowTrajectorySpace(modelToRender, RenderSystem.getModelViewMatrix(),
                RenderSystem.getProjectionMatrix(), projection, camera.rotation());
        Vec3 cameraPosition = camera.getPosition();
        Vec3 start = cameraPosition.add(new Vec3(space.muzzleOffset()));
        Vec3 direction = new Vec3(space.firingDirection());
        float partialTick = minecraft.getTimer().getGameTimeDeltaPartialTick(true);
        float progress = Math.clamp((stack.getUseDuration(entity) - entity.getUseItemRemainingTicks() + partialTick) / MatterConvergingCrossbowItem.getChargeDuration(stack, entity), 0.0F, 1.0F);
        double distance = 4.0D + 28.0D * progress;
        double flight = distance / 3.15D;
        int segments = (int) Math.ceil(distance * 4.0D);
        VertexConsumer vertices = minecraft.renderBuffers().bufferSource().getBuffer(RenderType.lines());
        Vec3 previous = start;
        for (int segment = 1; segment <= segments; segment++) {
            double ratio = segment / (double) segments;
            Vec3 point = start.add(direction.scale(distance * ratio))
                    .add(0.0D, -0.5D * GRAVITY * flight * flight * ratio * ratio, 0.0D);
            HitResult hit = minecraft.level.clip(new ClipContext(previous, point, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, entity));
            boolean blocked = hit.getType() != HitResult.Type.MISS;
            Vec3 end = blocked ? hit.getLocation() : point;
            line(vertices, space, cameraPosition, previous, end);
            if (blocked) {
                break;
            }
            previous = point;
        }
        // The enclosing hand/entity renderer flushes this buffer with its matching projection.
    }

    private static void line(VertexConsumer vertices, CrossbowTrajectorySpace space, Vec3 camera,
                             Vec3 start, Vec3 end) {
        Vector3f from = space.renderPosition(start.subtract(camera).toVector3f());
        Vector3f to = space.renderPosition(end.subtract(camera).toVector3f());
        Vector3f normal = new Vector3f(to).sub(from);
        if (normal.lengthSquared() < 1.0E-10F) {
            return;
        }
        normal.normalize();
        vertices.addVertex(from.x, from.y, from.z).setColor(0.18F, 0.92F, 1.0F, 0.72F)
                .setNormal(normal.x, normal.y, normal.z);
        vertices.addVertex(to.x, to.y, to.z).setColor(0.18F, 0.92F, 1.0F, 0.72F)
                .setNormal(normal.x, normal.y, normal.z);
    }
}

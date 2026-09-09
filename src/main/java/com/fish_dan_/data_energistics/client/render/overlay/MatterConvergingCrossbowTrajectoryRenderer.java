package com.fish_dan_.data_energistics.client.render.overlay;

import com.fish_dan_.data_energistics.Data_Energistics;
import com.fish_dan_.data_energistics.client.input.cannon.CannonChargeInput;
import com.fish_dan_.data_energistics.client.render.item.crossbow.CrossbowTrajectorySpace;
import com.fish_dan_.data_energistics.item.powered.MatterConvergingCrossbowItem;
import com.fish_dan_.data_energistics.item.powered.MatterConvergingCrossbowMode;
import com.fish_dan_.data_energistics.item.powered.cannon.CannonBallistics;
import com.fish_dan_.data_energistics.item.powered.cannon.CannonCharge;
import com.fish_dan_.data_energistics.registry.DEDataComponents;

import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.EntityHitResult;
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
        if (entity != minecraft.player || minecraft.level == null || minecraft.screen != null || !MatterConvergingCrossbowItem.isCannon(stack)) {
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
        float partialTick = minecraft.getTimer().getGameTimeDeltaPartialTick(true);
        Vec3 eye = entity.getEyePosition(partialTick);
        Vec3 aimEnd = eye.add(entity.getViewVector(partialTick).scale(256.0D));
        HitResult aimHit = minecraft.level.clip(new ClipContext(eye, aimEnd, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, entity));
        Vec3 aimPoint = aimHit.getType() == HitResult.Type.MISS ? aimEnd : aimHit.getLocation();
        Vec3 direction = aimPoint.subtract(start).normalize();
        CannonChargeInput.recordMuzzle(hand, start, direction, minecraft.level.getGameTime());
        CannonCharge charge = stack.get(DEDataComponents.CANNON_CHARGE.get());
        if (charge == null || charge.mode() != MatterConvergingCrossbowMode.GRENADE || !charge.belongsTo(entity, hand, MatterConvergingCrossbowItem.mode(stack))) return;
        MatterConvergingCrossbowItem item = (MatterConvergingCrossbowItem) stack.getItem();
        boolean saberAmmo = item.cannonUsesSaberAmmo(stack);
        Vec3 velocity = CannonBallistics.launchVelocity(charge.mode(), charge.progress(minecraft.level.getGameTime()), direction, item.cannonAmmoSpeed(stack));
        VertexConsumer vertices = minecraft.renderBuffers().bufferSource().getBuffer(RenderType.lines());
        Vec3 previous = start;
        for (int tick = 0; tick < CannonBallistics.PREVIEW_TICKS; tick++) {
            Vec3 point = previous.add(velocity);
            HitResult hit = minecraft.level.clip(new ClipContext(previous, point, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, entity));
            boolean blocked = hit.getType() != HitResult.Type.MISS;
            Vec3 end = blocked ? hit.getLocation() : point;
            EntityHitResult entityHit = ProjectileUtil.getEntityHitResult(minecraft.level, entity, previous, end,
                    new AABB(previous, end).inflate(0.3D), target -> target != entity && target.isPickable() && !target.isSpectator());
            if (entityHit != null) {
                end = entityHit.getLocation();
                blocked = true;
            }
            line(vertices, space, cameraPosition, previous, end);
            if (blocked) {
                break;
            }
            velocity = CannonBallistics.nextVelocity(velocity, charge.mode(), minecraft.level.isWaterAt(BlockPos.containing(previous)), saberAmmo);
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

package com.fish_dan_.data_energistics.client.render.orbital;

import com.fish_dan_.data_energistics.Data_Energistics;
import com.fish_dan_.data_energistics.orbital.attack.OrbitalAttackMode;
import com.fish_dan_.data_energistics.orbital.attack.OrbitalAttackPhase;
import com.fish_dan_.data_energistics.orbital.attack.OrbitalAttackVisualSnapshot;
import com.fish_dan_.data_energistics.orbital.model.OrbitalWeaponLifecycleState;
import com.fish_dan_.data_energistics.orbital.projection.OrbitalProjectionVisualSnapshot;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Draws the server-authoritative orbital visual caches as batched placeholder geometry.
 *
 * <p>
 * The renderer owns no gameplay state and never predicts attack progress. It provides the complete culling, LOD and
 * batching path now, while dedicated textures and baked meshes can replace individual geometry methods later without
 * changing network or lifecycle behavior.
 * </p>
 */
@EventBusSubscriber(modid = Data_Energistics.MODID, value = Dist.CLIENT)
public final class OrbitalWorldProjectionRenderer {

    private static final double FULL_DETAIL_DISTANCE_SQUARED = 1_024.0D * 1_024.0D;
    private static final double REDUCED_DETAIL_DISTANCE_SQUARED = 4_096.0D * 4_096.0D;
    private static final double MAX_RENDER_DISTANCE_SQUARED = 16_384.0D * 16_384.0D;
    private static final int MAX_FULL_DETAIL_PROJECTIONS = 4;
    private static final int MAX_VISIBLE_PROJECTIONS = 64;
    private static final int MAX_VISIBLE_ATTACK_ECHOES = 32;
    private static final RenderType LINES = RenderType.lines();

    private OrbitalWorldProjectionRenderer() {}

    @SubscribeEvent
    public static void onRenderLevelStage(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_TRANSLUCENT_BLOCKS) {
            return;
        }

        Minecraft minecraft = Minecraft.getInstance();
        ClientLevel level = minecraft.level;
        if (level == null) {
            return;
        }
        ResourceLocation dimensionId = level.dimension().location();
        List<OrbitalProjectionVisualSnapshot> projections = projectionBaseline(dimensionId);
        List<OrbitalAttackVisualSnapshot> attacks = attackBaseline(dimensionId);
        if (projections.isEmpty() && attacks.isEmpty()) {
            return;
        }

        Vec3 camera = event.getCamera().getPosition();
        float partialTick = event.getPartialTick().getGameTimeDeltaPartialTick(true);
        MultiBufferSource.BufferSource buffers = minecraft.renderBuffers().bufferSource();
        VertexConsumer consumer = buffers.getBuffer(LINES);
        PoseStack poseStack = event.getPoseStack();
        boolean rendered = false;

        poseStack.pushPose();
        poseStack.translate(-camera.x, -camera.y, -camera.z);

        ArrayList<OrbitalProjectionVisualSnapshot> orderedProjections = new ArrayList<>(projections);
        orderedProjections.sort(Comparator.comparingDouble(projection -> projectionDistanceSquared(camera, projection)));
        int fullDetailCount = 0;
        int visibleProjectionCount = 0;
        for (OrbitalProjectionVisualSnapshot projection : orderedProjections) {
            if (visibleProjectionCount >= MAX_VISIBLE_PROJECTIONS) {
                break;
            }
            double distanceSquared = projectionDistanceSquared(camera, projection);
            if (distanceSquared > MAX_RENDER_DISTANCE_SQUARED) {
                continue;
            }
            AABB bounds = projectionBounds(projection);
            if (!event.getFrustum().isVisible(bounds)) {
                continue;
            }

            Detail detail;
            if (distanceSquared <= FULL_DETAIL_DISTANCE_SQUARED
                    && fullDetailCount < MAX_FULL_DETAIL_PROJECTIONS) {
                detail = Detail.FULL;
                fullDetailCount++;
            } else if (distanceSquared <= REDUCED_DETAIL_DISTANCE_SQUARED) {
                detail = Detail.REDUCED;
            } else {
                detail = Detail.IMPOSTOR;
            }
            renderProjection(poseStack, consumer, projection, detail, partialTick);
            visibleProjectionCount++;
            rendered = true;
        }

        ArrayList<OrbitalAttackVisualSnapshot> orderedAttacks = new ArrayList<>(attacks);
        orderedAttacks.sort(Comparator.comparingDouble(attack -> attackDistanceSquared(camera, level, attack)));
        int visibleAttackCount = 0;
        for (OrbitalAttackVisualSnapshot attack : orderedAttacks) {
            if (visibleAttackCount >= MAX_VISIBLE_ATTACK_ECHOES) {
                break;
            }
            double distanceSquared = attackDistanceSquared(camera, level, attack);
            if (distanceSquared > MAX_RENDER_DISTANCE_SQUARED) {
                continue;
            }
            AABB bounds = attackBounds(level, attack);
            if (!event.getFrustum().isVisible(bounds)) {
                continue;
            }
            Detail detail = distanceSquared <= FULL_DETAIL_DISTANCE_SQUARED
                    ? Detail.FULL
                    : (distanceSquared <= REDUCED_DETAIL_DISTANCE_SQUARED ? Detail.REDUCED : Detail.IMPOSTOR);
            renderAttackEcho(poseStack, consumer, level, attack, detail, partialTick);
            visibleAttackCount++;
            rendered = true;
        }

        poseStack.popPose();
        if (rendered) {
            buffers.endBatch(LINES);
        }
    }

    private static List<OrbitalProjectionVisualSnapshot> projectionBaseline(ResourceLocation dimensionId) {
        if (!dimensionId.equals(OrbitalProjectionVisualClientState.dimensionId())) {
            return List.of();
        }
        return OrbitalProjectionVisualClientState.projections();
    }

    private static List<OrbitalAttackVisualSnapshot> attackBaseline(ResourceLocation dimensionId) {
        if (!dimensionId.equals(OrbitalAttackVisualClientState.dimensionId())) {
            return List.of();
        }
        return OrbitalAttackVisualClientState.attacks();
    }

    private static double projectionDistanceSquared(Vec3 camera, OrbitalProjectionVisualSnapshot projection) {
        return camera.distanceToSqr(
                projection.anchor().getX() + 0.5D,
                projection.projectionY(),
                projection.anchor().getZ() + 0.5D);
    }

    private static double attackDistanceSquared(
                                                Vec3 camera,
                                                ClientLevel level,
                                                OrbitalAttackVisualSnapshot attack) {
        return camera.distanceToSqr(
                attack.target().getX() + 0.5D,
                attackEchoY(level, attack),
                attack.target().getZ() + 0.5D);
    }

    private static AABB projectionBounds(OrbitalProjectionVisualSnapshot projection) {
        double centerX = projection.anchor().getX() + 0.5D;
        double centerZ = projection.anchor().getZ() + 0.5D;
        return new AABB(
                centerX - 260.0D,
                projection.projectionY() - 72.0D,
                centerZ - 72.0D,
                centerX + 260.0D,
                projection.projectionY() + 72.0D,
                centerZ + 72.0D);
    }

    private static AABB attackBounds(ClientLevel level, OrbitalAttackVisualSnapshot attack) {
        double centerX = attack.target().getX() + 0.5D;
        double centerZ = attack.target().getZ() + 0.5D;
        double echoY = attackEchoY(level, attack);
        return new AABB(
                centerX - 72.0D,
                Math.min(attack.target().getY(), echoY - 72.0D),
                centerZ - 72.0D,
                centerX + 72.0D,
                echoY + 72.0D,
                centerZ + 72.0D);
    }

    private static void renderProjection(
                                         PoseStack poseStack,
                                         VertexConsumer consumer,
                                         OrbitalProjectionVisualSnapshot projection,
                                         Detail detail,
                                         float partialTick) {
        double centerX = projection.anchor().getX() + 0.5D;
        double centerY = projection.projectionY();
        double centerZ = projection.anchor().getZ() + 0.5D;
        float pulse = pulse(projection.animationTime(), projection.randomSeed(), partialTick, 0.08F);
        float alpha = projection.lifecycleState() == OrbitalWeaponLifecycleState.REDEPLOYING
                ? 0.25F + 0.45F * pulse
                : (projection.lifecycleState() == OrbitalWeaponLifecycleState.RESERVE_GRACE ? 0.42F : 0.78F);
        float red = projection.lifecycleState() == OrbitalWeaponLifecycleState.RESERVE_GRACE ? 0.90F : 0.20F;
        float green = projection.lifecycleState() == OrbitalWeaponLifecycleState.RESERVE_GRACE ? 0.45F : 0.78F;
        float blue = 1.0F;

        if (detail == Detail.IMPOSTOR) {
            renderBox(poseStack, consumer, box(centerX, centerY, centerZ, 48.0D, 16.0D, 16.0D), red, green, blue, alpha);
            renderBox(
                    poseStack,
                    consumer,
                    new AABB(
                            centerX - 0.5D,
                            projection.anchor().getY() + 0.5D,
                            centerZ - 0.5D,
                            centerX + 0.5D,
                            centerY,
                            centerZ + 0.5D),
                    red,
                    green,
                    blue,
                    0.25F + 0.30F * pulse);
            return;
        }

        renderBox(poseStack, consumer, box(centerX, centerY, centerZ, 64.0D, 32.0D, 32.0D), red, green, blue, alpha);
        renderBox(poseStack, consumer, box(centerX, centerY + 20.0D, centerZ, 120.0D, 4.0D, 4.0D), red, green, blue, alpha);
        renderBox(poseStack, consumer, box(centerX, centerY - 20.0D, centerZ, 120.0D, 4.0D, 4.0D), red, green, blue, alpha);
        renderBox(poseStack, consumer, box(centerX, centerY, centerZ - 18.0D, 480.0D, 5.0D, 5.0D), red, green, blue, alpha);
        renderBox(poseStack, consumer, box(centerX, centerY, centerZ + 18.0D, 480.0D, 5.0D, 5.0D), red, green, blue, alpha);
        renderBox(poseStack, consumer, box(centerX + 96.0D, centerY, centerZ, 8.0D, 88.0D, 88.0D), red, green, blue, alpha);
        renderBox(poseStack, consumer, box(centerX - 176.0D, centerY, centerZ, 64.0D, 36.0D, 52.0D), red, green, blue, alpha);

        if (detail == Detail.REDUCED) {
            return;
        }

        for (int offset = -224; offset <= 224; offset += 32) {
            float segmentAlpha = projection.lifecycleState() == OrbitalWeaponLifecycleState.REDEPLOYING
                    && Math.floorMod(offset / 32 + (int) projection.animationTime() / 4, 3) == 0
                            ? alpha * 0.18F
                            : alpha;
            renderBox(
                    poseStack,
                    consumer,
                    box(centerX + offset, centerY, centerZ, 18.0D, 42.0D, 52.0D),
                    red,
                    green,
                    blue,
                    segmentAlpha);
        }
        for (int ring = 0; ring < 4; ring++) {
            double size = 32.0D + ring * 16.0D;
            renderBox(
                    poseStack,
                    consumer,
                    box(centerX + 96.0D + ring * 6.0D, centerY, centerZ, 2.0D, size, size),
                    0.35F,
                    0.85F,
                    1.0F,
                    alpha * (0.55F + pulse * 0.35F));
        }
        renderBox(poseStack, consumer, box(centerX - 96.0D, centerY, centerZ - 12.0D, 96.0D, 3.0D, 3.0D), 0.65F, 0.92F, 1.0F, alpha);
        renderBox(poseStack, consumer, box(centerX - 96.0D, centerY, centerZ + 12.0D, 96.0D, 3.0D, 3.0D), 0.65F, 0.92F, 1.0F, alpha);
    }

    private static void renderAttackEcho(
                                         PoseStack poseStack,
                                         VertexConsumer consumer,
                                         ClientLevel level,
                                         OrbitalAttackVisualSnapshot attack,
                                         Detail detail,
                                         float partialTick) {
        double centerX = attack.target().getX() + 0.5D;
        double centerY = attackEchoY(level, attack);
        double centerZ = attack.target().getZ() + 0.5D;
        float pulse = pulse(attack.phaseAge(), attack.randomSeed(), partialTick, 0.12F);
        float alpha = attack.phase() == OrbitalAttackPhase.RESERVED_WARNING
                ? 0.35F + 0.55F * pulse
                : 0.82F;
        float[] color = attackColor(attack.mode());

        renderBox(
                poseStack,
                consumer,
                new AABB(
                        centerX - 0.5D,
                        attack.target().getY(),
                        centerZ - 0.5D,
                        centerX + 0.5D,
                        centerY,
                        centerZ + 0.5D),
                color[0],
                color[1],
                color[2],
                alpha * 0.55F);
        if (detail == Detail.IMPOSTOR) {
            renderBox(poseStack, consumer, box(centerX, centerY, centerZ, 24.0D, 12.0D, 24.0D), color[0], color[1], color[2], alpha);
            return;
        }

        switch (attack.mode()) {
            case KINETIC -> renderKineticEcho(poseStack, consumer, centerX, centerY, centerZ, detail, color, alpha);
            case DIRECTED_ENERGY -> renderDirectedEcho(poseStack, consumer, centerX, centerY, centerZ, detail, color, alpha, pulse);
            case DIGITAL_ANNIHILATION -> renderDigitalEcho(poseStack, consumer, centerX, centerY, centerZ, detail, color, alpha);
        }
    }

    private static void renderKineticEcho(
                                          PoseStack poseStack,
                                          VertexConsumer consumer,
                                          double x,
                                          double y,
                                          double z,
                                          Detail detail,
                                          float[] color,
                                          float alpha) {
        renderBox(poseStack, consumer, box(x, y, z - 10.0D, 72.0D, 5.0D, 5.0D), color[0], color[1], color[2], alpha);
        renderBox(poseStack, consumer, box(x, y, z + 10.0D, 72.0D, 5.0D, 5.0D), color[0], color[1], color[2], alpha);
        renderBox(poseStack, consumer, box(x, y, z, 20.0D, 32.0D, 44.0D), color[0], color[1], color[2], alpha);
        if (detail == Detail.FULL) {
            for (int offset = -32; offset <= 32; offset += 16) {
                renderBox(poseStack, consumer, box(x + offset, y, z, 3.0D, 22.0D, 34.0D), color[0], color[1], color[2], alpha * 0.8F);
            }
        }
    }

    private static void renderDirectedEcho(
                                           PoseStack poseStack,
                                           VertexConsumer consumer,
                                           double x,
                                           double y,
                                           double z,
                                           Detail detail,
                                           float[] color,
                                           float alpha,
                                           float pulse) {
        int rings = detail == Detail.FULL ? 5 : 3;
        for (int ring = 0; ring < rings; ring++) {
            double size = 20.0D + ring * 14.0D;
            renderBox(
                    poseStack,
                    consumer,
                    box(x, y + ring * 4.0D, z, size, 2.0D, size),
                    color[0],
                    color[1],
                    color[2],
                    alpha * (0.55F + pulse * 0.35F));
        }
        renderBox(poseStack, consumer, box(x, y + 18.0D, z, 18.0D, 36.0D, 18.0D), color[0], color[1], color[2], alpha);
    }

    private static void renderDigitalEcho(
                                          PoseStack poseStack,
                                          VertexConsumer consumer,
                                          double x,
                                          double y,
                                          double z,
                                          Detail detail,
                                          float[] color,
                                          float alpha) {
        renderBox(poseStack, consumer, box(x, y, z, 52.0D, 28.0D, 52.0D), color[0], color[1], color[2], alpha);
        renderBox(poseStack, consumer, box(x, y - 26.0D, z, 14.0D, 48.0D, 14.0D), color[0], color[1], color[2], alpha);
        if (detail == Detail.FULL) {
            renderBox(poseStack, consumer, box(x - 22.0D, y, z, 5.0D, 44.0D, 44.0D), color[0], color[1], color[2], alpha * 0.75F);
            renderBox(poseStack, consumer, box(x + 22.0D, y, z, 5.0D, 44.0D, 44.0D), color[0], color[1], color[2], alpha * 0.75F);
        }
    }

    private static float[] attackColor(OrbitalAttackMode mode) {
        return switch (mode) {
            case KINETIC -> new float[] { 0.40F, 0.82F, 1.0F };
            case DIRECTED_ENERGY -> new float[] { 0.85F, 0.35F, 1.0F };
            case DIGITAL_ANNIHILATION -> new float[] { 0.20F, 1.0F, 0.78F };
        };
    }

    private static double attackEchoY(ClientLevel level, OrbitalAttackVisualSnapshot attack) {
        return Math.max(level.getMaxBuildHeight() + 96.0D, attack.target().getY() + 96.0D);
    }

    private static float pulse(long time, long seed, float partialTick, float speed) {
        float phase = (time + partialTick + Math.floorMod(seed, 10_000L)) * speed;
        return 0.5F + 0.5F * Mth.sin(phase);
    }

    private static AABB box(
                            double centerX,
                            double centerY,
                            double centerZ,
                            double sizeX,
                            double sizeY,
                            double sizeZ) {
        return new AABB(
                centerX - sizeX * 0.5D,
                centerY - sizeY * 0.5D,
                centerZ - sizeZ * 0.5D,
                centerX + sizeX * 0.5D,
                centerY + sizeY * 0.5D,
                centerZ + sizeZ * 0.5D);
    }

    private static void renderBox(
                                  PoseStack poseStack,
                                  VertexConsumer consumer,
                                  AABB box,
                                  float red,
                                  float green,
                                  float blue,
                                  float alpha) {
        LevelRenderer.renderLineBox(poseStack, consumer, box, red, green, blue, alpha);
    }

    private enum Detail {
        FULL,
        REDUCED,
        IMPOSTOR
    }
}

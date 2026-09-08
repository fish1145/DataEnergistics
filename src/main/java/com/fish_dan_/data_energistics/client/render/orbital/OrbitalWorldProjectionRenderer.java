package com.fish_dan_.data_energistics.client.render.orbital;

import com.fish_dan_.data_energistics.Data_Energistics;
import com.fish_dan_.data_energistics.client.render.orbital.animation.OrbitalAnimationClock;
import com.fish_dan_.data_energistics.client.render.orbital.geometry.OrbitalBeamMesh;
import com.fish_dan_.data_energistics.client.render.orbital.geometry.OrbitalProjectionPlacement;
import com.fish_dan_.data_energistics.client.render.orbital.geometry.OrbitalProjectionPlacement.Detail;
import com.fish_dan_.data_energistics.client.render.orbital.geometry.OrbitalRenderBuffers;
import com.fish_dan_.data_energistics.client.render.orbital.model.OrbitalConstructModel;
import com.fish_dan_.data_energistics.client.render.orbital.model.OrbitalModelRenderer;
import com.fish_dan_.data_energistics.orbital.attack.OrbitalAttackMode;
import com.fish_dan_.data_energistics.orbital.attack.OrbitalAttackPhase;
import com.fish_dan_.data_energistics.orbital.attack.OrbitalAttackVisualSnapshot;
import com.fish_dan_.data_energistics.orbital.attack.beam.OrbitalBeamPath;
import com.fish_dan_.data_energistics.orbital.attack.beam.OrbitalBeamScan;
import com.fish_dan_.data_energistics.orbital.attack.beam.OrbitalBeamScan.Segment;
import com.fish_dan_.data_energistics.orbital.attack.beam.OrbitalBeamSweep;
import com.fish_dan_.data_energistics.orbital.model.OrbitalWeaponLifecycleState;
import com.fish_dan_.data_energistics.orbital.projection.OrbitalProjectionVisualSnapshot;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import org.jspecify.annotations.Nullable;

import java.util.Comparator;
import java.util.List;

/** Renders resource-backed orbital constructs without creating entities, querying chunks, or advancing gameplay. */
@EventBusSubscriber(modid = Data_Energistics.MODID, value = Dist.CLIENT)
public final class OrbitalWorldProjectionRenderer {

    private static final int MAX_FULL_DETAIL = 4;
    private static final int MAX_VISIBLE_PROJECTIONS = 64;
    private static final int MAX_VISIBLE_ATTACK_ECHOES = 32;
    private static final double MAX_DISTANCE_SQUARED = OrbitalProjectionPlacement.MAX_DISTANCE * OrbitalProjectionPlacement.MAX_DISTANCE;
    private static final int SKY_LIGHT = LightTexture.pack(4, 15);
    private static final OrbitalAnimationClock PROJECTION_CLOCK = new OrbitalAnimationClock();
    private static final OrbitalAnimationClock ATTACK_CLOCK = new OrbitalAnimationClock();
    private static @Nullable OrbitalRenderBuffers renderBuffers;
    private static final RenderType[] PASSES = {
            OrbitalModelRenderer.SOLID, OrbitalModelRenderer.HOLOGRAM, OrbitalModelRenderer.EMISSIVE,
            OrbitalBeamMesh.RENDER_TYPE
    };

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
        Vec3 camera = event.getCamera().getPosition();
        double far = minecraft.gameRenderer.getDepthFar();
        List<ProjectionDraw> projections = visibleProjections(event, level, camera, far);
        List<AttackDraw> attacks = visibleAttacks(event, level, camera, far);
        if (projections.isEmpty() && attacks.isEmpty()) {
            return;
        }
        float partialTick = event.getPartialTick().getGameTimeDeltaPartialTick(true);
        double projectionTime = PROJECTION_CLOCK.sample(OrbitalProjectionVisualClientState.revision(), level.getGameTime(), partialTick);
        double attackTime = ATTACK_CLOCK.sample(OrbitalAttackVisualClientState.revision(), level.getGameTime(), partialTick);
        if (renderBuffers == null) {
            renderBuffers = new OrbitalRenderBuffers();
        }
        MultiBufferSource.BufferSource buffers = renderBuffers.source();
        float fogStart = RenderSystem.getShaderFogStart();
        float fogEnd = RenderSystem.getShaderFogEnd();
        try {
            RenderSystem.setShaderFogStart((float) far * 0.9F);
            RenderSystem.setShaderFogEnd((float) far);
            for (int pass = 0; pass < 3; pass++) {
                VertexConsumer consumer = buffers.getBuffer(PASSES[pass]);
                for (ProjectionDraw draw : projections) {
                    renderProjection(event.getPoseStack(), consumer, draw, projectionTime, pass);
                }
                if (pass != 0) {
                    for (AttackDraw draw : attacks) {
                        renderAttack(event.getPoseStack(), consumer, draw, attackTime, pass == 2);
                    }
                }
                buffers.endBatch(PASSES[pass]);
            }
            renderEffects(event.getPoseStack(), buffers.getBuffer(OrbitalBeamMesh.RENDER_TYPE),
                    projections, attacks, projectionTime, attackTime);
        } finally {
            for (RenderType type : PASSES) {
                buffers.endBatch(type);
            }
            RenderSystem.setShaderFogStart(fogStart);
            RenderSystem.setShaderFogEnd(fogEnd);
        }
    }

    private static List<ProjectionDraw> visibleProjections(RenderLevelStageEvent event, ClientLevel level,
                                                           Vec3 camera, double far) {
        ResourceLocation dimension = level.dimension().location();
        if (!dimension.equals(OrbitalProjectionVisualClientState.dimensionId())) {
            return List.of();
        }
        List<OrbitalProjectionVisualSnapshot> ordered = new ObjectArrayList<>(OrbitalProjectionVisualClientState.projections());
        ordered.sort(Comparator.comparingDouble(snapshot -> projectionOrigin(snapshot).distanceToSqr(camera)));
        List<ProjectionDraw> result = new ObjectArrayList<>();
        int fullDetail = 0;
        for (OrbitalProjectionVisualSnapshot snapshot : ordered) {
            Vec3 origin = projectionOrigin(snapshot);
            double distance = origin.distanceToSqr(camera);
            if (distance > MAX_DISTANCE_SQUARED || result.size() >= MAX_VISIBLE_PROJECTIONS) {
                break;
            }
            Detail detail = OrbitalProjectionPlacement.detail(distance, fullDetail < MAX_FULL_DETAIL);
            AABB localBounds = OrbitalConstructModel.BOUNDS;
            if (detail == Detail.DISTANT) {
                localBounds = localBounds.minmax(new AABB(-1, snapshot.anchor().getY() - origin.y, -1, 1, 0, 1));
            }
            OrbitalProjectionPlacement placement = OrbitalProjectionPlacement.create(camera, origin, localBounds, far);
            if (event.getFrustum().isVisible(placement.bounds())) {
                result.add(new ProjectionDraw(snapshot, placement, detail));
                if (detail == Detail.FULL) {
                    fullDetail++;
                }
            }
        }
        // Translucent construct instances are submitted back-to-front; quads within a material batch are also sorted.
        return result.reversed();
    }

    private static List<AttackDraw> visibleAttacks(RenderLevelStageEvent event, ClientLevel level,
                                                   Vec3 camera, double far) {
        if (!level.dimension().location().equals(OrbitalAttackVisualClientState.dimensionId())) {
            return List.of();
        }
        List<OrbitalAttackVisualSnapshot> ordered = new ObjectArrayList<>(OrbitalAttackVisualClientState.attacks());
        ordered.sort(Comparator.comparingDouble(snapshot -> attackOrigin(level, snapshot).distanceToSqr(camera)));
        List<AttackDraw> result = new ObjectArrayList<>();
        int fullDetail = 0;
        for (OrbitalAttackVisualSnapshot snapshot : ordered) {
            Vec3 origin = attackOrigin(level, snapshot);
            double distance = origin.distanceToSqr(camera);
            if (distance > MAX_DISTANCE_SQUARED || result.size() >= MAX_VISIBLE_ATTACK_ECHOES) {
                break;
            }
            Detail detail = OrbitalProjectionPlacement.detail(distance, fullDetail < MAX_FULL_DETAIL);
            double radius = Math.max(1, snapshot.effectRadius()) + 4.0;
            Vec3 effect = Vec3.atCenterOf(snapshot.effectPosition()).subtract(origin);
            double targetY = snapshot.target().getY() - origin.y;
            AABB localBounds = OrbitalConstructModel.ECHO_BOUNDS
                    .minmax(new AABB(-radius, targetY, -radius, radius, targetY + 1, radius))
                    .minmax(new AABB(effect, effect).inflate(8));
            OrbitalBeamSweep sweep = snapshot.beamSweep();
            List<Segment> beams = sweep == null ? List.of() : sweep.scan(snapshot.target(), snapshot.effectRadius())
                    .completedBeams(sweep.fromCursor(), snapshot.workCursor(), detail == Detail.FULL ? 32 : 8);
            for (Segment beam : beams) {
                localBounds = localBounds.minmax(new AABB(beam.origin().subtract(origin), beam.tip().subtract(origin)).inflate(1));
            }
            OrbitalProjectionPlacement placement = OrbitalProjectionPlacement.create(camera, origin, localBounds, far);
            if (event.getFrustum().isVisible(placement.bounds())) {
                result.add(new AttackDraw(snapshot, origin, placement, detail, beams));
                if (detail == Detail.FULL) {
                    fullDetail++;
                }
            }
        }
        return result.reversed();
    }

    private static void renderProjection(PoseStack poses, VertexConsumer consumer, ProjectionDraw draw,
                                         double time, int pass) {
        OrbitalWeaponLifecycleState state = draw.snapshot().lifecycleState();
        boolean redeploying = state == OrbitalWeaponLifecycleState.REDEPLOYING;
        if ((pass == 0 && redeploying) || (pass == 1 && !redeploying)) {
            return;
        }
        boolean emissive = pass == 2;
        float pulse = pulse(time, draw.snapshot().randomSeed());
        float alpha = redeploying ? 0.32F + 0.25F * pulse : 1;
        float brightness = state == OrbitalWeaponLifecycleState.RESERVE_GRACE ? 0.5F : 1;
        OrbitalModelRenderer renderer = new OrbitalModelRenderer(consumer, draw.detail() == Detail.FULL,
                emissive, SKY_LIGHT, brightness, brightness, brightness,
                emissive ? alpha * (0.7F + pulse * 0.3F) : alpha);
        beginPlacement(poses, draw.placement());
        OrbitalConstructModel.render(poses, renderer, draw.detail(), time, draw.snapshot().randomSeed(), redeploying);
        poses.popPose();
    }

    private static void renderAttack(PoseStack poses, VertexConsumer consumer, AttackDraw draw,
                                     double time, boolean emissive) {
        boolean charging = draw.snapshot().phase() == OrbitalAttackPhase.RESERVED_WARNING;
        float alpha = charging ? 0.38F + 0.18F * pulse(time, draw.snapshot().randomSeed()) : 0.76F;
        OrbitalModelRenderer renderer = new OrbitalModelRenderer(consumer, draw.detail() == Detail.FULL,
                emissive, SKY_LIGHT, 0.8F, 0.94F, 1, emissive ? 0.9F : alpha);
        beginPlacement(poses, draw.placement());
        if (draw.snapshot().mode() == OrbitalAttackMode.DIRECTED_ENERGY) {
            Vec3 aim = draw.beams().isEmpty() ? Vec3.atCenterOf(draw.snapshot().target()) : draw.beams().getLast().tip();
            OrbitalConstructModel.aimDirectedEcho(poses, aim.subtract(draw.origin()));
        }
        OrbitalConstructModel.echo(poses, renderer, draw.detail(), draw.snapshot().mode(), time,
                draw.snapshot().randomSeed(), charging);
        poses.popPose();
    }

    private static void renderEffects(PoseStack poses, VertexConsumer consumer, List<ProjectionDraw> projections,
                                      List<AttackDraw> attacks, double projectionTime, double attackTime) {
        for (ProjectionDraw draw : projections) {
            if (draw.detail() != Detail.DISTANT) {
                continue;
            }
            beginPlacement(poses, draw.placement());
            OrbitalBeamMesh.beam(poses, consumer, 0, 0,
                    draw.snapshot().anchor().getY() - draw.snapshot().projectionY(), 0, 0.65F,
                    projectionTime, 0.35F, 0.9F, 1, 0.5F);
            poses.popPose();
        }
        for (AttackDraw draw : attacks) {
            OrbitalAttackVisualSnapshot snapshot = draw.snapshot();
            BlockPos position = snapshot.phase() == OrbitalAttackPhase.DELIVERY ? snapshot.effectPosition() : snapshot.target();
            Vec3 effect = Vec3.atCenterOf(position).subtract(draw.origin());
            boolean warning = snapshot.phase() == OrbitalAttackPhase.RESERVED_WARNING;
            float red = snapshot.mode() == OrbitalAttackMode.DIRECTED_ENERGY ? 0.8F : 0.3F;
            float green = snapshot.mode() == OrbitalAttackMode.DIRECTED_ENERGY ? 0.5F : 0.95F;
            float blue = snapshot.mode() == OrbitalAttackMode.DIGITAL_ANNIHILATION ? 0.75F : 1;
            float width = warning ? 0.35F : (snapshot.mode() == OrbitalAttackMode.DIRECTED_ENERGY ? 3 : 1.2F);
            beginPlacement(poses, draw.placement());
            if (snapshot.mode() == OrbitalAttackMode.DIRECTED_ENERGY) {
                if (warning || snapshot.beamSweep() == null) {
                    OrbitalBeamMesh.beam(poses, consumer, Vec3.ZERO, effect, 0.35F,
                            attackTime, red, green, blue, 0.32F);
                    OrbitalBeamMesh.targetRing(poses, consumer, 0, snapshot.target().getY() + 0.08 - draw.origin().y, 0,
                            Math.max(1, snapshot.effectRadius()), red, green, blue, 0.3F);
                } else {
                    for (int index = 0; index < draw.beams().size(); index++) {
                        Segment beam = draw.beams().get(index);
                        boolean current = index == draw.beams().size() - 1;
                        OrbitalBeamMesh.beam(poses, consumer, beam.origin().subtract(draw.origin()),
                                beam.tip().subtract(draw.origin()), current ? width : width * 0.35F,
                                attackTime, red, green, blue, current ? 0.85F : 0.12F);
                    }
                }
                poses.popPose();
                continue;
            }
            OrbitalBeamMesh.beam(poses, consumer, effect.x, effect.z, effect.y, 0, width,
                    attackTime, red, green, blue, warning ? 0.32F : 0.85F);
            OrbitalBeamMesh.targetRing(poses, consumer, 0, snapshot.target().getY() + 0.08 - draw.origin().y, 0,
                    Math.max(1, snapshot.effectRadius()), red, green, blue, warning ? 0.3F : 0.55F);
            poses.popPose();
        }
    }

    private static void beginPlacement(PoseStack poses, OrbitalProjectionPlacement placement) {
        poses.pushPose();
        Vec3 offset = placement.cameraOffset();
        poses.translate(offset.x, offset.y, offset.z);
        poses.scale(placement.scale(), placement.scale(), placement.scale());
    }

    private static Vec3 projectionOrigin(OrbitalProjectionVisualSnapshot snapshot) {
        return new Vec3(snapshot.anchor().getX() + 0.5, snapshot.projectionY(), snapshot.anchor().getZ() + 0.5);
    }

    private static Vec3 attackOrigin(ClientLevel level, OrbitalAttackVisualSnapshot snapshot) {
        OrbitalBeamSweep sweep = snapshot.beamSweep();
        if (sweep != null) {
            if (sweep.path() == OrbitalBeamPath.VERTICAL_COLUMNS && snapshot.workCursor() > 0) {
                return sweep.scan(snapshot.target(), snapshot.effectRadius()).beamAt(snapshot.workCursor() - 1).origin();
            }
            return OrbitalBeamScan.muzzle(snapshot.target(), sweep.topY());
        }
        double y = Math.max(level.getMaxBuildHeight() + 96.0, snapshot.target().getY() + 96.0);
        return new Vec3(snapshot.target().getX() + 0.5, y, snapshot.target().getZ() + 0.5);
    }

    private static float pulse(double time, long seed) {
        return 0.5F + 0.5F * Mth.sin(OrbitalAnimationClock.angle(time, seed, 80));
    }

    /** Releases native scratch memory when the client leaves its server; the next world allocates it lazily. */
    public static void releaseBuffers() {
        if (renderBuffers != null) {
            renderBuffers.close();
            renderBuffers = null;
        }
    }

    private record ProjectionDraw(OrbitalProjectionVisualSnapshot snapshot, OrbitalProjectionPlacement placement,
                                  Detail detail) {}

    private record AttackDraw(OrbitalAttackVisualSnapshot snapshot, Vec3 origin, OrbitalProjectionPlacement placement,
                              Detail detail, List<Segment> beams) {}
}

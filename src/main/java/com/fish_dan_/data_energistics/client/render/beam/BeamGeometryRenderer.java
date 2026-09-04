package com.fish_dan_.data_energistics.client.render.beam;

import com.fish_dan_.data_energistics.common.beam.BeamDeviceKind;
import com.fish_dan_.data_energistics.common.beam.BeamEndpoint;
import com.fish_dan_.data_energistics.common.beam.BeamVisual;

import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import it.unimi.dsi.fastutil.objects.ObjectList;
import org.jspecify.annotations.Nullable;

/** Client-thread geometry shared by full blocks and mounted parts; never resolves remote world state. */
public final class BeamGeometryRenderer {

    private static final ResourceLocation TEXTURE = ResourceLocation.withDefaultNamespace("textures/entity/beacon_beam.png");
    private static final float OUTER_SCALE = 1.18F;
    private static final float CORE_SCALE = 0.62F;
    private static final float OUTER_ALPHA = 0.22F;
    private static final float CORE_ALPHA = 0.90F;
    private static final float CORE_WHITE_MIX = 0.16F;
    private static final double PART_OFFSET = 11.0 / 16.0;
    private static final double OMNI_OFFSET = 3.5 / 16.0;
    private static final double BLOCK_OFFSET = 0.25;

    private BeamGeometryRenderer() {}

    /**
     * Draws only the canonical-owner visual snapshot, relative to the host block's unrotated origin.
     * Call on the client render thread; the pose stack is restored before returning.
     */
    public static void render(BeamEndpoint endpoint, PoseStack poseStack, MultiBufferSource buffers,
                              float partialTicks) {
        Level level = endpoint.beamLevel();
        if (level == null) {
            return;
        }
        ObjectList<BeamVisual> visuals = endpoint.beamState().visuals();
        if (visuals.isEmpty()) {
            return;
        }

        BeamDeviceKind kind = endpoint.beamState().kind();
        float radius = kind.width() * 0.5F;
        float scroll = -((level.getGameTime() % 4000L) + partialTicks) * 0.05F;
        VertexConsumer consumer = buffers.getBuffer(RenderType.beaconBeam(TEXTURE, true));
        BlockPos origin = endpoint.beamPosition();
        for (BeamVisual visual : visuals) {
            BeamSegment segment = segment(endpoint, visual, kind);
            if (segment == null) {
                continue;
            }
            DisplayColor color = displayColor(visual.color());
            Vec3 vector = segment.end().subtract(segment.start());
            float textureEnd = scroll + (float) vector.length() * 1.6F;

            poseStack.pushPose();
            poseStack.translate(segment.start().x - origin.getX(), segment.start().y - origin.getY(),
                    segment.start().z - origin.getZ());
            PoseStack.Pose pose = poseStack.last();
            emitShell(pose, consumer, vector, radius * OUTER_SCALE, color, OUTER_ALPHA, scroll, textureEnd);
            emitShell(pose, consumer, vector, radius * CORE_SCALE, color.whiten(), CORE_ALPHA, scroll, textureEnd);
            poseStack.popPose();
        }
    }

    /** Returns the finite world-space host and actual visible beam bounds, including the outer square shell. */
    public static AABB bounds(BeamEndpoint endpoint) {
        AABB bounds = new AABB(endpoint.beamPosition());
        BeamDeviceKind kind = endpoint.beamState().kind();
        // Square cross sections can reach sqrt(2) times their side radius after arbitrary rotation.
        double padding = kind.width() * 0.5 * OUTER_SCALE * Math.sqrt(2.0);
        for (BeamVisual visual : endpoint.beamState().visuals()) {
            BeamSegment segment = segment(endpoint, visual, kind);
            if (segment != null) {
                bounds = bounds.minmax(new AABB(segment.start(), segment.end()).inflate(padding));
            }
        }
        return bounds;
    }

    private static @Nullable BeamSegment segment(BeamEndpoint endpoint, BeamVisual visual, BeamDeviceKind kind) {
        double sourceOffset = switch (kind) {
            case PART -> PART_OFFSET;
            case DIRECTIONAL -> BLOCK_OFFSET;
            case OMNI -> OMNI_OFFSET;
        };
        Vec3 start = anchor(endpoint.beamPosition(), endpoint.beamFacing(), sourceOffset);
        Vec3 end = anchor(visual.target(), visual.targetFacing(), sourceOffset);
        if (kind == BeamDeviceKind.PART &&
                end.subtract(start).dot(Vec3.atLowerCornerOf(endpoint.beamFacing().getNormal())) <= 0) {
            // Adjacent mounted emitters overlap; the original part beam has no exposed segment in that case.
            return null;
        }
        return new BeamSegment(start, end);
    }

    private static Vec3 anchor(BlockPos pos, Direction facing, double offset) {
        return new Vec3(pos.getX() + 0.5 + facing.getStepX() * offset,
                pos.getY() + 0.5 + facing.getStepY() * offset,
                pos.getZ() + 0.5 + facing.getStepZ() * offset);
    }

    private static void emitShell(PoseStack.Pose pose, VertexConsumer consumer, Vec3 vector, float radius,
                                  DisplayColor color, float alpha, float textureStart, float textureEnd) {
        Vec3 direction = vector.normalize();
        Vec3 reference = Math.abs(direction.y) < 0.92 ? new Vec3(0, 1, 0) : new Vec3(1, 0, 0);
        Vec3 side = reference.cross(direction).normalize().scale(radius);
        Vec3 up = direction.cross(side).normalize().scale(radius);
        Vec3 first = side.scale(-1).subtract(up);
        Vec3 second = side.subtract(up);
        Vec3 third = side.add(up);
        Vec3 fourth = up.subtract(side);
        emitFace(pose, consumer, first, second, vector, color, alpha, textureStart, textureEnd);
        emitFace(pose, consumer, second, third, vector, color, alpha, textureStart, textureEnd);
        emitFace(pose, consumer, third, fourth, vector, color, alpha, textureStart, textureEnd);
        emitFace(pose, consumer, fourth, first, vector, color, alpha, textureStart, textureEnd);
    }

    private static void emitFace(PoseStack.Pose pose, VertexConsumer consumer, Vec3 first, Vec3 second,
                                 Vec3 vector, DisplayColor color, float alpha, float textureStart, float textureEnd) {
        Vec3 third = second.add(vector);
        Vec3 fourth = first.add(vector);
        Vec3 normal = second.subtract(first).cross(vector).normalize();
        vertex(pose, consumer, first, normal, color, alpha, 0, textureStart);
        vertex(pose, consumer, second, normal, color, alpha, 1, textureStart);
        vertex(pose, consumer, third, normal, color, alpha, 1, textureEnd);
        vertex(pose, consumer, fourth, normal, color, alpha, 0, textureEnd);

        Vec3 reverseNormal = normal.scale(-1);
        vertex(pose, consumer, fourth, reverseNormal, color, alpha, 0, textureEnd);
        vertex(pose, consumer, third, reverseNormal, color, alpha, 1, textureEnd);
        vertex(pose, consumer, second, reverseNormal, color, alpha, 1, textureStart);
        vertex(pose, consumer, first, reverseNormal, color, alpha, 0, textureStart);
    }

    private static void vertex(PoseStack.Pose pose, VertexConsumer consumer, Vec3 position, Vec3 normal,
                               DisplayColor color, float alpha, float u, float v) {
        consumer.addVertex(pose.pose(), (float) position.x, (float) position.y, (float) position.z)
                .setColor(color.red(), color.green(), color.blue(), alpha)
                .setUv(u, v)
                .setOverlay(OverlayTexture.NO_OVERLAY)
                .setLight(LightTexture.FULL_BRIGHT)
                .setNormal(pose, (float) normal.x, (float) normal.y, (float) normal.z);
    }

    private static DisplayColor displayColor(int rgb) {
        float red = ((rgb >> 16) & 255) / 255.0F;
        float green = ((rgb >> 8) & 255) / 255.0F;
        float blue = (rgb & 255) / 255.0F;
        float maximum = Math.max(red, Math.max(green, blue));
        float minimum = Math.min(red, Math.min(green, blue));
        if (maximum <= 1.0E-4F || (maximum - minimum) / maximum < 0.08F) {
            return new DisplayColor(1, 1, 1);
        }
        float gain = 0.92F / maximum;
        red *= gain;
        green *= gain;
        blue *= gain;
        float average = (red + green + blue) / 3.0F;
        return new DisplayColor(Mth.clamp(average + (red - average) * 1.28F, 0, 1),
                Mth.clamp(average + (green - average) * 1.28F, 0, 1),
                Mth.clamp(average + (blue - average) * 1.28F, 0, 1));
    }

    private record BeamSegment(Vec3 start, Vec3 end) {}

    private record DisplayColor(float red, float green, float blue) {

        private DisplayColor whiten() {
            return new DisplayColor(this.red * (1 - CORE_WHITE_MIX) + CORE_WHITE_MIX,
                    this.green * (1 - CORE_WHITE_MIX) + CORE_WHITE_MIX,
                    this.blue * (1 - CORE_WHITE_MIX) + CORE_WHITE_MIX);
        }
    }
}

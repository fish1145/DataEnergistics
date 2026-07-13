package com.fish_dan_.data_energistics.client.render;

import com.fish_dan_.data_energistics.entity.DataNukePrimedEntity;

import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/**
 * Renders the primed digital annihilator as a black sphere surrounded by data-colored annihilation rays.
 */
public final class DataNukeRenderer extends EntityRenderer<DataNukePrimedEntity> {

    private static final ResourceLocation WHITE_TEXTURE = ResourceLocation.withDefaultNamespace("textures/misc/white.png");
    private static final float SPHERE_RADIUS = 0.49F;
    private static final int LATITUDE_SEGMENTS = 16;
    private static final int LONGITUDE_SEGMENTS = 32;
    private static final int SPHERE_COLOR = 0xFF05080A;
    private static final int RAY_CENTER_COLOR = 0xFFFFFFFF;
    // A transparent endpoint preserves the vanilla additive white-core-to-colored-edge gradient.
    private static final int DATA_CYAN_EDGE_COLOR = 0x0022B0AE;
    private static final float FULL_FUSE_TICKS = 80.0F;
    private static final double RAY_CULLING_RADIUS = 40.0D;
    private static final float HALF_SQRT_3 = (float) (Math.sqrt(3.0D) / 2.0D);

    public DataNukeRenderer(EntityRendererProvider.Context context) {
        super(context);
        this.shadowRadius = 0.5F;
    }

    @Override
    public void render(DataNukePrimedEntity entity, float entityYaw, float partialTick, PoseStack poseStack,
                       MultiBufferSource bufferSource, int packedLight) {
        poseStack.pushPose();
        poseStack.translate(0.0F, 0.5F, 0.0F);
        renderSphere(poseStack, bufferSource.getBuffer(RenderType.entitySolid(WHITE_TEXTURE)), packedLight);

        float rayProgress = calculateRayProgress(entity.isActive(), entity.getFuse(), partialTick);
        renderRays(poseStack, rayProgress, bufferSource.getBuffer(RenderType.dragonRays()));
        renderRays(poseStack, rayProgress, bufferSource.getBuffer(RenderType.dragonRaysDepth()));
        poseStack.popPose();

        super.render(entity, entityYaw, partialTick, poseStack, bufferSource, packedLight);
    }

    @Override
    public boolean shouldRender(DataNukePrimedEntity entity, Frustum frustum, double cameraX, double cameraY,
                                double cameraZ) {
        return super.shouldRender(entity, frustum, cameraX, cameraY, cameraZ) || frustum.isVisible(entity.getBoundingBox().inflate(RAY_CULLING_RADIUS));
    }

    @Override
    public ResourceLocation getTextureLocation(DataNukePrimedEntity entity) {
        return WHITE_TEXTURE;
    }

    static float calculateRayProgress(boolean active, int fuse, float partialTick) {
        if (active) {
            return 1.0F;
        }
        return Mth.clamp(1.0F - ((float) fuse - partialTick) / FULL_FUSE_TICKS, 0.0F, 1.0F);
    }

    private static void renderSphere(PoseStack poseStack, VertexConsumer consumer, int packedLight) {
        PoseStack.Pose pose = poseStack.last();
        for (int latitude = 0; latitude < LATITUDE_SEGMENTS; latitude++) {
            float v0 = (float) latitude / LATITUDE_SEGMENTS;
            float v1 = (float) (latitude + 1) / LATITUDE_SEGMENTS;
            float phi0 = ((float) Math.PI * v0) - (float) (Math.PI / 2.0D);
            float phi1 = ((float) Math.PI * v1) - (float) (Math.PI / 2.0D);

            for (int longitude = 0; longitude < LONGITUDE_SEGMENTS; longitude++) {
                float u0 = (float) longitude / LONGITUDE_SEGMENTS;
                float u1 = (float) (longitude + 1) / LONGITUDE_SEGMENTS;
                float theta0 = (float) (Math.PI * 2.0D) * u0;
                float theta1 = (float) (Math.PI * 2.0D) * u1;

                addSphereVertex(consumer, pose, phi0, theta0, u0, 1.0F - v0, packedLight);
                addSphereVertex(consumer, pose, phi1, theta0, u0, 1.0F - v1, packedLight);
                addSphereVertex(consumer, pose, phi1, theta1, u1, 1.0F - v1, packedLight);
                addSphereVertex(consumer, pose, phi0, theta1, u1, 1.0F - v0, packedLight);
            }
        }
    }

    private static void addSphereVertex(VertexConsumer consumer, PoseStack.Pose pose, float phi, float theta,
                                        float u, float v, int packedLight) {
        float horizontalRadius = Mth.cos(phi);
        float normalX = horizontalRadius * Mth.cos(theta);
        float normalY = Mth.sin(phi);
        float normalZ = horizontalRadius * Mth.sin(theta);
        consumer.addVertex(pose, normalX * SPHERE_RADIUS, normalY * SPHERE_RADIUS, normalZ * SPHERE_RADIUS)
                .setColor(SPHERE_COLOR)
                .setUv(u, v)
                .setOverlay(OverlayTexture.NO_OVERLAY)
                .setLight(packedLight)
                .setNormal(pose, normalX, normalY, normalZ);
    }

    private static void renderRays(PoseStack poseStack, float progress, VertexConsumer consumer) {
        poseStack.pushPose();
        float completionBoost = Math.min(progress > 0.8F ? (progress - 0.8F) / 0.2F : 0.0F, 1.0F);
        RandomSource random = RandomSource.create(432L);
        Vector3f center = new Vector3f();
        Vector3f first = new Vector3f();
        Vector3f second = new Vector3f();
        Vector3f third = new Vector3f();
        Quaternionf rotation = new Quaternionf();
        int rayCount = Mth.floor((progress + progress * progress) / 2.0F * 60.0F);

        for (int ray = 0; ray < rayCount; ray++) {
            rotation.rotationXYZ(
                    random.nextFloat() * (float) (Math.PI * 2.0D),
                    random.nextFloat() * (float) (Math.PI * 2.0D),
                    random.nextFloat() * (float) (Math.PI * 2.0D))
                    .rotateXYZ(
                            random.nextFloat() * (float) (Math.PI * 2.0D),
                            random.nextFloat() * (float) (Math.PI * 2.0D),
                            random.nextFloat() * (float) (Math.PI * 2.0D) + progress * (float) (Math.PI / 2.0D));
            poseStack.mulPose(rotation);
            float length = random.nextFloat() * 20.0F + 5.0F + completionBoost * 10.0F;
            float width = random.nextFloat() * 2.0F + 1.0F + completionBoost * 2.0F;
            first.set(-HALF_SQRT_3 * width, length, -0.5F * width);
            second.set(HALF_SQRT_3 * width, length, -0.5F * width);
            third.set(0.0F, length, width);
            PoseStack.Pose pose = poseStack.last();
            addRayTriangle(consumer, pose, center, first, second);
            addRayTriangle(consumer, pose, center, second, third);
            addRayTriangle(consumer, pose, center, third, first);
        }

        poseStack.popPose();
    }

    private static void addRayTriangle(VertexConsumer consumer, PoseStack.Pose pose, Vector3f center,
                                       Vector3f first, Vector3f second) {
        consumer.addVertex(pose, center).setColor(RAY_CENTER_COLOR);
        consumer.addVertex(pose, first).setColor(DATA_CYAN_EDGE_COLOR);
        consumer.addVertex(pose, second).setColor(DATA_CYAN_EDGE_COLOR);
    }
}

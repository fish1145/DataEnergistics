package com.fish_dan_.data_energistics.client.render.entity;

import com.fish_dan_.data_energistics.entity.projectile.MatterConvergingBoltEntity;
import com.fish_dan_.data_energistics.item.powered.MatterConvergingCrossbowMode;

import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.ItemRenderer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.FastColor;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import org.joml.Vector3f;

public class MatterConvergingBoltRenderer extends EntityRenderer<MatterConvergingBoltEntity> {

    private static final ResourceLocation NORMAL_ARROW_LOCATION = ResourceLocation.withDefaultNamespace("textures/entity/projectiles/arrow.png");
    private static final ResourceLocation TIPPED_ARROW_LOCATION = ResourceLocation.withDefaultNamespace("textures/entity/projectiles/tipped_arrow.png");
    private static final float AMMUNITION_MODEL_SIZE = 8.0F / 16.0F;
    private static final Direction[] MODEL_FACES = Direction.values();
    private final ItemRenderer itemRenderer;

    public MatterConvergingBoltRenderer(EntityRendererProvider.Context context) {
        super(context);
        this.itemRenderer = context.getItemRenderer();
    }

    @Override
    public void render(MatterConvergingBoltEntity entity, float entityYaw, float partialTick, PoseStack poseStack,
                       MultiBufferSource buffer, int packedLight) {
        if (entity.firingMode() == MatterConvergingCrossbowMode.GRENADE) {
            this.renderAmmunition(entity, poseStack, buffer, packedLight);
            super.render(entity, entityYaw, partialTick, poseStack, buffer, packedLight);
            return;
        }
        poseStack.pushPose();
        poseStack.mulPose(Axis.YP.rotationDegrees(Mth.lerp(partialTick, entity.yRotO, entity.getYRot()) - 90.0F));
        poseStack.mulPose(Axis.ZP.rotationDegrees(Mth.lerp(partialTick, entity.xRotO, entity.getXRot())));

        poseStack.mulPose(Axis.XP.rotationDegrees(45.0F));
        poseStack.scale(0.05625F, 0.05625F, 0.05625F);
        poseStack.translate(-4.0F, 0.0F, 0.0F);

        VertexConsumer consumer = buffer.getBuffer(RenderType.entityCutout(this.getTextureLocation(entity)));
        PoseStack.Pose pose = poseStack.last();
        int color = entity.getColor();
        this.vertex(pose, consumer, -7, -2, -2, 0.0F, 0.15625F, -1, 0, 0, packedLight, color);
        this.vertex(pose, consumer, -7, -2, 2, 0.15625F, 0.15625F, -1, 0, 0, packedLight, color);
        this.vertex(pose, consumer, -7, 2, 2, 0.15625F, 0.3125F, -1, 0, 0, packedLight, color);
        this.vertex(pose, consumer, -7, 2, -2, 0.0F, 0.3125F, -1, 0, 0, packedLight, color);
        this.vertex(pose, consumer, -7, 2, -2, 0.0F, 0.15625F, 1, 0, 0, packedLight, color);
        this.vertex(pose, consumer, -7, 2, 2, 0.15625F, 0.15625F, 1, 0, 0, packedLight, color);
        this.vertex(pose, consumer, -7, -2, 2, 0.15625F, 0.3125F, 1, 0, 0, packedLight, color);
        this.vertex(pose, consumer, -7, -2, -2, 0.0F, 0.3125F, 1, 0, 0, packedLight, color);

        for (int i = 0; i < 4; ++i) {
            poseStack.mulPose(Axis.XP.rotationDegrees(90.0F));
            this.vertex(pose, consumer, -8, -2, 0, 0.0F, 0.0F, 0, 1, 0, packedLight, color);
            this.vertex(pose, consumer, 8, -2, 0, 0.5F, 0.0F, 0, 1, 0, packedLight, color);
            this.vertex(pose, consumer, 8, 2, 0, 0.5F, 0.15625F, 0, 1, 0, packedLight, color);
            this.vertex(pose, consumer, -8, 2, 0, 0.0F, 0.15625F, 0, 1, 0, packedLight, color);
        }

        poseStack.popPose();
        super.render(entity, entityYaw, partialTick, poseStack, buffer, packedLight);
    }

    private void renderAmmunition(MatterConvergingBoltEntity entity, PoseStack poseStack, MultiBufferSource buffer,
                                  int packedLight) {
        ItemStack ammunition = entity.getItem();
        BakedModel model = this.itemRenderer.getModel(ammunition, entity.level(), null, entity.getId());
        poseStack.pushPose();
        if (!model.isGui3d()) {
            poseStack.mulPose(this.entityRenderDispatcher.cameraOrientation());
            poseStack.mulPose(Axis.YP.rotationDegrees(180.0F));
        }
        fitAmmunitionModel(model, ammunition, poseStack);
        this.itemRenderer.render(ammunition, ItemDisplayContext.NONE, false, poseStack, buffer, packedLight,
                OverlayTexture.NO_OVERLAY, model);
        poseStack.popPose();
    }

    /** Fits the actual model to eight pixels, so an already miniature TNT is not shrunk a second time. */
    private static void fitAmmunitionModel(BakedModel model, ItemStack ammunition, PoseStack poseStack) {
        Vector3f minimum = new Vector3f(Float.POSITIVE_INFINITY);
        Vector3f maximum = new Vector3f(Float.NEGATIVE_INFINITY);
        RandomSource random = RandomSource.create(42L);
        if (!model.isCustomRenderer()) {
            for (BakedModel pass : model.getRenderPasses(ammunition, true)) {
                for (int face = -1; face < MODEL_FACES.length; face++) {
                    // Match ItemRenderer's seed and include both culled and unculled item faces.
                    random.setSeed(42L);
                    for (BakedQuad quad : pass.getQuads(null, face < 0 ? null : MODEL_FACES[face], random)) {
                        int[] vertices = quad.getVertices();
                        int stride = vertices.length / 4;
                        for (int vertex = 0; vertex < 4; vertex++) {
                            int offset = vertex * stride;
                            float x = Float.intBitsToFloat(vertices[offset]);
                            float y = Float.intBitsToFloat(vertices[offset + 1]);
                            float z = Float.intBitsToFloat(vertices[offset + 2]);
                            minimum.set(Math.min(minimum.x, x), Math.min(minimum.y, y), Math.min(minimum.z, z));
                            maximum.set(Math.max(maximum.x, x), Math.max(maximum.y, y), Math.max(maximum.z, z));
                        }
                    }
                }
            }
        }
        float size = Math.max(maximum.x - minimum.x, Math.max(maximum.y - minimum.y, maximum.z - minimum.z));
        if (size > 0 && Float.isFinite(size)) {
            float scale = AMMUNITION_MODEL_SIZE / size;
            poseStack.scale(scale, scale, scale);
            // ItemRenderer translates by -0.5; compensate for off-center models such as AE2's tiny TNT.
            poseStack.translate(0.5F - (minimum.x + maximum.x) * 0.5F,
                    0.5F - (minimum.y + maximum.y) * 0.5F, 0.5F - (minimum.z + maximum.z) * 0.5F);
        } else {
            // Custom item renderers do not expose baked geometry; retain their model at half-block scale.
            poseStack.scale(AMMUNITION_MODEL_SIZE, AMMUNITION_MODEL_SIZE, AMMUNITION_MODEL_SIZE);
        }
    }

    private void vertex(PoseStack.Pose pose, VertexConsumer consumer, int x, int y, int z, float u, float v,
                        int normalX, int normalY, int normalZ, int packedLight, int color) {
        consumer.addVertex(pose, x, y, z)
                .setColor(FastColor.ARGB32.red(color), FastColor.ARGB32.green(color), FastColor.ARGB32.blue(color), 255)
                .setUv(u, v)
                .setOverlay(OverlayTexture.NO_OVERLAY)
                .setLight(packedLight)
                .setNormal(pose, normalX, normalZ, normalY);
    }

    @Override
    public ResourceLocation getTextureLocation(MatterConvergingBoltEntity entity) {
        if (entity.firingMode() == MatterConvergingCrossbowMode.GRENADE) return InventoryMenu.BLOCK_ATLAS;
        return entity.getColor() >= 0 ? TIPPED_ARROW_LOCATION : NORMAL_ARROW_LOCATION;
    }
}

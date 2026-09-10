package com.fish_dan_.data_energistics.client.render.entity;

import com.fish_dan_.data_energistics.client.render.entity.cannon.ElementalGrenadeRenderer;
import com.fish_dan_.data_energistics.client.render.item.crossbow.RailAmmunitionRenderer;
import com.fish_dan_.data_energistics.entity.projectile.MatterConvergingBoltEntity;
import com.fish_dan_.data_energistics.item.powered.MatterConvergingCrossbowMode;

import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.ItemRenderer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import org.joml.Vector3f;

public class MatterConvergingBoltRenderer extends EntityRenderer<MatterConvergingBoltEntity> {

    private static final float AMMUNITION_MODEL_SIZE = 8.0F / 16.0F;
    private static final Direction[] MODEL_FACES = Direction.values();
    private final ItemRenderer itemRenderer;
    private final ElementalGrenadeRenderer elementalGrenades;

    public MatterConvergingBoltRenderer(EntityRendererProvider.Context context) {
        super(context);
        this.itemRenderer = context.getItemRenderer();
        this.elementalGrenades = new ElementalGrenadeRenderer(context);
    }

    @Override
    public void render(MatterConvergingBoltEntity entity, float entityYaw, float partialTick, PoseStack poseStack,
                       MultiBufferSource buffer, int packedLight) {
        if (entity.firingMode() == MatterConvergingCrossbowMode.RAIL) {
            poseStack.pushPose();
            poseStack.mulPose(Axis.YP.rotationDegrees(180 + Mth.lerp(partialTick, entity.yRotO, entity.getYRot())));
            poseStack.mulPose(Axis.XP.rotationDegrees(Mth.lerp(partialTick, entity.xRotO, entity.getXRot())));
            RailAmmunitionRenderer.draw(entity.getItem(), entity.level(), poseStack, buffer, packedLight);
            poseStack.popPose();
            super.render(entity, entityYaw, partialTick, poseStack, buffer, packedLight);
            return;
        }
        if (entity.firingMode() == MatterConvergingCrossbowMode.GRENADE && entity.getItem().is(Items.WIND_CHARGE)) {
            this.elementalGrenades.wind(entity.tickCount + partialTick, poseStack, buffer, packedLight);
        } else if (entity.firingMode() == MatterConvergingCrossbowMode.GRENADE && entity.getItem().is(Items.FIRE_CHARGE)) {
            this.elementalGrenades.fire(entity.tickCount + partialTick, poseStack, buffer);
        } else {
            this.renderAmmunition(entity, poseStack, buffer, packedLight);
        }
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

    @Override
    public ResourceLocation getTextureLocation(MatterConvergingBoltEntity entity) {
        return InventoryMenu.BLOCK_ATLAS;
    }
}

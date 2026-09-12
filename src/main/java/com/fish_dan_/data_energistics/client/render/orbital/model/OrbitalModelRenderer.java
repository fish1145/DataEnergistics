package com.fish_dan_.data_energistics.client.render.orbital.model;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.world.inventory.InventoryMenu;
import net.neoforged.neoforge.client.model.data.ModelData;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;

/** One material pass over many module instances; only used on the client render thread. */
public final class OrbitalModelRenderer {

    public static final RenderType SOLID = RenderType.entitySolid(InventoryMenu.BLOCK_ATLAS);
    public static final RenderType HOLOGRAM = RenderType.entityTranslucent(InventoryMenu.BLOCK_ATLAS);
    public static final RenderType EMISSIVE = RenderType.entityTranslucentEmissive(InventoryMenu.BLOCK_ATLAS);
    private static final Direction[] DIRECTIONS = Direction.values();
    private static final float[] BRIGHTNESS = { 1, 1, 1, 1 };

    private final VertexConsumer consumer;
    private final boolean detailed;
    private final boolean emissive;
    private final float red;
    private final float green;
    private final float blue;
    private final float alpha;
    private final int[] light;
    private final RandomSource random = RandomSource.create(42L);

    public OrbitalModelRenderer(VertexConsumer consumer, boolean detailed, boolean emissive,
                                int packedLight, float red, float green, float blue, float alpha) {
        this.consumer = consumer;
        this.detailed = detailed;
        this.emissive = emissive;
        this.red = red;
        this.green = green;
        this.blue = blue;
        this.alpha = alpha;
        int moduleLight = emissive ? LightTexture.FULL_BRIGHT : packedLight;
        this.light = new int[] { moduleLight, moduleLight, moduleLight, moduleLight };
    }

    /** Draws a module centered at the supplied local position, with sizes measured in world blocks. */
    public void part(PoseStack poses, OrbitalModelPart part, double x, double y, double z,
                     float sizeX, float sizeY, float sizeZ) {
        poses.pushPose();
        poses.translate(x, y, z);
        poses.scale(sizeX, sizeY, sizeZ);
        poses.translate(-0.5, -0.5, -0.5);
        BakedModel model = Minecraft.getInstance().getModelManager().getModel(part.model(detailed, emissive));
        for (Direction direction : DIRECTIONS) {
            random.setSeed(42L);
            for (BakedQuad quad : model.getQuads(null, direction, random, ModelData.EMPTY, null)) {
                quad(poses.last(), quad);
            }
        }
        random.setSeed(42L);
        for (BakedQuad quad : model.getQuads(null, null, random, ModelData.EMPTY, null)) {
            quad(poses.last(), quad);
        }
        poses.popPose();
    }

    private void quad(PoseStack.Pose pose, BakedQuad quad) {
        consumer.putBulkData(pose, quad, BRIGHTNESS, red, green, blue, alpha, light, OverlayTexture.NO_OVERLAY, true);
    }
}

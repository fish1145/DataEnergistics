package com.fish_dan_.data_energistics.client;

import com.fish_dan_.data_energistics.Data_Energistics;
import com.fish_dan_.data_energistics.ae2.DataFlowKey;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;
import appeng.api.client.AEKeyRenderHandler;
import org.joml.Matrix4f;

public final class DataFlowKeyRenderHandler implements AEKeyRenderHandler<DataFlowKey> {
    private static final ResourceLocation TEXTURE = ResourceLocation.fromNamespaceAndPath(Data_Energistics.MODID, "textures/block/flow/data_flow.png");
    private static final int FRAME_SIZE = 16;
    private static final int FRAME_COUNT = 16;
    private static final int FRAME_TIME_MS = 100;
    private static final float FACE_Z_OFFSET = 0.01F;
    private static final long SCROLL_PERIOD_TICKS = 40L;

    @Override
    public void drawInGui(Minecraft minecraft, GuiGraphics guiGraphics, int x, int y, DataFlowKey key) {
        int frame = getCurrentFrame(minecraft);
        guiGraphics.blit(TEXTURE, x, y, 0, 0.0F, frame * FRAME_SIZE, FRAME_SIZE, FRAME_SIZE, FRAME_SIZE, FRAME_SIZE * FRAME_COUNT);
    }

    @Override
    public void drawOnBlockFace(PoseStack poseStack, MultiBufferSource buffers, DataFlowKey key, float scale, int light, Level level) {
        float offset = (level.getGameTime() % SCROLL_PERIOD_TICKS) / (float) SCROLL_PERIOD_TICKS;
        float halfSize = (scale - 0.05F) / 2.0F;

        poseStack.pushPose();
        poseStack.translate(0.0F, 0.0F, FACE_Z_OFFSET);

        Matrix4f transform = poseStack.last().pose();
        VertexConsumer buffer = buffers.getBuffer(RenderType.entityTranslucent(TEXTURE));

        float left = -halfSize;
        float right = halfSize;
        float top = halfSize;
        float bottom = -halfSize;
        float splitY = top - (top - bottom) * offset;

        addFaceQuad(buffer, transform, light, left, right, top, splitY, 1.0F - offset, 1.0F);
        addFaceQuad(buffer, transform, light, left, right, splitY, bottom, 0.0F, 1.0F - offset);

        poseStack.popPose();
    }

    @Override
    public Component getDisplayName(DataFlowKey key) {
        return key.getDisplayName();
    }

    private static int getCurrentFrame(Minecraft minecraft) {
        if (minecraft.level != null) {
            return (int) ((minecraft.level.getGameTime() / 2L) % FRAME_COUNT);
        }

        return (int) ((Util.getMillis() / FRAME_TIME_MS) % FRAME_COUNT);
    }

    private static void addFaceQuad(
            VertexConsumer buffer,
            Matrix4f transform,
            int light,
            float left,
            float right,
            float top,
            float bottom,
            float vTop,
            float vBottom) {
        buffer.addVertex(transform, left, bottom, 0.0F)
                .setColor(0xFFFFFFFF)
                .setUv(0.0F, vBottom)
                .setOverlay(OverlayTexture.NO_OVERLAY)
                .setLight(light)
                .setNormal(0.0F, 0.0F, 1.0F);
        buffer.addVertex(transform, right, bottom, 0.0F)
                .setColor(0xFFFFFFFF)
                .setUv(1.0F, vBottom)
                .setOverlay(OverlayTexture.NO_OVERLAY)
                .setLight(light)
                .setNormal(0.0F, 0.0F, 1.0F);
        buffer.addVertex(transform, right, top, 0.0F)
                .setColor(0xFFFFFFFF)
                .setUv(1.0F, vTop)
                .setOverlay(OverlayTexture.NO_OVERLAY)
                .setLight(light)
                .setNormal(0.0F, 0.0F, 1.0F);
        buffer.addVertex(transform, left, top, 0.0F)
                .setColor(0xFFFFFFFF)
                .setUv(0.0F, vTop)
                .setOverlay(OverlayTexture.NO_OVERLAY)
                .setLight(light)
                .setNormal(0.0F, 0.0F, 1.0F);
    }
}

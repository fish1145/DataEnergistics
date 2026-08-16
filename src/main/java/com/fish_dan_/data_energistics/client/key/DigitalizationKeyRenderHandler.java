package com.fish_dan_.data_energistics.client.key;

import com.fish_dan_.data_energistics.ae2.key.CelestialEnergyKey;
import com.fish_dan_.data_energistics.ae2.key.DataFlowKey;
import com.fish_dan_.data_energistics.ae2.key.DigitalizationKey;
import com.fish_dan_.data_energistics.ae2.key.EchoKey;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.Level;

import appeng.api.client.AEKeyRenderHandler;
import appeng.client.gui.style.Blitter;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import org.joml.Matrix4f;

/**
 * Renders every resource in the shared Digitalization AE key type.
 */
public final class DigitalizationKeyRenderHandler implements AEKeyRenderHandler<DigitalizationKey> {

    private static final float FACE_Z_OFFSET = 0.01F;

    @Override
    public void drawInGui(Minecraft minecraft, GuiGraphics guiGraphics, int x, int y, DigitalizationKey key) {
        Blitter.sprite(sprite(key))
                .dest(x, y, 16, 16)
                .blit(guiGraphics);
    }

    @Override
    public void drawOnBlockFace(
                                PoseStack poseStack,
                                MultiBufferSource buffers,
                                DigitalizationKey key,
                                float scale,
                                int light,
                                Level level) {
        TextureAtlasSprite sprite = sprite(key);
        float halfSize = (scale - 0.05F) / 2.0F;

        poseStack.pushPose();
        poseStack.translate(0.0F, 0.0F, FACE_Z_OFFSET);

        Matrix4f transform = poseStack.last().pose();
        VertexConsumer buffer = buffers.getBuffer(RenderType.cutout());

        addFaceQuad(
                buffer,
                transform,
                light,
                -halfSize,
                halfSize,
                halfSize,
                -halfSize,
                sprite.getU0(),
                sprite.getU1(),
                sprite.getV0(),
                sprite.getV1());

        poseStack.popPose();
    }

    @Override
    public Component getDisplayName(DigitalizationKey key) {
        return key.getDisplayName();
    }

    private static TextureAtlasSprite sprite(DigitalizationKey key) {
        if (key instanceof DataFlowKey) {
            return CustomKeyGuiRenderer.dataFlowSprite();
        }
        if (key instanceof EchoKey) {
            return CustomKeyGuiRenderer.echoSprite();
        }
        if (key instanceof CelestialEnergyKey) {
            return CustomKeyGuiRenderer.celestialEnergySprite();
        }
        throw new IllegalArgumentException("Unsupported Digitalization key: " + key.getClass().getName());
    }

    private static void addFaceQuad(
                                    VertexConsumer buffer,
                                    Matrix4f transform,
                                    int light,
                                    float left,
                                    float right,
                                    float top,
                                    float bottom,
                                    float uLeft,
                                    float uRight,
                                    float vTop,
                                    float vBottom) {
        buffer.addVertex(transform, left, bottom, 0.0F)
                .setColor(0xFFFFFFFF)
                .setUv(uLeft, vBottom)
                .setOverlay(OverlayTexture.NO_OVERLAY)
                .setLight(light)
                .setNormal(0.0F, 0.0F, 1.0F);
        buffer.addVertex(transform, right, bottom, 0.0F)
                .setColor(0xFFFFFFFF)
                .setUv(uRight, vBottom)
                .setOverlay(OverlayTexture.NO_OVERLAY)
                .setLight(light)
                .setNormal(0.0F, 0.0F, 1.0F);
        buffer.addVertex(transform, right, top, 0.0F)
                .setColor(0xFFFFFFFF)
                .setUv(uRight, vTop)
                .setOverlay(OverlayTexture.NO_OVERLAY)
                .setLight(light)
                .setNormal(0.0F, 0.0F, 1.0F);
        buffer.addVertex(transform, left, top, 0.0F)
                .setColor(0xFFFFFFFF)
                .setUv(uLeft, vTop)
                .setOverlay(OverlayTexture.NO_OVERLAY)
                .setLight(light)
                .setNormal(0.0F, 0.0F, 1.0F);
    }
}

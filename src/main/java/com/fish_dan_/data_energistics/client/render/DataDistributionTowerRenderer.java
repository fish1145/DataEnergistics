package com.fish_dan_.data_energistics.client.render;

import com.fish_dan_.data_energistics.blockentity.DataDistributionTowerBlockEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.world.phys.AABB;
import org.jetbrains.annotations.NotNull;

public class DataDistributionTowerRenderer implements BlockEntityRenderer<DataDistributionTowerBlockEntity> {
    private static final float[] LABEL_Y_OFFSETS = {0.75f, 1.75f, 2.75f};

    public DataDistributionTowerRenderer(BlockEntityRendererProvider.Context context) {
    }

    @Override
    public boolean shouldRenderOffScreen(@NotNull DataDistributionTowerBlockEntity blockEntity) {
        return true;
    }

    @Override
    public void render(@NotNull DataDistributionTowerBlockEntity blockEntity, float partialTick, @NotNull PoseStack poseStack,
                       @NotNull MultiBufferSource buffer, int packedLight, int packedOverlay) {
        if (blockEntity.isRangeDisplayEnabled()) {
            int range = blockEntity.getConfiguredRange();
            if (range > 0) {
                AABB aabb = new AABB(
                        -range, -range, -range,
                        range + 1.0, range + 3.0 + range, range + 1.0
                );

                var consumer = buffer.getBuffer(RenderType.lines());
                LevelRenderer.renderLineBox(poseStack, consumer, aabb, 0.2f, 0.85f, 1.0f, 0.85f);
            }
        }

        renderInfoLines(blockEntity, poseStack, buffer, packedLight);
    }

    @Override
    public @NotNull AABB getRenderBoundingBox(@NotNull DataDistributionTowerBlockEntity blockEntity) {
        return new AABB(blockEntity.getBlockPos()).inflate(blockEntity.getConfiguredRange(), blockEntity.getConfiguredRange(), blockEntity.getConfiguredRange()).expandTowards(0, 2, 0);
    }

    private void renderInfoLines(DataDistributionTowerBlockEntity blockEntity, PoseStack poseStack, MultiBufferSource buffer, int packedLight) {
        String channelText = "AE " + blockEntity.getChannelDisplayText();
        String energyText = "FE " + blockEntity.getEnergyDisplayText();
        Font font = Minecraft.getInstance().font;

        for (float yOffset : LABEL_Y_OFFSETS) {
            poseStack.pushPose();
            poseStack.translate(0.5, yOffset, 0.5);
            poseStack.mulPose(Minecraft.getInstance().getEntityRenderDispatcher().cameraOrientation());
            poseStack.scale(-0.02f, -0.02f, 0.02f);

            float channelX = -font.width(channelText) / 2.0f;
            float energyX = -font.width(energyText) / 2.0f;
            font.drawInBatch(channelText, channelX, -font.lineHeight / 2.0f, 0x9EEBFF, false, poseStack.last().pose(), buffer, Font.DisplayMode.NORMAL, 0, packedLight);
            font.drawInBatch(energyText, energyX, font.lineHeight / 2.0f + 1, 0xB8FF9E, false, poseStack.last().pose(), buffer, Font.DisplayMode.NORMAL, 0, packedLight);
            poseStack.popPose();
        }
    }
}

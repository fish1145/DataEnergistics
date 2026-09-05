package com.fish_dan_.data_energistics.client.render.blockentity;

import com.fish_dan_.data_energistics.block.machine.DataChargerBlock;
import com.fish_dan_.data_energistics.blockentity.machine.DataChargerBlockEntity;

import appeng.api.orientation.BlockOrientation;

import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.entity.ItemRenderer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;

import com.mojang.blaze3d.vertex.PoseStack;
import org.joml.Quaternionf;

public class DataChargerRenderer implements BlockEntityRenderer<DataChargerBlockEntity> {

    private static final float[] EXTENDED_SLOT_X = { 0.27F, 0.4F, 0.6F, 0.73F };
    private static final Quaternionf EXTENDED_ITEM_ROTATION = new Quaternionf().rotateYXZ((float) Math.PI / 2.0F, 0.0F, 0.0F);
    private final ItemRenderer itemRenderer;

    public DataChargerRenderer(BlockEntityRendererProvider.Context context) {
        this.itemRenderer = context.getItemRenderer();
    }

    @Override
    public void render(DataChargerBlockEntity blockEntity, float partialTick, PoseStack poseStack,
                       MultiBufferSource buffer, int packedLight, int packedOverlay) {
        BlockOrientation orientation = BlockOrientation.get(blockEntity.getBlockState());
        poseStack.pushPose();
        poseStack.translate(0.5D, 0.5D, 0.5D);
        poseStack.mulPose(orientation.getQuaternion());
        poseStack.translate(-0.5D, -0.5D, -0.5D);

        if (DataChargerBlock.isExtended(blockEntity.getBlockState())) {
            renderExtendedItems(blockEntity, poseStack, buffer, packedLight);
        } else {
            renderRegularItem(blockEntity, poseStack, buffer, packedLight);
        }

        poseStack.popPose();
    }

    private void renderRegularItem(DataChargerBlockEntity blockEntity, PoseStack poseStack, MultiBufferSource buffer,
                                   int packedLight) {
        ItemStack stack = blockEntity.getDisplayStack(0);
        if (stack.isEmpty()) {
            return;
        }

        double time = System.currentTimeMillis() / 1000.0D;
        float yOffset = (float) Math.sin(time) * 0.02F;
        poseStack.pushPose();
        poseStack.translate(0.5D, 0.35D + yOffset, 0.5D);
        renderStack(blockEntity, stack, 0, poseStack, buffer, packedLight);
        poseStack.popPose();
    }

    private void renderExtendedItems(DataChargerBlockEntity blockEntity, PoseStack poseStack, MultiBufferSource buffer,
                                     int packedLight) {
        for (int slot = 0; slot < EXTENDED_SLOT_X.length; slot++) {
            ItemStack stack = blockEntity.getDisplayStack(slot);
            if (stack.isEmpty()) {
                continue;
            }

            poseStack.pushPose();
            poseStack.translate(EXTENDED_SLOT_X[slot], 0.375D, 0.5D);
            poseStack.mulPose(EXTENDED_ITEM_ROTATION);
            renderStack(blockEntity, stack, slot, poseStack, buffer, packedLight);
            poseStack.popPose();
        }
    }

    private void renderStack(DataChargerBlockEntity blockEntity, ItemStack stack, int slot, PoseStack poseStack,
                             MultiBufferSource buffer, int packedLight) {
        int seed = (int) blockEntity.getBlockPos().asLong() + slot;
        this.itemRenderer.renderStatic(stack, ItemDisplayContext.GROUND, packedLight, OverlayTexture.NO_OVERLAY,
                poseStack, buffer, blockEntity.getLevel(), seed);
    }
}

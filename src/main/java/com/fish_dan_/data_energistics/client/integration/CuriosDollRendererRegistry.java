package com.fish_dan_.data_energistics.client.integration;

import com.fish_dan_.data_energistics.block.decor.DollBlock;
import com.fish_dan_.data_energistics.registry.ModBlocks;

import net.minecraft.client.Minecraft;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.block.BlockRenderDispatcher;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.Direction;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.client.model.data.ModelData;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import top.theillusivec4.curios.api.SlotContext;
import top.theillusivec4.curios.api.client.CuriosRendererRegistry;
import top.theillusivec4.curios.api.client.ICurioRenderer;

public final class CuriosDollRendererRegistry {

    private CuriosDollRendererRegistry() {}

    public static void register() {
        CuriosRendererRegistry.register(ModBlocks.FISH_DAN.get().asItem(), DollCurioRenderer::new);
        CuriosRendererRegistry.register(ModBlocks.QIUYEQAQ2024.get().asItem(), DollCurioRenderer::new);
    }

    private static final class DollCurioRenderer implements ICurioRenderer {

        private static final String HEAD_SLOT = "head";
        private static final String BACK_SLOT = "back";
        private static final float HEAD_SCALE = 0.55F;
        private static final float BACK_SCALE = 0.55F;
        private static final double BLOCK_CENTER = 0.5D;
        private static final double DOLL_BACK_Z = 14.7D / 16.0D;
        private static final double HEAD_Y = -0.50D;
        private static final double BACK_Y = 0.34D;
        private static final double BACK_Z = 0.12D;

        @Override
        public <T extends LivingEntity, M extends EntityModel<T>> void render(ItemStack stack, SlotContext slotContext,
                                                                              PoseStack poseStack,
                                                                              RenderLayerParent<T, M> renderLayerParent,
                                                                              MultiBufferSource buffer, int light,
                                                                              float limbSwing, float limbSwingAmount,
                                                                              float partialTicks, float ageInTicks,
                                                                              float netHeadYaw, float headPitch) {
            if (!slotContext.visible() || !(renderLayerParent.getModel() instanceof HumanoidModel<?> humanoidModel)) {
                return;
            }

            switch (slotContext.identifier()) {
                case HEAD_SLOT -> renderOnHead(stack, poseStack, buffer, light, humanoidModel);
                case BACK_SLOT -> renderOnBack(stack, slotContext.entity(), poseStack, buffer, light, humanoidModel);
                default -> {}
            }
        }

        private static void renderOnHead(ItemStack stack, PoseStack poseStack, MultiBufferSource buffer, int light,
                                         HumanoidModel<?> humanoidModel) {
            poseStack.pushPose();
            humanoidModel.head.translateAndRotate(poseStack);
            poseStack.translate(0.0D, HEAD_Y, 0.0D);
            poseStack.mulPose(Axis.YP.rotationDegrees(180.0F));
            poseStack.scale(HEAD_SCALE, -HEAD_SCALE, -HEAD_SCALE);
            renderDollBlock(stack, poseStack, buffer, light);
            poseStack.popPose();
        }

        private static void renderOnBack(ItemStack stack, LivingEntity entity, PoseStack poseStack,
                                         MultiBufferSource buffer, int light, HumanoidModel<?> humanoidModel) {
            poseStack.pushPose();
            ICurioRenderer.translateIfSneaking(poseStack, entity);
            humanoidModel.body.translateAndRotate(poseStack);
            poseStack.translate(0.0D, BACK_Y, BACK_Z);
            poseStack.scale(BACK_SCALE, -BACK_SCALE, -BACK_SCALE);
            renderDollBlock(stack, poseStack, buffer, light, DOLL_BACK_Z);
            poseStack.popPose();
        }

        private static void renderDollBlock(ItemStack stack, PoseStack poseStack, MultiBufferSource buffer, int light) {
            renderDollBlock(stack, poseStack, buffer, light, BLOCK_CENTER);
        }

        private static void renderDollBlock(ItemStack stack, PoseStack poseStack, MultiBufferSource buffer, int light,
                                            double originZ) {
            if (!(stack.getItem() instanceof BlockItem blockItem)) {
                return;
            }

            BlockState state = blockItem.getBlock().defaultBlockState();
            if (state.hasProperty(DollBlock.FACING)) {
                state = state.setValue(DollBlock.FACING, Direction.NORTH);
            }

            poseStack.translate(-BLOCK_CENTER, 0.0D, -originZ);
            BlockRenderDispatcher blockRenderer = Minecraft.getInstance().getBlockRenderer();
            blockRenderer.renderSingleBlock(state, poseStack, buffer, light, OverlayTexture.NO_OVERLAY, ModelData.EMPTY,
                    RenderType.cutout());
        }
    }
}

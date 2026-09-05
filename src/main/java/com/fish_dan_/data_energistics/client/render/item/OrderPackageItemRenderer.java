package com.fish_dan_.data_energistics.client.render.item;

import com.fish_dan_.data_energistics.Data_Energistics;
import com.fish_dan_.data_energistics.item.order.OrderPackageTarget;
import com.fish_dan_.data_energistics.registry.DEItems;

import appeng.api.client.AEKeyRendering;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.AEKeyType;

import net.minecraft.client.Minecraft;
import net.minecraft.client.model.geom.EntityModelSet;
import net.minecraft.client.renderer.BlockEntityWithoutLevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderDispatcher;
import net.minecraft.client.renderer.entity.ItemRenderer;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.client.resources.model.ModelResourceLocation;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Renders the target as the package body and the marked-package texture as an eight-pixel corner badge.
 */
public final class OrderPackageItemRenderer extends BlockEntityWithoutLevelRenderer {

    /** Standalone half-size overlay model backed by {@code tybg1.png}. */
    public static final ModelResourceLocation MARKED_BADGE_MODEL = ModelResourceLocation.standalone(
            Data_Energistics.id("item/order_package_marked_badge"));

    /** Key types whose broken client renderer has already been logged to avoid per-frame log spam. */
    private static final Set<AEKeyType> REPORTED_RENDER_FAILURES = ConcurrentHashMap.newKeySet();

    /** Creates the renderer with Minecraft's block-entity and entity-model dispatchers. */
    public OrderPackageItemRenderer(BlockEntityRenderDispatcher dispatcher, EntityModelSet entityModelSet) {
        super(dispatcher, entityModelSet);
    }

    /** Builds the lazy client extension renderer from the active Minecraft instance. */
    public static OrderPackageItemRenderer create() {
        Minecraft minecraft = Minecraft.getInstance();
        return new OrderPackageItemRenderer(minecraft.getBlockEntityRenderDispatcher(), minecraft.getEntityModels());
    }

    @Override
    public void renderByItem(ItemStack stack, ItemDisplayContext displayContext, PoseStack poseStack,
                             MultiBufferSource bufferSource, int combinedLight, int combinedOverlay) {
        Minecraft minecraft = Minecraft.getInstance();
        BakedModel baseModel = getBaseModel(minecraft);
        var target = OrderPackageTarget.get().getTarget(stack);
        if (target.isEmpty()) {
            renderBakedModel(minecraft.getItemRenderer(), stack, baseModel, poseStack, bufferSource, combinedLight,
                    combinedOverlay);
            return;
        }

        if (!renderTarget(target.get(), baseModel, stack, poseStack, bufferSource, combinedLight, combinedOverlay)) {
            renderBakedModel(minecraft.getItemRenderer(), stack, baseModel, poseStack, bufferSource, combinedLight,
                    combinedOverlay);
        }
        renderBadge(minecraft, stack, poseStack, bufferSource, combinedLight, combinedOverlay);
    }

    private static boolean renderTarget(AEKey target, BakedModel baseModel, ItemStack packageStack,
                                        PoseStack poseStack, MultiBufferSource bufferSource, int combinedLight,
                                        int combinedOverlay) {
        Minecraft minecraft = Minecraft.getInstance();
        if (target instanceof AEItemKey itemKey && itemKey.is(DEItems.ORDER_PACKAGE.get())) {
            renderBakedModel(minecraft.getItemRenderer(), packageStack, baseModel, poseStack, bufferSource,
                    combinedLight, combinedOverlay);
            return true;
        }

        Level level = minecraft.level;
        if (level == null) {
            return false;
        }

        try {
            renderTargetFace(target, poseStack, bufferSource, combinedLight, level, 0.54F, false);
            renderTargetFace(target, poseStack, bufferSource, combinedLight, level, 0.46F, true);
            return true;
        } catch (RuntimeException exception) {
            if (REPORTED_RENDER_FAILURES.add(target.getType())) {
                Data_Energistics.LOGGER.error("Cannot render order-package target key type {}", target.getType(),
                        exception);
            }
            return false;
        }
    }

    private static void renderTargetFace(AEKey target, PoseStack poseStack, MultiBufferSource bufferSource,
                                         int combinedLight, Level level, float depth, boolean reverse) {
        poseStack.pushPose();
        try {
            poseStack.translate(0.5F, 0.5F, depth);
            if (reverse) {
                applyReverseFaceTransform(poseStack);
            }
            AEKeyRendering.drawOnBlockFace(poseStack, bufferSource, target, 1.0F, combinedLight, level);
        } finally {
            poseStack.popPose();
        }
    }

    private static void renderBadge(Minecraft minecraft, ItemStack stack, PoseStack poseStack,
                                    MultiBufferSource bufferSource, int combinedLight, int combinedOverlay) {
        BakedModel badge = minecraft.getModelManager().getModel(MARKED_BADGE_MODEL);
        renderBadgeFace(minecraft, badge, stack, poseStack, bufferSource, combinedLight, combinedOverlay, 0.55F,
                false);
        renderBadgeFace(minecraft, badge, stack, poseStack, bufferSource, combinedLight, combinedOverlay, 0.45F,
                true);
    }

    private static void renderBadgeFace(Minecraft minecraft, BakedModel badge, ItemStack stack, PoseStack poseStack,
                                        MultiBufferSource bufferSource, int combinedLight, int combinedOverlay,
                                        float depth, boolean reverse) {
        poseStack.pushPose();
        try {
            poseStack.translate(0.5F, 0.0F, depth);
            if (reverse) {
                applyReverseFaceTransform(poseStack);
            }
            poseStack.scale(0.5F, 0.5F, 0.02F);
            renderBakedModel(minecraft.getItemRenderer(), stack, badge, poseStack, bufferSource, combinedLight,
                    combinedOverlay);
        } finally {
            poseStack.popPose();
        }
    }

    private static void applyReverseFaceTransform(PoseStack poseStack) {
        poseStack.mulPose(Axis.YP.rotationDegrees(180.0F));
        poseStack.scale(-1.0F, 1.0F, 1.0F);
    }

    private static BakedModel getBaseModel(Minecraft minecraft) {
        BakedModel model = minecraft.getModelManager().getModel(
                ModelResourceLocation.inventory(Data_Energistics.id("order_package")));
        if (model instanceof OrderPackageBakedModel orderPackageModel) {
            return orderPackageModel.withoutCustomRenderer();
        }
        return model;
    }

    private static void renderBakedModel(ItemRenderer itemRenderer, ItemStack stack, BakedModel model,
                                         PoseStack poseStack, MultiBufferSource bufferSource, int combinedLight,
                                         int combinedOverlay) {
        boolean fabulous = true;
        for (BakedModel pass : model.getRenderPasses(stack, fabulous)) {
            for (var renderType : pass.getRenderTypes(stack, fabulous)) {
                var vertexConsumer = ItemRenderer.getFoilBufferDirect(
                        bufferSource,
                        renderType,
                        true,
                        stack.hasFoil());
                itemRenderer.renderModelLists(
                        pass,
                        stack,
                        combinedLight,
                        combinedOverlay,
                        poseStack,
                        vertexConsumer);
            }
        }
    }
}

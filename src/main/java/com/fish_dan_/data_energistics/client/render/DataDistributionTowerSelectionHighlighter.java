package com.fish_dan_.data_energistics.client.render;

import com.fish_dan_.data_energistics.Data_Energistics;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;

@EventBusSubscriber(modid = Data_Energistics.MODID, value = Dist.CLIENT)
public class DataDistributionTowerSelectionHighlighter {
    private static ResourceKey<Level> highlightedDimension;
    private static BlockPos highlightedPos;
    private static long expiresAtGameTime;

    private DataDistributionTowerSelectionHighlighter() {
    }

    public static void highlight(ResourceKey<Level> dimension, BlockPos pos) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null) {
            return;
        }

        highlightedDimension = dimension;
        highlightedPos = pos.immutable();
        expiresAtGameTime = minecraft.level.getGameTime() + 200;
    }

    @SubscribeEvent
    public static void onRenderLevelStage(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_TRANSLUCENT_BLOCKS) {
            return;
        }

        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null || highlightedPos == null || highlightedDimension == null) {
            return;
        }

        if (!minecraft.level.dimension().equals(highlightedDimension) || minecraft.level.getGameTime() > expiresAtGameTime) {
            highlightedDimension = null;
            highlightedPos = null;
            return;
        }

        var cameraPos = event.getCamera().getPosition();
        PoseStack poseStack = event.getPoseStack();
        poseStack.pushPose();
        poseStack.translate(
                highlightedPos.getX() - cameraPos.x,
                highlightedPos.getY() - cameraPos.y,
                highlightedPos.getZ() - cameraPos.z
        );

        var consumer = minecraft.renderBuffers().bufferSource().getBuffer(RenderType.lines());
        LevelRenderer.renderLineBox(poseStack, consumer, new AABB(0, 0, 0, 1, 1, 1), 1.0f, 0.85f, 0.2f, 1.0f);
        minecraft.renderBuffers().bufferSource().endBatch(RenderType.lines());
        poseStack.popPose();
    }
}

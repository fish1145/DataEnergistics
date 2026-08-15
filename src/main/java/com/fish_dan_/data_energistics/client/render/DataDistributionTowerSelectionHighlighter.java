package com.fish_dan_.data_energistics.client.render;

import com.fish_dan_.data_energistics.Data_Energistics;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;

import com.mojang.blaze3d.vertex.PoseStack;
import org.jspecify.annotations.Nullable;

@EventBusSubscriber(modid = Data_Energistics.MODID, value = Dist.CLIENT)
public class DataDistributionTowerSelectionHighlighter {

    private static ResourceKey<Level> highlightedDimension;
    private static BlockPos highlightedPos;
    private static Direction highlightedSide;
    private static long expiresAtGameTime;

    private DataDistributionTowerSelectionHighlighter() {}

    public static void highlight(ResourceKey<Level> dimension, BlockPos pos, @Nullable Direction side) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null) {
            return;
        }

        highlightedDimension = dimension;
        highlightedPos = pos.immutable();
        highlightedSide = side;
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
            highlightedSide = null;
            return;
        }

        var cameraPos = event.getCamera().getPosition();
        PoseStack poseStack = event.getPoseStack();
        poseStack.pushPose();
        poseStack.translate(
                highlightedPos.getX() - cameraPos.x,
                highlightedPos.getY() - cameraPos.y,
                highlightedPos.getZ() - cameraPos.z);

        var consumer = minecraft.renderBuffers().bufferSource().getBuffer(RenderType.lines());
        LevelRenderer.renderLineBox(poseStack, consumer, selectionBox(highlightedSide), 1.0f, 0.85f, 0.2f, 1.0f);
        minecraft.renderBuffers().bufferSource().endBatch(RenderType.lines());
        poseStack.popPose();
    }

    /** Highlights the mounted face for sided devices and the full block for ordinary nodes. */
    private static AABB selectionBox(@Nullable Direction side) {
        double depth = 0.25D;
        if (side == null) {
            return new AABB(0, 0, 0, 1, 1, 1);
        }
        return switch (side) {
            case DOWN -> new AABB(0, 0, 0, 1, depth, 1);
            case UP -> new AABB(0, 1 - depth, 0, 1, 1, 1);
            case NORTH -> new AABB(0, 0, 0, 1, 1, depth);
            case SOUTH -> new AABB(0, 0, 1 - depth, 1, 1, 1);
            case WEST -> new AABB(0, 0, 0, depth, 1, 1);
            case EAST -> new AABB(1 - depth, 0, 0, 1, 1, 1);
        };
    }
}

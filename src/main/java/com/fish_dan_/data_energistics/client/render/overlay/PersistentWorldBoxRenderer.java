package com.fish_dan_.data_energistics.client.render.overlay;

import com.fish_dan_.data_energistics.Data_Energistics;
import com.fish_dan_.data_energistics.blockentity.machine.DataExtractorBlockEntity;
import com.fish_dan_.data_energistics.blockentity.sanctum.DataSanctumBlockEntity;
import com.fish_dan_.data_energistics.blockentity.tower.DataDistributionTowerBlockEntity;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent.Stage;
import net.neoforged.neoforge.event.level.ChunkEvent;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import org.jspecify.annotations.Nullable;

import java.util.HashMap;
import java.util.Map;

/**
 * Draws persistent range boxes during world rendering instead of from block entity renderers.
 *
 * <p>
 * The cache follows client-loaded chunks and is periodically reconciled with the client chunk source. This keeps the
 * render independent from BER distance culling while avoiding a full block-entity scan on every frame.
 */
@EventBusSubscriber(modid = Data_Energistics.MODID, value = Dist.CLIENT)
public final class PersistentWorldBoxRenderer {

    private static final int RESCAN_INTERVAL_TICKS = 20;
    private static final double TOWER_RANGE_LINE_INSET = 0.03125D;
    private static final Map<BlockPos, BlockEntity> LOADED_BLOCK_ENTITIES = new HashMap<>();

    @Nullable
    private static ClientLevel trackedLevel;
    private static long nextRescanGameTime = Long.MIN_VALUE;

    private PersistentWorldBoxRenderer() {}

    @SubscribeEvent
    public static void onChunkLoad(ChunkEvent.Load event) {
        if (!(event.getLevel() instanceof ClientLevel level)) {
            return;
        }

        switchLevel(level);
        registerChunk(event.getChunk());
    }

    @SubscribeEvent
    public static void onChunkUnload(ChunkEvent.Unload event) {
        if (!(event.getLevel() instanceof ClientLevel level) || trackedLevel != level) {
            return;
        }

        ChunkPos unloadedChunk = event.getChunk().getPos();
        LOADED_BLOCK_ENTITIES.entrySet().removeIf(entry -> new ChunkPos(entry.getKey()).equals(unloadedChunk));
    }

    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        Minecraft minecraft = Minecraft.getInstance();
        ClientLevel level = minecraft.level;
        if (level == null) {
            switchLevel(null);
            return;
        }

        switchLevel(level);
        pruneRemovedBlockEntities(level);
        var player = minecraft.player;
        if (player == null || level.getGameTime() < nextRescanGameTime) {
            return;
        }

        nextRescanGameTime = level.getGameTime() + RESCAN_INTERVAL_TICKS;
        reconcileNearbyChunks(minecraft, level, player.blockPosition());
    }

    @SubscribeEvent
    public static void onRenderLevelStage(RenderLevelStageEvent event) {
        if (event.getStage() != Stage.AFTER_TRANSLUCENT_BLOCKS) {
            return;
        }

        Minecraft minecraft = Minecraft.getInstance();
        ClientLevel level = minecraft.level;
        if (level == null || trackedLevel != level) {
            return;
        }

        Vec3 cameraPos = event.getCamera().getPosition();
        PoseStack poseStack = event.getPoseStack();
        MultiBufferSource.BufferSource bufferSource = minecraft.renderBuffers().bufferSource();
        VertexConsumer consumer = null;
        boolean rendered = false;

        for (BlockEntity blockEntity : LOADED_BLOCK_ENTITIES.values()) {
            if (blockEntity.isRemoved() || blockEntity.getLevel() != level) {
                continue;
            }

            BoxVisual visual = resolveVisual(blockEntity);
            if (visual == null || !event.getFrustum().isVisible(visual.bounds())) {
                continue;
            }

            if (!rendered) {
                consumer = bufferSource.getBuffer(RenderType.lines());
                rendered = true;
            }

            poseStack.pushPose();
            poseStack.translate(-cameraPos.x, -cameraPos.y, -cameraPos.z);
            LevelRenderer.renderLineBox(
                    poseStack,
                    consumer,
                    visual.bounds(),
                    visual.red(),
                    visual.green(),
                    visual.blue(),
                    visual.alpha());
            poseStack.popPose();
        }

        if (rendered) {
            bufferSource.endBatch(RenderType.lines());
        }
    }

    private static void switchLevel(@Nullable ClientLevel level) {
        if (trackedLevel == level) {
            return;
        }

        LOADED_BLOCK_ENTITIES.clear();
        trackedLevel = level;
        nextRescanGameTime = Long.MIN_VALUE;
    }

    private static void registerChunk(ChunkAccess chunk) {
        if (!(chunk instanceof LevelChunk levelChunk)) {
            return;
        }

        for (BlockEntity blockEntity : levelChunk.getBlockEntities().values()) {
            if (isSupported(blockEntity)) {
                LOADED_BLOCK_ENTITIES.put(blockEntity.getBlockPos().immutable(), blockEntity);
            }
        }
    }

    private static void reconcileNearbyChunks(Minecraft minecraft, ClientLevel level, BlockPos playerPosition) {
        int radius = Math.max(2, minecraft.options.renderDistance().get());
        ChunkPos center = new ChunkPos(playerPosition);
        for (int chunkX = center.x - radius; chunkX <= center.x + radius; chunkX++) {
            for (int chunkZ = center.z - radius; chunkZ <= center.z + radius; chunkZ++) {
                ChunkAccess chunk = level.getChunkSource().getChunk(chunkX, chunkZ, ChunkStatus.FULL, false);
                if (chunk != null) {
                    registerChunk(chunk);
                }
            }
        }
    }

    private static void pruneRemovedBlockEntities(ClientLevel level) {
        LOADED_BLOCK_ENTITIES.entrySet().removeIf(entry -> {
            BlockEntity blockEntity = entry.getValue();
            return blockEntity.isRemoved() || blockEntity.getLevel() != level;
        });
    }

    private static boolean isSupported(BlockEntity blockEntity) {
        return blockEntity instanceof DataDistributionTowerBlockEntity || blockEntity instanceof DataExtractorBlockEntity || blockEntity instanceof DataSanctumBlockEntity;
    }

    @Nullable
    private static BoxVisual resolveVisual(BlockEntity blockEntity) {
        if (blockEntity instanceof DataDistributionTowerBlockEntity tower && tower.isRangeDisplayEnabled()) {
            return new BoxVisual(
                    tower.getCoverageAabb().deflate(TOWER_RANGE_LINE_INSET),
                    0.2F,
                    0.85F,
                    1.0F,
                    0.5F);
        }
        if (blockEntity instanceof DataExtractorBlockEntity extractor && extractor.isRangeDisplayEnabled()) {
            return new BoxVisual(extractor.getCoverageAabb(), 1.0F, 0.35F, 0.2F, 0.9F);
        }
        if (blockEntity instanceof DataSanctumBlockEntity sanctum && sanctum.canDisplayBlackHoleRange()) {
            return new BoxVisual(sanctum.getBlackHoleCoverageAabb(), 0.55F, 0.25F, 1.0F, 0.9F);
        }
        return null;
    }

    private record BoxVisual(AABB bounds, float red, float green, float blue, float alpha) {}
}

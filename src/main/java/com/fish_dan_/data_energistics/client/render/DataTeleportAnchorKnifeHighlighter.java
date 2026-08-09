package com.fish_dan_.data_energistics.client.render;

import com.fish_dan_.data_energistics.Data_Energistics;
import com.fish_dan_.data_energistics.block.DataTeleportAnchorBlock;
import com.fish_dan_.data_energistics.blockentity.DataTeleportAnchorBlockEntity;
import com.fish_dan_.data_energistics.item.powered.PoweredCuttingKnifeItem;
import com.fish_dan_.data_energistics.network.DataTeleportAnchorKnifeTeleportPayload;
import com.fish_dan_.data_energistics.registry.DEItems;

import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderStateShard;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.InputEvent;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import net.neoforged.neoforge.network.PacketDistributor;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexFormat;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.OptionalDouble;

@EventBusSubscriber(modid = Data_Energistics.MODID, value = Dist.CLIENT)
public final class DataTeleportAnchorKnifeHighlighter {

    private static final int CHUNK_RADIUS = 9;
    private static final int SABER_ENERGY_CHUNK_RADIUS = 11;
    private static final long RESCAN_INTERVAL_TICKS = 10L;
    private static final double BASE_BOX_INSET = 0.002d;
    private static final double SELECTED_BOX_EXPANSION = 0.25d;
    private static final double MAX_SELECT_DISTANCE = 128.0d;
    private static final RenderType SEE_THROUGH_LINES = RenderType.create(
            "data_energistics_teleport_anchor_highlight",
            DefaultVertexFormat.POSITION_COLOR_NORMAL,
            VertexFormat.Mode.LINES,
            1536,
            false,
            false,
            RenderType.CompositeState.builder()
                    .setShaderState(RenderStateShard.RENDERTYPE_LINES_SHADER)
                    .setLineState(new RenderStateShard.LineStateShard(OptionalDouble.of(2.0d)))
                    .setLayeringState(RenderStateShard.VIEW_OFFSET_Z_LAYERING)
                    .setTransparencyState(RenderStateShard.TRANSLUCENT_TRANSPARENCY)
                    .setOutputState(RenderStateShard.MAIN_TARGET)
                    .setWriteMaskState(RenderStateShard.COLOR_WRITE)
                    .setCullState(RenderStateShard.NO_CULL)
                    .setDepthTestState(RenderStateShard.NO_DEPTH_TEST)
                    .createCompositeState(false));

    private static int cachedCenterChunkX = Integer.MIN_VALUE;
    private static int cachedCenterChunkZ = Integer.MIN_VALUE;
    private static long cachedScanGameTime = Long.MIN_VALUE;
    private static List<BlockPos> cachedAnchorPositions = List.of();
    private static BlockPos selectedAnchorPos;

    private DataTeleportAnchorKnifeHighlighter() {}

    @SubscribeEvent
    public static void onInteractionKeyTriggered(InputEvent.InteractionKeyMappingTriggered event) {
        if (!event.isUseItem()) {
            return;
        }

        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null || minecraft.player == null || minecraft.screen != null) {
            return;
        }

        if (!tryTriggerRemoteTeleport(minecraft, event.getHand())) {
            return;
        }

        event.setCanceled(true);
        event.setSwingHand(true);
    }

    @SubscribeEvent
    public static void onMouseButtonPre(InputEvent.MouseButton.Pre event) {
        if (event.getButton() != GLFW.GLFW_MOUSE_BUTTON_RIGHT || event.getAction() != GLFW.GLFW_PRESS) {
            return;
        }

        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null || minecraft.player == null || minecraft.screen != null) {
            return;
        }

        if (tryTriggerRemoteTeleport(minecraft, InteractionHand.MAIN_HAND) || tryTriggerRemoteTeleport(minecraft, InteractionHand.OFF_HAND)) {
            event.setCanceled(true);
        }
    }

    private static boolean tryTriggerRemoteTeleport(Minecraft minecraft, InteractionHand hand) {
        if (!minecraft.player.getItemInHand(hand).is(DEItems.DATA_CRYSTAL_CUTTING_KNIFE.get())) {
            return false;
        }

        BlockPos anchorPos = resolveCurrentSelectedAnchor(minecraft);
        if (anchorPos == null) {
            return false;
        }

        HitResult hitResult = minecraft.hitResult;
        if (hitResult instanceof BlockHitResult blockHitResult && blockHitResult.getBlockPos().equals(anchorPos)) {
            return false;
        }
        if (hitResult != null && hitResult.getType() == HitResult.Type.BLOCK) {
            BlockPos hitPos = BlockPos.containing(hitResult.getLocation());
            if (hitPos.equals(anchorPos)) {
                return false;
            }
        }

        PacketDistributor.sendToServer(new DataTeleportAnchorKnifeTeleportPayload(anchorPos, hand == InteractionHand.OFF_HAND));
        minecraft.player.swing(hand);
        return true;
    }

    private static BlockPos resolveCurrentSelectedAnchor(Minecraft minecraft) {
        if (minecraft.level == null || minecraft.player == null) {
            selectedAnchorPos = null;
            return null;
        }

        List<BlockPos> anchors = getCachedAnchorPositions(minecraft);
        if (anchors.isEmpty()) {
            selectedAnchorPos = null;
            return null;
        }

        selectedAnchorPos = findSelectedAnchor(minecraft, anchors).orElse(null);
        return selectedAnchorPos;
    }

    @SubscribeEvent
    public static void onRenderLevelStage(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_PARTICLES) {
            return;
        }

        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null || minecraft.player == null) {
            selectedAnchorPos = null;
            return;
        }
        if (!isHoldingCuttingKnife(minecraft)) {
            selectedAnchorPos = null;
            return;
        }

        List<BlockPos> anchors = getCachedAnchorPositions(minecraft);
        if (anchors.isEmpty()) {
            selectedAnchorPos = null;
            return;
        }

        selectedAnchorPos = findSelectedAnchor(minecraft, anchors).orElse(null);

        PoseStack poseStack = event.getPoseStack();
        MultiBufferSource.BufferSource buffer = minecraft.renderBuffers().bufferSource();

        RenderSystem.disableDepthTest();
        RenderSystem.enableBlend();

        for (BlockPos anchorPos : anchors) {
            float[] color = resolveHighlightColor(minecraft, anchorPos);
            AABB box = anchorPos.equals(selectedAnchorPos) ? createSelectedBox(anchorPos) : createBaseBox(anchorPos);
            drawHighlight(box, color, poseStack, event.getCamera(), buffer);
        }

        buffer.endBatch(SEE_THROUGH_LINES);
        RenderSystem.enableDepthTest();
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
    }

    private static boolean isHoldingCuttingKnife(Minecraft minecraft) {
        return minecraft.player.getMainHandItem().is(DEItems.DATA_CRYSTAL_CUTTING_KNIFE.get()) || minecraft.player.getOffhandItem().is(DEItems.DATA_CRYSTAL_CUTTING_KNIFE.get());
    }

    private static Optional<BlockPos> findSelectedAnchor(Minecraft minecraft, List<BlockPos> anchors) {
        Vec3 start = minecraft.gameRenderer.getMainCamera().getPosition();
        Vec3 look = minecraft.player.getViewVector(1.0F);
        Vec3 end = start.add(look.scale(MAX_SELECT_DISTANCE));

        double closestDistance = Double.MAX_VALUE;
        BlockPos closestAnchor = null;
        for (BlockPos anchorPos : anchors) {
            AABB box = createBaseBox(anchorPos);
            Optional<Vec3> hit = box.clip(start, end);
            if (hit.isEmpty()) {
                continue;
            }

            double distance = hit.get().distanceToSqr(start);
            if (distance < closestDistance) {
                closestDistance = distance;
                closestAnchor = anchorPos;
            }
        }

        return Optional.ofNullable(closestAnchor);
    }

    private static void drawHighlight(AABB box, float[] color, PoseStack poseStack,
                                      Camera camera, MultiBufferSource buffer) {
        if (!camera.isInitialized()) {
            return;
        }

        var cameraPos = camera.getPosition();
        poseStack.pushPose();
        poseStack.translate(-cameraPos.x, -cameraPos.y, -cameraPos.z);
        var consumer = buffer.getBuffer(SEE_THROUGH_LINES);
        LevelRenderer.renderLineBox(poseStack, consumer, box, color[0], color[1], color[2], color[3]);
        poseStack.popPose();
    }

    private static AABB createBaseBox(BlockPos pos) {
        return new AABB(
                pos.getX() + BASE_BOX_INSET,
                pos.getY() + BASE_BOX_INSET,
                pos.getZ() + BASE_BOX_INSET,
                pos.getX() + 1.0d - BASE_BOX_INSET,
                pos.getY() + 1.0d - BASE_BOX_INSET,
                pos.getZ() + 1.0d - BASE_BOX_INSET);
    }

    private static AABB createSelectedBox(BlockPos pos) {
        return new AABB(
                pos.getX() - SELECTED_BOX_EXPANSION,
                pos.getY() - SELECTED_BOX_EXPANSION,
                pos.getZ() - SELECTED_BOX_EXPANSION,
                pos.getX() + 1.0d + SELECTED_BOX_EXPANSION,
                pos.getY() + 1.0d + SELECTED_BOX_EXPANSION,
                pos.getZ() + 1.0d + SELECTED_BOX_EXPANSION);
    }

    private static float[] resolveHighlightColor(Minecraft minecraft, BlockPos anchorPos) {
        var state = minecraft.level.getBlockState(anchorPos);
        int color = switch (state.hasProperty(DataTeleportAnchorBlock.COLOR) ? state.getValue(DataTeleportAnchorBlock.COLOR) : DataTeleportAnchorBlock.ColorVariant.DEFAULT) {
            case BLACK -> 0xFF303030;
            case BLUE -> 0xFF4066FF;
            case BROWN -> 0xFF7A4D2B;
            case CYAN -> 0xFF30D5C8;
            case GRAY -> 0xFF6D6D73;
            case GREEN -> 0xFF4CC94C;
            case LIGHT_BLUE -> 0xFF68B6FF;
            case LIGHT_GRAY -> 0xFFB8BCC2;
            case LIME -> 0xFF8CFF3F;
            case MAGENTA -> 0xFFF062D6;
            case ORANGE -> 0xFFFF9A2F;
            case PINK -> 0xFFFF95C8;
            case PURPLE -> 0xFFA85CFF;
            case RED -> 0xFFFF4A4A;
            case WHITE -> 0xFFFFFFFF;
            case YELLOW -> 0xFFFFF15A;
            case DEFAULT -> 0xFF26E65A;
        };
        return new float[] {
                ((color >> 16) & 0xFF) / 255.0F,
                ((color >> 8) & 0xFF) / 255.0F,
                (color & 0xFF) / 255.0F,
                ((color >> 24) & 0xFF) / 255.0F
        };
    }

    private static List<BlockPos> getCachedAnchorPositions(Minecraft minecraft) {
        var player = minecraft.player;
        var level = minecraft.level;
        if (player == null || level == null) {
            return List.of();
        }

        int centerChunkX = player.blockPosition().getX() >> 4;
        int centerChunkZ = player.blockPosition().getZ() >> 4;
        int chunkRadius = getChunkRadius(player);
        long gameTime = level.getGameTime();
        if (centerChunkX == cachedCenterChunkX && centerChunkZ == cachedCenterChunkZ && gameTime - cachedScanGameTime < RESCAN_INTERVAL_TICKS) {
            return cachedAnchorPositions;
        }

        List<BlockPos> anchors = new ArrayList<>();
        for (int chunkX = centerChunkX - chunkRadius; chunkX <= centerChunkX + chunkRadius; chunkX++) {
            for (int chunkZ = centerChunkZ - chunkRadius; chunkZ <= centerChunkZ + chunkRadius; chunkZ++) {
                if (!level.hasChunk(chunkX, chunkZ)) {
                    continue;
                }

                var chunk = level.getChunk(chunkX, chunkZ);
                for (BlockPos blockEntityPos : chunk.getBlockEntitiesPos()) {
                    if (chunk.getBlockEntity(blockEntityPos) instanceof DataTeleportAnchorBlockEntity) {
                        anchors.add(blockEntityPos.immutable());
                    }
                }
            }
        }

        cachedCenterChunkX = centerChunkX;
        cachedCenterChunkZ = centerChunkZ;
        cachedScanGameTime = gameTime;
        cachedAnchorPositions = List.copyOf(anchors);
        return cachedAnchorPositions;
    }

    private static int getChunkRadius(Player player) {
        if (player == null) {
            return CHUNK_RADIUS;
        }

        return player.getMainHandItem().getItem() instanceof PoweredCuttingKnifeItem knife && knife.getUpgrades(player.getMainHandItem()).getInstalledUpgrades(DEItems.CARD_SABER_ENERGY.get()) > 0 || player.getOffhandItem().getItem() instanceof PoweredCuttingKnifeItem offhandKnife && offhandKnife.getUpgrades(player.getOffhandItem()).getInstalledUpgrades(DEItems.CARD_SABER_ENERGY.get()) > 0 ? SABER_ENERGY_CHUNK_RADIUS : CHUNK_RADIUS;
    }
}

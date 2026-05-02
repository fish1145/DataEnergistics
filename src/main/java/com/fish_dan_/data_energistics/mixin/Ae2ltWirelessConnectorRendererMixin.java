package com.fish_dan_.data_energistics.mixin;

import appeng.client.render.overlay.OverlayRenderType;
import com.fish_dan_.data_energistics.blockentity.AdaptivePatternProviderBlockEntity;
import com.fish_dan_.data_energistics.integration.Ae2LtAdaptiveProviderCompat;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.moakiee.ae2lt.client.Ae2ltRenderTypes;
import com.moakiee.ae2lt.item.OverloadedWirelessConnectorItem;
import com.moakiee.ae2lt.logic.WirelessConnectorTargetHelper;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.HashSet;
import java.util.Set;
import java.util.function.Function;

@Mixin(value = com.moakiee.ae2lt.client.WirelessConnectorRenderer.class, remap = false)
public abstract class Ae2ltWirelessConnectorRendererMixin {
    @Unique private static final int DE_COLOR_PREVIEW = 0x60FFFF00;
    @Unique private static final int DE_COLOR_CONNECTED = 0x600080FF;
    @Unique private static final int DE_COLOR_PREVIEW_LINE = 0xC0FFFF00;
    @Unique private static final int DE_COLOR_HOST = 0x800080FF;
    @Unique private static final int DE_COLOR_HOST_SELECTED = 0x80FFFF00;
    @Unique private static final int DE_COLOR_LINE = 0xC00080FF;
    @Unique private static final int DE_SCAN_RANGE = 64;
    @Unique private static final String DE_TAG_SELECTED = "SelectedProvider";
    @Unique private static final String DE_TAG_DIM = "Dim";
    @Unique private static final String DE_TAG_POS = "Pos";
    @Unique private static final String DE_TAG_HOST_TYPE = "HostType";

    @Inject(method = "onRenderLevelStage", at = @At("HEAD"))
    private static void dataEnergistics$renderAdaptiveProviders(RenderLevelStageEvent event, CallbackInfo ci) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_LEVEL) {
            return;
        }

        var mc = Minecraft.getInstance();
        var player = mc.player;
        if (player == null || mc.level == null) {
            return;
        }

        ItemStack stack = ItemStack.EMPTY;
        for (var hand : net.minecraft.world.InteractionHand.values()) {
            var held = player.getItemInHand(hand);
            if (held.getItem() instanceof OverloadedWirelessConnectorItem) {
                stack = held;
                break;
            }
        }
        if (stack.isEmpty()) {
            return;
        }

        PoseStack poseStack = event.getPoseStack();
        MultiBufferSource.BufferSource buffer = mc.renderBuffers().bufferSource();

        poseStack.pushPose();
        Vec3 cam = mc.gameRenderer.getMainCamera().getPosition();
        Quaternionf rotation = new Quaternionf(mc.gameRenderer.getMainCamera().rotation());
        rotation.invert();
        poseStack.mulPose(rotation);
        poseStack.translate(-cam.x, -cam.y, -cam.z);

        var selectedHost = dataEnergistics$getSelectedHost(stack);
        var selectedPos = selectedHost != null ? selectedHost.pos() : null;
        var selectedDim = selectedHost != null ? selectedHost.dimension() : null;
        var selectedHostType = selectedHost != null ? selectedHost.hostType() : null;
        boolean hasSelection = selectedPos != null
                && selectedDim != null
                && selectedHostType != null
                && mc.level.dimension().equals(selectedDim);
        boolean selectedRendered = false;

        BlockPos playerPos = player.blockPosition();
        int minCX = (playerPos.getX() - DE_SCAN_RANGE) >> 4;
        int maxCX = (playerPos.getX() + DE_SCAN_RANGE) >> 4;
        int minCZ = (playerPos.getZ() - DE_SCAN_RANGE) >> 4;
        int maxCZ = (playerPos.getZ() + DE_SCAN_RANGE) >> 4;

        for (int cx = minCX; cx <= maxCX; cx++) {
            for (int cz = minCZ; cz <= maxCZ; cz++) {
                if (!mc.level.hasChunk(cx, cz)) {
                    continue;
                }
                var chunk = mc.level.getChunk(cx, cz);
                for (var bePos : chunk.getBlockEntitiesPos()) {
                    var be = chunk.getBlockEntity(bePos);
                    if (!(be instanceof AdaptivePatternProviderBlockEntity adaptive)
                            || !adaptive.isAe2LightningTechOverloadedProviderSelected()
                            || !adaptive.isAe2LtWirelessMode()) {
                        continue;
                    }

                    boolean isSelected = hasSelection
                            && OverloadedWirelessConnectorItem.HOST_PROVIDER.equals(selectedHostType)
                            && bePos.equals(selectedPos);
                    dataEnergistics$renderAdaptiveProviderHost(poseStack, buffer, mc.level, bePos, adaptive, isSelected);
                    selectedRendered |= isSelected;
                }
            }
        }

        if (hasSelection && !selectedRendered && mc.level.isLoaded(selectedPos)) {
            var selectedBe = mc.level.getBlockEntity(selectedPos);
            if (Ae2LtAdaptiveProviderCompat.isAdaptiveOverloadedProvider(selectedBe)
                    && Ae2LtAdaptiveProviderCompat.isWirelessMode(selectedBe)
                    && OverloadedWirelessConnectorItem.HOST_PROVIDER.equals(selectedHostType)) {
                dataEnergistics$renderAdaptiveProviderHost(
                        poseStack, buffer, mc.level, selectedPos, (AdaptivePatternProviderBlockEntity) selectedBe, true);
            }
        }

        if (hasSelection) {
            var selectedBe = mc.level.getBlockEntity(selectedPos);
            if (OverloadedWirelessConnectorItem.HOST_PROVIDER.equals(selectedHostType)
                    && selectedBe instanceof AdaptivePatternProviderBlockEntity adaptive
                    && adaptive.isAe2LightningTechOverloadedProviderSelected()
                    && adaptive.isAe2LtWirelessMode()
                    && mc.hitResult instanceof BlockHitResult bhr
                    && bhr.getType() == HitResult.Type.BLOCK
                    && !bhr.getBlockPos().equals(selectedPos)
                    && mc.level.getBlockEntity(bhr.getBlockPos()) != null) {

                var previewTargets = WirelessConnectorTargetHelper.collectTargets(
                        mc.level,
                        bhr.getBlockPos(),
                        net.minecraft.client.gui.screens.Screen.hasControlDown());
                Direction lookFace = bhr.getDirection();
                var existingConnections = dataEnergistics$collectConnectionsForFace(
                        adaptive.getConnections(),
                        mc.level,
                        lookFace,
                        c -> c.dimension(),
                        c -> c.pos(),
                        c -> c.boundFace());
                for (var lookPos : previewTargets) {
                    if (!existingConnections.contains(lookPos)) {
                        dataEnergistics$renderFaceOverlay(poseStack, buffer, lookPos, lookFace, DE_COLOR_PREVIEW);
                        dataEnergistics$renderLine(poseStack, buffer, selectedPos, lookPos, lookFace, DE_COLOR_PREVIEW_LINE);
                    }
                }
            }
        }
        poseStack.popPose();
    }

    @Unique
    private static void dataEnergistics$renderAdaptiveProviderHost(PoseStack poseStack, MultiBufferSource buffer,
                                                                   Level level, BlockPos hostPos,
                                                                   AdaptivePatternProviderBlockEntity provider,
                                                                   boolean selected) {
        dataEnergistics$renderInnerCube(poseStack, buffer, hostPos, selected ? DE_COLOR_HOST_SELECTED : DE_COLOR_HOST);

        for (var conn : provider.getConnections()) {
            if (!conn.dimension().equals(level.dimension())) {
                continue;
            }
            dataEnergistics$renderFaceOverlay(poseStack, buffer, conn.pos(), conn.boundFace(), DE_COLOR_CONNECTED);
            dataEnergistics$renderLine(poseStack, buffer, hostPos, conn.pos(), conn.boundFace(), DE_COLOR_LINE);
        }
    }

    @Unique
    private static <T> Set<BlockPos> dataEnergistics$collectConnectionsForFace(Iterable<T> connections,
                                                                               Level level, Direction face,
                                                                               Function<T, ResourceKey<Level>> dimensionGetter,
                                                                               Function<T, BlockPos> posGetter,
                                                                               Function<T, Direction> faceGetter) {
        Set<BlockPos> result = new HashSet<>();
        for (var conn : connections) {
            if (dimensionGetter.apply(conn).equals(level.dimension()) && faceGetter.apply(conn) == face) {
                result.add(posGetter.apply(conn));
            }
        }
        return result;
    }

    @Unique
    private static SelectedHost dataEnergistics$getSelectedHost(ItemStack stack) {
        var tag = stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag();
        if (!tag.contains(DE_TAG_SELECTED, CompoundTag.TAG_COMPOUND)) {
            return null;
        }
        var sel = tag.getCompound(DE_TAG_SELECTED);
        var dimStr = sel.getString(DE_TAG_DIM);
        if (dimStr.isEmpty()) {
            return null;
        }
        return new SelectedHost(
                BlockPos.of(sel.getLong(DE_TAG_POS)),
                ResourceKey.create(Registries.DIMENSION, ResourceLocation.parse(dimStr)),
                sel.contains(DE_TAG_HOST_TYPE, CompoundTag.TAG_STRING)
                        ? sel.getString(DE_TAG_HOST_TYPE)
                        : OverloadedWirelessConnectorItem.HOST_PROVIDER);
    }

    @Unique
    private static void dataEnergistics$renderInnerCube(PoseStack poseStack, MultiBufferSource buffer,
                                                        BlockPos pos, int color) {
        VertexConsumer vc = buffer.getBuffer(Ae2ltRenderTypes.getFaceSeeThrough());
        int[] c = OverlayRenderType.decomposeColor(color);

        poseStack.pushPose();
        poseStack.translate(pos.getX(), pos.getY(), pos.getZ());
        Matrix4f mat = poseStack.last().pose();

        float lo = 0.25f;
        float hi = 0.75f;

        dataEnergistics$quad(vc, mat, c, lo, lo, lo, hi, lo, lo, hi, lo, hi, lo, lo, hi, 0, -1, 0);
        dataEnergistics$quad(vc, mat, c, lo, hi, hi, hi, hi, hi, hi, hi, lo, lo, hi, lo, 0, 1, 0);
        dataEnergistics$quad(vc, mat, c, lo, lo, lo, lo, hi, lo, hi, hi, lo, hi, lo, lo, 0, 0, -1);
        dataEnergistics$quad(vc, mat, c, hi, lo, hi, hi, hi, hi, lo, hi, hi, lo, lo, hi, 0, 0, 1);
        dataEnergistics$quad(vc, mat, c, lo, lo, hi, lo, hi, hi, lo, hi, lo, lo, lo, lo, -1, 0, 0);
        dataEnergistics$quad(vc, mat, c, hi, lo, lo, hi, hi, lo, hi, hi, hi, hi, lo, hi, 1, 0, 0);

        poseStack.popPose();
    }

    @Unique
    private static void dataEnergistics$quad(VertexConsumer vc, Matrix4f mat, int[] c,
                                             float x1, float y1, float z1,
                                             float x2, float y2, float z2,
                                             float x3, float y3, float z3,
                                             float x4, float y4, float z4,
                                             float nx, float ny, float nz) {
        vc.addVertex(mat, x1, y1, z1).setColor(c[1], c[2], c[3], c[0]).setNormal(nx, ny, nz);
        vc.addVertex(mat, x2, y2, z2).setColor(c[1], c[2], c[3], c[0]).setNormal(nx, ny, nz);
        vc.addVertex(mat, x3, y3, z3).setColor(c[1], c[2], c[3], c[0]).setNormal(nx, ny, nz);
        vc.addVertex(mat, x4, y4, z4).setColor(c[1], c[2], c[3], c[0]).setNormal(nx, ny, nz);
    }

    @Unique
    private static void dataEnergistics$renderFaceOverlay(PoseStack poseStack, MultiBufferSource buffer,
                                                          BlockPos pos, Direction face, int color) {
        VertexConsumer vc = buffer.getBuffer(OverlayRenderType.getBlockHilightFace());
        int[] c = OverlayRenderType.decomposeColor(color);

        poseStack.pushPose();
        poseStack.translate(pos.getX(), pos.getY(), pos.getZ());
        Matrix4f mat = poseStack.last().pose();
        float offset = 0.001f;

        float nx = face.getStepX();
        float ny = face.getStepY();
        float nz = face.getStepZ();

        switch (face) {
            case DOWN -> {
                vc.addVertex(mat, 0, -offset, 0).setColor(c[1], c[2], c[3], c[0]).setNormal(nx, ny, nz);
                vc.addVertex(mat, 1, -offset, 0).setColor(c[1], c[2], c[3], c[0]).setNormal(nx, ny, nz);
                vc.addVertex(mat, 1, -offset, 1).setColor(c[1], c[2], c[3], c[0]).setNormal(nx, ny, nz);
                vc.addVertex(mat, 0, -offset, 1).setColor(c[1], c[2], c[3], c[0]).setNormal(nx, ny, nz);
            }
            case UP -> {
                vc.addVertex(mat, 0, 1 + offset, 1).setColor(c[1], c[2], c[3], c[0]).setNormal(nx, ny, nz);
                vc.addVertex(mat, 1, 1 + offset, 1).setColor(c[1], c[2], c[3], c[0]).setNormal(nx, ny, nz);
                vc.addVertex(mat, 1, 1 + offset, 0).setColor(c[1], c[2], c[3], c[0]).setNormal(nx, ny, nz);
                vc.addVertex(mat, 0, 1 + offset, 0).setColor(c[1], c[2], c[3], c[0]).setNormal(nx, ny, nz);
            }
            case NORTH -> {
                vc.addVertex(mat, 0, 0, -offset).setColor(c[1], c[2], c[3], c[0]).setNormal(nx, ny, nz);
                vc.addVertex(mat, 0, 1, -offset).setColor(c[1], c[2], c[3], c[0]).setNormal(nx, ny, nz);
                vc.addVertex(mat, 1, 1, -offset).setColor(c[1], c[2], c[3], c[0]).setNormal(nx, ny, nz);
                vc.addVertex(mat, 1, 0, -offset).setColor(c[1], c[2], c[3], c[0]).setNormal(nx, ny, nz);
            }
            case SOUTH -> {
                vc.addVertex(mat, 1, 0, 1 + offset).setColor(c[1], c[2], c[3], c[0]).setNormal(nx, ny, nz);
                vc.addVertex(mat, 1, 1, 1 + offset).setColor(c[1], c[2], c[3], c[0]).setNormal(nx, ny, nz);
                vc.addVertex(mat, 0, 1, 1 + offset).setColor(c[1], c[2], c[3], c[0]).setNormal(nx, ny, nz);
                vc.addVertex(mat, 0, 0, 1 + offset).setColor(c[1], c[2], c[3], c[0]).setNormal(nx, ny, nz);
            }
            case WEST -> {
                vc.addVertex(mat, -offset, 0, 1).setColor(c[1], c[2], c[3], c[0]).setNormal(nx, ny, nz);
                vc.addVertex(mat, -offset, 1, 1).setColor(c[1], c[2], c[3], c[0]).setNormal(nx, ny, nz);
                vc.addVertex(mat, -offset, 1, 0).setColor(c[1], c[2], c[3], c[0]).setNormal(nx, ny, nz);
                vc.addVertex(mat, -offset, 0, 0).setColor(c[1], c[2], c[3], c[0]).setNormal(nx, ny, nz);
            }
            case EAST -> {
                vc.addVertex(mat, 1 + offset, 0, 0).setColor(c[1], c[2], c[3], c[0]).setNormal(nx, ny, nz);
                vc.addVertex(mat, 1 + offset, 1, 0).setColor(c[1], c[2], c[3], c[0]).setNormal(nx, ny, nz);
                vc.addVertex(mat, 1 + offset, 1, 1).setColor(c[1], c[2], c[3], c[0]).setNormal(nx, ny, nz);
                vc.addVertex(mat, 1 + offset, 0, 1).setColor(c[1], c[2], c[3], c[0]).setNormal(nx, ny, nz);
            }
        }

        poseStack.popPose();
    }

    @Unique
    private static void dataEnergistics$renderLine(PoseStack poseStack, MultiBufferSource buffer,
                                                   BlockPos from, BlockPos to, Direction face, int color) {
        VertexConsumer vc = buffer.getBuffer(OverlayRenderType.getBlockHilightLine());
        int[] c = OverlayRenderType.decomposeColor(color);

        Matrix4f mat = poseStack.last().pose();
        float fx = from.getX() + 0.5f;
        float fy = from.getY() + 0.5f;
        float fz = from.getZ() + 0.5f;

        float tx = to.getX() + 0.5f + face.getStepX() * 0.501f;
        float ty = to.getY() + 0.5f + face.getStepY() * 0.501f;
        float tz = to.getZ() + 0.5f + face.getStepZ() * 0.501f;

        float dx = tx - fx;
        float dy = ty - fy;
        float dz = tz - fz;
        float len = (float) Math.sqrt(dx * dx + dy * dy + dz * dz);
        if (len < 1.0e-6f) {
            return;
        }
        float nx = dx / len;
        float ny = dy / len;
        float nz = dz / len;

        vc.addVertex(mat, fx, fy, fz).setColor(c[1], c[2], c[3], c[0]).setNormal(nx, ny, nz);
        vc.addVertex(mat, tx, ty, tz).setColor(c[1], c[2], c[3], c[0]).setNormal(nx, ny, nz);
    }

    @Unique
    private record SelectedHost(BlockPos pos, ResourceKey<Level> dimension, String hostType) {
    }
}

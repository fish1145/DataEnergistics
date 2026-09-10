package com.fish_dan_.data_energistics.client.render.item.crossbow;

import com.fish_dan_.data_energistics.Data_Energistics;
import com.fish_dan_.data_energistics.effect.ChromaticGlow;
import com.fish_dan_.data_energistics.item.powered.MatterConvergingCrossbowItem;
import com.fish_dan_.data_energistics.network.action.ChromaticGlowPayload;
import com.fish_dan_.data_energistics.network.action.RailChainPayload;
import com.fish_dan_.data_energistics.registry.DEDataComponents;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderGuiEvent;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import org.joml.Vector3f;
import org.jspecify.annotations.Nullable;

import java.util.List;
import java.util.Locale;

/** Brief impact-only FE arcs and server-authored cooldown HUD. */
@EventBusSubscriber(modid = Data_Energistics.MODID, value = Dist.CLIENT)
public final class RailImpactPresentation {

    private static final List<Chain> CHAINS = new ObjectArrayList<>();
    private static @Nullable ClientLevel trackedLevel;

    private RailImpactPresentation() {}

    public static void accept(RailChainPayload payload) {
        ClientLevel level = Minecraft.getInstance().level;
        if (level == null) return;
        if (trackedLevel != level) {
            CHAINS.clear();
            trackedLevel = level;
        }
        CHAINS.add(new Chain(payload.points(), level.getGameTime() + 6));
    }

    public static void glow(ChromaticGlowPayload payload) {
        ClientLevel level = Minecraft.getInstance().level;
        if (level == null) return;
        var entity = level.getEntity(payload.entity());
        if (entity != null) ChromaticGlow.accept(entity, payload.color(), payload.until());
    }

    @SubscribeEvent
    public static void render(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_PARTICLES) return;
        Minecraft minecraft = Minecraft.getInstance();
        ClientLevel level = minecraft.level;
        if (level == null || level != trackedLevel) {
            CHAINS.clear();
            return;
        }
        CHAINS.removeIf(chain -> chain.until <= level.getGameTime());
        if (CHAINS.isEmpty()) return;
        PoseStack pose = event.getPoseStack();
        Vec3 camera = event.getCamera().getPosition();
        pose.pushPose();
        pose.translate(-camera.x, -camera.y, -camera.z);
        VertexConsumer vertices = minecraft.renderBuffers().bufferSource().getBuffer(RenderType.lines());
        for (Chain chain : CHAINS) {
            Vec3 center = chain.points.getFirst();
            for (int i = 1; i < chain.points.size(); i++) {
                Vec3 delta = chain.points.get(i).subtract(center), previous = center;
                for (int piece = 1; piece <= 6; piece++) {
                    double wave = piece == 6 ? 0 : Math.sin(piece * 4.3 + level.getGameTime()) * 0.14;
                    Vec3 next = center.add(delta.scale(piece / 6.0)).add(wave, -wave * 0.7, wave);
                    segment(vertices, pose.last(), previous, next);
                    previous = next;
                }
            }
        }
        pose.popPose();
        minecraft.renderBuffers().bufferSource().endBatch(RenderType.lines());
    }

    private static void segment(VertexConsumer vertices, PoseStack.Pose pose, Vec3 from, Vec3 to) {
        Vector3f direction = to.subtract(from).toVector3f();
        if (direction.lengthSquared() < 1.0E-9F) return;
        direction.normalize();
        vertices.addVertex(pose, (float) from.x, (float) from.y, (float) from.z).setColor(138, 189, 255, 255).setNormal(pose, direction.x, direction.y, direction.z);
        vertices.addVertex(pose, (float) to.x, (float) to.y, (float) to.z).setColor(138, 189, 255, 255).setNormal(pose, direction.x, direction.y, direction.z);
    }

    @SubscribeEvent
    public static void hud(RenderGuiEvent.Post event) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null || minecraft.level == null || minecraft.options.hideGui) return;
        long remaining = 0;
        int duration = CrossbowRailRecoil.DURATION_TICKS;
        for (InteractionHand hand : InteractionHand.values()) {
            var stack = minecraft.player.getItemInHand(hand);
            long time = stack.getOrDefault(DEDataComponents.RAIL_COOLDOWN_END.get(), 0L) - minecraft.level.getGameTime();
            if (stack.getItem() instanceof MatterConvergingCrossbowItem && time > remaining) {
                remaining = time;
                duration = stack.getOrDefault(DEDataComponents.RAIL_COOLDOWN_DURATION.get(), CrossbowRailRecoil.DURATION_TICKS);
            }
        }
        if (remaining <= 0) return;
        int left = event.getGuiGraphics().guiWidth() / 2 - 60;
        event.getGuiGraphics().fill(left, 24, left + 120, 29, 0xB0303030);
        event.getGuiGraphics().fill(left, 24, left + (int) (120 * Math.min(1, remaining / (double) duration)), 29, 0xFF72D9F3);
        event.getGuiGraphics().drawCenteredString(minecraft.font, Component.translatable("hud.data_energistics.rail_cooldown", String.format(Locale.ROOT, "%.1f", remaining / 20.0)), left + 60, 12, 0xFFFFFF);
    }

    private record Chain(List<Vec3> points, long until) {}
}

package com.fish_dan_.data_energistics.client.render.item.crossbow;

import com.fish_dan_.data_energistics.Data_Energistics;
import com.fish_dan_.data_energistics.effect.ChromaticGlow;
import com.fish_dan_.data_energistics.item.powered.MatterConvergingCrossbowItem;
import com.fish_dan_.data_energistics.item.powered.cannon.rail.RailFiring;
import com.fish_dan_.data_energistics.network.action.ChromaticGlowPayload;
import com.fish_dan_.data_energistics.network.action.RailBeamPayload;
import com.fish_dan_.data_energistics.registry.DEDataComponents;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderGuiEvent;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import org.joml.Vector3f;
import org.jspecify.annotations.Nullable;

import java.util.Locale;

/** Server-authored contacts expire promptly; rendering never determines hit damage or cooldown. */
@EventBusSubscriber(modid = Data_Energistics.MODID, value = Dist.CLIENT)
public final class RailBeamPresentation {

    private static final Int2ObjectMap<Beam> BEAMS = new Int2ObjectOpenHashMap<>();
    private static @Nullable ClientLevel trackedLevel;

    private RailBeamPresentation() {}

    public static void accept(RailBeamPayload payload) {
        ClientLevel level = Minecraft.getInstance().level;
        if (level == null) return;
        if (trackedLevel != level) {
            BEAMS.clear();
            trackedLevel = level;
        }
        BEAMS.put(payload.owner(), new Beam(payload, level.getGameTime() + 3));
    }

    public static void glow(ChromaticGlowPayload payload) {
        ClientLevel level = Minecraft.getInstance().level;
        if (level != null && level.getEntity(payload.entity()) != null) ChromaticGlow.accept(level.getEntity(payload.entity()), payload.color(), payload.until());
    }

    @SubscribeEvent
    public static void render(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_PARTICLES) return;
        Minecraft minecraft = Minecraft.getInstance();
        ClientLevel level = minecraft.level;
        if (level == null || level != trackedLevel) {
            BEAMS.clear();
            return;
        }
        BEAMS.values().removeIf(beam -> beam.until <= level.getGameTime());
        PoseStack pose = event.getPoseStack();
        Vec3 camera = event.getCamera().getPosition();
        pose.pushPose();
        pose.translate(-camera.x, -camera.y, -camera.z);
        VertexConsumer vertices = minecraft.renderBuffers().bufferSource().getBuffer(RenderType.lines());
        for (Beam beam : BEAMS.values()) {
            var payload = beam.payload;
            if (!(level.getEntity(payload.owner()) instanceof LivingEntity owner)) continue;
            if (!owner.getItemInHand(payload.hand()).has(DEDataComponents.RAIL_SESSION.get())) continue;
            Vec3 start = CannonModelAnchors.muzzle(owner, payload.hand(), owner.getEyePosition().add(owner.getViewVector(1).scale(0.7)));
            Vec3 end = payload.points().getFirst();
            segment(vertices, pose.last(), start, end, payload.color());
            if (payload.chain()) {
                for (int i = 1; i < payload.points().size(); i++) {
                    Vec3 target = payload.points().get(i);
                    Vec3 delta = target.subtract(end);
                    Vec3 previous = end;
                    for (int piece = 1; piece <= 6; piece++) {
                        double wave = piece == 6 ? 0 : Math.sin(piece * 4.3 + level.getGameTime()) * 0.14;
                        Vec3 next = end.add(delta.scale(piece / 6.0)).add(wave, -wave * 0.7, wave);
                        segment(vertices, pose.last(), previous, next, payload.color());
                        previous = next;
                    }
                }
            }
        }
        pose.popPose();
        minecraft.renderBuffers().bufferSource().endBatch(RenderType.lines());
    }

    private static void segment(VertexConsumer vertices, PoseStack.Pose pose, Vec3 from, Vec3 to, int color) {
        Vector3f direction = to.subtract(from).toVector3f();
        if (direction.lengthSquared() < 1.0E-9F) return;
        direction.normalize();
        int red = color >> 16 & 255, green = color >> 8 & 255, blue = color & 255;
        vertices.addVertex(pose, (float) from.x, (float) from.y, (float) from.z).setColor(red, green, blue, 255).setNormal(pose, direction.x, direction.y, direction.z);
        vertices.addVertex(pose, (float) to.x, (float) to.y, (float) to.z).setColor(red, green, blue, 255).setNormal(pose, direction.x, direction.y, direction.z);
    }

    @SubscribeEvent
    public static void hud(RenderGuiEvent.Post event) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null || minecraft.level == null || minecraft.options.hideGui) return;
        long remaining = 0;
        for (InteractionHand hand : InteractionHand.values()) {
            var stack = minecraft.player.getItemInHand(hand);
            if (stack.getItem() instanceof MatterConvergingCrossbowItem) remaining = Math.max(remaining,
                    stack.getOrDefault(DEDataComponents.RAIL_COOLDOWN_END.get(), 0L) - minecraft.level.getGameTime());
        }
        if (remaining <= 0) return;
        int left = event.getGuiGraphics().guiWidth() / 2 - 60;
        event.getGuiGraphics().fill(left, 24, left + 120, 29, 0xB0303030);
        event.getGuiGraphics().fill(left, 24, left + (int) (120 * Math.min(1, remaining / (double) RailFiring.COOLDOWN_TICKS)), 29, 0xFF72D9F3);
        event.getGuiGraphics().drawCenteredString(minecraft.font, Component.translatable("hud.data_energistics.rail_cooldown", String.format(Locale.ROOT, "%.1f", remaining / 20.0)), left + 60, 12, 0xFFFFFF);
    }

    private record Beam(RailBeamPayload payload, long until) {}
}

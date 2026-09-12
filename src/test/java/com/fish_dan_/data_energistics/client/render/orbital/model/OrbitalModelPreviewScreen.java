package com.fish_dan_.data_energistics.client.render.orbital.model;

import com.fish_dan_.data_energistics.Data_Energistics;
import com.fish_dan_.data_energistics.client.render.orbital.geometry.OrbitalProjectionPlacement.Detail;
import com.fish_dan_.data_energistics.orbital.attack.OrbitalAttackMode;

import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.network.chat.Component;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;

import com.mojang.blaze3d.platform.Lighting;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;

import java.util.concurrent.atomic.AtomicInteger;

/** Opt-in native GPU snapshots of the production assembly and baked resources, without opening or modifying a world. */
@EventBusSubscriber(modid = Data_Energistics.MODID, value = Dist.CLIENT)
public final class OrbitalModelPreviewScreen extends Screen {

    private static final String[] VIEWS = { "full", "reduced", "distant", "kinetic", "directed", "annihilation", "payload" };
    private static boolean started;
    private final AtomicInteger saved = new AtomicInteger();
    private int view;
    private int frames;

    private OrbitalModelPreviewScreen() {
        super(Component.literal("Orbital model verification"));
    }

    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        if (started || !"true".equals(System.getenv("DE_ORBITAL_MODEL_PREVIEW"))) {
            return;
        }
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.screen instanceof TitleScreen && minecraft.getOverlay() == null) {
            started = true;
            minecraft.setScreen(new OrbitalModelPreviewScreen());
        }
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        Minecraft client = Minecraft.getInstance();
        graphics.fill(0, 0, width, height, 0xFFE7EAED);
        graphics.flush();
        Lighting.setupFor3DItems();
        RenderSystem.enableDepthTest();
        float fogStart = RenderSystem.getShaderFogStart();
        float fogEnd = RenderSystem.getShaderFogEnd();
        RenderSystem.setShaderFogStart(10_000);
        RenderSystem.setShaderFogEnd(20_000);
        PoseStack poses = graphics.pose();
        poses.pushPose();
        poses.translate(width * 0.5, height * 0.47, 200);
        float scale = view < 3 ? Math.min(width / 620.0F, height / 190.0F) : (view == 6 ? Math.min(width, height) / 3.5F : Math.min(width / 180.0F, height / 170.0F));
        poses.scale(scale, -scale, scale);
        poses.mulPose(Axis.XP.rotationDegrees(-18));
        poses.mulPose(Axis.YP.rotationDegrees(-25));
        MultiBufferSource.BufferSource buffers = client.renderBuffers().bufferSource();
        try {
            Detail detail = view == 1 ? Detail.REDUCED : (view == 2 ? Detail.DISTANT : Detail.FULL);
            for (int pass = 0; pass < 2; pass++) {
                RenderType type = pass == 0 ? OrbitalModelRenderer.SOLID : OrbitalModelRenderer.EMISSIVE;
                OrbitalModelRenderer renderer = new OrbitalModelRenderer(buffers.getBuffer(type), detail == Detail.FULL,
                        pass == 1, LightTexture.FULL_BRIGHT, 1, 1, 1, 1);
                if (view < 3) {
                    OrbitalConstructModel.render(poses, renderer, detail, 120, 42, false);
                } else if (view < 6) {
                    OrbitalConstructModel.echo(poses, renderer, detail, OrbitalAttackMode.values()[view - 3], 120, 42, true);
                } else {
                    OrbitalConstructModel.payload(poses, renderer, 0, 0, 0, 1.4F, 0.4F);
                }
                buffers.endBatch(type);
            }
        } finally {
            poses.popPose();
            RenderSystem.setShaderFogStart(fogStart);
            RenderSystem.setShaderFogEnd(fogEnd);
            RenderSystem.disableDepthTest();
        }
        graphics.drawString(font, "Orbital / " + VIEWS[view], 12, 12, 0xFF30363D, false);
        graphics.flush();
        if (++frames == 12) {
            Screenshot.grab(client.gameDirectory, "orbital-model-" + VIEWS[view] + ".png", client.getMainRenderTarget(), message -> {
                Data_Energistics.LOGGER.info("Orbital render snapshot: {}", message.getString());
                saved.incrementAndGet();
            });
        }
        if (frames > 16 && view < VIEWS.length - 1) {
            view++;
            frames = 0;
        }
        if (saved.get() == VIEWS.length) {
            client.stop();
        }
    }
}

package com.fish_dan_.data_energistics.client;

import com.fish_dan_.data_energistics.Data_Energistics;
import com.fish_dan_.data_energistics.ae2.DataFlowKey;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;
import appeng.api.client.AEKeyRenderHandler;

public final class DataFlowKeyRenderHandler implements AEKeyRenderHandler<DataFlowKey> {
    private static final ResourceLocation TEXTURE = ResourceLocation.fromNamespaceAndPath(Data_Energistics.MODID, "textures/block/flow/data_flow.png");

    @Override
    public void drawInGui(Minecraft minecraft, GuiGraphics guiGraphics, int x, int y, DataFlowKey key) {
        guiGraphics.blit(TEXTURE, x, y, 0, 0.0F, 0.0F, 16, 16, 16, 16);
    }

    @Override
    public void drawOnBlockFace(PoseStack poseStack, MultiBufferSource buffers, DataFlowKey key, float scale, int light, Level level) {
    }

    @Override
    public Component getDisplayName(DataFlowKey key) {
        return key.getDisplayName();
    }
}

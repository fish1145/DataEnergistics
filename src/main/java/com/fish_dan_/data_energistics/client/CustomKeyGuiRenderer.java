package com.fish_dan_.data_energistics.client;

import com.fish_dan_.data_energistics.Data_Energistics;
import com.fish_dan_.data_energistics.ae2.DataFlowKey;
import com.fish_dan_.data_energistics.ae2.DataKey;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.inventory.InventoryMenu;
import net.neoforged.neoforge.client.extensions.common.IClientFluidTypeExtensions;
import net.neoforged.neoforge.fluids.FluidStack;

import appeng.api.stacks.AEFluidKey;
import appeng.api.client.AEKeyRendering;
import appeng.api.stacks.AEKey;
import appeng.client.gui.style.Blitter;

public final class CustomKeyGuiRenderer {

    private static final ResourceLocation DATA_FLOW_SPRITE = ResourceLocation.fromNamespaceAndPath(Data_Energistics.MODID, "block/key/data_flow");
    private static final ResourceLocation DATA_SPRITE = ResourceLocation.fromNamespaceAndPath(Data_Energistics.MODID, "block/key/data");

    private CustomKeyGuiRenderer() {}

    public static void draw(Minecraft minecraft, GuiGraphics guiGraphics, int x, int y, AEKey key) {
        if (!drawCustom(minecraft, guiGraphics, x, y, key)) {
            AEKeyRendering.drawInGui(minecraft, guiGraphics, x, y, key);
        }
    }

    public static boolean drawCustom(Minecraft minecraft, GuiGraphics guiGraphics, int x, int y, AEKey key) {
        if (key instanceof AEFluidKey fluidKey) {
            drawFluid(guiGraphics, x, y, fluidKey);
            return true;
        }
        if (key instanceof DataFlowKey) {
            drawSprite(guiGraphics, x, y, dataFlowSprite());
            return true;
        }
        if (key instanceof DataKey) {
            drawSprite(guiGraphics, x, y, dataSprite());
            return true;
        }
        return false;
    }

    public static TextureAtlasSprite dataFlowSprite() {
        return Minecraft.getInstance().getTextureAtlas(InventoryMenu.BLOCK_ATLAS).apply(DATA_FLOW_SPRITE);
    }

    public static TextureAtlasSprite dataSprite() {
        return Minecraft.getInstance().getTextureAtlas(InventoryMenu.BLOCK_ATLAS).apply(DATA_SPRITE);
    }

    private static void drawSprite(GuiGraphics guiGraphics, int x, int y, TextureAtlasSprite sprite) {
        Blitter.sprite(sprite)
                .dest(x, y, 16, 16)
                .blit(guiGraphics);
    }

    private static void drawFluid(GuiGraphics guiGraphics, int x, int y, AEFluidKey fluidKey) {
        FluidStack fluidStack = fluidKey.toStack(1);
        IClientFluidTypeExtensions fluidExtensions = IClientFluidTypeExtensions.of(fluidStack.getFluid());
        ResourceLocation stillTexture = fluidExtensions.getStillTexture(fluidStack);
        if (stillTexture == null) {
            AEKeyRendering.drawInGui(Minecraft.getInstance(), guiGraphics, x, y, fluidKey);
            return;
        }

        TextureAtlasSprite sprite = Minecraft.getInstance().getTextureAtlas(InventoryMenu.BLOCK_ATLAS).apply(stillTexture);
        int tint = fluidExtensions.getTintColor(fluidStack);
        Blitter.sprite(sprite)
                .dest(x, y, 16, 16)
                .colorArgb(tint == -1 ? 0xFFFFFFFF : tint)
                .blit(guiGraphics);
    }
}

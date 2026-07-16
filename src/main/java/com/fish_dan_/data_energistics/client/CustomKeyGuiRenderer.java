package com.fish_dan_.data_energistics.client;

import com.fish_dan_.data_energistics.Data_Energistics;
import com.fish_dan_.data_energistics.ae2.DataFlowKey;
import com.fish_dan_.data_energistics.ae2.DataKey;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.inventory.InventoryMenu;

import appeng.api.client.AEKeyRendering;
import appeng.api.stacks.AEKey;
import appeng.client.gui.style.Blitter;

public final class CustomKeyGuiRenderer {

    private static final ResourceLocation DATA_FLOW_SPRITE = ResourceLocation.fromNamespaceAndPath(Data_Energistics.MODID, "block/key/data_flow");
    private static final ResourceLocation DATA_SPRITE = ResourceLocation.fromNamespaceAndPath(Data_Energistics.MODID, "block/key/data");

    private CustomKeyGuiRenderer() {}

    public static void draw(Minecraft minecraft, GuiGraphics guiGraphics, int x, int y, AEKey key) {
        if (!drawCustom(guiGraphics, x, y, key)) {
            AEKeyRendering.drawInGui(minecraft, guiGraphics, x, y, key);
        }
    }

    public static boolean drawCustom(GuiGraphics guiGraphics, int x, int y, AEKey key) {
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
}

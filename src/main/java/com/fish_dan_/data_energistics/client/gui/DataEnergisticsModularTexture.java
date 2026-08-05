package com.fish_dan_.data_energistics.client.gui;

import com.fish_dan_.data_energistics.Data_Energistics;

import net.minecraft.client.gui.GuiGraphics;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

import com.lowdragmc.lowdraglib2.configurator.annotation.Configurable;
import com.lowdragmc.lowdraglib2.gui.texture.ColorBorderTexture;
import com.lowdragmc.lowdraglib2.gui.texture.ColorRectTexture;
import com.lowdragmc.lowdraglib2.gui.texture.GuiTextureGroup;
import com.lowdragmc.lowdraglib2.gui.texture.IGuiTexture;
import com.lowdragmc.lowdraglib2.gui.texture.TransformTexture;
import com.lowdragmc.lowdraglib2.registry.annotation.LDLRegisterClient;

/**
 * Editable Data Energistics texture composition exposed by the LDLib2 UI Editor.
 *
 * <p>
 * The nested texture intentionally uses the regular LDLib2 texture configurator, so the editor
 * can replace this default composition with a SpriteTexture or a GuiTextureGroup and persist it in
 * the normal texture resource format.
 * </p>
 */
@OnlyIn(Dist.CLIENT)
@LDLRegisterClient(
                   name = "data_energistics_modular_texture",
                   group = "data_energistics",
                   modID = Data_Energistics.MODID,
                   registry = "ldlib2:gui_texture")
public final class DataEnergisticsModularTexture extends TransformTexture {

    @Configurable(name = "texture", collapse = false)
    private IGuiTexture texture;

    /**
     * Creates the editor-instantiable default composition matching the Trinity host background.
     */
    public DataEnergisticsModularTexture() {
        this(GuiTextureGroup.of(
                new ColorRectTexture(0xFFE3E3EA),
                new ColorBorderTexture(-1, 0xFF696D88)));
    }

    private DataEnergisticsModularTexture(IGuiTexture texture) {
        this.texture = requireTexture(texture);
    }

    /**
     * Returns the nested composition edited by LDLib2's IGuiTexture configurator.
     *
     * @return current nested texture composition
     */
    public IGuiTexture getTexture() {
        return texture;
    }

    /**
     * Replaces the nested composition when the editor changes the texture accessor.
     *
     * @param texture new non-null LDLib2 texture composition
     */
    public void setTexture(IGuiTexture texture) {
        this.texture = requireTexture(texture);
    }

    @Override
    @OnlyIn(Dist.CLIENT)
    protected void drawInternal(
                                GuiGraphics graphics,
                                float mouseX,
                                float mouseY,
                                float x,
                                float y,
                                float width,
                                float height,
                                float partialTicks) {
        texture.draw(graphics, mouseX, mouseY, x, y, width, height, partialTicks);
    }

    @Override
    public DataEnergisticsModularTexture copy() {
        var copied = new DataEnergisticsModularTexture(texture.copy());
        copied.copyTransform(this);
        return copied;
    }

    @Override
    public DataEnergisticsModularTexture setColor(int color) {
        var copied = new DataEnergisticsModularTexture(texture.copy().setColor(color));
        copied.copyTransform(this);
        return copied;
    }

    private static IGuiTexture requireTexture(IGuiTexture texture) {
        if (texture == null) {
            Data_Energistics.LOGGER.error("Data Energistics modular texture cannot contain a null texture");
            throw new IllegalArgumentException("Data Energistics modular texture must not be null");
        }
        return texture;
    }
}

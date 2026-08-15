package com.fish_dan_.data_energistics.gui.ldlib2.trinity.layout;

import com.fish_dan_.data_energistics.Data_Energistics;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.fml.loading.FMLEnvironment;

import com.lowdragmc.lowdraglib2.Platform;
import com.lowdragmc.lowdraglib2.gui.ui.UI;
import com.lowdragmc.lowdraglib2.gui.ui.UIElement;
import com.lowdragmc.lowdraglib2.gui.ui.UITemplate;

import java.io.DataInputStream;
import java.io.FileNotFoundException;
import java.io.InputStream;

/**
 * Loads packaged LDLib2 UI templates from the mod JAR on both logical sides.
 */
public final class TrinityUiNbtLayouts {

    private static final String ROOT_PATH = "ui/trinity/";
    private static final ResourceLocation TRINITY_STYLESHEET = Data_Energistics.id("lss/trinity_ui");

    private TrinityUiNbtLayouts() {}

    /**
     * Loads one uncompressed editor-generated UI template and preserves its decoding failure.
     */
    public static UI load(String name) {
        return loadTemplate(name).createUI();
    }

    /**
     * Applies one editor-generated template to a lifecycle-owned root supplied by the hosted UI framework.
     */
    public static void init(String name, UIElement root) {
        loadTemplate(name).initUI(root);
    }

    private static UITemplate loadTemplate(String name) {
        if (name.isBlank()) {
            throw new IllegalArgumentException("Trinity NBT layout name must not be blank");
        }

        ResourceLocation location = Data_Energistics.id(ROOT_PATH + name + ".ui.nbt");
        String classpathLocation = "/assets/" + location.getNamespace() + "/" + location.getPath();
        try (InputStream stream = TrinityUiNbtLayouts.class.getResourceAsStream(classpathLocation)) {
            if (stream == null) {
                throw new FileNotFoundException(classpathLocation);
            }
            CompoundTag wrapper = NbtIo.read(new DataInputStream(stream));
            if (!"ui".equals(wrapper.getString("type"))) {
                throw new IllegalArgumentException("Expected LDLib2 resource type 'ui', found '" +
                        wrapper.getString("type") + "'");
            }
            if (!wrapper.contains("data", Tag.TAG_COMPOUND)) {
                throw new IllegalArgumentException("LDLib2 UI resource is missing its compound data payload");
            }

            UITemplate template = UITemplate.CODEC
                    .parse(
                            Platform.getFrozenRegistry().createSerializationContext(NbtOps.INSTANCE),
                            wrapper.getCompound("data"))
                    .getOrThrow();
            template.getStylesheets().clear();
            template.getStylesheets().add(TRINITY_STYLESHEET);
            return template;
        } catch (Exception failure) {
            String message = "Unable to load Trinity NBT layout '" + name + "' from " + location +
                    " (classpath " + classpathLocation + ", runtime " + runtimeSide() + ")";
            Data_Energistics.LOGGER.error(message, failure);
            throw new IllegalStateException(message, failure);
        }
    }

    private static String runtimeSide() {
        return FMLEnvironment.dist + "/" + Thread.currentThread().getName();
    }
}

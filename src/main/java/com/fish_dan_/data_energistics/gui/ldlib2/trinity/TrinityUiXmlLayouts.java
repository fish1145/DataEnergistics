package com.fish_dan_.data_energistics.gui.ldlib2.trinity;

import com.fish_dan_.data_energistics.Data_Energistics;

import net.minecraft.resources.ResourceLocation;
import net.neoforged.fml.loading.FMLEnvironment;

import com.lowdragmc.lowdraglib2.gui.ui.UI;
import com.lowdragmc.lowdraglib2.gui.ui.UIElement;
import org.jetbrains.annotations.NotNull;
import org.w3c.dom.Document;
import org.xml.sax.InputSource;

import java.io.FileNotFoundException;
import java.io.InputStream;

import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilderFactory;

/**
 * Loads the declarative XML trees used by the Trinity UI family.
 *
 * <p>
 * Coordinates, dimensions, and visual layout rules belong in the paired {@code lss/trinity_ui.lss}
 * stylesheet. Java callers only locate typed elements here to attach data sources and game actions.
 * </p>
 */
final class TrinityUiXmlLayouts {

    private static final String ROOT_PATH = "ui/trinity/";

    private TrinityUiXmlLayouts() {}

    /**
     * Loads one complete LDLib2 UI document and rejects malformed or missing resources immediately.
     */
    static UI load(@NotNull String name) {
        if (name.isBlank()) {
            throw new IllegalArgumentException("Trinity XML layout name must not be blank");
        }

        ResourceLocation location = Data_Energistics.id(ROOT_PATH + name + ".xml");
        String classpathLocation = "/assets/" + location.getNamespace() + "/" + location.getPath();
        try (InputStream stream = TrinityUiXmlLayouts.class.getResourceAsStream(classpathLocation)) {
            if (stream == null) {
                throw new FileNotFoundException(classpathLocation);
            }
            return UI.of(parse(stream, location));
        } catch (Exception failure) {
            String message = "Unable to load Trinity XML layout '" + name + "' from " + location +
                    " (classpath " + classpathLocation + ", runtime " + runtimeSide() + ")";
            Data_Energistics.LOGGER.error(message, failure);
            throw new IllegalStateException(message, failure);
        }
    }

    private static Document parse(InputStream stream, ResourceLocation location) throws Exception {
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
        factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_DTD, "");
        factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_SCHEMA, "");
        factory.setExpandEntityReferences(false);
        InputSource source = new InputSource(stream);
        source.setSystemId(location.toString());
        return factory.newDocumentBuilder().parse(source);
    }

    private static String runtimeSide() {
        return FMLEnvironment.dist + "/" + Thread.currentThread().getName();
    }

    /**
     * Loads a detached XML root for a child panel that is later composed into a runtime-owned element.
     */
    static UIElement loadRoot(String name) {
        return load(name).rootElement;
    }

    /**
     * Retrieves exactly one element by its stable layout id and validates its expected LDLib2 type.
     */
    static <T extends UIElement> T require(UIElement root, String id, Class<T> type) {
        if (root == null || id == null || id.isBlank() || type == null) {
            throw new IllegalArgumentException("Trinity XML element lookup arguments must not be null or blank");
        }
        T element = root.selectId(id, type).findFirst().orElse(null);
        if (element == null) {
            String message = "Trinity UI layout is missing " + type.getSimpleName() + " with id " + id;
            Data_Energistics.LOGGER.error(message);
            throw new IllegalStateException(message);
        }
        return element;
    }

    /**
     * Moves every XML child into a runtime subclass while preserving the parsed declarative tree.
     */
    static void moveChildren(UIElement source, UIElement target) {
        if (source == null || target == null) {
            throw new IllegalArgumentException("Trinity XML child transfer requires source and target");
        }
        for (UIElement child : source.getSafeChildren()) {
            if (!source.removeChild(child)) {
                throw new IllegalStateException("Trinity XML child disappeared during transfer: " + child.getId());
            }
            target.addChild(child);
        }
    }
}

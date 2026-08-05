package com.fish_dan_.data_energistics.gui.ldlib2.trinity;

import com.fish_dan_.data_energistics.Data_Energistics;

import com.lowdragmc.lowdraglib2.gui.ui.UI;
import com.lowdragmc.lowdraglib2.gui.ui.UIElement;
import com.lowdragmc.lowdraglib2.utils.XmlUtils;
import org.w3c.dom.Document;

/**
 * Loads the declarative XML trees used by the Trinity UI family.
 *
 * <p>Coordinates, dimensions, and visual layout rules belong in the paired {@code lss/trinity_ui.lss}
 * stylesheet. Java callers only locate typed elements here to attach data sources and game actions.</p>
 */
final class TrinityUiXmlLayouts {

    private static final String ROOT_PATH = "ui/trinity/";

    private TrinityUiXmlLayouts() {}

    /** Loads one complete LDLib2 UI document and rejects malformed or missing resources immediately. */
    static UI load(String name) {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("Trinity XML layout name must not be blank");
        }
        Document document = XmlUtils.loadXml(Data_Energistics.id(ROOT_PATH + name + ".xml"));
        if (document == null) {
            String message = "Unable to load Trinity XML layout " + name;
            Data_Energistics.LOGGER.error(message);
            throw new IllegalStateException(message);
        }
        return UI.of(document);
    }

    /** Loads a detached XML root for a child panel that is later composed into a runtime-owned element. */
    static UIElement loadRoot(String name) {
        return load(name).rootElement;
    }

    /** Retrieves exactly one element by its stable layout id and validates its expected LDLib2 type. */
    static <T extends UIElement> T require(UIElement root, String id, Class<T> type) {
        if (root == null || id == null || id.isBlank() || type == null) {
            throw new IllegalArgumentException("Trinity XML element lookup arguments must not be null or blank");
        }
        T element = root.selectId(id, type).findFirst().orElse(null);
        if (element == null) {
            String message = "Trinity XML layout is missing " + type.getSimpleName() + " with id " + id;
            Data_Energistics.LOGGER.error(message);
            throw new IllegalStateException(message);
        }
        return element;
    }

    /** Moves every XML child into a runtime subclass while preserving the parsed declarative tree. */
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

package com.fish_dan_.data_energistics.client.screen;

import com.fish_dan_.data_energistics.Data_Energistics;
import com.lowdragmc.lowdraglib2.utils.XmlUtils;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

/**
 * Loads the declarative native-screen geometry for the Trinity access hatch.
 *
 * <p>
 * The host screen is an AE2 native screen rather than an LDLib2 UI tree, so its widget bounds are supplied by this
 * XML model while the LDLib2 Trinity surfaces use LSS directly.
 * </p>
 */
final class TrinityAccessHatchLayout {

    private static final String RESOURCE_PATH = "ui/trinity/access_hatch.xml";
    private static final String ROOT_NAME = "trinity-access-hatch";

    private final int screenWidth;
    private final ManagementPanel managementPanel;
    private final Title title;
    private final Button searchModeButton;
    private final Button refundPatternsButton;
    private final Button refundRetainedItemsButton;

    private TrinityAccessHatchLayout(int screenWidth,
                                     ManagementPanel managementPanel,
                                     Title title,
                                     Button searchModeButton,
                                     Button refundPatternsButton,
                                     Button refundRetainedItemsButton) {
        this.screenWidth = screenWidth;
        this.managementPanel = managementPanel;
        this.title = title;
        this.searchModeButton = searchModeButton;
        this.refundPatternsButton = refundPatternsButton;
        this.refundRetainedItemsButton = refundRetainedItemsButton;
    }

    /** Loads one complete, validated access-hatch geometry model from the client resource pack. */
    static TrinityAccessHatchLayout load() {
        Document document = XmlUtils.loadXml(Data_Energistics.id(RESOURCE_PATH));
        if (document == null) {
            throw invalid("Unable to load " + RESOURCE_PATH);
        }
        Element root = document.getDocumentElement();
        if (root == null || !ROOT_NAME.equals(root.getTagName())) {
            throw invalid(RESOURCE_PATH + " must have <" + ROOT_NAME + "> as its root");
        }

        int screenWidth = requiredPositiveInt(root, "width");
        Element panelElement = requiredChild(root, "management-panel");
        ManagementPanel panel = new ManagementPanel(
                requiredNonNegativeInt(panelElement, "left"),
                requiredPositiveInt(panelElement, "width"));
        Element titleElement = requiredChild(panelElement, "title");
        Title title = new Title(
                requiredNonNegativeInt(titleElement, "left"),
                requiredNonNegativeInt(titleElement, "top"));

        Button searchModeButton = readButton(panelElement, "search-mode");
        Button refundPatternsButton = readButton(panelElement, "refund-patterns");
        Button refundRetainedItemsButton = readButton(panelElement, "refund-retained-items");
        return new TrinityAccessHatchLayout(
                screenWidth,
                panel,
                title,
                searchModeButton,
                refundPatternsButton,
                refundRetainedItemsButton);
    }

    int screenWidth() {
        return this.screenWidth;
    }

    ManagementPanel managementPanel() {
        return this.managementPanel;
    }

    Title title() {
        return this.title;
    }

    Button searchModeButton() {
        return this.searchModeButton;
    }

    Button refundPatternsButton() {
        return this.refundPatternsButton;
    }

    Button refundRetainedItemsButton() {
        return this.refundRetainedItemsButton;
    }

    private static Button readButton(Element panel, String id) {
        Element element = requiredButton(panel, id);
        return new Button(
                requiredNonNegativeInt(element, "left"),
                requiredNonNegativeInt(element, "top"),
                requiredPositiveInt(element, "width"),
                requiredPositiveInt(element, "height"),
                requiredNonNegativeInt(element, "text-left-padding"),
                requiredTextScale(element));
    }

    private static Element requiredChild(Element parent, String name) {
        NodeList children = parent.getChildNodes();
        for (int index = 0; index < children.getLength(); index++) {
            Node child = children.item(index);
            if (child instanceof Element element && name.equals(element.getTagName())) {
                return element;
            }
        }
        throw invalid("Missing <" + name + "> in " + RESOURCE_PATH);
    }

    private static Element requiredButton(Element parent, String id) {
        NodeList children = parent.getChildNodes();
        for (int index = 0; index < children.getLength(); index++) {
            Node child = children.item(index);
            if (child instanceof Element element && "button".equals(element.getTagName()) &&
                    id.equals(element.getAttribute("id"))) {
                return element;
            }
        }
        throw invalid("Missing <button id=\"" + id + "\"> in " + RESOURCE_PATH);
    }

    private static int requiredPositiveInt(Element element, String attribute) {
        int value = requiredInt(element, attribute);
        if (value <= 0) {
            throw invalid(attribute + " must be positive in " + RESOURCE_PATH);
        }
        return value;
    }

    private static int requiredNonNegativeInt(Element element, String attribute) {
        int value = requiredInt(element, attribute);
        if (value < 0) {
            throw invalid(attribute + " must not be negative in " + RESOURCE_PATH);
        }
        return value;
    }

    private static int requiredInt(Element element, String attribute) {
        String value = element.getAttribute(attribute);
        if (value.isBlank()) {
            throw invalid("Missing " + attribute + " in " + RESOURCE_PATH);
        }
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException exception) {
            throw invalid(attribute + " must be an integer in " + RESOURCE_PATH, exception);
        }
    }

    private static float requiredTextScale(Element element) {
        String value = element.getAttribute("text-scale");
        if (value.isBlank()) {
            throw invalid("Missing text-scale in " + RESOURCE_PATH);
        }
        try {
            float parsed = Float.parseFloat(value);
            if (parsed <= 0.0F) {
                throw invalid("text-scale must be positive in " + RESOURCE_PATH);
            }
            return parsed;
        } catch (NumberFormatException exception) {
            throw invalid("text-scale must be a decimal number in " + RESOURCE_PATH, exception);
        }
    }

    private static IllegalStateException invalid(String message) {
        Data_Energistics.LOGGER.error("Trinity access hatch XML layout is invalid: {}", message);
        return new IllegalStateException(message);
    }

    private static IllegalStateException invalid(String message, Exception cause) {
        Data_Energistics.LOGGER.error("Trinity access hatch XML layout is invalid: {}", message, cause);
        return new IllegalStateException(message, cause);
    }

    /** Geometry of the AE2-native right-hand management panel. */
    record ManagementPanel(int left, int width) {}

    /** Position of the management-panel title relative to the native screen. */
    record Title(int left, int top) {}

    /** Geometry and text metrics for one native management action. */
    record Button(int left, int top, int width, int height, int textLeftPadding, float textScale) {}
}

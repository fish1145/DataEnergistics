package com.fish_dan_.data_energistics.gui.ldlib2.crafting.tree;

import com.lowdragmc.lowdraglib2.gui.ui.UI;
import com.lowdragmc.lowdraglib2.gui.ui.UIElement;

import java.io.IOException;
import java.io.InputStream;

import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilderFactory;

/** Loads this page's own declarative layout, independent of AE2 screen styles and optional tree mods. */
public final class CraftingPlanTreeUi {
    private CraftingPlanTreeUi() {}

    public static UI load() {
        String path = "/assets/data_energistics/ui/crafting/plan_tree.xml";
        try (InputStream input = CraftingPlanTreeUi.class.getResourceAsStream(path)) {
            if (input == null) throw new IOException("Missing " + path);
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            factory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
            factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_DTD, "");
            factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_SCHEMA, "");
            factory.setExpandEntityReferences(false);
            return UI.of(factory.newDocumentBuilder().parse(input));
        } catch (Exception failure) {
            throw new IllegalStateException("Cannot load the crafting plan tree layout", failure);
        }
    }

    public static <T extends UIElement> T element(UI ui, String id, Class<T> type) {
        return ui.selectId(id, type).findFirst().orElseThrow(() -> new IllegalStateException("Missing plan-tree layout element " + id));
    }
}

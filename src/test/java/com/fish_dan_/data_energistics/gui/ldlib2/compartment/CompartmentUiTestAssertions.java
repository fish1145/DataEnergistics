package com.fish_dan_.data_energistics.gui.ldlib2.compartment;

import com.fish_dan_.data_energistics.common.compartment.CompartmentType;

import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.network.chat.contents.TranslatableContents;

import com.lowdragmc.lowdraglib2.gui.ui.ModularUI;
import com.lowdragmc.lowdraglib2.gui.ui.UIElement;
import com.lowdragmc.lowdraglib2.gui.ui.data.TextWrap;
import com.lowdragmc.lowdraglib2.gui.ui.elements.Label;
import com.lowdragmc.lowdraglib2.gui.ui.layout.LayoutProperties;
import com.lowdragmc.lowdraglib2.gui.ui.style.PropertyRegistry;
import org.appliedenergistics.yoga.YogaOverflow;

final class CompartmentUiTestAssertions {

    private CompartmentUiTestAssertions() {}

    static Label assertStyledTranslation(ModularUI modularUI, String id, String expectedKey) {
        Label label = requireLabel(modularUI, id);
        assertTranslation(label, expectedKey);
        Integer textColor = label.getTextStyle().getInline(PropertyRegistry.TEXT_COLOR);
        if (textColor == null || textColor != CompartmentHostUi.TITLE_COLOR) {
            throw new GameTestAssertException("Unexpected compartment label color for " + id);
        }
        if (label.getTextStyle().getInline(PropertyRegistry.TEXT_WRAP) != TextWrap.HOVER_ROLL) {
            throw new GameTestAssertException("Compartment label must use hover-roll wrapping: " + id);
        }
        if (label.getStyle().getInline(LayoutProperties.OVERFLOW) != YogaOverflow.HIDDEN) {
            throw new GameTestAssertException("Compartment label must clip hover-roll text to its bounds: " + id);
        }
        return label;
    }

    static void assertExplicitOverflowVisible(UIElement element) {
        if (element.getStyle().getInline(LayoutProperties.OVERFLOW) != YogaOverflow.VISIBLE) {
            throw new GameTestAssertException("Element must explicitly expose controlled overflow: " + element.getId());
        }
    }

    static void assertTranslationArgument(Label label, int index, int expected) {
        Object[] arguments = translation(label).getArgs();
        if (index < 0 || index >= arguments.length || !(arguments[index] instanceof Number number) ||
                number.intValue() != expected) {
            throw new GameTestAssertException(
                    "Unexpected translation argument " + index + " for " + label.getId());
        }
    }

    static void assertHeaderGeometry(CompartmentType type, int firstMachineElementTop, int rootWidth) {
        CompartmentHostUi.HeaderGeometry geometry = CompartmentHostUi.headerGeometry(type);
        if (geometry.titleWidth() <= 0 || geometry.statusWidth() <= 0) {
            throw new GameTestAssertException("Compartment header regions must have positive width");
        }
        if (geometry.top() + geometry.height() > firstMachineElementTop) {
            throw new GameTestAssertException("Compartment header overlaps the first machine element");
        }
        if (geometry.statusRight() > rootWidth) {
            throw new GameTestAssertException("Compartment header status exceeds its root width");
        }
    }

    static UIElement requireElement(ModularUI modularUI, String id) {
        UIElement element = modularUI.getElementById(id);
        if (element == null) {
            throw new GameTestAssertException("Missing LDLib2 element " + id);
        }
        return element;
    }

    private static Label requireLabel(ModularUI modularUI, String id) {
        UIElement element = requireElement(modularUI, id);
        if (element instanceof Label label) {
            return label;
        }
        throw new GameTestAssertException("LDLib2 element is not a label: " + id);
    }

    private static void assertTranslation(Label label, String expectedKey) {
        TranslatableContents contents = translation(label);
        if (!expectedKey.equals(contents.getKey())) {
            throw new GameTestAssertException(
                    "Expected translation " + expectedKey + ", got " + contents.getKey());
        }
    }

    private static TranslatableContents translation(Label label) {
        if (label.getText().getContents() instanceof TranslatableContents contents) {
            return contents;
        }
        throw new GameTestAssertException("Label does not contain a translatable component: " + label.getId());
    }
}

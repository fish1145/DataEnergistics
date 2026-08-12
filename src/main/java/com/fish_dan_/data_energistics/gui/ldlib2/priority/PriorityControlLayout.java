package com.fish_dan_.data_energistics.gui.ldlib2.priority;

import com.lowdragmc.lowdraglib2.gui.texture.IGuiTexture;
import com.lowdragmc.lowdraglib2.gui.ui.UIElement;
import com.lowdragmc.lowdraglib2.gui.ui.elements.Button;
import com.lowdragmc.lowdraglib2.gui.ui.elements.Label;
import com.lowdragmc.lowdraglib2.gui.ui.elements.TextField;

import java.util.List;

/**
 * Assigns the reusable priority contract to the editor-authored control tree.
 */
record PriorityControlLayout(
                             UIElement root,
                             Label title,
                             List<Button> increaseButtons,
                             TextField value,
                             List<Button> decreaseButtons,
                             Label insertHint,
                             Label extractHint,
                             Button close) {

    private static final int AUTHORED_CHILD_COUNT = 13;

    static PriorityControlLayout bind(UIElement root, String idPrefix) {
        List<UIElement> authoredChildren = root.getChildren().stream()
                .filter(child -> !child.isInternalUI())
                .toList();
        if (authoredChildren.size() != AUTHORED_CHILD_COUNT) {
            throw new IllegalStateException("Priority layout expected " + AUTHORED_CHILD_COUNT +
                    " authored children, found " + authoredChildren.size());
        }

        root.setId(idPrefix);
        root.addClass("priority-control-root");
        Label title = require(authoredChildren, 0, Label.class, "title");
        List<Button> increaseButtons = List.of(
                require(authoredChildren, 1, Button.class, "increase 1"),
                require(authoredChildren, 2, Button.class, "increase 10"),
                require(authoredChildren, 3, Button.class, "increase 100"),
                require(authoredChildren, 4, Button.class, "increase 1000"));
        TextField value = require(authoredChildren, 5, TextField.class, "value");
        List<Button> decreaseButtons = List.of(
                require(authoredChildren, 6, Button.class, "decrease 1"),
                require(authoredChildren, 7, Button.class, "decrease 10"),
                require(authoredChildren, 8, Button.class, "decrease 100"),
                require(authoredChildren, 9, Button.class, "decrease 1000"));
        Label insertHint = require(authoredChildren, 10, Label.class, "insert hint");
        Label extractHint = require(authoredChildren, 11, Label.class, "extract hint");
        Button close = require(authoredChildren, 12, Button.class, "close");

        title.setId(idPrefix + "_title");
        title.addClass("priority-control-title");
        title.setAllowHitTest(false);
        title.style(style -> style.backgroundTexture(IGuiTexture.EMPTY));
        value.setId(idPrefix + "_value");
        value.addClass("priority-control-value");
        insertHint.setId(idPrefix + "_insert_hint");
        insertHint.addClass("priority-control-hint");
        insertHint.setAllowHitTest(false);
        extractHint.setId(idPrefix + "_extract_hint");
        extractHint.addClass("priority-control-hint");
        extractHint.setAllowHitTest(false);
        close.setId(idPrefix + "_close");

        for (int index = 0; index < PriorityControl.Step.values().length; index++) {
            PriorityControl.Step step = PriorityControl.Step.fromIndex(index);
            configureStepButton(increaseButtons.get(index), idPrefix, "increase", step);
            configureStepButton(decreaseButtons.get(index), idPrefix, "decrease", step);
        }
        return new PriorityControlLayout(
                root,
                title,
                increaseButtons,
                value,
                decreaseButtons,
                insertHint,
                extractHint,
                close);
    }

    private static void configureStepButton(Button button, String idPrefix, String direction, PriorityControl.Step step) {
        button.setId(idPrefix + "_" + direction + "_" + step.amount());
        button.addClass("priority-control-step");
        button.enableText();
        button.text.addClass("priority-control-step-text");
    }

    private static <T extends UIElement> T require(
                                                   List<UIElement> children,
                                                   int index,
                                                   Class<T> expectedType,
                                                   String role) {
        UIElement child = children.get(index);
        if (!expectedType.isInstance(child)) {
            throw new IllegalStateException("Priority layout " + role + " must be " + expectedType.getSimpleName() +
                    ", found " + child.getClass().getSimpleName());
        }
        return expectedType.cast(child);
    }
}

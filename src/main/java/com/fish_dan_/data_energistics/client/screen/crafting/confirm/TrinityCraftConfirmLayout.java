package com.fish_dan_.data_energistics.client.screen.crafting.confirm;

import com.lowdragmc.lowdraglib2.gui.ui.UIElement;
import com.lowdragmc.lowdraglib2.gui.ui.elements.Button;
import com.lowdragmc.lowdraglib2.gui.ui.elements.Label;
import com.lowdragmc.lowdraglib2.gui.ui.elements.Scroller;

import java.util.List;

/** Strict runtime binding for the editor-authored Trinity crafting confirmation layout. */
final class TrinityCraftConfirmLayout {

    private static final int ROOT_CHILD_COUNT = 10;
    private static final float TOP_LABEL_SHIFT = 1.0F;
    private static final float BOTTOM_LABEL_SHIFT = 2.0F;

    private TrinityCraftConfirmLayout() {}

    static Layout require(UIElement root) {
        List<UIElement> children = root.getChildren();
        if (children.size() != ROOT_CHILD_COUNT) {
            throw new IllegalStateException("Trinity crafting confirmation layout expected " + ROOT_CHILD_COUNT +
                    " authored root children, found " + children.size());
        }
        root.setId("trinity_craft_confirm_root");
        return new Layout(
                identify(child(children, 0, Button.class, "CPU selector"), "trinity_craft_confirm_cpu"),
                identify(child(children, 1, Button.class, "cancel action"), "trinity_craft_confirm_cancel"),
                identify(child(children, 2, Button.class, "start action"), "trinity_craft_confirm_start"),
                identify(child(children, 3, Button.class, "plan-tree action"), "trinity_craft_confirm_tree"),
                identifyTopLabel(
                        child(children, 4, Label.class, "plan heading"),
                        "trinity_craft_confirm_heading",
                        5),
                identifyTopLabel(
                        child(children, 5, Label.class, "plan metrics"),
                        "trinity_craft_confirm_metrics",
                        11),
                identify(child(children, 6, Scroller.Vertical.class, "material scrollbar"),
                        "trinity_craft_confirm_scrollbar"),
                identifyBottomLabel(
                        child(children, 7, Label.class, "planning status"),
                        "trinity_craft_confirm_status",
                        51),
                identifyBottomLabel(
                        child(children, 8, Label.class, "CPU statistics"),
                        "trinity_craft_confirm_cpu_stats",
                        40),
                identifyBottomLabel(
                        child(children, 9, Label.class, "diagnostic"),
                        "trinity_craft_confirm_diagnostic",
                        28));
    }

    private static <T extends UIElement> T child(List<UIElement> children,
                                                 int index,
                                                 Class<T> type,
                                                 String role) {
        UIElement child = children.get(index);
        if (!type.isInstance(child)) {
            throw new IllegalStateException("Trinity crafting confirmation layout " + role + " has type " +
                    child.getClass().getName() + ", expected " + type.getName());
        }
        return type.cast(child);
    }

    private static <T extends UIElement> T identify(T element, String id) {
        element.setId(id);
        return element;
    }

    private static Label identifyBottomLabel(Label label, String id, float authoredBottom) {
        identify(label, id);
        label.layout(layout -> layout.bottom(authoredBottom + BOTTOM_LABEL_SHIFT));
        return label;
    }

    private static Label identifyTopLabel(Label label, String id, float authoredTop) {
        identify(label, id);
        label.layout(layout -> layout.top(authoredTop + TOP_LABEL_SHIFT));
        return label;
    }

    record Layout(Button cpu,
                  Button cancel,
                  Button start,
                  Button tree,
                  Label heading,
                  Label metrics,
                  Scroller.Vertical scrollbar,
                  Label status,
                  Label cpuStats,
                  Label diagnostic) {}
}

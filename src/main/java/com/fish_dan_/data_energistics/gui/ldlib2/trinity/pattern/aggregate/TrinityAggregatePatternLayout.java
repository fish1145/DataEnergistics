package com.fish_dan_.data_energistics.gui.ldlib2.trinity.pattern.aggregate;

import com.lowdragmc.lowdraglib2.gui.ui.UIElement;
import com.lowdragmc.lowdraglib2.gui.ui.elements.Button;
import com.lowdragmc.lowdraglib2.gui.ui.elements.Scroller;
import com.lowdragmc.lowdraglib2.gui.ui.elements.TextField;

import java.util.List;

/**
 * Assigns stable business identities to the single verified child order authored in {@code pattern.ui.nbt}.
 */
final class TrinityAggregatePatternLayout {

    static final String WINDOW_ID = "trinity_pattern_hosted_window";
    static final String CONTENT_ID = WINDOW_ID + "_content";
    static final String SCROLLER_ID = WINDOW_ID + "_scrollbar";
    static final String CLOSE_ID = WINDOW_ID + "_close";
    static final String SEARCH_ID = WINDOW_ID + "_search";
    static final String SEARCH_MODE_ID = WINDOW_ID + "_search_mode";
    static final String PRIORITY_ID = WINDOW_ID + "_priority";
    static final String REFUND_PATTERNS_ID = WINDOW_ID + "_refund_patterns";
    static final String MIGRATE_ID = WINDOW_ID + "_migrate";
    static final String REFUND_RETAINED_ID = WINDOW_ID + "_refund_retained";
    static final String TITLE_ID = WINDOW_ID + "_title";
    static final String MAINTENANCE_ID = WINDOW_ID + "_maintenance";

    private static final int AUTHORED_CHILD_COUNT = 8;

    private TrinityAggregatePatternLayout() {}

    static Controls bind(UIElement root) {
        List<UIElement> children = root.getChildren();
        if (children.size() != AUTHORED_CHILD_COUNT) {
            throw new IllegalStateException("Pattern layout expected " + AUTHORED_CHILD_COUNT +
                    " authored root children, found " + children.size());
        }

        root.setId(WINDOW_ID);
        UIElement content = child(children, 0, UIElement.class, "content");
        content.setId(CONTENT_ID);
        Scroller.Vertical scrollbar = child(content.getChildren(), 0, Scroller.Vertical.class, "scrollbar");
        scrollbar.setId(SCROLLER_ID);
        Button close = child(children, 1, Button.class, "close");
        close.setId(CLOSE_ID);
        TextField search = child(children, 2, TextField.class, "search");
        search.setId(SEARCH_ID);
        Button searchMode = child(children, 3, Button.class, "search mode");
        searchMode.setId(SEARCH_MODE_ID);
        Button priority = wrappedButton(children, 4, PRIORITY_ID, "priority");
        Button refundPatterns = wrappedButton(children, 5, REFUND_PATTERNS_ID, "refund patterns");
        Button migrate = wrappedButton(children, 6, MIGRATE_ID, "migrate");
        Button refundRetained = wrappedButton(children, 7, REFUND_RETAINED_ID, "refund retained materials");
        return new Controls(
                content,
                scrollbar,
                close,
                search,
                searchMode,
                priority,
                refundPatterns,
                migrate,
                refundRetained);
    }

    private static Button wrappedButton(List<UIElement> rootChildren, int index, String id, String role) {
        UIElement wrapper = child(rootChildren, index, UIElement.class, role + " wrapper");
        if (wrapper.getChildren().size() != 1) {
            throw new IllegalStateException("Pattern layout " + role + " wrapper must contain exactly one child");
        }
        Button button = child(wrapper.getChildren(), 0, Button.class, role);
        button.setId(id);
        return button;
    }

    private static <T extends UIElement> T child(List<UIElement> children,
                                                 int index,
                                                 Class<T> type,
                                                 String role) {
        UIElement child = children.get(index);
        if (!type.isInstance(child)) {
            throw new IllegalStateException("Pattern layout " + role + " has type " +
                    child.getClass().getName() + ", expected " + type.getName());
        }
        return type.cast(child);
    }

    record Controls(UIElement content,
                    Scroller.Vertical scrollbar,
                    Button close,
                    TextField search,
                    Button searchMode,
                    Button priority,
                    Button refundPatterns,
                    Button migrate,
                    Button refundRetained) {}
}

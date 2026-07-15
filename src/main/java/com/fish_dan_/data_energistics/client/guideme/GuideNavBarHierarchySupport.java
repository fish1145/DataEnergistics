package com.fish_dan_.data_energistics.client.guideme;

import guideme.document.LytRect;
import guideme.document.block.LytParagraph;
import guideme.internal.screen.GuideNavBar;
import guideme.layout.LayoutContext;
import guideme.layout.MinecraftFontMetrics;
import guideme.navigation.NavigationNode;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.util.List;

public final class GuideNavBarHierarchySupport {

    private static final int ROW_INDENT = 10;
    private static final int CHILD_ROW_INDENT = 10;
    private static final int PARENT_ROW_INDENT = 7;

    private static final Constructor<?> ROW_CONSTRUCTOR;
    private static final Field ROW_PARENT;
    private static final Field ROW_EXPANDED;
    private static final Field ROW_HAS_CHILDREN;
    private static final Field ROW_PARAGRAPH;
    private static final Field ROW_NODE;
    private static final Field ROW_TOP;
    private static final Field ROW_BOTTOM;

    static {
        try {
            Class<?> rowClass = Class.forName("guideme.internal.screen.GuideNavBar$Row");
            ROW_CONSTRUCTOR = rowClass.getDeclaredConstructor(GuideNavBar.class, NavigationNode.class, rowClass);
            ROW_PARENT = rowClass.getDeclaredField("parent");
            ROW_EXPANDED = rowClass.getDeclaredField("expanded");
            ROW_HAS_CHILDREN = rowClass.getDeclaredField("hasChildren");
            ROW_PARAGRAPH = rowClass.getDeclaredField("paragraph");
            ROW_NODE = rowClass.getDeclaredField("node");
            ROW_TOP = rowClass.getDeclaredField("top");
            ROW_BOTTOM = rowClass.getDeclaredField("bottom");

            ROW_CONSTRUCTOR.setAccessible(true);
            ROW_PARENT.setAccessible(true);
            ROW_EXPANDED.setAccessible(true);
            ROW_HAS_CHILDREN.setAccessible(true);
            ROW_PARAGRAPH.setAccessible(true);
            ROW_NODE.setAccessible(true);
            ROW_TOP.setAccessible(true);
            ROW_BOTTOM.setAccessible(true);
        } catch (ReflectiveOperationException e) {
            throw new ExceptionInInitializerError(e);
        }
    }

    private GuideNavBarHierarchySupport() {}

    public static void populateRows(GuideNavBar navBar, List<NavigationNode> rootNodes, List<Object> rows) {
        for (NavigationNode rootNode : rootNodes) {
            addRow(navBar, rootNode, null, rows);
        }
    }

    public static void layoutRows(List<Object> rows) {
        LayoutContext context = new LayoutContext(new MinecraftFontMetrics());
        int currentY = 0;

        for (Object row : rows) {
            if (!isVisible(row)) {
                continue;
            }

            int depth = getDepth(row);
            int indent = depth * ROW_INDENT;
            if (getBoolean(ROW_HAS_CHILDREN, row)) {
                indent += PARENT_ROW_INDENT;
            } else if (depth > 0) {
                indent += CHILD_ROW_INDENT;
            }

            NavigationNode node = get(ROW_NODE, row, NavigationNode.class);
            if (!node.icon().isEmpty()) {
                indent += 8;
            }

            LytParagraph paragraph = get(ROW_PARAGRAPH, row, LytParagraph.class);
            LytRect bounds = paragraph.layout(context, indent, currentY, GuideNavBar.WIDTH_OPEN - indent);
            setInt(ROW_TOP, row, bounds.y());
            setInt(ROW_BOTTOM, row, bounds.bottom());
            currentY = bounds.bottom();
        }
    }

    public static boolean isVisible(Object row) {
        Object parent = get(ROW_PARENT, row, Object.class);
        while (parent != null) {
            if (!getBoolean(ROW_EXPANDED, parent)) {
                return false;
            }
            parent = get(ROW_PARENT, parent, Object.class);
        }
        return true;
    }

    public static LytRect indentBounds(Object row, LytRect bounds) {
        int x = getDepth(row) * ROW_INDENT;
        if (x == 0) {
            return bounds;
        }
        return new LytRect(x, bounds.y(), Math.max(0, bounds.width() - x), bounds.height());
    }

    private static Object addRow(GuideNavBar navBar, NavigationNode node, Object parent, List<Object> rows) {
        Object row = newRow(navBar, node, parent);
        rows.add(row);

        List<NavigationNode> children = node.children();
        setBoolean(ROW_HAS_CHILDREN, row, !children.isEmpty());
        for (NavigationNode child : children) {
            addRow(navBar, child, row, rows);
        }
        return row;
    }

    private static Object newRow(GuideNavBar navBar, NavigationNode node, Object parent) {
        try {
            return ROW_CONSTRUCTOR.newInstance(navBar, node, parent);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("Failed to create a GuideME navigation row", e);
        }
    }

    private static int getDepth(Object row) {
        int depth = 0;
        Object parent = get(ROW_PARENT, row, Object.class);
        while (parent != null) {
            depth++;
            parent = get(ROW_PARENT, parent, Object.class);
        }
        return depth;
    }

    private static <T> T get(Field field, Object owner, Class<T> type) {
        try {
            return type.cast(field.get(owner));
        } catch (IllegalAccessException e) {
            throw new IllegalStateException("Failed to read GuideME navigation state", e);
        }
    }

    private static boolean getBoolean(Field field, Object owner) {
        try {
            return field.getBoolean(owner);
        } catch (IllegalAccessException e) {
            throw new IllegalStateException("Failed to read GuideME navigation state", e);
        }
    }

    private static void setBoolean(Field field, Object owner, boolean value) {
        try {
            field.setBoolean(owner, value);
        } catch (IllegalAccessException e) {
            throw new IllegalStateException("Failed to update GuideME navigation state", e);
        }
    }

    private static void setInt(Field field, Object owner, int value) {
        try {
            field.setInt(owner, value);
        } catch (IllegalAccessException e) {
            throw new IllegalStateException("Failed to update GuideME navigation layout", e);
        }
    }
}

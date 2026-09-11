package com.fish_dan_.data_energistics.client.crafting;

import appeng.client.gui.widgets.NumberEntryWidget;

import it.unimi.dsi.fastutil.objects.Reference2BooleanOpenHashMap;

/** Tracks the AE2 amount widgets that should use long-expression validation. */
public final class NumberEntryWidgetValidationRegistry {

    private static final Reference2BooleanOpenHashMap<NumberEntryWidget> ENABLED =
            new Reference2BooleanOpenHashMap<>();

    private NumberEntryWidgetValidationRegistry() {}

    public static synchronized void enable(NumberEntryWidget widget) {
        ENABLED.put(widget, true);
    }

    public static synchronized boolean isEnabled(NumberEntryWidget widget) {
        return ENABLED.containsKey(widget);
    }
}

package com.fish_dan_.data_energistics.gui.ldlib2.multiblock.autobuild;

import net.minecraft.network.chat.Component;

import com.lowdragmc.lowdraglib2.gui.ui.elements.Button;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import it.unimi.dsi.fastutil.objects.ObjectList;
import it.unimi.dsi.fastutil.objects.ObjectLists;
import org.jspecify.annotations.Nullable;

import java.util.List;
import java.util.function.UnaryOperator;

/**
 * Owns the ordered adjustment-context cursor and delegates value changes to each registered entry.
 */
final class AutoBuildAdjustmentRail {

    private final AutoBuildComposition.AdjustmentControls controls;
    private ObjectList<AutoBuildComposition.Adjustment> adjustments = ObjectLists.emptyList();
    private int adjustmentIndex;
    @Nullable
    private Component tooltipContext;
    @Nullable
    private UnaryOperator<Component> previousTooltip;
    @Nullable
    private UnaryOperator<Component> nextTooltip;

    AutoBuildAdjustmentRail(AutoBuildComposition.AdjustmentControls controls) {
        this.controls = controls;
        configureButton(this.controls.previousContext(), () -> selectRelative(-1));
        configureButton(this.controls.nextContext(), () -> selectRelative(1));
        configureButton(this.controls.previousValue(), () -> adjustValue(false));
        configureButton(this.controls.nextValue(), () -> adjustValue(true));
    }

    void setAdjustments(List<AutoBuildComposition.Adjustment> adjustments, @Nullable String retainedStableKey) {
        this.adjustments = ObjectLists.unmodifiable(new ObjectArrayList<>(adjustments));
        this.adjustmentIndex = indexOf(retainedStableKey);
        refresh();
    }

    void setTooltipFactories(Component context,
                             UnaryOperator<Component> previous,
                             UnaryOperator<Component> next) {
        this.tooltipContext = context;
        this.previousTooltip = previous;
        this.nextTooltip = next;
        refreshTooltips();
    }

    @Nullable
    String activeStableKey() {
        return this.adjustments.isEmpty() ? null : this.adjustments.get(this.adjustmentIndex).stableKey();
    }

    private void selectRelative(int direction) {
        if (this.adjustments.size() < 2) {
            return;
        }
        this.adjustmentIndex = Math.floorMod(this.adjustmentIndex + direction, this.adjustments.size());
        refresh();
    }

    private void adjustValue(boolean next) {
        if (this.adjustments.isEmpty()) {
            return;
        }
        AutoBuildComposition.Adjustment active = this.adjustments.get(this.adjustmentIndex);
        if (!active.adjustable().getAsBoolean()) {
            return;
        }
        if (next) {
            active.next().run();
        } else {
            active.previous().run();
        }
    }

    private void refresh() {
        boolean multiple = this.adjustments.size() > 1;
        this.controls.previousContext().setActive(multiple);
        this.controls.nextContext().setActive(multiple);
        if (this.adjustments.isEmpty()) {
            this.controls.contextValue().setText(Component.empty());
            this.controls.valueValue().setText(Component.empty());
            this.controls.previousValue().setActive(false);
            this.controls.nextValue().setActive(false);
            refreshTooltips();
            return;
        }

        AutoBuildComposition.Adjustment active = this.adjustments.get(this.adjustmentIndex);
        this.controls.contextValue().setText(active.label().get());
        this.controls.valueValue().setText(active.value().get());
        boolean adjustable = active.adjustable().getAsBoolean();
        this.controls.previousValue().setActive(adjustable);
        this.controls.nextValue().setActive(adjustable);
        refreshTooltips();
    }

    private void refreshTooltips() {
        if (this.tooltipContext == null || this.previousTooltip == null || this.nextTooltip == null) {
            return;
        }
        AutoBuildComposition.setTooltip(
                this.controls.previousContext(),
                this.previousTooltip.apply(this.tooltipContext));
        AutoBuildComposition.setTooltip(
                this.controls.nextContext(),
                this.nextTooltip.apply(this.tooltipContext));
        if (this.adjustments.isEmpty()) {
            return;
        }
        Component label = this.adjustments.get(this.adjustmentIndex).label().get();
        AutoBuildComposition.setTooltip(this.controls.previousValue(), this.previousTooltip.apply(label));
        AutoBuildComposition.setTooltip(this.controls.nextValue(), this.nextTooltip.apply(label));
    }

    private int indexOf(@Nullable String stableKey) {
        if (stableKey != null) {
            for (int index = 0; index < this.adjustments.size(); index++) {
                if (stableKey.equals(this.adjustments.get(index).stableKey())) {
                    return index;
                }
            }
        }
        return 0;
    }

    private static void configureButton(Button button, Runnable action) {
        button.setText(Component.empty());
        button.setOnClick(event -> action.run());
    }
}

package com.fish_dan_.data_energistics.gui.ldlib2.trinity.pattern.aggregate;

import com.fish_dan_.data_energistics.common.trinity.pattern.TrinityPatternMaintenanceSnapshot;

import net.minecraft.network.chat.Component;

import com.lowdragmc.lowdraglib2.gui.texture.ColorRectTexture;
import com.lowdragmc.lowdraglib2.gui.ui.UIElement;
import com.lowdragmc.lowdraglib2.gui.ui.elements.BindableUIElement;
import dev.vfyjxf.taffy.style.TaffyPosition;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.function.Consumer;

/** Displays capacity or server-authoritative maintenance progress without changing the authored window geometry. */
final class TrinityPatternMaintenanceBar extends BindableUIElement<TrinityPatternMaintenanceSnapshot> {

    private static final int HEIGHT = 154;
    private static final int CAPACITY_COLOR = 0xFF25C8C8;
    private static final int MIGRATION_COLOR = 0xFF4D8DFF;
    private static final int REFUND_COLOR = 0xFFF0A020;
    private static final int COMPLETED_COLOR = 0xFF45C46A;
    private static final int FAILED_COLOR = 0xFFE05252;
    private static final int BACKGROUND_COLOR = 0xFF34384B;

    private final UIElement fill = new UIElement();
    private final Consumer<Boolean> maintenanceState;
    private TrinityPatternMaintenanceSnapshot value = TrinityPatternMaintenanceSnapshot.idle(0, 0);

    TrinityPatternMaintenanceBar(String id, Consumer<Boolean> maintenanceState) {
        this.maintenanceState = maintenanceState;
        setId(id);
        style(style -> style.backgroundTexture(new ColorRectTexture(BACKGROUND_COLOR)));
        layout(layout -> layout
                .positionType(TaffyPosition.ABSOLUTE)
                .left(1)
                .top(15)
                .width(4)
                .height(HEIGHT));

        this.fill.setId(id + "_fill");
        this.fill.setAllowHitTest(false);
        addChild(this.fill);
        apply(this.value);
        internalSetup();
    }

    @Override
    public TrinityPatternMaintenanceSnapshot getValue() {
        return this.value;
    }

    @Override
    public TrinityPatternMaintenanceBar setValue(@Nullable TrinityPatternMaintenanceSnapshot value, boolean notify) {
        TrinityPatternMaintenanceSnapshot next = value == null ? TrinityPatternMaintenanceSnapshot.idle(0, 0) : value;
        if (this.value.equals(next)) {
            return this;
        }
        this.value = next;
        apply(next);
        if (notify) {
            notifyListeners();
        }
        return this;
    }

    @Override
    protected void onRemoved() {
        for (var dataSource : List.copyOf(getBoundDataSources())) {
            unbindDataSource(dataSource);
        }
        super.onRemoved();
    }

    private void apply(TrinityPatternMaintenanceSnapshot snapshot) {
        int height = Math.round(snapshot.progress() * HEIGHT);
        this.fill.layout(layout -> layout
                .positionType(TaffyPosition.ABSOLUTE)
                .left(0)
                .top(HEIGHT - height)
                .width(4)
                .height(height));
        this.fill.style(style -> style.backgroundTexture(new ColorRectTexture(color(snapshot))));
        style(style -> style.tooltips(tooltip(snapshot)));
        this.maintenanceState.accept(snapshot.active());
    }

    private static int color(TrinityPatternMaintenanceSnapshot snapshot) {
        return switch (snapshot.stage()) {
            case COMPLETED -> COMPLETED_COLOR;
            case FAILED, CANCELLED -> FAILED_COLOR;
            default -> switch (snapshot.operation()) {
                case IDLE -> CAPACITY_COLOR;
                case MIGRATION -> MIGRATION_COLOR;
                case REFUND_PATTERNS -> REFUND_COLOR;
            };
        };
    }

    private static Component tooltip(TrinityPatternMaintenanceSnapshot snapshot) {
        if (snapshot.operation() == TrinityPatternMaintenanceSnapshot.Operation.IDLE) {
            return Component.translatable(
                    "tooltip.data_energistics.trinity_data_core.pattern.capacity",
                    snapshot.installedPatterns(),
                    snapshot.patternCapacity());
        }
        if (snapshot.stage().terminal()) {
            return Component.translatable(
                    "tooltip.data_energistics.trinity_data_core.pattern.maintenance_result",
                    operationName(snapshot.operation()),
                    stageName(snapshot.stage()),
                    snapshot.succeededUnits(),
                    snapshot.failedUnits());
        }
        return Component.translatable(
                "tooltip.data_energistics.trinity_data_core.pattern.maintenance_progress",
                operationName(snapshot.operation()),
                stageName(snapshot.stage()),
                snapshot.completedUnits(),
                snapshot.totalUnits());
    }

    private static Component operationName(TrinityPatternMaintenanceSnapshot.Operation operation) {
        return Component.translatable("tooltip.data_energistics.trinity_data_core.pattern.operation." +
                operation.name().toLowerCase());
    }

    private static Component stageName(TrinityPatternMaintenanceSnapshot.Stage stage) {
        return Component.translatable("tooltip.data_energistics.trinity_data_core.pattern.stage." +
                stage.name().toLowerCase());
    }
}

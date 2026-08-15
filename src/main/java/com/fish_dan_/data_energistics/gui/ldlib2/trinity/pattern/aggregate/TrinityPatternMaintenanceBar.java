package com.fish_dan_.data_energistics.gui.ldlib2.trinity.pattern.aggregate;

import com.fish_dan_.data_energistics.common.trinity.pattern.TrinityPatternMaintenanceSnapshot;
import com.fish_dan_.data_energistics.gui.ldlib2.trinity.progress.TrinityPatternProgressAppearance;
import com.fish_dan_.data_energistics.gui.ldlib2.trinity.progress.TrinityPatternProgressBar;

import net.minecraft.network.chat.Component;

import com.lowdragmc.lowdraglib2.gui.ui.elements.BindableUIElement;
import dev.vfyjxf.taffy.style.TaffyPosition;
import org.jspecify.annotations.Nullable;

import java.util.List;
import java.util.function.Consumer;

/** Displays capacity or server-authoritative maintenance progress without changing the authored window geometry. */
final class TrinityPatternMaintenanceBar extends BindableUIElement<TrinityPatternMaintenanceSnapshot> {

    private static final int WIDTH = 10;
    private static final int HEIGHT = 154;

    private final TrinityPatternProgressBar progressBar;
    private final Consumer<Boolean> maintenanceState;
    private TrinityPatternMaintenanceSnapshot value = TrinityPatternMaintenanceSnapshot.idle(0, 0);

    TrinityPatternMaintenanceBar(String id, Consumer<Boolean> maintenanceState) {
        this.maintenanceState = maintenanceState;
        setId(id);
        layout(layout -> layout
                .positionType(TaffyPosition.ABSOLUTE)
                .left(1)
                .top(15)
                .width(WIDTH)
                .height(HEIGHT));

        this.progressBar = TrinityPatternProgressBar.vertical(id + "_texture");
        this.progressBar.layout(layout -> layout
                .positionType(TaffyPosition.ABSOLUTE)
                .left(0)
                .top(0));
        addChild(this.progressBar);
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
        this.progressBar.setProgress(snapshot.progress(), appearance(snapshot));
        style(style -> style.tooltips(tooltip(snapshot)));
        this.maintenanceState.accept(snapshot.active());
    }

    private static TrinityPatternProgressAppearance appearance(TrinityPatternMaintenanceSnapshot snapshot) {
        return switch (snapshot.stage()) {
            case COMPLETED -> TrinityPatternProgressAppearance.COMPLETED;
            case FAILED, CANCELLED -> TrinityPatternProgressAppearance.FAILED;
            default -> switch (snapshot.operation()) {
                case IDLE -> TrinityPatternProgressAppearance.CAPACITY;
                case MIGRATION -> TrinityPatternProgressAppearance.MIGRATION;
                case REFUND_PATTERNS -> TrinityPatternProgressAppearance.REFUND;
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

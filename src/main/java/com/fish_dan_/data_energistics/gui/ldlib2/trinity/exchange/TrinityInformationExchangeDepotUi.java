package com.fish_dan_.data_energistics.gui.ldlib2.trinity.exchange;

import com.fish_dan_.data_energistics.Data_Energistics;
import com.fish_dan_.data_energistics.blockentity.trinity.TrinityInformationExchangeDepotBlockEntity.StorageMode;
import com.fish_dan_.data_energistics.common.trinity.pattern.TrinityPatternMaintenanceSnapshot.Operation;
import com.fish_dan_.data_energistics.common.trinity.pattern.TrinityPatternMaintenanceSnapshot.Stage;
import com.fish_dan_.data_energistics.gui.ldlib2.ae.bridge.AeMenuBridge;
import com.fish_dan_.data_energistics.gui.ldlib2.trinity.layout.TrinityUiNbtLayouts;
import com.fish_dan_.data_energistics.gui.ldlib2.trinity.progress.TrinityPatternProgressAppearance;
import com.fish_dan_.data_energistics.gui.ldlib2.trinity.progress.TrinityPatternProgressBar;
import com.fish_dan_.data_energistics.menu.trinity.TrinityInformationExchangeDepotMenu;

import com.lowdragmc.lowdraglib2.gui.texture.IGuiTexture;
import com.lowdragmc.lowdraglib2.gui.ui.ModularUI;
import com.lowdragmc.lowdraglib2.gui.ui.UI;
import com.lowdragmc.lowdraglib2.gui.ui.UIElement;
import com.lowdragmc.lowdraglib2.gui.ui.elements.Button;
import com.lowdragmc.lowdraglib2.gui.ui.elements.Label;
import com.lowdragmc.lowdraglib2.gui.ui.elements.Toggle;
import com.lowdragmc.lowdraglib2.gui.ui.elements.ToggleGroupElement;
import com.lowdragmc.lowdraglib2.gui.ui.event.UIEvents;
import com.lowdragmc.lowdraglib2.gui.util.WindowDragHelper;

import net.minecraft.network.chat.Component;

import dev.vfyjxf.taffy.style.TaffyPosition;

import java.util.List;
import java.util.Locale;

/** Binds the authored information-exchange-depot NBT to its server-authoritative storage mode. */
public final class TrinityInformationExchangeDepotUi {

    private static final String ROOT_ID = "trinity_information_exchange_depot_root";
    private static final String CONTENT_ID = "trinity_information_exchange_depot_content";
    private static final String MODE_GROUP_ID = "trinity_information_exchange_depot_mode_group";
    private static final String TITLE_ID = "trinity_information_exchange_depot_title";
    private static final String CLOSE_ID = "trinity_information_exchange_depot_close";
    private static final String MODE_TITLE_ID = "trinity_information_exchange_depot_mode_title";
    private static final String INPUT_MODE_ID = "trinity_information_exchange_depot_mode_input";
    private static final String STORAGE_MODE_ID = "trinity_information_exchange_depot_mode_storage";
    private static final String OUTPUT_MODE_ID = "trinity_information_exchange_depot_mode_output";
    private static final String INPUT_MODE_LABEL_ID = INPUT_MODE_ID + "_label";
    private static final String STORAGE_MODE_LABEL_ID = STORAGE_MODE_ID + "_label";
    private static final String OUTPUT_MODE_LABEL_ID = OUTPUT_MODE_ID + "_label";
    private static final String MIGRATION_ID = "trinity_information_exchange_depot_migration";
    private static final String MIGRATION_TITLE_ID = MIGRATION_ID + "_title";
    private static final String MIGRATION_STATE_ID = MIGRATION_ID + "_state";
    private static final String MIGRATION_PROGRESS_TRACK_ID = MIGRATION_ID + "_track";
    private static final String PERFORMANCE_PANEL_ID = "trinity_information_exchange_depot_performance";
    private static final String PERFORMANCE_TITLE_ID = PERFORMANCE_PANEL_ID + "_title";
    private static final String EXCHANGE_TICK_ID = PERFORMANCE_PANEL_ID + "_exchange_tick";
    private static final String CORE_TICK_ID = PERFORMANCE_PANEL_ID + "_core_tick";
    private static final String MIGRATION_TICK_ID = PERFORMANCE_PANEL_ID + "_migration_tick";

    private static final String TRANSLATION_PREFIX = "gui.data_energistics.trinity_information_exchange_depot.";
    private static final int PROGRESS_TRACK_WIDTH = 175;
    private static final int PROGRESS_TRACK_HEIGHT = 10;
    private static final double TICK_BUDGET_NANOS = 50_000_000.0D;

    private TrinityInformationExchangeDepotUi() {}

    public static ModularUI mount(TrinityInformationExchangeDepotMenu menu, Component title) {
        try {
            UI ui = TrinityUiNbtLayouts.load("information_exchange_depot");
            Layout layout = Layout.bind(ui.rootElement);
            bindText(layout, title);
            bindModes(menu, layout);
            layout.telemetry().bind(menu, layout.root());
            layout.close().setOnClick(event -> menu.getPlayer().closeContainer());
            WindowDragHelper.setDragMove(
                    layout.root(),
                    layout.root(),
                    event -> event.button == 0 && event.target == layout.root(),
                    ignored -> {});

            ModularUI modularUI = ModularUI.of(ui, menu.getPlayer());
            AeMenuBridge.create(menu).mount(modularUI);
            return modularUI;
        } catch (RuntimeException | Error failure) {
            Data_Energistics.LOGGER.error("Failed to create the Trinity information exchange depot LDLib2 UI", failure);
            throw failure;
        }
    }

    private static void bindText(Layout layout, Component title) {
        layout.title().setText(title);
        layout.title().setAllowHitTest(false);
        layout.title().addClass("trinity-information-exchange-title");
        layout.title().style(style -> style.backgroundTexture(IGuiTexture.EMPTY));
        layout.close().text.style(style -> style.tooltips(Component.translatable("gui.close")));
        layout.close().style(style -> style.tooltips(Component.translatable("gui.close")));
    }

    private static void bindModes(TrinityInformationExchangeDepotMenu menu, Layout layout) {
        bindMode(menu, layout.storage(), layout.storageLabel(), StorageMode.STORAGE);
        bindMode(menu, layout.input(), layout.inputLabel(), StorageMode.INPUT);
        bindMode(menu, layout.output(), layout.outputLabel(), StorageMode.OUTPUT);
        layout.root().addEventListener(UIEvents.TICK, ignored -> {
            StorageMode mode = menu.mode();
            layout.input().setOn(mode == StorageMode.INPUT, false);
            layout.storage().setOn(mode == StorageMode.STORAGE, false);
            layout.output().setOn(mode == StorageMode.OUTPUT, false);
        });
    }

    private static void bindMode(
                                 TrinityInformationExchangeDepotMenu menu,
                                 Toggle toggle,
                                 Label label,
                                 StorageMode mode) {
        String translationKey = TRANSLATION_PREFIX + "mode." + mode.serializedName();
        toggle.noText();
        toggle.setOnToggleChanged(on -> {
            if (on && menu.mode() != mode) {
                menu.sendSetMode(mode);
            }
        });
        Component tooltip = Component.translatable(translationKey + ".hint");
        label.setText(Component.translatable(translationKey));
        label.style(style -> style.tooltips(tooltip));
        toggle.style(style -> style.tooltips(tooltip));
        toggle.toggleButton(button -> button.style(style -> style.tooltips(tooltip)));
    }

    private static void placeProgressBar(TrinityPatternProgressBar progressBar) {
        progressBar.layout(layout -> layout
                .positionType(TaffyPosition.ABSOLUTE)
                .left(0)
                .top(0)
                .width(PROGRESS_TRACK_WIDTH)
                .height(PROGRESS_TRACK_HEIGHT));
    }

    private static Component maintenanceState(Operation operation, Stage stage) {
        if (operation == Operation.IDLE) {
            return Component.translatable(TRANSLATION_PREFIX + "migration.idle");
        }
        return Component.translatable(
                TRANSLATION_PREFIX + "migration.state",
                Component.translatable(maintenanceOperationKey(operation)),
                Component.translatable(maintenanceStageKey(stage)));
    }

    private static String maintenanceOperationKey(Operation operation) {
        return "tooltip.data_energistics.trinity_data_core.pattern.operation." +
                operation.name().toLowerCase(Locale.ROOT);
    }

    private static String maintenanceStageKey(Stage stage) {
        return "tooltip.data_energistics.trinity_data_core.pattern.stage." +
                stage.name().toLowerCase(Locale.ROOT);
    }

    private static Component maintenanceProgress(Stage stage, long completed, long total) {
        long percentage = Math.round(maintenanceProgressRatio(stage, completed, total) * 100.0D);
        return Component.translatable(
                TRANSLATION_PREFIX + "migration.progress",
                completed,
                total,
                percentage);
    }

    private static double maintenanceProgressRatio(Stage stage, long completed, long total) {
        if (stage.terminal()) {
            return 1.0D;
        }
        return total == 0L ? 0.0D : Math.clamp(completed / (double) total, 0.0D, 1.0D);
    }

    private static Component duration(long nanos) {
        if (nanos < 1_000_000L) {
            return Component.literal(nanos == 0L ? "0 µs" : String.format(Locale.ROOT, "%.2f µs", nanos / 1_000.0D));
        }
        double milliseconds = nanos / 1_000_000.0D;
        double budgetPercentage = nanos * 100.0D / TICK_BUDGET_NANOS;
        return Component.literal(String.format(Locale.ROOT, "%.2f ms · %.1f%%", milliseconds, budgetPercentage));
    }

    private record Layout(
                          UIElement root,
                          Toggle input,
                          Toggle storage,
                          Toggle output,
                          Label title,
                          Button close,
                          Label inputLabel,
                          Label storageLabel,
                          Label outputLabel,
                          TelemetryArea telemetry) {

        private static Layout bind(UIElement root) {
            UIElement content = requireChild(root, CONTENT_ID, UIElement.class, "content");
            Label title = requireChild(root, TITLE_ID, Label.class, "title");
            Button close = requireChild(root, CLOSE_ID, Button.class, "close");
            ToggleGroupElement group = requireChild(content, MODE_GROUP_ID, ToggleGroupElement.class, "mode group");
            requireChild(content, MODE_TITLE_ID, Label.class, "mode title");
            Label storageLabel = requireChild(content, STORAGE_MODE_LABEL_ID, Label.class, "storage mode label");
            Label inputLabel = requireChild(content, INPUT_MODE_LABEL_ID, Label.class, "input mode label");
            Label outputLabel = requireChild(content, OUTPUT_MODE_LABEL_ID, Label.class, "output mode label");
            Toggle storage = requireChild(group, STORAGE_MODE_ID, Toggle.class, "storage mode");
            Toggle input = requireChild(group, INPUT_MODE_ID, Toggle.class, "input mode");
            Toggle output = requireChild(group, OUTPUT_MODE_ID, Toggle.class, "output mode");

            root.setId(ROOT_ID);
            return new Layout(
                    root,
                    input,
                    storage,
                    output,
                    title,
                    close,
                    inputLabel,
                    storageLabel,
                    outputLabel,
                    TelemetryArea.bind(content));
        }

        private static List<UIElement> authoredChildren(UIElement element) {
            return element.getChildren().stream().filter(child -> !child.isInternalUI()).toList();
        }

        private static <T extends UIElement> T requireChild(
                                                            UIElement parent,
                                                            String id,
                                                            Class<T> expected,
                                                            String role) {
            List<UIElement> matches = authoredChildren(parent).stream()
                    .filter(child -> id.equals(child.getId()))
                    .toList();
            if (matches.size() != 1) {
                throw new IllegalStateException("Information exchange depot " + role + " expected one authored child '" +
                        id + "', found " + matches.size());
            }
            UIElement child = matches.getFirst();
            if (!expected.isInstance(child)) {
                throw new IllegalStateException("Information exchange depot " + role + " must be " +
                        expected.getSimpleName() + ", found " + child.getClass().getSimpleName());
            }
            return expected.cast(child);
        }
    }

    private static final class TelemetryArea {

        private final TrinityPatternProgressBar progressBar;
        private final UIElement progressHost;
        private final Label state;
        private final Label exchangeTick;
        private final Label coreTick;
        private final Label migrationTick;
        private int operationId = -1;
        private int stageId = -1;
        private long completed = -1L;
        private long total = -1L;
        private long exchangeTickNanos = -1L;
        private long coreTickNanos = -1L;
        private long migrationTickNanos = -1L;
        private long migrationTickWorkUnits = -1L;

        private TelemetryArea(
                              TrinityPatternProgressBar progressBar,
                              UIElement progressHost,
                              Label state,
                              Label exchangeTick,
                              Label coreTick,
                              Label migrationTick) {
            this.progressBar = progressBar;
            this.progressHost = progressHost;
            this.state = state;
            this.exchangeTick = exchangeTick;
            this.coreTick = coreTick;
            this.migrationTick = migrationTick;
        }

        private static TelemetryArea bind(UIElement content) {
            UIElement migrationPanel = Layout.requireChild(content, MIGRATION_ID, UIElement.class, "migration panel");
            Layout.requireChild(migrationPanel, MIGRATION_TITLE_ID, Label.class, "migration title");
            Label state = Layout.requireChild(migrationPanel, MIGRATION_STATE_ID, Label.class, "migration state");
            UIElement progressHost = Layout.requireChild(
                    content, MIGRATION_PROGRESS_TRACK_ID, UIElement.class, "migration progress track");
            progressHost.setAllowHitTest(true);
            progressHost.setOverflowVisible(false);
            progressHost.style(style -> style.backgroundTexture(IGuiTexture.EMPTY));
            TrinityPatternProgressBar progressBar = TrinityPatternProgressBar.horizontal(
                    MIGRATION_PROGRESS_TRACK_ID + "_live");
            placeProgressBar(progressBar);
            progressHost.addChild(progressBar);

            UIElement performancePanel = Layout.requireChild(
                    content, PERFORMANCE_PANEL_ID, UIElement.class, "performance panel");
            Layout.requireChild(performancePanel, PERFORMANCE_TITLE_ID, Label.class, "performance title");
            Layout.requireChild(performancePanel, EXCHANGE_TICK_ID + "_label", Label.class, "exchange tick label");
            Layout.requireChild(performancePanel, CORE_TICK_ID + "_label", Label.class, "core tick label");
            Layout.requireChild(performancePanel, MIGRATION_TICK_ID + "_label", Label.class, "migration tick label");
            Label exchangeTick = Layout.requireChild(
                    performancePanel, EXCHANGE_TICK_ID + "_value", Label.class, "exchange tick value");
            Label coreTick = Layout.requireChild(
                    performancePanel, CORE_TICK_ID + "_value", Label.class, "core tick value");
            Label migrationTick = Layout.requireChild(
                    performancePanel, MIGRATION_TICK_ID + "_value", Label.class, "migration tick value");
            return new TelemetryArea(progressBar, progressHost, state, exchangeTick, coreTick, migrationTick);
        }

        private void bind(TrinityInformationExchangeDepotMenu menu, UIElement tickSource) {
            refresh(menu);
            tickSource.addEventListener(UIEvents.TICK, ignored -> refresh(menu));
        }

        private void refresh(TrinityInformationExchangeDepotMenu menu) {
            Operation operation = menu.maintenanceOperation();
            Stage stage = menu.maintenanceStage();
            long nextCompleted = menu.maintenanceCompletedUnits;
            long nextTotal = menu.maintenanceTotalUnits;
            if (this.operationId != operation.ordinal() || this.stageId != stage.ordinal() ||
                    this.completed != nextCompleted || this.total != nextTotal) {
                this.operationId = operation.ordinal();
                this.stageId = stage.ordinal();
                this.completed = nextCompleted;
                this.total = nextTotal;
                this.state.setText(maintenanceState(operation, stage));
                Component progressTooltip = maintenanceProgress(stage, nextCompleted, nextTotal);
                this.progressHost.style(style -> style.tooltips(progressTooltip));
                if (operation == Operation.IDLE) {
                    this.progressBar.clearProgress();
                } else {
                    this.progressBar.setProgress(
                            (float) maintenanceProgressRatio(stage, nextCompleted, nextTotal),
                            progressAppearance(operation, stage));
                }
            }

            if (this.exchangeTickNanos != menu.exchangeDepotTickNanos) {
                this.exchangeTickNanos = menu.exchangeDepotTickNanos;
                this.exchangeTick.setText(duration(this.exchangeTickNanos));
            }
            if (this.coreTickNanos != menu.coreTickNanos) {
                this.coreTickNanos = menu.coreTickNanos;
                this.coreTick.setText(duration(this.coreTickNanos));
            }
            if (this.migrationTickNanos != menu.maintenanceTickNanos ||
                    this.migrationTickWorkUnits != menu.maintenanceTickWorkUnits) {
                this.migrationTickNanos = menu.maintenanceTickNanos;
                this.migrationTickWorkUnits = menu.maintenanceTickWorkUnits;
                this.migrationTick.setText(Component.translatable(
                        TRANSLATION_PREFIX + "performance.migration_value",
                        duration(this.migrationTickNanos),
                        this.migrationTickWorkUnits));
            }
        }

        private static TrinityPatternProgressAppearance progressAppearance(Operation operation, Stage stage) {
            return switch (stage) {
                case COMPLETED -> TrinityPatternProgressAppearance.COMPLETED;
                case FAILED, CANCELLED -> TrinityPatternProgressAppearance.FAILED;
                default -> switch (operation) {
                    case MIGRATION -> TrinityPatternProgressAppearance.MIGRATION;
                    case REFUND_PATTERNS -> TrinityPatternProgressAppearance.REFUND;
                    case IDLE -> throw new IllegalStateException("Idle maintenance has no progress appearance");
                };
            };
        }
    }
}

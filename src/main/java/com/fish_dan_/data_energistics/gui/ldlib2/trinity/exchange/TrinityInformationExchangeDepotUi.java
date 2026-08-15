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

import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

import com.lowdragmc.lowdraglib2.gui.texture.IGuiTexture;
import com.lowdragmc.lowdraglib2.gui.texture.SpriteTexture;
import com.lowdragmc.lowdraglib2.gui.ui.ModularUI;
import com.lowdragmc.lowdraglib2.gui.ui.UI;
import com.lowdragmc.lowdraglib2.gui.ui.UIElement;
import com.lowdragmc.lowdraglib2.gui.ui.elements.Button;
import com.lowdragmc.lowdraglib2.gui.ui.elements.Label;
import com.lowdragmc.lowdraglib2.gui.ui.elements.Toggle;
import com.lowdragmc.lowdraglib2.gui.ui.elements.ToggleGroupElement;
import com.lowdragmc.lowdraglib2.gui.ui.event.UIEvents;
import com.lowdragmc.lowdraglib2.gui.util.WindowDragHelper;
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
    private static final String MIGRATION_PANEL_ID = "trinity_information_exchange_depot_migration";
    private static final String MIGRATION_STATE_ID = MIGRATION_PANEL_ID + "_state";
    private static final String MIGRATION_PROGRESS_ID = MIGRATION_PANEL_ID + "_progress";
    private static final String MIGRATION_PROGRESS_TRACK_ID = MIGRATION_PANEL_ID + "_track";
    private static final String PERFORMANCE_PANEL_ID = "trinity_information_exchange_depot_performance";
    private static final String EXCHANGE_TICK_ID = PERFORMANCE_PANEL_ID + "_exchange_tick";
    private static final String CORE_TICK_ID = PERFORMANCE_PANEL_ID + "_core_tick";
    private static final String MIGRATION_TICK_ID = PERFORMANCE_PANEL_ID + "_migration_tick";

    private static final String TRANSLATION_PREFIX = "gui.data_energistics.trinity_information_exchange_depot.";
    private static final ResourceLocation PANEL_TEXTURE = ResourceLocation.fromNamespaceAndPath(
            Data_Energistics.MODID,
            "textures/guis/trinity/exchange/panel.png");
    private static final int CONTENT_WIDTH = 187;
    private static final int CONTENT_INSET = 8;
    private static final int PANEL_WIDTH = CONTENT_WIDTH - CONTENT_INSET * 2;
    private static final int TELEMETRY_PANEL_LEFT = 6;
    private static final int TELEMETRY_PANEL_WIDTH = 175;
    private static final int TELEMETRY_PANEL_HEIGHT = 42;
    private static final int PROGRESS_TEXT_WIDTH = PANEL_WIDTH - 10;
    private static final int PROGRESS_TRACK_LEFT = 6;
    private static final int PROGRESS_TRACK_TOP = 55;
    private static final int PROGRESS_TRACK_WIDTH = 175;
    private static final int PROGRESS_TRACK_HEIGHT = 10;
    private static final double TICK_BUDGET_NANOS = 50_000_000.0D;

    private TrinityInformationExchangeDepotUi() {}

    public static ModularUI mount(TrinityInformationExchangeDepotMenu menu, Component title) {
        try {
            UI ui = TrinityUiNbtLayouts.load("information_exchange_depot");
            Layout layout = Layout.bind(ui.rootElement);
            layout.applyGeometry();
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

    private static Label text(String id, String translationKey, String styleClass) {
        Label label = new Label();
        label.setId(id);
        label.setText(Component.translatable(translationKey));
        label.setOverflowVisible(false);
        label.addClass(styleClass);
        return label;
    }

    private static Label value(String id) {
        Label label = new Label();
        label.setId(id);
        label.setText(Component.empty());
        label.setOverflowVisible(false);
        label.addClass("trinity-information-exchange-value");
        return label;
    }

    private static UIElement panel(String id, int top) {
        UIElement panel = new UIElement();
        panel.setId(id);
        panel.setOverflowVisible(false);
        panel.style(style -> style.backgroundTexture(SpriteTexture
                .of(PANEL_TEXTURE)
                .setSprite(0, 0, TELEMETRY_PANEL_WIDTH, TELEMETRY_PANEL_HEIGHT)));
        panel.layout(layout -> layout
                .positionType(TaffyPosition.ABSOLUTE)
                .left(TELEMETRY_PANEL_LEFT)
                .top(top)
                .width(TELEMETRY_PANEL_WIDTH)
                .height(TELEMETRY_PANEL_HEIGHT));
        return panel;
    }

    private static void place(UIElement element, int left, int top, int width, int height) {
        element.layout(layout -> layout
                .positionType(TaffyPosition.ABSOLUTE)
                .left(left)
                .top(top)
                .width(width)
                .height(height));
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
                          UIElement content,
                          ToggleGroupElement group,
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
            List<UIElement> rootChildren = authoredChildren(root);
            if (rootChildren.size() != 2) {
                throw new IllegalStateException("Information exchange depot layout expected two authored root children");
            }
            UIElement content = require(rootChildren, 0, UIElement.class, "content");
            Button close = require(rootChildren, 1, Button.class, "close");
            List<UIElement> contentChildren = authoredChildren(content);
            if (contentChildren.size() != 2) {
                throw new IllegalStateException("Information exchange depot content expected two authored children");
            }
            ToggleGroupElement group = require(contentChildren, 0, ToggleGroupElement.class, "mode group");
            Label title = require(contentChildren, 1, Label.class, "title");
            List<UIElement> toggles = authoredChildren(group);
            if (toggles.size() != 3) {
                throw new IllegalStateException("Information exchange depot mode group expected three authored toggles");
            }
            Toggle storage = require(toggles, 0, Toggle.class, "storage mode");
            Toggle input = require(toggles, 1, Toggle.class, "input mode");
            Toggle output = require(toggles, 2, Toggle.class, "output mode");

            root.setId(ROOT_ID);
            content.setId(CONTENT_ID);
            group.setId(MODE_GROUP_ID);
            title.setId(TITLE_ID);
            close.setId(CLOSE_ID);
            input.setId(INPUT_MODE_ID);
            storage.setId(STORAGE_MODE_ID);
            output.setId(OUTPUT_MODE_ID);
            Label storageLabel = modeLabel(STORAGE_MODE_LABEL_ID);
            Label inputLabel = modeLabel(INPUT_MODE_LABEL_ID);
            Label outputLabel = modeLabel(OUTPUT_MODE_LABEL_ID);
            content.addChildren(storageLabel, inputLabel, outputLabel);
            return new Layout(
                    root,
                    content,
                    group,
                    input,
                    storage,
                    output,
                    title,
                    close,
                    inputLabel,
                    storageLabel,
                    outputLabel,
                    TelemetryArea.create(content));
        }

        private void applyGeometry() {
            this.content.removeChild(this.title);
            this.root.addChild(this.title);
            place(this.title, 8, 0, 160, 12);

            Label modeTitle = text(
                    MODE_TITLE_ID,
                    TRANSLATION_PREFIX + "mode.title",
                    "trinity-information-exchange-section-title");
            place(modeTitle, CONTENT_INSET, 3, PANEL_WIDTH, 8);
            this.content.addChild(modeTitle);

            place(this.storageLabel, 8, 13, 57, 8);
            place(this.inputLabel, 65, 13, 57, 8);
            place(this.outputLabel, 122, 13, 57, 8);

            place(this.group, 8, 22, PANEL_WIDTH, 12);
            place(this.storage, 17, 0, 22, 12);
            place(this.input, 74, 0, 22, 12);
            place(this.output, 131, 0, 22, 12);
        }

        private static Label modeLabel(String id) {
            Label label = value(id);
            label.removeClass("trinity-information-exchange-value");
            label.addClass("trinity-information-exchange-mode-caption");
            return label;
        }

        private static List<UIElement> authoredChildren(UIElement element) {
            return element.getChildren().stream().filter(child -> !child.isInternalUI()).toList();
        }

        private static <T extends UIElement> T require(
                                                       List<UIElement> children,
                                                       int index,
                                                       Class<T> expected,
                                                       String role) {
            UIElement child = children.get(index);
            if (!expected.isInstance(child)) {
                throw new IllegalStateException("Information exchange depot " + role + " must be " +
                        expected.getSimpleName() + ", found " + child.getClass().getSimpleName());
            }
            return expected.cast(child);
        }
    }

    private static final class TelemetryArea {

        private final TrinityPatternProgressBar progressBar;
        private final Label state;
        private final Label progress;
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
                              Label state,
                              Label progress,
                              Label exchangeTick,
                              Label coreTick,
                              Label migrationTick) {
            this.progressBar = progressBar;
            this.state = state;
            this.progress = progress;
            this.exchangeTick = exchangeTick;
            this.coreTick = coreTick;
            this.migrationTick = migrationTick;
        }

        private static TelemetryArea create(UIElement content) {
            UIElement migrationPanel = panel(MIGRATION_PANEL_ID, 41);
            Label migrationTitle = text(
                    MIGRATION_PANEL_ID + "_title",
                    TRANSLATION_PREFIX + "migration.title",
                    "trinity-information-exchange-section-title");
            Label state = value(MIGRATION_STATE_ID);
            Label progress = value(MIGRATION_PROGRESS_ID);
            place(migrationTitle, 5, 3, 61, 8);
            place(state, 67, 3, 99, 8);
            place(progress, 5, 24, PROGRESS_TEXT_WIDTH, 9);
            migrationPanel.addChildren(migrationTitle, state, progress);

            TrinityPatternProgressBar progressBar = TrinityPatternProgressBar.horizontal(MIGRATION_PROGRESS_TRACK_ID);
            place(
                    progressBar,
                    PROGRESS_TRACK_LEFT,
                    PROGRESS_TRACK_TOP,
                    PROGRESS_TRACK_WIDTH,
                    PROGRESS_TRACK_HEIGHT);

            UIElement performancePanel = panel(PERFORMANCE_PANEL_ID, 85);
            Label performanceTitle = text(
                    PERFORMANCE_PANEL_ID + "_title",
                    TRANSLATION_PREFIX + "performance.title",
                    "trinity-information-exchange-section-title");
            Label exchangeLabel = text(
                    EXCHANGE_TICK_ID + "_label",
                    TRANSLATION_PREFIX + "performance.exchange_tick",
                    "trinity-information-exchange-label");
            Label coreLabel = text(
                    CORE_TICK_ID + "_label",
                    TRANSLATION_PREFIX + "performance.core_tick",
                    "trinity-information-exchange-label");
            Label migrationLabel = text(
                    MIGRATION_TICK_ID + "_label",
                    TRANSLATION_PREFIX + "performance.migration_tick",
                    "trinity-information-exchange-label");
            Label exchangeTick = value(EXCHANGE_TICK_ID + "_value");
            Label coreTick = value(CORE_TICK_ID + "_value");
            Label migrationTick = value(MIGRATION_TICK_ID + "_value");
            place(performanceTitle, 5, 3, 161, 8);
            place(exchangeLabel, 5, 12, 55, 8);
            place(exchangeTick, 61, 12, 105, 8);
            place(coreLabel, 5, 20, 55, 8);
            place(coreTick, 61, 20, 105, 8);
            place(migrationLabel, 5, 28, 55, 8);
            place(migrationTick, 61, 28, 105, 8);
            performancePanel.addChildren(
                    performanceTitle,
                    exchangeLabel,
                    exchangeTick,
                    coreLabel,
                    coreTick,
                    migrationLabel,
                    migrationTick);

            content.addChildren(migrationPanel, progressBar, performancePanel);
            return new TelemetryArea(progressBar, state, progress, exchangeTick, coreTick, migrationTick);
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
                this.progress.setText(maintenanceProgress(stage, nextCompleted, nextTotal));
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

package com.fish_dan_.data_energistics.gui.ldlib2.compartment;

import com.fish_dan_.data_energistics.Data_Energistics;
import com.fish_dan_.data_energistics.common.compartment.CompartmentType;
import com.fish_dan_.data_energistics.gui.ldlib2.ae.bridge.AeMenuBridge;
import com.fish_dan_.data_energistics.gui.ldlib2.ae.inventory.AePlayerInventoryLayout;
import com.fish_dan_.data_energistics.gui.ldlib2.ae.inventory.AePlayerInventoryPanel;
import com.fish_dan_.data_energistics.gui.ldlib2.compartment.slot.MeInputCompartmentPanel;
import com.fish_dan_.data_energistics.gui.ldlib2.compartment.slot.MeOutputCompartmentPanel;
import com.fish_dan_.data_energistics.gui.ldlib2.compartment.slot.PatternBufferCompartmentPanel;
import com.fish_dan_.data_energistics.menu.storage.CompartmentMenu;
import com.fish_dan_.data_energistics.menu.storage.CompositeWarehouseMenu;
import com.fish_dan_.data_energistics.menu.storage.MeCompositeInputWarehouseMenu;
import com.fish_dan_.data_energistics.menu.storage.MeCompositeOutputWarehouseMenu;
import com.fish_dan_.data_energistics.menu.storage.MePatternBufferMenu;

import com.lowdragmc.lowdraglib2.gui.sync.bindings.impl.SupplierDataSource;
import com.lowdragmc.lowdraglib2.gui.texture.SpriteTexture;
import com.lowdragmc.lowdraglib2.gui.ui.ModularUI;
import com.lowdragmc.lowdraglib2.gui.ui.UI;
import com.lowdragmc.lowdraglib2.gui.ui.UIElement;
import com.lowdragmc.lowdraglib2.gui.ui.data.TextWrap;
import com.lowdragmc.lowdraglib2.gui.ui.elements.Label;

import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;

import dev.vfyjxf.taffy.style.TaffyPosition;

import java.util.function.Function;

/**
 * Mounts deterministic LDLib2 roots after a compartment menu has created all of its AE2 slots.
 */
public final class CompartmentHostUi {

    public static final String TITLE_ID = "compartment_title";
    public static final String HEADER_STATUS_ID = "compartment_header_status";
    public static final String PLAYER_INVENTORY_TITLE_ID = "compartment_player_inventory_title";
    public static final String COMPOSITE_WAREHOUSE_ROOT_ID = "composite_warehouse_root";
    public static final String ME_INPUT_ROOT_ID = "me_input_compartment_root";
    public static final String ME_OUTPUT_ROOT_ID = "me_output_compartment_root";
    public static final String PATTERN_BUFFER_ROOT_ID = "pattern_buffer_compartment_root";
    static final int TITLE_COLOR = 0xFF404050;
    private static final int TITLE_LEFT = 8;
    private static final int HEADER_TOP = 5;
    private static final int HEADER_HEIGHT = 9;
    private static final int HEADER_GAP = 8;
    private static final HostLayout COMPOSITE_WAREHOUSE_LAYOUT = new HostLayout(
            COMPOSITE_WAREHOUSE_ROOT_ID,
            176,
            253,
            new AePlayerInventoryLayout(8, 169, 227),
            "ae2:textures/guis/composite_warehouse.png",
            "plain composite warehouse",
            true);
    private static final HostLayout ME_INPUT_LAYOUT = new HostLayout(
            ME_INPUT_ROOT_ID,
            208,
            208,
            new AePlayerInventoryLayout(24, 124, 182),
            "ae2:textures/guis/me_composite_input_warehouse.png",
            "ME input compartment",
            false);
    private static final HostLayout ME_OUTPUT_LAYOUT = new HostLayout(
            ME_OUTPUT_ROOT_ID,
            176,
            192,
            new AePlayerInventoryLayout(8, 108, 166),
            "ae2:textures/guis/me_composite_output_warehouse.png",
            "ME output compartment",
            false);
    private static final HostLayout PATTERN_BUFFER_LAYOUT = new HostLayout(
            PATTERN_BUFFER_ROOT_ID,
            256,
            226,
            new AePlayerInventoryLayout(8, 139, 197),
            "ae2:textures/guis/me_pattern_buffer.png",
            "ME pattern-buffer compartment",
            false);

    private CompartmentHostUi() {}

    /**
     * Mounts all plain input/output warehouse slots while retaining the original AE2 menu protocol.
     *
     * @param menu fully constructed plain composite warehouse menu
     */
    public static void mountCompositeWarehouse(CompositeWarehouseMenu menu) {
        mountCompositeWarehouse(
                menu,
                bridge -> CompositeWarehousePanel.create(menu, bridge));
    }

    /** Fixed plain warehouse mount boundary used by package-level fault-injection tests. */
    static void mountCompositeWarehouse(CompartmentMenu menu, Function<AeMenuBridge, UIElement> contentFactory) {
        mount(menu, contentFactory, COMPOSITE_WAREHOUSE_LAYOUT);
    }

    /**
     * Mounts the paired ME input configuration and buffer grids with the original player slots.
     *
     * @param menu fully constructed ME input menu
     */
    public static void mountMeInput(MeCompositeInputWarehouseMenu menu) {
        mountMeInput(
                menu,
                bridge -> MeInputCompartmentPanel.create(menu, bridge));
    }

    /**
     * Fixed ME input mount boundary used by package-level fault-injection tests.
     */
    static void mountMeInput(CompartmentMenu menu, Function<AeMenuBridge, UIElement> contentFactory) {
        mount(menu, contentFactory, ME_INPUT_LAYOUT);
    }

    /**
     * Mounts the ME output display and the original player slots on both menu sides.
     *
     * @param menu fully constructed ME output menu
     */
    public static void mountMeOutput(MeCompositeOutputWarehouseMenu menu) {
        mountMeOutput(
                menu,
                bridge -> MeOutputCompartmentPanel.create(menu, bridge));
    }

    /**
     * Fixed ME output mount boundary used by package-level fault-injection tests.
     */
    static void mountMeOutput(CompartmentMenu menu, Function<AeMenuBridge, UIElement> contentFactory) {
        mount(menu, contentFactory, ME_OUTPUT_LAYOUT);
    }

    /**
     * Mounts the fixed pattern, aggregate display, catalyst, composite-key, and player surfaces.
     *
     * @param menu fully constructed pattern-buffer menu
     */
    public static void mountPatternBuffer(MePatternBufferMenu menu) {
        mountPatternBuffer(
                menu,
                bridge -> PatternBufferCompartmentPanel.create(menu, bridge));
    }

    /** Fixed pattern-buffer mount boundary used by package-level fault-injection tests. */
    static void mountPatternBuffer(CompartmentMenu menu, Function<AeMenuBridge, UIElement> contentFactory) {
        mount(menu, contentFactory, PATTERN_BUFFER_LAYOUT);
    }

    private static void mount(CompartmentMenu menu,
                              Function<AeMenuBridge, UIElement> contentFactory,
                              HostLayout hostLayout) {
        validateMountArguments(menu, contentFactory);
        AeMenuBridge bridge = AeMenuBridge.create(menu);
        CompartmentModularUI modularUI = null;
        try {
            UIElement content = contentFactory.apply(bridge);
            if (content == null) {
                throw invalid("content factory returned no element");
            }
            UIElement root = new UIElement();
            root.setId(hostLayout.rootId());
            root.layout(layout -> layout.width(hostLayout.width()).height(hostLayout.height()));
            if (hostLayout.overflowVisible()) {
                root.setOverflowVisible(true);
            }
            root.style(style -> style.backgroundTexture(SpriteTexture
                    .of(hostLayout.backgroundTexture())
                    .setSprite(0, 0, hostLayout.width(), hostLayout.height())));
            HeaderGeometry header = headerGeometry(menu.getCompartmentType());
            root.addChild(title(
                    TITLE_ID,
                    compartmentTitle(menu),
                    TITLE_LEFT,
                    HEADER_TOP,
                    header.titleWidth()));
            root.addChild(headerStatus(menu, header));
            root.addChild(title(
                    PLAYER_INVENTORY_TITLE_ID,
                    Component.translatable("container.inventory"),
                    hostLayout.playerLayout().slotLeft(),
                    hostLayout.playerLayout().inventoryTop() - 11,
                    162));
            root.addChild(content);
            root.addChild(AePlayerInventoryPanel.create(menu, bridge, hostLayout.playerLayout()));
            modularUI = new CompartmentModularUI(UI.of(root), menu.getPlayer());
            bridge.mount(modularUI);
        } catch (RuntimeException | Error failure) {
            Data_Energistics.LOGGER.error("Failed to create the {} LDLib2 host UI", hostLayout.logName(), failure);
            if (modularUI != null) {
                try {
                    modularUI.onRemoved();
                } catch (RuntimeException | Error cleanupFailure) {
                    Data_Energistics.LOGGER.error(
                            "Failed to release an incomplete {} LDLib2 UI",
                            hostLayout.logName(),
                            cleanupFailure);
                    if (cleanupFailure != failure) {
                        failure.addSuppressed(cleanupFailure);
                    }
                }
            }
            throw failure;
        }
    }

    private static void validateMountArguments(CompartmentMenu menu,
                                               Function<AeMenuBridge, UIElement> contentFactory) {
        if (menu == null || contentFactory == null) {
            throw invalid("menu and content factory must both be present");
        }
    }

    static Label title(String id, Component text, int left, int top, int width) {
        Label label = new Label();
        label.setId(id);
        label.setText(text);
        label.textStyle(style -> style
                .adaptiveWidth(false)
                .adaptiveHeight(false)
                .textWrap(TextWrap.HOVER_ROLL)
                .textColor(TITLE_COLOR)
                .textShadow(false));
        label.layout(layout -> layout
                .positionType(TaffyPosition.ABSOLUTE)
                .left(left)
                .top(top)
                .width(width)
                .height(9));
        label.setOverflowVisible(false);
        return label;
    }

    private static Label headerStatus(CompartmentMenu menu, HeaderGeometry geometry) {
        Label label = title(
                HEADER_STATUS_ID,
                Component.empty(),
                geometry.statusLeft(),
                HEADER_TOP,
                geometry.statusWidth());
        switch (menu.getCompartmentType()) {
            case INPUT, OUTPUT -> label.bindDataSource(SupplierDataSource.of(() -> Component.translatable(
                    "screen.data_energistics.compartment.unlocked_rows",
                    menu.unlockedRowCount,
                    CompartmentMenu.COMPOSITE_WAREHOUSE_ROW_COUNT)));
            case ME_INPUT -> label.setText(Component.translatable(
                    "screen.data_energistics.compartment.config_to_buffer"));
            case ME_OUTPUT -> label.setText(Component.translatable(
                    "screen.data_energistics.compartment.read_only"));
            case PATTERN_BUFFER -> label.setText(Component.translatable(
                    "screen.data_energistics.compartment.aggregation_read_only"));
            case TRINITY_INFORMATION_EXCHANGE -> throw invalid("Trinity information exchange does not use the compartment host UI");
        }
        return label;
    }

    private static Component compartmentTitle(CompartmentMenu menu) {
        String titleKey = switch (menu.getCompartmentType()) {
            case INPUT -> "screen.data_energistics.compartment.title.input";
            case OUTPUT -> "screen.data_energistics.compartment.title.output";
            case ME_INPUT -> "screen.data_energistics.compartment.title.me_input";
            case ME_OUTPUT -> "screen.data_energistics.compartment.title.me_output";
            case PATTERN_BUFFER -> "screen.data_energistics.compartment.title.pattern_buffer";
            case TRINITY_INFORMATION_EXCHANGE -> throw invalid("Trinity information exchange does not use the compartment host UI");
        };
        return Component.translatable(titleKey);
    }

    static HeaderGeometry headerGeometry(CompartmentType type) {
        if (type == null) {
            throw invalid("compartment type must be present");
        }
        return switch (type) {
            case INPUT, OUTPUT, ME_OUTPUT -> new HeaderGeometry(112, 56);
            case ME_INPUT -> new HeaderGeometry(104, 96);
            case PATTERN_BUFFER -> new HeaderGeometry(177, 71);
            case TRINITY_INFORMATION_EXCHANGE -> throw invalid("Trinity information exchange does not use the compartment host UI");
        };
    }

    private static IllegalStateException invalid(String message) {
        Data_Energistics.LOGGER.error("Compartment LDLib2 host invariant failed: {}", message);
        return new IllegalStateException(message);
    }

    private record HostLayout(String rootId,
                              int width,
                              int height,
                              AePlayerInventoryLayout playerLayout,
                              String backgroundTexture,
                              String logName,
                              boolean overflowVisible) {}

    record HeaderGeometry(int statusLeft, int statusWidth) {

        int titleWidth() {
            return statusLeft - TITLE_LEFT - HEADER_GAP;
        }

        int top() {
            return HEADER_TOP;
        }

        int height() {
            return HEADER_HEIGHT;
        }

        int statusRight() {
            return statusLeft + statusWidth;
        }
    }

    /**
     * Ensures bridge and outer failure paths can both request cleanup without releasing the tree twice.
     */
    private static final class CompartmentModularUI extends ModularUI {

        private boolean removed;

        private CompartmentModularUI(UI ui, Player player) {
            super(ui, player);
        }

        @Override
        public void onRemoved() {
            if (this.removed) {
                return;
            }
            this.removed = true;
            super.onRemoved();
        }
    }
}

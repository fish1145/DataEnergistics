package com.fish_dan_.data_energistics.gui.ldlib2.compartment;

import com.fish_dan_.data_energistics.Data_Energistics;
import com.fish_dan_.data_energistics.gui.ldlib2.AeMenuBridge;
import com.fish_dan_.data_energistics.gui.ldlib2.AePlayerInventoryLayout;
import com.fish_dan_.data_energistics.gui.ldlib2.AePlayerInventoryPanel;
import com.fish_dan_.data_energistics.menu.CompartmentMenu;
import com.fish_dan_.data_energistics.menu.MeCompositeInputWarehouseMenu;
import com.fish_dan_.data_energistics.menu.MeCompositeOutputWarehouseMenu;

import net.minecraft.world.entity.player.Player;

import com.lowdragmc.lowdraglib2.gui.ui.ModularUI;
import com.lowdragmc.lowdraglib2.gui.ui.UI;
import com.lowdragmc.lowdraglib2.gui.ui.UIElement;

import java.util.function.Function;

/**
 * Mounts deterministic LDLib2 roots after a compartment menu has created all of its AE2 slots.
 */
public final class CompartmentHostUi {

    public static final String ME_INPUT_ROOT_ID = "me_input_compartment_root";
    public static final String ME_OUTPUT_ROOT_ID = "me_output_compartment_root";
    private static final HostLayout ME_INPUT_LAYOUT = new HostLayout(
            ME_INPUT_ROOT_ID,
            208,
            208,
            new AePlayerInventoryLayout(24, 124, 182),
            "ME input compartment");
    private static final HostLayout ME_OUTPUT_LAYOUT = new HostLayout(
            ME_OUTPUT_ROOT_ID,
            176,
            192,
            new AePlayerInventoryLayout(8, 108, 166),
            "ME output compartment");

    private CompartmentHostUi() {}

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

    private static IllegalStateException invalid(String message) {
        Data_Energistics.LOGGER.error("Compartment LDLib2 host invariant failed: {}", message);
        return new IllegalStateException(message);
    }

    private record HostLayout(String rootId,
                              int width,
                              int height,
                              AePlayerInventoryLayout playerLayout,
                              String logName) {}

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

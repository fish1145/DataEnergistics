package com.fish_dan_.data_energistics.gui.ldlib2.trinity;

import com.fish_dan_.data_energistics.Data_Energistics;
import com.fish_dan_.data_energistics.common.crafting.trinity.TrinityCpuListStatus;
import com.fish_dan_.data_energistics.common.trinity.TrinityDataCoreHostStatus;
import com.fish_dan_.data_energistics.gui.ldlib2.AeMenuBridge;
import com.fish_dan_.data_energistics.gui.ldlib2.AePlayerInventoryLayout;
import com.fish_dan_.data_energistics.gui.ldlib2.AePlayerInventoryPanel;
import com.fish_dan_.data_energistics.gui.ldlib2.HostModularUI;
import com.fish_dan_.data_energistics.gui.ldlib2.HostUiCoordinator;
import com.fish_dan_.data_energistics.gui.ldlib2.HostUiExtension;
import com.fish_dan_.data_energistics.menu.TrinityDataCoreMenu;

import net.minecraft.network.chat.Component;

import com.lowdragmc.lowdraglib2.gui.sync.bindings.IDataProvider;
import com.lowdragmc.lowdraglib2.gui.texture.ColorBorderTexture;
import com.lowdragmc.lowdraglib2.gui.texture.ColorRectTexture;
import com.lowdragmc.lowdraglib2.gui.texture.GuiTextureGroup;
import com.lowdragmc.lowdraglib2.gui.texture.IGuiTexture;
import com.lowdragmc.lowdraglib2.gui.texture.SpriteTexture;
import com.lowdragmc.lowdraglib2.gui.ui.UI;
import com.lowdragmc.lowdraglib2.gui.ui.UIElement;
import com.lowdragmc.lowdraglib2.gui.ui.elements.Label;
import dev.vfyjxf.taffy.style.TaffyPosition;

import java.util.UUID;
import java.util.function.BiConsumer;
import java.util.function.Function;

/**
 * Mounts the double-sided LDLib2 host tree during Trinity Data Core menu construction.
 */
public final class TrinityDataCoreHostUi {

    static final String ROOT_ID = "trinity_data_core_root";
    static final String TITLE_ID = "trinity_data_core_title";
    static final String PLAYER_INVENTORY_TITLE_ID = "trinity_data_core_player_inventory_title";
    private static final int WIDTH = 256;
    private static final int HEIGHT = 212;
    static final int CPU_LIST_LEFT = 168;
    static final int CPU_LIST_TOP = 129;
    static final AePlayerInventoryLayout PLAYER_INVENTORY_LAYOUT = new AePlayerInventoryLayout(6, 130, 188);
    private static final IGuiTexture ROOT_BACKGROUND = GuiTextureGroup.of(
            new ColorRectTexture(0xFFE3E3EA),
            new ColorBorderTexture(-1, 0xFF696D88));
    private static final IGuiTexture PLAYER_SLOT_BACKGROUND = GuiTextureGroup.of(
            SpriteTexture.of("data_energistics:textures/guis/trinity_data_core/inventory_slot.png")
                    .setSprite(0, 0, 16, 16),
            new ColorBorderTexture(-1, 0xFF696D88));

    private TrinityDataCoreHostUi() {}

    /**
     * Builds and mounts the complete root after the AE2 menu has created all of its existing slots.
     *
     * @param menu               menu whose server and client instances must construct an identical root tree
     * @param coordinatorFactory side-specific endpoint factory bound before ModularUI registration begins
     * @return coordinator owned by the mounted ModularUI lifetime
     */
    public static HostUiCoordinator mount(TrinityDataCoreMenu menu,
                                          Function<HostUiExtension, HostUiCoordinator> coordinatorFactory) {
        if (menu == null) {
            Data_Energistics.LOGGER.error("Cannot mount the Trinity Data Core LDLib2 UI without a menu");
            throw new IllegalArgumentException("Trinity Data Core menu must not be null");
        }
        if (coordinatorFactory == null) {
            Data_Energistics.LOGGER.error("Cannot mount the Trinity Data Core LDLib2 UI without a coordinator factory");
            throw new IllegalArgumentException("Trinity Data Core coordinator factory must not be null");
        }

        AeMenuBridge bridge = AeMenuBridge.create(menu);
        TrinityDataCoreUiSync sync = TrinityDataCoreUiSync.create(menu);
        UIElement root = new UIElement();
        root.setId(ROOT_ID);
        root.layout(layout -> layout.width(WIDTH).height(HEIGHT));
        root.style(style -> style.backgroundTexture(ROOT_BACKGROUND));
        HostUiExtension hostUi = HostUiExtension.create(root);
        HostModularUI modularUI = null;
        try {
            registerProviders(menu, hostUi);
            root.addChild(title(
                    TITLE_ID,
                    Component.translatable("block.data_energistics.trinity_data_core"),
                    15,
                    7,
                    218));
            root.addChild(title(
                    PLAYER_INVENTORY_TITLE_ID,
                    Component.translatable("container.inventory"),
                    PLAYER_INVENTORY_LAYOUT.slotLeft(),
                    PLAYER_INVENTORY_LAYOUT.inventoryTop() - 11,
                    162));
            root.addChild(TrinityDataCoreStatusPanel.create(sync.hostStatusProvider()));
            root.addChild(TrinityDataCoreStoragePanel.create(sync.storageStatusProvider()));
            root.addChild(AePlayerInventoryPanel.create(
                    menu,
                    bridge,
                    PLAYER_INVENTORY_LAYOUT,
                    PLAYER_SLOT_BACKGROUND));
            root.addChild(cpuList(menu, sync.cpuListStatusProvider(), sync.hostStatusProvider()));
            root.addChild(TrinityDataCoreHostLauncherPanel.create(hostUi));
            HostUiCoordinator coordinator = coordinatorFactory.apply(hostUi);
            if (coordinator == null || coordinator.hostUi() != hostUi) {
                throw new IllegalStateException("Trinity Data Core coordinator must own the mounted host extension");
            }
            modularUI = hostUi.createModularUI(UI.of(root), menu.getPlayer());
            sync.register(modularUI);
            bridge.mount(modularUI);
            return coordinator;
        } catch (RuntimeException | Error failure) {
            Data_Energistics.LOGGER.error("Failed to create the Trinity Data Core LDLib2 host UI", failure);
            try {
                if (modularUI == null) {
                    HostUiExtension.discardUnmounted(hostUi);
                } else {
                    modularUI.onRemoved();
                }
            } catch (RuntimeException | Error cleanupFailure) {
                Data_Energistics.LOGGER.error("Failed to dispose an incomplete Trinity Data Core host UI", cleanupFailure);
                if (cleanupFailure != failure) {
                    failure.addSuppressed(cleanupFailure);
                }
            }
            throw failure;
        }
    }

    private static Label title(String id, Component text, int left, int top, int width) {
        Label label = new Label();
        label.setId(id);
        label.setText(text);
        label.textStyle(style -> style
                .adaptiveWidth(false)
                .adaptiveHeight(false)
                .textShadow(false));
        label.layout(layout -> layout
                .positionType(TaffyPosition.ABSOLUTE)
                .left(left)
                .top(top)
                .width(width)
                .height(9));
        return label;
    }

    private static TrinityCpuStatusList cpuList(TrinityDataCoreMenu menu,
                                                IDataProvider<TrinityCpuListStatus> statusProvider,
                                                IDataProvider<TrinityDataCoreHostStatus> hostStatusProvider) {
        TrinityCpuStatusList cpuList = new TrinityCpuStatusList();
        cpuList.setOnCpuSelected(cpuNumber -> dispatchCpuSelection(
                hostStatusProvider.getValue(),
                cpuNumber,
                menu::sendOpenCpuStatus));
        cpuList.bindDataSource(statusProvider);
        cpuList.layout(layout -> layout
                .positionType(TaffyPosition.ABSOLUTE)
                .left(CPU_LIST_LEFT)
                .top(CPU_LIST_TOP)
                .width(TrinityCpuStatusList.DEFAULT_WIDTH)
                .height(TrinityCpuStatusList.DEFAULT_HEIGHT));
        return cpuList;
    }

    static boolean dispatchCpuSelection(TrinityDataCoreHostStatus hostStatus,
                                        int cpuNumber,
                                        BiConsumer<UUID, Integer> selectionSink) {
        if (hostStatus == null || selectionSink == null) {
            throw new IllegalArgumentException("Trinity CPU selection requires synchronized host status and a sink");
        }
        if (hostStatus.hostId().isEmpty()) {
            return false;
        }
        selectionSink.accept(hostStatus.hostId().orElseThrow(), cpuNumber);
        return true;
    }

    private static void registerProviders(TrinityDataCoreMenu menu, HostUiExtension hostUi) {
        hostUi.register(TrinityDataCoreStructureProviders.autoBuild(
                menu,
                menu::sendHostedAutoBuild,
                generation -> menu.isHostedActionPending(TrinityDataCoreHostUiKeys.AUTO_BUILD, generation)));
        if (!hostUi.registeredKeys().equals(TrinityDataCoreHostUiKeys.registrationOrder())) {
            throw new IllegalStateException("Trinity Data Core hosted providers were registered out of order");
        }
    }
}

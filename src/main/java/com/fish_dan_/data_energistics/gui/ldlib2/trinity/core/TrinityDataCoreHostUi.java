package com.fish_dan_.data_energistics.gui.ldlib2.trinity.core;

import com.fish_dan_.data_energistics.Data_Energistics;
import com.fish_dan_.data_energistics.bridge.DataEnergisticsClientBridgeAccess;
import com.fish_dan_.data_energistics.common.crafting.trinity.status.TrinityCpuListStatus;
import com.fish_dan_.data_energistics.gui.ldlib2.host.protocol.HostUiKey;
import com.fish_dan_.data_energistics.gui.ldlib2.host.window.HostModularUI;
import com.fish_dan_.data_energistics.gui.ldlib2.host.window.HostUiCoordinator;
import com.fish_dan_.data_energistics.gui.ldlib2.host.window.HostUiExtension;
import com.fish_dan_.data_energistics.gui.ldlib2.trinity.autobuild.TrinityDataCoreStructureProviders;
import com.fish_dan_.data_energistics.gui.ldlib2.trinity.cpu.TrinityCpuStatusList;
import com.fish_dan_.data_energistics.gui.ldlib2.trinity.layout.TrinityUiNbtLayouts;
import com.fish_dan_.data_energistics.gui.ldlib2.trinity.layout.TrinityUiXmlLayouts;
import com.fish_dan_.data_energistics.gui.ldlib2.trinity.pattern.aggregate.TrinityAggregatePatternProvider;
import com.fish_dan_.data_energistics.gui.ldlib2.trinity.priority.TrinityPriorityProvider;
import com.fish_dan_.data_energistics.gui.ldlib2.trinity.storage.TrinityDataCoreStorageProvider;
import com.fish_dan_.data_energistics.menu.trinity.TrinityDataCoreMenu;

import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.Slot;

import com.lowdragmc.lowdraglib2.gui.holder.IModularUIHolderMenu;
import com.lowdragmc.lowdraglib2.gui.sync.bindings.IDataProvider;
import com.lowdragmc.lowdraglib2.gui.ui.UI;
import com.lowdragmc.lowdraglib2.gui.ui.UIElement;
import com.lowdragmc.lowdraglib2.gui.ui.elements.Button;
import com.lowdragmc.lowdraglib2.gui.ui.elements.ItemSlot;
import com.lowdragmc.lowdraglib2.gui.ui.elements.Label;
import com.lowdragmc.lowdraglib2.gui.ui.elements.Scroller;
import com.lowdragmc.lowdraglib2.gui.ui.elements.inventory.InventorySlots;
import dev.vfyjxf.taffy.style.TaffyPosition;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;
import java.util.function.IntSupplier;

/**
 * Mounts the double-sided LDLib2 host tree during Trinity Data Core menu construction.
 */
public final class TrinityDataCoreHostUi {

    public static final String ROOT_ID = "trinity_data_core_root";
    static final String TITLE_ID = "trinity_data_core_title";
    static final String PLAYER_INVENTORY_TITLE_ID = "trinity_data_core_player_inventory_title";
    static final String PLAYER_INVENTORY_ID = "trinity_data_core_player_inventory";
    static final String CPU_PANEL_ID = "trinity_data_core_cpu_panel";
    static final String CLOSE_ID = "trinity_data_core_close";

    private TrinityDataCoreHostUi() {}

    /**
     * Builds the complete root and lets LDLib2 create and register the native player inventory slots during mount.
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

        IModularUIHolderMenu holder = requireUnmountedMenu(menu);
        TrinityDataCoreUiSync sync = TrinityDataCoreUiSync.create(menu);
        UI ui = TrinityUiNbtLayouts.load("data_core");
        UIElement root = ui.rootElement;
        HostUiExtension hostUi = HostUiExtension.create(root);
        HostModularUI modularUI = null;
        try {
            registerProviders(menu, hostUi, sync);
            sync.setStorageWindowOpen(() -> hostUi.isOpen(TrinityDataCoreHostUiKeys.STORAGE));
            sync.setPatternWindowOpen(() -> hostUi.isOpen(TrinityDataCoreHostUiKeys.PATTERN));
            TrinityUiXmlLayouts.require(root, TITLE_ID, Label.class)
                    .setText(Component.translatable("block.data_energistics.trinity_data_core"));
            TrinityUiXmlLayouts.require(root, PLAYER_INVENTORY_TITLE_ID, Label.class)
                    .setText(Component.translatable("container.inventory"));
            InventorySlots playerInventorySlots = playerInventorySlots(root);
            TrinityDataCoreStatusPanel.bindExisting(
                    TrinityUiXmlLayouts.require(root, TrinityDataCoreStatusPanel.PANEL_ID, UIElement.class),
                    sync.hostStatusProvider(),
                    sync.storageStatusProvider(),
                    sync.cpuListStatusProvider(),
                    sync.coreTickNanosProvider());
            mountCpuList(
                    root,
                    cpuList(menu, sync.cpuListStatusProvider()));
            TrinityDataCoreHostLauncherPanel.bindExisting(root, hostUi);
            bindClose(root, menu);
            HostUiCoordinator coordinator = coordinatorFactory.apply(hostUi);
            if (coordinator == null || coordinator.hostUi() != hostUi) {
                throw new IllegalStateException("Trinity Data Core coordinator must own the mounted host extension");
            }
            modularUI = hostUi.createModularUI(ui, menu.getPlayer());
            sync.register(modularUI);
            mountNativePlayerInventory(menu, holder, modularUI, playerInventorySlots);
            return coordinator;
        } catch (RuntimeException | Error failure) {
            Data_Energistics.LOGGER.error(
                    "Failed to create the Trinity Data Core LDLib2 host UI; discard this menu and UI instance",
                    failure);
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

    private static InventorySlots playerInventorySlots(UIElement root) {
        return TrinityUiXmlLayouts.require(root, PLAYER_INVENTORY_ID, InventorySlots.class);
    }

    private static void mountCpuList(UIElement root, TrinityCpuStatusList cpuList) {
        UIElement panel = TrinityUiXmlLayouts.require(root, CPU_PANEL_ID, UIElement.class);
        Scroller.Vertical scrollbar = TrinityUiXmlLayouts.require(
                root,
                TrinityCpuStatusList.SCROLLER_ID,
                Scroller.Vertical.class);
        if (scrollbar.getParent() != panel) {
            throw mountViolation("editor-authored CPU scrollbar is not attached to the CPU panel");
        }
        int scrollbarIndex = panel.getChildren().indexOf(scrollbar);
        if (scrollbarIndex < 0) {
            throw mountViolation("editor-authored CPU scrollbar is missing from the CPU panel children");
        }

        cpuList.bindScrollbar(scrollbar);
        cpuList.layout(layout -> layout
                .positionType(TaffyPosition.ABSOLUTE)
                .left(-1)
                .top(0)
                .width(TrinityCpuStatusList.DEFAULT_WIDTH + 1)
                .height(TrinityCpuStatusList.DEFAULT_HEIGHT));
        panel.addChildAt(cpuList, scrollbarIndex);
        if (cpuList.getParent() != panel) {
            throw mountViolation("CPU list content was not attached to the expected panel");
        }
    }

    private static void bindClose(UIElement root, TrinityDataCoreMenu menu) {
        Button close = TrinityUiXmlLayouts.require(root, CLOSE_ID, Button.class);
        Component tooltip = Component.translatable("gui.close");
        close.setOnServerClick(event -> menu.getPlayer().closeContainer());
        close.text.style(style -> style.tooltips(tooltip));
        close.style(style -> style.tooltips(tooltip));
    }

    /**
     * Validates every invariant that can be checked before LDLib2 mutates the menu.
     */
    private static IModularUIHolderMenu requireUnmountedMenu(TrinityDataCoreMenu menu) {
        if (!(menu instanceof IModularUIHolderMenu holder)) {
            throw mountViolation("menu is not an IModularUIHolderMenu: " + menu.getClass().getName());
        }
        if (holder.getModularUI() != null) {
            throw mountViolation("menu already has a ModularUI: " + menu.getClass().getName());
        }
        if (!menu.slots.isEmpty()) {
            throw mountViolation("menu must not pre-create slots before InventorySlots mounts; found " +
                    menu.slots.size());
        }
        return holder;
    }

    /**
     * Mounts once, then proves the native slot order and both sides of LDLib2's slot mapping.
     */
    private static void mountNativePlayerInventory(TrinityDataCoreMenu menu,
                                                   IModularUIHolderMenu holder,
                                                   HostModularUI modularUI,
                                                   InventorySlots inventorySlots) {
        if (modularUI.getMenu() != null) {
            throw mountViolation("ModularUI is already attached to a menu");
        }
        if (modularUI.player != menu.getPlayer()) {
            throw mountViolation("ModularUI player does not own the Trinity Data Core menu");
        }
        List<ItemSlot> expectedSlots = orderedPlayerSlots(inventorySlots);
        if (expectedSlots.size() != 36) {
            throw mountViolation("Trinity Data Core UI must contain exactly 36 native player ItemSlots");
        }
        for (int menuIndex = 0; menuIndex < expectedSlots.size(); menuIndex++) {
            ItemSlot itemSlot = expectedSlots.get(menuIndex);
            if (!itemSlot.getChildren().isEmpty()) {
                throw mountViolation("native player ItemSlot '" + itemSlot.getId() + "' at menu index " +
                        menuIndex + " must be a leaf because child elements intercept vanilla slot hit-testing");
            }
        }

        holder.setModularUI(modularUI);

        if (holder.getModularUI() != modularUI || modularUI.getMenu() != menu) {
            throw mountViolation("LDLib2 did not establish the expected menu/UI references");
        }
        if (menu.slots.size() != expectedSlots.size()) {
            throw mountViolation("LDLib2 mounted " + menu.slots.size() + " menu slots instead of 36");
        }
        List<InventorySlots> inventories = modularUI.getElementsByType(InventorySlots.class);
        if (inventories.size() != 1 || inventories.getFirst() != inventorySlots) {
            throw mountViolation("Trinity Data Core UI must contain its exact single InventorySlots instance");
        }
        Inventory playerInventory = menu.getPlayer().getInventory();
        for (int menuIndex = 0; menuIndex < expectedSlots.size(); menuIndex++) {
            ItemSlot itemSlot = expectedSlots.get(menuIndex);
            Slot slot = itemSlot.getSlot();
            int inventoryIndex = menuIndex < 27 ? menuIndex + 9 : menuIndex - 27;
            if (menu.slots.get(menuIndex) != slot || slot.index != menuIndex) {
                throw mountViolation("native player slot order diverged at menu index " + menuIndex);
            }
            if (slot.container != playerInventory || slot.getContainerSlot() != inventoryIndex) {
                throw mountViolation("menu slot " + menuIndex + " is not bound to player inventory index " +
                        inventoryIndex);
            }
            if (holder.getItemSlot(slot) != itemSlot || itemSlot.getSlot() != slot) {
                throw mountViolation("LDLib2 did not retain the native ItemSlot mapping at menu index " +
                        menuIndex);
            }
        }
    }

    private static List<ItemSlot> orderedPlayerSlots(InventorySlots inventorySlots) {
        List<ItemSlot> slots = new ArrayList<>(36);
        for (InventorySlots.Row row : inventorySlots.rows) {
            slots.addAll(List.of(row.slots));
        }
        slots.addAll(List.of(inventorySlots.hotbar.slots));
        return slots;
    }

    private static IllegalStateException mountViolation(String message) {
        Data_Energistics.LOGGER.error("Trinity Data Core native LDLib2 mount invariant failed: {}", message);
        return new IllegalStateException(message);
    }

    private static TrinityCpuStatusList cpuList(TrinityDataCoreMenu menu,
                                                IDataProvider<TrinityCpuListStatus> statusProvider) {
        TrinityCpuStatusList cpuList = new TrinityCpuStatusList();
        cpuList.setOnCpuSelected(cpuNumber -> menu.sendOpenCpuStatus(menu.getHostId(), cpuNumber));
        cpuList.bindDataSource(statusProvider);
        return cpuList;
    }

    private static void registerProviders(TrinityDataCoreMenu menu,
                                          HostUiExtension hostUi,
                                          TrinityDataCoreUiSync sync) {
        IntSupplier modifierMask = menu.getPlayer().level().isClientSide() ?
                () -> DataEnergisticsClientBridgeAccess.get().priorityModifierMask() : () -> 0;
        hostUi.register(TrinityDataCoreStructureProviders.autoBuild(
                menu,
                menu::sendHostedAutoBuild,
                generation -> menu.isHostedActionPending(TrinityDataCoreHostUiKeys.AUTO_BUILD, generation)));
        hostUi.register(new TrinityDataCoreStorageProvider(
                sync.storageViewProvider(),
                sync::requestStoragePage,
                () -> hostUi.requestToggle(TrinityDataCoreHostUiKeys.STORAGE_PRIORITY)));
        hostUi.register(new TrinityAggregatePatternProvider(
                sync.patternViewProvider(),
                sync.patternMaintenanceProvider(),
                sync::requestPatternPage,
                menu.getPlayer().level(),
                menu::sendHostedPatternSlot,
                menu::sendHostedPatternQuickMove,
                menu::sendHostedPatternMigration,
                () -> hostUi.requestToggle(TrinityDataCoreHostUiKeys.PATTERN_PRIORITY),
                menu::sendRefundPatterns,
                menu::sendRefundRetainedItems));
        hostUi.register(priorityProvider(
                menu,
                TrinityDataCoreHostUiKeys.STORAGE_PRIORITY,
                "trinity_storage_priority_window",
                "screen.data_energistics.trinity_data_core.storage.priority.title",
                "screen.data_energistics.trinity_data_core.storage.priority.insert_hint",
                "screen.data_energistics.trinity_data_core.storage.priority.extract_hint",
                sync.storagePriorityProvider(),
                modifierMask));
        hostUi.register(priorityProvider(
                menu,
                TrinityDataCoreHostUiKeys.PATTERN_PRIORITY,
                "trinity_pattern_priority_window",
                "screen.data_energistics.trinity_data_core.pattern.priority.title",
                "screen.data_energistics.trinity_data_core.pattern.priority.selection_hint",
                "screen.data_energistics.trinity_data_core.pattern.priority.scope_hint",
                sync.patternPriorityProvider(),
                modifierMask));
        if (!hostUi.registeredKeys().equals(TrinityDataCoreHostUiKeys.registrationOrder())) {
            throw new IllegalStateException("Trinity Data Core hosted providers were registered out of order");
        }
    }

    private static TrinityPriorityProvider priorityProvider(TrinityDataCoreMenu menu,
                                                            HostUiKey key,
                                                            String windowId,
                                                            String titleKey,
                                                            String firstHintKey,
                                                            String secondHintKey,
                                                            IDataProvider<Integer> priority,
                                                            IntSupplier modifierMask) {
        return new TrinityPriorityProvider(
                key,
                windowId,
                Component.translatable(titleKey),
                Component.translatable(firstHintKey),
                Component.translatable(secondHintKey),
                priority,
                modifierMask,
                (generation, operation) -> menu.sendHostedPriority(key, generation, operation),
                generation -> menu.isHostedActionPending(key, generation));
    }
}

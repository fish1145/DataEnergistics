package com.fish_dan_.data_energistics.gui.ldlib2.trinity;

import com.fish_dan_.data_energistics.Data_Energistics;
import com.fish_dan_.data_energistics.common.crafting.trinity.status.TrinityCpuListStatus;
import com.fish_dan_.data_energistics.common.trinity.host.TrinityDataCoreHostStatus;
import com.fish_dan_.data_energistics.gui.ldlib2.HostModularUI;
import com.fish_dan_.data_energistics.gui.ldlib2.HostUiCoordinator;
import com.fish_dan_.data_energistics.gui.ldlib2.HostUiExtension;
import com.fish_dan_.data_energistics.menu.TrinityDataCoreMenu;

import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.Slot;

import com.lowdragmc.lowdraglib2.gui.holder.IModularUIHolderMenu;
import com.lowdragmc.lowdraglib2.gui.sync.bindings.IDataProvider;
import com.lowdragmc.lowdraglib2.gui.ui.UI;
import com.lowdragmc.lowdraglib2.gui.ui.UIElement;
import com.lowdragmc.lowdraglib2.gui.ui.elements.ItemSlot;
import com.lowdragmc.lowdraglib2.gui.ui.elements.Label;
import com.lowdragmc.lowdraglib2.gui.ui.elements.inventory.InventorySlots;

import java.util.ArrayList;
import java.util.List;
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
    static final String PLAYER_INVENTORY_ID = "trinity_data_core_player_inventory";

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
        UI ui = TrinityUiXmlLayouts.load("data_core");
        UIElement root = ui.rootElement;
        HostUiExtension hostUi = HostUiExtension.create(root);
        HostModularUI modularUI = null;
        try {
            registerProviders(menu, hostUi);
            TrinityUiXmlLayouts.require(root, TITLE_ID, Label.class)
                    .setText(Component.translatable("block.data_energistics.trinity_data_core"));
            TrinityUiXmlLayouts.require(root, PLAYER_INVENTORY_TITLE_ID, Label.class)
                    .setText(Component.translatable("container.inventory"));
            root.addChild(TrinityDataCoreStatusPanel.create(sync.hostStatusProvider()));
            root.addChild(TrinityDataCoreStoragePanel.create(sync.storageStatusProvider()));
            InventorySlots playerInventorySlots = playerInventorySlots(root);
            root.addChild(cpuList(menu, sync.cpuListStatusProvider(), sync.hostStatusProvider()));
            root.addChild(TrinityDataCoreHostLauncherPanel.create(hostUi));
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

    /** Validates every invariant that can be checked before LDLib2 mutates the menu. */
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

    /** Mounts once, then proves the native slot order and both sides of LDLib2's slot mapping. */
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
        List<ItemSlot> mountedSlots = modularUI.getElementsByType(ItemSlot.class);
        if (mountedSlots.size() != expectedSlots.size()) {
            throw mountViolation("LDLib2 did not mount the complete native player ItemSlot tree");
        }
        for (int index = 0; index < expectedSlots.size(); index++) {
            if (mountedSlots.get(index) != expectedSlots.get(index)) {
                throw mountViolation("LDLib2 reordered the native player ItemSlot tree at index " + index);
            }
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
                                                IDataProvider<TrinityCpuListStatus> statusProvider,
                                                IDataProvider<TrinityDataCoreHostStatus> hostStatusProvider) {
        TrinityCpuStatusList cpuList = new TrinityCpuStatusList();
        cpuList.setOnCpuSelected(cpuNumber -> dispatchCpuSelection(
                hostStatusProvider.getValue(),
                cpuNumber,
                menu::sendOpenCpuStatus));
        cpuList.bindDataSource(statusProvider);
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

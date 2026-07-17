package com.fish_dan_.data_energistics.gui.ldlib2.trinity;

import com.fish_dan_.data_energistics.Data_Energistics;
import com.fish_dan_.data_energistics.common.crafting.trinity.TrinityCpuListStatus;
import com.fish_dan_.data_energistics.common.trinity.TrinityDataCoreHostStatus;
import com.fish_dan_.data_energistics.gui.ldlib2.HostModularUI;
import com.fish_dan_.data_energistics.gui.ldlib2.HostUiCoordinator;
import com.fish_dan_.data_energistics.gui.ldlib2.HostUiExtension;
import com.fish_dan_.data_energistics.menu.TrinityDataCoreMenu;

import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.Slot;

import com.lowdragmc.lowdraglib2.gui.holder.IModularUIHolderMenu;
import com.lowdragmc.lowdraglib2.gui.sync.bindings.IDataProvider;
import com.lowdragmc.lowdraglib2.gui.texture.ColorBorderTexture;
import com.lowdragmc.lowdraglib2.gui.texture.ColorRectTexture;
import com.lowdragmc.lowdraglib2.gui.texture.GuiTextureGroup;
import com.lowdragmc.lowdraglib2.gui.texture.IGuiTexture;
import com.lowdragmc.lowdraglib2.gui.texture.SpriteTexture;
import com.lowdragmc.lowdraglib2.gui.ui.UI;
import com.lowdragmc.lowdraglib2.gui.ui.UIElement;
import com.lowdragmc.lowdraglib2.gui.ui.elements.ItemSlot;
import com.lowdragmc.lowdraglib2.gui.ui.elements.Label;
import com.lowdragmc.lowdraglib2.gui.ui.elements.inventory.InventorySlots;
import dev.vfyjxf.taffy.style.TaffyPosition;

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
    private static final int WIDTH = 256;
    private static final int HEIGHT = 212;
    static final int CPU_LIST_LEFT = 168;
    static final int CPU_LIST_TOP = 129;
    private static final int PLAYER_INVENTORY_LEFT = 5;
    private static final int PLAYER_INVENTORY_TOP = 129;
    private static final int PLAYER_INVENTORY_WIDTH = 162;
    private static final int PLAYER_INVENTORY_HEIGHT = 76;
    private static final int PLAYER_HOTBAR_MARGIN_TOP = 4;
    private static final IGuiTexture ROOT_BACKGROUND = GuiTextureGroup.of(
            new ColorRectTexture(0xFFE3E3EA),
            new ColorBorderTexture(-1, 0xFF696D88));
    private static final IGuiTexture PLAYER_SLOT_BACKGROUND = GuiTextureGroup.of(
            SpriteTexture.of("data_energistics:textures/guis/trinity_data_core/inventory_slot.png")
                    .setSprite(0, 0, 16, 16),
            new ColorBorderTexture(-1, 0xFF696D88));

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
                    PLAYER_INVENTORY_LEFT + 1,
                    PLAYER_INVENTORY_TOP - 10,
                    PLAYER_INVENTORY_WIDTH));
            root.addChild(TrinityDataCoreStatusPanel.create(sync.hostStatusProvider()));
            root.addChild(TrinityDataCoreStoragePanel.create(sync.storageStatusProvider()));
            InventorySlots playerInventorySlots = playerInventorySlots();
            root.addChild(playerInventorySlots);
            root.addChild(cpuList(menu, sync.cpuListStatusProvider(), sync.hostStatusProvider()));
            root.addChild(TrinityDataCoreHostLauncherPanel.create(hostUi));
            HostUiCoordinator coordinator = coordinatorFactory.apply(hostUi);
            if (coordinator == null || coordinator.hostUi() != hostUi) {
                throw new IllegalStateException("Trinity Data Core coordinator must own the mounted host extension");
            }
            modularUI = hostUi.createModularUI(UI.of(root), menu.getPlayer());
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

    private static InventorySlots playerInventorySlots() {
        InventorySlots inventorySlots = new InventorySlots();
        inventorySlots.setId(PLAYER_INVENTORY_ID);
        inventorySlots.hotbar.getLayout().marginTop(PLAYER_HOTBAR_MARGIN_TOP);
        inventorySlots.apply(slot -> slot.getStyle().backgroundTexture(PLAYER_SLOT_BACKGROUND));
        inventorySlots.layout(layout -> layout
                .positionType(TaffyPosition.ABSOLUTE)
                .left(PLAYER_INVENTORY_LEFT)
                .top(PLAYER_INVENTORY_TOP)
                .width(PLAYER_INVENTORY_WIDTH)
                .height(PLAYER_INVENTORY_HEIGHT));
        return inventorySlots;
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

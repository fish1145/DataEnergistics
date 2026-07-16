package com.fish_dan_.data_energistics.client.ui.machine;

import com.fish_dan_.data_energistics.Data_Energistics;
import com.fish_dan_.data_energistics.client.gui.DataEnergisticsIcon;
import com.fish_dan_.data_energistics.client.ui.DataReassemblerProgressElement;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

import appeng.client.gui.Icon;
import appeng.client.gui.StackWithBounds;
import appeng.core.localization.ButtonToolTips;
import appeng.core.localization.GuiText;
import com.lowdragmc.lowdraglib2.gui.holder.IModularUIHolderMenu;
import com.lowdragmc.lowdraglib2.gui.texture.ColorBorderTexture;
import com.lowdragmc.lowdraglib2.gui.texture.ColorRectTexture;
import com.lowdragmc.lowdraglib2.gui.texture.GuiTextureGroup;
import com.lowdragmc.lowdraglib2.gui.texture.IGuiTexture;
import com.lowdragmc.lowdraglib2.gui.texture.SpriteTexture;
import com.lowdragmc.lowdraglib2.gui.ui.ModularUI;
import com.lowdragmc.lowdraglib2.gui.ui.UI;
import com.lowdragmc.lowdraglib2.gui.ui.UIElement;
import com.lowdragmc.lowdraglib2.gui.ui.elements.ItemSlot;
import com.lowdragmc.lowdraglib2.gui.ui.elements.Label;
import com.lowdragmc.lowdraglib2.gui.ui.event.HoverTooltips;
import com.lowdragmc.lowdraglib2.gui.ui.event.UIEvents;
import com.lowdragmc.lowdraglib2.gui.ui.utils.IModularUIProvider;
import dev.vfyjxf.taffy.style.FlexDirection;
import dev.vfyjxf.taffy.style.TaffyPosition;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * Builds one fresh fixed-layout LDLib2 tree for an open data reassembler menu.
 */
public final class DataRipperReassemblerMachineUiProviderImpl
                                                              implements IModularUIProvider<DataRipperReassemblerMachineUiState> {

    private static final int WIDTH = 176;
    private static final int HEIGHT = 183;
    private static final int UPGRADE_PANEL_X = 174;
    private static final int UPGRADE_PANEL_Y = 0;
    private static final int VIEWER_EXCLUSION_MARGIN = 2;
    private static final ResourceLocation BACKGROUND = ResourceLocation.fromNamespaceAndPath(
            "ae2",
            "textures/guis/data_reassembler.png");
    private static final ResourceLocation EXTRA_PANELS = ResourceLocation.fromNamespaceAndPath(
            "ae2",
            "textures/guis/extra_panels.png");
    private static final IGuiTexture SLOT_HOVER = GuiTextureGroup.of(
            new ColorRectTexture(0x6693FFDE),
            new ColorBorderTexture(1, 0xFF57E0BB));

    private final List<ItemSlot> mappedSlots = new ArrayList<>();
    private final List<DataReassemblerGenericSlotElement> genericSlots = new ArrayList<>();
    private DataRipperReassemblerMachineUiState state;
    private UIElement root;
    private boolean dialogOpen;

    @Override
    public ModularUI createModularUI(DataRipperReassemblerMachineUiState state) {
        if (this.root != null) {
            Data_Energistics.LOGGER.error("A data reassembler UI provider was reused after creating its ModularUI");
            throw new IllegalStateException("Data reassembler UI providers create exactly one ModularUI");
        }
        this.state = state;
        this.root = new UIElement();
        this.root.getLayout().width(WIDTH);
        this.root.getLayout().height(HEIGHT);
        this.root.style(style -> style.backgroundTexture(
                SpriteTexture.of(BACKGROUND).setSprite(0, 0, WIDTH, HEIGHT)));

        addLabels();
        addMachineSlots();
        addPlayerSlots();
        addUpgradePanel();
        addToolboxPanel();
        addProgress();
        addToolbar();
        return ModularUI.of(UI.of(this.root));
    }

    /** Maps every existing vanilla slot to its LDLib2 element without adding it to the menu a second time. */
    public void mapExistingSlots(IModularUIHolderMenu holder) {
        if (this.root == null) {
            Data_Energistics.LOGGER.error("Cannot map data reassembler slots before creating its ModularUI");
            throw new IllegalStateException("The ModularUI must be created before mapping its existing slots");
        }
        for (ItemSlot itemSlot : this.mappedSlots) {
            ItemSlot existing = holder.ldlib2$getItemSlot(itemSlot.getSlot());
            if (existing != null && existing != itemSlot) {
                Data_Energistics.LOGGER.error("Menu slot {} is already mapped to another LDLib2 ItemSlot", itemSlot.getSlot().index);
                throw new IllegalStateException("Duplicate LDLib2 mapping for an existing data reassembler slot");
            }
            holder.ldlib2$addSlot(itemSlot);
        }
    }

    /** Reapplies calculated LDLib2 content coordinates to every mapped vanilla slot after screen initialization. */
    public void updateMappedSlotPositions() {
        if (this.root == null || this.root.getModularUI() == null) {
            Data_Energistics.LOGGER.error("Cannot update data reassembler slot positions before ModularUI initialization");
            throw new IllegalStateException("The ModularUI must be initialized before updating mapped slot positions");
        }
        for (ItemSlot itemSlot : this.mappedSlots) {
            itemSlot.updateSlotPosition();
        }
    }

    /** Returns the generic stack rendered below the pointer unless the output dialog is modal. */
    public @Nullable StackWithBounds getGenericStackUnderMouse(double mouseX, double mouseY) {
        if (this.dialogOpen) {
            return null;
        }
        for (DataReassemblerGenericSlotElement slot : this.genericSlots) {
            StackWithBounds stack = slot.stackUnderMouse(mouseX, mouseY);
            if (stack != null) {
                return stack;
            }
        }
        return null;
    }

    private void addLabels() {
        this.root.addChild(position(label(this.state.title()), 8, 6, 160, 9));
        this.root.addChild(position(label(this.state.inventoryTitle()), 8, 89, 160, 9));
    }

    private void addMachineSlots() {
        List<Slot> inputs = requireCount(DataRipperReassemblerMachineUiState.SlotGroup.ITEM_INPUT, 9);
        for (int i = 0; i < inputs.size(); i++) {
            addItemSlot(this.root, inputs.get(i), 19 + i % 3 * 18, 22 + i / 3 * 18, false);
        }

        addGenericSlot(DataRipperReassemblerMachineUiState.GenericStorage.FLUID_INPUT_A, 74, 22);
        addGenericSlot(DataRipperReassemblerMachineUiState.GenericStorage.KEY_INPUT, 74, 40);
        addGenericSlot(DataRipperReassemblerMachineUiState.GenericStorage.FLUID_INPUT_B, 74, 58);

        addItemSlot(this.root, singleSlot(DataRipperReassemblerMachineUiState.SlotGroup.ITEM_OUTPUT_A), 125, 22, false);
        addItemSlot(this.root, singleSlot(DataRipperReassemblerMachineUiState.SlotGroup.ITEM_OUTPUT_B), 125, 40, false);
        addItemSlot(this.root, singleSlot(DataRipperReassemblerMachineUiState.SlotGroup.ITEM_OUTPUT_C), 125, 58, false);
        addGenericSlot(DataRipperReassemblerMachineUiState.GenericStorage.FLUID_OUTPUT_A, 143, 22);
        addGenericSlot(DataRipperReassemblerMachineUiState.GenericStorage.KEY_OUTPUT, 143, 40);
        addGenericSlot(DataRipperReassemblerMachineUiState.GenericStorage.FLUID_OUTPUT_B, 143, 58);
    }

    private void addPlayerSlots() {
        List<Slot> inventory = requireCount(DataRipperReassemblerMachineUiState.SlotGroup.PLAYER_INVENTORY, 27);
        for (int i = 0; i < inventory.size(); i++) {
            addItemSlot(this.root, inventory.get(i), 8 + i % 9 * 18, 99 + i / 9 * 18, true);
        }
        List<Slot> hotbar = requireCount(DataRipperReassemblerMachineUiState.SlotGroup.PLAYER_HOTBAR, 9);
        for (int i = 0; i < hotbar.size(); i++) {
            addItemSlot(this.root, hotbar.get(i), 8 + i * 18, 157, true);
        }
    }

    private void addUpgradePanel() {
        List<Slot> upgrades = this.state.slots(DataRipperReassemblerMachineUiState.SlotGroup.UPGRADE);
        if (upgrades.isEmpty()) {
            return;
        }
        var panel = new DataReassemblerUpgradePanelElement(upgrades.size(), this.state::compatibleUpgradeTooltip);
        var exclusion = new UIElement();
        exclusion.setAllowHitTest(false);
        position(
                exclusion,
                UPGRADE_PANEL_X - VIEWER_EXCLUSION_MARGIN,
                UPGRADE_PANEL_Y - VIEWER_EXCLUSION_MARGIN,
                DataReassemblerUpgradePanelElement.widthForSlots(upgrades.size()) + VIEWER_EXCLUSION_MARGIN * 2,
                DataReassemblerUpgradePanelElement.heightForSlots(upgrades.size()) + VIEWER_EXCLUSION_MARGIN * 2);
        position(panel, VIEWER_EXCLUSION_MARGIN, VIEWER_EXCLUSION_MARGIN);
        for (int i = 0; i < upgrades.size(); i++) {
            var itemSlot = new DataReassemblerUpgradePanelElement.UpgradeSlot(upgrades.get(i));
            position(itemSlot, 1 + i / 8 * 18, 6 + i % 8 * 18, 16, 16);
            panel.addChild(itemSlot);
            this.mappedSlots.add(itemSlot);
        }
        exclusion.addChild(panel);
        this.root.addChild(exclusion);
    }

    private void addToolboxPanel() {
        List<Slot> toolboxSlots = this.state.slots(DataRipperReassemblerMachineUiState.SlotGroup.TOOLBOX);
        if (toolboxSlots.isEmpty()) {
            return;
        }
        requireCount(DataRipperReassemblerMachineUiState.SlotGroup.TOOLBOX, 9);
        Component toolboxName = this.state.toolboxName();
        var panel = new UIElement().style(style -> style.backgroundTexture(
                SpriteTexture.of(EXTRA_PANELS).setSprite(69, 62, 59, 66)));
        position(panel, 174, 93, 59, 66);
        panel.addEventListener(UIEvents.HOVER_TOOLTIPS, event -> event.hoverTooltips = new HoverTooltips(
                List.of(
                        toolboxName,
                        GuiText.UpgradeToolbelt.text().plainCopy().withStyle(ChatFormatting.GRAY)),
                null,
                null,
                ItemStack.EMPTY));
        for (int i = 0; i < toolboxSlots.size(); i++) {
            addItemSlot(panel, toolboxSlots.get(i), 1 + i % 3 * 18, 6 + i / 3 * 18, true);
        }
        this.root.addChild(panel);
    }

    private void addProgress() {
        var progress = new DataReassemblerProgressElement(
                BACKGROUND,
                176,
                0,
                6,
                18,
                256,
                256,
                () -> this.state.hasProgressRange() ? this.state.progressFraction() : 0.0D);
        position(progress, 164, 39, 6, 18);
        progress.setDisplay(this.state.hasProgressRange());
        progress.addEventListener(UIEvents.HOVER_TOOLTIPS, event -> event.hoverTooltips = new HoverTooltips(
                List.of(Component.literal(this.state.progressPercent() + "%")),
                null,
                null,
                ItemStack.EMPTY));
        this.root.addEventListener(UIEvents.TICK, event -> progress.setDisplay(this.state.hasProgressRange()));
        this.root.addChild(progress);
    }

    private void addToolbar() {
        var toolbar = new UIElement();
        toolbar.getLayout().positionType(TaffyPosition.ABSOLUTE);
        toolbar.getLayout().left(-15);
        toolbar.getLayout().top(3);
        toolbar.getLayout().width(16);
        toolbar.getLayout().flexDirection(FlexDirection.COLUMN);
        toolbar.getLayout().gapAll(6);

        if (this.state.hasHelp()) {
            toolbar.addChild(new DataReassemblerIconButtonElement(
                    Icon.HELP::getBlitter,
                    () -> ItemStack.EMPTY,
                    () -> List.of(ButtonToolTips.OpenGuide.text(), ButtonToolTips.OpenGuideDetail.text()),
                    () -> false,
                    false,
                    this.state::openHelp));
        }

        toolbar.addChild(new DataReassemblerIconButtonElement(
                () -> (this.state.isAutoExportEnabled() ? Icon.AUTO_EXPORT_ON : Icon.AUTO_EXPORT_OFF).getBlitter(),
                () -> ItemStack.EMPTY,
                () -> List.of(
                        ButtonToolTips.AutoExport.text(),
                        (this.state.isAutoExportEnabled() ? ButtonToolTips.AutoExportOn : ButtonToolTips.AutoExportOff).text()),
                this.state::isAutoExportEnabled,
                false,
                this::toggleAutoExport));

        var outputButton = new DataReassemblerIconButtonElement(
                () -> DataEnergisticsIcon.getBlitter("PLACEMENT_TOOLBOX"),
                () -> ItemStack.EMPTY,
                () -> List.of(Component.translatable("gui.data_energistics.set_output_sides.open")),
                () -> false,
                false,
                this::openOutputDialog);
        outputButton.setDisplay(this.state.isAutoExportEnabled());
        toolbar.addChild(outputButton);
        this.root.addEventListener(UIEvents.TICK, event -> outputButton.setDisplay(this.state.isAutoExportEnabled()));
        this.root.addChild(toolbar);
    }

    void openOutputDialog() {
        if (this.dialogOpen) {
            return;
        }
        this.dialogOpen = true;
        new DataReassemblerOutputSideDialog(this.state, () -> this.dialogOpen = false).show(this.root);
    }

    /** Reports whether Escape should be routed to the modal output-side dialog instead of closing its Screen. */
    public boolean isOutputDialogOpen() {
        return this.dialogOpen;
    }

    void toggleAutoExport() {
        this.state.setAutoExportEnabled(!this.state.isAutoExportEnabled());
    }

    private void addGenericSlot(
                                DataRipperReassemblerMachineUiState.GenericStorage storage,
                                int contentX,
                                int contentY) {
        DataRipperReassemblerMachineUiState.SlotGroup group = switch (storage) {
            case FLUID_INPUT_A -> DataRipperReassemblerMachineUiState.SlotGroup.FLUID_INPUT_A;
            case FLUID_INPUT_B -> DataRipperReassemblerMachineUiState.SlotGroup.FLUID_INPUT_B;
            case KEY_INPUT -> DataRipperReassemblerMachineUiState.SlotGroup.KEY_INPUT;
            case FLUID_OUTPUT_A -> DataRipperReassemblerMachineUiState.SlotGroup.FLUID_OUTPUT_A;
            case FLUID_OUTPUT_B -> DataRipperReassemblerMachineUiState.SlotGroup.FLUID_OUTPUT_B;
            case KEY_OUTPUT -> DataRipperReassemblerMachineUiState.SlotGroup.KEY_OUTPUT;
        };
        var element = new DataReassemblerGenericSlotElement(
                singleSlot(group),
                storage,
                () -> this.state.genericStack(storage),
                () -> this.state.capacity(storage));
        position(element, contentX, contentY, 16, 16);
        this.root.addChild(element);
        this.genericSlots.add(element);
        this.mappedSlots.add(element);
    }

    private void addItemSlot(UIElement parent, Slot slot, int contentX, int contentY, boolean playerSlot) {
        var element = new ItemSlot(slot);
        configureSlot(element, playerSlot);
        position(element, contentX, contentY, 16, 16);
        parent.addChild(element);
        this.mappedSlots.add(element);
    }

    static void configureSlot(ItemSlot slot, boolean playerSlot) {
        slot.getLayout().paddingAll(0);
        slot.style(style -> style.backgroundTexture(IGuiTexture.EMPTY));
        slot.slotStyle(style -> style
                .isPlayerSlot(playerSlot)
                .hoverOverlay(SLOT_HOVER));
    }

    private Slot singleSlot(DataRipperReassemblerMachineUiState.SlotGroup group) {
        return requireCount(group, 1).getFirst();
    }

    private List<Slot> requireCount(DataRipperReassemblerMachineUiState.SlotGroup group, int expected) {
        List<Slot> slots = this.state.slots(group);
        if (slots.size() != expected) {
            Data_Energistics.LOGGER.error("Expected {} data reassembler slots for {}, found {}", expected, group, slots.size());
            throw new IllegalStateException("Unexpected data reassembler slot count for " + group);
        }
        return slots;
    }

    private static Label label(Component component) {
        return (Label) new Label()
                .setText(component)
                .textStyle(style -> style.textColor(0x404040).textShadow(false));
    }

    private static <T extends UIElement> T position(T element, int x, int y) {
        element.getLayout().positionType(TaffyPosition.ABSOLUTE);
        element.getLayout().left(x);
        element.getLayout().top(y);
        return element;
    }

    private static <T extends UIElement> T position(T element, int x, int y, int width, int height) {
        position(element, x, y);
        element.getLayout().width(width);
        element.getLayout().height(height);
        return element;
    }
}

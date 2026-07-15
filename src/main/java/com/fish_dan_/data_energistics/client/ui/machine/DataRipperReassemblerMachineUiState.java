package com.fish_dan_.data_energistics.client.ui.machine;

import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

import appeng.api.orientation.RelativeSide;
import appeng.api.stacks.GenericStack;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * Exposes the synchronized data and existing menu slots needed to render the data reassembler UI.
 */
public interface DataRipperReassemblerMachineUiState {

    /** Identifies existing menu slot groups without exposing their AE2 semantic constants to UI elements. */
    enum SlotGroup {
        ITEM_INPUT,
        FLUID_INPUT_A,
        FLUID_INPUT_B,
        KEY_INPUT,
        ITEM_OUTPUT_A,
        ITEM_OUTPUT_B,
        ITEM_OUTPUT_C,
        FLUID_OUTPUT_A,
        FLUID_OUTPUT_B,
        KEY_OUTPUT,
        PLAYER_INVENTORY,
        PLAYER_HOTBAR,
        UPGRADE,
        TOOLBOX
    }

    /** Identifies the six non-item storage slots shown by the machine. */
    enum GenericStorage {

        FLUID_INPUT_A(true, true),
        FLUID_INPUT_B(true, true),
        KEY_INPUT(false, true),
        FLUID_OUTPUT_A(true, false),
        FLUID_OUTPUT_B(true, false),
        KEY_OUTPUT(false, false);

        private final boolean fluid;
        private final boolean input;

        GenericStorage(boolean fluid, boolean input) {
            this.fluid = fluid;
            this.input = input;
        }

        /** Returns whether amounts for this storage use milli-buckets. */
        public boolean isFluid() {
            return this.fluid;
        }

        /** Returns whether this storage accepts machine inputs. */
        public boolean isInput() {
            return this.input;
        }
    }

    /** Returns the title supplied by the active menu. */
    Component title();

    /** Returns the localized player inventory title. */
    Component inventoryTitle();

    /** Returns the existing slots associated with the requested layout group. */
    List<Slot> slots(SlotGroup group);

    /** Returns the synchronized stack displayed in a generic storage slot, or {@code null} when empty. */
    @Nullable
    GenericStack genericStack(GenericStorage storage);

    /** Returns the capacity displayed in the requested generic storage tooltip. */
    long capacity(GenericStorage storage);

    /** Returns whether the progress bar should be displayed. */
    boolean hasProgressRange();

    /** Returns the validated current progress fraction in the inclusive range 0..1. */
    double progressFraction();

    /** Returns the current progress percentage used by the hover tooltip. */
    int progressPercent();

    /** Returns the synchronized automatic-export setting. */
    boolean isAutoExportEnabled();

    /** Sends the existing automatic-export client action after a direct user click. */
    void setAutoExportEnabled(boolean enabled);

    /** Returns whether an absolute side is enabled in the synchronized output mask. */
    boolean isOutputSideEnabled(Direction side);

    /** Sends the existing output-side client action after a direct user click. */
    void setOutputSideEnabled(Direction side, boolean enabled);

    /** Resolves a UI-relative side using the host block entity orientation. */
    Direction resolveSide(RelativeSide side);

    /** Returns the neighboring block or cable-part item rendered for an absolute side. */
    ItemStack outputSideIcon(Direction side);

    /** Returns the machine item name used by the output dialog's return button. */
    Component machineName();

    /** Returns whether GuideME indexed a help topic for this machine. */
    boolean hasHelp();

    /** Opens the automatically indexed GuideME topic for the current player. */
    void openHelp();

    /** Returns the complete tooltip shown by the upgrade panel. */
    List<Component> compatibleUpgradeTooltip();

    /** Returns the attached network-tool inventory name; called only while toolbox slots are present. */
    Component toolboxName();
}

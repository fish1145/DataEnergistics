package com.fish_dan_.data_energistics.client.ui.machine;

import com.fish_dan_.data_energistics.Data_Energistics;
import com.fish_dan_.data_energistics.blockentity.DataRipperReassemblerBlockEntity;
import com.fish_dan_.data_energistics.menu.DataRipperReassemblerMenu;

import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.neoforged.neoforge.fluids.FluidStack;

import appeng.api.config.YesNo;
import appeng.api.orientation.BlockOrientation;
import appeng.api.orientation.RelativeSide;
import appeng.api.parts.IPart;
import appeng.api.stacks.AEFluidKey;
import appeng.api.stacks.GenericStack;
import appeng.api.upgrades.Upgrades;
import appeng.blockentity.networking.CableBusBlockEntity;
import appeng.core.AppEngClient;
import appeng.core.localization.GuiText;
import appeng.items.tools.GuideItem;
import appeng.menu.SlotSemantic;
import appeng.menu.SlotSemantics;
import guideme.GuidesCommon;
import guideme.PageAnchor;
import guideme.indices.ItemIndex;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * Reads machine UI state exclusively from the existing menu synchronization fields and client actions.
 */
public final class DataRipperReassemblerMachineUiStateImpl implements DataRipperReassemblerMachineUiState {

    private final DataRipperReassemblerMenu menu;
    private final Inventory playerInventory;
    private final Component title;
    private final DataRipperReassemblerBlockEntity host;
    private final PageAnchor helpTopic;

    /** Creates state for one open menu and validates that its client-side host is available. */
    public DataRipperReassemblerMachineUiStateImpl(
                                                   DataRipperReassemblerMenu menu,
                                                   Inventory playerInventory,
                                                   Component title) {
        this.menu = menu;
        this.playerInventory = playerInventory;
        this.title = title;
        if (menu.getHost() == null) {
            Data_Energistics.LOGGER.error("Cannot create data reassembler ModularUI without a menu host");
            throw new IllegalStateException("Data reassembler menu host is missing");
        }
        this.host = menu.getHost();
        this.helpTopic = findHelpTopic(this.host);
    }

    @Override
    public Component title() {
        return this.title;
    }

    @Override
    public Component inventoryTitle() {
        return this.playerInventory.getDisplayName();
    }

    @Override
    public List<Slot> slots(SlotGroup group) {
        SlotSemantic semantic = switch (group) {
            case ITEM_INPUT -> SlotSemantics.MACHINE_INPUT;
            case FLUID_INPUT_A -> SlotSemantics.STORAGE;
            case FLUID_INPUT_B -> DataRipperReassemblerMenu.FLUID_INPUT_B;
            case KEY_INPUT -> DataRipperReassemblerMenu.KEY_INPUT;
            case ITEM_OUTPUT_A -> SlotSemantics.MACHINE_OUTPUT;
            case ITEM_OUTPUT_B -> DataRipperReassemblerMenu.ITEM_OUTPUT_B;
            case ITEM_OUTPUT_C -> DataRipperReassemblerMenu.ITEM_OUTPUT_C;
            case FLUID_OUTPUT_A -> DataRipperReassemblerMenu.FLUID_OUTPUT_A;
            case FLUID_OUTPUT_B -> DataRipperReassemblerMenu.FLUID_OUTPUT_B;
            case KEY_OUTPUT -> DataRipperReassemblerMenu.KEY_OUTPUT;
            case PLAYER_INVENTORY -> SlotSemantics.PLAYER_INVENTORY;
            case PLAYER_HOTBAR -> SlotSemantics.PLAYER_HOTBAR;
            case UPGRADE -> SlotSemantics.UPGRADE;
            case TOOLBOX -> SlotSemantics.TOOLBOX;
        };
        return List.copyOf(this.menu.getSlots(semantic));
    }

    @Override
    public @Nullable GenericStack genericStack(GenericStorage storage) {
        return switch (storage) {
            case FLUID_INPUT_A -> decodeFluid(this.menu.fluidInputAId, this.menu.fluidInputAAmount, storage);
            case FLUID_INPUT_B -> decodeFluid(this.menu.fluidInputBId, this.menu.fluidInputBAmount, storage);
            case FLUID_OUTPUT_A -> decodeFluid(this.menu.fluidOutputAId, this.menu.fluidOutputAAmount, storage);
            case FLUID_OUTPUT_B -> decodeFluid(this.menu.fluidOutputBId, this.menu.fluidOutputBAmount, storage);
            case KEY_INPUT -> decodeKey(singleSlot(SlotGroup.KEY_INPUT));
            case KEY_OUTPUT -> decodeKey(singleSlot(SlotGroup.KEY_OUTPUT));
        };
    }

    @Override
    public long capacity(GenericStorage storage) {
        if (storage.isFluid()) {
            return storage.isInput() ? this.menu.getFluidInputCapacity() : this.menu.getFluidOutputCapacity();
        }
        return storage.isInput() ? this.menu.getKeyInputCapacity() : this.menu.getKeyOutputCapacity();
    }

    @Override
    public boolean hasProgressRange() {
        int maxProgress = this.menu.getMaxProgress();
        validateProgress(this.menu.getCurrentProgress(), maxProgress);
        return maxProgress > 0;
    }

    @Override
    public double progressFraction() {
        return validateProgress(this.menu.getCurrentProgress(), this.menu.getMaxProgress());
    }

    @Override
    public int progressPercent() {
        int progress = this.menu.getCurrentProgress();
        int maxProgress = this.menu.getMaxProgress();
        validateProgress(progress, maxProgress);
        return maxProgress == 0 ? 0 : (int) ((long) progress * 100L / maxProgress);
    }

    @Override
    public boolean isAutoExportEnabled() {
        return this.menu.getAutoExport() == YesNo.YES;
    }

    @Override
    public void setAutoExportEnabled(boolean enabled) {
        this.menu.autoExport = enabled ? YesNo.YES : YesNo.NO;
        this.menu.sendSetAutoExport(enabled);
    }

    @Override
    public boolean isOutputSideEnabled(Direction side) {
        return isSideEnabled(this.menu.outputSidesMask, side);
    }

    @Override
    public void setOutputSideEnabled(Direction side, boolean enabled) {
        this.menu.sendSetOutputSide(side, enabled);
    }

    @Override
    public Direction resolveSide(RelativeSide side) {
        return resolveSide(this.host.getOrientation(), side);
    }

    @Override
    public ItemStack outputSideIcon(Direction side) {
        Level level = requireLevel();
        BlockEntity blockEntity = level.getBlockEntity(this.host.getBlockPos().relative(side));
        if (blockEntity instanceof CableBusBlockEntity cableBus) {
            IPart part = cableBus.getPart(side.getOpposite());
            if (part != null) {
                return new ItemStack(part.getPartItem());
            }
        }
        return new ItemStack(level.getBlockState(this.host.getBlockPos().relative(side)).getBlock());
    }

    @Override
    public Component machineName() {
        return new ItemStack(this.host.getBlockState().getBlock()).getHoverName();
    }

    @Override
    public boolean hasHelp() {
        return this.helpTopic != null;
    }

    @Override
    public void openHelp() {
        GuidesCommon.openGuide(this.playerInventory.player, GuideItem.GUIDE_ID, this.helpTopic);
    }

    @Override
    public List<Component> compatibleUpgradeTooltip() {
        List<Component> tooltip = new ArrayList<>();
        tooltip.add(GuiText.CompatibleUpgrades.text());
        tooltip.addAll(Upgrades.getTooltipLinesForMachine(this.menu.getUpgrades().getUpgradableItem()));
        return List.copyOf(tooltip);
    }

    @Override
    public Component toolboxName() {
        return this.menu.getToolbox().getName();
    }

    /** Validates synchronized progress without silently clamping invalid menu state. */
    static double validateProgress(int progress, int maxProgress) {
        if (maxProgress < 0) {
            Data_Energistics.LOGGER.error(
                    "Invalid negative data reassembler max progress synchronization: {}",
                    maxProgress);
            throw new IllegalStateException("Data reassembler max progress must not be negative");
        }
        if (maxProgress == 0) {
            if (progress != 0) {
                Data_Energistics.LOGGER.error(
                        "Invalid data reassembler progress synchronization without a range: progress={}",
                        progress);
                throw new IllegalStateException("Data reassembler progress requires a positive synchronized range");
            }
            return 0.0D;
        }
        if (progress < 0 || progress > maxProgress) {
            Data_Energistics.LOGGER.error(
                    "Invalid data reassembler progress synchronization: progress={}, maxProgress={}",
                    progress,
                    maxProgress);
            throw new IllegalStateException("Data reassembler progress is outside its synchronized range");
        }
        return (double) progress / maxProgress;
    }

    /** Decodes one absolute direction bit from the synchronized output-side mask. */
    static boolean isSideEnabled(int mask, Direction side) {
        return (mask & (1 << side.ordinal())) != 0;
    }

    /** Resolves a relative side through the supplied host orientation. */
    static Direction resolveSide(BlockOrientation orientation, RelativeSide side) {
        return orientation.getSide(side);
    }

    private Slot singleSlot(SlotGroup group) {
        List<Slot> slots = slots(group);
        if (slots.size() != 1) {
            Data_Energistics.LOGGER.error("Expected one data reassembler slot for {}, found {}", group, slots.size());
            throw new IllegalStateException("Unexpected data reassembler slot count for " + group);
        }
        return slots.getFirst();
    }

    private @Nullable GenericStack decodeFluid(String fluidId, int amount, GenericStorage storage) {
        if (fluidId.isBlank() && amount == 0) {
            return null;
        }
        ResourceLocation id = ResourceLocation.tryParse(fluidId);
        if (id == null || amount <= 0) {
            Data_Energistics.LOGGER.error(
                    "Invalid synchronized fluid for data reassembler {}: id='{}', amount={}",
                    storage,
                    fluidId,
                    amount);
            throw new IllegalStateException("Invalid synchronized data reassembler fluid");
        }
        var fluid = BuiltInRegistries.FLUID.getOptional(id).orElseThrow(() -> {
            Data_Energistics.LOGGER.error(
                    "Missing fluid registry holder for synchronized data reassembler {}: {}",
                    storage,
                    id);
            return new IllegalStateException("Missing synchronized data reassembler fluid: " + id);
        });
        AEFluidKey key = AEFluidKey.of(new FluidStack(fluid, amount));
        if (key == null) {
            Data_Energistics.LOGGER.error("Could not create AE fluid key for synchronized data reassembler fluid {}", id);
            throw new IllegalStateException("Could not create synchronized data reassembler fluid key: " + id);
        }
        return new GenericStack(key, amount);
    }

    static @Nullable GenericStack decodeKey(Slot slot) {
        ItemStack item = slot.getItem();
        if (item.isEmpty()) {
            return null;
        }
        GenericStack stack = GenericStack.unwrapItemStack(item);
        if (stack == null || stack.amount() <= 0) {
            Data_Energistics.LOGGER.error(
                    "Invalid non-empty generic wrapper in data reassembler slot {}: {}",
                    slot.index,
                    item);
            throw new IllegalStateException("Data reassembler key slot contains an invalid generic wrapper");
        }
        return stack;
    }

    private Level requireLevel() {
        Level level = this.host.getLevel();
        if (level == null) {
            Data_Energistics.LOGGER.error("Cannot render data reassembler output sides without a client level");
            throw new IllegalStateException("Data reassembler client level is missing");
        }
        return level;
    }

    private static @Nullable PageAnchor findHelpTopic(DataRipperReassemblerBlockEntity host) {
        var guide = AppEngClient.instance().getGuide();
        var itemIndex = guide.getIndex(ItemIndex.class);
        ResourceLocation blockId = BuiltInRegistries.BLOCK.getKey(host.getBlockState().getBlock());
        return itemIndex.get(blockId);
    }
}

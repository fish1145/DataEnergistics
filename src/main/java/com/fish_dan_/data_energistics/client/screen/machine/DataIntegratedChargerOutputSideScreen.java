package com.fish_dan_.data_energistics.client.screen.machine;

import com.fish_dan_.data_energistics.blockentity.storage.DigitalStorageDepotOutputType;
import com.fish_dan_.data_energistics.client.widget.OutputSideDisplayButton;
import com.fish_dan_.data_energistics.menu.machine.DataIntegratedChargerMenu;

import appeng.api.config.ActionItems;
import appeng.api.orientation.RelativeSide;
import appeng.api.parts.IPart;
import appeng.blockentity.AEBaseBlockEntity;
import appeng.blockentity.networking.CableBusBlockEntity;
import appeng.client.gui.AESubScreen;
import appeng.client.gui.Icon;
import appeng.client.gui.widgets.ActionButton;
import appeng.client.gui.widgets.TabButton;
import appeng.menu.SlotSemantics;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ItemLike;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;

import java.util.EnumMap;
import java.util.List;

/** Item-output side configuration using the same backing screen as the data reassembler. */
public final class DataIntegratedChargerOutputSideScreen
                                                         extends AESubScreen<DataIntegratedChargerMenu, DataIntegratedChargerScreen> {

    private final EnumMap<Direction, OutputSideDisplayButton> buttons = new EnumMap<>(Direction.class);

    public DataIntegratedChargerOutputSideScreen(
                                                 DataIntegratedChargerScreen parent,
                                                 DataIntegratedChargerMenu menu,
                                                 AEBaseBlockEntity host) {
        super(parent, "/screens/data_ripper_output_sides.json");

        ItemStack icon = new ItemStack(host.getBlockState().getBlock());
        TabButton backButton = new TabButton(Icon.BACK, icon.getHoverName(), btn -> this.returnToParent());
        this.widgets.add("return", backButton);

        ActionButton clearButton = new ActionButton(ActionItems.S_CLOSE, btn -> clearOutputSides(menu));
        clearButton.setHalfSize(true);
        clearButton.setDisableBackground(true);
        clearButton.setMessage(Component.translatable("gui.data_energistics.set_output_sides.clear"));
        this.widgets.add("clear", clearButton);

        List<Direction> selectedSides = menu.getOutputSides(DigitalStorageDepotOutputType.ITEMS);
        for (Direction side : Direction.values()) {
            OutputSideDisplayButton button = new OutputSideDisplayButton(btn -> {
                OutputSideDisplayButton outputButton = (OutputSideDisplayButton) btn;
                outputButton.flip();
                menu.sendSetOutputSide(DigitalStorageDepotOutputType.ITEMS, side, outputButton.isOn());
            });
            if (host.getLevel() != null) {
                button.setDisplay(getDisplayIcon(host, host.getLevel(), side));
            }
            button.setOn(selectedSides.contains(side));
            this.buttons.put(side, button);
        }

        for (RelativeSide relative : RelativeSide.values()) {
            Direction side = host.getOrientation().getSide(getGuiRelativeSide(relative));
            this.widgets.add(relative.name().toLowerCase(java.util.Locale.ROOT), this.buttons.get(side));
        }
    }

    @Override
    protected void init() {
        super.init();
        this.setSlotsHidden(SlotSemantics.TOOLBOX, true);
    }

    private void clearOutputSides(DataIntegratedChargerMenu menu) {
        for (OutputSideDisplayButton button : this.buttons.values()) {
            button.setOn(false);
        }
        for (Direction side : Direction.values()) {
            menu.sendSetOutputSide(DigitalStorageDepotOutputType.ITEMS, side, false);
        }
    }

    /** The side-selection screen is viewed from the machine front, opposite AE2's horizontal relative-side view. */
    private static RelativeSide getGuiRelativeSide(RelativeSide relative) {
        return switch (relative) {
            case LEFT -> RelativeSide.RIGHT;
            case RIGHT -> RelativeSide.LEFT;
            default -> relative;
        };
    }

    private static ItemLike getDisplayIcon(AEBaseBlockEntity host, Level level, Direction side) {
        BlockPos pos = host.getBlockPos().relative(side);
        BlockEntity blockEntity = level.getBlockEntity(pos);
        if (blockEntity instanceof CableBusBlockEntity cableBus) {
            IPart part = cableBus.getPart(side.getOpposite());
            if (part != null) {
                return part.getPartItem();
            }
        }
        return level.getBlockState(pos).getBlock();
    }
}

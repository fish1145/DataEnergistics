package com.fish_dan_.data_energistics.client.screen;

import com.fish_dan_.data_energistics.blockentity.DigitalStorageDepotOutputType;
import com.fish_dan_.data_energistics.client.widget.DigitalStorageDepotOutputTypeCycleButton;
import com.fish_dan_.data_energistics.client.widget.OutputSideDisplayButton;
import com.fish_dan_.data_energistics.menu.DigitalStorageDepotMenu;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ItemLike;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;

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

import java.util.EnumMap;
import java.util.List;
import java.util.Locale;

public class DigitalStorageDepotOutputSideScreen extends AESubScreen<DigitalStorageDepotMenu, DigitalStorageDepotScreen> {

    private final EnumMap<Direction, OutputSideDisplayButton> buttons = new EnumMap<>(Direction.class);
    private final DigitalStorageDepotMenu menu;
    private final DigitalStorageDepotOutputTypeCycleButton outputTypeButton;
    private DigitalStorageDepotOutputType selectedContentType;

    public DigitalStorageDepotOutputSideScreen(
                                               DigitalStorageDepotScreen parent,
                                               DigitalStorageDepotMenu menu,
                                               AEBaseBlockEntity host,
                                               DigitalStorageDepotOutputType initialContentType) {
        super(parent, "/screens/data_ripper_output_sides.json");
        this.menu = menu;
        this.selectedContentType = initialContentType == null ? DigitalStorageDepotOutputType.ITEMS : initialContentType;

        ItemStack icon = new ItemStack(host.getBlockState().getBlock());
        TabButton backButton = new TabButton(Icon.BACK, icon.getHoverName(), btn -> this.returnToParent());
        this.widgets.add("return", backButton);

        ActionButton clearButton = new ActionButton(ActionItems.S_CLOSE, btn -> {
            for (var button : this.buttons.values()) {
                button.setOn(false);
            }
            for (Direction side : Direction.values()) {
                this.menu.sendSetOutputSide(this.selectedContentType, side, false);
            }
        });
        clearButton.setHalfSize(true);
        clearButton.setDisableBackground(true);
        clearButton.setMessage(Component.translatable("gui.data_energistics.set_output_sides.clear"));
        this.widgets.add("clear", clearButton);

        this.outputTypeButton = new DigitalStorageDepotOutputTypeCycleButton(this::setSelectedContentType);
        this.addToLeftToolbar(this.outputTypeButton);

        for (Direction side : Direction.values()) {
            OutputSideDisplayButton button = new OutputSideDisplayButton(btn -> {
                var outputButton = (OutputSideDisplayButton) btn;
                outputButton.flip();
                this.menu.sendSetOutputSide(this.selectedContentType, side, outputButton.isOn());
            });
            if (host.getLevel() != null) {
                button.setDisplay(this.getDisplayIcon(host, host.getLevel(), side));
            }
            this.buttons.put(side, button);
        }

        for (RelativeSide relative : RelativeSide.values()) {
            Direction side = host.getOrientation().getSide(relative);
            this.widgets.add(relative.name().toLowerCase(Locale.ROOT), this.buttons.get(side));
        }

        this.setSelectedContentType(this.selectedContentType);
    }

    @Override
    protected void init() {
        super.init();
        this.setSlotsHidden(SlotSemantics.TOOLBOX, true);
    }

    private void setSelectedContentType(DigitalStorageDepotOutputType selectedContentType) {
        this.selectedContentType = selectedContentType;
        this.outputTypeButton.setCurrentType(selectedContentType);
        refreshSideButtons();
    }

    private void refreshSideButtons() {
        List<Direction> selectedSides = this.menu.getOutputSides(this.selectedContentType);
        for (Direction side : Direction.values()) {
            var button = this.buttons.get(side);
            if (button != null) {
                button.setOn(selectedSides.contains(side));
            }
        }
    }

    private ItemLike getDisplayIcon(AEBaseBlockEntity host, Level level, Direction side) {
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

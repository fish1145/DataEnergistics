package com.fish_dan_.data_energistics.client.screen;

import com.fish_dan_.data_energistics.ae2.DataSanctumLargeInterfaceHost;
import com.fish_dan_.data_energistics.client.widget.OutputSideDisplayButton;
import com.fish_dan_.data_energistics.menu.DataSanctumLargeInterfaceMenu;

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
import appeng.blockentity.networking.CableBusBlockEntity;
import appeng.client.gui.AESubScreen;
import appeng.client.gui.Icon;
import appeng.client.gui.widgets.ActionButton;
import appeng.client.gui.widgets.TabButton;
import appeng.menu.SlotSemantics;

import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import java.util.function.BiConsumer;

public class DataSanctumActivePullSideScreen extends AESubScreen<DataSanctumLargeInterfaceMenu, DataSanctumLargeInterfaceScreen> {

    private final EnumMap<Direction, OutputSideDisplayButton> buttons = new EnumMap<>(Direction.class);

    public DataSanctumActivePullSideScreen(
                                           DataSanctumLargeInterfaceScreen parent,
                                           DataSanctumLargeInterfaceHost host,
                                           List<Direction> selectedSides,
                                           BiConsumer<Direction, Boolean> setter) {
        super(parent, "/screens/data_sanctum_active_pull_sides.json");

        ItemStack icon = host.getMainMenuIcon();
        TabButton backButton = new TabButton(Icon.BACK, icon.getHoverName(), btn -> this.returnToParent());
        this.widgets.add("return", backButton);

        ActionButton clearButton = new ActionButton(ActionItems.S_CLOSE, btn -> {
            for (var button : this.buttons.values()) {
                button.setOn(false);
            }
            for (Direction side : Direction.values()) {
                setter.accept(side, false);
            }
        });
        clearButton.setHalfSize(true);
        clearButton.setDisableBackground(true);
        clearButton.setMessage(Component.translatable("gui.data_energistics.set_active_pull_sides.clear"));
        this.widgets.add("clear", clearButton);

        for (Direction side : Direction.values()) {
            OutputSideDisplayButton button = new OutputSideDisplayButton(btn -> {
                var outputButton = (OutputSideDisplayButton) btn;
                outputButton.flip();
                setter.accept(side, outputButton.isOn());
            });
            Level level = host.getInterfaceLevel();
            if (level != null) {
                button.setDisplay(this.getDisplayIcon(host, level, side));
            }
            this.buttons.put(side, button);
        }

        for (Direction side : selectedSides) {
            var button = this.buttons.get(side);
            if (button != null) {
                button.setOn(true);
            }
        }

        for (RelativeSide relative : RelativeSide.values()) {
            Direction side = host.mapRelativeSide(relative);
            this.widgets.add(relative.name().toLowerCase(Locale.ROOT), this.buttons.get(side));
        }
    }

    @Override
    protected void init() {
        super.init();
        this.setSlotsHidden(SlotSemantics.TOOLBOX, true);
    }

    private ItemLike getDisplayIcon(DataSanctumLargeInterfaceHost host, Level level, Direction side) {
        BlockPos pos = host.getInterfaceBlockPos().relative(side);
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

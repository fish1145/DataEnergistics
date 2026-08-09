package com.fish_dan_.data_energistics.client.screen.base;

import com.fish_dan_.data_energistics.blockentity.DigitalStorageDepotOutputType;
import com.fish_dan_.data_energistics.client.widget.DigitalStorageDepotOutputTypeCycleButton;
import com.fish_dan_.data_energistics.client.widget.OutputSideDisplayButton;

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
import appeng.client.gui.AEBaseScreen;
import appeng.client.gui.AESubScreen;
import appeng.client.gui.Icon;
import appeng.client.gui.widgets.ActionButton;
import appeng.client.gui.widgets.TabButton;
import appeng.menu.AEBaseMenu;
import appeng.menu.SlotSemantics;

import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import java.util.function.Function;

/** Shared AE2 sub-screen used by machines with item, fluid, and generic-key output masks. */
public abstract class TypedOutputSideScreen<C extends AEBaseMenu, P extends AEBaseScreen<C>> extends AESubScreen<C, P> {

    private static final String STYLE_PATH = "/screens/data_ripper_output_sides.json";

    private final EnumMap<Direction, OutputSideDisplayButton> buttons = new EnumMap<>(Direction.class);
    private final Function<DigitalStorageDepotOutputType, List<Direction>> selectedSides;
    private final OutputSideSetter setter;
    private final DigitalStorageDepotOutputTypeCycleButton outputTypeButton;
    private DigitalStorageDepotOutputType selectedContentType;

    protected TypedOutputSideScreen(
                                    P parent,
                                    AEBaseBlockEntity host,
                                    DigitalStorageDepotOutputType initialContentType,
                                    Function<DigitalStorageDepotOutputType, List<Direction>> selectedSides,
                                    OutputSideSetter setter) {
        super(parent, STYLE_PATH);
        this.selectedSides = selectedSides;
        this.setter = setter;
        this.selectedContentType = initialContentType == null ? DigitalStorageDepotOutputType.ITEMS : initialContentType;

        ItemStack icon = new ItemStack(host.getBlockState().getBlock());
        TabButton backButton = new TabButton(Icon.BACK, icon.getHoverName(), btn -> this.returnToParent());
        this.widgets.add("return", backButton);

        ActionButton clearButton = new ActionButton(ActionItems.S_CLOSE, btn -> clearSelectedSides());
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
                this.setter.set(this.selectedContentType, side, outputButton.isOn());
            });
            if (host.getLevel() != null) {
                button.setDisplay(getDisplayIcon(host, host.getLevel(), side));
            }
            this.buttons.put(side, button);
        }

        for (RelativeSide relative : RelativeSide.values()) {
            Direction side = host.getOrientation().getSide(relative);
            this.widgets.add(relative.name().toLowerCase(Locale.ROOT), this.buttons.get(side));
        }

        setSelectedContentType(this.selectedContentType);
    }

    @Override
    protected void init() {
        super.init();
        this.setSlotsHidden(SlotSemantics.TOOLBOX, true);
    }

    private void setSelectedContentType(DigitalStorageDepotOutputType selectedContentType) {
        this.selectedContentType = selectedContentType;
        this.outputTypeButton.setCurrentType(selectedContentType);
        List<Direction> sides = this.selectedSides.apply(selectedContentType);
        for (Direction side : Direction.values()) {
            OutputSideDisplayButton button = this.buttons.get(side);
            if (button != null) {
                button.setOn(sides.contains(side));
            }
        }
    }

    private void clearSelectedSides() {
        for (var button : this.buttons.values()) {
            button.setOn(false);
        }
        for (Direction side : Direction.values()) {
            this.setter.set(this.selectedContentType, side, false);
        }
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

    @FunctionalInterface
    protected interface OutputSideSetter {

        void set(DigitalStorageDepotOutputType outputType, Direction side, boolean enabled);
    }
}

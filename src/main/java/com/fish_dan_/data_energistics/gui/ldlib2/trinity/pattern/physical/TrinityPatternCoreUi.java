package com.fish_dan_.data_energistics.gui.ldlib2.trinity.pattern.physical;

import com.fish_dan_.data_energistics.Data_Energistics;
import com.fish_dan_.data_energistics.blockentity.trinity.TrinityPatternCoreBlockEntity;
import com.fish_dan_.data_energistics.gui.ldlib2.trinity.layout.TrinityUiNbtLayouts;

import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;

import com.lowdragmc.lowdraglib2.gui.ui.ModularUI;
import com.lowdragmc.lowdraglib2.gui.ui.UI;
import com.lowdragmc.lowdraglib2.gui.ui.UIElement;

/** Builds the native LDLib2 NBT surface for one physical Trinity pattern core. */
public final class TrinityPatternCoreUi {

    private TrinityPatternCoreUi() {}

    /**
     * Creates a native LDLib2 container UI backed directly by the physical core and player inventories.
     *
     * @param core   pattern core whose real tier capacity supplies the grid rows
     * @param player player opening the block UI
     * @return a new native LDLib2 UI for the block menu holder
     */
    public static ModularUI create(TrinityPatternCoreBlockEntity core, Player player) {
        try {
            UI ui = TrinityUiNbtLayouts.load("pattern_core");
            UIElement root = ui.rootElement;
            TrinityPatternCoreNbtLayout.Controls controls = TrinityPatternCoreNbtLayout.bind(
                    root,
                    player.level().isClientSide());
            int scrollbarIndex = controls.content().getChildren().indexOf(controls.scrollbar());
            if (scrollbarIndex < 0) {
                throw new IllegalStateException("Physical pattern core authored scrollbar is detached from its content");
            }
            controls.content().addChildAt(
                    TrinityPatternCoreNativeGrid.create(core, controls.scrollbar()),
                    scrollbarIndex);
            bindClose(controls, player);
            return ModularUI.of(ui, player);
        } catch (RuntimeException | Error failure) {
            Data_Energistics.LOGGER.error("Failed to create the native Trinity Pattern Core LDLib2 UI", failure);
            throw failure;
        }
    }

    private static void bindClose(TrinityPatternCoreNbtLayout.Controls controls, Player player) {
        Component tooltip = Component.translatable("gui.close");
        controls.close().setOnServerClick(event -> player.closeContainer());
        controls.close().text.style(style -> style.tooltips(tooltip));
        controls.close().style(style -> style.tooltips(tooltip));
    }
}

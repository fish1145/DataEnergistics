package com.fish_dan_.data_energistics.orbital.control;

import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;

import com.lowdragmc.lowdraglib2.gui.sync.SyncValue;
import com.lowdragmc.lowdraglib2.gui.ui.ModularUI;
import com.lowdragmc.lowdraglib2.gui.ui.UI;
import com.lowdragmc.lowdraglib2.gui.ui.UIElement;
import com.lowdragmc.lowdraglib2.gui.ui.data.Horizontal;
import com.lowdragmc.lowdraglib2.gui.ui.data.TextWrap;
import com.lowdragmc.lowdraglib2.gui.ui.data.Vertical;
import com.lowdragmc.lowdraglib2.gui.ui.elements.Label;
import dev.vfyjxf.taffy.style.TaffyPosition;

import java.util.function.Supplier;

/**
 * Builds the shared read-only LDLib2 control surface used by a handheld terminal and its bound console.
 *
 * <p>
 * The status supplier is server-side only and must resolve the current player UUID against authoritative
 * {@code SavedData}. The factory deliberately does not retain a weapon ID in the item stack or client UI tree.
 * </p>
 */
public final class OrbitalControlUiFactory {

    private static final String STATUS_SYNC_NAME = "orbital_control_terminal_status";
    private static final int UI_WIDTH = 320;
    private static final int UI_HEIGHT = 220;

    private OrbitalControlUiFactory() {}

    /**
     * Creates the shared component tree and its server-to-client status synchronization.
     *
     * @param player         player owning the menu lifecycle
     * @param statusSupplier authoritative status supplier, invoked only on the logical server
     * @return a new LDLib2 modular UI instance
     */
    public static ModularUI create(Player player, Supplier<Component> statusSupplier) {
        boolean clientSide = player.level().isClientSide();
        UIElement root = new UIElement();
        root.setId("orbital_control_terminal_root");
        root.layout(layout -> layout.width(UI_WIDTH).height(UI_HEIGHT));

        Label title = new Label();
        title.setId("orbital_control_terminal_title");
        title.setValue(Component.translatable("screen.data_energistics.orbital_control_terminal.title"));
        title.setAllowHitTest(false);
        title.textStyle(style -> style
                .adaptiveWidth(false)
                .adaptiveHeight(false)
                .textAlignHorizontal(Horizontal.CENTER)
                .textAlignVertical(Vertical.CENTER));
        title.layout(layout -> layout
                .positionType(TaffyPosition.ABSOLUTE)
                .left(8)
                .top(8)
                .width(UI_WIDTH - 16)
                .height(18));

        Label status = new Label();
        status.setId("orbital_control_terminal_status");
        status.setAllowHitTest(false);
        status.textStyle(style -> style
                .adaptiveWidth(false)
                .adaptiveHeight(false)
                .textAlignHorizontal(Horizontal.LEFT)
                .textAlignVertical(Vertical.TOP)
                .textWrap(TextWrap.WRAP));
        status.layout(layout -> layout
                .positionType(TaffyPosition.ABSOLUTE)
                .left(8)
                .top(30)
                .width(UI_WIDTH - 16)
                .height(UI_HEIGHT - 38));
        root.addChildren(title, status);

        SyncValue<Component> statusSync = new SyncValue<>(STATUS_SYNC_NAME, Component.class, Component.empty());
        statusSync.setToSync(!clientSide);
        statusSync.setAcceptSync(clientSide);
        statusSync.addListener(status::setValue);
        if (!clientSide) {
            statusSync.setValueProvider(statusSupplier);
            statusSync.setValue(statusSupplier.get());
        }

        ModularUI modularUI = ModularUI.of(UI.of(root), player);
        modularUI.syncManager.registerSyncValue(statusSync);
        return modularUI;
    }
}

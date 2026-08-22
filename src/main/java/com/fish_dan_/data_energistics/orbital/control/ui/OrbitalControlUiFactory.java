package com.fish_dan_.data_energistics.orbital.control.ui;

import com.fish_dan_.data_energistics.orbital.attack.OrbitalAttackMode;
import com.fish_dan_.data_energistics.orbital.control.OrbitalControlActionDispatcher;

import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;

import com.lowdragmc.lowdraglib2.gui.sync.SyncValue;
import com.lowdragmc.lowdraglib2.gui.ui.ModularUI;
import com.lowdragmc.lowdraglib2.gui.ui.UI;
import com.lowdragmc.lowdraglib2.gui.ui.UIElement;
import com.lowdragmc.lowdraglib2.gui.ui.data.Horizontal;
import com.lowdragmc.lowdraglib2.gui.ui.data.TextWrap;
import com.lowdragmc.lowdraglib2.gui.ui.data.Vertical;
import com.lowdragmc.lowdraglib2.gui.ui.elements.Button;
import com.lowdragmc.lowdraglib2.gui.ui.elements.Label;
import com.lowdragmc.lowdraglib2.gui.ui.event.UIEvents;
import dev.vfyjxf.taffy.style.TaffyPosition;

import java.util.function.BooleanSupplier;
import java.util.function.Supplier;

/**
 * Builds the shared LDLib2 control surface used by a handheld terminal and its bound console.
 *
 * <p>
 * The status supplier is server-side only and must resolve the current player UUID against authoritative
 * {@code SavedData}. The factory deliberately does not retain a weapon ID in the item stack or client UI tree.
 * </p>
 */
public final class OrbitalControlUiFactory {

    private static final String STATUS_SYNC_NAME = "orbital_control_terminal_status";
    private static final String PREVIEW_SYNC_NAME = "orbital_control_terminal_preview";
    private static final int UI_WIDTH = 420;
    private static final int UI_HEIGHT = 480;
    private static final int PAGE_TOP = 58;
    private static final int PAGE_HEIGHT = 412;
    private static final int SELECTOR_TOP = 172;
    private static final int FIRE_CONTROL_TOP = 0;
    private static final int PREVIEW_TOP = OrbitalFireControlPanel.HEIGHT + 6;
    private static final int ACTION_TOP = 204;
    private static final int ACTION_HEIGHT = 22;
    private static final int ACTION_GAP = 4;
    private static final int ACTION_WIDTH = 98;

    private OrbitalControlUiFactory() {}

    /**
     * Creates the shared component tree and its server-to-client status synchronization.
     *
     * @param player         player owning the menu lifecycle
     * @param statusSupplier authoritative status supplier, invoked only on the logical server
     * @param sourceValid    authoritative check that the terminal or console is still the active control source
     * @return a new LDLib2 modular UI instance
     */
    public static ModularUI create(
                                   Player player,
                                   Supplier<Component> statusSupplier,
                                   BooleanSupplier sourceValid) {
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

        UIElement overviewPage = new UIElement();
        overviewPage.setId("orbital_control_terminal_overview_page");
        overviewPage.layout(layout -> layout
                .positionType(TaffyPosition.ABSOLUTE)
                .left(0)
                .top(PAGE_TOP)
                .width(UI_WIDTH)
                .height(PAGE_HEIGHT));

        UIElement fireControlPage = new UIElement();
        fireControlPage.setId("orbital_control_terminal_fire_control_page");
        fireControlPage.setDisplay(false);
        fireControlPage.layout(layout -> layout
                .positionType(TaffyPosition.ABSOLUTE)
                .left(0)
                .top(PAGE_TOP)
                .width(UI_WIDTH)
                .height(PAGE_HEIGHT));

        Button overviewTab = pageButton(
                "orbital_control_terminal_overview_tab",
                "screen.data_energistics.orbital_control_terminal.page.overview",
                8);
        Button fireControlTab = pageButton(
                "orbital_control_terminal_fire_control_tab",
                "screen.data_energistics.orbital_control_terminal.page.fire_control",
                214);
        overviewTab.setOnClick(event -> {
            if (clientSide) {
                overviewPage.setDisplay(true);
                fireControlPage.setDisplay(false);
            }
        });
        fireControlTab.setOnClick(event -> {
            if (clientSide) {
                overviewPage.setDisplay(false);
                fireControlPage.setDisplay(true);
            }
        });

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
                .top(0)
                .width(UI_WIDTH - 16)
                .height(164));

        Button previousWeapon = selectionButton(
                "orbital_control_terminal_previous_weapon",
                "screen.data_energistics.orbital_control_terminal.action.previous_weapon",
                8,
                player,
                false);
        Button nextWeapon = selectionButton(
                "orbital_control_terminal_next_weapon",
                "screen.data_energistics.orbital_control_terminal.action.next_weapon",
                112,
                player,
                true);

        UIElement fireControl = OrbitalFireControlPanel.create(root, player, sourceValid, clientSide);
        fireControl.layout(layout -> layout
                .positionType(TaffyPosition.ABSOLUTE)
                .left(8)
                .top(FIRE_CONTROL_TOP));

        Label preview = new Label();
        preview.setId("orbital_control_terminal_preview");
        preview.setAllowHitTest(false);
        preview.textStyle(style -> style
                .adaptiveWidth(false)
                .adaptiveHeight(false)
                .textAlignHorizontal(Horizontal.LEFT)
                .textAlignVertical(Vertical.TOP)
                .textWrap(TextWrap.WRAP));
        preview.layout(layout -> layout
                .positionType(TaffyPosition.ABSOLUTE)
                .left(8)
                .top(PREVIEW_TOP)
                .width(UI_WIDTH - 16)
                .height(PAGE_HEIGHT - PREVIEW_TOP));

        Button kinetic = actionButton(
                "orbital_control_terminal_kinetic",
                "screen.data_energistics.orbital_control_terminal.action.kinetic",
                8,
                player,
                sourceValid,
                OrbitalAttackMode.KINETIC);
        Button directed = actionButton(
                "orbital_control_terminal_directed",
                "screen.data_energistics.orbital_control_terminal.action.directed_energy",
                8 + ACTION_WIDTH + ACTION_GAP,
                player,
                sourceValid,
                OrbitalAttackMode.DIRECTED_ENERGY);
        Button digital = actionButton(
                "orbital_control_terminal_digital",
                "screen.data_energistics.orbital_control_terminal.action.digital_annihilation",
                8 + (ACTION_WIDTH + ACTION_GAP) * 2,
                player,
                sourceValid,
                OrbitalAttackMode.DIGITAL_ANNIHILATION);
        Button cancel = new Button();
        cancel.setId("orbital_control_terminal_cancel");
        cancel.setText(Component.translatable("screen.data_energistics.orbital_control_terminal.action.stop"));
        cancel.layout(layout -> layout
                .positionType(TaffyPosition.ABSOLUTE)
                .left(8 + (ACTION_WIDTH + ACTION_GAP) * 3)
                .top(ACTION_TOP)
                .width(ACTION_WIDTH)
                .height(ACTION_HEIGHT));
        cancel.setOnServerClick(event -> {
            if (player instanceof ServerPlayer serverPlayer) {
                if (!OrbitalControlActionDispatcher.cancelOrAbortFirst(serverPlayer)) {
                    serverPlayer.displayClientMessage(
                            Component.translatable("message.data_energistics.orbital_control_terminal.cancel_rejected"),
                            true);
                }
            }
        });
        overviewPage.addChildren(
                status,
                previousWeapon,
                nextWeapon,
                kinetic,
                directed,
                digital,
                cancel);
        fireControlPage.addChildren(fireControl, preview);
        root.addChildren(title, overviewTab, fireControlTab, overviewPage, fireControlPage);

        SyncValue<Component> statusSync = new SyncValue<>(STATUS_SYNC_NAME, Component.class, Component.empty());
        statusSync.setToSync(!clientSide);
        statusSync.setAcceptSync(clientSide);
        statusSync.addListener(status::setValue);
        if (!clientSide) {
            statusSync.setValueProvider(statusSupplier);
            statusSync.setValue(statusSupplier.get());
        }

        SyncValue<Component> previewSync = new SyncValue<>(PREVIEW_SYNC_NAME, Component.class, Component.empty());
        previewSync.setToSync(!clientSide);
        previewSync.setAcceptSync(clientSide);
        previewSync.addListener(preview::setValue);
        if (!clientSide) {
            ServerPlayer serverPlayer = (ServerPlayer) player;
            previewSync.setValueProvider(() -> OrbitalControlActionDispatcher.currentPreviewStatus(serverPlayer));
            previewSync.setValue(OrbitalControlActionDispatcher.currentPreviewStatus(serverPlayer));
        }

        ModularUI modularUI = ModularUI.of(UI.of(root), player);
        modularUI.syncManager.registerSyncValue(statusSync);
        modularUI.syncManager.registerSyncValue(previewSync);
        return modularUI;
    }

    private static Button pageButton(String id, String translationKey, int left) {
        Button button = new Button();
        button.setId(id);
        button.setText(Component.translatable(translationKey));
        button.layout(layout -> layout
                .positionType(TaffyPosition.ABSOLUTE)
                .left(left)
                .top(30)
                .width(198)
                .height(ACTION_HEIGHT));
        return button;
    }

    private static Button actionButton(
                                       String id,
                                       String translationKey,
                                       int left,
                                       Player player,
                                       BooleanSupplier sourceValid,
                                       OrbitalAttackMode mode) {
        Button button = new Button();
        button.setId(id);
        button.setText(Component.translatable(translationKey));
        button.layout(layout -> layout
                .positionType(TaffyPosition.ABSOLUTE)
                .left(left)
                .top(ACTION_TOP)
                .width(ACTION_WIDTH)
                .height(ACTION_HEIGHT));
        button.addServerEventListener(UIEvents.MOUSE_DOWN, event -> {
            if (player instanceof ServerPlayer serverPlayer && sourceValid.getAsBoolean()) {
                OrbitalControlActionDispatcher.beginFireAtLookTarget(serverPlayer, mode, sourceValid);
            }
        });
        button.addServerEventListener(UIEvents.MOUSE_UP, event -> {
            if (player instanceof ServerPlayer serverPlayer) {
                OrbitalControlActionDispatcher.releaseFireAtTarget(serverPlayer, mode, sourceValid);
            }
        });
        return button;
    }

    private static Button selectionButton(
                                          String id,
                                          String translationKey,
                                          int left,
                                          Player player,
                                          boolean forward) {
        Button button = new Button();
        button.setId(id);
        button.setText(Component.translatable(translationKey));
        button.layout(layout -> layout
                .positionType(TaffyPosition.ABSOLUTE)
                .left(left)
                .top(SELECTOR_TOP)
                .width(96)
                .height(ACTION_HEIGHT));
        button.setOnServerClick(event -> {
            if (player instanceof ServerPlayer serverPlayer) {
                OrbitalControlActionDispatcher.cycleWeapon(serverPlayer, forward);
            }
        });
        return button;
    }
}

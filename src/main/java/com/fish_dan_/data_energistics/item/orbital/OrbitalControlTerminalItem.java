package com.fish_dan_.data_energistics.item.orbital;

import com.fish_dan_.data_energistics.orbital.control.OrbitalControlTerminalSnapshot;

import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

import com.lowdragmc.lowdraglib2.gui.factory.HeldItemUIMenuType;
import com.lowdragmc.lowdraglib2.gui.sync.SyncValue;
import com.lowdragmc.lowdraglib2.gui.ui.ModularUI;
import com.lowdragmc.lowdraglib2.gui.ui.UI;
import com.lowdragmc.lowdraglib2.gui.ui.UIElement;
import com.lowdragmc.lowdraglib2.gui.ui.data.Horizontal;
import com.lowdragmc.lowdraglib2.gui.ui.data.TextWrap;
import com.lowdragmc.lowdraglib2.gui.ui.data.Vertical;
import com.lowdragmc.lowdraglib2.gui.ui.elements.Label;
import dev.vfyjxf.taffy.style.TaffyPosition;

/**
 * UUID-routed handheld entry point for the orbital weapon fire-control UI.
 *
 * <p>
 * The item has no owner or weapon components. The server opens an LDLib2 held-item menu only after looking up the
 * current player's accessible weapon index, and the menu rechecks that index while it remains open.
 * </p>
 */
public final class OrbitalControlTerminalItem extends Item implements HeldItemUIMenuType.HeldItemUI {

    private static final String STATUS_SYNC_NAME = "orbital_control_terminal_status";
    private static final int UI_WIDTH = 320;
    private static final int UI_HEIGHT = 220;

    public OrbitalControlTerminalItem(Properties properties) {
        super(properties.stacksTo(1));
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand usedHand) {
        ItemStack stack = player.getItemInHand(usedHand);
        if (level.isClientSide) {
            return InteractionResultHolder.sidedSuccess(stack, true);
        }
        if (!(player instanceof ServerPlayer serverPlayer)) {
            return InteractionResultHolder.fail(stack);
        }

        MinecraftServer server = serverPlayer.getServer();
        if (server == null || OrbitalControlTerminalSnapshot.capture(server, serverPlayer.getUUID()).weapons().isEmpty()) {
            return InteractionResultHolder.fail(stack);
        }
        boolean opened = HeldItemUIMenuType.openUI(serverPlayer, usedHand);
        return opened ? InteractionResultHolder.sidedSuccess(stack, false) : InteractionResultHolder.fail(stack);
    }

    @Override
    public ModularUI createUI(HeldItemUIMenuType.HeldItemUIHolder holder) {
        boolean clientSide = holder.player.level().isClientSide();
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
            statusSync.setValueProvider(() -> snapshot(holder).toComponent());
            statusSync.setValue(snapshot(holder).toComponent());
        }

        ModularUI modularUI = ModularUI.of(UI.of(root), holder.player);
        modularUI.syncManager.registerSyncValue(statusSync);
        return modularUI;
    }

    @Override
    public boolean stillValid(HeldItemUIMenuType.HeldItemUIHolder holder) {
        if (!HeldItemUIMenuType.HeldItemUI.super.stillValid(holder)) {
            return false;
        }
        if (!(holder.player instanceof ServerPlayer serverPlayer)) {
            return true;
        }
        MinecraftServer server = serverPlayer.getServer();
        return server != null && !snapshot(serverPlayer).weapons().isEmpty();
    }

    private static OrbitalControlTerminalSnapshot snapshot(HeldItemUIMenuType.HeldItemUIHolder holder) {
        ServerPlayer player = (ServerPlayer) holder.player;
        return snapshot(player);
    }

    private static OrbitalControlTerminalSnapshot snapshot(ServerPlayer player) {
        MinecraftServer server = player.getServer();
        if (server == null) {
            throw new IllegalStateException("An orbital control terminal requires an attached server player");
        }
        return OrbitalControlTerminalSnapshot.capture(server, player.getUUID());
    }
}

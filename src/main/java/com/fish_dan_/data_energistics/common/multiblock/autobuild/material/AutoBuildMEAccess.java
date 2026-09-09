package com.fish_dan_.data_energistics.common.multiblock.autobuild.material;

import appeng.api.config.Actionable;
import appeng.api.ids.AEComponents;
import appeng.api.implementations.blockentities.IWirelessAccessPoint;
import appeng.api.networking.IGrid;
import appeng.api.networking.security.IActionSource;
import appeng.api.stacks.AEKey;
import appeng.api.storage.MEStorage;
import appeng.items.tools.powered.WirelessTerminalItem;

import net.minecraft.core.GlobalPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;

import org.jspecify.annotations.Nullable;

import java.util.List;
import java.util.function.Supplier;

/** Binds the connected structure network and the first usable carried wireless terminal directly through AE2. */
final class AutoBuildMEAccess implements AutoBuildMaterialSource {

    private final MEStorage storage;
    private final Supplier<@Nullable MEStorage> binding;
    private final IActionSource actionSource;

    private AutoBuildMEAccess(MEStorage storage, Supplier<@Nullable MEStorage> binding, IActionSource actionSource) {
        this.storage = storage;
        this.binding = binding;
        this.actionSource = actionSource;
    }

    static void addSources(List<AutoBuildMaterialSource> sources, Player player,
                           Supplier<@Nullable IGrid> connectedGrid, List<AutoBuildInventoryTraversal.Slot> slots) {
        IGrid connected = connectedGrid.get();
        MEStorage connectedStorage = connected == null ? null : connected.getStorageService().getInventory();
        if (connectedStorage != null) {
            sources.add(new AutoBuildMEAccess(connectedStorage,
                    () -> connectedGrid.get() == connected ? connected.getStorageService().getInventory() : null,
                    IActionSource.ofPlayer(player)));
        }
        for (AutoBuildInventoryTraversal.Slot slot : slots) {
            ItemStack terminal = slot.stack();
            if (!(terminal.getItem() instanceof WirelessTerminalItem)) continue;
            GlobalPos target = terminal.get(AEComponents.WIRELESS_LINK_TARGET);
            if (target == null) continue;
            MEStorage wireless = wirelessStorage(player, target);
            if (wireless == null) continue;
            if (wireless != connectedStorage) {
                ItemStack expected = terminal.copy();
                sources.add(new AutoBuildMEAccess(wireless,
                        () -> ItemStack.isSameItemSameComponents(slot.stack(), expected) ?
                                wirelessStorage(player, target) : null,
                        IActionSource.ofPlayer(player)));
            }
            break;
        }
    }

    private static @Nullable MEStorage wirelessStorage(Player player, GlobalPos target) {
        if (!(player.level() instanceof ServerLevel level) || !level.dimension().equals(target.dimension()) ||
                !level.isLoaded(target.pos()))
            return null;
        if (!(level.getBlockEntity(target.pos()) instanceof IWirelessAccessPoint accessPoint) || !accessPoint.isActive()) {
            return null;
        }
        double range = accessPoint.getRange();
        if (player.distanceToSqr(Vec3.atCenterOf(target.pos())) >= range * range) return null;
        IGrid grid = accessPoint.getGrid();
        return grid == null ? null : grid.getStorageService().getInventory();
    }

    @Override
    public long available(AEKey key, long amount) {
        return binding.get() == storage ? checked(storage.extract(key, amount, Actionable.SIMULATE,
                actionSource), amount) : 0;
    }

    @Override
    public long extract(AEKey key, long amount) {
        return binding.get() == storage ? checked(storage.extract(key, amount, Actionable.MODULATE, actionSource), amount) : 0;
    }

    @Override
    public long refund(AEKey key, long amount) {
        return binding.get() == storage ? checked(storage.insert(key, amount, Actionable.MODULATE, actionSource), amount) : 0;
    }

    private static long checked(long amount, long requested) {
        if (amount < 0 || amount > requested) throw new IllegalStateException("Invalid ME material transfer: " + amount);
        return amount;
    }
}

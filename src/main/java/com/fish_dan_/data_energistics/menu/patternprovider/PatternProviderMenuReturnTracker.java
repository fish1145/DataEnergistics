package com.fish_dan_.data_energistics.menu.patternprovider;

import com.fish_dan_.data_energistics.Data_Energistics;
import com.fish_dan_.data_energistics.common.tick.ServerTickDelayQueue;
import com.fish_dan_.data_energistics.menu.patternencoding.PatternEncodingPreviewMenu;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.MenuType;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerContainerEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;

import appeng.menu.AEBaseMenu;
import appeng.menu.MenuOpener;
import appeng.menu.locator.MenuHostLocator;
import it.unimi.dsi.fastutil.objects.Object2ObjectMap;
import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap;
import org.jspecify.annotations.Nullable;

import java.util.Objects;
import java.util.UUID;

/** Tracks one server-authoritative route from an opened provider menu back to its pattern encoding terminal. */
@EventBusSubscriber(modid = Data_Energistics.MODID)
public final class PatternProviderMenuReturnTracker {

    private static final Object2ObjectMap<UUID, ActiveRoute> ACTIVE_ROUTES = new Object2ObjectOpenHashMap<>();

    private PatternProviderMenuReturnTracker() {}

    static @Nullable ReturnDestination captureDestination(ServerPlayer player) {
        if (!(player.containerMenu instanceof AEBaseMenu sourceMenu) ||
                !(sourceMenu instanceof PatternEncodingPreviewMenu)) {
            return null;
        }
        MenuHostLocator locator = sourceMenu.getLocator();
        if (locator == null) {
            return null;
        }
        return new ReturnDestination(sourceMenu.getType(), locator);
    }

    static boolean completeOpenAttempt(
                                       ServerPlayer player,
                                       AbstractContainerMenu sourceMenu,
                                       ReturnDestination destination) {
        AbstractContainerMenu childMenu = player.containerMenu;
        if (childMenu == sourceMenu) {
            return false;
        }
        if (childMenu == player.inventoryMenu) {
            if (!destination.returnTo(player)) {
                Data_Energistics.LOGGER.warn(
                        "Could not restore pattern encoding menu after a provider menu failed to open for player {}",
                        player.getGameProfile().getName());
            }
            return false;
        }
        ACTIVE_ROUTES.put(player.getUUID(), new ActiveRoute(destination, childMenu));
        return true;
    }

    /** Confirms that a client close packet targets the exact provider menu currently bound to this player. */
    public static boolean isTrackedClientClose(ServerPlayer player, int containerId) {
        ActiveRoute route = ACTIVE_ROUTES.get(player.getUUID());
        return route != null && route.childMenu == player.containerMenu &&
                route.childMenu.containerId == containerId;
    }

    /** Reopens the captured source only after Vanilla completed the matching player-requested container close. */
    public static void returnAfterClientClose(ServerPlayer player, int closedContainerId) {
        ActiveRoute route = ACTIVE_ROUTES.get(player.getUUID());
        if (route == null || route.childMenu.containerId != closedContainerId ||
                player.containerMenu != player.inventoryMenu) {
            return;
        }
        ACTIVE_ROUTES.remove(player.getUUID());
        if (!route.destination.returnTo(player)) {
            Data_Energistics.LOGGER.warn(
                    "Could not return player {} to the pattern encoding terminal after closing provider menu {}",
                    player.getGameProfile().getName(), closedContainerId);
        }
    }

    @SubscribeEvent
    public static void onMenuOpened(PlayerContainerEvent.Open event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }
        ActiveRoute route = ACTIVE_ROUTES.get(player.getUUID());
        if (route == null) {
            return;
        }
        AbstractContainerMenu openedMenu = event.getContainer();
        if (route.destination.matches(openedMenu)) {
            ACTIVE_ROUTES.remove(player.getUUID());
            return;
        }
        if (!route.follow(openedMenu)) {
            ACTIVE_ROUTES.remove(player.getUUID());
        }
    }

    @SubscribeEvent
    public static void onMenuClosed(PlayerContainerEvent.Close event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }
        ActiveRoute route = ACTIVE_ROUTES.get(player.getUUID());
        if (route == null || route.childMenu != event.getContainer()) {
            return;
        }
        long closingGeneration = route.generation;
        AbstractContainerMenu closedMenu = route.childMenu;
        ServerTickDelayQueue.runNextServerTick(player.server, () -> clearForcedClose(player, closedMenu, closingGeneration));
    }

    @SubscribeEvent
    public static void onPlayerLoggedOut(PlayerEvent.PlayerLoggedOutEvent event) {
        ACTIVE_ROUTES.remove(event.getEntity().getUUID());
    }

    @SubscribeEvent
    public static void onServerStopped(ServerStoppedEvent event) {
        ACTIVE_ROUTES.clear();
    }

    private static void clearForcedClose(
                                         ServerPlayer player,
                                         AbstractContainerMenu closedMenu,
                                         long closingGeneration) {
        ActiveRoute route = ACTIVE_ROUTES.get(player.getUUID());
        if (route != null && route.childMenu == closedMenu && route.generation == closingGeneration &&
                player.containerMenu == player.inventoryMenu) {
            ACTIVE_ROUTES.remove(player.getUUID());
        }
    }

    record ReturnDestination(MenuType<?> menuType, MenuHostLocator locator) {

        private boolean matches(AbstractContainerMenu menu) {
            return menu.getType() == this.menuType;
        }

        private boolean returnTo(ServerPlayer player) {
            return MenuOpener.returnTo(this.menuType, player, this.locator);
        }
    }

    private static final class ActiveRoute {

        private final ReturnDestination destination;
        private final @Nullable MenuHostLocator childLocator;
        private AbstractContainerMenu childMenu;
        private long generation;

        private ActiveRoute(ReturnDestination destination, AbstractContainerMenu childMenu) {
            this.destination = destination;
            this.childMenu = childMenu;
            this.childLocator = childMenu instanceof AEBaseMenu aeMenu ? aeMenu.getLocator() : null;
        }

        private boolean follow(AbstractContainerMenu openedMenu) {
            if (!(openedMenu instanceof AEBaseMenu aeMenu) || this.childLocator == null ||
                    !Objects.equals(this.childLocator, aeMenu.getLocator())) {
                return false;
            }
            this.childMenu = openedMenu;
            this.generation++;
            return true;
        }
    }
}

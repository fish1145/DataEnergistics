package com.fish_dan_.data_energistics.network;

import com.fish_dan_.data_energistics.Data_Energistics;
import com.fish_dan_.data_energistics.blockentity.TrinityAccessHatchBlockEntity;
import com.fish_dan_.data_energistics.blockentity.TrinityDataCoreBlockEntity;
import com.fish_dan_.data_energistics.common.crafting.trinity.execution.cpu.TrinityDataCoreCraftingRuntime;
import com.fish_dan_.data_energistics.common.crafting.trinity.execution.cpu.TrinityDataCoreVirtualCpu;
import com.fish_dan_.data_energistics.menu.TrinityCraftingStatusSelection;
import com.fish_dan_.data_energistics.menu.TrinityDataCoreMenu;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;

import appeng.api.networking.IGrid;
import appeng.menu.MenuOpener;
import appeng.menu.locator.MenuLocators;
import appeng.menu.me.crafting.CraftingStatusMenu;

/**
 * Reconstructs a CPU-status request from the current server menu and live Trinity topology.
 */
public final class TrinityOpenCpuStatusPayloadHandler {

    private TrinityOpenCpuStatusPayloadHandler() {}

    static void handle(TrinityOpenCpuStatusPayload payload, Player player) {
        if (!(player instanceof ServerPlayer serverPlayer)) {
            Data_Energistics.LOGGER.error("Rejected Trinity CPU status request outside a server player context");
            return;
        }

        AbstractContainerMenu currentMenu = serverPlayer.containerMenu;
        TrinityOpenCpuStatusValidator.Rejection menuRejection = TrinityOpenCpuStatusValidator.validateMenu(
                payload.containerId(),
                currentMenu.containerId,
                currentMenu instanceof TrinityDataCoreMenu);
        if (menuRejection != TrinityOpenCpuStatusValidator.Rejection.NONE) {
            Data_Energistics.LOGGER.warn(
                    "Ignored Trinity CPU status request: reason={}, player={}, requestedContainer={}, currentMenu={}",
                    menuRejection,
                    serverPlayer.getGameProfile().getName(),
                    payload.containerId(),
                    currentMenu);
            return;
        }
        TrinityDataCoreMenu menu = (TrinityDataCoreMenu) currentMenu;

        try {
            RoutedCpu routed = resolve(payload, serverPlayer, menu);
            if (routed == null) {
                return;
            }
            boolean opened = TrinityCraftingStatusSelection.open(
                    serverPlayer,
                    routed.hatch(),
                    routed.target(),
                    () -> MenuOpener.open(
                            CraftingStatusMenu.TYPE,
                            serverPlayer,
                            MenuLocators.forBlockEntity(routed.hatch())));
            if (!opened) {
                Data_Energistics.LOGGER.error(
                        "AE refused to open Trinity CPU status: player={}, host={}, cpu={}",
                        serverPlayer.getGameProfile().getName(),
                        routed.host().getHostId(),
                        routed.target().cpu().number());
            }
        } catch (RuntimeException failure) {
            Data_Energistics.LOGGER.error(
                    "Failed to open Trinity CPU status: player={}, container={}, host={}, cpu={}",
                    serverPlayer.getGameProfile().getName(),
                    payload.containerId(),
                    payload.hostId(),
                    payload.cpuNumber(),
                    failure);
            if (serverPlayer.containerMenu instanceof CraftingStatusMenu) {
                serverPlayer.closeContainer();
            }
        }
    }

    private static RoutedCpu resolve(TrinityOpenCpuStatusPayload payload,
                                     ServerPlayer player,
                                     TrinityDataCoreMenu menu) {
        if (!menu.isHostUiAvailable(player) || !(menu.getHost() instanceof TrinityDataCoreBlockEntity host)) {
            logRejected("host is unavailable or replaced", player, menu, payload);
            return null;
        }
        TrinityDataCoreCraftingRuntime runtime = host.getCraftingRuntime();
        TrinityOpenCpuStatusValidator.Resolution<TrinityDataCoreVirtualCpu, TrinityAccessHatchBlockEntity, IGrid> resolution = TrinityOpenCpuStatusValidator.resolve(
                payload.hostId(),
                payload.cpuNumber(),
                host.getHostId(),
                host.isStructureFormed() && host.isCpuProviderAvailable(),
                runtime.publishedCpus(),
                TrinityDataCoreVirtualCpu::number,
                host::getActiveAccessHatch,
                hatch -> host.isLeaseOwner(hatch) && hatch.boundCraftingRuntime() == runtime ? hatch.accessGrid() : null,
                (grid, cpu) -> grid.getCraftingService().getCpus().contains(cpu));
        if (!resolution.isResolved()) {
            logRejected(resolution.rejection().name(), player, menu, payload);
            return null;
        }
        return new RoutedCpu(
                host,
                resolution.hatch(),
                new TrinityCraftingStatusSelection.Target(
                        host.getHostId(),
                        runtime,
                        resolution.cpu(),
                        resolution.grid()));
    }

    private static void logRejected(String reason,
                                    ServerPlayer player,
                                    TrinityDataCoreMenu menu,
                                    TrinityOpenCpuStatusPayload payload) {
        Data_Energistics.LOGGER.warn(
                "Rejected Trinity CPU status request: reason={}, player={}, menu={}, host={}, cpu={}",
                reason,
                player.getGameProfile().getName(),
                menu.containerId,
                payload.hostId(),
                payload.cpuNumber());
    }

    private record RoutedCpu(TrinityDataCoreBlockEntity host,
                             TrinityAccessHatchBlockEntity hatch,
                             TrinityCraftingStatusSelection.Target target) {}
}

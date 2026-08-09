package com.fish_dan_.data_energistics.network;

import com.fish_dan_.data_energistics.Data_Energistics;
import com.fish_dan_.data_energistics.common.multiblock.transfer.CatalogValidatedPatternEncodingTransfer;
import com.fish_dan_.data_energistics.common.multiblock.transfer.MultiblockPatternTransferRequest;
import com.fish_dan_.data_energistics.common.multiblock.transfer.PatternEncodingMultiblockTransfer;
import com.fish_dan_.data_energistics.menu.patternencoding.PatternEncodingMultiblockTransferTarget;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;

import appeng.menu.me.items.PatternEncodingTermMenu;

/**
 * Main-thread router for authoritative multiblock recipe transfer into AE2 pattern terminals.
 */
public final class MultiblockPatternTransferPayloadHandler {

    private static final PatternEncodingMultiblockTransfer TRANSFER = new CatalogValidatedPatternEncodingTransfer();

    private MultiblockPatternTransferPayloadHandler() {}

    /**
     * Rejects stale or unrelated menus before resolving or mutating any recipe state.
     */
    static void handle(MultiblockPatternTransferPayload payload, Player player) {
        MultiblockPatternTransferRequest request = payload.request();
        RequestLogContext requestLogContext = RequestLogContext.from(request);
        if (!(player instanceof ServerPlayer serverPlayer)) {
            Data_Energistics.LOGGER.warn(
                    "Rejected multiblock pattern transfer outside a server player context: request={}",
                    requestLogContext);
            return;
        }

        AbstractContainerMenu currentMenu = serverPlayer.containerMenu;
        if (currentMenu.containerId != request.containerId() ||
                !(currentMenu instanceof PatternEncodingTermMenu patternMenu) ||
                !(currentMenu instanceof PatternEncodingMultiblockTransferTarget target)) {
            Data_Energistics.LOGGER.warn(
                    "Rejected multiblock pattern transfer for stale or incompatible menu: player={}, currentMenu={}, request={}",
                    serverPlayer.getGameProfile().getName(),
                    currentMenu,
                    requestLogContext);
            return;
        }

        boolean menuValid;
        try {
            menuValid = patternMenu.isValidMenu() && patternMenu.stillValid(serverPlayer);
        } catch (RuntimeException | Error failure) {
            logFailure("menu validity check failed", serverPlayer, patternMenu, requestLogContext, failure);
            menuValid = false;
        }
        if (!menuValid) {
            Data_Energistics.LOGGER.warn(
                    "Rejected multiblock pattern transfer for invalid menu: player={}, request={}",
                    serverPlayer.getGameProfile().getName(),
                    requestLogContext);
            return;
        }

        try {
            TRANSFER.transfer(request, target);
        } catch (RuntimeException | Error failure) {
            if (failure.getSuppressed().length > 0 || menuMustClose(patternMenu, failure)) {
                closeMenu(serverPlayer, patternMenu, false, failure);
            }
            logFailure(
                    "catalog reconstruction or atomic inventory transfer failed",
                    serverPlayer,
                    patternMenu,
                    requestLogContext,
                    failure);
            return;
        }

        try {
            patternMenu.broadcastChanges();
        } catch (RuntimeException | Error failure) {
            closeMenu(serverPlayer, patternMenu, true, failure);
            logFailure("menu synchronization failed", serverPlayer, patternMenu, requestLogContext, failure);
        }
    }

    private static boolean menuMustClose(PatternEncodingTermMenu menu, Throwable primaryFailure) {
        try {
            return !menu.isValidMenu();
        } catch (RuntimeException | Error validityFailure) {
            primaryFailure.addSuppressed(validityFailure);
            return true;
        }
    }

    private static void closeMenu(ServerPlayer player,
                                  PatternEncodingTermMenu menu,
                                  boolean invalidate,
                                  Throwable primaryFailure) {
        if (invalidate) {
            try {
                menu.setValidMenu(false);
            } catch (RuntimeException | Error invalidationFailure) {
                primaryFailure.addSuppressed(invalidationFailure);
            }
        }
        try {
            player.closeContainer();
        } catch (RuntimeException | Error closeFailure) {
            primaryFailure.addSuppressed(closeFailure);
        }
    }

    private static void logFailure(String reason,
                                   ServerPlayer player,
                                   PatternEncodingTermMenu menu,
                                   RequestLogContext request,
                                   Throwable failure) {
        Data_Energistics.LOGGER.error(
                "Rejected multiblock pattern transfer: reason={}, player={}, menu={}, request={}",
                reason,
                player.getGameProfile().getName(),
                menu,
                request,
                failure);
    }

    private record RequestLogContext(int containerId,
                                     ResourceLocation registeredRecipeId,
                                     ResourceLocation controllerId,
                                     long definitionRevision,
                                     ResourceLocation structureMachineId,
                                     String structureName,
                                     int variantIndex,
                                     int repeatSelectionCount,
                                     int tierSelectionCount,
                                     int candidateSelectionCount) {

        private static RequestLogContext from(MultiblockPatternTransferRequest request) {
            var fingerprint = request.projectionFingerprint();
            return new RequestLogContext(
                    request.containerId(),
                    request.registeredRecipeId(),
                    fingerprint.controllerId(),
                    fingerprint.definitionRevision(),
                    fingerprint.structureKey().machineId(),
                    fingerprint.structureKey().structureName(),
                    fingerprint.variantIndex(),
                    fingerprint.repeatCounts().size(),
                    fingerprint.tierSelections().size(),
                    fingerprint.candidateSelections().size());
        }
    }
}

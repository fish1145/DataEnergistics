package com.fish_dan_.data_energistics.menu.common;

import com.fish_dan_.data_energistics.Data_Energistics;
import com.fish_dan_.data_energistics.api.registry.provider.PatternProviderMenuOpenAdapter;
import com.fish_dan_.data_energistics.api.registry.provider.PatternProviderMenuOpenContext;
import com.fish_dan_.data_energistics.api.registry.provider.PatternProviderMenuOpenResult;
import com.fish_dan_.data_energistics.api.registry.provider.PatternProviderRegistration;
import com.fish_dan_.data_energistics.common.entrypoint.provider.PatternProviderRuntimeBindings;
import com.fish_dan_.data_energistics.common.entrypoint.provider.ResolvedProviderBinding;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.player.Player;

import appeng.helpers.patternprovider.PatternContainer;
import appeng.helpers.patternprovider.PatternProviderLogicHost;
import appeng.menu.locator.MenuLocators;
import appeng.parts.AEBasePart;

import java.util.List;
import java.util.Optional;

/** Opens provider menus through exact plugin declarations or AE2's typed core contracts. */
public final class PatternProviderMenuOpenHelper {

    private PatternProviderMenuOpenHelper() {}

    /** Attempts to open the exact provider group selected by a server-side terminal row. */
    public static boolean openProviderGroup(List<PatternContainer> providers, Player player) {
        if (!(player instanceof ServerPlayer serverPlayer)) {
            return false;
        }

        PatternProviderRegistration registration = null;
        for (PatternContainer provider : providers) {
            Optional<ResolvedProviderBinding> resolved;
            try {
                resolved = PatternProviderRuntimeBindings.resolve(provider);
            } catch (RuntimeException exception) {
                Data_Energistics.LOGGER.error("Failed to resolve provider plugin menu binding for {}", provider, exception);
                return false;
            }
            if (resolved.isEmpty()) {
                registration = null;
                break;
            }
            PatternProviderRegistration current = resolved.get().registration();
            if (registration != null && registration != current) {
                Data_Energistics.LOGGER.error(
                        "Pattern provider terminal group contains multiple plugin registrations: '{}' and '{}'",
                        registration.metadata().registrationId(),
                        current.metadata().registrationId());
                return false;
            }
            registration = current;
        }
        if (registration != null) {
            PatternProviderMenuOpenAdapter adapter = registration.menuOpenAdapter();
            if (adapter != null) {
                try {
                    PatternProviderMenuOpenResult result = adapter.open(
                            new PatternProviderMenuOpenContext(serverPlayer, providers));
                    switch (result) {
                        case OPENED -> {
                            return true;
                        }
                        case DENIED -> {
                            return false;
                        }
                        case PASS -> {}
                    }
                } catch (RuntimeException exception) {
                    Data_Energistics.LOGGER.error(
                            "Pattern provider menu adapter '{}' failed for {} provider(s)",
                            registration.metadata().registrationId(),
                            providers.size(),
                            exception);
                    return false;
                }
            }
        }

        for (PatternContainer provider : providers) {
            if (openCoreProvider(provider, serverPlayer)) {
                return true;
            }
        }
        return false;
    }

    private static boolean openCoreProvider(PatternContainer provider, ServerPlayer player) {
        if (provider instanceof PatternProviderLogicHost providerHost) {
            try {
                if (provider instanceof AEBasePart part) {
                    providerHost.openMenu(player, MenuLocators.forPart(part));
                } else {
                    providerHost.openMenu(player, MenuLocators.forBlockEntity(providerHost.getBlockEntity()));
                }
                return true;
            } catch (RuntimeException exception) {
                Data_Energistics.LOGGER.error("Failed to open AE2 pattern provider menu for {}", provider, exception);
                return false;
            }
        }
        if (provider instanceof MenuProvider menuProvider) {
            try {
                return player.openMenu(menuProvider).isPresent();
            } catch (RuntimeException exception) {
                Data_Energistics.LOGGER.error("Failed to open provider MenuProvider for {}", provider, exception);
            }
        }
        return false;
    }
}

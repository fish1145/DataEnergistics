package com.fish_dan_.data_energistics.orbital.control.ui;

import com.fish_dan_.data_energistics.Data_Energistics;
import com.fish_dan_.data_energistics.orbital.control.OrbitalControlTerminalAccess;
import com.fish_dan_.data_energistics.orbital.control.OrbitalControlTerminalSnapshot;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;

import com.lowdragmc.lowdraglib2.gui.factory.PlayerUIMenuType;
import com.lowdragmc.lowdraglib2.gui.ui.ModularUI;

/** Registers and opens the source-agnostic LDLib2 menu used by Curios and map callbacks. */
public final class OrbitalControlPlayerMenu {

    private static final ResourceLocation MENU_ID = ResourceLocation.fromNamespaceAndPath(
            Data_Energistics.MODID,
            "orbital_control_terminal");

    private OrbitalControlPlayerMenu() {}

    /** Registers the same holder factory on logical client and server during common setup. */
    public static void register() {
        OrbitalControlUiSyncAccessors.register();
        PlayerUIMenuType.register(MENU_ID, OrbitalPlayerUiHolder::new);
    }

    /** Opens the shared UI only after the server verifies both terminal presence and weapon access. */
    public static boolean open(ServerPlayer player) {
        return OrbitalControlTerminalAccess.hasTerminal(player) &&
                hasWeaponAccess(player) &&
                PlayerUIMenuType.openUI(player, MENU_ID);
    }

    private static boolean hasWeaponAccess(ServerPlayer player) {
        MinecraftServer server = player.getServer();
        return server != null && !OrbitalControlTerminalSnapshot.capture(server, player.getUUID()).weapons().isEmpty();
    }

    private record OrbitalPlayerUiHolder(Player player) implements PlayerUIMenuType.PlayerUIHolder {

        @Override
        public ModularUI createUI(Player menuPlayer) {
            return OrbitalControlUiFactory.create(
                    menuPlayer,
                    this::snapshot,
                    () -> isStillValid(menuPlayer),
                    OrbitalControlUiSource.TERMINAL);
        }

        @Override
        public boolean isStillValid(Player menuPlayer) {
            if (menuPlayer != this.player || !OrbitalControlTerminalAccess.hasTerminal(menuPlayer)) {
                return false;
            }
            return !(menuPlayer instanceof ServerPlayer serverPlayer) || hasWeaponAccess(serverPlayer);
        }

        private OrbitalControlTerminalSnapshot snapshot() {
            if (!(this.player instanceof ServerPlayer serverPlayer)) {
                return OrbitalControlTerminalSnapshot.EMPTY;
            }
            MinecraftServer server = serverPlayer.getServer();
            if (server == null) {
                throw new IllegalStateException("An orbital player menu requires an attached server player");
            }
            return OrbitalControlTerminalSnapshot.capture(server, serverPlayer.getUUID());
        }
    }
}

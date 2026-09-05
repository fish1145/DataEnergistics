package com.fish_dan_.data_energistics.menu.universal;

import com.fish_dan_.data_energistics.common.terminal.UniversalTerminalData;
import com.fish_dan_.data_energistics.part.UniversalTerminalPart;

import appeng.api.parts.IPart;
import appeng.api.parts.PartHelper;
import appeng.menu.locator.MenuHostLocator;
import appeng.menu.locator.MenuLocators;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.entity.player.Player;

import org.jspecify.annotations.Nullable;

public record UniversalTerminalMenuLocator(BlockPos pos, @Nullable Direction side, String terminalName)
        implements MenuHostLocator {

    private static boolean initialized;

    public static void init() {
        if (!initialized) {
            initialized = true;
            MenuLocators.register(
                    UniversalTerminalMenuLocator.class,
                    UniversalTerminalMenuLocator::writeToPacket,
                    UniversalTerminalMenuLocator::readFromPacket);
        }
    }

    public static UniversalTerminalMenuLocator forPart(UniversalTerminalPart part, String terminalName) {
        var location = part.getHost().getLocation();
        return new UniversalTerminalMenuLocator(location.getPos(), part.getSide(), terminalName);
    }

    @Override
    public <T> @Nullable T locate(Player player, Class<T> hostInterface) {
        IPart part = PartHelper.getPart(player.level(), this.pos, this.side);
        if (!(part instanceof UniversalTerminalPart universalPart)) {
            return null;
        }

        return UniversalTerminalData.resolveMenuHost(universalPart, player, this.terminalName, hostInterface);
    }

    public void writeToPacket(FriendlyByteBuf buf) {
        buf.writeBlockPos(this.pos);
        buf.writeBoolean(this.side != null);
        if (this.side != null) {
            buf.writeByte(this.side.ordinal());
        }
        buf.writeUtf(this.terminalName);
    }

    public static UniversalTerminalMenuLocator readFromPacket(FriendlyByteBuf buf) {
        BlockPos pos = buf.readBlockPos();
        Direction side = null;
        if (buf.readBoolean()) {
            side = Direction.values()[buf.readByte()];
        }
        return new UniversalTerminalMenuLocator(pos, side, buf.readUtf());
    }
}

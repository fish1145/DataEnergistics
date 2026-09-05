package com.fish_dan_.data_energistics.menu.universal;

import com.fish_dan_.data_energistics.network.ui.UniversalTerminalCyclePayload;
import com.fish_dan_.data_energistics.part.UniversalTerminalPart;
import com.fish_dan_.data_energistics.registry.DEMenus;

import appeng.menu.guisync.GuiSync;
import appeng.menu.implementations.PatternAccessTermMenu;

import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.MenuType;
import net.neoforged.neoforge.network.PacketDistributor;

public class UniversalPatternAccessTermMenu extends PatternAccessTermMenu implements UniversalTerminalMenuBridge {

    private final UniversalTerminalPart host;
    @GuiSync(790)
    public int availableTerminalMask;
    @GuiSync(791)
    public int activeTerminalIndex = -1;

    public UniversalPatternAccessTermMenu(int id, Inventory playerInventory, UniversalTerminalPart host) {
        this(DEMenus.UNIVERSAL_PATTERN_ACCESS_TERM.get(), id, playerInventory, host, true);
    }

    public UniversalPatternAccessTermMenu(MenuType<?> menuType, int id, Inventory playerInventory,
                                          UniversalTerminalPart host, boolean bindInventory) {
        super(menuType, id, playerInventory, host, bindInventory);
        this.host = host;
        syncTerminalState();
    }

    @Override
    public void broadcastChanges() {
        if (this.isServerSide()) {
            syncTerminalState();
        }
        super.broadcastChanges();
    }

    @Override
    public int getAvailableTerminalMask() {
        return this.availableTerminalMask;
    }

    @Override
    public int getActiveTerminalIndex() {
        return this.activeTerminalIndex;
    }

    @Override
    public UniversalTerminalPart getUniversalTerminalHost() {
        return this.host;
    }

    @Override
    public void sendCycleTerminal(boolean reverse) {
        PacketDistributor.sendToServer(new UniversalTerminalCyclePayload(reverse));
    }

    private void syncTerminalState() {
        this.availableTerminalMask = this.host.getInstalledTerminalMask();
        this.activeTerminalIndex = this.host.getActiveTerminalIndex();
    }
}

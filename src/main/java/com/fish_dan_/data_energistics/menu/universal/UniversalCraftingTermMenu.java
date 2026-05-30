package com.fish_dan_.data_energistics.menu.universal;

import com.fish_dan_.data_energistics.network.UniversalTerminalCyclePayload;
import com.fish_dan_.data_energistics.part.UniversalTerminalPart;
import com.fish_dan_.data_energistics.registry.ModMenus;

import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.MenuType;
import net.neoforged.neoforge.network.PacketDistributor;

import appeng.menu.guisync.GuiSync;
import appeng.menu.me.items.CraftingTermMenu;

public class UniversalCraftingTermMenu extends CraftingTermMenu implements UniversalTerminalMenuBridge {

    private final UniversalTerminalPart host;
    @GuiSync(790)
    public int availableTerminalMask;
    @GuiSync(791)
    public int activeTerminalIndex = -1;

    public UniversalCraftingTermMenu(int id, Inventory playerInventory, UniversalTerminalPart host) {
        this(ModMenus.UNIVERSAL_CRAFTING_TERM.get(), id, playerInventory, host, true);
    }

    public UniversalCraftingTermMenu(MenuType<?> menuType, int id, Inventory playerInventory,
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

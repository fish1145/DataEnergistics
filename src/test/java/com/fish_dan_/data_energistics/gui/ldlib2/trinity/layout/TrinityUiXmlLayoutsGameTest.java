package com.fish_dan_.data_energistics.gui.ldlib2.trinity;

import com.fish_dan_.data_energistics.Data_Energistics;
import com.fish_dan_.data_energistics.blockentity.TrinityDataCoreBlockEntity;
import com.fish_dan_.data_energistics.menu.TrinityDataCoreMenu;
import com.fish_dan_.data_energistics.registry.DEBlocks;

import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.level.GameType;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import net.neoforged.testframework.annotation.TestHolder;
import net.neoforged.testframework.gametest.EmptyTemplate;

import com.lowdragmc.lowdraglib2.gui.holder.IModularUIHolderMenu;
import com.lowdragmc.lowdraglib2.gui.ui.ModularUI;

/** Dedicated-server regressions for the embedded Trinity XML menu trees. */
@GameTestHolder(Data_Energistics.MODID)
@PrefixGameTestTemplate(false)
public final class TrinityUiXmlLayoutsGameTest {

    private TrinityUiXmlLayoutsGameTest() {}

    @TestHolder("trinity_data_core_menu_mounts_embedded_xml_on_server")
    @EmptyTemplate("5")
    @GameTest(template = "empty_5x5")
    public static void dataCoreMenuMountsEmbeddedLayoutOnServer(GameTestHelper helper) {
        BlockPos position = new BlockPos(2, 1, 2);
        helper.setBlock(position, DEBlocks.TRINITY_DATA_CORE.get().defaultBlockState());
        TrinityDataCoreBlockEntity host = helper.getBlockEntity(position);
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        player.setPos(position.getX() + 1.5D, position.getY() + 0.5D, position.getZ() + 0.5D);

        TrinityDataCoreMenu menu = new TrinityDataCoreMenu(1, player.getInventory(), host);
        if (!(menu instanceof IModularUIHolderMenu holder)) {
            throw new GameTestAssertException("Trinity Data Core menu is missing its LDLib2 holder mixin");
        }
        ModularUI modularUI = holder.getModularUI();
        if (modularUI == null) {
            throw new GameTestAssertException("Trinity Data Core menu did not mount its server ModularUI");
        }
        helper.assertValueEqual(
                modularUI.ui.rootElement.getId(),
                TrinityDataCoreHostUi.ROOT_ID,
                "Server menu must mount the stable Trinity Data Core root");
        helper.assertValueEqual(menu.slots.size(), 36, "Server menu must mount all native player slots");
        for (int menuIndex = 0; menuIndex < menu.slots.size(); menuIndex++) {
            Slot slot = menu.slots.get(menuIndex);
            int inventoryIndex = menuIndex < 27 ? menuIndex + 9 : menuIndex - 27;
            helper.assertTrue(
                    slot.container == player.getInventory() && slot.getContainerSlot() == inventoryIndex,
                    "Native player slot order diverged at menu index " + menuIndex);
        }

        menu.removed(player);
        helper.succeed();
    }
}

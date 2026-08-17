package com.fish_dan_.data_energistics.orbital.control;

import com.fish_dan_.data_energistics.Data_Energistics;
import com.fish_dan_.data_energistics.orbital.model.OrbitalAccessRole;
import com.fish_dan_.data_energistics.orbital.model.OrbitalWeaponRecord;
import com.fish_dan_.data_energistics.orbital.storage.OrbitalWeaponSavedData;
import com.fish_dan_.data_energistics.registry.DEItems;

import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ClientInformation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import net.neoforged.testframework.annotation.TestHolder;
import net.neoforged.testframework.gametest.EmptyTemplate;

import com.mojang.authlib.GameProfile;

import java.util.OptionalInt;
import java.util.List;
import java.util.UUID;

@GameTestHolder(Data_Energistics.MODID)
@PrefixGameTestTemplate(false)
public final class OrbitalControlTerminalGameTest {

    private OrbitalControlTerminalGameTest() {}

    @TestHolder("orbital_control_terminal_opens_uuid_scoped_ldlib2_overview")
    @EmptyTemplate("5")
    @GameTest(template = "empty_5x5")
    public static void opensUuidScopedLdlib2Overview(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        MinecraftServer server = level.getServer();
        OrbitalWeaponSavedData data = OrbitalWeaponSavedData.get(server);
        Item terminal = DEItems.ORBITAL_CONTROL_TERMINAL.get();

        ServerPlayer owner = createPlayer(level, "terminal-owner");
        ServerPlayer operator = createPlayer(level, "terminal-operator");
        ServerPlayer outsider = createPlayer(level, "terminal-outsider");
        OrbitalWeaponRecord ownerWeapon = data.createForOwner(server, owner.getUUID());
        OrbitalWeaponRecord sharedWeapon = data.createForOwner(server, UUID.randomUUID());
        data.authorize(server, sharedWeapon.weaponId(), sharedWeapon.ownerId(), operator.getUUID(), OrbitalAccessRole.OPERATOR);

        ItemStack ownerTerminal = DEItems.ORBITAL_CONTROL_TERMINAL.toStack();
        owner.setItemInHand(InteractionHand.MAIN_HAND, ownerTerminal);
        InteractionResultHolder<ItemStack> ownerResult = terminal.use(level, owner, InteractionHand.MAIN_HAND);
        helper.assertValueEqual(
                ownerResult.getResult(),
                InteractionResult.CONSUME,
                "An owner holding the orbital terminal must open the server-side LDLib2 menu");
        AbstractContainerMenu ownerMenu = requireMenu(helper, owner, "owner");
        helper.assertTrue(ownerMenu.stillValid(owner), "The owner menu must remain valid while the terminal is held");
        OrbitalControlTerminalSnapshot ownerSnapshot = OrbitalControlTerminalSnapshot.capture(server, owner.getUUID());
        helper.assertValueEqual(ownerSnapshot.weapons().size(), 1, "The owner UUID must select only its own weapon");
        helper.assertValueEqual(
                ownerSnapshot.selectedWeaponId(),
                ownerWeapon.weaponId(),
                "The terminal selection must come from the owner's UUID index");

        owner.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
        helper.assertFalse(ownerMenu.stillValid(owner), "Removing the terminal must invalidate its held-item menu");
        closeMenu(owner, ownerMenu);

        operator.setItemInHand(InteractionHand.MAIN_HAND, DEItems.ORBITAL_CONTROL_TERMINAL.toStack());
        InteractionResultHolder<ItemStack> operatorResult = terminal.use(level, operator, InteractionHand.MAIN_HAND);
        helper.assertValueEqual(
                operatorResult.getResult(),
                InteractionResult.CONSUME,
                "An operator holding the orbital terminal must open the same LDLib2 menu path");
        AbstractContainerMenu operatorMenu = requireMenu(helper, operator, "operator");
        OrbitalControlTerminalSnapshot operatorSnapshot = OrbitalControlTerminalSnapshot.capture(server, operator.getUUID());
        helper.assertValueEqual(operatorSnapshot.weapons().size(), 1, "The operator must see the delegated weapon");
        helper.assertValueEqual(
                operatorSnapshot.weapons().getFirst().delegatedRole(),
                OrbitalAccessRole.OPERATOR,
                "The terminal overview must preserve the delegated role from SavedData");
        data.revoke(server, sharedWeapon.weaponId(), sharedWeapon.ownerId(), operator.getUUID());
        helper.assertFalse(operatorMenu.stillValid(operator), "Revoking access must invalidate the open terminal menu");
        closeMenu(operator, operatorMenu);

        outsider.setItemInHand(InteractionHand.MAIN_HAND, DEItems.ORBITAL_CONTROL_TERMINAL.toStack());
        InteractionResultHolder<ItemStack> outsiderResult = terminal.use(level, outsider, InteractionHand.MAIN_HAND);
        helper.assertValueEqual(
                outsiderResult.getResult(),
                InteractionResult.FAIL,
                "A UUID without an owned or delegated weapon must not open a control terminal");
        helper.assertFalse(
                outsider.containerMenu != outsider.inventoryMenu,
                "A rejected terminal use must not replace the player's normal menu");
        helper.succeed();
    }

    @TestHolder("orbital_control_terminal_cycles_persisted_server_selection")
    @EmptyTemplate("5")
    @GameTest(template = "empty_5x5")
    public static void cyclesPersistedServerSelection(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        MinecraftServer server = level.getServer();
        OrbitalWeaponSavedData data = OrbitalWeaponSavedData.get(server);
        ServerPlayer player = createPlayer(level, "selection-player");
        OrbitalWeaponRecord owned = data.createForOwner(server, player.getUUID());
        OrbitalWeaponRecord delegated = data.createForOwner(server, UUID.randomUUID());
        data.authorize(server, delegated.weaponId(), delegated.ownerId(), player.getUUID(), OrbitalAccessRole.OPERATOR);

        OrbitalControlTerminalSnapshot initial = OrbitalControlTerminalSnapshot.capture(server, player.getUUID());
        List<UUID> accessible = initial.weapons().stream().map(OrbitalControlTerminalSnapshot.WeaponEntry::weaponId).toList();
        helper.assertValueEqual(accessible.size(), 2, "The selector must expose both owned and delegated weapons");
        helper.assertValueEqual(initial.selectedWeaponId(), accessible.getFirst(), "Selection must start at stable first UUID");

        helper.assertTrue(
                OrbitalControlActionDispatcher.cycleWeapon(player, true).isPresent(),
                "The server-side next-weapon action must select an accessible UUID");
        OrbitalControlTerminalSnapshot next = OrbitalControlTerminalSnapshot.capture(server, player.getUUID());
        helper.assertValueEqual(next.selectedWeaponId(), accessible.get(1), "Next must select the second accessible weapon");

        data.revoke(server, delegated.weaponId(), delegated.ownerId(), player.getUUID());
        OrbitalControlTerminalSnapshot afterRevoke = OrbitalControlTerminalSnapshot.capture(server, player.getUUID());
        helper.assertValueEqual(afterRevoke.weapons().size(), 1, "Revoking access must remove the selected weapon");
        helper.assertValueEqual(afterRevoke.selectedWeaponId(), owned.weaponId(), "Selection must fall back to the owned weapon");
        helper.succeed();
    }

    private static AbstractContainerMenu requireMenu(GameTestHelper helper, ServerPlayer player, String subject) {
        AbstractContainerMenu menu = player.containerMenu;
        helper.assertTrue(
                menu != player.inventoryMenu,
                subject + " use must replace the normal menu through the LDLib2 held-item factory");
        return menu;
    }

    private static void closeMenu(ServerPlayer player, AbstractContainerMenu menu) {
        if (player.containerMenu == menu) {
            // GameTest's synthetic ServerPlayer has no network connection. The public
            // closeContainer() path sends ClientboundContainerClosePacket; doCloseContainer()
            // performs the same server-side lifecycle without requiring a client socket.
            player.doCloseContainer();
        }
    }

    private static ServerPlayer createPlayer(ServerLevel level, String name) {
        return new TestServerPlayer(
                level.getServer(),
                level,
                new GameProfile(UUID.randomUUID(), name),
                ClientInformation.createDefault());
    }

    private static final class TestServerPlayer extends ServerPlayer {

        private TestServerPlayer(
                                 MinecraftServer server,
                                 ServerLevel level,
                                 GameProfile profile,
                                 ClientInformation clientInformation) {
            super(server, level, profile, clientInformation);
        }

        @Override
        public void displayClientMessage(Component chatComponent, boolean actionBar) {}

        @Override
        public OptionalInt openMenu(MenuProvider provider) {
            AbstractContainerMenu menu = provider.createMenu(1, this.getInventory(), this);
            if (menu == null) {
                return OptionalInt.empty();
            }
            this.containerMenu = menu;
            return OptionalInt.of(menu.containerId);
        }
    }
}

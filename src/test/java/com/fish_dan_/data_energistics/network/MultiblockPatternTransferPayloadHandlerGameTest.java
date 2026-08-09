package com.fish_dan_.data_energistics.network;

import com.fish_dan_.data_energistics.Data_Energistics;
import com.fish_dan_.data_energistics.common.multiblock.json.definition.JsonMultiBlockStructureKey;
import com.fish_dan_.data_energistics.common.multiblock.preview.MultiblockRecipeView;
import com.fish_dan_.data_energistics.common.multiblock.preview.ProjectionFingerprint;

import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ClientInformation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import net.neoforged.testframework.annotation.TestHolder;
import net.neoforged.testframework.gametest.EmptyTemplate;

import com.mojang.authlib.GameProfile;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@PrefixGameTestTemplate(false)
@GameTestHolder(Data_Energistics.MODID)
public final class MultiblockPatternTransferPayloadHandlerGameTest {

    private MultiblockPatternTransferPayloadHandlerGameTest() {}

    @TestHolder("multiblock_pattern_transfer_rejects_replaced_or_incompatible_menu_without_changes")
    @EmptyTemplate("5")
    @GameTest(template = "empty_5x5")
    public static void rejectsReplacedOrIncompatibleMenuWithoutChanges(GameTestHelper helper) {
        ServerPlayer player = new ServerPlayer(
                helper.getLevel().getServer(),
                helper.getLevel(),
                new GameProfile(UUID.randomUUID(), "pattern-transfer-route"),
                ClientInformation.createDefault());
        TrackingMenu replacement = new TrackingMenu(42);
        player.containerMenu = replacement;

        MultiblockPatternTransferPayload stalePayload = payload(41);
        MultiblockPatternTransferPayloadHandler.handle(stalePayload, player);

        helper.assertTrue(player.containerMenu == replacement, "A stale request must not replace the current menu");
        helper.assertValueEqual(replacement.stillValidCalls, 0, "A stale request must not validate the replacement");
        helper.assertValueEqual(replacement.broadcastCalls, 0, "A stale request must not synchronize the replacement");
        helper.assertValueEqual(replacement.removedCalls, 0, "A stale request must not close the replacement");

        MultiblockPatternTransferPayload incompatiblePayload = payload(replacement.containerId);
        MultiblockPatternTransferPayloadHandler.handle(incompatiblePayload, player);

        helper.assertTrue(
                player.containerMenu == replacement,
                "An incompatible same-id menu must remain the player's current menu");
        helper.assertValueEqual(
                replacement.stillValidCalls,
                0,
                "An incompatible menu must be rejected before stillValid");
        helper.assertValueEqual(
                replacement.broadcastCalls,
                0,
                "An incompatible menu must not receive transferred synchronization");
        helper.assertValueEqual(replacement.removedCalls, 0, "An incompatible menu must not be closed");

        player.containerMenu = player.inventoryMenu;
        helper.succeed();
    }

    private static MultiblockPatternTransferPayload payload(int containerId) {
        ResourceLocation controllerId = ResourceLocation.parse("data_energistics:handler_route_test");
        ProjectionFingerprint fingerprint = new ProjectionFingerprint(
                controllerId,
                0L,
                new JsonMultiBlockStructureKey(controllerId, "main"),
                0,
                List.of(1),
                Map.of(),
                Map.of());
        return new MultiblockPatternTransferPayload(
                containerId,
                MultiblockRecipeView.registeredRecipeIdFor(controllerId),
                fingerprint);
    }

    private static final class TrackingMenu extends AbstractContainerMenu {

        private int stillValidCalls;
        private int broadcastCalls;
        private int removedCalls;

        private TrackingMenu(int containerId) {
            super(null, containerId);
        }

        @Override
        public ItemStack quickMoveStack(Player player, int index) {
            return ItemStack.EMPTY;
        }

        @Override
        public boolean stillValid(Player player) {
            this.stillValidCalls++;
            return true;
        }

        @Override
        public void broadcastChanges() {
            this.broadcastCalls++;
        }

        @Override
        public void removed(Player player) {
            this.removedCalls++;
        }
    }
}

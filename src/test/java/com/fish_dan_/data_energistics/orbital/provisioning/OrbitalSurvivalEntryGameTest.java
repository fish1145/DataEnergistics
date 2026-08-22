package com.fish_dan_.data_energistics.orbital.provisioning;

import com.fish_dan_.data_energistics.Data_Energistics;
import com.fish_dan_.data_energistics.orbital.endpoint.OrbitalEndpointLocation;
import com.fish_dan_.data_energistics.orbital.storage.OrbitalWeaponSavedData;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ClientInformation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import net.neoforged.testframework.annotation.TestHolder;
import net.neoforged.testframework.gametest.EmptyTemplate;

import com.mojang.authlib.GameProfile;

import java.util.List;
import java.util.OptionalInt;
import java.util.UUID;

@GameTestHolder(Data_Energistics.MODID)
@PrefixGameTestTemplate(false)
public final class OrbitalSurvivalEntryGameTest {

    private static final BlockPos CONSOLE = new BlockPos(1, 2, 1);
    private static final BlockPos BEACON = new BlockPos(3, 2, 1);

    private OrbitalSurvivalEntryGameTest() {}

    @TestHolder("orbital_survival_recipes_provision_weapon_beacon_and_terminal")
    @EmptyTemplate("5")
    @GameTest(template = "empty_5x5", batch = "orbital_survival_entry")
    public static void recipesProvisionWeaponBeaconAndTerminal(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        OrbitalWeaponSavedData weapons = OrbitalWeaponSavedData.get(level.getServer());
        ServerPlayer owner = createPlayer(level);

        ItemStack console = craft(level, List.of(
                stack("data_energistics:data_framework"),
                stack("ae2:engineering_processor"),
                stack("data_energistics:data_framework"),
                stack("ae2:calculation_processor"),
                stack("ae2:controller"),
                stack("ae2:calculation_processor"),
                stack("data_energistics:data_framework"),
                stack("data_energistics:data_crystal_block"),
                stack("data_energistics:data_framework")));
        placeCraftedBlock(helper, CONSOLE, console, owner);
        OrbitalEndpointLocation consoleLocation = location(helper, CONSOLE);
        UUID weaponId = weapons.weaponAt(consoleLocation)
                .orElseThrow(() -> new IllegalStateException("The crafted console did not provision a weapon"))
                .weaponId();

        ItemStack beacon = craft(level, List.of(
                stack("data_energistics:data_framework"),
                stack("data_energistics:astronomical_observatory"),
                stack("data_energistics:data_framework"),
                stack("data_energistics:celestial_waveguide"),
                stack("ae2:wireless_access_point"),
                stack("data_energistics:celestial_waveguide"),
                stack("data_energistics:data_framework"),
                stack("ae2:energy_cell"),
                stack("data_energistics:data_framework")));
        placeCraftedBlock(helper, BEACON, beacon, owner);
        OrbitalEndpointLocation beaconLocation = location(helper, BEACON);
        helper.assertValueEqual(
                weapons.weaponAt(beaconLocation).orElseThrow().weaponId(),
                weaponId,
                "The crafted uplink beacon must bind the weapon provisioned by the crafted console");

        ItemStack terminal = craft(level, List.of(
                stack("data_energistics:data_crystal"),
                stack("ae2:engineering_processor"),
                stack("data_energistics:data_crystal"),
                stack("ae2:fluix_crystal"),
                stack("data_energistics:universal_terminal"),
                stack("ae2:fluix_crystal"),
                stack("data_energistics:data_crystal"),
                stack("ae2:wireless_access_point"),
                stack("data_energistics:data_crystal")));
        owner.setItemInHand(InteractionHand.MAIN_HAND, terminal);
        helper.assertValueEqual(
                terminal.use(level, owner, InteractionHand.MAIN_HAND).getResult(),
                InteractionResult.CONSUME,
                "The crafted terminal must be accepted by the server as a real control source");
        helper.assertTrue(
                owner.containerMenu != owner.inventoryMenu,
                "Using the crafted terminal must enter the server-side control lifecycle");
        owner.doCloseContainer();
        helper.succeed();
    }

    private static ItemStack craft(ServerLevel level, List<ItemStack> grid) {
        CraftingInput input = CraftingInput.of(3, 3, grid);
        RecipeHolder<CraftingRecipe> recipe = level.getRecipeManager()
                .getRecipeFor(RecipeType.CRAFTING, input, level)
                .orElseThrow(() -> new IllegalStateException("The survival crafting grid has no matching recipe"));
        ItemStack result = recipe.value().assemble(input, level.registryAccess());
        if (result.isEmpty()) {
            throw new IllegalStateException("The survival crafting recipe produced no usable output");
        }
        return result;
    }

    private static ItemStack stack(String itemId) {
        Item item = BuiltInRegistries.ITEM.get(ResourceLocation.parse(itemId));
        if (item == Items.AIR) {
            throw new IllegalStateException("Unknown survival recipe ingredient " + itemId);
        }
        return new ItemStack(item);
    }

    private static void placeCraftedBlock(
                                          GameTestHelper helper,
                                          BlockPos relativePos,
                                          ItemStack craftedStack,
                                          ServerPlayer placer) {
        Block block = Block.byItem(craftedStack.getItem());
        if (block == Blocks.AIR) {
            throw new IllegalStateException("Crafted orbital entry is not a placeable block");
        }
        ServerLevel level = helper.getLevel();
        BlockPos absolutePos = helper.absolutePos(relativePos);
        BlockState state = block.defaultBlockState();
        if (!level.setBlock(absolutePos, state, Block.UPDATE_ALL)) {
            throw new IllegalStateException("Failed to place crafted orbital entry at " + absolutePos);
        }
        state.getBlock().setPlacedBy(level, absolutePos, state, placer, craftedStack);
        craftedStack.shrink(1);
    }

    private static OrbitalEndpointLocation location(GameTestHelper helper, BlockPos relativePos) {
        return new OrbitalEndpointLocation(
                helper.getLevel().dimension().location(),
                helper.absolutePos(relativePos));
    }

    private static ServerPlayer createPlayer(ServerLevel level) {
        return new TestServerPlayer(
                level.getServer(),
                level,
                new GameProfile(UUID.randomUUID(), "orbital-survival-owner"),
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

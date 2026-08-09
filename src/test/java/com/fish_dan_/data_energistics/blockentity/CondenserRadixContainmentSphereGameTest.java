package com.fish_dan_.data_energistics.blockentity;

import com.fish_dan_.data_energistics.Data_Energistics;
import com.fish_dan_.data_energistics.accessor.CondenserMenuAccessor;
import com.fish_dan_.data_energistics.ae2.settings.CondenserOutputMode;
import com.fish_dan_.data_energistics.item.carrier.RadixContainmentSphereItem;
import com.fish_dan_.data_energistics.registry.DEItems;
import com.fish_dan_.data_energistics.registry.DERecipes;

import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import net.neoforged.testframework.annotation.TestHolder;
import net.neoforged.testframework.gametest.EmptyTemplate;

import appeng.api.ids.AEComponents;
import appeng.blockentity.misc.CondenserBlockEntity;
import appeng.core.definitions.AEBlocks;
import appeng.menu.implementations.CondenserMenu;

import java.util.List;

@GameTestHolder(Data_Energistics.MODID)
@PrefixGameTestTemplate(false)
public final class CondenserRadixContainmentSphereGameTest {

    private static final BlockPos CAPACITY_CONDENSER_POS = new BlockPos(1, 1, 1);
    private static final BlockPos MODE_SWITCH_CONDENSER_POS = new BlockPos(3, 1, 1);
    private static final double REQUIRED_POWER = 131_072.0D;
    private static final double INITIAL_BALL_POWER = 1_000.0D;

    private CondenserRadixContainmentSphereGameTest() {}

    @TestHolder("radix_containment_sphere_uses_fixed_ae_cost_and_component_capacity")
    @EmptyTemplate("5")
    @GameTest(template = "empty_5x5")
    public static void usesFixedAeCostAndComponentCapacity(GameTestHelper helper) {
        assertFixedCostAndFullComponentCapacity(helper);
        assertModeSwitchUsesFinalOutputState(helper);
        assertLoadedRecipeMatchesRuntime(helper);
        helper.succeed();
    }

    private static void assertFixedCostAndFullComponentCapacity(GameTestHelper helper) {
        CondenserBlockEntity condenser = placeCondenser(helper, CAPACITY_CONDENSER_POS);
        condenser.getInternalInventory().setItemDirect(2, DEItems.DATA_STORAGE_COMPONENT_64K.toStack());
        setMode(helper, condenser, CondenserOutputMode.RADIX_CONTAINMENT_SPHERE);

        helper.assertValueEqual(condenser.getRequiredPower(), REQUIRED_POWER,
                "A Radix Containment Sphere must always cost 131072 AE");
        helper.assertValueEqual((long) condenser.getStorage(), 524_288L,
                "A 64K component must retain its complete four-ball AE capacity");

        condenser.addPower(REQUIRED_POWER - 1.0D);
        helper.assertTrue(condenser.getInternalInventory().getStackInSlot(1).isEmpty(),
                "The condenser must not produce a ball before the fixed AE cost is reached");
        condenser.addPower(1.0D);
        assertRadixContainmentSphereOutput(helper, condenser, 1);
        helper.assertValueEqual(condenser.getStoredPower(), 0.0D,
                "Producing one ball must consume exactly 131072 AE");

        condenser.getInternalInventory().setItemDirect(1, ItemStack.EMPTY);
        condenser.addPower(REQUIRED_POWER * 2.0D + 17.0D);
        assertRadixContainmentSphereOutput(helper, condenser, 1);
        helper.assertValueEqual(condenser.getStoredPower(), REQUIRED_POWER + 17.0D,
                "A higher component must buffer the next fixed-cost output while the output slot is blocked");
        condenser.getInternalInventory().setItemDirect(1, ItemStack.EMPTY);
        assertRadixContainmentSphereOutput(helper, condenser, 1);
        helper.assertValueEqual(condenser.getStoredPower(), 17.0D,
                "A higher component must retain AE remaining after multiple fixed-cost outputs");

        condenser.getInternalInventory().setItemDirect(2, DEItems.DATA_STORAGE_COMPONENT_256M.toStack());
        helper.assertValueEqual((long) condenser.getStorage(), 2_147_483_648L,
                "The 256M component capacity must not overflow an integer multiplication");
        condenser.getInternalInventory().setItemDirect(2, DEItems.DATA_STORAGE_COMPONENT_4K.toStack());
        helper.assertValueEqual(condenser.getStorage(), 0.0D,
                "A component smaller than 16K must not enable Radix Containment Sphere output");
    }

    private static void assertModeSwitchUsesFinalOutputState(GameTestHelper helper) {
        CondenserBlockEntity condenser = placeCondenser(helper, MODE_SWITCH_CONDENSER_POS);
        condenser.getInternalInventory().setItemDirect(2, DEItems.DATA_STORAGE_COMPONENT_64K.toStack());
        condenser.addPower(256_000.0D);

        setMode(helper, condenser, CondenserOutputMode.RADIX_CONTAINMENT_SPHERE);

        assertRadixContainmentSphereOutput(helper, condenser, 1);
        helper.assertValueEqual(condenser.getStoredPower(), 124_928.0D,
                "Switching modes must apply the Radix Containment Sphere cost before refreshing output");
    }

    private static void assertLoadedRecipeMatchesRuntime(GameTestHelper helper) {
        var recipes = helper.getLevel().getRecipeManager()
                .getAllRecipesFor(DERecipes.RADIX_CONTAINMENT_SPHERE_CONDENSER_TYPE.get());
        helper.assertTrue(!recipes.isEmpty(), "The Radix Containment Sphere condenser recipe must be loaded");
        var recipe = recipes.getFirst().value();

        helper.assertValueEqual(recipe.getRequiredPower(), (int) REQUIRED_POWER,
                "The displayed recipe must use the runtime AE cost");
        List.of(
                DEItems.DATA_STORAGE_COMPONENT_16K,
                DEItems.DATA_STORAGE_COMPONENT_64K,
                DEItems.DATA_STORAGE_COMPONENT_256K,
                DEItems.DATA_STORAGE_COMPONENT_1M,
                DEItems.DATA_STORAGE_COMPONENT_4M,
                DEItems.DATA_STORAGE_COMPONENT_16M,
                DEItems.DATA_STORAGE_COMPONENT_64M,
                DEItems.DATA_STORAGE_COMPONENT_256M)
                .forEach(component -> helper.assertTrue(recipe.getCatalyst().test(component.toStack()),
                        "The displayed recipe must include " + component.getId()));
        helper.assertTrue(!recipe.getCatalyst().test(DEItems.DATA_STORAGE_COMPONENT_4K.toStack()),
                "The displayed recipe must reject components smaller than 16K");

        var result = recipe.getResultItem(helper.getLevel().registryAccess());
        helper.assertTrue(result.is(DEItems.RADIX_CONTAINMENT_SPHERE.get()),
                "The displayed recipe must output a Radix Containment Sphere");
        helper.assertValueEqual(result.getOrDefault(AEComponents.STORED_ENERGY, 0.0D), INITIAL_BALL_POWER,
                "The displayed recipe must output the same initially charged ball as the condenser");
    }

    private static CondenserBlockEntity placeCondenser(GameTestHelper helper, BlockPos position) {
        helper.setBlock(position, AEBlocks.CONDENSER.block().defaultBlockState());
        BlockEntity blockEntity = helper.getBlockEntity(position);
        if (blockEntity instanceof CondenserBlockEntity condenser) {
            return condenser;
        }
        throw new GameTestAssertException("Expected a Matter Condenser at " + position);
    }

    private static void setMode(GameTestHelper helper, CondenserBlockEntity condenser, CondenserOutputMode mode) {
        var player = helper.makeMockPlayer(GameType.CREATIVE);
        var menu = new CondenserMenu(1, player.getInventory(), condenser);
        ((CondenserMenuAccessor) menu).dataEnergistics$setCondenserOutputMode(mode.ordinal());
    }

    private static void assertRadixContainmentSphereOutput(GameTestHelper helper, CondenserBlockEntity condenser,
                                                           int expectedCount) {
        var output = condenser.getInternalInventory().getStackInSlot(1);
        helper.assertTrue(output.is(DEItems.RADIX_CONTAINMENT_SPHERE.get()),
                "The condenser must output Radix Containment Spheres, not the mapped vanilla output");
        helper.assertValueEqual(output.getCount(), expectedCount,
                "The condenser must output one ball for each complete 131072 AE");
        helper.assertValueEqual(output.getOrDefault(AEComponents.STORED_ENERGY, 0.0D), INITIAL_BALL_POWER,
                "Each condenser output must start with 1000 AE");
        helper.assertValueEqual(RadixContainmentSphereItem.getStoredDataAmount(output), 0L,
                "A newly condensed Radix Containment Sphere must not contain Data");
    }
}

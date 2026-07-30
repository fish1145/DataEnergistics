package com.fish_dan_.data_energistics.worldgen.meteorite;

import com.fish_dan_.data_energistics.Data_Energistics;
import com.fish_dan_.data_energistics.registry.ModBlocks;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.testframework.annotation.TestHolder;
import net.neoforged.testframework.gametest.EmptyTemplate;

import java.util.List;

/**
 * Verifies the real block loot tables for all five Data Crystal mother-rock tiers.
 */
@GameTestHolder(Data_Energistics.MODID)
@PrefixGameTestTemplate(false)
public final class MotherRockLootGameTest {

    /** Position reused to resolve each real mother-rock loot table in the server level. */
    private static final BlockPos MOTHER_ROCK_POS = new BlockPos(1, 1, 1);

    /** Five mother rocks in ascending charge order. */
    private static final List<DeferredBlock<Block>> MOTHER_ROCKS = List.of(
            ModBlocks.BUDDING_DATA_CRYSTAL_0,
            ModBlocks.BUDDING_DATA_CRYSTAL_1,
            ModBlocks.BUDDING_DATA_CRYSTAL_2,
            ModBlocks.BUDDING_DATA_CRYSTAL_3,
            ModBlocks.BUDDING_DATA_CRYSTAL_4);

    /** Utility holder; GameTest invokes only its static test entry point. */
    private MotherRockLootGameTest() {}

    /**
     * Aggregates Silk Touch coverage for all tiers and ordinary-drop regression coverage for the two changed tables.
     *
     * @param helper server-backed GameTest world used to load and execute the real loot tables
     */
    @TestHolder("data_crystal_mother_rocks_preserve_their_tier_with_silk_touch")
    @EmptyTemplate("5")
    @GameTest(template = "empty_5x5")
    public static void motherRocksPreserveTheirTierWithSilkTouch(GameTestHelper helper) {
        ItemStack silkTouchPickaxe = new ItemStack(Items.DIAMOND_PICKAXE);
        silkTouchPickaxe.enchant(
                helper.getLevel()
                        .registryAccess()
                        .lookupOrThrow(Registries.ENCHANTMENT)
                        .getOrThrow(Enchantments.SILK_TOUCH),
                1);

        for (DeferredBlock<Block> motherRockDefinition : MOTHER_ROCKS) {
            Block motherRock = motherRockDefinition.get();
            List<ItemStack> drops = getDrops(helper, motherRock, silkTouchPickaxe);
            assertSingleDrop(
                    helper,
                    drops,
                    motherRock.asItem(),
                    "Silk Touch must preserve " + motherRock.getName().getString());
        }

        helper.assertTrue(
                getDrops(helper, ModBlocks.BUDDING_DATA_CRYSTAL_0.get(), new ItemStack(Items.DIAMOND_PICKAXE))
                        .isEmpty(),
                "Ordinary mining must still produce no drop from Deactivated Data Crystal mother rock");
        assertSingleDrop(
                helper,
                getDrops(helper, ModBlocks.BUDDING_DATA_CRYSTAL_4.get(), new ItemStack(Items.DIAMOND_PICKAXE)),
                ModBlocks.BUDDING_DATA_CRYSTAL_2.get().asItem(),
                "Ordinary mining of Charged Data Crystal mother rock must still drop the Fatigued tier");
        helper.succeed();
    }

    /**
     * Places one mother rock and executes its server-loaded block loot table with the supplied tool.
     *
     * @param helper     server-backed GameTest world
     * @param motherRock block whose real loot table is evaluated
     * @param tool       mining tool supplied to loot predicates
     * @return generated item stacks
     */
    private static List<ItemStack> getDrops(GameTestHelper helper, Block motherRock, ItemStack tool) {
        helper.setBlock(MOTHER_ROCK_POS, motherRock.defaultBlockState());
        BlockState state = helper.getBlockState(MOTHER_ROCK_POS);
        return Block.getDrops(
                state,
                helper.getLevel(),
                helper.absolutePos(MOTHER_ROCK_POS),
                null,
                null,
                tool);
    }

    /**
     * Requires exactly one unit of the expected item from a deterministic mother-rock loot table.
     *
     * @param helper   active GameTest assertion helper
     * @param drops    generated loot stacks
     * @param expected expected item
     * @param message  failure context
     */
    private static void assertSingleDrop(GameTestHelper helper, List<ItemStack> drops, Item expected, String message) {
        helper.assertValueEqual(drops.size(), 1, message + " (stack count)");
        ItemStack drop = drops.getFirst();
        helper.assertTrue(drop.is(expected), message + " (item)");
        helper.assertValueEqual(drop.getCount(), 1, message + " (item count)");
    }
}

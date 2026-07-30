package com.fish_dan_.data_energistics.blockentity;

import com.fish_dan_.data_energistics.Data_Energistics;
import com.fish_dan_.data_energistics.registry.ModBlocks;

import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import net.neoforged.testframework.annotation.TestHolder;
import net.neoforged.testframework.gametest.EmptyTemplate;

import appeng.api.crafting.PatternDetailsHelper;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.GenericStack;
import appeng.core.definitions.AEBlocks;

import java.util.List;

@GameTestHolder(Data_Energistics.MODID)
@PrefixGameTestTemplate(false)
public final class AdaptivePatternProviderSlotMigrationGameTest {

    private static final BlockPos SOURCE_POS = new BlockPos(1, 2, 1);
    private static final BlockPos RELOADED_POS = new BlockPos(3, 2, 1);
    private static final int SHRUNK_SLOT_COUNT = 9;
    private static final int EXPANDED_SLOT_COUNT = 36;

    private AdaptivePatternProviderSlotMigrationGameTest() {}

    @TestHolder("adaptive_pattern_provider_slot_migration_preserves_overflow_across_nbt")
    @EmptyTemplate("5")
    @GameTest(template = "empty_5x5")
    public static void preservesOverflowAcrossNbtAndRestoresItInOrder(GameTestHelper helper) {
        AdaptivePatternProviderBlockEntity source = placeProvider(helper, SOURCE_POS);
        setProviderCount(source, 4);
        helper.assertValueEqual(source.getPatternSlotCountForMenu(), EXPANDED_SLOT_COUNT,
                "Four standard providers must expose the expanded pattern slot count");

        var sourcePatterns = source.getLogic().getPatternInv();
        for (int slot = 0; slot < SHRUNK_SLOT_COUNT; slot++) {
            sourcePatterns.setItemDirect(slot, encodedPattern(
                    Items.COBBLESTONE,
                    Items.STONE,
                    slot + 1L,
                    "visible-" + slot));
        }
        ItemStack firstOverflow = encodedPattern(Items.DIAMOND, Items.EMERALD, 1L, "overflow-first");
        ItemStack secondOverflow = encodedPattern(Items.GOLD_INGOT, Items.IRON_INGOT, 2L, "overflow-second");
        sourcePatterns.setItemDirect(SHRUNK_SLOT_COUNT, firstOverflow.copy());
        sourcePatterns.setItemDirect(SHRUNK_SLOT_COUNT + 1, secondOverflow.copy());

        setProviderCount(source, 1);
        helper.assertValueEqual(source.getPatternSlotCountForMenu(), SHRUNK_SLOT_COUNT,
                "One standard provider must shrink the visible pattern boundary");
        helper.assertTrue(sourcePatterns.getStackInSlot(SHRUNK_SLOT_COUNT).isEmpty(),
                "The first hidden pattern must leave the backing inventory during shrink");
        helper.assertTrue(sourcePatterns.getStackInSlot(SHRUNK_SLOT_COUNT + 1).isEmpty(),
                "The second hidden pattern must leave the backing inventory during shrink");
        helper.assertValueEqual(source.getLogic().getAvailablePatterns().size(), SHRUNK_SLOT_COUNT,
                "Overflow patterns must not remain in the active pattern directory after shrink");

        CompoundTag savedState = new CompoundTag();
        source.saveAdditional(savedState, helper.getLevel().registryAccess());
        AdaptivePatternProviderBlockEntity reloaded = placeProvider(helper, RELOADED_POS);
        reloaded.loadTag(savedState, helper.getLevel().registryAccess());
        helper.assertValueEqual(reloaded.getLogic().getAvailablePatterns().size(), SHRUNK_SLOT_COUNT,
                "Reloading must not publish persisted overflow patterns while the provider remains shrunk");

        setProviderCount(reloaded, 4);
        var reloadedPatterns = reloaded.getLogic().getPatternInv();
        helper.assertValueEqual(reloaded.getPatternSlotCountForMenu(), EXPANDED_SLOT_COUNT,
                "Reloaded provider must expand to the configured pattern slot count");
        helper.assertTrue(ItemStack.matches(firstOverflow, reloadedPatterns.getStackInSlot(SHRUNK_SLOT_COUNT)),
                "The first overflow pattern must restore first with all ItemStack components intact");
        helper.assertTrue(ItemStack.matches(secondOverflow, reloadedPatterns.getStackInSlot(SHRUNK_SLOT_COUNT + 1)),
                "The second overflow pattern must restore second with all ItemStack components intact");
        helper.assertValueEqual(reloaded.getLogic().getAvailablePatterns().size(), SHRUNK_SLOT_COUNT + 2,
                "Restored overflow patterns must become active exactly once after expansion");
        helper.succeed();
    }

    private static AdaptivePatternProviderBlockEntity placeProvider(GameTestHelper helper, BlockPos position) {
        helper.setBlock(position, ModBlocks.ADAPTIVE_PATTERN_PROVIDER.get().defaultBlockState());
        BlockEntity blockEntity = helper.getBlockEntity(position);
        if (blockEntity instanceof AdaptivePatternProviderBlockEntity provider) {
            return provider;
        }
        throw new GameTestAssertException("Placed Adaptive Pattern Provider has no matching block entity");
    }

    private static void setProviderCount(AdaptivePatternProviderBlockEntity provider, int count) {
        provider.getProviderInventory().setItemDirect(0,
                new ItemStack(AEBlocks.PATTERN_PROVIDER.block().asItem(), count));
    }

    private static ItemStack encodedPattern(Item input, Item output, long outputAmount, String name) {
        ItemStack pattern = PatternDetailsHelper.encodeProcessingPattern(
                List.of(new GenericStack(AEItemKey.of(input), 1L)),
                List.of(new GenericStack(AEItemKey.of(output), outputAmount)));
        pattern.set(DataComponents.CUSTOM_NAME, Component.literal(name));
        return pattern;
    }
}

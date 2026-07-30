package com.fish_dan_.data_energistics.part;

import com.fish_dan_.data_energistics.Data_Energistics;
import com.fish_dan_.data_energistics.registry.ModItems;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import net.neoforged.testframework.annotation.TestHolder;
import net.neoforged.testframework.gametest.EmptyTemplate;

import appeng.api.AECapabilities;
import appeng.api.behaviors.GenericInternalInventory;
import appeng.api.config.Actionable;
import appeng.api.parts.IPart;
import appeng.api.stacks.AEItemKey;
import appeng.blockentity.networking.CableBusBlockEntity;
import appeng.core.definitions.AEBlocks;

@GameTestHolder(Data_Energistics.MODID)
@PrefixGameTestTemplate(false)
public final class AdaptivePatternProviderPartCapabilityGameTest {

    private static final BlockPos PART_HOST_POS = new BlockPos(2, 1, 2);

    private AdaptivePatternProviderPartCapabilityGameTest() {}

    @TestHolder("adaptive_pattern_provider_part_exposes_return_inventory_capability")
    @EmptyTemplate("5")
    @GameTest(template = "empty_5x5")
    public static void exposesReturnInventoryCapability(GameTestHelper helper) {
        CableBusBlockEntity cableBus = placeCableBus(helper);
        IPart installedPart = cableBus.addPart(ModItems.ADAPTIVE_PATTERN_PROVIDER_PART.get(), Direction.NORTH, null);
        if (!(installedPart instanceof AdaptivePatternProviderPart adaptivePart)) {
            throw new GameTestAssertException("The cable bus must install an adaptive pattern provider part");
        }

        GenericInternalInventory returnInventory = helper.getLevel().getCapability(
                AECapabilities.GENERIC_INTERNAL_INV, helper.absolutePos(PART_HOST_POS), Direction.NORTH);
        if (returnInventory == null) {
            throw new GameTestAssertException("The adaptive pattern provider part must expose its return inventory");
        }

        helper.assertTrue(returnInventory == adaptivePart.getLogic().getReturnInv(),
                "The capability must return the adaptive part logic's own return inventory");
        helper.assertTrue(returnInventory.canInsert(), "The return inventory capability must accept writes");
        helper.assertTrue(!returnInventory.canExtract(), "The return inventory capability must reject direct extraction");

        AEItemKey stone = AEItemKey.of(Items.STONE);
        long inserted = returnInventory.insert(0, stone, 1, Actionable.MODULATE);
        helper.assertValueEqual(inserted, 1L, "The capability must write into the adaptive part return inventory");
        helper.assertValueEqual(adaptivePart.getLogic().getReturnInv().getAmount(0), 1L,
                "The adaptive part logic must observe the capability write");
        helper.assertValueEqual(returnInventory.extract(0, stone, 1, Actionable.MODULATE), 0L,
                "The capability must not bypass the return inventory extraction restriction");
        helper.succeed();
    }

    private static CableBusBlockEntity placeCableBus(GameTestHelper helper) {
        helper.setBlock(PART_HOST_POS, AEBlocks.CABLE_BUS.block().defaultBlockState());
        BlockEntity blockEntity = helper.getBlockEntity(PART_HOST_POS);
        if (blockEntity instanceof CableBusBlockEntity cableBus) {
            return cableBus;
        }
        throw new GameTestAssertException("Placed cable bus has no matching block entity");
    }
}

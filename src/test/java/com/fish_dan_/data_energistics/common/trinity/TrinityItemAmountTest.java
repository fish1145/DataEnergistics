package com.fish_dan_.data_energistics.common.trinity;

import com.fish_dan_.data_energistics.Data_Energistics;

import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import net.neoforged.testframework.annotation.TestHolder;
import net.neoforged.testframework.gametest.EmptyTemplate;

import appeng.api.stacks.AEItemKey;

import java.math.BigInteger;
import java.util.List;

@PrefixGameTestTemplate(false)
@GameTestHolder(Data_Energistics.MODID)
public final class TrinityItemAmountTest {

    private TrinityItemAmountTest() {}

    @TestHolder("trinity_item_amount_captures_stack_identity_and_count")
    @EmptyTemplate("5")
    @GameTest(template = "empty_5x5")
    public static void capturesStackIdentityAndCountWithoutMutatingSource(GameTestHelper helper) {
        ItemStack source = new ItemStack(Items.DIAMOND, 7);

        TrinityItemAmount amount = TrinityItemAmount.of(source);

        assertTrue(amount.key().equals(AEItemKey.of(source)));
        assertEquals(7L, amount.amount());
        assertEquals(7L, source.getCount());
        assertEquals(3L, amount.withAmount(3L).amount());
        assertEquals(7L, amount.amount());
        helper.succeed();
    }

    @TestHolder("trinity_item_amount_rejects_non_positive_amounts")
    @EmptyTemplate("5")
    @GameTest(template = "empty_5x5")
    public static void rejectsEmptyStacksAndNonPositiveAmounts(GameTestHelper helper) {
        AEItemKey diamond = AEItemKey.of(Items.DIAMOND);

        assertIllegalArgument(() -> new TrinityItemAmount(diamond, 0L));
        assertIllegalArgument(() -> new TrinityItemAmount(diamond, -1L));
        assertIllegalArgument(() -> TrinityItemAmount.of(ItemStack.EMPTY));
        assertIllegalArgument(() -> TrinityItemAmount.multiply(new ItemStack(Items.DIAMOND), 0L));
        assertIllegalArgument(() -> TrinityItemAmount.multiply(new ItemStack(Items.DIAMOND), -1L));
        helper.succeed();
    }

    @TestHolder("trinity_item_amount_segments_overflowing_products")
    @EmptyTemplate("5")
    @GameTest(template = "empty_5x5")
    public static void segmentsProductsBeyondLongMaxWithoutLosingAmount(GameTestHelper helper) {
        ItemStack unit = new ItemStack(Items.DIAMOND, 2);

        List<TrinityItemAmount> segments = TrinityItemAmount.multiply(unit, Long.MAX_VALUE);

        assertEquals(3L, segments.size());
        BigInteger actualTotal = BigInteger.ZERO;
        for (TrinityItemAmount segment : segments) {
            assertTrue(segment.key().equals(AEItemKey.of(unit)));
            assertTrue(segment.amount() > 0L);
            actualTotal = actualTotal.add(BigInteger.valueOf(segment.amount()));
        }
        BigInteger expectedTotal = BigInteger.valueOf(2L).multiply(BigInteger.valueOf(Long.MAX_VALUE));
        assertTrue(expectedTotal.equals(actualTotal));
        assertEquals(2L, unit.getCount());
        helper.succeed();
    }

    @TestHolder("trinity_item_amount_keeps_products_within_one_entry")
    @EmptyTemplate("5")
    @GameTest(template = "empty_5x5")
    public static void keepsProductsWithinLongRangeInOneEntry(GameTestHelper helper) {
        List<TrinityItemAmount> segments = TrinityItemAmount.multiply(new ItemStack(Items.GOLD_INGOT, 3), 5L);

        assertEquals(1L, segments.size());
        assertEquals(15L, segments.getFirst().amount());
        assertTrue(segments.getFirst().key().is(Items.GOLD_INGOT));
        helper.succeed();
    }

    private static void assertIllegalArgument(Runnable action) {
        try {
            action.run();
        } catch (IllegalArgumentException exception) {
            return;
        }
        throw new GameTestAssertException("Expected IllegalArgumentException");
    }

    private static void assertTrue(boolean condition) {
        if (!condition) {
            throw new GameTestAssertException("Expected condition to be true");
        }
    }

    private static void assertEquals(long expected, long actual) {
        if (expected != actual) {
            throw new GameTestAssertException("Expected " + expected + " but got " + actual);
        }
    }
}

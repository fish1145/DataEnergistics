package com.fish_dan_.data_energistics.client.recipe;

import com.fish_dan_.data_energistics.Data_Energistics;
import com.fish_dan_.data_energistics.ae2.DataFlowKey;
import com.fish_dan_.data_energistics.ae2.DataKey;
import com.fish_dan_.data_energistics.recipe.DataRipperReassemblerIngredient;
import com.fish_dan_.data_energistics.recipe.DataRipperReassemblerRecipe;

import net.minecraft.SharedConstants;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.material.Fluids;
import net.neoforged.fml.ModList;
import net.neoforged.fml.loading.LoadingModList;

import appeng.api.stacks.AEFluidKey;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.AEKeyType;
import appeng.api.stacks.GenericStack;
import com.mojang.serialization.MapCodec;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

public final class DataRipperReassemblerRecipeViewTest {

    private static final ResourceLocation RECIPE_ID = Data_Energistics.id("test/data_reassembler_view");

    @BeforeAll
    static void bootstrapMinecraftRegistries() {
        if (LoadingModList.get() == null) {
            LoadingModList.of(List.of(), List.of(), List.of(), List.of(), Map.of());
        }
        if (ModList.get() == null) {
            ModList.of(List.of(), List.of());
        }
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    @Test
    void preservesRecipeIdentityAndDefensivelyCopiesItemOutputs() {
        ItemStack output = new ItemStack(Items.DIAMOND, 2);
        var view = view(recipe(
                List.of(itemInput()),
                List.of(water(1_000L)),
                List.of(output),
                List.of(),
                DataRipperReassemblerRecipe.PROCESS_TICKS,
                new GenericStack(DataKey.of(), 64L),
                new GenericStack(DataFlowKey.of(), 32L)));

        output.setCount(9);
        ItemStack firstRead = view.itemOutputs().getFirst();
        firstRead.setCount(7);

        assertEquals(RECIPE_ID, view.id());
        assertEquals(2, view.itemOutputs().getFirst().getCount());
        assertNotSame(firstRead, view.itemOutputs().getFirst());
        assertNotNull(view.keyInput());
        assertNotNull(view.keyOutput());
        assertEquals(64L, view.keyInput().amount());
        assertEquals(32L, view.keyOutput().amount());
    }

    @Test
    void acceptsEveryDeclaredSlotAtItsBoundary() {
        var view = view(recipe(
                Collections.nCopies(DataRipperReassemblerRecipe.ITEM_INPUT_SLOTS, itemInput()),
                List.of(water(1_000L), lava(2_000L)),
                List.of(
                        new ItemStack(Items.DIAMOND),
                        new ItemStack(Items.EMERALD),
                        new ItemStack(Items.GOLD_INGOT)),
                List.of(water(3_000L), lava(4_000L)),
                40,
                new GenericStack(DataKey.of(), 5L),
                new GenericStack(DataFlowKey.of(), 6L)));

        assertEquals(DataRipperReassemblerRecipe.ITEM_INPUT_SLOTS, view.itemInputs().size());
        assertEquals(DataRipperReassemblerRecipe.FLUID_INPUT_SLOTS, view.fluidInputs().size());
        assertEquals(DataRipperReassemblerRecipe.ITEM_OUTPUT_SLOTS, view.itemOutputs().size());
        assertEquals(DataRipperReassemblerRecipe.FLUID_OUTPUT_SLOTS, view.fluidOutputs().size());
        assertEquals(40, view.processTicks());
    }

    @Test
    void rejectsEverySlotListThatExceedsTheMachineContract() {
        assertThrows(IllegalArgumentException.class, () -> view(recipe(
                Collections.nCopies(DataRipperReassemblerRecipe.ITEM_INPUT_SLOTS + 1, itemInput()),
                List.of(),
                List.of(),
                List.of(),
                1,
                null,
                null)));
        assertThrows(IllegalArgumentException.class, () -> view(recipe(
                List.of(),
                List.of(water(1L), water(2L), water(3L)),
                List.of(),
                List.of(),
                1,
                null,
                null)));
        assertThrows(IllegalArgumentException.class, () -> view(recipe(
                List.of(),
                List.of(),
                List.of(
                        new ItemStack(Items.STONE),
                        new ItemStack(Items.DIRT),
                        new ItemStack(Items.DIAMOND),
                        new ItemStack(Items.EMERALD)),
                List.of(),
                1,
                null,
                null)));
        assertThrows(IllegalArgumentException.class, () -> view(recipe(
                List.of(),
                List.of(),
                List.of(),
                List.of(water(1L), water(2L), water(3L)),
                1,
                null,
                null)));
    }

    @Test
    void rejectsNonPositiveAmountsAndProcessTimes() {
        assertThrows(IllegalArgumentException.class, () -> view(recipe(
                List.of(new DataRipperReassemblerIngredient(Ingredient.of(Items.STONE), 0)),
                List.of(),
                List.of(),
                List.of(),
                1,
                null,
                null)));
        assertThrows(IllegalArgumentException.class, () -> view(recipe(
                List.of(),
                List.of(water(0L)),
                List.of(),
                List.of(),
                1,
                null,
                null)));
        assertThrows(IllegalArgumentException.class, () -> view(recipe(
                List.of(),
                List.of(),
                List.of(ItemStack.EMPTY),
                List.of(),
                1,
                null,
                null)));
        assertThrows(IllegalArgumentException.class, () -> view(recipe(
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                0,
                null,
                null)));
    }

    @Test
    void rejectsKeysInTheWrongLogicalSlot() {
        GenericStack item = new GenericStack(AEItemKey.of(Items.STONE), 1L);
        GenericStack fluid = water(1L);

        assertThrows(IllegalArgumentException.class, () -> view(recipe(
                List.of(),
                List.of(item),
                List.of(),
                List.of(),
                1,
                null,
                null)));
        assertThrows(IllegalArgumentException.class, () -> view(recipe(
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                1,
                item,
                null)));
        assertThrows(IllegalArgumentException.class, () -> view(recipe(
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                1,
                fluid,
                null)));
    }

    @Test
    void acceptsArbitraryPositiveCustomKeysInKeySlots() {
        GenericStack input = new GenericStack(TestCustomKey.INSTANCE, 23L);
        GenericStack output = new GenericStack(TestCustomKey.INSTANCE, 47L);

        DataRipperReassemblerRecipeView view = view(recipe(
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                1,
                input,
                output));

        assertEquals(input, view.keyInput());
        assertEquals(output, view.keyOutput());
    }

    private static DataRipperReassemblerIngredient itemInput() {
        return new DataRipperReassemblerIngredient(Ingredient.of(Items.STONE), 2);
    }

    private static GenericStack water(long amount) {
        return new GenericStack(AEFluidKey.of(Fluids.WATER), amount);
    }

    private static GenericStack lava(long amount) {
        return new GenericStack(AEFluidKey.of(Fluids.LAVA), amount);
    }

    private static DataRipperReassemblerRecipe recipe(List<DataRipperReassemblerIngredient> itemInputs,
                                                      List<GenericStack> fluidInputs,
                                                      List<ItemStack> itemOutputs,
                                                      List<GenericStack> fluidOutputs,
                                                      int processTicks,
                                                      GenericStack keyInput,
                                                      GenericStack keyOutput) {
        return new DataRipperReassemblerRecipe(
                itemInputs,
                fluidInputs,
                itemOutputs,
                fluidOutputs,
                processTicks,
                keyInput,
                keyOutput);
    }

    private static DataRipperReassemblerRecipeView view(DataRipperReassemblerRecipe recipe) {
        return DataRipperReassemblerRecipeView.from(new RecipeHolder<>(RECIPE_ID, recipe));
    }

    private static final class TestCustomKeyType extends AEKeyType {

        private static final ResourceLocation ID = Data_Energistics.id("test_custom_key_type");
        private static final TestCustomKeyType INSTANCE = new TestCustomKeyType();

        private TestCustomKeyType() {
            super(ID, TestCustomKey.class, Component.literal("Test custom key"));
        }

        @Override
        public MapCodec<? extends AEKey> codec() {
            return TestCustomKey.MAP_CODEC;
        }

        @Override
        public AEKey readFromPacket(RegistryFriendlyByteBuf input) {
            return TestCustomKey.INSTANCE;
        }
    }

    private static final class TestCustomKey extends AEKey {

        private static final ResourceLocation ID = Data_Energistics.id("test_custom_key");
        private static final TestCustomKey INSTANCE = new TestCustomKey();
        private static final MapCodec<TestCustomKey> MAP_CODEC = MapCodec.unit(INSTANCE);

        private TestCustomKey() {}

        @Override
        public AEKeyType getType() {
            return TestCustomKeyType.INSTANCE;
        }

        @Override
        public AEKey dropSecondary() {
            return this;
        }

        @Override
        public CompoundTag toTag(HolderLookup.Provider registries) {
            return new CompoundTag();
        }

        @Override
        public Object getPrimaryKey() {
            return ID;
        }

        @Override
        public ResourceLocation getId() {
            return ID;
        }

        @Override
        public void writeToPacket(RegistryFriendlyByteBuf data) {}

        @Override
        protected Component computeDisplayName() {
            return Component.literal("Test custom key");
        }

        @Override
        public void addDrops(long amount, List<ItemStack> drops, Level level, BlockPos pos) {}

        @Override
        public boolean hasComponents() {
            return false;
        }
    }
}

package com.fish_dan_.data_energistics.client.recipe;

import com.fish_dan_.data_energistics.Data_Energistics;
import com.fish_dan_.data_energistics.ae2.DataFlowKey;
import com.fish_dan_.data_energistics.ae2.DataKey;
import com.fish_dan_.data_energistics.client.ui.DataReassemblerProgressElement;
import com.fish_dan_.data_energistics.recipe.DataRipperReassemblerIngredient;
import com.fish_dan_.data_energistics.recipe.DataRipperReassemblerRecipe;

import net.minecraft.SharedConstants;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.level.material.Fluids;
import net.neoforged.fml.ModList;
import net.neoforged.fml.loading.LoadingModList;

import appeng.api.stacks.AEFluidKey;
import appeng.api.stacks.GenericStack;
import com.lowdragmc.lowdraglib2.gui.ui.ModularUI;
import com.lowdragmc.lowdraglib2.gui.ui.UIElement;
import com.lowdragmc.lowdraglib2.gui.ui.elements.ItemSlot;
import com.lowdragmc.lowdraglib2.gui.ui.layout.LayoutProperties;
import com.lowdragmc.lowdraglib2.integration.xei.IngredientIO;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.DoubleSupplier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

public final class DataRipperReassemblerRecipeUiTest {

    private static final int RECIPE_WIDTH = 162;
    private static final int RECIPE_HEIGHT = 58;
    private static final ResourceLocation RECIPE_ID = Data_Energistics.id("test/data_reassembler_ui");
    private static final ResourceLocation TEXTURE = ResourceLocation.fromNamespaceAndPath("ae2", "textures/guis/data_reassembler.png");

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
        LayoutProperties.init();
    }

    @Test
    void createsFreshUiTreesWithStableSlotCoordinates() {
        var adapter = new RecordingAdapterImpl();
        var provider = new DataRipperReassemblerRecipeUiProviderImpl(adapter);
        var recipe = recipeView();

        ModularUI first = provider.createModularUI(recipe);
        ModularUI second = provider.createModularUI(recipe);
        first.init(RECIPE_WIDTH, RECIPE_HEIGHT);
        second.init(RECIPE_WIDTH, RECIPE_HEIGHT);

        assertNotSame(first, second, "Provider should create a fresh ModularUI");
        assertNotSame(first.ui.rootElement, second.ui.rootElement, "Provider should create a fresh UI tree");
        assertEquals(
                RECIPE_WIDTH,
                Math.round(first.ui.rootElement.getSizeWidth()),
                "Recipe root width");
        assertEquals(
                RECIPE_HEIGHT,
                Math.round(first.ui.rootElement.getSizeHeight()),
                "Recipe root height");

        assertStableBounds(first, second, "item-input-0", 8, 3, 18, 18);
        assertStableBounds(first, second, "item-input-1", 26, 3, 18, 18);
        assertStableBounds(first, second, "item-input-2", 44, 3, 18, 18);
        assertStableBounds(first, second, "item-input-3", 8, 21, 18, 18);
        assertStableBounds(first, second, "item-input-4", 26, 21, 18, 18);
        assertStableBounds(first, second, "item-input-5", 44, 21, 18, 18);
        assertStableBounds(first, second, "item-input-6", 8, 39, 18, 18);
        assertStableBounds(first, second, "item-input-7", 26, 39, 18, 18);
        assertStableBounds(first, second, "item-input-8", 44, 39, 18, 18);
        assertStableBounds(first, second, "fluid-input-0", 63, 3, 18, 18);
        assertStableBounds(first, second, "key-input", 63, 21, 18, 18);
        assertStableBounds(first, second, "fluid-input-1", 63, 39, 18, 18);
        assertStableBounds(first, second, "item-output-0", 114, 3, 18, 18);
        assertStableBounds(first, second, "item-output-1", 114, 21, 18, 18);
        assertStableBounds(first, second, "item-output-2", 114, 39, 18, 18);
        assertStableBounds(first, second, "fluid-output-0", 132, 3, 18, 18);
        assertStableBounds(first, second, "key-output", 132, 21, 18, 18);
        assertStableBounds(first, second, "fluid-output-1", 132, 39, 18, 18);
        assertStableBounds(first, second, "progress", 153, 20, 6, 18);

        assertInstanceOf(ItemSlot.class, element(first, "item-input-0"));
        assertInstanceOf(DataReassemblerGenericStackSlot.class, element(first, "fluid-input-0"));
    }

    @Test
    void registersEveryElementWithItsRoleAndFullAmount() {
        var adapter = new RecordingAdapterImpl();
        var provider = new DataRipperReassemblerRecipeUiProviderImpl(adapter);
        var recipe = recipeView();

        provider.createModularUI(recipe);

        int expectedRegistrations = recipe.itemInputs().size() + recipe.fluidInputs().size() + recipe.itemOutputs().size() + recipe.fluidOutputs().size() + 2;
        assertEquals(expectedRegistrations, adapter.registrationCount(), "Registered recipe elements");

        for (int index = 0; index < recipe.itemInputs().size(); index++) {
            DataRipperReassemblerIngredient expected = recipe.itemInputs().get(index);
            Registration registration = adapter.registration("item-input-" + index);
            assertEquals(IngredientIO.INPUT, registration.role(), "Item input " + index + " role");
            ItemStack[] expectedCandidates = expected.ingredient().getItems();
            assertEquals(
                    expectedCandidates.length,
                    registration.items().size(),
                    "Item input " + index + " candidate count");
            for (int candidateIndex = 0; candidateIndex < expectedCandidates.length; candidateIndex++) {
                assertItemStack(
                        expectedCandidates[candidateIndex].copyWithCount(expected.count()),
                        registration.items().get(candidateIndex),
                        "Item input " + index + " candidate " + candidateIndex);
            }
        }

        for (int index = 0; index < recipe.fluidInputs().size(); index++) {
            assertGeneric(
                    adapter,
                    "fluid-input-" + index,
                    IngredientIO.INPUT,
                    recipe.fluidInputs().get(index));
        }
        assertGeneric(adapter, "key-input", IngredientIO.INPUT, recipe.keyInput());

        List<ItemStack> outputs = recipe.itemOutputs();
        for (int index = 0; index < outputs.size(); index++) {
            Registration registration = adapter.registration("item-output-" + index);
            assertEquals(IngredientIO.OUTPUT, registration.role(), "Item output " + index + " role");
            assertEquals(1, registration.items().size(), "Item output " + index + " candidate count");
            assertItemStack(outputs.get(index), registration.items().getFirst(), "Item output " + index);
        }
        for (int index = 0; index < recipe.fluidOutputs().size(); index++) {
            assertGeneric(
                    adapter,
                    "fluid-output-" + index,
                    IngredientIO.OUTPUT,
                    recipe.fluidOutputs().get(index));
        }
        assertGeneric(adapter, "key-output", IngredientIO.OUTPUT, recipe.keyOutput());
    }

    @Test
    void omitsOptionalKeyElementsWhenTheRecipeDoesNotDeclareThem() {
        var adapter = new RecordingAdapterImpl();
        var provider = new DataRipperReassemblerRecipeUiProviderImpl(adapter);
        var recipe = view(recipe(
                List.of(itemInput()),
                List.of(),
                List.of(new ItemStack(Items.DIAMOND)),
                List.of(),
                DataRipperReassemblerRecipe.PROCESS_TICKS,
                null,
                null));

        ModularUI ui = provider.createModularUI(recipe);

        assertTrue(ui.ui.selectId("key-input").findFirst().isEmpty(), "Optional key input should be absent");
        assertTrue(ui.ui.selectId("key-output").findFirst().isEmpty(), "Optional key output should be absent");
        assertEquals(2, adapter.registrationCount(), "Only item input and output should be registered");
    }

    @Test
    void validatesProgressSupplierAndTextureRegion() {
        assertEquals(0.0D, element(() -> 0.0D).progress(), "Zero progress");
        assertEquals(1.0D, element(() -> 1.0D).progress(), "Full progress");
        assertEquals(0.375D, element(() -> 0.375D).progress(), "Partial progress");

        assertThrows(IllegalStateException.class, () -> element(() -> -0.01D).progress(), "Negative progress");
        assertThrows(IllegalStateException.class, () -> element(() -> 1.01D).progress(), "Excess progress");
        assertThrows(IllegalStateException.class, () -> element(() -> Double.NaN).progress(), "NaN progress");
        assertThrows(
                IllegalStateException.class,
                () -> element(() -> Double.POSITIVE_INFINITY).progress(),
                "Infinite progress");
        assertThrows(IllegalArgumentException.class, () -> new DataReassemblerProgressElement(
                TEXTURE,
                252,
                0,
                6,
                18,
                256,
                256,
                () -> 0.5D), "Out-of-bounds texture region");
    }

    private static void assertGeneric(RecordingAdapterImpl adapter,
                                      String id,
                                      IngredientIO role,
                                      GenericStack expected) {
        Registration registration = adapter.registration(id);
        assertEquals(role, registration.role(), id + " role");
        assertEquals(expected.what(), registration.genericStack().what(), id + " key");
        assertEquals(expected.amount(), registration.genericStack().amount(), id + " amount");
    }

    private static void assertItemStack(ItemStack expected, ItemStack actual, String message) {
        assertTrue(ItemStack.isSameItemSameComponents(expected, actual), message + " identity and components");
        assertEquals(expected.getCount(), actual.getCount(), message + " amount");
    }

    private static void assertBounds(ModularUI ui,
                                     String id,
                                     int x,
                                     int y,
                                     int width,
                                     int height) {
        UIElement element = element(ui, id);
        assertEquals(x, Math.round(element.getLayoutX()), id + " x");
        assertEquals(y, Math.round(element.getLayoutY()), id + " y");
        assertEquals(width, Math.round(element.getSizeWidth()), id + " width");
        assertEquals(height, Math.round(element.getSizeHeight()), id + " height");
    }

    private static void assertStableBounds(ModularUI first,
                                           ModularUI second,
                                           String id,
                                           int x,
                                           int y,
                                           int width,
                                           int height) {
        assertBounds(first, id, x, y, width, height);
        assertBounds(second, id, x, y, width, height);
    }

    private static UIElement element(ModularUI ui, String id) {
        return ui.ui.selectId(id).findFirst().orElseThrow();
    }

    private static DataReassemblerProgressElement element(DoubleSupplier supplier) {
        return new DataReassemblerProgressElement(TEXTURE, 176, 0, 6, 18, 256, 256, supplier);
    }

    private static DataRipperReassemblerRecipeView recipeView() {
        return view(recipe(
                List.of(
                        new DataRipperReassemblerIngredient(Ingredient.of(Items.STONE, Items.COBBLESTONE), 4),
                        new DataRipperReassemblerIngredient(Ingredient.of(Items.DIRT), 2),
                        new DataRipperReassemblerIngredient(Ingredient.of(Items.IRON_INGOT), 3),
                        new DataRipperReassemblerIngredient(Ingredient.of(Items.GOLD_INGOT), 5),
                        new DataRipperReassemblerIngredient(Ingredient.of(Items.REDSTONE), 6),
                        new DataRipperReassemblerIngredient(Ingredient.of(Items.QUARTZ), 7),
                        new DataRipperReassemblerIngredient(Ingredient.of(Items.COAL), 8),
                        new DataRipperReassemblerIngredient(Ingredient.of(Items.COPPER_INGOT), 9),
                        new DataRipperReassemblerIngredient(Ingredient.of(Items.LAPIS_LAZULI), 10)),
                List.of(
                        new GenericStack(AEFluidKey.of(Fluids.WATER), 1_000L),
                        new GenericStack(AEFluidKey.of(Fluids.LAVA), 2_000L)),
                List.of(
                        namedDiamondOutput(),
                        new ItemStack(Items.EMERALD, 2),
                        new ItemStack(Items.NETHERITE_INGOT)),
                List.of(
                        new GenericStack(AEFluidKey.of(Fluids.WATER), 4_000L),
                        new GenericStack(AEFluidKey.of(Fluids.LAVA), 5_000L)),
                DataRipperReassemblerRecipe.PROCESS_TICKS,
                new GenericStack(DataFlowKey.of(), 128L),
                new GenericStack(DataKey.of(), 256L)));
    }

    private static DataRipperReassemblerIngredient itemInput() {
        return new DataRipperReassemblerIngredient(Ingredient.of(Items.STONE), 1);
    }

    private static ItemStack namedDiamondOutput() {
        ItemStack stack = new ItemStack(Items.DIAMOND, 3);
        stack.set(DataComponents.CUSTOM_NAME, Component.literal("Viewer test component"));
        return stack;
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

    private record Registration(String id,
                                IngredientIO role,
                                List<ItemStack> items,
                                GenericStack genericStack) {}

    private static final class RecordingAdapterImpl implements DataReassemblerRecipeIngredientAdapter {

        private final List<Registration> registrations = new ArrayList<>();

        @Override
        public void registerItemSlot(ItemSlot element, IngredientIO role, List<ItemStack> candidates) {
            this.registrations.add(new Registration(
                    element.getId(),
                    role,
                    candidates.stream().map(ItemStack::copy).toList(),
                    null));
        }

        @Override
        public void registerGenericStackSlot(UIElement element, IngredientIO role, GenericStack stack) {
            this.registrations.add(new Registration(element.getId(), role, List.of(), stack));
        }

        private Registration registration(String id) {
            return this.registrations.stream()
                    .filter(registration -> registration.id().equals(id))
                    .findFirst()
                    .orElseThrow();
        }

        private int registrationCount() {
            return this.registrations.size();
        }
    }
}

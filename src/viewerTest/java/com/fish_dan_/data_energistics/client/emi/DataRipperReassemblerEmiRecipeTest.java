package com.fish_dan_.data_energistics.client.emi;

import com.fish_dan_.data_energistics.Data_Energistics;
import com.fish_dan_.data_energistics.ae2.DataFlowKeyType;
import com.fish_dan_.data_energistics.ae2.DataKeyType;
import com.fish_dan_.data_energistics.block.DataRipperReassemblerMainBlock;
import com.fish_dan_.data_energistics.recipe.DataRipperReassemblerRecipe;
import com.fish_dan_.data_energistics.recipe.DataRipperReassemblerRecipeSerializer;

import net.minecraft.SharedConstants;
import net.minecraft.core.MappedRegistry;
import net.minecraft.core.Registry;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.RegistryOps;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.Bootstrap;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.Fluids;
import net.neoforged.fml.ModList;
import net.neoforged.fml.loading.LoadingModList;
import net.neoforged.neoforge.fluids.BaseFlowingFluid;
import net.neoforged.neoforge.fluids.FluidType;
import net.neoforged.neoforge.registries.RegistryBuilder;

import appeng.api.stacks.AEKeyType;
import appeng.api.stacks.AEKeyTypesInternal;
import com.google.gson.JsonParser;
import com.lowdragmc.lowdraglib2.gui.ui.layout.LayoutProperties;
import com.lowdragmc.lowdraglib2.integration.xei.emi.EMIRecipeSlotWidget;
import com.lowdragmc.lowdraglib2.integration.xei.emi.ModularUIEMIWidget;
import com.mojang.serialization.JsonOps;
import dev.emi.emi.api.stack.EmiIngredient;
import dev.emi.emi.api.stack.EmiStack;
import dev.emi.emi.api.widget.Bounds;
import dev.emi.emi.api.widget.Widget;
import dev.emi.emi.api.widget.WidgetHolder;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Predicate;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;

public final class DataRipperReassemblerEmiRecipeTest {

    private static final String RECIPE_RESOURCE_ROOT = "/data/data_energistics/recipe/data_energistics/data_reassembler/";
    private static final ResourceLocation CATEGORY_ID = Data_Energistics.id("data_reassembler");
    private static final TagKey<Item> OBSIDIAN_INGOT_TAG = TagKey.create(
            Registries.ITEM,
            ResourceLocation.fromNamespaceAndPath("c", "ingots/obsidian"));
    private static final ResourceLocation TEST_OBSIDIAN_INGOT_ID = Data_Energistics.id("viewer_test_obsidian_ingot");
    private static Fluid enderFluid;
    private static Item testObsidianIngot;

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
        registerRecipeDependencies();
        registerAeKeyTypes();
        LayoutProperties.init();
    }

    @Test
    void exposesShippedRecipeIngredientsByIdentityAndAmount() throws IOException {
        DataRipperReassemblerEmiRecipe ender = loadEmiRecipe("ender");
        assertRecipeIdentity(ender, "ender");
        assertEquals(1, ender.getInputs().size());
        assertItemIngredient(ender.getInputs().getFirst(), 1L, Items.ENDER_PEARL);
        assertEquals(1, ender.getOutputs().size());
        assertFluidIngredient(ender.getOutputs().getFirst(), enderFluid, 250L);

        RecipeHolder<DataRipperReassemblerRecipe> woolFusionHolder = loadRecipe("mysterious_wool_fusion");
        assertSame(
                Items.WHITE_WOOL,
                woolFusionHolder.value().getItemInputs().getFirst().ingredient().getItems()[0].getItem());
        DataRipperReassemblerEmiRecipe woolFusion = new DataRipperReassemblerEmiRecipe(woolFusionHolder);
        assertRecipeIdentity(woolFusion, "mysterious_wool_fusion");
        List<EmiIngredient> woolFusionInputs = woolFusion.getInputs();
        assertEquals(12, woolFusionInputs.size());
        assertItemIngredient(findItemIngredient(woolFusionInputs, Items.WHITE_WOOL), 1L, Items.WHITE_WOOL);
        assertItemIngredient(findItemIngredient(woolFusionInputs, Items.LIGHT_GRAY_WOOL), 1L, Items.LIGHT_GRAY_WOOL);
        assertItemIngredient(findItemIngredient(woolFusionInputs, Items.GRAY_WOOL), 1L, Items.GRAY_WOOL);
        assertItemIngredient(findItemIngredient(woolFusionInputs, Items.BLACK_WOOL), 1L, Items.BLACK_WOOL);
        assertItemIngredient(findItemIngredient(woolFusionInputs, Items.BROWN_WOOL), 1L, Items.BROWN_WOOL);
        assertItemIngredient(findItemIngredient(woolFusionInputs, Items.RED_WOOL), 1L, Items.RED_WOOL);
        assertItemIngredient(findItemIngredient(woolFusionInputs, Items.ORANGE_WOOL), 1L, Items.ORANGE_WOOL);
        assertItemIngredient(findItemIngredient(woolFusionInputs, Items.YELLOW_WOOL), 1L, Items.YELLOW_WOOL);
        assertItemIngredient(findItemIngredient(woolFusionInputs, Items.LIME_WOOL), 1L, Items.LIME_WOOL);
        assertFluidIngredient(findFluidIngredient(woolFusionInputs, Fluids.WATER), Fluids.WATER, 1_000L);
        assertFluidIngredient(findFluidIngredient(woolFusionInputs, Fluids.LAVA), Fluids.LAVA, 1_000L);
        assertDataIngredient(
                findDataIngredient(woolFusionInputs, DataResourceEmiKey.DATA_FLOW),
                DataResourceEmiKey.DATA_FLOW,
                1_200L);

        List<EmiStack> woolFusionOutputs = woolFusion.getOutputs();
        assertEquals(6, woolFusionOutputs.size());
        Item mysteriousCube = registeredItem("ae2:mysterious_cube");
        Item notSoMysteriousCube = registeredItem("ae2:not_so_mysterious_cube");
        Item quartzFixture = registeredItem("ae2:quartz_fixture");
        assertItemIngredient(findItemIngredient(woolFusionOutputs, mysteriousCube), 1L, mysteriousCube);
        assertItemIngredient(findItemIngredient(woolFusionOutputs, notSoMysteriousCube), 1L, notSoMysteriousCube);
        assertItemIngredient(findItemIngredient(woolFusionOutputs, quartzFixture), 1L, quartzFixture);
        assertFluidIngredient(findFluidIngredient(woolFusionOutputs, Fluids.WATER), Fluids.WATER, 5_000L);
        assertFluidIngredient(findFluidIngredient(woolFusionOutputs, Fluids.LAVA), Fluids.LAVA, 5_000L);
        assertDataIngredient(
                findDataIngredient(woolFusionOutputs, DataResourceEmiKey.DATA_FLOW),
                DataResourceEmiKey.DATA_FLOW,
                200L);

        DataRipperReassemblerEmiRecipe dataExtractor = loadEmiRecipe("data_extractor");
        assertRecipeIdentity(dataExtractor, "data_extractor");
        List<EmiIngredient> dataExtractorInputs = dataExtractor.getInputs();
        assertEquals(7, dataExtractorInputs.size());
        assertItemIngredient(
                findItemIngredient(dataExtractorInputs, Items.OBSIDIAN),
                12L,
                Items.OBSIDIAN,
                testObsidianIngot);
        assertItemIngredient(
                findItemIngredient(dataExtractorInputs, registeredItem("ae2:spatial_io_port")),
                1L,
                registeredItem("ae2:spatial_io_port"));
        assertItemIngredient(
                findItemIngredient(dataExtractorInputs, registeredItem("ae2:molecular_assembler")),
                4L,
                registeredItem("ae2:molecular_assembler"));
        assertItemIngredient(
                findItemIngredient(dataExtractorInputs, registeredItem("data_energistics:data_meteorite_compass")),
                1L,
                registeredItem("data_energistics:data_meteorite_compass"));
        assertItemIngredient(
                findItemIngredient(dataExtractorInputs, registeredItem("ae2:energy_acceptor")),
                2L,
                registeredItem("ae2:energy_acceptor"));
        assertItemIngredient(
                findItemIngredient(dataExtractorInputs, registeredItem("data_energistics:data_framework")),
                8L,
                registeredItem("data_energistics:data_framework"));
        assertDataIngredient(
                findDataIngredient(dataExtractorInputs, DataResourceEmiKey.DATA),
                DataResourceEmiKey.DATA,
                35L);
        assertEquals(1, dataExtractor.getOutputs().size());
        Item dataExtractorItem = registeredItem("data_energistics:data_extractor");
        assertItemIngredient(
                findItemIngredient(dataExtractor.getOutputs(), dataExtractorItem),
                1L,
                dataExtractorItem);
    }

    @Test
    void createsProductionRecipeSlotsForMaximumShippedRecipe() throws IOException {
        DataRipperReassemblerEmiRecipe recipe = loadEmiRecipe("mysterious_wool_fusion");
        var holder = new RecordingWidgetHolderImpl();

        recipe.addWidgets(holder);

        List<EMIRecipeSlotWidget> slots = holder.widgets().stream()
                .filter(EMIRecipeSlotWidget.class::isInstance)
                .map(EMIRecipeSlotWidget.class::cast)
                .toList();
        assertEquals(18, slots.size());
        assertEquals(19, holder.widgets().size());
        assertInstanceOf(ModularUIEMIWidget.class, holder.widgets().getLast());

        Map<Bounds, EMIRecipeSlotWidget> slotsByBounds = slots.stream()
                .collect(Collectors.toMap(EMIRecipeSlotWidget::getBounds, slot -> slot));
        assertEquals(18, slotsByBounds.size());

        Map<Bounds, Item> expectedItemInputs = Map.ofEntries(
                Map.entry(new Bounds(8, 3, 18, 18), Items.WHITE_WOOL),
                Map.entry(new Bounds(26, 3, 18, 18), Items.LIGHT_GRAY_WOOL),
                Map.entry(new Bounds(44, 3, 18, 18), Items.GRAY_WOOL),
                Map.entry(new Bounds(8, 21, 18, 18), Items.BLACK_WOOL),
                Map.entry(new Bounds(26, 21, 18, 18), Items.BROWN_WOOL),
                Map.entry(new Bounds(44, 21, 18, 18), Items.RED_WOOL),
                Map.entry(new Bounds(8, 39, 18, 18), Items.ORANGE_WOOL),
                Map.entry(new Bounds(26, 39, 18, 18), Items.YELLOW_WOOL),
                Map.entry(new Bounds(44, 39, 18, 18), Items.LIME_WOOL));
        expectedItemInputs.forEach((bounds, item) -> assertItemIngredient(slotAt(slotsByBounds, bounds).getStack(), 1L, item));

        Bounds waterInputBounds = new Bounds(63, 3, 18, 18);
        Bounds lavaInputBounds = new Bounds(63, 39, 18, 18);
        Bounds dataFlowInputBounds = new Bounds(63, 21, 18, 18);
        assertFluidIngredient(slotAt(slotsByBounds, waterInputBounds).getStack(), Fluids.WATER, 1_000L);
        assertFluidIngredient(slotAt(slotsByBounds, lavaInputBounds).getStack(), Fluids.LAVA, 1_000L);
        assertDataIngredient(
                slotAt(slotsByBounds, dataFlowInputBounds).getStack(),
                DataResourceEmiKey.DATA_FLOW,
                1_200L);

        Map<Bounds, Item> expectedItemOutputs = Map.of(
                new Bounds(114, 3, 18, 18), registeredItem("ae2:mysterious_cube"),
                new Bounds(114, 21, 18, 18), registeredItem("ae2:not_so_mysterious_cube"),
                new Bounds(114, 39, 18, 18), registeredItem("ae2:quartz_fixture"));
        expectedItemOutputs.forEach((bounds, item) -> assertItemIngredient(slotAt(slotsByBounds, bounds).getStack(), 1L, item));

        Bounds waterOutputBounds = new Bounds(132, 3, 18, 18);
        Bounds lavaOutputBounds = new Bounds(132, 39, 18, 18);
        Bounds dataFlowOutputBounds = new Bounds(132, 21, 18, 18);
        assertFluidIngredient(slotAt(slotsByBounds, waterOutputBounds).getStack(), Fluids.WATER, 5_000L);
        assertFluidIngredient(slotAt(slotsByBounds, lavaOutputBounds).getStack(), Fluids.LAVA, 5_000L);
        assertDataIngredient(
                slotAt(slotsByBounds, dataFlowOutputBounds).getStack(),
                DataResourceEmiKey.DATA_FLOW,
                200L);

        List<Bounds> outputBounds = new ArrayList<>(expectedItemOutputs.keySet());
        outputBounds.addAll(List.of(waterOutputBounds, lavaOutputBounds, dataFlowOutputBounds));
        for (Bounds bounds : outputBounds) {
            assertSame(recipe, slotAt(slotsByBounds, bounds).getRecipe(), "Output slot " + bounds + " recipe context");
        }

        List<Bounds> inputBounds = new ArrayList<>(expectedItemInputs.keySet());
        inputBounds.addAll(List.of(waterInputBounds, lavaInputBounds, dataFlowInputBounds));
        for (Bounds bounds : inputBounds) {
            assertNull(slotAt(slotsByBounds, bounds).getRecipe(), "Input slot " + bounds + " recipe context");
        }
    }

    @SuppressWarnings("deprecation")
    private static void registerRecipeDependencies() {
        MappedRegistry<Block> blockRegistry = (MappedRegistry<Block>) BuiltInRegistries.BLOCK;
        Block reassemblerBlock;
        blockRegistry.unfreeze();
        try {
            reassemblerBlock = Registry.register(
                    blockRegistry,
                    CATEGORY_ID,
                    new DataRipperReassemblerMainBlock(BlockBehaviour.Properties.ofFullCopy(Blocks.IRON_BLOCK)));
        } finally {
            blockRegistry.freeze();
        }

        MappedRegistry<Item> itemRegistry = (MappedRegistry<Item>) BuiltInRegistries.ITEM;
        itemRegistry.unfreeze();
        try {
            Registry.register(
                    itemRegistry,
                    CATEGORY_ID,
                    new BlockItem(reassemblerBlock, new Item.Properties()));
            registerItem(itemRegistry, "ae2:mysterious_cube");
            registerItem(itemRegistry, "ae2:not_so_mysterious_cube");
            registerItem(itemRegistry, "ae2:quartz_fixture");
            registerItem(itemRegistry, "ae2:spatial_io_port");
            registerItem(itemRegistry, "ae2:molecular_assembler");
            registerItem(itemRegistry, "ae2:energy_acceptor");
            registerItem(itemRegistry, "data_energistics:data_meteorite_compass");
            registerItem(itemRegistry, "data_energistics:data_framework");
            registerItem(itemRegistry, "data_energistics:data_extractor");
            testObsidianIngot = Registry.register(
                    itemRegistry,
                    TEST_OBSIDIAN_INGOT_ID,
                    new Item(new Item.Properties()));
            itemRegistry.bindTags(Map.of(
                    OBSIDIAN_INGOT_TAG,
                    List.of(itemRegistry.wrapAsHolder(Items.OBSIDIAN), itemRegistry.wrapAsHolder(testObsidianIngot))));
        } finally {
            itemRegistry.freeze();
        }

        MappedRegistry<Fluid> fluidRegistry = (MappedRegistry<Fluid>) BuiltInRegistries.FLUID;
        fluidRegistry.unfreeze();
        try {
            AtomicReference<Fluid> fluidReference = new AtomicReference<>();
            var properties = new BaseFlowingFluid.Properties(
                    TestFluidTypeImpl::new,
                    fluidReference::get,
                    fluidReference::get);
            Fluid fluid = new BaseFlowingFluid.Source(properties);
            fluidReference.set(fluid);
            enderFluid = Registry.register(fluidRegistry, Data_Energistics.id("ender"), fluid);
        } finally {
            fluidRegistry.freeze();
        }
    }

    private static void registerAeKeyTypes() {
        Registry<AEKeyType> registry = new RegistryBuilder<>(AEKeyType.REGISTRY_KEY)
                .sync(true)
                .maxId(127)
                .create();
        AEKeyTypesInternal.setRegistry(registry);
        Registry.register(registry, AEKeyType.items().getId(), AEKeyType.items());
        Registry.register(registry, AEKeyType.fluids().getId(), AEKeyType.fluids());
        Registry.register(registry, DataKeyType.TYPE.getId(), DataKeyType.TYPE);
        Registry.register(registry, DataFlowKeyType.TYPE.getId(), DataFlowKeyType.TYPE);
        registry.freeze();
    }

    private static void registerItem(MappedRegistry<Item> registry, String id) {
        Registry.register(registry, ResourceLocation.parse(id), new Item(new Item.Properties()));
    }

    private static DataRipperReassemblerEmiRecipe loadEmiRecipe(String name) throws IOException {
        return new DataRipperReassemblerEmiRecipe(loadRecipe(name));
    }

    private static RecipeHolder<DataRipperReassemblerRecipe> loadRecipe(String name) throws IOException {
        String resourcePath = RECIPE_RESOURCE_ROOT + name + ".json";
        InputStream input = DataRipperReassemblerEmiRecipeTest.class.getResourceAsStream(resourcePath);
        assertNotNull(input, resourcePath);
        try (Reader reader = new InputStreamReader(input, StandardCharsets.UTF_8)) {
            RegistryAccess registries = RegistryAccess.fromRegistryOfRegistries(BuiltInRegistries.REGISTRY);
            DataRipperReassemblerRecipe recipe = new DataRipperReassemblerRecipeSerializer()
                    .codec()
                    .codec()
                    .parse(RegistryOps.create(JsonOps.INSTANCE, registries), JsonParser.parseReader(reader))
                    .getOrThrow();
            return new RecipeHolder<>(recipeId(name), recipe);
        }
    }

    private static ResourceLocation recipeId(String name) {
        return Data_Energistics.id("data_energistics/data_reassembler/" + name);
    }

    private static Item registeredItem(String id) {
        return BuiltInRegistries.ITEM.getOptional(ResourceLocation.parse(id)).orElseThrow();
    }

    private static void assertRecipeIdentity(DataRipperReassemblerEmiRecipe recipe, String name) {
        assertEquals(recipeId(name), recipe.getId());
        assertEquals(CATEGORY_ID, recipe.getCategory().getId());
    }

    private static void assertItemIngredient(EmiIngredient ingredient, long expectedAmount, Item... expectedItems) {
        List<EmiStack> actualStacks = ingredient.getEmiStacks();
        assertEquals(expectedAmount, ingredient.getAmount());
        assertEquals(expectedItems.length, actualStacks.size());
        for (int index = 0; index < expectedItems.length; index++) {
            EmiStack actual = actualStacks.get(index);
            assertSame(expectedItems[index], actual.getItemStack().getItem(), "Item alternative " + index + " identity");
            assertEquals(
                    new ItemStack(expectedItems[index]).getComponentsPatch(),
                    actual.getComponentChanges(),
                    "Item alternative " + index + " components");
        }
    }

    private static void assertFluidIngredient(EmiIngredient ingredient, Fluid expectedFluid, long expectedAmount) {
        EmiStack stack = singleStack(ingredient);
        assertSame(expectedFluid, stack.getKeyOfType(Fluid.class));
        assertEquals(expectedAmount, stack.getAmount());
    }

    private static void assertDataIngredient(EmiIngredient ingredient,
                                             DataResourceEmiKey expectedKey,
                                             long expectedAmount) {
        DataResourceEmiStack stack = assertInstanceOf(DataResourceEmiStack.class, singleStack(ingredient));
        assertEquals(expectedKey, stack.getKey());
        assertEquals(expectedAmount, stack.getAmount());
    }

    private static EmiStack singleStack(EmiIngredient ingredient) {
        List<EmiStack> stacks = ingredient.getEmiStacks();
        assertEquals(1, stacks.size());
        return stacks.getFirst();
    }

    private static EmiIngredient findItemIngredient(List<? extends EmiIngredient> ingredients, Item item) {
        return findIngredient(
                ingredients,
                ingredient -> ingredient.getEmiStacks().stream()
                        .anyMatch(stack -> stack.getItemStack().is(item)),
                "item " + BuiltInRegistries.ITEM.getKey(item));
    }

    private static EmiIngredient findFluidIngredient(List<? extends EmiIngredient> ingredients, Fluid fluid) {
        return findIngredient(
                ingredients,
                ingredient -> ingredient.getEmiStacks().stream()
                        .anyMatch(stack -> stack.getKeyOfType(Fluid.class) == fluid),
                "fluid " + BuiltInRegistries.FLUID.getKey(fluid));
    }

    private static EmiIngredient findDataIngredient(
                                                    List<? extends EmiIngredient> ingredients,
                                                    DataResourceEmiKey key) {
        return findIngredient(
                ingredients,
                ingredient -> ingredient.getEmiStacks().stream()
                        .anyMatch(stack -> stack instanceof DataResourceEmiStack dataStack && dataStack.getKey().equals(key)),
                "data resource " + key);
    }

    private static EmiIngredient findIngredient(
                                                List<? extends EmiIngredient> ingredients,
                                                Predicate<EmiIngredient> predicate,
                                                String description) {
        var matches = ingredients.stream().filter(predicate).toList();
        assertEquals(1, matches.size(), "EMI ingredient matches for " + description);
        return matches.getFirst();
    }

    private static EMIRecipeSlotWidget slotAt(Map<Bounds, EMIRecipeSlotWidget> slots, Bounds bounds) {
        EMIRecipeSlotWidget slot = slots.get(bounds);
        assertNotNull(slot, "Recipe slot " + bounds);
        return slot;
    }

    private static final class TestFluidTypeImpl extends FluidType {

        private TestFluidTypeImpl() {
            super(Properties.create().descriptionId("fluid.data_energistics.ender"));
        }
    }

    private static final class RecordingWidgetHolderImpl implements WidgetHolder {

        private final List<Widget> widgets = new ArrayList<>();

        @Override
        public int getWidth() {
            return 162;
        }

        @Override
        public int getHeight() {
            return 58;
        }

        @Override
        public <T extends Widget> T add(T widget) {
            widgets.add(widget);
            return widget;
        }

        private List<Widget> widgets() {
            return widgets;
        }
    }
}

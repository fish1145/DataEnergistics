package com.fish_dan_.data_energistics.client.emi;

import com.fish_dan_.data_energistics.common.multiblock.json.JsonMultiBlockStructureKey;
import com.fish_dan_.data_energistics.common.multiblock.preview.MultiblockRecipeView;
import com.fish_dan_.data_energistics.common.multiblock.preview.MultiblockRecipeViewSource;
import com.fish_dan_.data_energistics.common.multiblock.preview.PreviewMaterial;
import com.fish_dan_.data_energistics.common.multiblock.preview.ProjectionFingerprint;
import com.fish_dan_.data_energistics.menu.common.PatternEncodingMultiblockTransferState;
import com.fish_dan_.data_energistics.menu.common.PatternEncodingMultiblockTransferTarget;

import net.minecraft.SharedConstants;
import net.minecraft.core.MappedRegistry;
import net.minecraft.core.Registry;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.neoforged.fml.loading.LoadingModList;
import net.neoforged.neoforge.registries.RegistryBuilder;

import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.AEKeyType;
import appeng.api.stacks.AEKeyTypesInternal;
import appeng.integration.modules.emi.EmiEncodePatternHandler;
import appeng.menu.me.items.PatternEncodingTermMenu;
import appeng.parts.encoding.EncodingMode;
import appeng.util.ConfigInventory;
import dev.emi.emi.api.recipe.EmiRecipe;
import dev.emi.emi.api.recipe.EmiRecipeCategory;
import dev.emi.emi.api.recipe.VanillaEmiRecipeCategories;
import dev.emi.emi.api.recipe.handler.EmiCraftContext;
import dev.emi.emi.api.stack.EmiIngredient;
import dev.emi.emi.api.stack.EmiStack;
import dev.emi.emi.api.widget.WidgetHolder;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

public final class EmiMultiblockPatternTransferHandlerTest {

    static {
        if (LoadingModList.get() == null) {
            LoadingModList.of(List.of(), List.of(), List.of(), List.of(), Map.of());
        }
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
        initializeAeKeyTypes();
    }

    private static void initializeAeKeyTypes() {
        synchronized (AEKeyTypesInternal.class) {
            try {
                AEKeyTypesInternal.getRegistry();
                return;
            } catch (IllegalStateException notInitialized) {
                Registry<AEKeyType> registry = new RegistryBuilder<>(AEKeyType.REGISTRY_KEY)
                        .disableRegistrationCheck()
                        .create();
                AEKeyTypesInternal.setRegistry(registry);
                Registry.register(registry, AEKeyType.items().getId(), AEKeyType.items());
                Registry.register(registry, AEKeyType.fluids().getId(), AEKeyType.fluids());
                ((MappedRegistry<AEKeyType>) registry).freeze();
            }
        }
    }

    private static final ResourceLocation CONTROLLER_ID = ResourceLocation.parse("data_energistics:emi_transfer_test");
    private static final ResourceLocation REGISTERED_RECIPE_ID = MultiblockRecipeView.registeredRecipeIdFor(CONTROLLER_ID);

    @Test
    void everyHandlerEntryPointSamplesTheCurrentTypedViewExactlyOnce() {
        MultiblockRecipeView supported = recipe(20L, 1);
        MultiblockRecipeView craftability = recipe(21L, 2);
        MultiblockRecipeView tooltip = recipe(22L, 3);
        MultiblockRecipeView craft = recipe(23L, 4);
        TrackingTypedRecipe recipe = new TrackingTypedRecipe(supported);
        EmiMultiblockPatternTransferHandler<PatternEncodingTermMenu> handler = new EmiMultiblockPatternTransferHandler<>(PatternEncodingTermMenu.class);
        EmiCraftContext<PatternEncodingTermMenu> fillButton = new EmiCraftContext<>(
                null,
                null,
                EmiCraftContext.Type.FILL_BUTTON);

        assertTrue(handler.supportsRecipe(recipe));
        assertSame(supported, recipe.lastReadView);
        recipe.publish(craftability);
        assertFalse(handler.canCraft(recipe, fillButton));
        assertSame(craftability, recipe.lastReadView);
        recipe.publish(tooltip);
        assertFalse(handler.getTooltip(recipe, fillButton).isEmpty());
        assertSame(tooltip, recipe.lastReadView);
        recipe.publish(craft);
        assertFalse(handler.craft(recipe, fillButton));
        assertSame(craft, recipe.lastReadView);

        assertEquals(4, recipe.liveViewReads);
        assertEquals(0, recipe.cachedInputReads);
        assertEquals(0, recipe.cachedOutputReads);
    }

    @Test
    void typedHandlerAlwaysReadsTheLiveViewAndNeverReadsCachedEmiIngredients() {
        MultiblockRecipeView first = recipe(1L, 2);
        MultiblockRecipeView second = recipe(2L, 3);
        TrackingTypedRecipe recipe = new TrackingTypedRecipe(first);
        EmiMultiblockPatternTransferHandler<PatternEncodingTermMenu> handler = new EmiMultiblockPatternTransferHandler<>(PatternEncodingTermMenu.class);

        assertTrue(handler.supportsRecipe(recipe));
        assertEquals(1, recipe.liveViewReads);

        EmiMultiblockPatternTransfer.LiveView firstResolution = EmiMultiblockPatternTransfer.resolve(recipe);
        assertSame(first, firstResolution.view());
        recipe.publish(second);
        EmiMultiblockPatternTransfer.LiveView secondResolution = EmiMultiblockPatternTransfer.resolve(recipe);
        assertSame(second, secondResolution.view());

        TrackingTarget target = new TrackingTarget(81, 27);
        EmiMultiblockPatternTransfer.TransferCheck check = EmiMultiblockPatternTransfer.validate(secondResolution, target);
        assertTrue(check.canTransfer());
        assertEquals(1, target.inputInventoryReads);
        assertEquals(1, target.outputInventoryReads);

        assertTrue(check.transfer());

        assertSame(second, target.requestedView);
        assertEquals(1, target.requestCount);
        assertEquals(1, target.inputInventoryReads);
        assertEquals(1, target.outputInventoryReads);
        assertEquals(0, recipe.cachedInputReads);
        assertEquals(0, recipe.cachedOutputReads);
        assertEquals(3, recipe.liveViewReads);
    }

    @Test
    void realInputCapacityRejectsEightyTwoWithoutTruncatingAndAcceptsEightyOne() {
        TrackingTypedRecipe recipe = new TrackingTypedRecipe(recipe(7L, 82));
        TrackingTarget target = new TrackingTarget(81, 27);

        EmiMultiblockPatternTransfer.TransferCheck rejected = EmiMultiblockPatternTransfer.validate(
                EmiMultiblockPatternTransfer.resolve(recipe),
                target);

        assertFalse(rejected.canTransfer());
        assertTrue(rejected.error().getString().contains("82"));
        assertTrue(rejected.error().getString().contains("81"));
        assertFalse(rejected.transfer());
        assertEquals(0, target.requestCount);

        MultiblockRecipeView fitting = recipe(8L, 81);
        recipe.publish(fitting);
        EmiMultiblockPatternTransfer.TransferCheck accepted = EmiMultiblockPatternTransfer.validate(
                EmiMultiblockPatternTransfer.resolve(recipe),
                target);
        assertTrue(accepted.canTransfer());

        assertTrue(accepted.transfer());

        assertSame(fitting, target.requestedView);
        assertEquals(81, target.requestedView.inputs().size());
        assertEquals(1, target.requestCount);
        assertEquals(0, recipe.cachedInputReads);
        assertEquals(0, recipe.cachedOutputReads);
    }

    @Test
    void staleTypedSourceRemainsOwnedByTheTypedHandlerButCannotTransfer() {
        TrackingTypedRecipe recipe = new TrackingTypedRecipe(recipe(11L, 1));
        EmiMultiblockPatternTransferHandler<PatternEncodingTermMenu> handler = new EmiMultiblockPatternTransferHandler<>(PatternEncodingTermMenu.class);
        recipe.failWith(new AssertionError("catalog revision changed"));

        assertTrue(handler.supportsRecipe(recipe));
        EmiMultiblockPatternTransfer.LiveView stale = EmiMultiblockPatternTransfer.resolve(recipe);

        assertTrue(stale.applicable());
        assertFalse(stale.ready());
        assertTrue(stale.error().getString().contains("changed"));
        EmiMultiblockPatternTransfer.TransferCheck rejected = EmiMultiblockPatternTransfer.validate(stale, new TrackingTarget(81, 27));
        assertFalse(rejected.canTransfer());
        assertEquals(0, recipe.cachedInputReads);
        assertEquals(0, recipe.cachedOutputReads);
        assertEquals(2, recipe.liveViewReads);
    }

    @Test
    void inventoryAndRequestFailuresAreLoggedAndReportedAsRejected() {
        TrackingTypedRecipe recipe = new TrackingTypedRecipe(recipe(12L, 1));
        EmiMultiblockPatternTransfer.LiveView liveView = EmiMultiblockPatternTransfer.resolve(recipe);
        TrackingTarget inventoryFailure = new TrackingTarget(81, 27);
        inventoryFailure.inputInventoryFailure = new IllegalStateException("input inventory unavailable");

        EmiMultiblockPatternTransfer.TransferCheck rejected = EmiMultiblockPatternTransfer.validate(liveView, inventoryFailure);

        assertFalse(rejected.canTransfer());
        assertTrue(rejected.error().getString().contains("could not validate"));

        TrackingTarget requestFailure = new TrackingTarget(81, 27);
        EmiMultiblockPatternTransfer.TransferCheck ready = EmiMultiblockPatternTransfer.validate(liveView, requestFailure);
        requestFailure.requestFailure = new AssertionError("network request unavailable");

        assertTrue(ready.canTransfer());
        assertFalse(ready.transfer());
        assertEquals(1, requestFailure.requestCount);
        assertEquals(0, recipe.cachedInputReads);
        assertEquals(0, recipe.cachedOutputReads);
        assertEquals(1, recipe.liveViewReads);
    }

    @Test
    void ae2CatchAllHandlerDefersOnlyTypedMultiblockRecipes() {
        TrackingTypedRecipe typedRecipe = new TrackingTypedRecipe(recipe(13L, 1));
        EmiEncodePatternHandler<PatternEncodingTermMenu> ae2Handler = new EmiEncodePatternHandler<>(PatternEncodingTermMenu.class);
        EmiMultiblockPatternTransferHandler<PatternEncodingTermMenu> typedHandler = new EmiMultiblockPatternTransferHandler<>(PatternEncodingTermMenu.class);
        EmiRecipe ordinaryRecipe = new OrdinaryRecipe();

        assertTrue(EmiEncodePatternHandlerMultiblockTransferGuard.shouldDefer(ae2Handler, typedRecipe));
        assertFalse(EmiEncodePatternHandlerMultiblockTransferGuard.shouldDefer(ae2Handler, ordinaryRecipe));
        assertFalse(EmiEncodePatternHandlerMultiblockTransferGuard.shouldDefer(typedHandler, typedRecipe));
        assertFalse(EmiEncodePatternHandlerMultiblockTransferGuard.shouldDefer(new Object(), typedRecipe));
        assertFalse(EmiEncodePatternHandlerMultiblockTransferGuard.shouldDefer(ae2Handler, null));
        assertEquals(0, typedRecipe.cachedInputReads);
        assertEquals(0, typedRecipe.cachedOutputReads);
        assertEquals(0, typedRecipe.liveViewReads);
    }

    @Test
    void typedTransferAcceptsOnlyTheExplicitFillButtonContext() {
        EmiCraftContext<PatternEncodingTermMenu> fillButton = new EmiCraftContext<>(
                null,
                null,
                EmiCraftContext.Type.FILL_BUTTON);
        EmiCraftContext<PatternEncodingTermMenu> craftable = new EmiCraftContext<>(
                null,
                null,
                EmiCraftContext.Type.CRAFTABLE);

        assertTrue(EmiMultiblockPatternTransferHandler.isFillButtonContext(fillButton));
        assertFalse(EmiMultiblockPatternTransferHandler.isFillButtonContext(craftable));
        assertFalse(EmiMultiblockPatternTransferHandler.isFillButtonContext(null));
    }

    private static MultiblockRecipeView recipe(long revision, int inputCount) {
        List<PreviewMaterial> inputs = new ArrayList<>(inputCount);
        for (int index = 0; index < inputCount; index++) {
            ItemStack stack = new ItemStack(Items.STONE);
            stack.set(DataComponents.CUSTOM_NAME, Component.literal(
                    "emi-input-" + revision + "-" + index));
            AEItemKey key = AEItemKey.of(stack);
            if (key == null) {
                throw new IllegalStateException("Test input did not produce an AE item key");
            }
            inputs.add(new PreviewMaterial(key, 1L));
        }
        AEItemKey outputKey = AEItemKey.of(Items.CRAFTING_TABLE);
        if (outputKey == null) {
            throw new IllegalStateException("Test output did not produce an AE item key");
        }
        ProjectionFingerprint fingerprint = new ProjectionFingerprint(
                CONTROLLER_ID,
                revision,
                new JsonMultiBlockStructureKey(CONTROLLER_ID, "main"),
                0,
                List.of(1),
                Map.of(),
                Map.of());
        return new MultiblockRecipeView(
                REGISTERED_RECIPE_ID,
                CONTROLLER_ID,
                "main",
                revision,
                fingerprint,
                inputs,
                new PreviewMaterial(outputKey, 1L));
    }

    private static final class TrackingTarget implements PatternEncodingMultiblockTransferTarget {

        private final ConfigInventory inputs;
        private final ConfigInventory outputs;
        private int inputInventoryReads;
        private int outputInventoryReads;
        private int requestCount;
        private MultiblockRecipeView requestedView;
        private RuntimeException inputInventoryFailure;
        private Error requestFailure;

        private TrackingTarget(int inputSize, int outputSize) {
            this.inputs = ConfigInventory.configStacks(inputSize)
                    .allowOverstacking(true)
                    .build();
            this.outputs = ConfigInventory.configStacks(outputSize)
                    .allowOverstacking(true)
                    .build();
        }

        @Override
        public void data_energistics$requestMultiblockTransfer(MultiblockRecipeView recipe) {
            this.requestCount++;
            if (this.requestFailure != null) {
                throw this.requestFailure;
            }
            this.requestedView = recipe;
        }

        @Override
        public ConfigInventory data_energistics$getMultiblockTransferInputInventory() {
            this.inputInventoryReads++;
            if (this.inputInventoryFailure != null) {
                throw this.inputInventoryFailure;
            }
            return this.inputs;
        }

        @Override
        public ConfigInventory data_energistics$getMultiblockTransferOutputInventory() {
            this.outputInventoryReads++;
            return this.outputs;
        }

        @Override
        public EncodingMode data_energistics$getMultiblockTransferEncodingMode() {
            return EncodingMode.PROCESSING;
        }

        @Override
        public void data_energistics$setMultiblockTransferEncodingMode(EncodingMode mode) {
            throw new AssertionError("Client EMI transfer must not change the encoding mode directly");
        }

        @Override
        public PatternEncodingMultiblockTransferState data_energistics$snapshotMultiblockTransferState() {
            throw new AssertionError("Client EMI transfer must not snapshot server source state");
        }

        @Override
        public void data_energistics$clearMultiblockTransferState() {
            throw new AssertionError("Client EMI transfer must not clear server source state");
        }

        @Override
        public void data_energistics$restoreMultiblockTransferState(
                                                                    PatternEncodingMultiblockTransferState state) {
            throw new AssertionError("Client EMI transfer must not restore server source state");
        }

        @Override
        public void data_energistics$invalidateMultiblockTransferTarget() {
            throw new AssertionError("Client EMI transfer must not invalidate the menu");
        }
    }

    private static final class TrackingTypedRecipe implements EmiRecipe, MultiblockRecipeViewSource {

        private MultiblockRecipeView currentView;
        private Throwable liveFailure;
        private MultiblockRecipeView lastReadView;
        private int liveViewReads;
        private int cachedInputReads;
        private int cachedOutputReads;

        private TrackingTypedRecipe(MultiblockRecipeView currentView) {
            this.currentView = currentView;
        }

        private void publish(MultiblockRecipeView currentView) {
            this.currentView = currentView;
            this.liveFailure = null;
        }

        private void failWith(Throwable liveFailure) {
            if (!(liveFailure instanceof RuntimeException) && !(liveFailure instanceof Error)) {
                throw new IllegalArgumentException("Live test failure must be unchecked");
            }
            this.liveFailure = liveFailure;
        }

        @Override
        public ResourceLocation registeredRecipeId() {
            return REGISTERED_RECIPE_ID;
        }

        @Override
        public MultiblockRecipeView currentRecipeView() {
            this.liveViewReads++;
            this.lastReadView = this.currentView;
            if (this.liveFailure instanceof RuntimeException runtimeFailure) {
                throw runtimeFailure;
            }
            if (this.liveFailure instanceof Error error) {
                throw error;
            }
            return this.currentView;
        }

        @Override
        public EmiRecipeCategory getCategory() {
            return VanillaEmiRecipeCategories.CRAFTING;
        }

        @Override
        public ResourceLocation getId() {
            return REGISTERED_RECIPE_ID;
        }

        @Override
        public List<EmiIngredient> getInputs() {
            this.cachedInputReads++;
            throw new AssertionError("Typed EMI transfer must not read cached EMI inputs");
        }

        @Override
        public List<EmiStack> getOutputs() {
            this.cachedOutputReads++;
            throw new AssertionError("Typed EMI transfer must not read cached EMI outputs");
        }

        @Override
        public int getDisplayWidth() {
            return 1;
        }

        @Override
        public int getDisplayHeight() {
            return 1;
        }

        @Override
        public void addWidgets(WidgetHolder widgets) {
            throw new AssertionError("Widget construction is outside the EMI transfer test");
        }

        @Override
        public RecipeHolder<?> getBackingRecipe() {
            return null;
        }
    }

    private static final class OrdinaryRecipe implements EmiRecipe {

        @Override
        public EmiRecipeCategory getCategory() {
            return VanillaEmiRecipeCategories.CRAFTING;
        }

        @Override
        public ResourceLocation getId() {
            return ResourceLocation.parse("data_energistics:ordinary_emi_test");
        }

        @Override
        public List<EmiIngredient> getInputs() {
            return List.of();
        }

        @Override
        public List<EmiStack> getOutputs() {
            return List.of();
        }

        @Override
        public int getDisplayWidth() {
            return 1;
        }

        @Override
        public int getDisplayHeight() {
            return 1;
        }

        @Override
        public void addWidgets(WidgetHolder widgets) {
            throw new AssertionError("Widget construction is outside the EMI transfer guard test");
        }

        @Override
        public RecipeHolder<?> getBackingRecipe() {
            return null;
        }
    }
}

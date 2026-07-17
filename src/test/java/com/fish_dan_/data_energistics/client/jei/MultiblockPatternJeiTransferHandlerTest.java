package com.fish_dan_.data_energistics.client.jei;

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
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.flag.FeatureFlags;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.fml.loading.LoadingModList;
import net.neoforged.neoforge.registries.RegistryBuilder;

import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.AEKeyType;
import appeng.api.stacks.AEKeyTypesInternal;
import appeng.parts.encoding.EncodingMode;
import appeng.util.ConfigInventory;
import mezz.jei.api.gui.ingredient.IRecipeSlotView;
import mezz.jei.api.gui.ingredient.IRecipeSlotsView;
import mezz.jei.api.recipe.RecipeType;
import mezz.jei.api.recipe.transfer.IRecipeTransferError;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;

class MultiblockPatternJeiTransferHandlerTest {

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
            Registry<AEKeyType> registry;
            try {
                AEKeyTypesInternal.getRegistry();
                return;
            } catch (IllegalStateException notInitialized) {
                registry = new RegistryBuilder<AEKeyType>(AEKeyType.REGISTRY_KEY)
                        .disableRegistrationCheck()
                        .create();
            }

            AEKeyTypesInternal.setRegistry(registry);
            Registry.register(registry, AEKeyType.items().getId(), AEKeyType.items());
            Registry.register(registry, AEKeyType.fluids().getId(), AEKeyType.fluids());
            ((MappedRegistry<AEKeyType>) registry).freeze();
        }
    }

    private static final ResourceLocation CONTROLLER_ID = ResourceLocation.fromNamespaceAndPath(
            "data_energistics",
            "jei_transfer_test");
    private static final RecipeType<FakeRecipeSource> RECIPE_TYPE = new RecipeType<>(
            MultiblockRecipeView.registeredRecipeIdFor(CONTROLLER_ID),
            FakeRecipeSource.class);
    private static final MenuType<FakeMenu> MENU_TYPE = new MenuType<>(
            (containerId, inventory) -> new FakeMenu(containerId, 81, 27),
            FeatureFlags.VANILLA_SET);
    private static final MenuType<UnsupportedMenu> UNSUPPORTED_MENU_TYPE = new MenuType<>(
            (containerId, inventory) -> new UnsupportedMenu(containerId),
            FeatureFlags.VANILLA_SET);
    private static final IRecipeSlotsView THROWING_RECIPE_SLOTS = new ThrowingRecipeSlotsView();

    @Test
    void previewCheckSamplesLiveViewOnceWithoutReadingCachedJeiSlots() {
        MultiblockRecipeView view = recipeView();
        FakeRecipeSource source = FakeRecipeSource.live(view);
        FakeMenu menu = new FakeMenu(0, 81, 27);
        ErrorCapture errors = new ErrorCapture();
        var handler = handler(errors);

        IRecipeTransferError error = handler.transferRecipe(
                menu,
                source,
                THROWING_RECIPE_SLOTS,
                null,
                false,
                false);

        assertNull(error);
        assertEquals(1, source.currentViewCalls);
        assertEquals(0, menu.requestCalls);
        assertEquals(0, menu.modeChangeCalls);
        assertEquals(0, menu.invalidateCalls);
        assertEquals(List.of(), errors.messages);
    }

    @Test
    void transferRequestsTheExactSingleLiveViewWithoutEncodingOrMovingMaterials() {
        MultiblockRecipeView view = recipeView();
        FakeRecipeSource source = FakeRecipeSource.live(view);
        FakeMenu menu = new FakeMenu(0, 81, 27);
        var handler = handler(new ErrorCapture());

        IRecipeTransferError error = handler.transferRecipe(
                menu,
                source,
                THROWING_RECIPE_SLOTS,
                null,
                true,
                true);

        assertNull(error);
        assertEquals(1, source.currentViewCalls);
        assertEquals(1, menu.requestCalls);
        assertSame(view, menu.requestedView);
        assertEquals(0, menu.modeChangeCalls);
        assertEquals(0, menu.invalidateCalls);
    }

    @Test
    void previewCheckReturnsVisibleStaleAndMenuErrors() {
        ErrorCapture staleErrors = new ErrorCapture();
        FakeRecipeSource staleSource = FakeRecipeSource.stale();
        IRecipeTransferError staleError = handler(staleErrors).transferRecipe(
                new FakeMenu(0, 81, 27),
                staleSource,
                THROWING_RECIPE_SLOTS,
                null,
                false,
                false);

        assertEquals(IRecipeTransferError.Type.USER_FACING, staleError.getType());
        assertEquals(1, staleSource.currentViewCalls);
        assertEquals(1, staleErrors.messages.size());

        ErrorCapture menuErrors = new ErrorCapture();
        FakeRecipeSource liveSource = FakeRecipeSource.live(recipeView());
        var unsupportedHandler = new MultiblockPatternJeiTransferHandler<>(
                UnsupportedMenu.class,
                UNSUPPORTED_MENU_TYPE,
                RECIPE_TYPE,
                menuErrors);
        IRecipeTransferError menuError = unsupportedHandler.transferRecipe(
                new UnsupportedMenu(0),
                liveSource,
                THROWING_RECIPE_SLOTS,
                null,
                false,
                false);

        assertEquals(IRecipeTransferError.Type.USER_FACING, menuError.getType());
        assertEquals(1, liveSource.currentViewCalls);
        assertEquals(1, menuErrors.messages.size());
    }

    @Test
    void sourceFailuresAndNullLiveViewsReturnVisibleErrors() {
        ErrorCapture failureErrors = new ErrorCapture();
        FakeRecipeSource failingSource = new FakeRecipeSource(() -> {
            throw new AssertionError("unexpected live source failure");
        });

        IRecipeTransferError failure = handler(failureErrors).transferRecipe(
                new FakeMenu(0, 81, 27),
                failingSource,
                THROWING_RECIPE_SLOTS,
                null,
                false,
                false);

        assertEquals(IRecipeTransferError.Type.USER_FACING, failure.getType());
        assertEquals(1, failingSource.currentViewCalls);
        assertEquals(1, failureErrors.messages.size());

        ErrorCapture nullErrors = new ErrorCapture();
        FakeRecipeSource nullSource = FakeRecipeSource.live(null);
        IRecipeTransferError nullView = handler(nullErrors).transferRecipe(
                new FakeMenu(0, 81, 27),
                nullSource,
                THROWING_RECIPE_SLOTS,
                null,
                false,
                false);

        assertEquals(IRecipeTransferError.Type.USER_FACING, nullView.getType());
        assertEquals(1, nullSource.currentViewCalls);
        assertEquals(1, nullErrors.messages.size());
    }

    @Test
    void rejectsEightyTwoInputsInsteadOfTruncatingAnEightyOneSlotRecipe() {
        assertNull(MultiblockPatternJeiTransferHandler.findCapacityError(81, 1, 81, 27));

        var boundaryError = MultiblockPatternJeiTransferHandler.findCapacityError(82, 1, 81, 27);
        assertEquals(MultiblockPatternJeiTransferHandler.CapacityKind.INPUT, boundaryError.kind());
        assertEquals(82, boundaryError.required());
        assertEquals(81, boundaryError.available());

        ErrorCapture errors = new ErrorCapture();
        FakeRecipeSource source = FakeRecipeSource.live(recipeView());
        FakeMenu zeroInputCapacity = new FakeMenu(0, 0, 27);
        IRecipeTransferError error = handler(errors).transferRecipe(
                zeroInputCapacity,
                source,
                THROWING_RECIPE_SLOTS,
                null,
                false,
                false);

        assertEquals(IRecipeTransferError.Type.USER_FACING, error.getType());
        assertEquals(1, source.currentViewCalls);
        assertEquals(0, zeroInputCapacity.requestCalls);
        assertEquals(List.of(
                "This pattern terminal has 0 processing input slots, but the multiblock recipe requires 1."),
                errors.messages);
    }

    @Test
    void rejectsSlotFiltersAndAmountLimitsBeforeRequestingTransfer() {
        ConfigInventory filteredInputs = ConfigInventory.configStacks(81)
                .slotFilter((slot, key) -> false)
                .allowOverstacking(true)
                .build();
        FakeMenu filteredMenu = new FakeMenu(
                0,
                filteredInputs,
                ConfigInventory.configStacks(27).allowOverstacking(true).build());
        ErrorCapture filterErrors = new ErrorCapture();

        IRecipeTransferError filterError = handler(filterErrors).transferRecipe(
                filteredMenu,
                FakeRecipeSource.live(recipeView()),
                THROWING_RECIPE_SLOTS,
                null,
                false,
                true);

        assertEquals(IRecipeTransferError.Type.USER_FACING, filterError.getType());
        assertEquals(0, filteredMenu.requestCalls);
        assertEquals(List.of("This pattern terminal rejects multiblock input slot 1."), filterErrors.messages);

        FakeMenu limitedMenu = new FakeMenu(0, 81, 27);
        ErrorCapture amountErrors = new ErrorCapture();
        IRecipeTransferError amountError = handler(amountErrors).transferRecipe(
                limitedMenu,
                FakeRecipeSource.live(recipeView(65L)),
                THROWING_RECIPE_SLOTS,
                null,
                false,
                true);

        assertEquals(IRecipeTransferError.Type.USER_FACING, amountError.getType());
        assertEquals(0, limitedMenu.requestCalls);
        assertEquals(List.of(
                "Multiblock input slot 1 exceeds this pattern terminal's amount limit."),
                amountErrors.messages);
    }

    @Test
    void returnsVisibleErrorsWhenMenuCapacityOrRequestAccessFails() {
        ErrorCapture capacityErrors = new ErrorCapture();
        FakeRecipeSource capacitySource = FakeRecipeSource.live(recipeView());
        FakeMenu capacityFailure = new FakeMenu(0, 81, 27);
        capacityFailure.capacityFailure = new IllegalStateException("capacity unavailable");
        IRecipeTransferError capacityError = handler(capacityErrors).transferRecipe(
                capacityFailure,
                capacitySource,
                THROWING_RECIPE_SLOTS,
                null,
                false,
                false);

        assertEquals(IRecipeTransferError.Type.USER_FACING, capacityError.getType());
        assertEquals(1, capacitySource.currentViewCalls);
        assertEquals(0, capacityFailure.requestCalls);
        assertEquals(List.of("This pattern terminal cannot inspect its processing slot capacity."),
                capacityErrors.messages);

        ErrorCapture requestErrors = new ErrorCapture();
        FakeRecipeSource requestSource = FakeRecipeSource.live(recipeView());
        FakeMenu requestFailure = new FakeMenu(0, 81, 27);
        requestFailure.requestFailure = new IllegalStateException("request unavailable");
        IRecipeTransferError requestError = handler(requestErrors).transferRecipe(
                requestFailure,
                requestSource,
                THROWING_RECIPE_SLOTS,
                null,
                false,
                true);

        assertEquals(IRecipeTransferError.Type.USER_FACING, requestError.getType());
        assertEquals(1, requestSource.currentViewCalls);
        assertEquals(1, requestFailure.requestCalls);
        assertEquals(List.of("The multiblock recipe transfer request failed."), requestErrors.messages);
    }

    private static MultiblockPatternJeiTransferHandler<FakeMenu, FakeRecipeSource> handler(
                                                                                           Function<Component, IRecipeTransferError> errorFactory) {
        return new MultiblockPatternJeiTransferHandler<>(
                FakeMenu.class,
                MENU_TYPE,
                RECIPE_TYPE,
                errorFactory);
    }

    private static MultiblockRecipeView recipeView() {
        return recipeView(4L);
    }

    private static MultiblockRecipeView recipeView(long inputAmount) {
        var structureKey = JsonMultiBlockStructureKey.main(CONTROLLER_ID);
        var fingerprint = new ProjectionFingerprint(
                CONTROLLER_ID,
                0L,
                structureKey,
                0,
                List.of(),
                Map.of(),
                Map.of());
        return new MultiblockRecipeView(
                MultiblockRecipeView.registeredRecipeIdFor(CONTROLLER_ID),
                CONTROLLER_ID,
                structureKey.structureName(),
                0L,
                fingerprint,
                List.of(new PreviewMaterial(AEItemKey.of(Items.IRON_INGOT), inputAmount)),
                new PreviewMaterial(AEItemKey.of(Items.CRAFTING_TABLE), 1L));
    }

    private static final class ErrorCapture implements Function<Component, IRecipeTransferError> {

        private final List<String> messages = new ArrayList<>();

        @Override
        public IRecipeTransferError apply(Component message) {
            this.messages.add(message.getString());
            return () -> IRecipeTransferError.Type.USER_FACING;
        }
    }

    private static final class ThrowingRecipeSlotsView implements IRecipeSlotsView {

        @Override
        public List<IRecipeSlotView> getSlotViews() {
            throw new AssertionError("JEI recipe slots must not be read for a live multiblock transfer");
        }
    }

    private static final class FakeRecipeSource implements MultiblockRecipeViewSource {

        private final Supplier<MultiblockRecipeView> currentView;
        private int currentViewCalls;

        private static FakeRecipeSource live(MultiblockRecipeView view) {
            return new FakeRecipeSource(() -> view);
        }

        private static FakeRecipeSource stale() {
            return new FakeRecipeSource(() -> {
                throw new IllegalStateException("stale preview");
            });
        }

        private FakeRecipeSource(Supplier<MultiblockRecipeView> currentView) {
            this.currentView = currentView;
        }

        @Override
        public ResourceLocation registeredRecipeId() {
            return MultiblockRecipeView.registeredRecipeIdFor(CONTROLLER_ID);
        }

        @Override
        public MultiblockRecipeView currentRecipeView() {
            this.currentViewCalls++;
            return this.currentView.get();
        }
    }

    private static final class FakeMenu extends AbstractContainerMenu
                                        implements PatternEncodingMultiblockTransferTarget {

        private final ConfigInventory inputs;
        private final ConfigInventory outputs;
        private int requestCalls;
        private int modeChangeCalls;
        private int invalidateCalls;
        private MultiblockRecipeView requestedView;
        private RuntimeException capacityFailure;
        private RuntimeException requestFailure;

        private FakeMenu(int containerId, int inputCapacity, int outputCapacity) {
            this(
                    containerId,
                    ConfigInventory.configStacks(inputCapacity).build(),
                    ConfigInventory.configStacks(outputCapacity).build());
        }

        private FakeMenu(int containerId, ConfigInventory inputs, ConfigInventory outputs) {
            super(null, containerId);
            this.inputs = inputs;
            this.outputs = outputs;
        }

        @Override
        public void data_energistics$requestMultiblockTransfer(MultiblockRecipeView recipe) {
            this.requestCalls++;
            if (this.requestFailure != null) {
                throw this.requestFailure;
            }
            this.requestedView = recipe;
        }

        @Override
        public ConfigInventory data_energistics$getMultiblockTransferInputInventory() {
            if (this.capacityFailure != null) {
                throw this.capacityFailure;
            }
            return this.inputs;
        }

        @Override
        public ConfigInventory data_energistics$getMultiblockTransferOutputInventory() {
            return this.outputs;
        }

        @Override
        public EncodingMode data_energistics$getMultiblockTransferEncodingMode() {
            return EncodingMode.PROCESSING;
        }

        @Override
        public void data_energistics$setMultiblockTransferEncodingMode(EncodingMode mode) {
            this.modeChangeCalls++;
        }

        @Override
        public PatternEncodingMultiblockTransferState data_energistics$snapshotMultiblockTransferState() {
            throw new AssertionError("Client JEI transfer must not snapshot server source state");
        }

        @Override
        public void data_energistics$clearMultiblockTransferState() {
            throw new AssertionError("Client JEI transfer must not clear server source state");
        }

        @Override
        public void data_energistics$restoreMultiblockTransferState(
                                                                    PatternEncodingMultiblockTransferState state) {
            throw new AssertionError("Client JEI transfer must not restore server source state");
        }

        @Override
        public void data_energistics$invalidateMultiblockTransferTarget() {
            this.invalidateCalls++;
        }

        @Override
        public ItemStack quickMoveStack(Player player, int index) {
            return ItemStack.EMPTY;
        }

        @Override
        public boolean stillValid(Player player) {
            return true;
        }
    }

    private static final class UnsupportedMenu extends AbstractContainerMenu {

        private UnsupportedMenu(int containerId) {
            super(null, containerId);
        }

        @Override
        public ItemStack quickMoveStack(Player player, int index) {
            return ItemStack.EMPTY;
        }

        @Override
        public boolean stillValid(Player player) {
            return true;
        }
    }
}

package com.fish_dan_.data_energistics.integration.viewer.jei.recipe;

import com.fish_dan_.data_energistics.common.multiblock.preview.catalog.MultiblockRecipeView;
import com.fish_dan_.data_energistics.common.multiblock.preview.material.PreviewMaterial;
import com.fish_dan_.data_energistics.integration.viewer.xei.multiblock.MultiblockXeiComposition;
import com.fish_dan_.data_energistics.integration.viewer.xei.multiblock.MultiblockXeiIngredient;
import com.fish_dan_.data_energistics.integration.viewer.xei.multiblock.MultiblockXeiRecipe;
import com.fish_dan_.data_energistics.registry.DEBlocks;

import net.minecraft.client.gui.navigation.ScreenPosition;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;
import com.google.common.cache.RemovalNotification;
import com.lowdragmc.lowdraglib2.gui.ui.ModularUI;
import com.lowdragmc.lowdraglib2.integration.xei.IngredientIO;
import com.lowdragmc.lowdraglib2.integration.xei.XEITooltipContext;
import com.lowdragmc.lowdraglib2.integration.xei.jei.LDLibJEIPlugin;
import com.lowdragmc.lowdraglib2.integration.xei.jei.ModularUIJEIWidget;
import com.lowdragmc.lowdraglib2.integration.xei.jei.handler.JEIRecipeSlotHandler;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import lombok.Getter;
import mezz.jei.api.gui.builder.IRecipeLayoutBuilder;
import mezz.jei.api.gui.builder.ITooltipBuilder;
import mezz.jei.api.gui.drawable.IDrawable;
import mezz.jei.api.gui.ingredient.IRecipeSlotDrawable;
import mezz.jei.api.gui.inputs.RecipeSlotUnderMouse;
import mezz.jei.api.gui.widgets.IRecipeExtrasBuilder;
import mezz.jei.api.gui.widgets.ISlottedRecipeWidget;
import mezz.jei.api.helpers.IJeiHelpers;
import mezz.jei.api.recipe.IFocusGroup;
import mezz.jei.api.recipe.RecipeIngredientRole;
import mezz.jei.api.recipe.RecipeType;
import mezz.jei.api.recipe.category.IRecipeCategory;

import java.time.Duration;
import java.util.List;
import java.util.Optional;

/**
 * JEI adapter for the shared live Trinity multiblock composition.
 */
public final class TrinityMultiblockJeiCategory implements IRecipeCategory<MultiblockXeiRecipe> {

    /**
     * Sole controller-level JEI recipe type for every Trinity substructure and selection.
     */
    public static final RecipeType<MultiblockXeiRecipe> RECIPE_TYPE = new RecipeType<>(
            MultiblockXeiRecipe.CATEGORY_ID,
            MultiblockXeiRecipe.class);

    @Getter
    private final IDrawable icon;
    private final LoadingCache<MultiblockXeiRecipe, MultiblockXeiComposition> compositions;
    private boolean released;

    /**
     * Creates the category with independently owned LDLib2 compositions and the Trinity controller icon.
     */
    public TrinityMultiblockJeiCategory(IJeiHelpers helpers, RecipeRefresh recipeRefresh) {
        this(createIcon(helpers), recipeRefresh);
    }

    TrinityMultiblockJeiCategory(IDrawable icon, RecipeRefresh recipeRefresh) {
        this.icon = icon;
        this.compositions = CacheBuilder.newBuilder()
                .expireAfterAccess(Duration.ofSeconds(10))
                .maximumSize(10)
                .removalListener((RemovalNotification<MultiblockXeiRecipe, MultiblockXeiComposition> notification) -> {
                    MultiblockXeiComposition composition = notification.getValue();
                    if (composition != null) {
                        composition.modularUI().onRemoved();
                    }
                })
                .build(CacheLoader.from(recipe -> createComposition(recipe, recipeRefresh)));
    }

    private static IDrawable createIcon(IJeiHelpers helpers) {
        return helpers.getGuiHelper().createDrawableItemLike(DEBlocks.TRINITY_DATA_CORE.get());
    }

    /**
     * Releases every LDLib2 composition retained by the category before a JEI runtime restart.
     */
    public void releaseCachedUis() {
        this.released = true;
        this.compositions.invalidateAll();
        this.compositions.cleanUp();
    }

    private static MultiblockXeiComposition createComposition(MultiblockXeiRecipe recipe, RecipeRefresh recipeRefresh) {
        MultiblockXeiComposition composition = recipe.createComposition(
                "trinity_multiblock_jei",
                (activeComposition, change) -> recipeRefresh.request(recipe, activeComposition, change));
        ModularUI modularUI = composition.modularUI();
        modularUI.setAllowDebugMode(false);
        modularUI.setDrawTooltips(false);
        modularUI.init(MultiblockXeiComposition.WIDTH, MultiblockXeiComposition.HEIGHT);
        return composition;
    }

    private MultiblockXeiComposition compositionFor(MultiblockXeiRecipe recipe) {
        if (this.released) {
            throw new IllegalStateException("Trinity multiblock JEI category has already been released");
        }
        return this.compositions.getUnchecked(recipe);
    }

    @Override
    public void setRecipe(IRecipeLayoutBuilder builder, MultiblockXeiRecipe recipe, IFocusGroup focuses) {
        MultiblockXeiComposition composition = compositionFor(recipe);
        // Bindings must be collected per layout: tier/repeat changes update an existing composition.
        for (var binding : JEIRecipeSlotHandler.collectBindings(composition.modularUI())) {
            var area = LDLibJEIPlugin.getAreaLocal(binding.element(), true);
            builder.addSlot(binding.role())
                    .addTypedIngredients(binding.ingredients())
                    .setSlotName(binding.name())
                    .setPosition(area.getX(), area.getY())
                    .addRichTooltipCallback((slot, tooltip) -> appendSlotTooltip(binding, tooltip));
        }
        MultiblockRecipeView view = composition.currentRecipeView();
        // The virtual grid has 18 cells; publish off-page inputs and the hidden order-package output as well.
        builder.addInvisibleIngredients(RecipeIngredientRole.INPUT)
                .addItemStacks(view.inputs().stream().map(material -> ingredientStack(IngredientIO.INPUT, material)).toList());
        builder.addInvisibleIngredients(RecipeIngredientRole.OUTPUT)
                .addItemStack(ingredientStack(IngredientIO.OUTPUT, view.output()));
    }

    @Override
    public void createRecipeExtras(IRecipeExtrasBuilder builder, MultiblockXeiRecipe recipe, IFocusGroup focuses) {
        ModularUI modularUI = compositionFor(recipe).modularUI();
        var widget = new ModularUIJEIWidget(modularUI);
        builder.addWidget(widget);
        builder.addGuiEventListener(widget);
        List<SlottedBinding> slots = new ObjectArrayList<>();
        for (var binding : JEIRecipeSlotHandler.collectBindings(modularUI)) {
            builder.getRecipeSlots().findSlotByName(binding.name()).ifPresent(slot -> {
                if (binding.slotUpdater() != null) {
                    binding.slotUpdater().bind(slot);
                }
                slots.add(new SlottedBinding(binding, slot));
            });
        }
        if (!slots.isEmpty()) {
            builder.addSlottedWidget(new MaterialSlotsWidget(widget, slots), slots.stream().map(SlottedBinding::slot).toList());
        }
    }

    @Override
    public void onDisplayedIngredientsUpdate(MultiblockXeiRecipe recipe, List<IRecipeSlotDrawable> recipeSlots,
                                             IFocusGroup focuses) {
        for (var binding : JEIRecipeSlotHandler.collectBindings(compositionFor(recipe).modularUI())) {
            if (binding.slotUpdater() != null) {
                binding.slotUpdater().onDisplayedIngredientsUpdate();
            }
        }
    }

    private static void appendSlotTooltip(JEIRecipeSlotHandler.Binding binding, ITooltipBuilder tooltip) {
        var additional = XEITooltipContext.RECIPE_SLOT.collectTooltips(binding.element());
        if (additional != null) {
            tooltip.addAll(additional.tooltipTexts());
            if (additional.tooltipComponent() != null) {
                tooltip.add(additional.tooltipComponent());
            }
        }
    }

    private static ItemStack ingredientStack(IngredientIO role, PreviewMaterial material) {
        return new MultiblockXeiIngredient(role, material).toItemStack();
    }

    @Override
    public RecipeType<MultiblockXeiRecipe> getRecipeType() {
        return RECIPE_TYPE;
    }

    @Override
    public Component getTitle() {
        return DEBlocks.TRINITY_DATA_CORE.get().getName();
    }

    @Override
    public int getWidth() {
        return MultiblockXeiComposition.WIDTH;
    }

    @Override
    public int getHeight() {
        return MultiblockXeiComposition.HEIGHT;
    }

    private record SlottedBinding(JEIRecipeSlotHandler.Binding binding, IRecipeSlotDrawable slot) {}

    private record MaterialSlotsWidget(ModularUIJEIWidget widget, List<SlottedBinding> slots) implements ISlottedRecipeWidget {

        @Override
        public Optional<RecipeSlotUnderMouse> getSlotUnderMouse(double mouseX, double mouseY) {
            var localMouse = this.widget.getWorldMouse((float) mouseX, (float) mouseY);
            for (var slot : this.slots) {
                if (slot.binding().isInteractive() && slot.binding().element().isMouseOverElement(localMouse.x, localMouse.y)) {
                    return Optional.of(new RecipeSlotUnderMouse(slot.slot(), 0, 0));
                }
            }
            return Optional.empty();
        }

        @Override
        public ScreenPosition getPosition() {
            return ModularUIJEIWidget.ZERO;
        }
    }

    @FunctionalInterface
    public interface RecipeRefresh {

        /**
         * Requests a deferred layout refresh for one exact active composition.
         */
        void request(MultiblockXeiRecipe recipe,
                     MultiblockXeiComposition composition,
                     MultiblockXeiComposition.RecipeChange change);
    }
}

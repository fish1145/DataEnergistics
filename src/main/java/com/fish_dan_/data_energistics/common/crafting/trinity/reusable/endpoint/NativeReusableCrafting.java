package com.fish_dan_.data_energistics.common.crafting.trinity.reusable.endpoint;

import com.fish_dan_.data_energistics.api.crafting.reusable.ReusableInputRule;
import com.fish_dan_.data_energistics.common.crafting.trinity.reusable.endpoint.PersistentReusableCraftingEndpoint.Binding;
import com.fish_dan_.data_energistics.common.crafting.trinity.reusable.endpoint.PersistentReusableCraftingEndpoint.NativeResult;
import com.fish_dan_.data_energistics.common.crafting.trinity.reusable.session.ReusableInputSession.Operation;
import com.fish_dan_.data_energistics.common.crafting.trinity.reusable.session.ReusableInputSession.ToolDelivery;
import com.fish_dan_.data_energistics.common.crafting.trinity.reusable.session.ReusableInputSession.ToolOutcome;
import com.fish_dan_.data_energistics.common.trinity.pattern.RoutedCraftingPatternDetails;

import appeng.api.crafting.IPatternDetails;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.GenericStack;
import appeng.api.stacks.KeyCounter;
import appeng.blockentity.crafting.IMolecularAssemblerSupportedPattern;
import appeng.crafting.CraftingEvent;
import appeng.crafting.pattern.AECraftingPattern;
import appeng.menu.AutoCraftingMenu;

import net.minecraft.core.NonNullList;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.inventory.TransientCraftingContainer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.CraftingRecipe;

import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.ints.IntSet;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;

/** Materializes real per-slot escrow, executes the native recipe once, and classifies actual grid remainders. */
public final class NativeReusableCrafting {

    private static final int GRID_SIZE = 9;

    private NativeReusableCrafting() {}

    public static boolean usesNativeRecipeValidation(IPatternDetails pattern, Optional<ResourceLocation> recipeId) {
        return original(pattern) instanceof AECraftingPattern && recipeId.isPresent();
    }

    /**
     * Server-thread candidate validation shared by planning, exact selection and native execution. Only slots
     * already proven reusable may advance beyond the encoded key; ordinary input substitution restrictions remain.
     */
    public static boolean matches(IPatternDetails pattern, List<GenericStack> exactInputs, IntSet reusableSlots,
                                  Optional<ResourceLocation> recipeId, ServerLevel level) {
        IPatternDetails nativePattern = original(pattern);
        IPatternDetails.IInput[] inputs = nativePattern.getInputs();
        if (inputs.length != exactInputs.size()) {
            return false;
        }
        boolean nativeValidation = usesNativeRecipeValidation(nativePattern, recipeId);
        for (int slot = 0; slot < inputs.length; slot++) {
            GenericStack exact = exactInputs.get(slot);
            if (exact.amount() <= 0 || !(exact.what() instanceof AEItemKey) ||
                    (!nativeValidation || !reusableSlots.contains(slot)) && !inputs[slot].isValid(exact.what(), level)) {
                return false;
            }
        }
        if (!(nativePattern instanceof IMolecularAssemblerSupportedPattern molecular)) {
            return true;
        }
        KeyCounter[] all = counters(inputs.length);
        for (int slot = 0; slot < inputs.length; slot++) {
            GenericStack exact = exactInputs.get(slot);
            all[slot].add(exact.what(), exact.amount());
        }
        Grid grid;
        try {
            grid = materialize(molecular, all, counters(inputs.length));
        } catch (IllegalArgumentException unsupported) {
            return false;
        }
        CraftingInput input = CraftingInput.ofPositioned(3, 3, grid.items()).input();
        if (nativeValidation) {
            var holder = level.getRecipeManager().byKey(recipeId.orElseThrow()).orElse(null);
            return holder != null && holder.value() instanceof CraftingRecipe recipe && recipe.matches(input, level);
        }
        return !molecular.assemble(input, level).isEmpty();
    }

    private static IPatternDetails original(IPatternDetails pattern) {
        return pattern instanceof RoutedCraftingPatternDetails routed ? routed.delegate() : pattern;
    }

    /** Rejects patterns whose compressed inputs cannot be mapped independently to native grid positions. */
    public static boolean supports(IMolecularAssemblerSupportedPattern pattern, Binding binding) {
        List<ToolDelivery> initial = binding.tools().stream().map(tool -> new ToolDelivery(tool.slot(), new GenericStack(tool.rule().initialKey(), tool.heldAmount()))).toList();
        try {
            materialize(pattern, binding, new Operation(0, 0, binding.consumed(), initial));
            return true;
        } catch (IllegalArgumentException unsupported) {
            return false;
        }
    }

    public static NativeResult execute(IMolecularAssemblerSupportedPattern pattern, Binding binding, Operation operation,
                                       ServerLevel level, ResourceLocation recipeId) {
        Grid grid = materialize(pattern, binding, operation);
        CraftingInput.Positioned positioned = CraftingInput.ofPositioned(3, 3, grid.items());
        CraftingInput input = positioned.input();
        CraftingRecipe actualRecipe = null;
        if (pattern instanceof AECraftingPattern) {
            var holder = level.getRecipeManager().byKey(recipeId).orElse(null);
            if (holder == null || !(holder.value() instanceof CraftingRecipe recipe) || !recipe.matches(input, level)) {
                return NativeResult.paused();
            }
            actualRecipe = recipe;
        }
        ItemStack output = actualRecipe == null ? pattern.assemble(input, level) : actualRecipe.assemble(input, level.registryAccess());
        if (output.isEmpty()) {
            return NativeResult.paused();
        }
        TransientCraftingContainer container = new TransientCraftingContainer(new AutoCraftingMenu(), 3, 3);
        for (int slot = 0; slot < GRID_SIZE; slot++) {
            container.setItem(slot, grid.items().get(slot).copy());
        }
        output.onCraftedBySystem(level);
        CraftingEvent.fireAutoCraftingEvent(level, pattern, output, container);
        NonNullList<ItemStack> remainders = actualRecipe == null ? pattern.getRemainingItems(input) : actualRecipe.getRemainingItems(input);
        if (remainders.size() != input.size()) {
            throw new IllegalStateException("Native reusable recipe returned a remainder grid of the wrong size");
        }
        Int2ObjectOpenHashMap<List<GenericStack>> successors = new Int2ObjectOpenHashMap<>();
        Int2ObjectOpenHashMap<List<GenericStack>> byproducts = new Int2ObjectOpenHashMap<>();
        Int2ObjectOpenHashMap<ReusableInputRule> rules = new Int2ObjectOpenHashMap<>();
        binding.tools().forEach(tool -> {
            successors.put(tool.slot(), new ObjectArrayList<>());
            byproducts.put(tool.slot(), new ObjectArrayList<>());
            rules.put(tool.slot(), tool.rule());
        });
        List<GenericStack> outputs = new ObjectArrayList<>();
        outputs.add(new GenericStack(AEItemKey.of(output), output.getCount()));
        for (int index = 0; index < input.size(); index++) {
            ItemStack remainder = remainders.get(index);
            if (remainder.isEmpty()) {
                continue;
            }
            int sparse = positioned.left() + index % input.width() + (positioned.top() + index / input.width()) * 3;
            int owner = grid.toolOwners()[sparse];
            GenericStack actual = new GenericStack(AEItemKey.of(remainder), remainder.getCount());
            if (owner < 0) {
                outputs.add(actual);
                continue;
            }
            ReusableInputRule.Result prediction = rules.get(owner).advance(AEItemKey.of(grid.items().get(sparse)), 1);
            // Prediction identifies output roles only. Every recorded key/count comes from the actual remainder.
            if (actual.what().equals(prediction.successor())) {
                successors.get(owner).add(new GenericStack(actual.what(), 1));
                if (actual.amount() > 1) {
                    byproducts.get(owner).add(new GenericStack(actual.what(), actual.amount() - 1));
                }
            } else if (prediction.byproducts().stream().anyMatch(product -> product.what().equals(actual.what()))) {
                byproducts.get(owner).add(actual);
            } else {
                // Unexpected actual state is retained so fault settlement cannot reconstruct the old tool.
                successors.get(owner).add(actual);
            }
        }
        List<ToolOutcome> outcomes = binding.tools().stream().map(tool -> new ToolOutcome(tool.slot(), successors.get(tool.slot()), byproducts.get(tool.slot()))).toList();
        return new NativeResult(true, outcomes, outputs, Optional.empty());
    }

    private static Grid materialize(IMolecularAssemblerSupportedPattern pattern, Binding binding, Operation operation) {
        KeyCounter[] all = counters(binding.inputSlots());
        operation.tools().forEach(tool -> all[tool.slot()].add(tool.stack().what(), tool.stack().amount()));
        operation.consumed().forEach(material -> all[material.slot()].add(material.stack().what(), material.stack().amount()));
        KeyCounter[] held = counters(binding.inputSlots());
        operation.tools().forEach(tool -> held[tool.slot()].add(tool.stack().what(), tool.stack().amount()));
        return materialize(pattern, all, held);
    }

    private static Grid materialize(IMolecularAssemblerSupportedPattern pattern, KeyCounter[] all, KeyCounter[] held) {
        List<ItemStack> grid = new ObjectArrayList<>(GRID_SIZE);
        for (int index = 0; index < GRID_SIZE; index++) {
            grid.add(ItemStack.EMPTY);
        }
        int[] toolOwners = new int[GRID_SIZE];
        Arrays.fill(toolOwners, -1);
        boolean encodedMapping = pattern instanceof AECraftingPattern;
        for (int source = 0; source < all.length; source++) {
            final int originalSlot = source;
            KeyCounter[] isolated = counters(all.length);
            if (encodedMapping) {
                long units = 0;
                for (var entry : all[source]) {
                    units = Math.addExact(units, entry.getLongValue());
                }
                GenericStack template = pattern.getInputs()[source].getPossibleInputs()[0];
                if (!(template.what() instanceof AEItemKey)) {
                    throw new IllegalArgumentException("Native reusable mapping needs an encoded item template");
                }
                isolated[source].add(template.what(), units);
            } else {
                isolated[source].addAll(all[source]);
            }
            pattern.fillCraftingGrid(isolated, (sparse, stack) -> {
                if (stack.isEmpty()) {
                    return;
                }
                if (sparse < 0 || sparse >= GRID_SIZE || !grid.get(sparse).isEmpty() || stack.getCount() != 1) {
                    throw new IllegalArgumentException("Native reusable inputs need disjoint one-item grid positions");
                }
                ItemStack actual = encodedMapping ? takeItem(all[originalSlot]) : stack.copy();
                grid.set(sparse, actual);
                AEItemKey key = AEItemKey.of(actual);
                if (held[originalSlot].get(key) > 0) {
                    toolOwners[sparse] = originalSlot;
                    held[originalSlot].remove(key, 1);
                }
            });
            isolated[source].removeZeros();
            held[source].removeZeros();
            if (!isolated[source].isEmpty() || !held[source].isEmpty()) {
                throw new IllegalArgumentException("Native recipe did not consume the exact reusable slot input");
            }
        }
        return new Grid(grid, toolOwners);
    }

    private static ItemStack takeItem(KeyCounter counter) {
        for (var entry : counter) {
            if (entry.getLongValue() > 0 && entry.getKey() instanceof AEItemKey key) {
                counter.remove(key, 1);
                return key.toStack();
            }
        }
        throw new IllegalArgumentException("Native mapping requested more physical items than were supplied");
    }

    private static KeyCounter[] counters(int slots) {
        KeyCounter[] result = new KeyCounter[slots];
        for (int slot = 0; slot < slots; slot++) {
            result[slot] = new KeyCounter();
        }
        return result;
    }

    private record Grid(List<ItemStack> items, int[] toolOwners) {}
}

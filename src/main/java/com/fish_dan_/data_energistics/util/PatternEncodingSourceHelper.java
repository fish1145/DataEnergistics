package com.fish_dan_.data_energistics.util;

import com.fish_dan_.data_energistics.Data_Energistics;
import com.fish_dan_.data_energistics.ae2.ModAE2Keys;
import com.fish_dan_.data_energistics.menu.common.PatternEncodingPreferenceMenu;
import com.fish_dan_.data_energistics.menu.common.PatternEncodingPreviewMenu;
import com.fish_dan_.data_energistics.menu.common.PatternEncodingRankingContext;
import com.fish_dan_.data_energistics.menu.common.PatternEncodingSourceAware;
import com.fish_dan_.data_energistics.menu.common.PatternEncodingTransferKeyAware;
import com.fish_dan_.data_energistics.recipe.DataRipperReassemblerRecipe;
import com.fish_dan_.data_energistics.recipe.DataRipperReassemblerRecipeInput;
import com.fish_dan_.data_energistics.registry.ModRecipes;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.TagParser;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.RecipeHolder;

import appeng.api.crafting.PatternDetailsHelper;
import appeng.api.stacks.AEFluidKey;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.GenericStack;
import appeng.core.definitions.AEItems;
import appeng.helpers.IPatternTerminalMenuHost;
import appeng.menu.me.items.PatternEncodingTermMenu;
import appeng.parts.encoding.EncodingMode;
import appeng.parts.encoding.PatternEncodingLogic;
import appeng.util.ConfigInventory;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import org.apache.logging.log4j.Logger;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

public final class PatternEncodingSourceHelper {

    private static final Logger LOGGER = Data_Energistics.LOGGER;
    private static final int DATA_RIPPER_KEY_INPUT_SLOT = DataRipperReassemblerRecipe.KEY_INPUT_SLOT_INDEX;
    private static final int DATA_RIPPER_FLUID_INPUT_SLOT_BASE = DataRipperReassemblerRecipe.ITEM_INPUT_SLOTS + DataRipperReassemblerRecipe.KEY_INPUT_SLOTS;
    private static final int DATA_RIPPER_ITEM_OUTPUT_SLOT_BASE = 0;
    private static final int DATA_RIPPER_KEY_OUTPUT_SLOT = DATA_RIPPER_ITEM_OUTPUT_SLOT_BASE + DataRipperReassemblerRecipe.ITEM_OUTPUT_SLOTS;
    private static final int DATA_RIPPER_FLUID_OUTPUT_SLOT_BASE = DATA_RIPPER_KEY_OUTPUT_SLOT + DataRipperReassemblerRecipe.KEY_OUTPUT_SLOTS;
    public static final String ACTION_SET_PATTERN_SOURCE = "dataEnergisticsSetPatternSource";
    public static final String ACTION_SET_TRANSFER_KEY_INPUT = "dataEnergisticsSetTransferKeyInput";
    public static final String ACTION_SET_TRANSFER_KEY_OUTPUT = "dataEnergisticsSetTransferKeyOutput";
    public static final String ACTION_SET_TRANSFER_FLUID_INPUTS = "dataEnergisticsSetTransferFluidInputs";
    public static final String ACTION_SET_TRANSFER_FLUID_OUTPUTS = "dataEnergisticsSetTransferFluidOutputs";
    public static final String CLEAR_PATTERN_SOURCE = "";
    public static final String CLEAR_TRANSFER_KEY_INPUT = "";
    public static final String CLEAR_TRANSFER_KEY_OUTPUT = "";
    public static final String CLEAR_TRANSFER_FLUID_STACKS = "";
    private static final String PLAYER_PATTERN_SOURCE_ROOT = "data_energistics_pattern_source";
    private static final String TAG_PENDING = "pending";
    private static final String TAG_LAST = "last";
    private static final String TAG_ENABLED = "enabled";
    private static final String TAG_UPLOAD_ENABLED = "upload_enabled";
    private static final String TAG_PENDING_KEY_INPUT = "pending_key_input";
    private static final String TAG_PENDING_KEY_OUTPUT = "pending_key_output";
    private static final ResourceLocation CRAFTING_TABLE_ID = ResourceLocation.withDefaultNamespace("crafting_table");
    private static final ResourceLocation CRAFTING_RECIPE_TYPE_ID = ResourceLocation.withDefaultNamespace("crafting");
    private static final ResourceLocation STONECUTTER_ID = ResourceLocation.withDefaultNamespace("stonecutter");
    private static final ResourceLocation STONECUTTING_RECIPE_TYPE_ID = ResourceLocation.withDefaultNamespace("stonecutting");
    private static final ResourceLocation SMITHING_TABLE_ID = ResourceLocation.withDefaultNamespace("smithing_table");
    private static final ResourceLocation SMITHING_RECIPE_TYPE_ID = ResourceLocation.withDefaultNamespace("smithing");
    private static final ResourceLocation DATA_RIPPER_REASSEMBLER_ID = Data_Energistics.id("data_reassembler");

    private PatternEncodingSourceHelper() {}

    @Nullable
    public static ItemStack encodeProcessingPattern(ConfigInventory inputs, ConfigInventory outputs) {
        List<GenericStack> normalizedInputs = normalizeProcessingPatternInventory(inputs, "input");
        if (normalizedInputs == null || normalizedInputs.stream().noneMatch(stack -> stack != null)) {
            return null;
        }

        List<GenericStack> normalizedOutputs = normalizeProcessingPatternInventory(outputs, "output");
        if (normalizedOutputs == null || normalizedOutputs.isEmpty() || normalizedOutputs.getFirst() == null) {
            return null;
        }

        return PatternDetailsHelper.encodeProcessingPattern(normalizedInputs, normalizedOutputs);
    }

    @Nullable
    private static List<GenericStack> normalizeProcessingPatternInventory(
                                                                          ConfigInventory inventory,
                                                                          String inventoryKind) {
        List<GenericStack> normalized = new ArrayList<>(inventory.size());
        for (int slot = 0; slot < inventory.size(); slot++) {
            GenericStack stack = inventory.getStack(slot);
            if (stack == null || !(stack.what() instanceof AEItemKey itemKey)) {
                normalized.add(stack);
                continue;
            }

            GenericStack wrapped = GenericStack.unwrapItemStack(itemKey.toStack());
            if (wrapped == null || !ModAE2Keys.isCustomKey(wrapped.what())) {
                normalized.add(stack);
                continue;
            }

            if (stack.amount() <= 0L || wrapped.amount() <= 0L) {
                LOGGER.error(
                        "Cannot encode processing pattern: wrapped custom key amount must be positive, inventory={}, slot={}, outerAmount={}, innerAmount={}, key={}",
                        inventoryKind,
                        slot,
                        stack.amount(),
                        wrapped.amount(),
                        wrapped.what());
                return null;
            }

            try {
                normalized.add(new GenericStack(
                        wrapped.what(),
                        Math.multiplyExact(stack.amount(), wrapped.amount())));
            } catch (ArithmeticException exception) {
                LOGGER.error(
                        "Cannot encode processing pattern: wrapped custom key amount overflow, inventory={}, slot={}, outerAmount={}, innerAmount={}, key={}",
                        inventoryKind,
                        slot,
                        stack.amount(),
                        wrapped.amount(),
                        wrapped.what(),
                        exception);
                return null;
            }
        }
        return normalized;
    }

    @Nullable
    public static ResourceLocation resolveFallbackWorkstationForMode(@Nullable EncodingMode mode) {
        if (mode == null) {
            return null;
        }

        return switch (mode) {
            case CRAFTING -> CRAFTING_TABLE_ID;
            case STONECUTTING -> STONECUTTER_ID;
            case SMITHING_TABLE -> SMITHING_TABLE_ID;
            case PROCESSING -> null;
        };
    }

    public static void rememberTransferSource(PatternEncodingTermMenu menu,
                                              @Nullable PatternEncodingRankingContext transferContext) {
        if (menu instanceof PatternEncodingSourceAware sourceAware) {
            if (shouldIgnoreWorkstationMemory(sourceAware)) {
                sourceAware.data_energistics$setPendingPatternSource(null);
                sourceAware.data_energistics$setLastEncodedPatternSource(null);
                if (menu instanceof PatternEncodingPreferenceMenu preferenceMenu) {
                    EncodingMode mode = menu.getMode();
                    preferenceMenu.data_energistics$getPreferenceSession().setRankingContext(
                            resolveFixedModeRankingContext(mode, resolveFallbackWorkstationForMode(mode)));
                }
                return;
            }

            if (transferContext == null) {
                throw new IllegalArgumentException("Processing pattern transfer requires an exact ranking context");
            }
            ResourceLocation workstationId = resolveUniqueWorkstation(transferContext);
            sourceAware.data_energistics$setPendingPatternSource(workstationId);
            if (sourceAware.data_energistics$isPatternSourceEnabled()) {
                sourceAware.data_energistics$setLastEncodedPatternSource(workstationId);
            }
            if (menu instanceof PatternEncodingPreferenceMenu preferenceMenu) {
                preferenceMenu.data_energistics$getPreferenceSession().setRankingContext(transferContext);
            }
        }
    }

    @Nullable
    private static ResourceLocation resolveUniqueWorkstation(PatternEncodingRankingContext context) {
        for (ResourceLocation workstationId : context.workstationIds()) {
            if (isInvalidWorkstationItem(workstationId)) {
                throw new IllegalArgumentException("Processing pattern transfer references an invalid workstation: "
                        + workstationId);
            }
        }
        return context.workstationIds().size() == 1 ? context.workstationIds().getFirst() : null;
    }

    /**
     * Resolves the fixed vanilla recipe scope for non-processing encoder modes.
     */
    @Nullable
    public static PatternEncodingRankingContext resolveFixedModeRankingContext(@Nullable EncodingMode mode,
                                                                               @Nullable ResourceLocation workstationId) {
        if (mode == null || !isResolvableWorkstation(workstationId)) {
            return null;
        }
        ResourceLocation recipeTypeId = switch (mode) {
            case CRAFTING -> CRAFTING_RECIPE_TYPE_ID;
            case SMITHING_TABLE -> SMITHING_RECIPE_TYPE_ID;
            case STONECUTTING -> STONECUTTING_RECIPE_TYPE_ID;
            case PROCESSING -> null;
        };
        return recipeTypeId == null ? null : PatternEncodingRankingContext.of(recipeTypeId, List.of(workstationId));
    }

    /**
     * Verifies that a client ranking context describes the current recipe mode and registered workstation items.
     * Fixed vanilla modes are derived entirely on the server; processing contexts come from exact viewer metadata.
     */
    public static boolean isRankingContextValid(PatternEncodingPreviewMenu previewMenu,
                                                @Nullable PatternEncodingRankingContext context) {
        EncodingMode mode = previewMenu.data_energistics$getEncodingMode();
        ResourceLocation fixedWorkstation = resolveFallbackWorkstationForMode(mode);
        if (fixedWorkstation != null) {
            return Objects.equals(context, resolveFixedModeRankingContext(mode, fixedWorkstation));
        }
        if (mode != EncodingMode.PROCESSING) {
            return context == null;
        }
        if (context == null) {
            return true;
        }
        for (ResourceLocation candidateWorkstation : context.workstationIds()) {
            if (isInvalidWorkstationItem(candidateWorkstation)) {
                return false;
            }
        }
        return true;
    }

    private static boolean isResolvableWorkstation(@Nullable ResourceLocation workstationId) {
        return workstationId != null && (BuiltInRegistries.BLOCK.containsKey(workstationId) ||
                BuiltInRegistries.ITEM.containsKey(workstationId));
    }

    private static boolean isInvalidWorkstationItem(ResourceLocation workstationId) {
        return BuiltInRegistries.ITEM.get(workstationId) == Items.AIR;
    }

    public static void rememberTransferKeyInput(PatternEncodingTermMenu menu, @Nullable Object recipe,
                                                @Nullable Object transferContext) {
        if (menu.getMode() != EncodingMode.PROCESSING) {
            syncPendingTransferKeyInput(menu, null);
            return;
        }

        DataRipperReassemblerRecipe dataRipperRecipe = resolveDataRipperReassemblerRecipe(recipe, transferContext);
        if (dataRipperRecipe != null) {
            syncPendingTransferKeyInput(menu, dataRipperRecipe.getKeyInput());
        }
    }

    public static void rememberTransferFluidInputs(PatternEncodingTermMenu menu, @Nullable Object recipe,
                                                   @Nullable Object transferContext) {
        if (menu.getMode() != EncodingMode.PROCESSING) {
            syncPendingTransferFluidInputs(menu, List.of());
            return;
        }

        DataRipperReassemblerRecipe dataRipperRecipe = resolveDataRipperReassemblerRecipe(recipe, transferContext);
        if (dataRipperRecipe != null) {
            syncPendingTransferFluidInputs(menu, dataRipperRecipe.getFluidInputs());
        }
    }

    public static void rememberTransferKeyOutput(PatternEncodingTermMenu menu, @Nullable Object recipe,
                                                 @Nullable Object transferContext) {
        if (menu.getMode() != EncodingMode.PROCESSING) {
            syncPendingTransferKeyOutput(menu, null);
            return;
        }

        DataRipperReassemblerRecipe dataRipperRecipe = resolveDataRipperReassemblerRecipe(recipe, transferContext);
        if (dataRipperRecipe != null) {
            syncPendingTransferKeyOutput(menu, dataRipperRecipe.getKeyOutput());
        }
    }

    public static void rememberTransferFluidOutputs(PatternEncodingTermMenu menu, @Nullable Object recipe,
                                                    @Nullable Object transferContext) {
        if (menu.getMode() != EncodingMode.PROCESSING) {
            syncPendingTransferFluidOutputs(menu, List.of());
            return;
        }

        DataRipperReassemblerRecipe dataRipperRecipe = resolveDataRipperReassemblerRecipe(recipe, transferContext);
        if (dataRipperRecipe != null) {
            syncPendingTransferFluidOutputs(menu, dataRipperRecipe.getFluidOutputs());
        }
    }

    public static void applyPendingTransferKeyInput(PatternEncodingTermMenu menu) {
        if (menu.getMode() != EncodingMode.PROCESSING) {
            return;
        }

        ResourceLocation pendingPatternSource = readPendingPatternSource(menu.getPlayer());
        if (!DATA_RIPPER_REASSEMBLER_ID.equals(pendingPatternSource)) {
            return;
        }

        GenericStack keyInput = readPendingTransferKeyInput(menu.getPlayer());
        if (keyInput == null && menu instanceof PatternEncodingTransferKeyAware transferKeyAware) {
            keyInput = deserializeTransferKey(menu, transferKeyAware.dataEnergistics$getDisplayedTransferKeyInputSerialized());
        }
        if (keyInput == null) {
            LOGGER.debug("[DE][PatternKey] pending key is null");
            return;
        }

        if (!(menu.getHost() instanceof IPatternTerminalMenuHost host)) {
            return;
        }

        PatternEncodingLogic logic = host.getLogic();
        ConfigInventory encodedInputsInv = logic.getEncodedInputInv();
        int keySlot = DATA_RIPPER_KEY_INPUT_SLOT;
        if (keySlot < 0 || keySlot >= encodedInputsInv.size()) {
            LOGGER.debug("[DE][PatternKey] pending key slot {} out of bounds size={}", keySlot, encodedInputsInv.size());
            return;
        }

        LOGGER.debug("[DE][PatternKey] applying pending key {}", describeGenericStack(keyInput));
        applyTransferKeyInputServer(encodedInputsInv, keySlot, keyInput);
    }

    public static void applyPendingTransferKeyOutput(PatternEncodingTermMenu menu) {
        if (menu.getMode() != EncodingMode.PROCESSING) {
            return;
        }

        ResourceLocation pendingPatternSource = readPendingPatternSource(menu.getPlayer());
        if (!DATA_RIPPER_REASSEMBLER_ID.equals(pendingPatternSource)) {
            return;
        }

        GenericStack keyOutput = readPendingTransferKeyOutput(menu.getPlayer());
        if (keyOutput == null && menu instanceof PatternEncodingTransferKeyAware transferKeyAware) {
            keyOutput = deserializeTransferKey(menu, transferKeyAware.dataEnergistics$getDisplayedTransferKeyOutputSerialized());
        }
        if (keyOutput == null) {
            return;
        }

        if (!(menu.getHost() instanceof IPatternTerminalMenuHost host)) {
            return;
        }

        PatternEncodingLogic logic = host.getLogic();
        ConfigInventory encodedOutputsInv = logic.getEncodedOutputInv();
        applyTransferKeyOutputServer(encodedOutputsInv, keyOutput);
    }

    public static void sanitizeActiveDataRipperTransferLayout(PatternEncodingTermMenu menu) {
        if (menu.getMode() != EncodingMode.PROCESSING) {
            return;
        }

        ResourceLocation pendingPatternSource = readPendingPatternSource(menu.getPlayer());
        if (!DATA_RIPPER_REASSEMBLER_ID.equals(pendingPatternSource)) {
            return;
        }
        if (!(menu.getHost() instanceof IPatternTerminalMenuHost host)) {
            return;
        }

        PatternEncodingLogic logic = host.getLogic();
        sanitizeDataRipperItemInputs(logic.getEncodedInputInv());
        sanitizeDataRipperItemOutputs(logic.getEncodedOutputInv());
    }

    public static void resolveAndApplyDataRipperRecipeKeyInput(PatternEncodingTermMenu menu) {
        if (menu.getMode() != EncodingMode.PROCESSING) {
            return;
        }
        if (!DATA_RIPPER_REASSEMBLER_ID.equals(readPendingPatternSource(menu.getPlayer()))) {
            return;
        }
        if (!(menu.getHost() instanceof IPatternTerminalMenuHost host)) {
            return;
        }

        PatternEncodingLogic logic = host.getLogic();
        ConfigInventory encodedInputsInv = logic.getEncodedInputInv();
        ConfigInventory encodedOutputsInv = logic.getEncodedOutputInv();
        if (DATA_RIPPER_KEY_INPUT_SLOT >= encodedInputsInv.size()) {
            LOGGER.debug("[DE][PatternKey] resolve key slot {} out of bounds size={}",
                    DATA_RIPPER_KEY_INPUT_SLOT, encodedInputsInv.size());
            return;
        }

        List<ItemStack> items = new ArrayList<>(DataRipperReassemblerRecipe.ITEM_INPUT_SLOTS);
        for (int i = 0; i < DataRipperReassemblerRecipe.ITEM_INPUT_SLOTS && i < encodedInputsInv.size(); i++) {
            GenericStack stack = encodedInputsInv.getStack(i);
            if (stack == null || !(stack.what() instanceof AEItemKey itemKey)) {
                continue;
            }
            items.add(itemKey.toStack((int) Math.min(Integer.MAX_VALUE, stack.amount())));
        }

        LOGGER.debug("[DE][PatternKey] resolve items={}", describeItems(items));

        if (items.isEmpty()) {
            LOGGER.debug("[DE][PatternKey] resolve aborted: items empty");
            return;
        }

        List<ItemStack> outputs = new ArrayList<>(encodedOutputsInv.size());
        for (int i = 0; i < encodedOutputsInv.size(); i++) {
            GenericStack stack = encodedOutputsInv.getStack(i);
            if (stack == null || !(stack.what() instanceof AEItemKey itemKey)) {
                outputs.add(ItemStack.EMPTY);
                continue;
            }
            outputs.add(itemKey.toStack((int) Math.min(Integer.MAX_VALUE, stack.amount())));
        }

        LOGGER.debug("[DE][PatternKey] resolve outputs={}", describeItems(outputs));
        GenericStack keyOutput = extractDataRipperKeyStack(encodedOutputsInv);
        List<GenericStack> fluidOutputs = extractFluidStacks(encodedOutputsInv);

        var level = menu.getPlayer().level();
        for (RecipeHolder<DataRipperReassemblerRecipe> holder : level.getRecipeManager()
                .getAllRecipesFor(ModRecipes.DATA_RIPPER_REASSEMBLER_TYPE.get())) {
            DataRipperReassemblerRecipe recipe = holder.value();
            if (!recipe.matches(new DataRipperReassemblerRecipeInput(items, List.of(), null), level)) {
                continue;
            }
            if (!matchesEncodedOutputs(recipe, outputs, keyOutput, fluidOutputs)) {
                continue;
            }

            GenericStack keyInput = recipe.getKeyInput();
            LOGGER.debug("[DE][PatternKey] matched recipe={} key={}", holder.id(), describeGenericStack(keyInput));
            writePendingTransferKeyInput(menu.getPlayer(), keyInput);
            applyTransferKeyInputServer(encodedInputsInv, DATA_RIPPER_KEY_INPUT_SLOT, keyInput);
            return;
        }

        LOGGER.debug("[DE][PatternKey] no data_reassembler recipe matched");
    }

    public static void applyPatternSource(ItemStack stack, PatternEncodingSourceAware sourceAware,
                                          @Nullable ResourceLocation fallbackWorkstationId) {
        if (shouldIgnoreWorkstationMemory(sourceAware)) {
            sourceAware.data_energistics$setLastEncodedPatternSource(null);
            return;
        }

        ResourceLocation workstationId = resolvePreferredWorkstationId(sourceAware);
        if (workstationId == null) {
            workstationId = fallbackWorkstationId;
        }

        sourceAware.data_energistics$setLastEncodedPatternSource(workstationId);
    }

    @Nullable
    public static ResourceLocation resolvePreferredWorkstationId(PatternEncodingSourceAware sourceAware) {
        if (!sourceAware.data_energistics$isPatternSourceEnabled()) {
            return null;
        }

        if (shouldIgnoreWorkstationMemory(sourceAware)) {
            return null;
        }

        ResourceLocation workstationId = sourceAware.data_energistics$getPendingPatternSource();
        if (workstationId != null) {
            return workstationId;
        }

        return sourceAware.data_energistics$getLastEncodedPatternSource();
    }

    private static boolean shouldIgnoreWorkstationMemory(PatternEncodingSourceAware sourceAware) {
        if (!(sourceAware instanceof PatternEncodingPreviewMenu previewMenuHost)) {
            return false;
        }

        EncodingMode mode = previewMenuHost.data_energistics$getEncodingMode();
        return mode == EncodingMode.CRAFTING || mode == EncodingMode.STONECUTTING || mode == EncodingMode.SMITHING_TABLE;
    }

    @Nullable
    private static DataRipperReassemblerRecipe resolveDataRipperReassemblerRecipe(@Nullable Object recipe,
                                                                                  @Nullable Object transferContext) {
        DataRipperReassemblerRecipe resolved = unwrapDataRipperReassemblerRecipe(recipe);
        if (resolved != null) {
            return resolved;
        }

        return unwrapDataRipperReassemblerRecipe(transferContext);
    }

    @Nullable
    private static DataRipperReassemblerRecipe unwrapDataRipperReassemblerRecipe(@Nullable Object candidate) {
        if (candidate instanceof DataRipperReassemblerRecipe recipe) {
            return recipe;
        }

        if (candidate instanceof RecipeHolder<?> holder && holder.value() instanceof DataRipperReassemblerRecipe recipe) {
            return recipe;
        }

        return null;
    }

    private static void applyTransferKeyInputServer(ConfigInventory encodedInputsInv, int keySlot,
                                                    @Nullable GenericStack keyInput) {
        repackDataRipperInputs(encodedInputsInv, keyInput, null);
    }

    private static void applyTransferKeyOutputServer(ConfigInventory encodedOutputsInv,
                                                     @Nullable GenericStack keyOutput) {
        repackDataRipperOutputs(encodedOutputsInv, keyOutput, null);
    }

    private static void applyTransferFluidStacksServer(ConfigInventory inventory, int slotBase, int slotCount,
                                                       @Nullable List<GenericStack> stacks) {
        if (slotBase == DATA_RIPPER_FLUID_INPUT_SLOT_BASE) {
            repackDataRipperInputs(inventory, null, stacks);
            return;
        }
        if (slotBase == DATA_RIPPER_FLUID_OUTPUT_SLOT_BASE) {
            repackDataRipperOutputs(inventory, null, stacks);
            return;
        }

        for (int i = 0; i < slotCount; i++) {
            int slot = slotBase + i;
            if (slot < 0 || slot >= inventory.size()) {
                continue;
            }

            GenericStack stack = stacks != null && i < stacks.size() ? stacks.get(i) : null;
            inventory.setStack(slot, stack == null ? null : new GenericStack(stack.what(), stack.amount()));
        }
    }

    private static void sanitizeDataRipperItemInputs(ConfigInventory encodedInputsInv) {
        repackDataRipperInputs(encodedInputsInv, null, null);
    }

    private static void sanitizeDataRipperItemOutputs(ConfigInventory encodedOutputsInv) {
        repackDataRipperOutputs(encodedOutputsInv, null, null);
    }

    private static void repackDataRipperInputs(ConfigInventory encodedInputsInv,
                                               @Nullable GenericStack overrideKeyInput,
                                               @Nullable List<GenericStack> overrideFluidInputs) {
        List<GenericStack> plainItems = collectPlainItemStacks(encodedInputsInv);
        GenericStack keyInput = overrideKeyInput != null ? copyGenericStack(overrideKeyInput) : extractDataRipperKeyStack(encodedInputsInv);
        List<GenericStack> fluidInputs = overrideFluidInputs != null ? copyGenericStacks(overrideFluidInputs) : extractFluidStacks(encodedInputsInv);

        rewriteStacks(encodedInputsInv, plainItems, keyInput, fluidInputs);
    }

    private static void repackDataRipperOutputs(ConfigInventory encodedOutputsInv,
                                                @Nullable GenericStack overrideKeyOutput,
                                                @Nullable List<GenericStack> overrideFluidOutputs) {
        List<GenericStack> plainItems = collectPlainItemStacks(encodedOutputsInv);
        GenericStack keyOutput = overrideKeyOutput != null ? copyGenericStack(overrideKeyOutput) : extractDataRipperKeyStack(encodedOutputsInv);
        List<GenericStack> fluidOutputs = overrideFluidOutputs != null ? copyGenericStacks(overrideFluidOutputs) : extractFluidStacks(encodedOutputsInv);

        rewriteStacks(encodedOutputsInv, plainItems, keyOutput, fluidOutputs);
    }

    private static void rewriteStacks(ConfigInventory inventory, List<GenericStack> plainItems,
                                      @Nullable GenericStack keyStack, List<GenericStack> fluidStacks) {
        List<GenericStack> reordered = new ArrayList<>(plainItems.size() + fluidStacks.size() + 1);
        reordered.addAll(plainItems);
        if (isMeaningfulGenericStack(keyStack)) {
            reordered.add(keyStack);
        }
        reordered.addAll(copyGenericStacks(fluidStacks));

        int rewriteLimit = Math.min(inventory.size(), Math.max(reordered.size(), DataRipperReassemblerRecipe.ITEM_INPUT_SLOTS));
        for (int i = 0; i < rewriteLimit; i++) {
            GenericStack stack = i < reordered.size() ? reordered.get(i) : null;
            inventory.setStack(i, stack == null ? null : new GenericStack(stack.what(), stack.amount()));
        }
    }

    private static List<GenericStack> collectPlainItemStacks(ConfigInventory inventory) {
        List<GenericStack> result = new ArrayList<>(inventory.size());
        for (int i = 0; i < inventory.size(); i++) {
            GenericStack stack = inventory.getStack(i);
            if (isPlainItemStack(stack)) {
                result.add(copyGenericStack(stack));
            }
        }
        return result;
    }

    @Nullable
    private static GenericStack extractDataRipperKeyStack(ConfigInventory inventory) {
        for (int i = 0; i < inventory.size(); i++) {
            GenericStack stack = inventory.getStack(i);
            if (stack == null || stack.amount() <= 0) {
                continue;
            }
            if (isPlainItemStack(stack) || isWrappedGenericDisplayStack(stack) || stack.what() instanceof AEFluidKey) {
                continue;
            }
            return copyGenericStack(stack);
        }
        return null;
    }

    private static List<GenericStack> extractFluidStacks(ConfigInventory inventory) {
        List<GenericStack> result = new ArrayList<>();
        for (int i = 0; i < inventory.size(); i++) {
            GenericStack stack = inventory.getStack(i);
            if (stack != null && stack.amount() > 0 && stack.what() instanceof AEFluidKey) {
                result.add(copyGenericStack(stack));
            }
        }
        return result;
    }

    private static boolean isPlainItemStack(@Nullable GenericStack stack) {
        if (stack == null || !(stack.what() instanceof AEItemKey itemKey)) {
            return false;
        }
        return !itemKey.is(AEItems.WRAPPED_GENERIC_STACK);
    }

    private static boolean isMeaningfulGenericStack(@Nullable GenericStack stack) {
        return stack != null && stack.amount() > 0 && stack.what() != null;
    }

    @Nullable
    private static GenericStack copyGenericStack(@Nullable GenericStack stack) {
        return stack == null ? null : new GenericStack(stack.what(), stack.amount());
    }

    private static boolean isWrappedGenericDisplayStack(@Nullable GenericStack stack) {
        if (stack == null) {
            return false;
        }

        if (!(stack.what() instanceof AEItemKey itemKey)) {
            return false;
        }

        return itemKey.is(AEItems.WRAPPED_GENERIC_STACK);
    }

    private static boolean matchesEncodedOutputs(DataRipperReassemblerRecipe recipe, List<ItemStack> encodedOutputs,
                                                 @Nullable GenericStack encodedKeyOutput,
                                                 List<GenericStack> encodedFluidOutputs) {
        List<ItemStack> recipeOutputs = recipe.getItemOutputs();
        for (int i = 0; i < recipeOutputs.size(); i++) {
            if (i >= encodedOutputs.size()) {
                return false;
            }

            ItemStack expected = recipeOutputs.get(i);
            ItemStack actual = encodedOutputs.get(i);
            if (actual.isEmpty()) {
                return false;
            }
            if (!ItemStack.isSameItemSameComponents(expected, actual)) {
                return false;
            }
            if (actual.getCount() < expected.getCount()) {
                return false;
            }
        }

        if (!matchesGenericStack(recipe.getKeyOutput(), encodedKeyOutput)) {
            return false;
        }

        List<GenericStack> recipeFluidOutputs = recipe.getFluidOutputs();
        if (recipeFluidOutputs.size() > encodedFluidOutputs.size()) {
            return false;
        }
        for (int i = 0; i < recipeFluidOutputs.size(); i++) {
            if (!matchesGenericStack(recipeFluidOutputs.get(i), encodedFluidOutputs.get(i))) {
                return false;
            }
        }

        return !recipeOutputs.isEmpty() || isMeaningfulGenericStack(recipe.getKeyOutput()) || !recipeFluidOutputs.isEmpty();
    }

    public static void applyTransferKeyInputAction(PatternEncodingTermMenu menu, @Nullable String serializedKeyInput) {
        GenericStack keyInput = deserializeTransferKey(menu, serializedKeyInput);
        if (menu instanceof PatternEncodingTransferKeyAware transferKeyAware) {
            transferKeyAware.dataEnergistics$setDisplayedTransferKeyInputSerialized(serializedKeyInput);
        }
        LOGGER.debug("[DE][PatternKey] transfer action key={}", describeGenericStack(keyInput));
        writePendingTransferKeyInput(menu.getPlayer(), keyInput);

        if (menu.getMode() != EncodingMode.PROCESSING) {
            return;
        }
        if (!(menu.getHost() instanceof IPatternTerminalMenuHost host)) {
            return;
        }
        ResourceLocation pendingPatternSource = readPendingPatternSource(menu.getPlayer());
        if (!DATA_RIPPER_REASSEMBLER_ID.equals(pendingPatternSource)) {
            return;
        }

        PatternEncodingLogic logic = host.getLogic();
        ConfigInventory encodedInputsInv = logic.getEncodedInputInv();
        int keySlot = DATA_RIPPER_KEY_INPUT_SLOT;
        if (keySlot < 0 || keySlot >= encodedInputsInv.size()) {
            LOGGER.debug("[DE][PatternKey] transfer action key slot {} out of bounds size={}", keySlot, encodedInputsInv.size());
            return;
        }

        applyTransferKeyInputServer(encodedInputsInv, keySlot, keyInput);
    }

    public static void applyTransferKeyOutputAction(PatternEncodingTermMenu menu, @Nullable String serializedKeyOutput) {
        GenericStack keyOutput = deserializeTransferKey(menu, serializedKeyOutput);
        if (menu instanceof PatternEncodingTransferKeyAware transferKeyAware) {
            transferKeyAware.dataEnergistics$setDisplayedTransferKeyOutputSerialized(serializedKeyOutput);
        }
        writePendingTransferKeyOutput(menu.getPlayer(), keyOutput);

        if (menu.getMode() != EncodingMode.PROCESSING) {
            return;
        }
        if (!(menu.getHost() instanceof IPatternTerminalMenuHost host)) {
            return;
        }
        ResourceLocation pendingPatternSource = readPendingPatternSource(menu.getPlayer());
        if (!DATA_RIPPER_REASSEMBLER_ID.equals(pendingPatternSource)) {
            return;
        }

        PatternEncodingLogic logic = host.getLogic();
        ConfigInventory encodedOutputsInv = logic.getEncodedOutputInv();
        applyTransferKeyOutputServer(encodedOutputsInv, keyOutput);
    }

    public static void applyTransferFluidInputsAction(PatternEncodingTermMenu menu,
                                                      @Nullable String serializedFluidInputs) {
        List<GenericStack> fluidInputs = deserializeTransferFluidStacks(menu, serializedFluidInputs);
        writePendingTransferFluidInputs(menu.getPlayer(), fluidInputs);

        if (menu.getMode() != EncodingMode.PROCESSING) {
            return;
        }
        if (!(menu.getHost() instanceof IPatternTerminalMenuHost host)) {
            return;
        }
        ResourceLocation pendingPatternSource = readPendingPatternSource(menu.getPlayer());
        if (!DATA_RIPPER_REASSEMBLER_ID.equals(pendingPatternSource)) {
            return;
        }

        PatternEncodingLogic logic = host.getLogic();
        ConfigInventory encodedInputsInv = logic.getEncodedInputInv();
        applyTransferFluidStacksServer(encodedInputsInv, DATA_RIPPER_FLUID_INPUT_SLOT_BASE,
                DataRipperReassemblerRecipe.FLUID_INPUT_SLOTS, fluidInputs);
    }

    public static void applyTransferFluidOutputsAction(PatternEncodingTermMenu menu,
                                                       @Nullable String serializedFluidOutputs) {
        List<GenericStack> fluidOutputs = deserializeTransferFluidStacks(menu, serializedFluidOutputs);
        writePendingTransferFluidOutputs(menu.getPlayer(), fluidOutputs);

        if (menu.getMode() != EncodingMode.PROCESSING) {
            return;
        }
        if (!(menu.getHost() instanceof IPatternTerminalMenuHost host)) {
            return;
        }
        ResourceLocation pendingPatternSource = readPendingPatternSource(menu.getPlayer());
        if (!DATA_RIPPER_REASSEMBLER_ID.equals(pendingPatternSource)) {
            return;
        }

        PatternEncodingLogic logic = host.getLogic();
        ConfigInventory encodedOutputsInv = logic.getEncodedOutputInv();
        applyTransferFluidStacksServer(encodedOutputsInv, DATA_RIPPER_FLUID_OUTPUT_SLOT_BASE,
                DataRipperReassemblerRecipe.FLUID_OUTPUT_SLOTS, fluidOutputs);
    }

    private static void syncPendingTransferKeyInput(PatternEncodingTermMenu menu, @Nullable GenericStack keyInput) {
        if (menu.isClientSide()) {
            if (menu instanceof PatternEncodingTransferKeyAware transferKeyAware) {
                transferKeyAware.dataEnergistics$sendTransferKeyInputAction(serializeTransferKeyInput(menu, keyInput));
            }
            return;
        }

        writePendingTransferKeyInput(menu.getPlayer(), keyInput);
    }

    private static void syncPendingTransferKeyOutput(PatternEncodingTermMenu menu, @Nullable GenericStack keyOutput) {
        if (menu.isClientSide()) {
            if (menu instanceof PatternEncodingTransferKeyAware transferKeyAware) {
                transferKeyAware.dataEnergistics$sendTransferKeyOutputAction(serializeTransferKeyOutput(menu, keyOutput));
            }
            return;
        }

        writePendingTransferKeyOutput(menu.getPlayer(), keyOutput);
    }

    private static void syncPendingTransferFluidInputs(PatternEncodingTermMenu menu, List<GenericStack> fluidInputs) {
        if (menu.isClientSide()) {
            if (menu instanceof PatternEncodingTransferKeyAware transferKeyAware) {
                transferKeyAware.dataEnergistics$sendTransferFluidInputsAction(
                        serializeTransferFluidStacks(menu, fluidInputs));
            }
            return;
        }

        writePendingTransferFluidInputs(menu.getPlayer(), fluidInputs);
    }

    private static void syncPendingTransferFluidOutputs(PatternEncodingTermMenu menu, List<GenericStack> fluidOutputs) {
        if (menu.isClientSide()) {
            if (menu instanceof PatternEncodingTransferKeyAware transferKeyAware) {
                transferKeyAware.dataEnergistics$sendTransferFluidOutputsAction(
                        serializeTransferFluidStacks(menu, fluidOutputs));
            }
            return;
        }

        writePendingTransferFluidOutputs(menu.getPlayer(), fluidOutputs);
    }

    private static String serializeTransferKeyInput(PatternEncodingTermMenu menu, @Nullable GenericStack keyInput) {
        if (keyInput == null) {
            return CLEAR_TRANSFER_KEY_INPUT;
        }

        CompoundTag tag = GenericStack.writeTag(menu.getPlayer().registryAccess(), keyInput);
        return tag.toString();
    }

    private static String serializeTransferKeyOutput(PatternEncodingTermMenu menu, @Nullable GenericStack keyOutput) {
        if (keyOutput == null) {
            return CLEAR_TRANSFER_KEY_OUTPUT;
        }

        CompoundTag tag = GenericStack.writeTag(menu.getPlayer().registryAccess(), keyOutput);
        return tag.toString();
    }

    private static String serializeTransferFluidStacks(PatternEncodingTermMenu menu, @Nullable List<GenericStack> stacks) {
        if (stacks == null || stacks.isEmpty()) {
            return CLEAR_TRANSFER_FLUID_STACKS;
        }

        CompoundTag root = new CompoundTag();
        for (int i = 0; i < stacks.size(); i++) {
            GenericStack stack = stacks.get(i);
            if (stack == null) {
                continue;
            }
            root.put(Integer.toString(i), GenericStack.writeTag(menu.getPlayer().registryAccess(), stack));
        }
        return root.isEmpty() ? CLEAR_TRANSFER_FLUID_STACKS : root.toString();
    }

    @Nullable
    private static GenericStack deserializeTransferKey(PatternEncodingTermMenu menu,
                                                       @Nullable String serializedKey) {
        if (serializedKey == null || serializedKey.isEmpty()) {
            return null;
        }

        try {
            CompoundTag tag = TagParser.parseTag(serializedKey);
            return GenericStack.readTag(menu.getPlayer().registryAccess(), tag);
        } catch (CommandSyntaxException exception) {
            LOGGER.warn("Rejected malformed serialized pattern transfer key", exception);
            return null;
        }
    }

    private static List<GenericStack> deserializeTransferFluidStacks(PatternEncodingTermMenu menu,
                                                                     @Nullable String serializedFluidStacks) {
        if (serializedFluidStacks == null || serializedFluidStacks.isEmpty()) {
            return List.of();
        }

        try {
            CompoundTag root = TagParser.parseTag(serializedFluidStacks);
            int maxIndex = -1;
            for (String key : root.getAllKeys()) {
                try {
                    maxIndex = Math.max(maxIndex, Integer.parseInt(key));
                } catch (NumberFormatException exception) {
                    LOGGER.debug("Ignored non-slot key in serialized pattern transfer fluids: {}", key, exception);
                }
            }
            if (maxIndex < 0) {
                return List.of();
            }

            List<GenericStack> stacks = new ArrayList<>(Collections.nCopies(maxIndex + 1, null));
            for (int i = 0; i <= maxIndex; i++) {
                String key = Integer.toString(i);
                if (!root.contains(key, CompoundTag.TAG_COMPOUND)) {
                    continue;
                }
                GenericStack stack = GenericStack.readTag(menu.getPlayer().registryAccess(), root.getCompound(key));
                if (stack != null) {
                    stacks.set(i, stack);
                }
            }
            return stacks;
        } catch (CommandSyntaxException exception) {
            LOGGER.warn("Rejected malformed serialized pattern transfer fluids", exception);
            return List.of();
        }
    }

    @Nullable
    public static ResourceLocation readPendingPatternSource(Player player) {
        CompoundTag tag = getPatternSourceData(player, false);
        if (tag == null) {
            return null;
        }

        String value = tag.getString(TAG_PENDING);
        return value.isEmpty() ? null : ResourceLocation.tryParse(value);
    }

    public static void writePendingPatternSource(Player player, @Nullable ResourceLocation workstationId) {
        CompoundTag tag = getPatternSourceData(player, workstationId != null);
        if (tag == null) {
            return;
        }

        if (workstationId == null) {
            tag.remove(TAG_PENDING);
        } else {
            tag.putString(TAG_PENDING, workstationId.toString());
        }
        cleanupPatternSourceData(player, tag);
    }

    @Nullable
    public static ResourceLocation readLastEncodedPatternSource(Player player) {
        ResourceLocation sessionValue = PatternEncodingSessionState.getLastEncodedPatternSource(player.getUUID());
        return sessionValue != null ? sessionValue : readLegacyLastEncodedPatternSource(player);
    }

    /**
     * Reads the pre-client-preference persisted workstation without mutating legacy NBT.
     */
    @Nullable
    public static ResourceLocation readLegacyLastEncodedPatternSource(Player player) {
        CompoundTag tag = getPatternSourceData(player, false);
        if (tag == null) {
            return null;
        }
        String value = tag.getString(TAG_LAST);
        return value.isEmpty() ? null : ResourceLocation.tryParse(value);
    }

    public static void writeLastEncodedPatternSource(Player player, @Nullable ResourceLocation workstationId) {
        if (player.level().isClientSide()) {
            return;
        }

        if (workstationId == null) {
            PatternEncodingSessionState.clearLastEncodedPatternSource(player.getUUID());
        } else {
            PatternEncodingSessionState.setLastEncodedPatternSource(player.getUUID(), workstationId);
        }
    }

    @Nullable
    public static GenericStack readPendingTransferKeyInput(Player player) {
        GenericStack keyInput = PatternEncodingSessionState.getPendingTransferKeyInput(player.getUUID());
        if (keyInput != null) {
            return new GenericStack(keyInput.what(), keyInput.amount());
        }

        CompoundTag tag = getPatternSourceData(player, false);
        if (tag == null || !tag.contains(TAG_PENDING_KEY_INPUT, CompoundTag.TAG_COMPOUND)) {
            return null;
        }
        return GenericStack.readTag(player.registryAccess(), tag.getCompound(TAG_PENDING_KEY_INPUT));
    }

    @Nullable
    public static GenericStack readPendingTransferKeyOutput(Player player) {
        GenericStack keyOutput = PatternEncodingSessionState.getPendingTransferKeyOutput(player.getUUID());
        if (keyOutput != null) {
            return new GenericStack(keyOutput.what(), keyOutput.amount());
        }

        CompoundTag tag = getPatternSourceData(player, false);
        if (tag == null || !tag.contains(TAG_PENDING_KEY_OUTPUT, CompoundTag.TAG_COMPOUND)) {
            return null;
        }
        return GenericStack.readTag(player.registryAccess(), tag.getCompound(TAG_PENDING_KEY_OUTPUT));
    }

    public static void writePendingTransferKeyInput(Player player, @Nullable GenericStack keyInput) {
        if (player.level().isClientSide()) {
            return;
        }

        CompoundTag tag = getPatternSourceData(player, keyInput != null && keyInput.amount() > 0);
        if (keyInput == null || keyInput.amount() <= 0) {
            PatternEncodingSessionState.clearPendingTransferKeyInput(player.getUUID());
            if (tag != null) {
                tag.remove(TAG_PENDING_KEY_INPUT);
                cleanupPatternSourceData(player, tag);
            }
        } else {
            GenericStack copy = new GenericStack(keyInput.what(), keyInput.amount());
            PatternEncodingSessionState.setPendingTransferKeyInput(player.getUUID(), copy);
            if (tag != null) {
                tag.put(TAG_PENDING_KEY_INPUT, GenericStack.writeTag(player.registryAccess(), copy));
                cleanupPatternSourceData(player, tag);
            }
        }
    }

    public static void writePendingTransferKeyOutput(Player player, @Nullable GenericStack keyOutput) {
        if (player.level().isClientSide()) {
            return;
        }

        CompoundTag tag = getPatternSourceData(player, keyOutput != null && keyOutput.amount() > 0);
        if (keyOutput == null || keyOutput.amount() <= 0) {
            PatternEncodingSessionState.clearPendingTransferKeyOutput(player.getUUID());
            if (tag != null) {
                tag.remove(TAG_PENDING_KEY_OUTPUT);
                cleanupPatternSourceData(player, tag);
            }
        } else {
            GenericStack copy = new GenericStack(keyOutput.what(), keyOutput.amount());
            PatternEncodingSessionState.setPendingTransferKeyOutput(player.getUUID(), copy);
            if (tag != null) {
                tag.put(TAG_PENDING_KEY_OUTPUT, GenericStack.writeTag(player.registryAccess(), copy));
                cleanupPatternSourceData(player, tag);
            }
        }
    }

    private static boolean matchesGenericStack(@Nullable GenericStack expected, @Nullable GenericStack actual) {
        if (!isMeaningfulGenericStack(expected)) {
            return !isMeaningfulGenericStack(actual);
        }
        if (!isMeaningfulGenericStack(actual)) {
            return false;
        }
        return expected.what().equals(actual.what()) && actual.amount() >= expected.amount();
    }

    public static void writePendingTransferFluidInputs(Player player, @Nullable List<GenericStack> fluidInputs) {
        if (player.level().isClientSide()) {
            return;
        }

        List<GenericStack> copy = copyGenericStacks(fluidInputs);
        if (copy.isEmpty()) {
            PatternEncodingSessionState.clearPendingTransferFluidInputs(player.getUUID());
        } else {
            PatternEncodingSessionState.setPendingTransferFluidInputs(player.getUUID(), copy);
        }
    }

    /**
     * Returns a defensive copy of the Data Ripper fluid inputs awaiting the next encode.
     */
    public static List<GenericStack> readPendingTransferFluidInputs(Player player) {
        return copyGenericStacks(PatternEncodingSessionState.getPendingTransferFluidInputs(player.getUUID()));
    }

    public static void writePendingTransferFluidOutputs(Player player, @Nullable List<GenericStack> fluidOutputs) {
        if (player.level().isClientSide()) {
            return;
        }

        List<GenericStack> copy = copyGenericStacks(fluidOutputs);
        if (copy.isEmpty()) {
            PatternEncodingSessionState.clearPendingTransferFluidOutputs(player.getUUID());
        } else {
            PatternEncodingSessionState.setPendingTransferFluidOutputs(player.getUUID(), copy);
        }
    }

    /**
     * Returns a defensive copy of the Data Ripper fluid outputs awaiting the next encode.
     */
    public static List<GenericStack> readPendingTransferFluidOutputs(Player player) {
        return copyGenericStacks(PatternEncodingSessionState.getPendingTransferFluidOutputs(player.getUUID()));
    }

    private static List<GenericStack> copyGenericStacks(@Nullable List<GenericStack> stacks) {
        if (stacks == null || stacks.isEmpty()) {
            return List.of();
        }

        List<GenericStack> copy = new ArrayList<>(stacks.size());
        for (GenericStack stack : stacks) {
            if (stack == null || stack.amount() <= 0) {
                continue;
            }
            copy.add(new GenericStack(stack.what(), stack.amount()));
        }
        return copy.isEmpty() ? List.of() : List.copyOf(copy);
    }

    private static String describeItems(List<ItemStack> stacks) {
        List<String> result = new ArrayList<>(stacks.size());
        for (ItemStack stack : stacks) {
            if (stack == null || stack.isEmpty()) {
                result.add("empty");
            } else {
                result.add(BuiltInRegistries.ITEM.getKey(stack.getItem()) + "x" + stack.getCount());
            }
        }
        return result.toString();
    }

    private static String describeGenericStack(@Nullable GenericStack stack) {
        if (stack == null) {
            return "null";
        }
        return stack.what() + " x " + stack.amount();
    }

    public static boolean readPatternSourceEnabled(Player player) {
        CompoundTag tag = getPatternSourceData(player, false);
        return tag == null || !tag.contains(TAG_ENABLED) || tag.getBoolean(TAG_ENABLED);
    }

    /**
     * Returns whether the old player NBT explicitly stored the source preference.
     */
    public static boolean hasLegacyPatternSourceEnabled(Player player) {
        CompoundTag tag = getPatternSourceData(player, false);
        return tag != null && tag.contains(TAG_ENABLED);
    }

    public static void writePatternSourceEnabled(Player player, boolean enabled) {
        CompoundTag tag = getPatternSourceData(player, true);
        tag.putBoolean(TAG_ENABLED, enabled);
        if (!enabled) {
            tag.remove(TAG_PENDING);
            tag.remove(TAG_LAST);
            PatternEncodingSessionState.clearLastEncodedPatternSource(player.getUUID());
        }
        cleanupPatternSourceData(player, tag);
    }

    public static boolean readUploadEnabled(Player player) {
        CompoundTag tag = getPatternSourceData(player, false);
        return tag == null || !tag.contains(TAG_UPLOAD_ENABLED) || tag.getBoolean(TAG_UPLOAD_ENABLED);
    }

    /**
     * Returns whether the old player NBT explicitly stored the upload preference.
     */
    public static boolean hasLegacyUploadEnabled(Player player) {
        CompoundTag tag = getPatternSourceData(player, false);
        return tag != null && tag.contains(TAG_UPLOAD_ENABLED);
    }

    public static void writeUploadEnabled(Player player, boolean enabled) {
        CompoundTag tag = getPatternSourceData(player, true);
        tag.putBoolean(TAG_UPLOAD_ENABLED, enabled);
        cleanupPatternSourceData(player, tag);
    }

    public static Component resolveWorkstationDisplayName(ResourceLocation workstationId) {
        if (DATA_RIPPER_REASSEMBLER_ID.equals(workstationId)) {
            return Component.translatable("workstation.data_energistics.data_reassembler");
        }

        var item = BuiltInRegistries.ITEM.getOptional(workstationId).orElse(null);
        if (item != null) {
            return item.getDefaultInstance().getHoverName().copy();
        }

        var block = BuiltInRegistries.BLOCK.getOptional(workstationId).orElse(null);
        if (block != null) {
            return block.getName().copy();
        }

        return Component.literal(workstationId.toString());
    }

    @Nullable
    private static CompoundTag getPatternSourceData(Player player, boolean create) {
        CompoundTag persistentData = player.getPersistentData();
        if (!persistentData.contains(Player.PERSISTED_NBT_TAG, CompoundTag.TAG_COMPOUND)) {
            if (!create) {
                return null;
            }
            persistentData.put(Player.PERSISTED_NBT_TAG, new CompoundTag());
        }

        CompoundTag persisted = persistentData.getCompound(Player.PERSISTED_NBT_TAG);
        if (!persisted.contains(PLAYER_PATTERN_SOURCE_ROOT, CompoundTag.TAG_COMPOUND)) {
            if (!create) {
                return null;
            }
            persisted.put(PLAYER_PATTERN_SOURCE_ROOT, new CompoundTag());
        }

        return persisted.getCompound(PLAYER_PATTERN_SOURCE_ROOT);
    }

    private static void cleanupPatternSourceData(Player player, CompoundTag tag) {
        CompoundTag persistentData = player.getPersistentData();
        CompoundTag persisted = persistentData.getCompound(Player.PERSISTED_NBT_TAG);
        if (tag.isEmpty()) {
            persisted.remove(PLAYER_PATTERN_SOURCE_ROOT);
        }
        if (persisted.isEmpty()) {
            persistentData.remove(Player.PERSISTED_NBT_TAG);
        }
    }

}

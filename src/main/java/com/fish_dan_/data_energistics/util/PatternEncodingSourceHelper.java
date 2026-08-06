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
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.level.Level;

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
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import org.apache.logging.log4j.Logger;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Stream;

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
    private static final String WORKSTATION_MAPPINGS_RESOURCE = "data_energistics/pattern_workstation_mappings.json";
    private static final ResourceLocation CRAFTING_TABLE_ID = ResourceLocation.withDefaultNamespace("crafting_table");
    private static final ResourceLocation CRAFTING_RECIPE_TYPE_ID = ResourceLocation.withDefaultNamespace("crafting");
    private static final ResourceLocation FURNACE_ID = ResourceLocation.withDefaultNamespace("furnace");
    private static final ResourceLocation BLAST_FURNACE_ID = ResourceLocation.withDefaultNamespace("blast_furnace");
    private static final ResourceLocation SMOKER_ID = ResourceLocation.withDefaultNamespace("smoker");
    private static final ResourceLocation CAMPFIRE_ID = ResourceLocation.withDefaultNamespace("campfire");
    private static final ResourceLocation STONECUTTER_ID = ResourceLocation.withDefaultNamespace("stonecutter");
    private static final ResourceLocation STONECUTTING_RECIPE_TYPE_ID = ResourceLocation.withDefaultNamespace("stonecutting");
    private static final ResourceLocation SMITHING_TABLE_ID = ResourceLocation.withDefaultNamespace("smithing_table");
    private static final ResourceLocation SMITHING_RECIPE_TYPE_ID = ResourceLocation.withDefaultNamespace("smithing");
    private static final ResourceLocation AE2_INSCRIBER_ID = ResourceLocation.fromNamespaceAndPath("ae2", "inscriber");
    private static final ResourceLocation AE2_CHARGER_ID = ResourceLocation.fromNamespaceAndPath("ae2", "charger");
    private static final ResourceLocation DATA_RIPPER_REASSEMBLER_ID = Data_Energistics.id("data_reassembler");
    private static final ResourceLocation EXTENDEDAE_CRYSTAL_ASSEMBLER_ID = ResourceLocation.fromNamespaceAndPath("extendedae", "crystal_assembler");
    private static final ResourceLocation SUT_ADVANCED_ALLOY_FURNACE_RECIPE_ID = ResourceLocation.fromNamespaceAndPath("useless_mod", "advanced_alloy_furnace");
    private static final ResourceLocation SUT_ADVANCED_ALLOY_FURNACE_BLOCK_ID = ResourceLocation.fromNamespaceAndPath("useless_mod", "advanced_alloy_furnace_block");
    private static final ResourceLocation MEKANISM_COMBINER_ID = ResourceLocation.fromNamespaceAndPath("mekanism", "combiner");
    private static final ResourceLocation MEKANISM_OSMIUM_COMPRESSOR_ID = ResourceLocation.fromNamespaceAndPath("mekanism", "osmium_compressor");
    private static final ResourceLocation MEKANISM_CRUSHER_ID = ResourceLocation.fromNamespaceAndPath("mekanism", "crusher");
    private static final ResourceLocation MEKANISM_ENRICHMENT_CHAMBER_ID = ResourceLocation.fromNamespaceAndPath("mekanism", "enrichment_chamber");
    private static final ResourceLocation MEKANISM_CHEMICAL_INJECTION_CHAMBER_ID = ResourceLocation.fromNamespaceAndPath("mekanism", "chemical_injection_chamber");
    private static final ResourceLocation MEKANISM_PURIFICATION_CHAMBER_ID = ResourceLocation.fromNamespaceAndPath("mekanism", "purification_chamber");
    private static final ResourceLocation MEKANISM_METALLURGIC_INFUSER_ID = ResourceLocation.fromNamespaceAndPath("mekanism", "metallurgic_infuser");
    private static final ResourceLocation MEKANISM_PAINTING_MACHINE_ID = ResourceLocation.fromNamespaceAndPath("mekanism", "painting_machine");
    private static final ResourceLocation MEKANISM_PRECISION_SAWMILL_ID = ResourceLocation.fromNamespaceAndPath("mekanism", "precision_sawmill");
    private static final ResourceLocation MEKANISM_ENERGIZED_SMELTER_ID = ResourceLocation.fromNamespaceAndPath("mekanism", "energized_smelter");
    private static final ResourceLocation MEKANISM_ELECTROLYTIC_SEPARATOR_ID = ResourceLocation.fromNamespaceAndPath("mekanism", "electrolytic_separator");
    private static final ResourceLocation MEKANISM_CHEMICAL_WASHER_ID = ResourceLocation.fromNamespaceAndPath("mekanism", "chemical_washer");
    private static final ResourceLocation MEKANISM_SOLAR_NEUTRON_ACTIVATOR_ID = ResourceLocation.fromNamespaceAndPath("mekanism", "solar_neutron_activator");
    private static final ResourceLocation MEKANISM_CHEMICAL_CRYSTALLIZER_ID = ResourceLocation.fromNamespaceAndPath("mekanism", "chemical_crystallizer");
    private static final ResourceLocation MEKANISM_CHEMICAL_DISSOLUTION_CHAMBER_ID = ResourceLocation.fromNamespaceAndPath("mekanism", "chemical_dissolution_chamber");
    private static final ResourceLocation MEKANISM_CHEMICAL_OXIDIZER_ID = ResourceLocation.fromNamespaceAndPath("mekanism", "chemical_oxidizer");
    private static final ResourceLocation MEKANISM_PIGMENT_EXTRACTOR_ID = ResourceLocation.fromNamespaceAndPath("mekanism", "pigment_extractor");
    private static final ResourceLocation MEKANISM_PIGMENT_MIXER_ID = ResourceLocation.fromNamespaceAndPath("mekanism", "pigment_mixer");
    private static final ResourceLocation MEKANISM_ROTARY_CONDENSENTRATOR_ID = ResourceLocation.fromNamespaceAndPath("mekanism", "rotary_condensentrator");
    private static final ResourceLocation MEKANISM_THERMAL_EVAPORATION_CONTROLLER_ID = ResourceLocation.fromNamespaceAndPath("mekanism", "thermal_evaporation_controller");
    private static final ResourceLocation MEKANISM_CHEMICAL_INFUSER_ID = ResourceLocation.fromNamespaceAndPath("mekanism", "chemical_infuser");
    private static final ResourceLocation MEKANISM_ANTIPROTONIC_NUCLEOSYNTHESIZER_ID = ResourceLocation.fromNamespaceAndPath("mekanism", "antiprotonic_nucleosynthesizer");
    private static final ResourceLocation MEKANISM_PRESSURIZED_REACTION_CHAMBER_ID = ResourceLocation.fromNamespaceAndPath("mekanism", "pressurized_reaction_chamber");
    private static final ResourceLocation CREATE_MECHANICAL_MIXER_ID = ResourceLocation.fromNamespaceAndPath("create", "mechanical_mixer");
    private static final ResourceLocation CREATE_MECHANICAL_SAW_ID = ResourceLocation.fromNamespaceAndPath("create", "mechanical_saw");
    private static final ResourceLocation CREATE_MECHANICAL_PRESS_ID = ResourceLocation.fromNamespaceAndPath("create", "mechanical_press");
    private static final ResourceLocation CREATE_DEPLOYER_ID = ResourceLocation.fromNamespaceAndPath("create", "deployer");
    private static final ResourceLocation CREATE_SPOUT_ID = ResourceLocation.fromNamespaceAndPath("create", "spout");
    private static final ResourceLocation CREATE_MECHANICAL_CRAFTER_ID = ResourceLocation.fromNamespaceAndPath("create", "mechanical_crafter");
    private static final ResourceLocation CREATE_MILLSTONE_ID = ResourceLocation.fromNamespaceAndPath("create", "millstone");
    private static final ResourceLocation CREATE_CRUSHING_WHEEL_ID = ResourceLocation.fromNamespaceAndPath("create", "crushing_wheel");
    private static final ResourceLocation CREATE_ENCASED_FAN_ID = ResourceLocation.fromNamespaceAndPath("create", "encased_fan");
    private static final ResourceLocation CREATE_BASIN_ID = ResourceLocation.fromNamespaceAndPath("create", "basin");
    private static final ExternalMappings EXTERNAL_MAPPINGS = loadExternalMappings();
    private static final Map<String, ResourceLocation> RECIPE_TYPE_TO_WORKSTATION = createRecipeTypeToWorkstationMap();
    private static final Map<String, String> WORKSTATION_PATH_HINTS = createWorkstationPathHints();
    private static final List<String> EXTENDED_HINT_TOKENS = List.of(
            "extended", "plus", "advanced", "super", "ultimate", "elite", "扩展", "增强", "高级", "超级");
    private static final List<String> NON_WORKSTATION_HINT_TOKENS = List.of(
            "controller", "cable", "cover", "upgrade", "facade", "terminal", "bus", "interface",
            "panel", "part", "hatch", "frame", "wall", "glass", "pipe", "conduit", "wire", "io");
    private static final List<String> WORKSTATION_HINT_TOKENS = List.of(
            "machine", "assembler", "assembly", "station", "processor", "worker", "crafter", "chamber",
            "crusher", "press", "mixer", "saw", "infuser", "reactor", "spout", "deployer", "charger",
            "inscriber", "smelter", "furnace", "stonecutter", "smith", "mill", "milling", "cutting",
            "mixing", "pressing", "reaction", "crafting", "process", "processing",
            "机器", "装配", "工作站", "处理站", "处理器", "压印", "充能", "切石", "锻造", "编译", "合成");

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
    public static ResourceLocation resolveWorkstationForTransfer(@Nullable Object recipe, @Nullable Object transferContext) {
        ResourceLocation knownWorkstation = resolveKnownWorkstationFromTransferSource(recipe);
        if (knownWorkstation != null) {
            return knownWorkstation;
        }

        knownWorkstation = resolveKnownWorkstationFromTransferSource(transferContext);
        if (knownWorkstation != null) {
            return knownWorkstation;
        }

        if (recipe instanceof RecipeHolder<?> holder) {
            ResourceLocation directRecipeWorkstation = resolveWorkstationForRecipe(holder.value());
            if (directRecipeWorkstation != null) {
                return directRecipeWorkstation;
            }
        }
        if (recipe instanceof Recipe<?> vanillaRecipe) {
            ResourceLocation directRecipeWorkstation = resolveWorkstationForRecipe(vanillaRecipe);
            if (directRecipeWorkstation != null) {
                return directRecipeWorkstation;
            }
        }

        ResourceLocation recipeWorkstation = resolveWorkstationFromTransferContext(recipe);
        if (recipeWorkstation != null) {
            return recipeWorkstation;
        }

        ResourceLocation contextWorkstation = resolveWorkstationFromTransferContext(transferContext);
        if (contextWorkstation != null) {
            return contextWorkstation;
        }

        return null;
    }

    @Nullable
    public static ResourceLocation resolveWorkstationForRecipe(@Nullable Recipe<?> recipe) {
        if (recipe == null) {
            return null;
        }

        ResourceLocation knownWorkstation = resolveKnownWorkstationFromTransferSource(recipe);
        if (knownWorkstation != null) {
            return knownWorkstation;
        }

        ResourceLocation recipeTypeId = BuiltInRegistries.RECIPE_TYPE.getKey(recipe.getType());
        if (recipeTypeId == null) {
            return null;
        }

        ResourceLocation mappedId = RECIPE_TYPE_TO_WORKSTATION.get(recipeTypeId.toString());
        if (mappedId != null) {
            return mappedId;
        }

        if (BuiltInRegistries.ITEM.containsKey(recipeTypeId)) {
            return recipeTypeId;
        }

        if (BuiltInRegistries.BLOCK.containsKey(recipeTypeId)) {
            return recipeTypeId;
        }

        ResourceLocation derivedId = resolveDerivedWorkstationId(recipeTypeId, recipe);
        if (derivedId != null) {
            return derivedId;
        }

        return null;
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

    public static void rememberTransferSource(PatternEncodingTermMenu menu, @Nullable Object recipe,
                                              @Nullable Object transferContext) {
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

            ResourceLocation workstationId = resolveWorkstationForTransfer(recipe, transferContext);
            sourceAware.data_energistics$setPendingPatternSource(workstationId);
            if (sourceAware.data_energistics$isPatternSourceEnabled()) {
                sourceAware.data_energistics$setLastEncodedPatternSource(workstationId);
            }
            if (menu instanceof PatternEncodingPreferenceMenu preferenceMenu) {
                preferenceMenu.data_energistics$getPreferenceSession().setRankingContext(
                        resolveRankingContext(menu.getMode(), recipe, transferContext, workstationId));
            }
        }
    }

    /**
     * Resolves the exact history key for one successful recipe transfer.
     */
    @Nullable
    public static PatternEncodingRankingContext resolveRankingContext(@Nullable EncodingMode mode,
                                                                      @Nullable Object recipe,
                                                                      @Nullable Object transferContext,
                                                                      @Nullable ResourceLocation workstationId) {
        if (!isResolvableWorkstation(workstationId)) {
            return null;
        }
        PatternEncodingRankingContext resolved = resolveRankingContext(recipe, workstationId);
        if (resolved == null) {
            resolved = resolveRankingContext(transferContext, workstationId);
        }
        if (resolved != null || mode == EncodingMode.PROCESSING) {
            return resolved;
        }
        return resolveFixedModeRankingContext(mode, workstationId);
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
        return recipeTypeId == null ? null : PatternEncodingRankingContext.forRecipeType(recipeTypeId, workstationId);
    }

    /**
     * Verifies that a client ranking context describes the recipe mode and workstation currently owned by the menu.
     * Fixed vanilla modes are derived entirely on the server; processing contexts must reference a loaded recipe type
     * or recipe ID and the menu's current pending/last workstation.
     */
    public static boolean isRankingContextValid(PatternEncodingPreviewMenu previewMenu,
                                                PatternEncodingSourceAware sourceAware,
                                                @Nullable PatternEncodingRankingContext context,
                                                Level level) {
        if (previewMenu == null || sourceAware == null || level == null) {
            throw new IllegalArgumentException("Pattern ranking validation requires a menu, source state, and level");
        }
        EncodingMode mode = previewMenu.data_energistics$getEncodingMode();
        ResourceLocation fixedWorkstation = resolveFallbackWorkstationForMode(mode);
        if (fixedWorkstation != null) {
            return Objects.equals(context, resolveFixedModeRankingContext(mode, fixedWorkstation));
        }
        if (mode != EncodingMode.PROCESSING) {
            return context == null;
        }
        ResourceLocation workstation = sourceAware.data_energistics$getPendingPatternSource();
        if (workstation == null) {
            workstation = sourceAware.data_energistics$getLastEncodedPatternSource();
        }
        if (context == null) {
            return true;
        }
        if (!Objects.equals(workstation, context.workstation()) || !isResolvableWorkstation(workstation)) {
            return false;
        }
        String recipeScope = context.recipeScope();
        if (recipeScope.startsWith("type:")) {
            ResourceLocation recipeTypeId = ResourceLocation.tryParse(recipeScope.substring("type:".length()));
            return recipeTypeId != null && BuiltInRegistries.RECIPE_TYPE.containsKey(recipeTypeId);
        }
        if (recipeScope.startsWith("recipe:")) {
            ResourceLocation recipeId = ResourceLocation.tryParse(recipeScope.substring("recipe:".length()));
            return recipeId != null && level.getRecipeManager().byKey(recipeId).isPresent();
        }
        return false;
    }

    private static boolean isResolvableWorkstation(@Nullable ResourceLocation workstationId) {
        return workstationId != null && (BuiltInRegistries.BLOCK.containsKey(workstationId) ||
                BuiltInRegistries.ITEM.containsKey(workstationId));
    }

    @Nullable
    private static PatternEncodingRankingContext resolveRankingContext(@Nullable Object source,
                                                                       ResourceLocation workstationId) {
        if (source instanceof RecipeHolder<?> holder) {
            ResourceLocation typeId = BuiltInRegistries.RECIPE_TYPE.getKey(holder.value().getType());
            if (typeId != null) {
                return PatternEncodingRankingContext.forRecipeType(typeId, workstationId);
            }
            return PatternEncodingRankingContext.forRecipe(holder.id(), workstationId);
        }
        if (source instanceof Recipe<?> recipe) {
            ResourceLocation typeId = BuiltInRegistries.RECIPE_TYPE.getKey(recipe.getType());
            if (typeId != null) {
                return PatternEncodingRankingContext.forRecipeType(typeId, workstationId);
            }
        }
        return null;
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
    private static ResourceLocation resolveWorkstationFromTransferContext(@Nullable Object context) {
        if (context == null) {
            return null;
        }

        ResourceLocation knownWorkstation = resolveKnownWorkstationFromTransferSource(context);
        if (knownWorkstation != null) {
            return knownWorkstation;
        }

        ResourceLocation catalystWorkstation = resolveWorkstationFromCatalysts(context);
        if (catalystWorkstation != null) {
            return catalystWorkstation;
        }

        Object backingRecipe = PatternEncodingReflectionAccess.invokeNoArg(context, "getBackingRecipe");
        if (backingRecipe instanceof RecipeHolder<?> holder) {
            return resolveWorkstationForRecipe(holder.value());
        }
        if (backingRecipe instanceof Recipe<?> recipe) {
            return resolveWorkstationForRecipe(recipe);
        }

        Object category = PatternEncodingReflectionAccess.invokeNoArg(context, "getCategory");
        ResourceLocation categoryId = PatternEncodingReflectionAccess.tryReadResourceLocation(category, "getId");
        if (categoryId != null) {
            ResourceLocation categoryWorkstation = resolveWorkstationFromIdentifier(categoryId, category, context);
            if (categoryWorkstation != null) {
                return categoryWorkstation;
            }
        }

        ResourceLocation directId = PatternEncodingReflectionAccess.tryReadResourceLocation(context, "getId");
        if (directId != null) {
            ResourceLocation directWorkstation = resolveWorkstationFromIdentifier(directId, context, category);
            if (directWorkstation != null) {
                return directWorkstation;
            }
        }

        ResourceLocation titleWorkstation = resolveWorkstationFromTextHints(null, category, context);
        if (titleWorkstation != null) {
            return titleWorkstation;
        }

        return null;
    }

    @Nullable
    private static ResourceLocation resolveKnownWorkstationFromTransferSource(@Nullable Object source) {
        return resolveKnownWorkstationFromTransferSource(source, Collections.newSetFromMap(new IdentityHashMap<>()));
    }

    @Nullable
    private static ResourceLocation resolveKnownWorkstationFromTransferSource(@Nullable Object source, Set<Object> visited) {
        if (source == null || !visited.add(source)) {
            return null;
        }

        if (source instanceof RecipeHolder<?> holder) {
            return resolveKnownWorkstationFromTransferSource(holder.value(), visited);
        }

        if (source instanceof Recipe<?> recipe) {
            ResourceLocation recipeTypeId = BuiltInRegistries.RECIPE_TYPE.getKey(recipe.getType());
            if (SUT_ADVANCED_ALLOY_FURNACE_RECIPE_ID.equals(recipeTypeId)) {
                return SUT_ADVANCED_ALLOY_FURNACE_BLOCK_ID;
            }
        }

        String className = source.getClass().getName();
        if ("com.sorrowmist.useless.content.recipe.AdvancedAlloyFurnaceRecipe".equals(className) || "com.sorrowmist.useless.compat.jei.AdvancedAlloyFurnaceRecipeCategory".equals(className)) {
            return SUT_ADVANCED_ALLOY_FURNACE_BLOCK_ID;
        }

        ResourceLocation id = PatternEncodingReflectionAccess.tryReadResourceLocation(source, "getId");
        if (SUT_ADVANCED_ALLOY_FURNACE_RECIPE_ID.equals(id)) {
            return SUT_ADVANCED_ALLOY_FURNACE_BLOCK_ID;
        }

        Object category = PatternEncodingReflectionAccess.invokeNoArg(source, "getCategory");
        ResourceLocation categoryWorkstation = resolveKnownWorkstationFromTransferSource(category, visited);
        if (categoryWorkstation != null) {
            return categoryWorkstation;
        }

        Object backingRecipe = PatternEncodingReflectionAccess.invokeNoArg(source, "getBackingRecipe");
        ResourceLocation recipeWorkstation = resolveKnownWorkstationFromTransferSource(backingRecipe, visited);
        if (recipeWorkstation != null) {
            return recipeWorkstation;
        }

        if (hasAdvancedAlloyFurnaceTitle(source)) {
            return SUT_ADVANCED_ALLOY_FURNACE_BLOCK_ID;
        }

        return null;
    }

    private static boolean hasAdvancedAlloyFurnaceTitle(Object source) {
        List<String> hints = collectHintTexts(null, source,
                PatternEncodingReflectionAccess.invokeNoArg(source, "getCategory"),
                PatternEncodingReflectionAccess.invokeNoArg(source, "getTitle"),
                PatternEncodingReflectionAccess.invokeNoArg(source, "getName"));
        for (String hint : hints) {
            String normalizedHint = normalizeHintText(hint);
            if (normalizedHint.contains("advancedalloyfurnace") || normalizedHint.contains("万象合金炉")) {
                return true;
            }
        }
        return false;
    }

    @Nullable
    private static ResourceLocation resolveWorkstationFromCatalysts(Object context) {
        Object catalysts = PatternEncodingReflectionAccess.invokeNoArg(context, "getCatalysts");
        Collection<?> collection;
        if (catalysts instanceof Collection<?> catalystCollection) {
            collection = catalystCollection;
        } else {
            Object catalystSlots = PatternEncodingReflectionAccess.invokeSlotViewsByRole(context, "CATALYST");
            if (catalystSlots instanceof Collection<?> slotCollection) {
                collection = slotCollection;
            } else {
                return null;
            }
        }

        List<String> hintTexts = collectHintTexts(PatternEncodingReflectionAccess.tryReadResourceLocation(context, "getId"),
                PatternEncodingReflectionAccess.invokeNoArg(context, "getCategory"),
                context,
                PatternEncodingReflectionAccess.invokeNoArg(context, "getBackingRecipe"));
        ResourceLocation bestCandidate = null;
        int bestScore = Integer.MIN_VALUE;
        for (Object catalyst : collection) {
            for (ResourceLocation candidate : collectWorkstationCandidatesFromCatalyst(catalyst)) {
                int score = scoreCatalystCandidate(candidate, hintTexts);
                if (score > bestScore) {
                    bestScore = score;
                    bestCandidate = candidate;
                }
            }
        }

        return bestCandidate;
    }

    private static List<ResourceLocation> collectWorkstationCandidatesFromCatalyst(@Nullable Object catalyst) {
        return collectWorkstationCandidatesFromCatalyst(catalyst,
                Collections.newSetFromMap(new IdentityHashMap<>()));
    }

    private static List<ResourceLocation> collectWorkstationCandidatesFromCatalyst(@Nullable Object catalyst,
                                                                                   Set<Object> visited) {
        List<ResourceLocation> candidates = new ArrayList<>();
        if (catalyst == null) {
            return candidates;
        }
        if (!visited.add(catalyst)) {
            return candidates;
        }

        if (catalyst instanceof ItemStack stack && !stack.isEmpty()) {
            appendWorkstationCandidate(candidates, BuiltInRegistries.ITEM.getKey(stack.getItem()));
            return candidates;
        }

        Object displayedItemStack = PatternEncodingReflectionAccess.invokeNoArg(catalyst, "getDisplayedItemStack");
        if (displayedItemStack instanceof Optional<?> optional && optional.orElse(null) instanceof ItemStack stack && !stack.isEmpty()) {
            appendWorkstationCandidate(candidates, BuiltInRegistries.ITEM.getKey(stack.getItem()));
        }

        Object itemStacks = PatternEncodingReflectionAccess.invokeNoArg(catalyst, "getItemStacks");
        if (itemStacks instanceof Stream<?> stream) {
            try (stream) {
                stream.limit(8).forEach(entry -> {
                    if (entry instanceof ItemStack stack && !stack.isEmpty()) {
                        appendWorkstationCandidate(candidates, BuiltInRegistries.ITEM.getKey(stack.getItem()));
                    }
                });
            }
        }

        Object itemStack = PatternEncodingReflectionAccess.invokeNoArg(catalyst, "getItemStack");
        if (itemStack instanceof ItemStack stack && !stack.isEmpty()) {
            appendWorkstationCandidate(candidates, BuiltInRegistries.ITEM.getKey(stack.getItem()));
        }

        Object emiStacks = PatternEncodingReflectionAccess.invokeNoArg(catalyst, "getEmiStacks");
        if (emiStacks instanceof Collection<?> collection) {
            for (Object emiStack : collection) {
                for (ResourceLocation candidate : collectWorkstationCandidatesFromCatalyst(emiStack, visited)) {
                    appendWorkstationCandidate(candidates, candidate);
                }
            }
        }

        ResourceLocation id = PatternEncodingReflectionAccess.tryReadResourceLocation(catalyst, "getId");
        if (id != null) {
            appendWorkstationCandidate(candidates, resolveWorkstationFromIdentifier(id, catalyst));
        }

        return candidates;
    }

    @Nullable
    private static ResourceLocation resolveWorkstationFromIdentifier(@Nullable ResourceLocation id, Object... hintSources) {
        if (id == null) {
            return null;
        }

        ResourceLocation mappedId = RECIPE_TYPE_TO_WORKSTATION.get(id.toString());
        if (mappedId != null) {
            return mappedId;
        }

        if (BuiltInRegistries.ITEM.containsKey(id)) {
            return id;
        }

        if (BuiltInRegistries.BLOCK.containsKey(id)) {
            return id;
        }

        ResourceLocation derivedId = resolveDerivedWorkstationId(id, hintSources);
        if (derivedId != null) {
            return derivedId;
        }

        return null;
    }

    @Nullable
    private static ResourceLocation resolveDerivedWorkstationId(ResourceLocation id, Object... hintSources) {
        String path = id.getPath();
        String namespace = id.getNamespace();
        if (path.isEmpty()) {
            return null;
        }

        ResourceLocation directCandidate = tryResolveWorkstationCandidate(namespace, path);
        if (directCandidate != null) {
            return directCandidate;
        }

        for (var entry : WORKSTATION_PATH_HINTS.entrySet()) {
            if (!path.contains(entry.getKey())) {
                continue;
            }

            ResourceLocation candidate = tryResolveWorkstationCandidate(namespace, entry.getValue());
            if (candidate != null) {
                return candidate;
            }

            for (String aliasNamespace : getAliasedNamespaces(namespace)) {
                candidate = tryResolveWorkstationCandidate(aliasNamespace, entry.getValue());
                if (candidate != null) {
                    return candidate;
                }
            }
        }

        return resolveWorkstationFromTextHints(id, hintSources);
    }

    @Nullable
    private static ResourceLocation tryResolveWorkstationCandidate(String namespace, String path) {
        ResourceLocation candidate = ResourceLocation.fromNamespaceAndPath(namespace, path);
        if (BuiltInRegistries.ITEM.containsKey(candidate) || BuiltInRegistries.BLOCK.containsKey(candidate)) {
            return candidate;
        }
        return null;
    }

    @Nullable
    private static ResourceLocation resolveWorkstationFromTextHints(@Nullable ResourceLocation baseId, Object... hintSources) {
        List<String> hints = collectHintTexts(baseId, hintSources);
        if (hints.isEmpty()) {
            return null;
        }

        List<String> namespaces = collectCandidateNamespaces(baseId);
        ResourceLocation bestId = null;
        int bestScore = 0;

        for (String namespace : namespaces) {
            for (ResourceLocation candidateId : BuiltInRegistries.BLOCK.keySet()) {
                if (!namespace.equals(candidateId.getNamespace())) {
                    continue;
                }

                int score = scoreCandidate(candidateId, hints, true);
                if (score > bestScore) {
                    bestScore = score;
                    bestId = candidateId;
                }
            }

            for (ResourceLocation candidateId : BuiltInRegistries.ITEM.keySet()) {
                if (!namespace.equals(candidateId.getNamespace())) {
                    continue;
                }

                int score = scoreCandidate(candidateId, hints, false);
                if (score > bestScore) {
                    bestScore = score;
                    bestId = candidateId;
                }
            }
        }

        return bestScore >= 60 ? bestId : null;
    }

    private static List<String> collectHintTexts(@Nullable ResourceLocation baseId, Object... hintSources) {
        List<String> hints = new ArrayList<>();
        if (baseId != null) {
            hints.add(baseId.getPath());
            hints.add(baseId.toString());
        }

        for (Object hintSource : hintSources) {
            appendHintText(hints, hintSource);
            appendHintText(hints, PatternEncodingReflectionAccess.invokeNoArg(hintSource, "getTitle"));
            appendHintText(hints, PatternEncodingReflectionAccess.invokeNoArg(hintSource, "getName"));
            appendHintText(hints, PatternEncodingReflectionAccess.invokeNoArg(hintSource, "getTooltip"));
        }

        hints.removeIf(String::isBlank);
        return hints;
    }

    private static void appendWorkstationCandidate(List<ResourceLocation> candidates, @Nullable ResourceLocation candidate) {
        if (candidate != null && !candidates.contains(candidate)) {
            candidates.add(candidate);
        }
    }

    private static void appendHintText(List<String> hints, @Nullable Object value) {
        if (value == null) {
            return;
        }
        if (value instanceof Component component) {
            hints.add(component.getString());
            return;
        }
        if (value instanceof CharSequence sequence) {
            hints.add(sequence.toString());
            return;
        }
        if (value instanceof ResourceLocation id) {
            hints.add(id.getPath());
            hints.add(id.toString());
        }
    }

    private static List<String> collectCandidateNamespaces(@Nullable ResourceLocation baseId) {
        List<String> namespaces = new ArrayList<>();
        if (baseId != null) {
            namespaces.add(baseId.getNamespace());
            for (String aliasNamespace : getAliasedNamespaces(baseId.getNamespace())) {
                if (!namespaces.contains(aliasNamespace)) {
                    namespaces.add(aliasNamespace);
                }
            }
        }
        return namespaces;
    }

    private static List<String> getAliasedNamespaces(String namespace) {
        return EXTERNAL_MAPPINGS.namespaceAliases().getOrDefault(namespace, List.of());
    }

    private static int scoreCandidate(ResourceLocation candidateId, List<String> hints, boolean blockCandidate) {
        String candidatePath = normalizeHintText(candidateId.getPath());
        if (candidatePath.isEmpty()) {
            return 0;
        }

        List<String> candidateTexts = collectCandidateTexts(candidateId, blockCandidate);
        int score = blockCandidate ? 6 : 0;
        for (String hint : hints) {
            String normalizedHint = normalizeHintText(hint);
            if (normalizedHint.isEmpty()) {
                continue;
            }

            if (normalizedHint.equals(candidatePath) || normalizedHint.endsWith(candidatePath)) {
                score += 120;
            }
            if (candidatePath.endsWith(normalizedHint) && normalizedHint.length() >= 4) {
                score += 65;
            }

            for (String candidateText : candidateTexts) {
                if (candidateText.isEmpty()) {
                    continue;
                }
                if (normalizedHint.equals(candidateText) || normalizedHint.endsWith(candidateText)) {
                    score += 100;
                }
                if (candidateText.endsWith(normalizedHint) && normalizedHint.length() >= 4) {
                    score += 55;
                }
            }

            for (String token : tokenize(normalizedHint)) {
                if (token.length() < 3) {
                    continue;
                }

                String expandedToken = expandToken(token);
                if (expandedToken.equals(candidatePath)) {
                    score += 100;
                    continue;
                }
                if (candidatePath.contains(expandedToken)) {
                    score += 45;
                    continue;
                }
                if (expandedToken.contains(candidatePath)) {
                    score += 30;
                }

                for (String candidateText : candidateTexts) {
                    if (candidateText.equals(expandedToken)) {
                        score += 90;
                        break;
                    }
                    if (candidateText.contains(expandedToken)) {
                        score += 36;
                        break;
                    }
                    if (expandedToken.contains(candidateText) && candidateText.length() >= 3) {
                        score += 24;
                        break;
                    }
                }
            }
        }
        return score;
    }

    private static int scoreCatalystCandidate(ResourceLocation candidateId, List<String> hints) {
        int score = 0;
        if (candidateId == null) {
            return Integer.MIN_VALUE;
        }

        boolean isBlock = BuiltInRegistries.BLOCK.containsKey(candidateId);
        boolean isItem = BuiltInRegistries.ITEM.containsKey(candidateId);
        if (isBlock) {
            score += 40;
        } else if (isItem) {
            score += 10;
        }

        score += scoreCandidate(candidateId, hints, isBlock);

        String normalizedPath = normalizeHintText(candidateId.getPath());
        if (containsAny(normalizedPath, NON_WORKSTATION_HINT_TOKENS)) {
            score -= 55;
        }

        if (containsAny(normalizedPath, WORKSTATION_HINT_TOKENS)) {
            score += 24;
        }

        List<String> candidateTexts = collectCandidateTexts(candidateId, isBlock);
        if (candidateTexts.stream().anyMatch(text -> containsAny(text, WORKSTATION_HINT_TOKENS))) {
            score += 18;
        }
        if (candidateTexts.stream().anyMatch(text -> containsAny(text, NON_WORKSTATION_HINT_TOKENS))) {
            score -= 32;
        }

        boolean hintWantsExtended = hints.stream()
                .map(PatternEncodingSourceHelper::normalizeHintText)
                .anyMatch(text -> containsAny(text, EXTENDED_HINT_TOKENS));
        boolean candidateIsExtended = containsAny(normalizedPath, EXTENDED_HINT_TOKENS) || candidateTexts.stream().anyMatch(text -> containsAny(text, EXTENDED_HINT_TOKENS));
        if (hintWantsExtended == candidateIsExtended) {
            score += 22;
        } else if (hintWantsExtended) {
            score -= 45;
        } else if (candidateIsExtended) {
            score -= 12;
        }

        return score;
    }

    private static List<String> tokenize(String text) {
        List<String> tokens = new ArrayList<>();
        for (String token : text.split("[^\\p{IsAlphabetic}\\p{IsDigit}\\p{IsIdeographic}]+")) {
            if (!token.isBlank()) {
                tokens.add(token);
            }
        }
        return tokens;
    }

    private static String expandToken(String token) {
        String normalized = normalizeHintText(token);
        for (var entry : WORKSTATION_PATH_HINTS.entrySet()) {
            if (normalized.contains(entry.getKey())) {
                return entry.getValue();
            }
        }
        return normalized;
    }

    private static String normalizeHintText(String text) {
        StringBuilder normalized = new StringBuilder(text.length());
        text.codePoints()
                .map(Character::toLowerCase)
                .filter(codePoint -> Character.isLetterOrDigit(codePoint) || Character.UnicodeScript.of(codePoint) == Character.UnicodeScript.HAN)
                .forEach(normalized::appendCodePoint);
        return normalized.toString();
    }

    private static List<String> collectCandidateTexts(ResourceLocation candidateId, boolean blockCandidate) {
        List<String> texts = new ArrayList<>();
        texts.add(normalizeHintText(candidateId.getPath()));
        texts.add(normalizeHintText(candidateId.toString()));

        if (blockCandidate) {
            BuiltInRegistries.BLOCK.getOptional(candidateId)
                    .ifPresent(block -> appendNormalizedCandidateText(texts, block.getName().getString()));
        }

        BuiltInRegistries.ITEM.getOptional(candidateId)
                .ifPresent(item -> appendNormalizedCandidateText(texts, item.getDefaultInstance().getHoverName().getString()));

        texts.removeIf(String::isBlank);
        return texts;
    }

    private static void appendNormalizedCandidateText(List<String> texts, @Nullable String text) {
        if (text == null || text.isBlank()) {
            return;
        }

        String normalized = normalizeHintText(text);
        if (!normalized.isBlank() && !texts.contains(normalized)) {
            texts.add(normalized);
        }
    }

    private static boolean containsAny(String text, List<String> tokens) {
        if (text == null || text.isBlank()) {
            return false;
        }

        String normalized = normalizeHintText(text);
        for (String token : tokens) {
            String normalizedToken = normalizeHintText(token);
            if (!normalizedToken.isBlank() && normalized.contains(normalizedToken)) {
                return true;
            }
        }
        return false;
    }

    private static Map<String, String> createWorkstationPathHints() {
        Map<String, String> hints = new LinkedHashMap<>();
        hints.put("metallurgic_infus", "metallurgic_infuser");
        hints.put("chemical_infus", "chemical_infuser");
        hints.put("nucleosynth", "antiprotonic_nucleosynthesizer");
        hints.put("reaction", "pressurized_reaction_chamber");
        hints.put("purif", "purification_chamber");
        hints.put("inject", "chemical_injection_chamber");
        hints.put("enrich", "enrichment_chamber");
        hints.put("compress", "osmium_compressor");
        hints.put("paint", "painting_machine");
        hints.put("combin", "combiner");
        hints.put("crush", "crusher");
        hints.put("saw", "precision_sawmill");
        hints.put("mix", "mechanical_mixer");
        hints.put("cut", "mechanical_saw");
        hints.put("press", "mechanical_press");
        hints.put("deploy", "deployer");
        hints.put("fill", "spout");
        hints.put("empty", "spout");
        hints.put("mechanical_craft", "mechanical_crafter");
        hints.put("sequenced", "mechanical_crafter");
        hints.put("mill", "millstone");
        hints.put("grind", "millstone");
        hints.put("mixer", "mechanical_mixer");
        hints.put("milling", "millstone");
        hints.put("cutting", "mechanical_saw");
        hints.put("pressing", "mechanical_press");
        hints.put("deploying", "deployer");
        hints.put("filling", "spout");
        hints.put("emptying", "spout");
        hints.put("combining", "combiner");
        hints.put("compressing", "osmium_compressor");
        hints.put("crushing", "crusher");
        hints.put("enriching", "enrichment_chamber");
        hints.put("injecting", "chemical_injection_chamber");
        hints.put("purifying", "purification_chamber");
        hints.put("painting", "painting_machine");
        hints.put("sawing", "precision_sawmill");
        hints.put("smelt", "energized_smelter");
        hints.put("smelting", "energized_smelter");
        hints.put("energized", "energized_smelter");
        hints.put("separating", "electrolytic_separator");
        hints.put("separator", "electrolytic_separator");
        hints.put("electrolytic", "electrolytic_separator");
        hints.put("washing", "chemical_washer");
        hints.put("washer", "chemical_washer");
        hints.put("activating", "solar_neutron_activator");
        hints.put("activator", "solar_neutron_activator");
        hints.put("solar", "solar_neutron_activator");
        hints.put("neutron", "solar_neutron_activator");
        hints.put("crystallizing", "chemical_crystallizer");
        hints.put("crystallizer", "chemical_crystallizer");
        hints.put("dissolving", "chemical_dissolution_chamber");
        hints.put("dissolution", "chemical_dissolution_chamber");
        hints.put("oxidizing", "chemical_oxidizer");
        hints.put("oxidizer", "chemical_oxidizer");
        hints.put("pigmentextract", "pigment_extractor");
        hints.put("pigmentmix", "pigment_mixer");
        hints.put("rotary", "rotary_condensentrator");
        hints.put("condensentrator", "rotary_condensentrator");
        hints.put("evaporating", "thermal_evaporation_controller");
        hints.put("evaporation", "thermal_evaporation_controller");
        hints.put("nucleosynthesizing", "antiprotonic_nucleosynthesizer");
        hints.put("splash", "encased_fan");
        hints.put("haunt", "encased_fan");
        hints.putAll(EXTERNAL_MAPPINGS.pathHints());
        return hints;
    }

    private static Map<String, ResourceLocation> createRecipeTypeToWorkstationMap() {
        Map<String, ResourceLocation> mappings = new LinkedHashMap<>();
        mappings.put("minecraft:crafting", CRAFTING_TABLE_ID);
        mappings.put("minecraft:smelting", FURNACE_ID);
        mappings.put("minecraft:blasting", BLAST_FURNACE_ID);
        mappings.put("minecraft:smoking", SMOKER_ID);
        mappings.put("minecraft:campfire_cooking", CAMPFIRE_ID);
        mappings.put("minecraft:stonecutting", STONECUTTER_ID);
        mappings.put("minecraft:smithing", SMITHING_TABLE_ID);
        mappings.put("ae2:inscriber", AE2_INSCRIBER_ID);
        mappings.put("ae2:charger", AE2_CHARGER_ID);
        mappings.put("data_energistics:data_reassembler", DATA_RIPPER_REASSEMBLER_ID);
        mappings.put("extendedae:crystal_assembler", EXTENDEDAE_CRYSTAL_ASSEMBLER_ID);
        mappings.put("mekanism:combining", MEKANISM_COMBINER_ID);
        mappings.put("mekanism:compressing", MEKANISM_OSMIUM_COMPRESSOR_ID);
        mappings.put("mekanism:crushing", MEKANISM_CRUSHER_ID);
        mappings.put("mekanism:enriching", MEKANISM_ENRICHMENT_CHAMBER_ID);
        mappings.put("mekanism:injecting", MEKANISM_CHEMICAL_INJECTION_CHAMBER_ID);
        mappings.put("mekanism:purifying", MEKANISM_PURIFICATION_CHAMBER_ID);
        mappings.put("mekanism:metallurgic_infusing", MEKANISM_METALLURGIC_INFUSER_ID);
        mappings.put("mekanism:painting", MEKANISM_PAINTING_MACHINE_ID);
        mappings.put("mekanism:sawing", MEKANISM_PRECISION_SAWMILL_ID);
        mappings.put("mekanism:smelting", MEKANISM_ENERGIZED_SMELTER_ID);
        mappings.put("mekanism:separating", MEKANISM_ELECTROLYTIC_SEPARATOR_ID);
        mappings.put("mekanism:washing", MEKANISM_CHEMICAL_WASHER_ID);
        mappings.put("mekanism:activating", MEKANISM_SOLAR_NEUTRON_ACTIVATOR_ID);
        mappings.put("mekanism:crystallizing", MEKANISM_CHEMICAL_CRYSTALLIZER_ID);
        mappings.put("mekanism:dissolving", MEKANISM_CHEMICAL_DISSOLUTION_CHAMBER_ID);
        mappings.put("mekanism:oxidizing", MEKANISM_CHEMICAL_OXIDIZER_ID);
        mappings.put("mekanism:pigment_extracting", MEKANISM_PIGMENT_EXTRACTOR_ID);
        mappings.put("mekanism:pigment_mixing", MEKANISM_PIGMENT_MIXER_ID);
        mappings.put("mekanism:rotary", MEKANISM_ROTARY_CONDENSENTRATOR_ID);
        mappings.put("mekanism:evaporating", MEKANISM_THERMAL_EVAPORATION_CONTROLLER_ID);
        mappings.put("mekanism:chemical_infusing", MEKANISM_CHEMICAL_INFUSER_ID);
        mappings.put("mekanism:nucleosynthesizing", MEKANISM_ANTIPROTONIC_NUCLEOSYNTHESIZER_ID);
        mappings.put("mekanism:reaction", MEKANISM_PRESSURIZED_REACTION_CHAMBER_ID);
        mappings.put("create:mixing", CREATE_MECHANICAL_MIXER_ID);
        mappings.put("create:compacting", CREATE_BASIN_ID);
        mappings.put("create:cutting", CREATE_MECHANICAL_SAW_ID);
        mappings.put("create:pressing", CREATE_MECHANICAL_PRESS_ID);
        mappings.put("create:deploying", CREATE_DEPLOYER_ID);
        mappings.put("create:filling", CREATE_SPOUT_ID);
        mappings.put("create:emptying", CREATE_SPOUT_ID);
        mappings.put("create:mechanical_crafting", CREATE_MECHANICAL_CRAFTER_ID);
        mappings.put("create:sequenced_assembly", CREATE_MECHANICAL_CRAFTER_ID);
        mappings.put("create:milling", CREATE_MILLSTONE_ID);
        mappings.put("create:crushing", CREATE_CRUSHING_WHEEL_ID);
        mappings.put("create:splashing", CREATE_ENCASED_FAN_ID);
        mappings.put("create:haunting", CREATE_ENCASED_FAN_ID);
        mappings.putAll(EXTERNAL_MAPPINGS.identifierToWorkstation());
        return mappings;
    }

    private static ExternalMappings loadExternalMappings() {
        try (InputStream stream = PatternEncodingSourceHelper.class.getClassLoader()
                .getResourceAsStream(WORKSTATION_MAPPINGS_RESOURCE)) {
            if (stream == null) {
                return ExternalMappings.EMPTY;
            }

            try (Reader reader = new InputStreamReader(stream, StandardCharsets.UTF_8)) {
                JsonElement parsed = JsonParser.parseReader(reader);
                if (!parsed.isJsonObject()) {
                    return ExternalMappings.EMPTY;
                }

                JsonObject root = parsed.getAsJsonObject();
                return new ExternalMappings(
                        parseResourceLocationMap(root.getAsJsonObject("identifier_to_workstation")),
                        parseStringMap(root.getAsJsonObject("path_hints")),
                        parseStringListMap(root.getAsJsonObject("namespace_aliases")));
            }
        } catch (IOException | RuntimeException | LinkageError exception) {
            LOGGER.error("Failed to load pattern workstation mappings from {}", WORKSTATION_MAPPINGS_RESOURCE,
                    exception);
            return ExternalMappings.EMPTY;
        }
    }

    private static Map<String, ResourceLocation> parseResourceLocationMap(@Nullable JsonObject object) {
        Map<String, ResourceLocation> mappings = new LinkedHashMap<>();
        if (object == null) {
            return mappings;
        }

        for (var entry : object.entrySet()) {
            if (!entry.getValue().isJsonPrimitive()) {
                continue;
            }

            ResourceLocation workstationId = ResourceLocation.tryParse(entry.getValue().getAsString());
            if (workstationId != null) {
                mappings.put(entry.getKey(), workstationId);
            }
        }
        return mappings;
    }

    private static Map<String, String> parseStringMap(@Nullable JsonObject object) {
        Map<String, String> mappings = new LinkedHashMap<>();
        if (object == null) {
            return mappings;
        }

        for (var entry : object.entrySet()) {
            if (!entry.getValue().isJsonPrimitive()) {
                continue;
            }
            mappings.put(entry.getKey(), entry.getValue().getAsString());
        }
        return mappings;
    }

    private static Map<String, List<String>> parseStringListMap(@Nullable JsonObject object) {
        Map<String, List<String>> mappings = new LinkedHashMap<>();
        if (object == null) {
            return mappings;
        }

        for (var entry : object.entrySet()) {
            List<String> values = new ArrayList<>();
            if (entry.getValue().isJsonPrimitive()) {
                values.add(entry.getValue().getAsString());
            } else if (entry.getValue().isJsonArray()) {
                for (JsonElement element : entry.getValue().getAsJsonArray()) {
                    if (element.isJsonPrimitive()) {
                        values.add(element.getAsString());
                    }
                }
            }

            values.removeIf(String::isBlank);
            if (!values.isEmpty()) {
                mappings.put(entry.getKey(), List.copyOf(values));
            }
        }
        return mappings;
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

    private record ExternalMappings(Map<String, ResourceLocation> identifierToWorkstation,
                                    Map<String, String> pathHints,
                                    Map<String, List<String>> namespaceAliases) {

        private static final ExternalMappings EMPTY = new ExternalMappings(Map.of(), Map.of(), Map.of());
    }
}

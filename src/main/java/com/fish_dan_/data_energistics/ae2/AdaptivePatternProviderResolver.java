package com.fish_dan_.data_energistics.ae2;

import com.fish_dan_.data_energistics.registry.ModBlocks;
import com.fish_dan_.data_energistics.registry.ModItems;
import com.fish_dan_.data_energistics.util.ReflectionAccess;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

import appeng.api.implementations.blockentities.PatternContainerGroup;
import appeng.api.parts.IPart;
import appeng.api.parts.IPartItem;
import appeng.api.stacks.AEItemKey;
import appeng.blockentity.crafting.PatternProviderBlockEntity;
import appeng.blockentity.networking.CableBusBlockEntity;
import appeng.core.definitions.AEBlocks;
import appeng.core.definitions.AEParts;
import appeng.helpers.patternprovider.PatternProviderLogicHost;
import appeng.parts.crafting.PatternProviderPart;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Set;

public final class AdaptivePatternProviderResolver {

    private static final int BASE_PATTERN_SLOTS = 9;
    private static final int SIMPLE_PATTERN_SLOTS = 5;
    private static final int EXTENDED_PATTERN_SLOTS = 36;
    private static final int METEORITE_PATTERN_SLOTS = 63;
    private static final String APPLIED_CREATE_NAMESPACE = "appliedcreate";
    private static final String EXTENDEDAE_NAMESPACE = "extendedae";
    private static final String EXTENDEDAE_PLUS_NAMESPACE = "extendedae_plus";
    private static final ResourceLocation EXTENDEDAE_ASSEMBLER_MATRIX_SPEED_ID = ResourceLocation.fromNamespaceAndPath(EXTENDEDAE_NAMESPACE, "assembler_matrix_speed");
    private static final ResourceLocation EXTENDEDAE_PLUS_ASSEMBLER_MATRIX_SPEED_ID = ResourceLocation.fromNamespaceAndPath(EXTENDEDAE_PLUS_NAMESPACE, "assembler_matrix_speed_plus");
    private static final String EXTENDEDAE_ASSEMBLER_MATRIX_NAME_KEY = "gui.extendedae.assembler_matrix";
    private static final Set<String> EXTENDEDAE_ASSEMBLER_MATRIX_COMPONENTS = Set.of(
            "assembler_matrix_wall",
            "assembler_matrix_frame",
            "assembler_matrix_glass",
            "assembler_matrix_crafter",
            "assembler_matrix_pattern",
            "assembler_matrix_speed");
    private static final Set<String> EXTENDEDAE_PLUS_ASSEMBLER_MATRIX_COMPONENTS = Set.of(
            "assembler_matrix_upload_core",
            "assembler_matrix_crafter_plus",
            "assembler_matrix_pattern_plus",
            "assembler_matrix_speed_plus");

    private AdaptivePatternProviderResolver() {}

    public static boolean isSupportedProviderStack(ItemStack stack) {
        return resolveProviderProfile(stack) != null;
    }

    @Nullable
    public static ProviderKind getResolvedProviderKind(ItemStack stack) {
        ProviderProfile profile = resolveProviderProfile(stack);
        return profile != null ? profile.kind() : null;
    }

    public static int getResolvedSlotsPerProvider(ItemStack stack) {
        ProviderProfile profile = resolveProviderProfile(stack);
        return profile != null ? profile.slotsPerProvider() : 0;
    }

    @Nullable
    public static ItemStack getResolvedProviderMainMenuIcon(ItemStack stack) {
        ProviderProfile profile = resolveProviderProfile(stack);
        return profile != null ? profile.mainMenuIcon().copy() : null;
    }

    @Nullable
    public static AEItemKey getResolvedProviderTerminalIcon(ItemStack stack) {
        ProviderProfile profile = resolveProviderProfile(stack);
        return profile != null ? profile.terminalIcon() : null;
    }

    @Nullable
    public static Component getResolvedProviderDisplayName(ItemStack stack) {
        ProviderProfile profile = resolveProviderProfile(stack);
        return profile != null ? profile.displayName() : null;
    }

    public static boolean isAdvancedAeProviderStack(ItemStack stack) {
        ProviderProfile profile = resolveProviderProfile(stack);
        return profile != null && (profile.kind() == ProviderKind.ADVANCED_SMALL || profile.kind() == ProviderKind.ADVANCED_EXTENDED);
    }

    @Nullable
    public static PatternContainerGroup resolveSpecialAdjacentMachineGroup(Level level, BlockPos pos) {
        ResourceLocation blockId = BuiltInRegistries.BLOCK.getKey(level.getBlockState(pos).getBlock());
        if (blockId == null || !isAssemblerMatrixComponent(blockId)) {
            return null;
        }

        var speedBlock = BuiltInRegistries.BLOCK.getOptional(EXTENDEDAE_ASSEMBLER_MATRIX_SPEED_ID).orElse(null);
        if (speedBlock == null) {
            speedBlock = BuiltInRegistries.BLOCK.getOptional(EXTENDEDAE_PLUS_ASSEMBLER_MATRIX_SPEED_ID).orElse(null);
        }
        if (speedBlock == null) {
            return null;
        }

        ItemStack iconStack = speedBlock.asItem().getDefaultInstance();
        if (iconStack.isEmpty()) {
            return null;
        }

        return new PatternContainerGroup(
                AEItemKey.of(iconStack),
                Component.translatable(EXTENDEDAE_ASSEMBLER_MATRIX_NAME_KEY),
                List.of());
    }

    public static boolean isPatternProviderAttachment(Level level, BlockPos pos, @Nullable Direction side) {
        if (level.getBlockEntity(pos) instanceof PatternProviderBlockEntity) {
            return true;
        }

        if (!(level.getBlockEntity(pos) instanceof CableBusBlockEntity cableBusBlockEntity)) {
            return false;
        }

        var cableBus = cableBusBlockEntity.getCableBus();
        IPart centerPart = cableBus.getPart(null);
        if (centerPart instanceof PatternProviderPart) {
            return true;
        }

        if (side == null) {
            for (Direction direction : Direction.values()) {
                if (cableBus.getPart(direction) instanceof PatternProviderPart) {
                    return true;
                }
            }
            return false;
        }

        return cableBus.getPart(side) instanceof PatternProviderPart;
    }

    public static Component decorateAdaptiveProviderName(Component providerName) {
        return decorateAdaptiveProviderName(
                "screen.data_energistics.adaptive_pattern_provider.provider_variant",
                providerName);
    }

    public static Component decorateAdaptiveProviderName(String translationKey, Component providerName) {
        return Component.translatable(translationKey, providerName);
    }

    @Nullable
    public static ProviderProfile resolveProviderProfile(ItemStack stack) {
        if (stack.isEmpty() || stack.is(ModBlocks.ADAPTIVE_PATTERN_PROVIDER.get().asItem()) || stack.is(ModItems.ADAPTIVE_PATTERN_PROVIDER_PART.get())) {
            return null;
        }

        ProviderProfile profile = resolveAe2CrystalScienceProfile(stack);
        if (profile != null) {
            return profile;
        }

        profile = resolveAdvancedAeProfile(stack);
        if (profile != null) {
            return profile;
        }

        profile = resolveAppliedCreateProfile(stack);
        if (profile != null) {
            return profile;
        }

        profile = resolvePartProviderProfile(stack);
        if (profile != null) {
            return profile;
        }

        profile = resolveBlockProviderProfile(stack);
        if (profile != null) {
            return profile;
        }

        return resolveLegacyProviderProfile(stack);
    }

    @Nullable
    private static ProviderProfile resolveAe2CrystalScienceProfile(ItemStack stack) {
        ResourceLocation itemId = BuiltInRegistries.ITEM.getKey(stack.getItem());
        if (itemId == null || !"ae2cs".equals(itemId.getNamespace())) {
            return null;
        }

        String path = itemId.getPath();
        int slotCount = switch (path) {
            case "resonating_pattern_provider", "resonating_pattern_provider_part" -> BASE_PATTERN_SLOTS;
            case "simple_pattern_provider", "simple_pattern_provider_part" -> SIMPLE_PATTERN_SLOTS;
            case "extended_resonating_pattern_provider", "extended_resonating_pattern_provider_part", "ex_resonating_pattern_provider", "ex_resonating_pattern_provider_part" -> EXTENDED_PATTERN_SLOTS;
            case "meteorite_pattern_provider", "meteorite_pattern_provider_part" -> METEORITE_PATTERN_SLOTS;
            default -> -1;
        };
        if (slotCount <= 0) {
            return null;
        }

        ItemStack icon = new ItemStack(stack.getItem());
        ProviderKind kind = switch (path) {
            case "resonating_pattern_provider", "resonating_pattern_provider_part" -> ProviderKind.RESONATING;
            case "simple_pattern_provider", "simple_pattern_provider_part" -> ProviderKind.SIMPLE;
            case "extended_resonating_pattern_provider", "extended_resonating_pattern_provider_part", "ex_resonating_pattern_provider", "ex_resonating_pattern_provider_part" -> ProviderKind.EXTENDED_RESONATING;
            case "meteorite_pattern_provider", "meteorite_pattern_provider_part" -> ProviderKind.METEORITE;
            default -> ProviderKind.UNKNOWN;
        };
        return new ProviderProfile(kind, slotCount, icon, AEItemKey.of(icon), icon.getHoverName());
    }

    @Nullable
    private static ProviderProfile resolveAdvancedAeProfile(ItemStack stack) {
        ResourceLocation itemId = BuiltInRegistries.ITEM.getKey(stack.getItem());
        if (itemId == null || !"advanced_ae".equals(itemId.getNamespace())) {
            return null;
        }

        String path = itemId.getPath();
        int slotCount = switch (path) {
            case "small_adv_pattern_provider", "small_adv_pattern_provider_part" -> BASE_PATTERN_SLOTS;
            case "adv_pattern_provider", "adv_pattern_provider_part" -> EXTENDED_PATTERN_SLOTS;
            default -> -1;
        };
        if (slotCount <= 0) {
            return null;
        }

        ItemStack icon = new ItemStack(stack.getItem());
        ProviderKind kind = switch (path) {
            case "small_adv_pattern_provider", "small_adv_pattern_provider_part" -> ProviderKind.ADVANCED_SMALL;
            case "adv_pattern_provider", "adv_pattern_provider_part" -> ProviderKind.ADVANCED_EXTENDED;
            default -> ProviderKind.UNKNOWN;
        };
        return new ProviderProfile(kind, slotCount, icon, AEItemKey.of(icon), icon.getHoverName());
    }

    @Nullable
    private static ProviderProfile resolveAppliedCreateProfile(ItemStack stack) {
        if (!AdaptivePatternProviderExternalHandlers.supportsMechanicalProviders()) {
            return null;
        }
        ResourceLocation itemId = BuiltInRegistries.ITEM.getKey(stack.getItem());
        if (itemId == null || !APPLIED_CREATE_NAMESPACE.equals(itemId.getNamespace())) {
            return null;
        }

        String path = itemId.getPath();
        int slotCount = switch (path) {
            case "andesite_pattern_provider" -> BASE_PATTERN_SLOTS;
            case "brass_pattern_provider" -> EXTENDED_PATTERN_SLOTS;
            default -> -1;
        };
        if (slotCount <= 0) {
            return null;
        }

        ItemStack icon = new ItemStack(stack.getItem());
        ProviderKind kind = switch (path) {
            case "andesite_pattern_provider" -> ProviderKind.APPLIED_CREATE_ANDESITE;
            case "brass_pattern_provider" -> ProviderKind.APPLIED_CREATE_BRASS;
            default -> ProviderKind.UNKNOWN;
        };
        return new ProviderProfile(kind, slotCount, icon, AEItemKey.of(icon), icon.getHoverName());
    }

    @Nullable
    private static ProviderProfile resolvePartProviderProfile(ItemStack stack) {
        if (!(stack.getItem() instanceof IPartItem<?> partItem)) {
            return null;
        }

        try {
            IPart part = partItem.createPart();
            if (!(part instanceof PatternProviderLogicHost host)) {
                return null;
            }

            int slotCount = host.getLogic().getPatternInv().size();
            if (slotCount <= 0) {
                return null;
            }

            ItemStack menuIcon = resolveMainMenuIcon(part, new ItemStack(stack.getItem()));
            return new ProviderProfile(resolveKindFromSlotCount(slotCount), slotCount, menuIcon, host.getTerminalIcon(), menuIcon.getHoverName());
        } catch (Exception ignored) {
            return null;
        }
    }

    @Nullable
    private static ProviderProfile resolveBlockProviderProfile(ItemStack stack) {
        if (!(stack.getItem() instanceof BlockItem blockItem) || !(blockItem.getBlock() instanceof EntityBlock entityBlock)) {
            return null;
        }

        try {
            BlockState state = blockItem.getBlock().defaultBlockState();
            BlockEntity blockEntity = entityBlock.newBlockEntity(BlockPos.ZERO, state);
            if (!(blockEntity instanceof PatternProviderLogicHost host)) {
                return null;
            }

            int slotCount = host.getLogic().getPatternInv().size();
            if (slotCount <= 0) {
                return null;
            }

            ItemStack menuIcon = resolveMainMenuIcon(blockEntity, new ItemStack(stack.getItem()));
            return new ProviderProfile(resolveKindFromSlotCount(slotCount), slotCount, menuIcon, host.getTerminalIcon(), menuIcon.getHoverName());
        } catch (Exception ignored) {
            return null;
        }
    }

    @Nullable
    private static ProviderProfile resolveLegacyProviderProfile(ItemStack stack) {
        if (AEBlocks.PATTERN_PROVIDER.is(stack) || AEParts.PATTERN_PROVIDER.is(stack)) {
            ItemStack icon = new ItemStack(stack.getItem());
            return new ProviderProfile(ProviderKind.STANDARD, BASE_PATTERN_SLOTS, icon, AEItemKey.of(icon), icon.getHoverName());
        }

        ResourceLocation itemId = BuiltInRegistries.ITEM.getKey(stack.getItem());
        if (itemId != null && "extendedae".equals(itemId.getNamespace()) && ("ex_pattern_provider".equals(itemId.getPath()) || "ex_pattern_provider_part".equals(itemId.getPath()) || "wireless_ex_pat".equals(itemId.getPath()))) {
            ItemStack icon = new ItemStack(stack.getItem());
            return new ProviderProfile(ProviderKind.EXTENDED, EXTENDED_PATTERN_SLOTS, icon, AEItemKey.of(icon), icon.getHoverName());
        }

        return null;
    }

    private static ProviderKind resolveKindFromSlotCount(int slotCount) {
        if (slotCount == SIMPLE_PATTERN_SLOTS) {
            return ProviderKind.SIMPLE;
        }
        if (slotCount == BASE_PATTERN_SLOTS) {
            return ProviderKind.STANDARD;
        }
        if (slotCount == EXTENDED_PATTERN_SLOTS) {
            return ProviderKind.EXTENDED;
        }
        if (slotCount == METEORITE_PATTERN_SLOTS) {
            return ProviderKind.METEORITE;
        }
        return ProviderKind.UNKNOWN;
    }

    private static ItemStack resolveMainMenuIcon(Object source, ItemStack fallback) {
        Object result = ReflectionAccess.invokeNoArg(source, "getMainMenuIcon");
        if (result instanceof ItemStack stack && !stack.isEmpty()) {
            return stack.copy();
        }
        return fallback.copy();
    }

    private static boolean isAssemblerMatrixComponent(ResourceLocation blockId) {
        return (EXTENDEDAE_NAMESPACE.equals(blockId.getNamespace()) && EXTENDEDAE_ASSEMBLER_MATRIX_COMPONENTS.contains(blockId.getPath())) || (EXTENDEDAE_PLUS_NAMESPACE.equals(blockId.getNamespace()) && EXTENDEDAE_PLUS_ASSEMBLER_MATRIX_COMPONENTS.contains(blockId.getPath()));
    }

    public enum ProviderKind {
        UNKNOWN,
        STANDARD,
        SIMPLE,
        EXTENDED,
        ADVANCED_SMALL,
        ADVANCED_EXTENDED,
        APPLIED_CREATE_ANDESITE,
        APPLIED_CREATE_BRASS,
        RESONATING,
        EXTENDED_RESONATING,
        METEORITE
    }

    public record ProviderProfile(ProviderKind kind, int slotsPerProvider, ItemStack mainMenuIcon, AEItemKey terminalIcon,
                                  Component displayName) {}
}

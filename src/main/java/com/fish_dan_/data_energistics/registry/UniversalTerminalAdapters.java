package com.fish_dan_.data_energistics.registry;

import com.fish_dan_.data_energistics.menu.universal.UniversalTerminalMenuLocator;
import com.fish_dan_.data_energistics.util.UniversalTerminalConfigProfile;
import com.fish_dan_.data_energistics.util.UniversalTerminalData;
import com.fish_dan_.data_energistics.util.UniversalTerminalDefinition;
import com.fish_dan_.data_energistics.util.ReflectionAccess;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

import appeng.api.config.Setting;
import appeng.api.config.Settings;
import appeng.api.config.ShowPatternProviders;
import appeng.api.parts.IPartItem;
import appeng.api.storage.IPatternAccessTermMenuHost;
import appeng.api.storage.ITerminalHost;
import appeng.api.util.IConfigManager;
import appeng.core.definitions.AEParts;
import appeng.helpers.IPatternTerminalMenuHost;
import appeng.parts.encoding.PatternEncodingTerminalPart;
import appeng.parts.reporting.AbstractTerminalPart;
import appeng.parts.reporting.CraftingTerminalPart;
import appeng.parts.reporting.PatternAccessTerminalPart;
import com.mojang.logging.LogUtils;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

public final class UniversalTerminalAdapters {

    private static final Logger LOGGER = LogUtils.getLogger();
    private static boolean initialized;
    private static boolean discovered;

    private UniversalTerminalAdapters() {}

    public static void init() {
        if (initialized) {
            return;
        }
        initialized = true;
        UniversalTerminalMenuLocator.init();

        UniversalTerminalData.registerAdapter(new UniversalTerminalDefinition(
                UniversalTerminalData.TERMINAL_ITEM,
                AEParts.TERMINAL::is,
                () -> new ItemStack(AEParts.TERMINAL.asItem()),
                ModMenus.UNIVERSAL_ME_STORAGE::get));
        UniversalTerminalData.registerAdapter(new UniversalTerminalDefinition(
                UniversalTerminalData.TERMINAL_CRAFTING,
                AEParts.CRAFTING_TERMINAL::is,
                () -> new ItemStack(AEParts.CRAFTING_TERMINAL.asItem()),
                ModMenus.UNIVERSAL_CRAFTING_TERM::get));
        UniversalTerminalData.registerAdapter(new UniversalTerminalDefinition(
                UniversalTerminalData.TERMINAL_PATTERN_ACCESS,
                AEParts.PATTERN_ACCESS_TERMINAL::is,
                () -> new ItemStack(AEParts.PATTERN_ACCESS_TERMINAL.asItem()),
                ModMenus.UNIVERSAL_PATTERN_ACCESS_TERM::get,
                UniversalTerminalConfigProfile.PATTERN_ACCESS,
                false,
                UniversalTerminalAdapters::createPatternAccessConfigManager));
        UniversalTerminalData.registerAdapter(new UniversalTerminalDefinition(
                UniversalTerminalData.TERMINAL_PATTERN_ENCODING,
                AEParts.PATTERN_ENCODING_TERMINAL::is,
                () -> new ItemStack(AEParts.PATTERN_ENCODING_TERMINAL.asItem()),
                ModMenus.UNIVERSAL_PATTERN_ENCODING_TERM::get));
    }

    public static void discoverFromRegisteredItems() {
        if (discovered) {
            return;
        }
        discovered = true;
        Map<String, Item> partItemsByClassName = collectPartItemsByClassName();
        registerOptionalReflectiveAdapters(partItemsByClassName);

        for (Item item : BuiltInRegistries.ITEM) {
            if (!(item instanceof IPartItem<?> partItem)) {
                continue;
            }

            ItemStack stack = new ItemStack(item);
            if (stack.isEmpty() || UniversalTerminalData.isUniversalTerminal(stack) || UniversalTerminalData.isSupportedTerminal(stack)) {
                continue;
            }

            DetectedTerminalProfile profile = detectProfile(partItem.getPartClass());
            if (profile == null) {
                continue;
            }

            ResourceLocation itemId = BuiltInRegistries.ITEM.getKey(item);
            if (itemId == null || itemId.equals(BuiltInRegistries.ITEM.getDefaultKey())) {
                continue;
            }

            var externalMenuTypeSupplier = findExternalMenuTypeSupplier(itemId, partItem.getPartClass());
            if (externalMenuTypeSupplier != null) {
                UniversalTerminalData.registerAdapter(new UniversalTerminalDefinition(
                        itemId.toString(),
                        candidate -> candidate.is(item),
                        () -> stack.copy(),
                        externalMenuTypeSupplier,
                        profile.configProfile(),
                        true,
                        profile.configManagerFactory()));
                LOGGER.info("Auto-registered external universal terminal adapter for {} using detected menu type", itemId);
                continue;
            }

            UniversalTerminalData.registerAdapter(new UniversalTerminalDefinition(
                    itemId.toString(),
                    candidate -> candidate.is(item),
                    () -> stack.copy(),
                    profile.menuTypeSupplier(),
                    profile.configProfile(),
                    false,
                    profile.configManagerFactory()));
            LOGGER.info("Auto-registered wrapped universal terminal adapter for {}", itemId);
        }
    }

    private static Map<String, Item> collectPartItemsByClassName() {
        Map<String, Item> partItemsByClassName = new HashMap<>();
        for (Item item : BuiltInRegistries.ITEM) {
            if (item instanceof IPartItem<?> partItem) {
                partItemsByClassName.putIfAbsent(partItem.getPartClass().getName(), item);
            }
        }
        return partItemsByClassName;
    }

    private static DetectedTerminalProfile detectProfile(Class<?> partClass) {
        if (supportsPatternAccess(partClass)) {
            return new DetectedTerminalProfile(
                    ModMenus.UNIVERSAL_PATTERN_ACCESS_TERM::get,
                    UniversalTerminalConfigProfile.PATTERN_ACCESS,
                    UniversalTerminalAdapters::createPatternAccessConfigManager);
        }
        if (supportsPatternEncoding(partClass)) {
            return new DetectedTerminalProfile(
                    ModMenus.UNIVERSAL_PATTERN_ENCODING_TERM::get,
                    UniversalTerminalConfigProfile.STANDARD,
                    null);
        }
        if (supportsCrafting(partClass)) {
            return new DetectedTerminalProfile(
                    ModMenus.UNIVERSAL_CRAFTING_TERM::get,
                    UniversalTerminalConfigProfile.STANDARD,
                    null);
        }
        if (supportsStorage(partClass)) {
            return new DetectedTerminalProfile(
                    ModMenus.UNIVERSAL_ME_STORAGE::get,
                    UniversalTerminalConfigProfile.STANDARD,
                    null);
        }
        return null;
    }

    private static boolean supportsPatternAccess(Class<?> partClass) {
        return PatternAccessTerminalPart.class.isAssignableFrom(partClass) || IPatternAccessTermMenuHost.class.isAssignableFrom(partClass);
    }

    private static boolean supportsPatternEncoding(Class<?> partClass) {
        return PatternEncodingTerminalPart.class.isAssignableFrom(partClass) || IPatternTerminalMenuHost.class.isAssignableFrom(partClass);
    }

    private static boolean supportsCrafting(Class<?> partClass) {
        return CraftingTerminalPart.class.isAssignableFrom(partClass);
    }

    private static boolean supportsStorage(Class<?> partClass) {
        return AbstractTerminalPart.class.isAssignableFrom(partClass) || ITerminalHost.class.isAssignableFrom(partClass);
    }

    private static @Nullable java.util.function.Supplier<MenuType<?>> findExternalMenuTypeSupplier(
                                                                                                   ResourceLocation itemId, Class<?> partClass) {
        String itemPath = itemId.getPath();
        String partSimpleName = partClass.getSimpleName();
        if (!looksLikeTerminal(itemPath, partSimpleName)) {
            return null;
        }

        ResourceLocation bestMenuId = null;
        int bestScore = Integer.MIN_VALUE;
        for (MenuType<?> menuType : BuiltInRegistries.MENU) {
            ResourceLocation menuId = BuiltInRegistries.MENU.getKey(menuType);
            if (menuId == null || !itemId.getNamespace().equals(menuId.getNamespace())) {
                continue;
            }

            int score = scoreMenuCandidate(itemPath, partSimpleName, menuId.getPath());
            if (score > bestScore) {
                bestScore = score;
                bestMenuId = menuId;
            }
        }

        if (bestMenuId == null || bestScore < 80) {
            return null;
        }

        ResourceLocation resolvedMenuId = bestMenuId;
        return () -> BuiltInRegistries.MENU.get(resolvedMenuId);
    }

    private static boolean looksLikeTerminal(String itemPath, String partSimpleName) {
        String lowerPath = itemPath.toLowerCase(Locale.ROOT);
        String lowerName = partSimpleName.toLowerCase(Locale.ROOT);
        return lowerPath.contains("terminal") || lowerName.contains("terminal") || lowerPath.contains("requester") || lowerName.contains("requester");
    }

    private static int scoreMenuCandidate(String itemPath, String partSimpleName, String menuPath) {
        String lowerItemPath = itemPath.toLowerCase(Locale.ROOT);
        String lowerPartName = partSimpleName.toLowerCase(Locale.ROOT);
        String lowerMenuPath = menuPath.toLowerCase(Locale.ROOT);
        String normalizedItemPath = normalizeTerminalKey(lowerItemPath);
        String normalizedPartName = normalizeTerminalKey(lowerPartName);
        String normalizedMenuPath = normalizeTerminalKey(lowerMenuPath);

        int score = Integer.MIN_VALUE;
        if (lowerMenuPath.equals(lowerItemPath)) {
            score = Math.max(score, 200);
        }
        if (normalizedMenuPath.equals(normalizedItemPath)) {
            score = Math.max(score, 180);
        }
        if (normalizedMenuPath.equals(normalizedPartName)) {
            score = Math.max(score, 170);
        }
        if (lowerMenuPath.contains(normalizedItemPath) && lowerMenuPath.contains("terminal")) {
            score = Math.max(score, 150);
        }
        if (lowerMenuPath.contains(normalizedPartName) && lowerMenuPath.contains("terminal")) {
            score = Math.max(score, 140);
        }
        if (sharesTerminalTokens(normalizedItemPath, normalizedMenuPath)) {
            score = Math.max(score, 120);
        }
        if (lowerMenuPath.contains("terminal") && lowerItemPath.contains("terminal")) {
            score = Math.max(score, 90);
        }
        if (lowerMenuPath.contains("requester") && lowerItemPath.contains("requester")) {
            score = Math.max(score, 90);
        }
        if (lowerMenuPath.contains("wireless") != lowerItemPath.contains("wireless")) {
            score -= 30;
        }
        return score;
    }

    private static boolean sharesTerminalTokens(String left, String right) {
        int matches = 0;
        for (String token : new String[] { "pattern", "access", "encoding", "craft", "crafting", "request", "requester",
                "quantum", "storage", "terminal", "wireless" }) {
            if (left.contains(token) && right.contains(token)) {
                matches++;
            }
        }
        return matches >= 2;
    }

    private static String normalizeTerminalKey(String value) {
        return value
                .replace("_part", "")
                .replace("_menu", "")
                .replace("_screen", "")
                .replace("_container", "")
                .replace("_term", "_terminal")
                .replace("part", "")
                .replace("menu", "")
                .replace("screen", "")
                .replace("container", "");
    }

    private static void registerOptionalReflectiveAdapters(Map<String, Item> partItemsByClassName) {
        registerReflectiveAdapter(
                "com.glodblock.github.extendedae.common.parts.PartExPatternAccessTerminal",
                "com.glodblock.github.extendedae.container.ContainerExPatternTerminal",
                "TYPE",
                UniversalTerminalConfigProfile.PATTERN_ACCESS,
                true,
                UniversalTerminalAdapters::createPatternAccessConfigManager,
                partItemsByClassName);
        registerReflectiveAdapter(
                "com.almostreliable.merequester.terminal.RequesterTerminalPart",
                "com.almostreliable.merequester.core.Registration",
                "REQUESTER_TERMINAL_MENU",
                UniversalTerminalConfigProfile.STANDARD,
                true,
                null,
                partItemsByClassName);
        registerReflectiveAdapter(
                "net.pedroksl.advanced_ae.common.parts.QuantumCrafterTerminalPart",
                "net.pedroksl.advanced_ae.common.definitions.AAEMenus",
                "QUANTUM_CRAFTER_TERMINAL",
                UniversalTerminalConfigProfile.STANDARD,
                true,
                UniversalTerminalAdapters::createQuantumCrafterConfigManager,
                partItemsByClassName);
    }

    private static void registerReflectiveAdapter(String partClassName,
                                                  String menuOwnerClassName,
                                                  String menuFieldName,
                                                  UniversalTerminalConfigProfile configProfile,
                                                  boolean requiresCustomMenuLocator,
                                                  @Nullable java.util.function.Function<Runnable, IConfigManager> configManagerFactory,
                                                  Map<String, Item> partItemsByClassName) {
        var menuTypeSupplier = resolveMenuTypeSupplier(menuOwnerClassName, menuFieldName);
        if (menuTypeSupplier == null) {
            LOGGER.warn("Skipping universal terminal compat for {} because {}#{} could not be resolved",
                    partClassName, menuOwnerClassName, menuFieldName);
            return;
        }

        registerReflectiveAdapter(partClassName, menuTypeSupplier, configProfile, requiresCustomMenuLocator,
                configManagerFactory, partItemsByClassName);
    }

    private static void registerReflectiveAdapter(String partClassName,
                                                  java.util.function.Supplier<net.minecraft.world.inventory.MenuType<?>> menuTypeSupplier,
                                                  UniversalTerminalConfigProfile configProfile,
                                                  boolean requiresCustomMenuLocator,
                                                  @Nullable java.util.function.Function<Runnable, IConfigManager> configManagerFactory,
                                                  Map<String, Item> partItemsByClassName) {
        Item terminalItem = partItemsByClassName.get(partClassName);
        if (terminalItem == null) {
            return;
        }

        ItemStack stack = new ItemStack(terminalItem);
        if (stack.isEmpty() || UniversalTerminalData.isSupportedTerminal(stack)) {
            return;
        }

        ResourceLocation itemId = BuiltInRegistries.ITEM.getKey(terminalItem);
        if (itemId == null || itemId.equals(BuiltInRegistries.ITEM.getDefaultKey())) {
            return;
        }

        UniversalTerminalData.registerAdapter(new UniversalTerminalDefinition(
                itemId.toString(),
                candidate -> candidate.is(terminalItem),
                () -> stack.copy(),
                menuTypeSupplier::get,
                configProfile,
                requiresCustomMenuLocator,
                configManagerFactory));
        LOGGER.info("Registered reflected universal terminal adapter for {} using wrapped menu type", itemId);
    }

    private static @Nullable java.util.function.Supplier<net.minecraft.world.inventory.MenuType<?>> resolveMenuTypeSupplier(
                                                                                                                            String ownerClassName, String fieldName) {
        try {
            Object value = ReflectionAccess.getField(ReflectionAccess.findStaticField(ownerClassName, fieldName), null);
            if (value instanceof java.util.function.Supplier<?> supplier) {
                return () -> (net.minecraft.world.inventory.MenuType<?>) supplier.get();
            }
            if (value instanceof net.minecraft.world.inventory.MenuType<?> menuType) {
                return () -> menuType;
            }
        } catch (LinkageError e) {
            LOGGER.debug("Could not resolve reflected menu supplier {}#{}", ownerClassName, fieldName, e);
        }
        return null;
    }

    private static IConfigManager createPatternAccessConfigManager(Runnable saveAction) {
        return IConfigManager.builder(saveAction)
                .registerSetting(Settings.TERMINAL_SHOW_PATTERN_PROVIDERS, ShowPatternProviders.VISIBLE)
                .build();
    }

    @SuppressWarnings({ "rawtypes", "unchecked" })
    private static @Nullable IConfigManager createQuantumCrafterConfigManager(Runnable saveAction) {
        try {
            Class<?> settingsClass = Class.forName("net.pedroksl.advanced_ae.api.AAESettings");
            Class<? extends Enum> visibleClass = (Class<? extends Enum>) Class.forName("net.pedroksl.advanced_ae.api.ShowQuantumCrafters");
            Object setting = ReflectionAccess.getField(ReflectionAccess.findStaticField(settingsClass, "TERMINAL_SHOW_QUANTUM_CRAFTERS"), null);
            Enum<?> visible = Enum.valueOf(visibleClass, "VISIBLE");
            var builder = IConfigManager.builder(saveAction);
            ((appeng.api.util.IConfigManagerBuilder) builder).registerSetting((Setting) setting, (Enum) visible);
            return builder.build();
        } catch (ReflectiveOperationException e) {
            LOGGER.warn("Could not create AdvancedAE universal terminal config manager", e);
            return null;
        }
    }

    private record DetectedTerminalProfile(
                                           java.util.function.Supplier<net.minecraft.world.inventory.MenuType<?>> menuTypeSupplier,
                                           UniversalTerminalConfigProfile configProfile,
                                           @Nullable java.util.function.Function<Runnable, IConfigManager> configManagerFactory) {}
}

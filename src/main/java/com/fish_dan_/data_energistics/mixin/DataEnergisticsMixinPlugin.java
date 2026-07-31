package com.fish_dan_.data_energistics.mixin;

import com.fish_dan_.data_energistics.Data_Energistics;

import net.neoforged.fml.ModList;
import net.neoforged.fml.loading.FMLLoader;
import net.neoforged.fml.loading.LoadingModList;
import net.neoforged.neoforge.data.loading.DatagenModLoader;

import org.objectweb.asm.tree.ClassNode;
import org.spongepowered.asm.mixin.extensibility.IMixinConfigPlugin;
import org.spongepowered.asm.mixin.extensibility.IMixinInfo;

import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static com.fish_dan_.data_energistics.mixin.Ae2LtCompatibilityPolicy.Feature.AE2LT_CHANNEL_HELPER;
import static com.fish_dan_.data_energistics.mixin.Ae2LtCompatibilityPolicy.Feature.AE2LT_EJECT_INTERCEPTOR;
import static com.fish_dan_.data_energistics.mixin.Ae2LtCompatibilityPolicy.Feature.AE2LT_EJECT_REGISTRY;
import static com.fish_dan_.data_energistics.mixin.Ae2LtCompatibilityPolicy.Feature.AE2LT_MAX_FLOW;
import static com.fish_dan_.data_energistics.mixin.Ae2LtCompatibilityPolicy.Feature.THUNDERBOLT_CHANNEL_HELPER;
import static com.fish_dan_.data_energistics.mixin.Ae2LtCompatibilityPolicy.Feature.THUNDERBOLT_EJECT_INTERCEPTOR;
import static com.fish_dan_.data_energistics.mixin.Ae2LtCompatibilityPolicy.Feature.THUNDERBOLT_MAX_FLOW;
import static com.fish_dan_.data_energistics.mixin.Ae2LtCompatibilityPolicy.Feature.THUNDERBOLT_WIRELESS_CONNECTION_API;
import static com.fish_dan_.data_energistics.mixin.Ae2LtCompatibilityPolicy.Feature.WIRELESS_HOST_API;

public final class DataEnergisticsMixinPlugin implements IMixinConfigPlugin {

    private static final String MIXIN_PACKAGE = "com.fish_dan_.data_energistics.mixin.";
    private static final Map<String, String> MOD_COMPAT_MIXINS = new HashMap<>();
    private static final Map<Ae2LtCompatibilityPolicy.Feature, String> AE2LT_FEATURE_RESOURCES = Map.of(
            WIRELESS_HOST_API, "com/moakiee/ae2lt/api/patternprovider/WirelessPatternProviderHost.class",
            THUNDERBOLT_WIRELESS_CONNECTION_API,
            "com/moakiee/thunderbolt/api/wireless/WirelessConnectionRef.class",
            AE2LT_EJECT_REGISTRY, "com/moakiee/ae2lt/logic/EjectModeRegistry.class",
            AE2LT_EJECT_INTERCEPTOR, "com/moakiee/ae2lt/mixin/EjectCapabilityMixin.class",
            THUNDERBOLT_EJECT_INTERCEPTOR,
            "com/moakiee/thunderbolt/ae2/mixin/EjectCapabilityMixin.class",
            AE2LT_MAX_FLOW, "com/moakiee/ae2lt/grid/BorrowedCapacityCalculator.class",
            THUNDERBOLT_MAX_FLOW,
            "com/moakiee/thunderbolt/ae2/channel/BorrowedCapacityCalculator.class",
            AE2LT_CHANNEL_HELPER, "com/moakiee/ae2lt/grid/OverloadedChannelOwnerHelper.class",
            THUNDERBOLT_CHANNEL_HELPER,
            "com/moakiee/thunderbolt/ae2/channel/OverloadedChannelOwnerHelper.class");

    static {
        addModCompatMixin("ae2lt", "ae2lt.");
        addModCompatMixin("advancedae", "advancedae.");
        addModCompatMixin("ae2cs", "ae2cs.");
        addModCompatMixin("appliedcreate", "appliedcreate.");
        addModCompatMixin("extendedae", "extendedae.");
        addModCompatMixin("ae2jeiintegration", "jei.");
        addModCompatMixin("emi", "emi.");
        addModCompatMixin("guideme", "guideme.");
        addModCompatMixin("neoecoae", "neoecoae.");
        addModCompatMixin("useless_mod", "useless.");
    }

    private static void addModCompatMixin(String modId, String packageName) {
        MOD_COMPAT_MIXINS.put(modId, packageName);
    }

    private static boolean isModLoaded(String modId) {
        if (ModList.get() == null) {
            return LoadingModList.get().getModFileById(modId) != null;
        }
        return ModList.get().isLoaded(modId);
    }

    @Override
    public void onLoad(String mixinPackage) {}

    @Override
    public String getRefMapperConfig() {
        return null;
    }

    @Override
    public boolean shouldApplyMixin(String targetClassName, String mixinClassName) {
        if (!mixinClassName.startsWith(MIXIN_PACKAGE)) {
            return true;
        }
        mixinClassName = mixinClassName.substring(MIXIN_PACKAGE.length());

        if (mixinClassName.startsWith("dev.")) {
            if (FMLLoader.isProduction()) {
                return false;
            }
            mixinClassName = mixinClassName.substring("dev.".length());
            if (mixinClassName.startsWith("datagen.")) {
                return DatagenModLoader.isRunningDataGen();
            }
            return true;
        }

        for (var compatMod : MOD_COMPAT_MIXINS.entrySet()) {
            if (mixinClassName.toLowerCase().startsWith(compatMod.getValue())) {
                if (!isModLoaded(compatMod.getKey())) {
                    return false;
                }
                if ("ae2lt".equals(compatMod.getKey())) {
                    return Ae2LtPolicyHolder.POLICY.shouldApply(ae2LtRole(mixinClassName));
                }
                return true;
            }
        }

        return true;
    }

    @Override
    public void acceptTargets(Set<String> myTargets, Set<String> otherTargets) {}

    @Override
    public List<String> getMixins() {
        return null;
    }

    @Override
    public void preApply(String targetClassName, ClassNode targetClass, String mixinClassName, IMixinInfo mixinInfo) {
        Ae2LtSoftInterfaceInjector.apply(mixinClassName, targetClass);
    }

    @Override
    public void postApply(String targetClassName, ClassNode targetClass, String mixinClassName, IMixinInfo mixinInfo) {}

    /**
     * Maps a configured AE2LT mixin to its version-sensitive role.
     */
    private static Ae2LtCompatibilityPolicy.MixinRole ae2LtRole(String mixinClassName) {
        return switch (mixinClassName) {
            case "ae2lt.Ae2ltWirelessConnectorUsePacketMixin", "ae2lt.Ae2ltWirelessConnectorRendererMixin" -> Ae2LtCompatibilityPolicy.MixinRole.LEGACY_WIRELESS;
            case "ae2lt.Ae2lt2AdaptivePatternProviderHostMixin", "ae2lt.Ae2lt2AdaptiveWirelessConnectionMixin" -> Ae2LtCompatibilityPolicy.MixinRole.MODERN_WIRELESS_ADAPTER;
            case "ae2lt.Ae2ltEjectCapabilityMixin" -> Ae2LtCompatibilityPolicy.MixinRole.DATA_EJECT_INTERCEPTOR;
            case "ae2lt.Ae2ltPathingCalculationCompatMixin" -> Ae2LtCompatibilityPolicy.MixinRole.LEGACY_MAX_FLOW_GUARD;
            case "ae2lt.ThunderboltPathingCalculationCompatMixin" -> Ae2LtCompatibilityPolicy.MixinRole.MODERN_MAX_FLOW_GUARD;
            case "ae2lt.Ae2ltOverloadedControllerChannelSourceMixin" -> Ae2LtCompatibilityPolicy.MixinRole.LEGACY_CHANNEL_SOURCE;
            case "ae2lt.Ae2lt2OverloadedControllerChannelSourceMixin", "ae2lt.ThunderboltOverloadedChannelOwnerHelperInvoker" -> Ae2LtCompatibilityPolicy.MixinRole.MODERN_CHANNEL_SOURCE;
            default -> Ae2LtCompatibilityPolicy.MixinRole.GENERAL;
        };
    }

    /**
     * Detects optional class files once without initializing classes from either optional mod.
     */
    private static Ae2LtCompatibilityPolicy detectAe2LtPolicy() {
        EnumSet<Ae2LtCompatibilityPolicy.Feature> features = EnumSet.noneOf(
                Ae2LtCompatibilityPolicy.Feature.class);
        ClassLoader classLoader = DataEnergisticsMixinPlugin.class.getClassLoader();
        for (var entry : AE2LT_FEATURE_RESOURCES.entrySet()) {
            if (classLoader.getResource(entry.getValue()) != null) {
                features.add(entry.getKey());
            }
        }

        Ae2LtCompatibilityPolicy policy = new Ae2LtCompatibilityPolicy(features);
        Data_Energistics.LOGGER.info(
                "Detected AE2LT compatibility features {}; EJECT interceptor owner is {}.",
                features,
                policy.ejectOwner());
        if (features.contains(WIRELESS_HOST_API) && !features.contains(THUNDERBOLT_WIRELESS_CONNECTION_API)) {
            Data_Energistics.LOGGER.error(
                    "AE2LT exposes WirelessPatternProviderHost but Thunderbolt WirelessConnectionRef is missing; " + "the AE2LT 2.0 adaptive-provider adapter will remain disabled.");
        }
        if (features.contains(AE2LT_EJECT_INTERCEPTOR) && features.contains(THUNDERBOLT_EJECT_INTERCEPTOR)) {
            Data_Energistics.LOGGER.warn(
                    "Both AE2LT and Thunderbolt provide EJECT capability interceptors; Data Energistics disabled its " + "fallback, but the external runtime still contains duplicate owners.");
        }
        return policy;
    }

    /**
     * Lazy holder prevents resource probing before the mixin plugin receives its first compatibility decision.
     */
    private static final class Ae2LtPolicyHolder {

        private static final Ae2LtCompatibilityPolicy POLICY = detectAe2LtPolicy();

        private Ae2LtPolicyHolder() {}
    }
}

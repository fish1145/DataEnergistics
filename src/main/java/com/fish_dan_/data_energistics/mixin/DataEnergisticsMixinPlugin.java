package com.fish_dan_.data_energistics.mixin;

import net.neoforged.fml.ModList;

import org.objectweb.asm.tree.ClassNode;
import org.spongepowered.asm.mixin.extensibility.IMixinConfigPlugin;
import org.spongepowered.asm.mixin.extensibility.IMixinInfo;

import java.util.List;
import java.util.Set;

public final class DataEnergisticsMixinPlugin implements IMixinConfigPlugin {

    private static final String AE2LT_SENTINEL_CLASS = "com/moakiee/ae2lt/logic/EjectModeRegistry.class";
    private static final String AE2LT_MIXIN_PREFIX = "com.fish_dan_.data_energistics.mixin.Ae2lt";
    private static final String ADVANCED_AE_SENTINEL_CLASS = "net/pedroksl/advanced_ae/common/logic/AdvPatternProviderLogic.class";
    private static final String ADVANCED_AE_MIXIN_PREFIX = "com.fish_dan_.data_energistics.mixin.AdvancedAe";
    private static final String AE2CS_SENTINEL_CLASS = "io/github/lounode/ae2cs/common/me/logic/ResonatingPatternProviderLogic.class";
    private static final String AE2CS_MIXIN_PREFIX = "com.fish_dan_.data_energistics.mixin.Ae2Cs";
    private static final String APPLIED_CREATE_SENTINEL_CLASS = "com/loliball/appliedcreate/patternprovider/MechanicalCraftingPatternLogic.class";
    private static final String APPLIED_CREATE_MIXIN_PREFIX = "com.fish_dan_.data_energistics.mixin.AppliedCreate";
    private static final String EXTENDEDAE_SENTINEL_CLASS = "com/glodblock/github/extendedae/common/me/InscriberThread.class";
    private static final String EXTENDEDAE_MIXIN_PREFIX = "com.fish_dan_.data_energistics.mixin.ExtendedAe";
    private static final String JEI_TRANSFER_SENTINEL_CLASS = "tamaized/ae2jeiintegration/integration/modules/jei/transfer/EncodePatternTransferHandler.class";
    private static final String EMI_API_SENTINEL_CLASS = "dev/emi/emi/api/EmiPlugin.class";
    private static final String EMI_HANDLER_SENTINEL_CLASS = "appeng/integration/modules/emi/EmiEncodePatternHandler.class";
    private static final String NEOECOAE_MOD_ID = "neoecoae";
    private static final boolean AE2LT_PRESENT = isClassPresent(AE2LT_SENTINEL_CLASS);
    private static final boolean ADVANCED_AE_PRESENT = isClassPresent(ADVANCED_AE_SENTINEL_CLASS);
    private static final boolean AE2CS_PRESENT = isClassPresent(AE2CS_SENTINEL_CLASS);
    private static final boolean APPLIED_CREATE_PRESENT = isClassPresent(APPLIED_CREATE_SENTINEL_CLASS);
    private static final boolean EXTENDEDAE_PRESENT = isClassPresent(EXTENDEDAE_SENTINEL_CLASS);
    private static final boolean JEI_TRANSFER_PRESENT = isClassPresent(JEI_TRANSFER_SENTINEL_CLASS);
    private static final boolean EMI_PRESENT = isClassPresent(EMI_API_SENTINEL_CLASS) && isClassPresent(EMI_HANDLER_SENTINEL_CLASS);

    @Override
    public void onLoad(String mixinPackage) {}

    @Override
    public String getRefMapperConfig() {
        return null;
    }

    @Override
    public boolean shouldApplyMixin(String targetClassName, String mixinClassName) {
        if (mixinClassName.startsWith(AE2LT_MIXIN_PREFIX)) {
            return AE2LT_PRESENT;
        }
        if (mixinClassName.startsWith(ADVANCED_AE_MIXIN_PREFIX)) {
            return ADVANCED_AE_PRESENT;
        }
        if (mixinClassName.startsWith(AE2CS_MIXIN_PREFIX)) {
            return AE2CS_PRESENT;
        }
        if (mixinClassName.startsWith(APPLIED_CREATE_MIXIN_PREFIX)) {
            return APPLIED_CREATE_PRESENT;
        }
        if (mixinClassName.startsWith(EXTENDEDAE_MIXIN_PREFIX)) {
            return EXTENDEDAE_PRESENT;
        }
        if ("com.fish_dan_.data_energistics.mixin.ExtendedInscriberThreadMixin".equals(mixinClassName)) {
            return EXTENDEDAE_PRESENT;
        }
        if ("com.fish_dan_.data_energistics.mixin.JeiEncodePatternTransferHandlerMixin".equals(mixinClassName)) {
            return JEI_TRANSFER_PRESENT;
        }
        if ("com.fish_dan_.data_energistics.mixin.EmiEncodePatternHandlerMixin".equals(mixinClassName)) {
            return EMI_PRESENT;
        }
        if ("com.fish_dan_.data_energistics.mixin.NeoECOAEClientMixin".equals(mixinClassName)) {
            return isModLoaded(NEOECOAE_MOD_ID);
        }
        return true;
    }

    private static boolean isClassPresent(String classResourcePath) {
        return DataEnergisticsMixinPlugin.class.getClassLoader().getResource(classResourcePath) != null;
    }

    private static boolean isModLoaded(String modId) {
        ModList modList = ModList.get();
        return modList != null && modList.isLoaded(modId);
    }

    @Override
    public void acceptTargets(Set<String> myTargets, Set<String> otherTargets) {}

    @Override
    public List<String> getMixins() {
        return null;
    }

    @Override
    public void preApply(String targetClassName, ClassNode targetClass, String mixinClassName, IMixinInfo mixinInfo) {}

    @Override
    public void postApply(String targetClassName, ClassNode targetClass, String mixinClassName, IMixinInfo mixinInfo) {}
}

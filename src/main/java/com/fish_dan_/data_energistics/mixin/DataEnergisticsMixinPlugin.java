package com.fish_dan_.data_energistics.mixin;

import net.neoforged.fml.ModList;
import net.neoforged.fml.loading.FMLLoader;
import net.neoforged.fml.loading.LoadingModList;
import net.neoforged.neoforge.data.loading.DatagenModLoader;

import org.objectweb.asm.tree.ClassNode;
import org.spongepowered.asm.mixin.extensibility.IMixinConfigPlugin;
import org.spongepowered.asm.mixin.extensibility.IMixinInfo;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class DataEnergisticsMixinPlugin implements IMixinConfigPlugin {

    private static final String MIXIN_PACKAGE = "com.fish_dan_.data_energistics.mixin.";
    private static final Map<String, String> MOD_COMPAT_MIXINS = new HashMap<>();

    static {
        addModCompatMixin("advancedae", "advancedae.");
        addModCompatMixin("ae2cs", "ae2cs.");
        addModCompatMixin("appliedcreate", "appliedcreate.");
        addModCompatMixin("extendedae", "extendedae.");
        addModCompatMixin("extendedae_plus", "extendedaeplus.");
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
                return isModLoaded(compatMod.getKey());
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
    public void preApply(String targetClassName, ClassNode targetClass, String mixinClassName, IMixinInfo mixinInfo) {}

    @Override
    public void postApply(String targetClassName, ClassNode targetClass, String mixinClassName, IMixinInfo mixinInfo) {}
}

package com.fish_dan_.data_energistics.util;

import com.fish_dan_.data_energistics.mixin.core.AEBaseBlockEntityNameAccessor;
import com.fish_dan_.data_energistics.mixin.core.AEBasePartNameAccessor;

import net.minecraft.network.chat.Component;

import appeng.blockentity.AEBaseBlockEntity;
import appeng.parts.AEBasePart;
import org.jetbrains.annotations.Nullable;

public final class PatternProviderNameHelper {

    private PatternProviderNameHelper() {}

    public static boolean setCustomName(Object target, @Nullable Component customName) {
        if (target instanceof AEBasePart part) {
            ((AEBasePartNameAccessor) part).dataEnergistics$setCustomName(customName);
            return true;
        }
        if (target instanceof AEBaseBlockEntity blockEntity) {
            ((AEBaseBlockEntityNameAccessor) blockEntity).dataEnergistics$setCustomName(customName);
            return true;
        }
        return false;
    }

    @Nullable
    public static Component getCustomName(Object target) {
        if (target instanceof AEBasePart part) {
            return part.getCustomName();
        }
        if (target instanceof AEBaseBlockEntity blockEntity) {
            return blockEntity.getCustomName();
        }
        return null;
    }

    public static boolean canRename(Object target) {
        return target instanceof AEBasePart || target instanceof AEBaseBlockEntity;
    }

    public static void syncRename(Object target) {
        if (target instanceof AEBasePart part) {
            if (part.getHost() == null) {
                throw new IllegalStateException("Cannot synchronize a detached AE2 part custom name");
            }
            part.getHost().markForSave();
            part.getHost().markForUpdate();
            return;
        }
        if (target instanceof AEBaseBlockEntity blockEntity) {
            blockEntity.saveChanges();
            blockEntity.setChanged();
            blockEntity.markForUpdate();
            blockEntity.markForClientUpdate();
            return;
        }
        String targetType = target == null ? "null" : target.getClass().getName();
        throw new IllegalArgumentException("Unsupported pattern provider type: " + targetType);
    }
}

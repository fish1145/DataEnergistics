package com.fish_dan_.data_energistics.util;

import net.minecraft.network.chat.Component;

import appeng.blockentity.AEBaseBlockEntity;
import appeng.parts.AEBasePart;
import org.jetbrains.annotations.Nullable;

import java.lang.invoke.VarHandle;
import java.util.Optional;

public final class PatternProviderNameHelper {

    private static final Optional<VarHandle> AE_BASE_PART_CUSTOM_NAME_FIELD = resolveCustomNameField(AEBasePart.class);
    private static final Optional<VarHandle> AE_BASE_BLOCK_ENTITY_CUSTOM_NAME_FIELD = resolveCustomNameField(AEBaseBlockEntity.class);

    private PatternProviderNameHelper() {}

    public static boolean setCustomName(Object target, @Nullable Component customName) {
        if (target instanceof AEBasePart part) {
            return writeField(AE_BASE_PART_CUSTOM_NAME_FIELD, part, customName);
        }
        if (target instanceof AEBaseBlockEntity blockEntity) {
            return writeField(AE_BASE_BLOCK_ENTITY_CUSTOM_NAME_FIELD, blockEntity, customName);
        }
        return writeField(resolveCustomNameField(target.getClass()), target, customName);
    }

    @Nullable
    public static Component getCustomName(Object target) {
        if (target instanceof AEBasePart part) {
            return readField(AE_BASE_PART_CUSTOM_NAME_FIELD, part);
        }
        if (target instanceof AEBaseBlockEntity blockEntity) {
            return readField(AE_BASE_BLOCK_ENTITY_CUSTOM_NAME_FIELD, blockEntity);
        }
        return readField(resolveCustomNameField(target.getClass()), target);
    }

    public static boolean canRename(Object target) {
        if (target instanceof AEBasePart || target instanceof AEBaseBlockEntity) {
            return true;
        }
        return resolveCustomNameField(target.getClass()).isPresent();
    }

    public static void syncRename(Object target) {
        invokeNoArg(target, "saveChanges");
        invokeNoArg(target, "setChanged");
        invokeNoArg(target, "markForUpdate");
        invokeNoArg(target, "markForClientUpdate");
        if (target instanceof AEBasePart part && part.getHost() != null) {
            part.getHost().markForUpdate();
        }
    }

    @Nullable
    private static Optional<VarHandle> resolveCustomNameField(Class<?> owner) {
        return ReflectionAccess.findFieldAssignable(owner, "customName", Component.class);
    }

    private static boolean writeField(Optional<VarHandle> field, Object target, @Nullable Component value) {
        return ReflectionAccess.setField(field, target, value);
    }

    @Nullable
    private static Component readField(Optional<VarHandle> field, Object target) {
        Object value = ReflectionAccess.getField(field, target);
        return value instanceof Component component ? component : null;
    }

    private static void invokeNoArg(Object target, String methodName) {
        ReflectionAccess.invokeNoArgBestEffort(target, methodName);
    }
}

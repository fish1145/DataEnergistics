package com.fish_dan_.data_energistics.mixin.ae2lt;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.capabilities.BlockCapability;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import net.neoforged.neoforge.items.IItemHandler;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;

@Mixin(BlockCapability.class)
public abstract class Ae2ltEjectCapabilityMixin<T, C> {

    @Unique
    private static final String DE_EJECT_MODE_REGISTRY = "com.moakiee.ae2lt.logic.EjectModeRegistry";
    @Unique
    private static boolean dataEnergistics$proxying = false;
    @Unique
    private static MethodHandle dataEnergistics$isBypassedMethod;
    @Unique
    private static MethodHandle dataEnergistics$lookupByFaceMethod;
    @Unique
    private static MethodHandle dataEnergistics$getHostMethod;
    @Unique
    private static boolean dataEnergistics$ejectReflectionInitialized = false;

    @Unique
    private static final IItemHandler dataEnergistics$REJECTING_ITEM_HANDLER = new IItemHandler() {

        @Override
        public int getSlots() {
            return 1;
        }

        @Override
        public ItemStack getStackInSlot(int slot) {
            return ItemStack.EMPTY;
        }

        @Override
        public ItemStack insertItem(int slot, ItemStack stack, boolean simulate) {
            return stack;
        }

        @Override
        public ItemStack extractItem(int slot, int amount, boolean simulate) {
            return ItemStack.EMPTY;
        }

        @Override
        public int getSlotLimit(int slot) {
            return 0;
        }

        @Override
        public boolean isItemValid(int slot, ItemStack stack) {
            return false;
        }
    };

    @Unique
    private static final IFluidHandler dataEnergistics$REJECTING_FLUID_HANDLER = new IFluidHandler() {

        @Override
        public int getTanks() {
            return 1;
        }

        @Override
        public FluidStack getFluidInTank(int tank) {
            return FluidStack.EMPTY;
        }

        @Override
        public int getTankCapacity(int tank) {
            return 0;
        }

        @Override
        public boolean isFluidValid(int tank, FluidStack stack) {
            return false;
        }

        @Override
        public int fill(FluidStack resource, FluidAction action) {
            return 0;
        }

        @Override
        public FluidStack drain(FluidStack resource, FluidAction action) {
            return FluidStack.EMPTY;
        }

        @Override
        public FluidStack drain(int maxDrain, FluidAction action) {
            return FluidStack.EMPTY;
        }
    };

    @SuppressWarnings("unchecked")
    @Inject(method = "getCapability", at = @At("HEAD"), cancellable = true)
    private void dataEnergistics$interceptEjectCapability(Level level, BlockPos pos,
                                                          BlockState state, BlockEntity blockEntity, C context,
                                                          CallbackInfoReturnable<T> cir) {
        if (dataEnergistics$proxying) return;
        if (!dataEnergistics$hasAe2LtEjectSupport()) return;
        if (dataEnergistics$isBypassed()) return;
        if (!(level instanceof ServerLevel)) return;
        if (!(context instanceof Direction face)) return;

        Object entry = dataEnergistics$lookupByFace(level.dimension(), pos.asLong(), face);
        if (entry == null) return;

        BlockEntity host = dataEnergistics$getHost(entry);

        if (host != null) {
            Level hostLevel = host.getLevel();
            if (hostLevel == null) return;
            BlockPos hostPos = host.getBlockPos();
            BlockState hostState = hostLevel.getBlockState(hostPos);

            dataEnergistics$proxying = true;
            try {
                BlockCapability<T, C> cap = (BlockCapability<T, C>) (Object) this;
                T result = cap.getCapability(hostLevel, hostPos, hostState, host, context);
                if (result != null) {
                    cir.setReturnValue(result);
                }
            } finally {
                dataEnergistics$proxying = false;
            }
        } else {
            BlockCapability<T, C> cap = (BlockCapability<T, C>) (Object) this;
            if (cap == Capabilities.ItemHandler.BLOCK) {
                cir.setReturnValue((T) dataEnergistics$REJECTING_ITEM_HANDLER);
            } else if (cap == Capabilities.FluidHandler.BLOCK) {
                cir.setReturnValue((T) dataEnergistics$REJECTING_FLUID_HANDLER);
            }
        }
    }

    @Unique
    private static boolean dataEnergistics$hasAe2LtEjectSupport() {
        if (!dataEnergistics$ejectReflectionInitialized) {
            dataEnergistics$initEjectReflection();
        }
        return dataEnergistics$isBypassedMethod != null && dataEnergistics$lookupByFaceMethod != null && dataEnergistics$getHostMethod != null;
    }

    @Unique
    private static void dataEnergistics$initEjectReflection() {
        dataEnergistics$ejectReflectionInitialized = true;
        try {
            Class<?> registryClass = Class.forName(DE_EJECT_MODE_REGISTRY);
            MethodHandles.Lookup lookup = MethodHandles.publicLookup();
            dataEnergistics$isBypassedMethod = lookup.findStatic(registryClass, "isBypassed",
                    MethodType.methodType(boolean.class));
            Class<?> ejectEntryClass = Class.forName("com.moakiee.ae2lt.logic.EjectModeRegistry$EjectEntry");
            dataEnergistics$lookupByFaceMethod = lookup.findStatic(registryClass,
                    "lookupByFace",
                    MethodType.methodType(ejectEntryClass, ResourceKey.class, long.class, Direction.class));
            dataEnergistics$getHostMethod = lookup.findVirtual(ejectEntryClass, "getHost",
                    MethodType.methodType(BlockEntity.class));
        } catch (Exception ignored) {
            dataEnergistics$isBypassedMethod = null;
            dataEnergistics$lookupByFaceMethod = null;
            dataEnergistics$getHostMethod = null;
        }
    }

    @Unique
    private static boolean dataEnergistics$isBypassed() {
        try {
            return Boolean.TRUE.equals(dataEnergistics$isBypassedMethod.invoke());
        } catch (Throwable ignored) {
            return false;
        }
    }

    @Unique
    private static Object dataEnergistics$lookupByFace(ResourceKey<Level> dimension, long pos, Direction face) {
        try {
            return dataEnergistics$lookupByFaceMethod.invoke(dimension, pos, face);
        } catch (Throwable ignored) {
            return null;
        }
    }

    @Unique
    private static BlockEntity dataEnergistics$getHost(Object entry) {
        try {
            Object host = dataEnergistics$getHostMethod.invoke(entry);
            return host instanceof BlockEntity blockEntity ? blockEntity : null;
        } catch (Throwable ignored) {
            return null;
        }
    }
}

package com.fish_dan_.data_energistics.item;

import com.fish_dan_.data_energistics.ae2.ModAE2Keys;

import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;

import appeng.api.behaviors.ContainerItemStrategies;
import appeng.api.behaviors.ContainerItemStrategy;
import appeng.api.config.Actionable;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.AEKeyType;
import appeng.api.stacks.GenericStack;
import appeng.util.CowMap;
import org.jetbrains.annotations.Nullable;

import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Map;

public class DigitalStorageDepotKeyContainerItemStrategy implements ContainerItemStrategy<AEKey, DigitalStorageDepotKeyContainerItemStrategy.Context> {

    private final AEKeyType type;
    private final @Nullable ContainerItemStrategy<AEKey, Object> original;

    public DigitalStorageDepotKeyContainerItemStrategy(AEKeyType type, @Nullable ContainerItemStrategy<AEKey, Object> original) {
        this.type = type;
        this.original = original;
    }

    public static void registerMissingStrategies() {
        for (AEKeyType type : ModAE2Keys.types()) {
            if (type == AEKeyType.items() || type == AEKeyType.fluids()) {
                continue;
            }
            installStrategy(type);
        }
    }

    @Override
    public @Nullable GenericStack getContainedStack(ItemStack stack) {
        if (!isKeySlotContainer(stack)) {
            return this.original == null ? null : this.original.getContainedStack(stack);
        }

        GenericStack content = DigitalStorageDepotBlockItem.getSelectedKeyStack(stack);
        return content != null && this.type.contains(content.what()) ? content : null;
    }

    @Override
    public @Nullable Context findCarriedContext(Player player, AbstractContainerMenu menu) {
        ItemStack stack = menu.getCarried();
        if (isKeySlotContainer(stack)) {
            return Context.depot(stack);
        }
        Object originalContext = this.original == null ? null : this.original.findCarriedContext(player, menu);
        return originalContext == null ? null : Context.original(originalContext);
    }

    @Override
    public @Nullable Context findPlayerSlotContext(Player player, int slot) {
        ItemStack stack = player.getInventory().getItem(slot);
        if (isKeySlotContainer(stack)) {
            return Context.depot(stack);
        }
        Object originalContext = this.original == null ? null : this.original.findPlayerSlotContext(player, slot);
        return originalContext == null ? null : Context.original(originalContext);
    }

    @Override
    public long extract(Context context, AEKey what, long amount, Actionable mode) {
        if (!this.type.contains(what)) {
            return 0L;
        }
        if (context.depotStack() != null) {
            return DigitalStorageDepotBlockItem.extractFromSelectedKeySlot(context.depotStack(), what, amount, mode);
        }
        return this.original == null ? 0L : this.original.extract(context.originalContext(), what, amount, mode);
    }

    @Override
    public long insert(Context context, AEKey what, long amount, Actionable mode) {
        if (!this.type.contains(what)) {
            return 0L;
        }
        if (context.depotStack() != null) {
            return DigitalStorageDepotBlockItem.insertIntoSelectedKeySlot(context.depotStack(), what, amount, mode);
        }
        return this.original == null ? 0L : this.original.insert(context.originalContext(), what, amount, mode);
    }

    @Override
    public void playFillSound(Player player, AEKey what) {
        if (this.original != null) {
            this.original.playFillSound(player, what);
        } else {
            player.playNotifySound(SoundEvents.BUCKET_FILL, SoundSource.PLAYERS, 1.0F, 1.0F);
        }
    }

    @Override
    public void playEmptySound(Player player, AEKey what) {
        if (this.original != null) {
            this.original.playEmptySound(player, what);
        } else {
            player.playNotifySound(SoundEvents.BUCKET_EMPTY, SoundSource.PLAYERS, 1.0F, 1.0F);
        }
    }

    @Override
    public @Nullable GenericStack getExtractableContent(Context context) {
        if (context.depotStack() != null) {
            return getContainedStack(context.depotStack());
        }
        return this.original == null ? null : this.original.getExtractableContent(context.originalContext());
    }

    private static boolean isKeySlotContainer(ItemStack stack) {
        return DigitalStorageDepotBlockItem.isDepotStack(stack) && DigitalStorageDepotBlockItem.isBucketMode(stack) && DigitalStorageDepotBlockItem.isKeySlotMarked(stack);
    }

    private static @Nullable ContainerItemStrategy<?, ?> getRegisteredStrategy(AEKeyType type) {
        return ContainerItemStrategies.strategies.getMap().get(type);
    }

    @SuppressWarnings("unchecked")
    private static @Nullable ContainerItemStrategy<AEKey, Object> castStrategy(@Nullable ContainerItemStrategy<?, ?> strategy) {
        return strategy == null ? null : (ContainerItemStrategy<AEKey, Object>) strategy;
    }

    @SuppressWarnings("unchecked")
    private static void installStrategy(AEKeyType type) {
        CowMap<AEKeyType, ContainerItemStrategy<?, ?>> strategies = ContainerItemStrategies.strategies;
        synchronized (strategies) {
            ContainerItemStrategy<?, ?> original = strategies.map.get(type);
            if (original instanceof DigitalStorageDepotKeyContainerItemStrategy) {
                return;
            }

            DigitalStorageDepotKeyContainerItemStrategy strategy = new DigitalStorageDepotKeyContainerItemStrategy(
                    type,
                    castStrategy(original));

            Map<AEKeyType, ContainerItemStrategy<?, ?>> updated = new IdentityHashMap<>(strategies.map);
            updated.put(type, strategy);
            strategies.map = Collections.unmodifiableMap(updated);
        }
    }

    public record Context(@Nullable ItemStack depotStack, @Nullable Object originalContext) {

        private static Context depot(ItemStack stack) {
            return new Context(stack, null);
        }

        private static Context original(Object originalContext) {
            return new Context(null, originalContext);
        }
    }
}

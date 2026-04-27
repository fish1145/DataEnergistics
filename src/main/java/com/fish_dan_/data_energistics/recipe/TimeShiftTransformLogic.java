package com.fish_dan_.data_energistics.recipe;

import com.fish_dan_.data_energistics.registry.ModRecipes;
import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.entity.item.ItemExpireEvent;
import net.neoforged.neoforge.event.tick.EntityTickEvent;

public final class TimeShiftTransformLogic {
    private static final int CHECK_INTERVAL_TICKS = 20;
    private static final double SEARCH_RADIUS = 1.0D;

    @SubscribeEvent
    public void onEntityTick(EntityTickEvent.Post event) {
        if (!(event.getEntity() instanceof ItemEntity itemEntity) || itemEntity.level().isClientSide()) {
            return;
        }

        if (itemEntity.isRemoved() || itemEntity.getItem().isEmpty() || itemEntity.getAge() % CHECK_INTERVAL_TICKS != 0) {
            return;
        }

        tryTransform(itemEntity);
    }

    @SubscribeEvent
    public void onItemExpire(ItemExpireEvent event) {
        ItemEntity itemEntity = event.getEntity();
        if (itemEntity.level().isClientSide()) {
            return;
        }

        int extraLife = getNeededExtraLife(itemEntity);
        if (extraLife > event.getExtraLife()) {
            event.setExtraLife(extraLife);
        }
    }

    private static int getNeededExtraLife(ItemEntity itemEntity) {
        int age = itemEntity.getAge();
        int extraLife = 0;
        List<ItemEntity> nearbyItems = getNearbyItems(itemEntity.level(), itemEntity);

        for (var holder : itemEntity.level().getRecipeManager().getAllRecipesFor(ModRecipes.TIME_SHIFT_TYPE.get())) {
            TimeShiftRecipe recipe = holder.value();
            if (!canBeIngredient(recipe, itemEntity.getItem())) {
                continue;
            }

            Map<ItemEntity, Integer> usedItems = findUsedItems(recipe, nearbyItems, false);
            if (usedItems == null || !usedItems.containsKey(itemEntity)) {
                continue;
            }

            extraLife = Math.max(extraLife, CHECK_INTERVAL_TICKS);
            if (age < recipe.getDurationTicks()) {
                extraLife = Math.max(extraLife, recipe.getDurationTicks() - age + CHECK_INTERVAL_TICKS);
            }
        }

        return extraLife;
    }

    private static boolean canBeIngredient(TimeShiftRecipe recipe, ItemStack stack) {
        for (Ingredient ingredient : recipe.getIngredients()) {
            if (ingredient.test(stack)) {
                return true;
            }
        }

        return false;
    }

    private static boolean tryTransform(ItemEntity trigger) {
        Level level = trigger.level();
        List<ItemEntity> nearbyItems = getNearbyItems(level, trigger);

        for (var holder : level.getRecipeManager().getAllRecipesFor(ModRecipes.TIME_SHIFT_TYPE.get())) {
            TimeShiftRecipe recipe = holder.value();
            if (trigger.getAge() < recipe.getDurationTicks() || !canBeIngredient(recipe, trigger.getItem()) || !recipe.canRunAt(level)) {
                continue;
            }

            Map<ItemEntity, Integer> usedItems = findUsedItems(recipe, nearbyItems, true);
            if (usedItems == null) {
                continue;
            }

            consumeInputs(usedItems);
            spawnResults(level, trigger, recipe.getResults());
            return true;
        }

        return false;
    }

    private static List<ItemEntity> getNearbyItems(Level level, ItemEntity trigger) {
        AABB bounds = new AABB(
                trigger.getX() - SEARCH_RADIUS,
                trigger.getY() - SEARCH_RADIUS,
                trigger.getZ() - SEARCH_RADIUS,
                trigger.getX() + SEARCH_RADIUS,
                trigger.getY() + SEARCH_RADIUS,
                trigger.getZ() + SEARCH_RADIUS);

        return level.getEntities((Entity) null, bounds)
                .stream()
                .filter(ItemEntity.class::isInstance)
                .map(ItemEntity.class::cast)
                .filter(item -> !item.isRemoved() && !item.getItem().isEmpty())
                .toList();
    }

    private static Map<ItemEntity, Integer> findUsedItems(TimeShiftRecipe recipe, List<ItemEntity> nearbyItems, boolean requireDuration) {
        List<Ingredient> remaining = new ArrayList<>(recipe.getIngredients());
        Map<ItemEntity, Integer> usedItems = new IdentityHashMap<>();

        for (ItemEntity itemEntity : nearbyItems) {
            if (requireDuration && itemEntity.getAge() < recipe.getDurationTicks()) {
                continue;
            }

            ItemStack stack = itemEntity.getItem();
            var iterator = remaining.iterator();
            while (iterator.hasNext()) {
                int usedFromThisEntity = usedItems.getOrDefault(itemEntity, 0);
                if (stack.getCount() - usedFromThisEntity <= 0) {
                    break;
                }

                if (iterator.next().test(stack)) {
                    usedItems.put(itemEntity, usedFromThisEntity + 1);
                    iterator.remove();
                }
            }

            if (remaining.isEmpty()) {
                return usedItems;
            }
        }

        return null;
    }

    private static void consumeInputs(Map<ItemEntity, Integer> usedItems) {
        for (Map.Entry<ItemEntity, Integer> entry : usedItems.entrySet()) {
            ItemEntity itemEntity = entry.getKey();
            itemEntity.getItem().shrink(entry.getValue());
            if (itemEntity.getItem().isEmpty()) {
                itemEntity.discard();
            }
        }
    }

    private static void spawnResults(Level level, ItemEntity trigger, List<ItemStack> results) {
        for (ItemStack result : results) {
            if (result.isEmpty()) {
                continue;
            }

            ItemEntity output = new ItemEntity(level, trigger.getX(), trigger.getY(), trigger.getZ(), result.copy());
            output.setDeltaMovement(trigger.getDeltaMovement().scale(0.25D));
            level.addFreshEntity(output);
        }
    }
}

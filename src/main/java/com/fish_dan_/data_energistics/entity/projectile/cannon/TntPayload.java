package com.fish_dan_.data_energistics.entity.projectile.cannon;

import com.fish_dan_.data_energistics.Data_Energistics;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.PrimedTnt;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.BlockItemStateProperties;
import net.minecraft.world.level.block.TntBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;

import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import org.jspecify.annotations.Nullable;

import java.util.List;
import java.util.function.Consumer;

/** Native TNT ammunition, including mod variants whose ignition hook creates a {@link PrimedTnt}. */
public final class TntPayload {

    private static final TagKey<Item> AMMUNITION = TagKey.create(Registries.ITEM, Data_Energistics.id("cannon/tnt"));

    private TntPayload() {}

    /** TNT subclasses are automatic; the item tag supports TNT blocks with a different base class. */
    public static boolean isTnt(ItemStack stack) {
        return stack.getItem() instanceof BlockItem blockItem && (blockItem.getBlock() instanceof TntBlock || stack.is(AMMUNITION));
    }

    /**
     * Runs on the server collision tick. The native ignition hook receives the ammo's block state and shooter;
     * only TNT entities created by that call are detonated. No temporary block replaces the impact terrain.
     * Custom explosion/activation overrides remain in control, including multi-tick explosives.
     * The nonempty ammunition must satisfy {@link #isTnt(ItemStack)}. Face is null for entity impacts and owner
     * may be absent after a reload. Native hook failures are logged and the affected explosive is stopped.
     */
    public static void detonate(ServerLevel level, ItemStack ammunition, Vec3 impact, @Nullable Direction face,
                                @Nullable LivingEntity owner) {
        BlockItem item = (BlockItem) ammunition.getItem();
        BlockState state = ammunition.getOrDefault(DataComponents.BLOCK_STATE, BlockItemStateProperties.EMPTY)
                .apply(item.getBlock().defaultBlockState());
        BlockPos position = BlockPos.containing(impact);
        List<PrimedTnt> primedEntities = new ObjectArrayList<>();
        Thread collisionThread = Thread.currentThread();
        Consumer<EntityJoinLevelEvent> capture = event -> {
            if (Thread.currentThread() == collisionThread && event.getLevel() == level && !event.loadedFromDisk() && event.getEntity() instanceof PrimedTnt primed) {
                primedEntities.add(primed);
            }
        };
        NeoForge.EVENT_BUS.addListener(EventPriority.LOWEST, false, EntityJoinLevelEvent.class, capture);
        try {
            state.onCaughtFire(level, position, face, owner);
        } catch (RuntimeException exception) {
            primedEntities.forEach(PrimedTnt::discard);
            Data_Energistics.LOGGER.error("Failed to ignite cannon TNT {} at {} in {}", ammunition.getItem(), position,
                    level.dimension().location(), exception);
            return;
        } finally {
            NeoForge.EVENT_BUS.unregister(capture);
        }

        // Detonate after entity admission and after removing the listener, so chain reactions keep their own fuses.
        for (PrimedTnt primed : primedEntities) {
            if (!primed.isAddedToLevel() || primed.isRemoved()) continue;
            try {
                primed.setPos(impact);
                primed.setDeltaMovement(Vec3.ZERO);
                primed.setNoGravity(true);
                primed.setFuse(0);
                primed.tick();
            } catch (RuntimeException exception) {
                primed.discard();
                Data_Energistics.LOGGER.error("Failed to detonate cannon TNT {} at {} in {}", primed.getType(), position,
                        level.dimension().location(), exception);
            }
        }
    }
}

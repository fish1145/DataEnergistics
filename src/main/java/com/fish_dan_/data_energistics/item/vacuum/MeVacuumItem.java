package com.fish_dan_.data_energistics.item.vacuum;

import com.fish_dan_.data_energistics.ae2.key.DataKey;
import com.fish_dan_.data_energistics.entity.DispersingDataEntity;
import com.fish_dan_.data_energistics.item.carrier.RadixContainmentSphereItem;
import com.fish_dan_.data_energistics.item.powered.PoweredEnergyItem;
import com.fish_dan_.data_energistics.registry.DEMenus;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.UseAnim;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.fluids.FluidStack;

import appeng.api.config.Actionable;
import appeng.api.implementations.menuobjects.IMenuItem;
import appeng.api.implementations.menuobjects.ItemMenuHost;
import appeng.api.networking.security.IActionSource;
import appeng.api.stacks.AEFluidKey;
import appeng.api.stacks.AEItemKey;
import appeng.menu.MenuOpener;
import appeng.menu.locator.ItemMenuHostLocator;
import appeng.menu.locator.MenuLocators;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class MeVacuumItem extends Item implements PoweredEnergyItem, IMenuItem {

    private static final double MAX_POWER = 200_000.0D;
    private static final int USE_DURATION = 72_000;
    private static final int VACUUM_INTERVAL_TICKS = 5;
    private static final int VACUUM_SOUND_INTERVAL_TICKS = 8;
    private static final double VACUUM_DISTANCE = 2.5D;
    private static final double BLOCK_PICK_DISTANCE = 5.0D;
    private static final double HOVER_DISTANCE = 1.4D;
    private static final double HOVER_PULL_STRENGTH = 0.35D;
    private static final double HOVER_MAX_SPEED = 0.75D;
    private static final int LAUNCH_READY_TICKS = 30;
    private static final int HOVER_TRACKING_TTL_TICKS = 10;
    private static final double LAUNCH_RANGE = 15.0D;
    private static final double LAUNCH_SPEED = 15.0D;
    private static final double LAUNCH_UPWARD_SPEED = 0.45D;
    private static final long FLUID_SOURCE_AMOUNT = AEFluidKey.AMOUNT_BLOCK;
    private static final Map<UUID, HoveredEntityState> HOVERED_ENTITY_STATES = new HashMap<>();

    public MeVacuumItem(Properties properties) {
        super(properties);
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> lines,
                                TooltipFlag tooltipFlag) {
        super.appendHoverText(stack, context, lines, tooltipFlag);
        this.appendEnergyHoverText(stack, lines);
    }

    @Override
    public boolean isBarVisible(ItemStack stack) {
        return this.isEnergyBarVisible(stack);
    }

    @Override
    public int getBarWidth(ItemStack stack) {
        return this.getEnergyBarWidth(stack);
    }

    @Override
    public int getBarColor(ItemStack stack) {
        return this.getEnergyBarColor(stack);
    }

    @Override
    public double getAEMaxPower(ItemStack stack) {
        return MAX_POWER * (1 + 8 * this.getEnergyCardCount(stack));
    }

    @Override
    public double getActionEnergyCost(ItemStack stack) {
        return RadixContainmentSphereItem.ENERGY_PER_CAPTURE + ENERGY_PER_SPEED_CARD * this.getSpeedCardCount(stack) + ENERGY_PER_SABER_ENERGY_CARD * this.getSaberEnergyCardCount(stack);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand usedHand) {
        ItemStack stack = player.getItemInHand(usedHand);
        if (!player.isShiftKeyDown()) {
            player.startUsingItem(usedHand);
            return InteractionResultHolder.consume(stack);
        }

        if (!level.isClientSide()) {
            MenuOpener.open(DEMenus.ME_VACUUM.get(), player, MenuLocators.forHand(player, usedHand));
        }

        return new InteractionResultHolder<>(InteractionResult.sidedSuccess(level.isClientSide()), stack);
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        Player player = context.getPlayer();
        if (player == null) {
            return InteractionResult.PASS;
        }

        if (!player.isShiftKeyDown()) {
            player.startUsingItem(context.getHand());
            return InteractionResult.CONSUME;
        }

        if (!context.getLevel().isClientSide()) {
            MenuOpener.open(DEMenus.ME_VACUUM.get(), player, MenuLocators.forItemUseContext(context));
        }

        return InteractionResult.sidedSuccess(context.getLevel().isClientSide());
    }

    @Override
    public void onUseTick(Level level, LivingEntity livingEntity, ItemStack stack, int remainingUseTicks) {
        if (level.isClientSide() || !(livingEntity instanceof Player player)) {
            return;
        }

        int usedTicks = this.getUseDuration(stack, livingEntity) - remainingUseTicks;
        if (usedTicks <= 0) {
            return;
        }

        if (!this.hasSufficientEnergy(stack)) {
            livingEntity.stopUsingItem();
            return;
        }

        if (this.vacuumInFront((ServerLevel) level, player, stack, usedTicks % VACUUM_INTERVAL_TICKS == 0) && usedTicks % VACUUM_SOUND_INTERVAL_TICKS == 0) {
            level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.BREEZE_WHIRL,
                    SoundSource.PLAYERS, 0.55F, 1.15F + level.random.nextFloat() * 0.15F);
        }
    }

    @Override
    public int getUseDuration(ItemStack stack, LivingEntity entity) {
        return USE_DURATION;
    }

    @Override
    public UseAnim getUseAnimation(ItemStack stack) {
        return UseAnim.BOW;
    }

    @Override
    public boolean onLeftClickEntity(ItemStack stack, Player player, Entity entity) {
        if (player.level().isClientSide()) {
            return false;
        }
        if (!this.hasSufficientEnergy(stack)) {
            return true;
        }
        return isLaunchReady(player, entity) && this.tryLaunchEntity(stack, player, entity);
    }

    @Override
    public @Nullable ItemMenuHost<?> getMenuHost(Player player, ItemMenuHostLocator locator,
                                                 @Nullable BlockHitResult hitResult) {
        return new MeVacuumMenuHost(this, player, locator);
    }

    @SuppressWarnings("UnusedReturnValue")
    public boolean tryLaunchTrackedEntity(ItemStack stack, Player player) {
        if (!(player.level() instanceof ServerLevel serverLevel)) {
            return false;
        }
        if (stack.getItem() != this) {
            return false;
        }
        if (!player.isUsingItem() || !ItemStack.isSameItemSameComponents(player.getUseItem(), stack)) {
            return false;
        }

        HoveredEntityState state = HOVERED_ENTITY_STATES.get(player.getUUID());
        if (!isLaunchReady(player, state)) {
            return false;
        }

        Entity target = serverLevel.getEntity(state.targetId());
        if (target == null || !target.isAlive() || player.distanceToSqr(target) > LAUNCH_RANGE * LAUNCH_RANGE) {
            HOVERED_ENTITY_STATES.remove(player.getUUID());
            return false;
        }

        return this.tryLaunchEntity(stack, player, target);
    }

    private boolean vacuumInFront(ServerLevel level, Player player, ItemStack stack, boolean absorbThisTick) {
        BlockPos center = getVacuumCenter(level, player);
        AABB area = getVacuumArea(center);
        boolean changed = false;

        if (absorbThisTick) {
            List<ItemEntity> nearbyItems = level.getEntitiesOfClass(
                    ItemEntity.class, area, entity -> entity.isAlive() && !entity.getItem().isEmpty());
            changed |= vacuumItemEntities(level, player, stack, nearbyItems);
            changed |= vacuumFluidSources(level, player, stack, center);
        }

        List<Entity> nearbyEntities = level.getEntitiesOfClass(
                Entity.class, area, entity -> entity.isAlive() && !(entity instanceof Player) &&
                        !(entity instanceof ItemEntity));
        if (absorbThisTick) {
            changed |= vacuumDataEntities(level, player, stack, nearbyEntities);
        }
        changed |= hoverOtherEntities(level, player, area, nearbyEntities);

        return changed;
    }

    private boolean vacuumItemEntities(ServerLevel level, Player player, ItemStack stack,
                                       List<ItemEntity> nearbyItems) {
        boolean changed = false;
        IActionSource actionSource = IActionSource.ofPlayer(player);
        var registries = level.registryAccess();
        for (ItemEntity itemEntity : nearbyItems) {
            if (!this.hasSufficientEnergy(stack)) {
                player.stopUsingItem();
                break;
            }

            ItemStack entityStack = itemEntity.getItem();
            AEItemKey itemKey = AEItemKey.of(entityStack);
            if (itemKey == null) {
                continue;
            }

            long inserted = MeVacuumMenuHost.insertIntoStoredCells(stack, registries, itemKey,
                    entityStack.getCount(), actionSource);
            if (inserted <= 0L) {
                continue;
            }

            entityStack.shrink((int) Math.min(Integer.MAX_VALUE, inserted));
            if (entityStack.isEmpty()) {
                itemEntity.discard();
            }
            this.consumeActionEnergy(stack);
            changed = true;
        }
        return changed;
    }

    private boolean vacuumFluidSources(ServerLevel level, Player player, ItemStack stack, BlockPos center) {
        boolean changed = false;
        IActionSource actionSource = IActionSource.ofPlayer(player);
        var registries = level.registryAccess();
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        for (int x = -1; x <= 1; x++) {
            for (int y = -1; y <= 1; y++) {
                for (int z = -1; z <= 1; z++) {
                    if (!this.hasSufficientEnergy(stack)) {
                        player.stopUsingItem();
                        return changed;
                    }

                    pos.set(center.getX() + x, center.getY() + y, center.getZ() + z);
                    BlockState state = level.getBlockState(pos);
                    FluidState fluidState = state.getFluidState();
                    if (fluidState.isEmpty() || !fluidState.isSource() ||
                            !player.mayUseItemAt(pos, Direction.UP, stack)) {
                        continue;
                    }

                    AEFluidKey fluidKey = AEFluidKey.of(new FluidStack(fluidState.getType(), 1));
                    if (fluidKey == null) {
                        continue;
                    }

                    long simulated = MeVacuumMenuHost.simulateInsertIntoStoredCells(
                            stack, registries, fluidKey, FLUID_SOURCE_AMOUNT, actionSource);
                    if (simulated < FLUID_SOURCE_AMOUNT) {
                        continue;
                    }

                    long inserted = MeVacuumMenuHost.insertIntoStoredCells(
                            stack, registries, fluidKey, FLUID_SOURCE_AMOUNT, actionSource);
                    if (inserted < FLUID_SOURCE_AMOUNT) {
                        continue;
                    }

                    level.setBlock(pos, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL_IMMEDIATE);
                    this.consumeActionEnergy(stack);
                    changed = true;
                }
            }
        }
        return changed;
    }

    private boolean vacuumDataEntities(ServerLevel level, Player player, ItemStack stack,
                                       List<Entity> nearbyEntities) {
        boolean changed = false;
        IActionSource actionSource = IActionSource.ofPlayer(player);
        var registries = level.registryAccess();
        double energyCost = this.getActionEnergyCost(stack);
        for (Entity entity : nearbyEntities) {
            if (!(entity instanceof DispersingDataEntity target)) {
                continue;
            }
            int affordableAmount = (int) Math.min(
                    target.getDataAmount(),
                    Math.floor(this.getAECurrentPower(stack) / energyCost));
            if (affordableAmount <= 0) {
                player.stopUsingItem();
                break;
            }

            long inserted = MeVacuumMenuHost.insertIntoStoredCells(
                    stack,
                    registries,
                    DataKey.of(),
                    affordableAmount,
                    actionSource);
            if (inserted <= 0L) {
                continue;
            }

            target.removeDataAmount(Math.toIntExact(inserted));
            this.extractAEPower(stack, energyCost * inserted, Actionable.MODULATE);
            changed = true;
        }
        return changed;
    }

    private static boolean hoverOtherEntities(ServerLevel level, Player player, AABB area,
                                              List<Entity> nearbyEntities) {
        Vec3 look = player.getLookAngle().normalize();
        Vec3 hoverPoint = player.getEyePosition().add(look.scale(HOVER_DISTANCE));
        long gameTime = level.getGameTime();
        boolean moved = false;

        for (Entity target : nearbyEntities) {
            if (!isVacuumHoverCandidate(target) || !area.intersects(target.getBoundingBox())) {
                continue;
            }
            Vec3 targetCenter = target.getBoundingBox().getCenter();
            Vec3 toHoverPoint = hoverPoint.subtract(targetCenter);
            Vec3 movement = toHoverPoint.scale(HOVER_PULL_STRENGTH);
            if (movement.lengthSqr() > HOVER_MAX_SPEED * HOVER_MAX_SPEED) {
                movement = movement.normalize().scale(HOVER_MAX_SPEED);
            }

            target.setDeltaMovement(movement);
            target.resetFallDistance();
            target.hasImpulse = true;
            target.hurtMarked = true;
            moved = true;
        }

        updateHoveredEntityState(level, player, nearbyEntities, hoverPoint, gameTime);
        cleanupHoveredEntities(gameTime);
        return moved;
    }

    private static BlockPos getVacuumCenter(Level level, Player player) {
        Vec3 eye = player.getEyePosition();
        Vec3 look = player.getLookAngle();
        HitResult hitResult = level.clip(new ClipContext(
                eye,
                eye.add(look.scale(BLOCK_PICK_DISTANCE)),
                ClipContext.Block.COLLIDER,
                ClipContext.Fluid.SOURCE_ONLY,
                player));
        if (hitResult instanceof BlockHitResult blockHitResult && hitResult.getType() != HitResult.Type.MISS) {
            return blockHitResult.getBlockPos();
        }

        return BlockPos.containing(eye.add(look.scale(VACUUM_DISTANCE)));
    }

    private static AABB getVacuumArea(BlockPos center) {
        return new AABB(
                center.getX() - 1.0D,
                center.getY() - 1.0D,
                center.getZ() - 1.0D,
                center.getX() + 2.0D,
                center.getY() + 2.0D,
                center.getZ() + 2.0D);
    }

    private static boolean isVacuumHoverCandidate(Entity entity) {
        return entity.isAlive() && !(entity instanceof Player) && !(entity instanceof ItemEntity) && !(entity instanceof DispersingDataEntity);
    }

    private static void updateHoveredEntityState(ServerLevel level, Player player, List<Entity> nearbyEntities,
                                                 Vec3 hoverPoint, long gameTime) {
        UUID playerId = player.getUUID();
        HoveredEntityState previous = HOVERED_ENTITY_STATES.get(playerId);
        if (previous != null) {
            Entity trackedTarget = level.getEntity(previous.targetId());
            if (trackedTarget == null || !trackedTarget.isAlive()) {
                HOVERED_ENTITY_STATES.remove(playerId);
                previous = null;
            }
        }

        if (previous != null) {
            Entity trackedCandidate = findCandidate(nearbyEntities, previous.targetId());
            if (trackedCandidate != null) {
                trackHoveredEntity(player, trackedCandidate, gameTime);
                return;
            }
            if (gameTime - previous.lastSeenTick() <= HOVER_TRACKING_TTL_TICKS) {
                return;
            }
            HOVERED_ENTITY_STATES.remove(playerId);
        }

        Entity nearest = null;
        double nearestDistance = Double.POSITIVE_INFINITY;
        for (Entity candidate : nearbyEntities) {
            if (!isVacuumHoverCandidate(candidate)) {
                continue;
            }
            double distance = candidate.getBoundingBox().getCenter().distanceToSqr(hoverPoint);
            if (distance < nearestDistance ||
                    distance == nearestDistance && nearest != null && candidate.getId() < nearest.getId()) {
                nearest = candidate;
                nearestDistance = distance;
            }
        }
        if (nearest != null) {
            trackHoveredEntity(player, nearest, gameTime);
        }
    }

    private static @Nullable Entity findCandidate(List<Entity> nearbyEntities, UUID targetId) {
        for (Entity candidate : nearbyEntities) {
            if (candidate.getUUID().equals(targetId) && isVacuumHoverCandidate(candidate)) {
                return candidate;
            }
        }
        return null;
    }

    private static void trackHoveredEntity(Player player, Entity entity, long gameTime) {
        UUID playerId = player.getUUID();
        HoveredEntityState previous = HOVERED_ENTITY_STATES.get(playerId);
        int hoverTicks = previous != null && previous.targetId().equals(entity.getUUID()) && gameTime - previous.lastSeenTick() <= HOVER_TRACKING_TTL_TICKS ? previous.hoverTicks() + 1 : 1;
        HOVERED_ENTITY_STATES.put(playerId, new HoveredEntityState(entity.getUUID(), hoverTicks, gameTime));
    }

    private static boolean isLaunchReady(Player player, Entity entity) {
        HoveredEntityState state = HOVERED_ENTITY_STATES.get(player.getUUID());
        return entity.isAlive() && state != null && state.targetId().equals(entity.getUUID()) && isLaunchReady(player, state) && player.distanceToSqr(entity) <= LAUNCH_RANGE * LAUNCH_RANGE;
    }

    private static boolean isLaunchReady(Player player, @Nullable HoveredEntityState state) {
        return state != null && player.level().getGameTime() - state.lastSeenTick() <= HOVER_TRACKING_TTL_TICKS && state.hoverTicks() >= LAUNCH_READY_TICKS;
    }

    private boolean tryLaunchEntity(ItemStack stack, Player player, Entity target) {
        if (!(player.level() instanceof ServerLevel serverLevel)) {
            return false;
        }
        if (!this.hasSufficientEnergy(stack) || !isLaunchReady(player, target)) {
            return false;
        }

        Vec3 direction = new Vec3(target.getX() - player.getX(), 0.0D, target.getZ() - player.getZ());
        if (direction.lengthSqr() <= 1.0E-4D) {
            Vec3 look = player.getLookAngle();
            direction = new Vec3(look.x, 0.0D, look.z);
        }
        if (direction.lengthSqr() <= 1.0E-4D) {
            return false;
        }

        direction = direction.normalize();
        target.setDeltaMovement(direction.scale(LAUNCH_SPEED).add(0.0D, LAUNCH_UPWARD_SPEED, 0.0D));
        target.resetFallDistance();
        target.hasImpulse = true;
        target.hurtMarked = true;
        HOVERED_ENTITY_STATES.remove(player.getUUID());
        this.consumeActionEnergy(stack);

        Vec3 center = target.getBoundingBox().getCenter();
        serverLevel.playSound(null, center.x, center.y, center.z, SoundEvents.WARDEN_SONIC_BOOM,
                SoundSource.PLAYERS, 1.0F, 1.0F);
        serverLevel.sendParticles(ParticleTypes.SONIC_BOOM, center.x, center.y, center.z, 1,
                0.0D, 0.0D, 0.0D, 0.0D);
        return true;
    }

    private static void cleanupHoveredEntities(long gameTime) {
        HOVERED_ENTITY_STATES.entrySet()
                .removeIf(entry -> gameTime - entry.getValue().lastSeenTick() > HOVER_TRACKING_TTL_TICKS);
    }

    private record HoveredEntityState(UUID targetId, int hoverTicks, long lastSeenTick) {}
}

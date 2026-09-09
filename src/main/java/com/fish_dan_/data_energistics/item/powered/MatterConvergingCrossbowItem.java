package com.fish_dan_.data_energistics.item.powered;

import com.fish_dan_.data_energistics.entity.projectile.MatterConvergingBoltEntity;
import com.fish_dan_.data_energistics.entity.projectile.ThrownLightSaberEntity;
import com.fish_dan_.data_energistics.entity.projectile.cannon.CannonShot;
import com.fish_dan_.data_energistics.entity.projectile.cannon.GrenadePayload;
import com.fish_dan_.data_energistics.item.powered.cannon.CannonBallistics;
import com.fish_dan_.data_energistics.item.powered.cannon.CannonCharge;
import com.fish_dan_.data_energistics.registry.DEDataComponents;
import com.fish_dan_.data_energistics.registry.DEMenus;
import com.fish_dan_.data_energistics.menu.powered.MatterConvergingCrossbowConfigMenu;
import com.fish_dan_.data_energistics.registry.DEItems;

import appeng.api.config.AccessRestriction;
import appeng.api.config.Actionable;
import appeng.api.config.FuzzyMode;
import appeng.api.ids.AEComponents;
import appeng.api.implementations.items.IAEItemPowerStorage;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.AEKeyType;
import appeng.api.stacks.GenericStack;
import appeng.api.storage.StorageCells;
import appeng.api.storage.cells.IBasicCellItem;
import appeng.api.storage.cells.ISaveProvider;
import appeng.api.storage.cells.StorageCell;
import appeng.api.upgrades.IUpgradeInventory;
import appeng.api.upgrades.IUpgradeableItem;
import appeng.api.upgrades.UpgradeInventories;
import appeng.api.upgrades.Upgrades;
import appeng.core.definitions.AEItems;
import appeng.core.localization.Tooltips;
import appeng.items.misc.PaintBallItem;
import appeng.items.storage.StorageCellTooltipComponent;
import appeng.me.helpers.PlayerSource;
import appeng.util.ConfigInventory;

import net.minecraft.core.Holder;
import net.minecraft.core.HolderSet;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.stats.Stats;
import net.minecraft.util.Mth;
import net.minecraft.util.Unit;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.AbstractArrow.Pickup;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.inventory.tooltip.TooltipComponent;
import net.minecraft.world.item.CrossbowItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.ChargedProjectiles;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.item.enchantment.ItemEnchantments;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.event.EventHooks;

import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public class MatterConvergingCrossbowItem extends CrossbowItem implements IAEItemPowerStorage, IBasicCellItem, IUpgradeableItem {

    private static final double MAX_POWER = 200_000.0D;
    private static final double CHARGE_RATE = 200_000.0D;
    private static final double ENERGY_PER_SHOT = 200.0D;
    private static final double DATA_DUST_ENERGY_PER_SHOT = 200_000.0D;
    private static final double DATA_DUST_ENERGY_PER_PERCENT = 25_000.0D;
    private static final int DATA_DUST_BASE_PERCENT = 1;
    private static final int DATA_DUST_MAX_PERCENT = 5;
    private static final double HOMING_ENERGY_MULTIPLIER = 5.0D;
    private static final float PROJECTILE_SPEED = 3.15F;
    private static final float SPEED_CARD_PROJECTILE_SPEED_BONUS = 1.0F;
    private static final int BYTES = 512;
    private static final int BYTES_PER_TYPE = 8;
    private static final int TOTAL_TYPES = 1;
    private static final int CHARGE_DURATION_TICKS = 20;
    private static final int MAX_UPGRADES = 6;
    private static final int MAX_SPEED_UPGRADES = 4;
    private static final double SPECIAL_LIGHT_SABER_ENERGY = 20_000.0D;
    private static final long MAX_STORED_DATA = 512L;
    private static final String TAG_STORED_DATA = "StoredData";

    private boolean startSoundPlayed = false;
    private boolean midLoadSoundPlayed = false;

    public MatterConvergingCrossbowItem(Item.Properties properties) {
        super(properties.stacksTo(1));
    }

    public static MatterConvergingCrossbowMode mode(ItemStack stack) {
        return MatterConvergingCrossbowMode.fromId(stack.getOrDefault(DEDataComponents.MATTER_CONVERGING_CROSSBOW_MODE.get(), MatterConvergingCrossbowMode.GRENADE.id()));
    }

    public static boolean isCannon(ItemStack stack) {
        return stack.getItem() instanceof MatterConvergingCrossbowItem && mode(stack) != MatterConvergingCrossbowMode.CROSSBOW;
    }

    public boolean selectCannonAmmo(ItemStack weaponStack, ResourceLocation itemId) {
        Item item = BuiltInRegistries.ITEM.get(itemId);
        ItemStack candidate = new ItemStack(item);
        if (item == Items.AIR || !this.isSupportedAmmo(weaponStack, candidate)) return false;
        StorageCell inventory = StorageCells.getCellInventory(weaponStack, (ISaveProvider) null);
        if (inventory == null || inventory.getAvailableStacks().get(AEItemKey.of(item)) <= 0) return false;
        weaponStack.set(DEDataComponents.MATTER_CONVERGING_CROSSBOW_SELECTED_AMMO.get(), itemId);
        return true;
    }

    /** Server entry for a left-button press. No ammo or energy is spent until release. */
    public void beginCannonCharge(Player player, InteractionHand hand, ItemStack stack) {
        if (player.level().isClientSide || !isCannon(stack) || !player.isAlive() || player.isSpectator() || player.isUsingItem() || player.containerMenu != player.inventoryMenu || stack.has(DEDataComponents.CANNON_CHARGE.get())) return;
        if (!this.isChargedAmmoSupported(stack)) return;
        if (!isCharged(stack)) {
            if (!this.hasAmmo(stack)) this.tryStoreAmmoFromPlayer(stack, player);
            if (!this.hasAmmo(stack) || this.getAECurrentPower(stack) < this.getEnergyPerShot(stack, this.peekAmmo(stack))) return;
        }
        stack.set(DEDataComponents.CANNON_CHARGE.get(), new CannonCharge(player.level().getGameTime(), getChargeDuration(stack, player),
                mode(stack), hand, player.getUUID(), player.level().dimension().location()));
    }

    /** Server release consumes the charge exactly once; packets contain no client-chosen charge/damage value. */
    public void releaseCannonCharge(Player player, InteractionHand hand, ItemStack stack, Vec3 muzzleOffset, Vec3 direction) {
        if (!(player.level() instanceof ServerLevel level)) return;
        CannonCharge charge = stack.remove(DEDataComponents.CANNON_CHARGE.get());
        if (charge == null || !charge.belongsTo(player, hand, mode(stack)) || player.isSpectator() || player.containerMenu != player.inventoryMenu || player.getItemInHand(hand) != stack || !CannonBallistics.validAim(muzzleOffset, direction, player.getViewVector(1.0F))) return;
        float fraction = charge.progress(level.getGameTime());
        if (fraction <= 0.0F || player.getCooldowns().isOnCooldown(this)) return;
        Vec3 muzzle = player.getEyePosition().add(muzzleOffset);
        if (level.clip(new ClipContext(player.getEyePosition(), muzzle, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, player)).getType() != HitResult.Type.MISS) return;
        if (!this.isChargedAmmoSupported(stack) || !isCharged(stack) && !this.tryLoadProjectile(player, stack)) return;
        ChargedProjectiles loaded = stack.set(DataComponents.CHARGED_PROJECTILES, ChargedProjectiles.EMPTY);
        if (loaded == null || loaded.isEmpty()) return;
        for (ItemStack ammo : loaded.getItems()) {
            float ammoSpeed = this.getProjectileSpeed(stack, ammo);
            CannonShot shot = new CannonShot(charge.mode(), fraction, ammoSpeed);
            Projectile projectile = this.createProjectile(level, player, stack, ammo, false);
            if (projectile instanceof MatterConvergingBoltEntity bolt) bolt.configureCannonShot(shot);
            else if (projectile instanceof ThrownLightSaberEntity saber) saber.configureCannonShot(shot);
            else throw new IllegalStateException("Unsupported cannon projectile: " + projectile.getType());
            Vec3 velocity = CannonBallistics.launchVelocity(charge.mode(), fraction, direction, ammoSpeed);
            projectile.setPos(muzzle);
            projectile.shoot(velocity.x, velocity.y, velocity.z, (float) velocity.length(), 0.0F);
            projectile.setDeltaMovement(velocity);
            level.addFreshEntity(projectile);
        }
        stack.set(DEDataComponents.CANNON_SHOT_SEQUENCE.get(), stack.getOrDefault(DEDataComponents.CANNON_SHOT_SEQUENCE.get(), 0) + 1);
        player.getCooldowns().addCooldown(this, 3);
        player.awardStat(Stats.ITEM_USED.get(this));
        level.playSound(null, muzzle.x, muzzle.y, muzzle.z, SoundEvents.CROSSBOW_SHOOT, SoundSource.PLAYERS, 1.0F,
                charge.mode() == MatterConvergingCrossbowMode.RAIL ? 1.4F : 0.7F);
    }

    /** Shared with the preview; loading uses the same next/previously charged ammunition. */
    public float cannonAmmoSpeed(ItemStack stack) {
        ChargedProjectiles loaded = stack.getOrDefault(DataComponents.CHARGED_PROJECTILES, ChargedProjectiles.EMPTY);
        return this.getProjectileSpeed(stack, loaded.isEmpty() ? this.peekAmmo(stack) : loaded.getItems().getFirst());
    }

    public boolean cannonUsesSaberAmmo(ItemStack stack) {
        ChargedProjectiles loaded = stack.getOrDefault(DataComponents.CHARGED_PROJECTILES, ChargedProjectiles.EMPTY);
        return this.isDataDustAmmo(loaded.isEmpty() ? this.peekAmmo(stack) : loaded.getItems().getFirst());
    }

    @Override
    public void inventoryTick(ItemStack stack, Level level, Entity entity, int slot, boolean selected) {
        super.inventoryTick(stack, level, entity, slot, selected);
        if (!level.isClientSide) {
            CannonCharge charge = stack.get(DEDataComponents.CANNON_CHARGE.get());
            if (charge != null && (!(entity instanceof Player player) || !charge.belongsTo(player, charge.hand(), mode(stack)) || player.getItemInHand(charge.hand()) != stack || player.containerMenu != player.inventoryMenu)) {
                stack.remove(DEDataComponents.CANNON_CHARGE.get());
            }
        }
    }

    @Override
    public boolean supportsEnchantment(ItemStack stack, Holder<Enchantment> enchantment) {
        if (this.isBlockedBowEnchantment(enchantment)) {
            return false;
        }
        return enchantment.value().isSupportedItem(stack);
    }

    @Override
    public boolean isPrimaryItemFor(ItemStack stack, Holder<Enchantment> enchantment) {
        if (stack.is(Items.BOOK)) {
            return true;
        }

        Optional<HolderSet<Item>> primaryItems = enchantment.value().definition().primaryItems();
        return this.supportsEnchantment(stack, enchantment) && (primaryItems.isEmpty() || stack.is(primaryItems.get()));
    }

    @Override
    public boolean isBookEnchantable(ItemStack stack, ItemStack book) {
        ItemEnchantments storedEnchantments = book.getOrDefault(DataComponents.STORED_ENCHANTMENTS, ItemEnchantments.EMPTY);
        for (Holder<Enchantment> enchantment : storedEnchantments.keySet()) {
            if (this.isBlockedBowEnchantment(enchantment)) {
                return false;
            }
        }
        return super.isBookEnchantable(stack, book);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (player.isShiftKeyDown()) {
            if (!level.isClientSide) {
                player.openMenu(new SimpleMenuProvider((id, inventory, ignoredPlayer) -> new MatterConvergingCrossbowConfigMenu(
                        DEMenus.MATTER_CONVERGING_CROSSBOW_CONFIG.get(), id, inventory, hand),
                        Component.translatable("screen.data_energistics.dark_string_data_settlement_tool")));
            }
            return InteractionResultHolder.sidedSuccess(stack, level.isClientSide);
        }
        if (isCannon(stack)) return InteractionResultHolder.pass(stack);
        ChargedProjectiles charged = stack.getOrDefault(DataComponents.CHARGED_PROJECTILES, ChargedProjectiles.EMPTY);
        if (!charged.isEmpty()) {
            if (!this.isChargedAmmoSupported(stack)) return InteractionResultHolder.fail(stack);
            this.performShooting(level, player, hand, stack, this.getProjectileSpeed(stack, charged.getItems().getFirst()), 1.0F, null);
            return InteractionResultHolder.consume(stack);
        }

        if (!this.hasAmmo(stack)) {
            this.tryStoreAmmoFromPlayer(stack, player);
        }

        ItemStack nextAmmo = this.peekAmmo(stack);
        if (this.hasAmmo(stack) && this.getAECurrentPower(stack) >= this.getEnergyPerShot(stack, nextAmmo)) {
            this.startSoundPlayed = false;
            this.midLoadSoundPlayed = false;
            player.startUsingItem(hand);
            return InteractionResultHolder.consume(stack);
        }

        return InteractionResultHolder.fail(stack);
    }

    @Override
    public void releaseUsing(ItemStack stack, Level level, LivingEntity entity, int timeLeft) {
        if (isCannon(stack)) return;
        if (this.hasMaxSpeedCards(stack)) {
            return;
        }
        int usedTicks = this.getUseDuration(stack, entity) - timeLeft;
        float progress = getPowerForTime(usedTicks, stack, entity);
        if (progress >= 1.0F && !isCharged(stack) && this.tryLoadProjectile(entity, stack)) {
            level.playSound(null, entity.getX(), entity.getY(), entity.getZ(), SoundEvents.CROSSBOW_LOADING_END,
                    entity.getSoundSource(), 1.0F, 1.0F / (level.getRandom().nextFloat() * 0.5F + 1.0F) + 0.2F);
        }
    }

    @Override
    public void onUseTick(Level level, LivingEntity livingEntity, ItemStack stack, int remainingUseTicks) {
        if (isCannon(stack)) return;
        if (!level.isClientSide) {
            float progress = (float) (stack.getUseDuration(livingEntity) - remainingUseTicks) / (float) getChargeDuration(stack, livingEntity);
            if (this.hasMaxSpeedCards(stack) && progress >= 1.0F && !isCharged(stack) && this.tryLoadProjectile(livingEntity, stack)) {
                ItemStack loadedAmmo = stack.getOrDefault(DataComponents.CHARGED_PROJECTILES, ChargedProjectiles.EMPTY).getItems().getFirst();
                this.performShooting(level, livingEntity, livingEntity.getUsedItemHand(), stack,
                        this.getProjectileSpeed(stack, loadedAmmo), 1.0F, null);
                livingEntity.stopUsingItem();
                return;
            }
            if (progress < 0.2F) {
                this.startSoundPlayed = false;
                this.midLoadSoundPlayed = false;
            }

            if (progress >= 0.2F && !this.startSoundPlayed) {
                this.startSoundPlayed = true;
                level.playSound(null, livingEntity.getX(), livingEntity.getY(), livingEntity.getZ(),
                        SoundEvents.CROSSBOW_LOADING_START, SoundSource.PLAYERS, 0.5F, 1.0F);
            }

            if (progress >= 0.5F && !this.midLoadSoundPlayed) {
                this.midLoadSoundPlayed = true;
                level.playSound(null, livingEntity.getX(), livingEntity.getY(), livingEntity.getZ(),
                        SoundEvents.CROSSBOW_LOADING_MIDDLE, SoundSource.PLAYERS, 0.5F, 1.0F);
            }
        }
    }

    @Override
    public int getUseDuration(ItemStack stack, LivingEntity entity) {
        return getChargeDuration(stack, entity) + 3;
    }

    public static int getChargeDuration(ItemStack stack, LivingEntity entity) {
        float baseSeconds = CHARGE_DURATION_TICKS / 20.0F;
        float seconds = EnchantmentHelper.modifyCrossbowChargingTime(stack, entity, baseSeconds);
        return Math.max(1, Mth.floor(seconds * 20.0F));
    }

    private static float getPowerForTime(int usedTicks, ItemStack stack, LivingEntity entity) {
        float progress = (float) usedTicks / (float) getChargeDuration(stack, entity);
        return Math.min(progress, 1.0F);
    }

    @Override
    public Component getName(ItemStack stack) {
        return Component.translatable("item.data_energistics.dark_string_data_settlement_tool.mode." + mode(stack).nameKey());
    }

    @Override
    public void appendHoverText(ItemStack stack, Item.TooltipContext context, List<Component> lines,
                                TooltipFlag tooltipFlag) {
        if (isCannon(stack)) {
            lines.add(Component.translatable("tooltip.data_energistics.cannon." + (mode(stack) == MatterConvergingCrossbowMode.GRENADE ? "grenade" : "rail"), Component.keybind("key.attack")));
        }
        lines.add(Tooltips.energyStorageComponent(this.getAECurrentPower(stack), this.getAEMaxPower(stack)));
        this.addCellInformationToTooltip(stack, lines);
        lines.add(Component.translatable("item.data_energistics.dark_string_data_settlement_tool.projectile",
                this.getDisplayedAmmoName(stack)));
    }

    @Override
    public Optional<TooltipComponent> getTooltipImage(ItemStack stack) {
        StorageCell cellInventory = StorageCells.getCellInventory(stack, (ISaveProvider) null);
        List<GenericStack> content = new ArrayList<>();
        if (cellInventory != null) {
            for (var entry : cellInventory.getAvailableStacks()) {
                if (entry.getLongValue() > 0) {
                    content.add(new GenericStack(entry.getKey(), entry.getLongValue()));
                }
            }
        }
        return Optional.of(new StorageCellTooltipComponent(
                collectUpgradeItems(this.getUpgrades(stack)),
                content,
                false,
                false));
    }

    @Override
    public boolean isBarVisible(ItemStack stack) {
        return true;
    }

    @Override
    public int getBarWidth(ItemStack stack) {
        return Mth.clamp((int) Math.round(this.getAECurrentPower(stack) / this.getAEMaxPower(stack) * 13.0D), 0, 13);
    }

    @Override
    public int getBarColor(ItemStack stack) {
        return Mth.hsvToRgb(1.0F / 3.0F, 1.0F, 1.0F);
    }

    @Override
    public void performShooting(Level level, LivingEntity shooter, InteractionHand hand, ItemStack stack, float power,
                                float inaccuracy, @Nullable LivingEntity target) {
        if (!(level instanceof ServerLevel serverLevel)) {
            return;
        }

        if (!this.isChargedAmmoSupported(stack)) return;

        if (shooter instanceof Player player && EventHooks.onArrowLoose(stack, level, player, 1, true) < 0) {
            return;
        }

        ChargedProjectiles charged = stack.set(DataComponents.CHARGED_PROJECTILES, ChargedProjectiles.EMPTY);
        if (charged == null || charged.isEmpty()) {
            return;
        }

        float projectileSpeed = this.getProjectileSpeed(stack, charged.getItems().getFirst());
        this.shoot(serverLevel, shooter, hand, stack, charged.getItems(), projectileSpeed, inaccuracy, shooter instanceof Player,
                target);
        if (shooter instanceof Player player) {
            player.awardStat(Stats.ITEM_USED.get(this));
        }
    }

    private boolean tryLoadProjectile(LivingEntity shooter, ItemStack crossbow) {
        if (!(shooter instanceof Player player)) {
            return false;
        }
        ItemStack nextAmmo = this.peekAmmo(crossbow);
        double energyPerShot = this.getEnergyPerShot(crossbow, nextAmmo);
        if (this.getAECurrentPower(crossbow) < energyPerShot) {
            return false;
        }

        List<ItemStack> ammo = this.extractAmmoForLoading(crossbow, player);
        if (ammo.isEmpty()) {
            return false;
        }

        this.extractAEPower(crossbow, energyPerShot, Actionable.MODULATE);
        crossbow.set(DataComponents.CHARGED_PROJECTILES, ChargedProjectiles.of(ammo));
        return true;
    }

    @Override
    protected Projectile createProjectile(Level level, LivingEntity shooter, ItemStack weaponStack, ItemStack ammoStack,
                                          boolean isCrit) {
        if (this.isDataDustAmmo(ammoStack)) {
            ItemStack thrownStack = new ItemStack(DEItems.DATA_LIGHT_SABER.get());
            ThrownLightSaberEntity projectile = new ThrownLightSaberEntity(level, shooter, thrownStack);
            projectile.pickup = Pickup.ALLOWED;
            projectile.setWeaponStack(weaponStack);
            projectile.setDataDustDamageRatio(this.getDataDustDamagePercentForChargedShot(weaponStack) / 100.0F);
            projectile.setHoming(this.hasRedstoneCard(weaponStack));
            return projectile;
        }

        MatterConvergingBoltEntity projectile = new MatterConvergingBoltEntity(level, shooter, ammoStack.copyWithCount(1));
        projectile.setWeaponStack(weaponStack);
        projectile.setCritical(isCrit);
        projectile.setHoming(this.hasRedstoneCard(weaponStack));
        if (level instanceof ServerLevel serverLevel) {
            projectile.setPierceLevel(EnchantmentHelper.getPiercingCount(serverLevel, weaponStack, ammoStack));
        }
        return projectile;
    }

    private ItemStack extractAmmo(ItemStack weaponStack, Player player) {
        StorageCell inventory = StorageCells.getCellInventory(weaponStack, (ISaveProvider) null);
        if (inventory == null) {
            return ItemStack.EMPTY;
        }

        var entry = inventory.getAvailableStacks().getFirstEntry(AEItemKey.class);
        if (entry == null || !(entry.getKey() instanceof AEItemKey itemKey)) {
            return ItemStack.EMPTY;
        }
        ItemStack ammo = itemKey.toStack(1);
        if (!this.isSupportedAmmo(weaponStack, ammo)) {
            return ItemStack.EMPTY;
        }

        long extracted = inventory.extract(itemKey, 1, Actionable.MODULATE, new PlayerSource(player));
        return extracted > 0 ? ammo : ItemStack.EMPTY;
    }

    private boolean isBlockedBowEnchantment(Holder<Enchantment> enchantment) {
        return enchantment.is(Enchantments.FLAME) || enchantment.is(Enchantments.INFINITY);
    }

    private List<ItemStack> extractAmmoForLoading(ItemStack weaponStack, Player player) {
        ItemStack ammo = this.extractAmmo(weaponStack, player);
        if (ammo.isEmpty()) {
            return List.of();
        }
        if (this.isDataDustAmmo(ammo)) {
            this.applyDataDustShotData(weaponStack, ammo);
            return List.of(ammo);
        }

        int projectileCount = 1;
        if (player.level() instanceof ServerLevel serverLevel) {
            projectileCount = EnchantmentHelper.processProjectileCount(serverLevel, weaponStack, player, 1);
        }

        List<ItemStack> projectiles = new ArrayList<>(Math.max(projectileCount, 1));
        projectiles.add(ammo);
        for (int i = 1; i < projectileCount; i++) {
            ItemStack duplicate = ammo.copyWithCount(1);
            duplicate.set(DataComponents.INTANGIBLE_PROJECTILE, Unit.INSTANCE);
            projectiles.add(duplicate);
        }

        return projectiles;
    }

    private boolean hasAmmo(ItemStack weaponStack) {
        StorageCell inventory = StorageCells.getCellInventory(weaponStack, (ISaveProvider) null);
        if (inventory == null) {
            return false;
        }
        var firstEntry = inventory.getAvailableStacks().getFirstEntry(AEItemKey.class);
        return firstEntry != null && firstEntry.getLongValue() > 0 && firstEntry.getKey() instanceof AEItemKey itemKey && this.isSupportedAmmo(weaponStack, itemKey.toStack(1));
    }

    private boolean tryStoreAmmoFromPlayer(ItemStack weaponStack, Player player) {
        StorageCell inventory = StorageCells.getCellInventory(weaponStack, (ISaveProvider) null);
        if (inventory == null) {
            return false;
        }

        var playerInventory = player.getInventory();
        for (int i = 0; i < playerInventory.getContainerSize(); i++) {
            ItemStack candidate = playerInventory.getItem(i);
            if (candidate.isEmpty()) {
                continue;
            }

            AEItemKey itemKey = AEItemKey.of(candidate);
            if (itemKey == null || this.isBlackListed(weaponStack, itemKey)) {
                continue;
            }

            long inserted = inventory.insert(itemKey, 1, Actionable.MODULATE, new PlayerSource(player));
            if (inserted > 0) {
                candidate.shrink((int) inserted);
                return true;
            }
        }

        return false;
    }

    private Component getDisplayedAmmoName(ItemStack weaponStack) {
        StorageCell inventory = StorageCells.getCellInventory(weaponStack, (ISaveProvider) null);
        if (inventory == null) {
            return Component.translatable("item.data_energistics.dark_string_data_settlement_tool.projectile.none");
        }

        var firstEntry = inventory.getAvailableStacks().getFirstEntry(AEItemKey.class);
        if (firstEntry == null || !(firstEntry.getKey() instanceof AEItemKey itemKey) || firstEntry.getLongValue() <= 0) {
            return Component.translatable("item.data_energistics.dark_string_data_settlement_tool.projectile.none");
        }
        ItemStack ammo = itemKey.toStack(1);
        if (!this.isSupportedAmmo(weaponStack, ammo)) {
            return Component.translatable("item.data_energistics.dark_string_data_settlement_tool.projectile.none");
        }
        return ammo.getHoverName();
    }

    @Override
    public double injectAEPower(ItemStack stack, double amount, Actionable mode) {
        double maxStorage = this.getAEMaxPower(stack);
        double currentStorage = this.getAECurrentPower(stack);
        double required = maxStorage - currentStorage;
        double overflow = Math.max(0.0D, Math.min(amount - required, amount));
        if (mode == Actionable.MODULATE) {
            double toAdd = Math.min(amount, required);
            this.setAECurrentPower(stack, currentStorage + toAdd);
        }
        return overflow;
    }

    @Override
    public double extractAEPower(ItemStack stack, double amount, Actionable mode) {
        double currentStorage = this.getAECurrentPower(stack);
        double fulfillable = Math.min(amount, currentStorage);
        if (mode == Actionable.MODULATE) {
            this.setAECurrentPower(stack, currentStorage - fulfillable);
        }
        return fulfillable;
    }

    @Override
    public double getAEMaxPower(ItemStack stack) {
        return MAX_POWER * getEnergyCapacityMultiplier(stack);
    }

    @Override
    public double getAECurrentPower(ItemStack stack) {
        return stack.getOrDefault(AEComponents.STORED_ENERGY, 0.0D);
    }

    private void setAECurrentPower(ItemStack stack, double power) {
        if (power < 1.0E-4D) {
            stack.remove(AEComponents.STORED_ENERGY);
        } else {
            stack.set(AEComponents.STORED_ENERGY, power);
        }
    }

    @Override
    public AccessRestriction getPowerFlow(ItemStack stack) {
        return AccessRestriction.WRITE;
    }

    @Override
    public double getChargeRate(ItemStack stack) {
        return CHARGE_RATE + CHARGE_RATE * Upgrades.getEnergyCardMultiplier(this.getUpgrades(stack));
    }

    @Override
    public AEKeyType getKeyType() {
        return AEKeyType.items();
    }

    @Override
    public int getBytes(ItemStack stack) {
        return BYTES;
    }

    @Override
    public int getBytesPerType(ItemStack stack) {
        return BYTES_PER_TYPE;
    }

    @Override
    public int getTotalTypes(ItemStack stack) {
        return TOTAL_TYPES;
    }

    @Override
    public boolean isBlackListed(ItemStack stack, AEKey requestedAddition) {
        if (!(requestedAddition instanceof AEItemKey itemKey)) {
            return true;
        }

        Item item = itemKey.getItem();
        return item != AEItems.MATTER_BALL.asItem() && item != AEItems.SINGULARITY.asItem() && !(item instanceof PaintBallItem) && item != DEItems.DATA_LIGHT_SABER.get() && !(mode(stack) == MatterConvergingCrossbowMode.GRENADE && GrenadePayload.isExplosive(itemKey.toStack(1)));
    }

    @Override
    public double getIdleDrain() {
        return 0.0D;
    }

    @Override
    public ConfigInventory getConfigInventory(ItemStack stack) {
        return ConfigInventory.emptyTypes();
    }

    @Override
    public IUpgradeInventory getUpgrades(ItemStack stack) {
        return UpgradeInventories.forItem(stack, MAX_UPGRADES, this::onUpgradesChanged);
    }

    private void onUpgradesChanged(ItemStack stack, IUpgradeInventory upgrades) {
        double maxPower = this.getAEMaxPower(stack);
        if (this.getAECurrentPower(stack) > maxPower) {
            this.setAECurrentPower(stack, maxPower);
        }
        this.refreshChargedDataDustShotData(stack);
    }

    private static int getEnergyCapacityMultiplier(ItemStack stack) {
        return 1 + Upgrades.getEnergyCardMultiplier(UpgradeInventories.forItem(stack, MAX_UPGRADES)) * 8;
    }

    private double getEnergyPerShot(ItemStack stack, ItemStack ammoStack) {
        if (this.isDataDustAmmo(ammoStack)) {
            return this.getDataDustEnergyPerShot(stack);
        }
        return mode(stack) == MatterConvergingCrossbowMode.CROSSBOW && this.hasRedstoneCard(stack) ? ENERGY_PER_SHOT * HOMING_ENERGY_MULTIPLIER : ENERGY_PER_SHOT;
    }

    private boolean hasRedstoneCard(ItemStack stack) {
        return this.getUpgrades(stack).getInstalledUpgrades(DEItems.REDSTONE_TUNING_CARD.get()) > 0;
    }

    private boolean hasMaxSpeedCards(ItemStack stack) {
        return this.getUpgrades(stack).getInstalledUpgrades(AEItems.SPEED_CARD) >= MAX_SPEED_UPGRADES;
    }

    private float getProjectileSpeed(ItemStack stack, ItemStack ammoStack) {
        int speedCards = Math.max(0, this.getUpgrades(stack).getInstalledUpgrades(AEItems.SPEED_CARD));
        if (this.isDataDustAmmo(ammoStack)) {
            return PROJECTILE_SPEED * 1.5F + speedCards * SPEED_CARD_PROJECTILE_SPEED_BONUS;
        }
        return PROJECTILE_SPEED + speedCards * SPEED_CARD_PROJECTILE_SPEED_BONUS;
    }

    private ItemStack peekAmmo(ItemStack weaponStack) {
        StorageCell inventory = StorageCells.getCellInventory(weaponStack, (ISaveProvider) null);
        if (inventory == null) {
            return ItemStack.EMPTY;
        }

        ResourceLocation selected = weaponStack.get(DEDataComponents.MATTER_CONVERGING_CROSSBOW_SELECTED_AMMO.get());
        if (selected != null) {
            Item selectedItem = BuiltInRegistries.ITEM.get(selected);
            AEItemKey selectedKey = AEItemKey.of(selectedItem);
            if (selectedItem != Items.AIR && inventory.getAvailableStacks().get(selectedKey) > 0) return selectedKey.toStack(1);
        }
        var firstEntry = inventory.getAvailableStacks().getFirstEntry(AEItemKey.class);
        if (firstEntry == null || !(firstEntry.getKey() instanceof AEItemKey itemKey) || firstEntry.getLongValue() <= 0) {
            return ItemStack.EMPTY;
        }

        return itemKey.toStack(1);
    }

    public static boolean isSpecialLightSaberAmmo(ItemStack ammoStack) {
        return !ammoStack.isEmpty() && ammoStack.is(DEItems.DATA_LIGHT_SABER.get()) && Math.abs(ammoStack.getOrDefault(AEComponents.STORED_ENERGY, 0.0D) - SPECIAL_LIGHT_SABER_ENERGY) < 1.0E-4D;
    }

    private boolean isDataDustAmmo(ItemStack ammoStack) {
        return isSpecialLightSaberAmmo(ammoStack);
    }

    private boolean isChargedAmmoSupported(ItemStack weaponStack) {
        return weaponStack.getOrDefault(DataComponents.CHARGED_PROJECTILES, ChargedProjectiles.EMPTY).getItems()
                .stream().allMatch(ammo -> this.isSupportedAmmo(weaponStack, ammo));
    }

    private boolean isSupportedAmmo(ItemStack weaponStack, ItemStack ammoStack) {
        if (ammoStack.isEmpty()) {
            return false;
        }
        Item item = ammoStack.getItem();
        return item == AEItems.MATTER_BALL.asItem() || item == AEItems.SINGULARITY.asItem() || item instanceof PaintBallItem || this.isDataDustAmmo(ammoStack) || mode(weaponStack) == MatterConvergingCrossbowMode.GRENADE && GrenadePayload.isExplosive(ammoStack);
    }

    private double getDataDustEnergyPerShot(ItemStack stack) {
        return DATA_DUST_ENERGY_PER_SHOT + this.getDataDustExtraEnergyForShot(stack);
    }

    private int getDataDustDamagePercentForShot(ItemStack stack) {
        double extraPower = this.getDataDustExtraEnergyForShot(stack);
        int extraPercent = (int) Math.floor(extraPower / DATA_DUST_ENERGY_PER_PERCENT);
        return Math.min(DATA_DUST_MAX_PERCENT, DATA_DUST_BASE_PERCENT + extraPercent);
    }

    private void applyDataDustShotData(ItemStack weaponStack, ItemStack ammoStack) {
        float ratio = this.getDataDustDamagePercentForChargedShot(weaponStack) / 100.0F;
        ammoStack.set(DEDataComponents.MATTER_CONVERGING_BOLT_DAMAGE_RATIO.get(), ratio);
    }

    private int getDataDustDamagePercentForChargedShot(ItemStack stack) {
        return this.getDataDustDamagePercentForShot(stack);
    }

    private double getDataDustExtraEnergyFromCards(ItemStack stack) {
        return Math.max(0.0D, this.getAEMaxPower(stack) - MAX_POWER);
    }

    private double getDataDustExtraEnergyForShot(ItemStack stack) {
        double maxExtraEnergy = (DATA_DUST_MAX_PERCENT - DATA_DUST_BASE_PERCENT) * DATA_DUST_ENERGY_PER_PERCENT;
        return Math.min(this.getDataDustExtraEnergyFromCards(stack), maxExtraEnergy);
    }

    private void refreshChargedDataDustShotData(ItemStack stack) {
        ChargedProjectiles charged = stack.getOrDefault(DataComponents.CHARGED_PROJECTILES, ChargedProjectiles.EMPTY);
        if (charged.isEmpty()) {
            return;
        }

        List<ItemStack> updatedProjectiles = new ArrayList<>(charged.getItems().size());
        boolean changed = false;
        for (ItemStack projectile : charged.getItems()) {
            ItemStack updatedProjectile = projectile.copy();
            if (this.isDataDustAmmo(updatedProjectile)) {
                this.applyDataDustShotData(stack, updatedProjectile);
                changed = true;
            }
            updatedProjectiles.add(updatedProjectile);
        }

        if (changed) {
            stack.set(DataComponents.CHARGED_PROJECTILES, ChargedProjectiles.of(updatedProjectiles));
        }
    }

    private long getStoredDataAmount(ItemStack weaponStack) {
        Long stored = weaponStack.get(DEDataComponents.MATTER_CONVERGING_CROSSBOW_STORED_DATA.get());
        if (stored != null) {
            return Math.max(0L, stored);
        }
        CompoundTag tag = weaponStack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag();
        return Math.max(0L, tag.getLong(TAG_STORED_DATA));
    }

    public long insertStoredData(ItemStack weaponStack, long amount) {
        if (amount <= 0L) {
            return 0L;
        }
        long current = this.getStoredDataAmount(weaponStack);
        long accepted = Math.min(amount, MAX_STORED_DATA - current);
        if (accepted <= 0L) {
            return 0L;
        }
        long updated = current + accepted;
        weaponStack.set(DEDataComponents.MATTER_CONVERGING_CROSSBOW_STORED_DATA.get(), updated);
        return accepted;
    }

    @Override
    public FuzzyMode getFuzzyMode(ItemStack stack) {
        return FuzzyMode.IGNORE_ALL;
    }

    @Override
    public void setFuzzyMode(ItemStack stack, FuzzyMode fuzzyMode) {}

    private static List<ItemStack> collectUpgradeItems(IUpgradeInventory upgrades) {
        List<ItemStack> upgradeItems = new ArrayList<>(upgrades.size());
        for (int i = 0; i < upgrades.size(); i++) {
            ItemStack upgrade = upgrades.getStackInSlot(i);
            if (!upgrade.isEmpty()) {
                upgradeItems.add(upgrade.copy());
            }
        }
        return upgradeItems;
    }
}

package com.fish_dan_.data_energistics.item.powered;

import com.fish_dan_.data_energistics.Data_Energistics;
import com.fish_dan_.data_energistics.ae2.key.DataFlowKey;
import com.fish_dan_.data_energistics.effect.DataDisorderEffectLogic;
import com.fish_dan_.data_energistics.entity.LightBladeChargeEntity;
import com.fish_dan_.data_energistics.entity.ThrownLightSaberEntity;
import com.fish_dan_.data_energistics.registry.DEItems;
import com.fish_dan_.data_energistics.util.LightSaberColorData;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Holder;
import net.minecraft.core.Position;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.stats.Stats;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.entity.SlotAccess;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.AttributeModifier.Operation;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.AbstractArrow.Pickup;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.inventory.ClickAction;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.inventory.tooltip.TooltipComponent;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.DyeItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ProjectileItem;
import net.minecraft.world.item.SwordItem;
import net.minecraft.world.item.Tier;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.UseAnim;
import net.minecraft.world.item.component.ItemAttributeModifiers;
import net.minecraft.world.item.component.Tool;
import net.minecraft.world.item.enchantment.EnchantmentEffectComponents;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.ItemAbilities;
import net.neoforged.neoforge.common.ItemAbility;

import appeng.api.config.Actionable;
import appeng.api.networking.security.IActionSource;
import appeng.api.storage.StorageCells;
import appeng.api.upgrades.IUpgradeInventory;
import appeng.api.util.AEColor;
import appeng.items.storage.StorageCellTooltipComponent;
import appeng.items.tools.powered.ColorApplicatorItem;
import org.apache.logging.log4j.Logger;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public class PoweredSwordItem extends AbstractPoweredTieredItem implements ProjectileItem, ConditionalDataFlowCellItem {

    private static final Logger LOGGER = Data_Energistics.LOGGER;
    private static final int THROW_THRESHOLD_TIME = 10;
    private static final float THROW_POWER = 2.5F;
    private static final float LIGHT_BLADE_SPEED = 2.8F;
    private static final float LIGHT_BLADE_DAMAGE_RATIO = 5.0F / 6.0F;
    private static final long SWORD_DATA_DISORDER_DATA_FLOW_COST = 20L;
    private static final int DATA_DISORDER_DURATION_TICKS = 20;
    public static final float SANCTIFIER_THROWN_LIGHT_BLADE_SPEED = 2.4F;
    private final boolean throwable;

    public PoweredSwordItem(Tier tier, Properties properties) {
        this(tier, properties, createDefaultSwordTool(), true);
    }

    public PoweredSwordItem(Tier tier, Properties properties, boolean throwable) {
        this(tier, properties, createDefaultSwordTool(), throwable);
    }

    public PoweredSwordItem(Tier tier, Properties properties, Tool toolComponent) {
        this(tier, properties, toolComponent, true);
    }

    public PoweredSwordItem(Tier tier, Properties properties, Tool toolComponent, boolean throwable) {
        super(tier, properties, toolComponent);
        this.throwable = throwable;
    }

    public static Tool createDefaultSwordTool() {
        return SwordItem.createToolProperties();
    }

    public static ItemAttributeModifiers createAttributes(Tier tier, float attackDamage, float attackSpeed) {
        return SwordItem.createAttributes(tier, attackDamage, attackSpeed);
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> lines, TooltipFlag tooltipFlag) {
        super.appendHoverText(stack, context, lines, tooltipFlag);
        this.appendConditionalCellInformationToTooltip(stack, lines);
    }

    @Override
    public Optional<TooltipComponent> getTooltipImage(ItemStack stack) {
        Optional<TooltipComponent> cellTooltip = this.getConditionalCellTooltipImage(stack);
        if (cellTooltip.isPresent()) {
            return cellTooltip;
        }
        var upgrades = this.getUpgrades(stack);
        List<ItemStack> upgradeItems = collectUpgradeItems(upgrades);
        if (upgradeItems.isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(new StorageCellTooltipComponent(upgradeItems, List.of(), false, false));
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
    public boolean isValidRepairItem(ItemStack stack, ItemStack repairCandidate) {
        return false;
    }

    @Override
    public boolean canPerformAction(ItemStack stack, ItemAbility itemAbility) {
        return ItemAbilities.DEFAULT_SWORD_ACTIONS.contains(itemAbility) || this.throwable && ItemAbilities.DEFAULT_TRIDENT_ACTIONS.contains(itemAbility);
    }

    @Override
    public boolean canAttackBlock(BlockState state, Level level,
                                  BlockPos pos, Player player) {
        return !player.isCreative();
    }

    @Override
    public UseAnim getUseAnimation(ItemStack stack) {
        return this.throwable ? UseAnim.SPEAR : UseAnim.NONE;
    }

    @Override
    public int getUseDuration(ItemStack stack, LivingEntity entity) {
        return this.throwable ? 72000 : 0;
    }

    @Override
    public boolean useOnRelease(ItemStack stack) {
        return this.throwable;
    }

    @Override
    public boolean hurtEnemy(ItemStack stack, LivingEntity target, LivingEntity attacker) {
        if (!this.hasSufficientEnergy(stack)) {
            return false;
        }
        boolean result = true;
        if (result && !attacker.level().isClientSide) {
            this.tryApplyDataDisorder(stack, target, attacker);
            if (this.canFireLightBlade(stack)) {
                this.fireLightBlade((Level) attacker.level(), attacker, stack);
                this.consumeActionEnergy(stack);
            }
            this.consumeActionEnergy(stack);
        }
        return result;
    }

    @Override
    public void postHurtEnemy(ItemStack stack, LivingEntity target, LivingEntity attacker) {}

    @Override
    public boolean onEntitySwing(ItemStack stack, LivingEntity entity, InteractionHand hand) {
        if (hand != InteractionHand.MAIN_HAND || entity.level().isClientSide) {
            return false;
        }
        if (!(entity instanceof Player player)) {
            return false;
        }
        if (!this.canFireLightBlade(stack)) {
            return false;
        }
        if (player.getAttackStrengthScale(0.5F) < 1.0F) {
            return false;
        }

        this.fireLightBlade((Level) entity.level(), entity, stack);
        this.consumeActionEnergy(stack);
        player.resetAttackStrengthTicker();
        return false;
    }

    @Override
    public boolean overrideOtherStackedOnMe(ItemStack stack, ItemStack carriedStack, Slot slot,
                                            ClickAction clickAction, Player player, SlotAccess carriedSlotAccess) {
        if (clickAction != ClickAction.SECONDARY || !LightSaberColorData.isColorableLightSaber(stack)) {
            return false;
        }

        DyeColor color = LightSaberColorData.getColorFromIngredient(carriedStack);
        if (color == null || color == LightSaberColorData.getStoredColor(stack)) {
            return false;
        }

        slot.set(LightSaberColorData.withColor(stack, color));

        if (player.getAbilities().instabuild) {
            return true;
        }

        if (carriedStack.getItem() instanceof ColorApplicatorItem colorApplicatorItem) {
            AEColor aeColor = colorApplicatorItem.getActiveColor(carriedStack);
            if (aeColor != null && aeColor != AEColor.TRANSPARENT && aeColor.dye != null) {
                ItemStack updatedApplicator = carriedStack.copy();
                colorApplicatorItem.consumeColor(updatedApplicator, aeColor, false);
                carriedSlotAccess.set(updatedApplicator);
            }
            return true;
        }

        if (carriedStack.getItem() instanceof DyeItem) {
            ItemStack updatedDye = carriedStack.copy();
            updatedDye.shrink(1);
            carriedSlotAccess.set(updatedDye);
            return true;
        }

        return false;
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (!this.throwable) {
            return InteractionResultHolder.pass(stack);
        }
        if (!this.hasSufficientEnergy(stack)) {
            return InteractionResultHolder.fail(stack);
        }
        if (EnchantmentHelper.getTridentSpinAttackStrength(stack, player) > 0.0F && !player.isInWaterOrRain()) {
            return InteractionResultHolder.fail(stack);
        }
        player.startUsingItem(hand);
        return InteractionResultHolder.consume(stack);
    }

    @Override
    public void releaseUsing(ItemStack stack, Level level, LivingEntity livingEntity, int timeLeft) {
        if (!this.throwable || !(livingEntity instanceof Player player)) {
            return;
        }

        int useTicks = this.getUseDuration(stack, livingEntity) - timeLeft;
        if (useTicks < this.getThrowThresholdTime(stack)) {
            return;
        }

        float spinStrength = EnchantmentHelper.getTridentSpinAttackStrength(stack, player);
        if ((spinStrength > 0.0F && !player.isInWaterOrRain()) || !this.hasSufficientEnergy(stack)) {
            return;
        }

        Holder<SoundEvent> sound = EnchantmentHelper.pickHighestLevel(stack, EnchantmentEffectComponents.TRIDENT_SOUND)
                .orElse(SoundEvents.TRIDENT_THROW);
        if (!level.isClientSide) {
            LOGGER.debug("Light saber releaseUsing: item={}, useTicks={}, spinStrength={}, energy={}",
                    stack.getItem(), useTicks, spinStrength, this.getAECurrentPower(stack));
            if (spinStrength == 0.0F) {
                ItemStack thrownStack = stack.copyWithCount(1);
                this.consumeActionEnergy(stack);
                this.consumeActionEnergy(thrownStack);

                ThrownLightSaberEntity thrownLightSaber = new ThrownLightSaberEntity(level, player, thrownStack);
                thrownLightSaber.setWeaponStack(thrownStack);
                thrownLightSaber.shootFromRotation(player, player.getXRot(), player.getYRot(), 0.0F, THROW_POWER, 1.0F);
                if (player.hasInfiniteMaterials()) {
                    thrownLightSaber.pickup = Pickup.CREATIVE_ONLY;
                }

                boolean added = level.addFreshEntity(thrownLightSaber);
                LOGGER.debug("Light saber projectile spawn: entityType={}, added={}, uuid={}",
                        thrownLightSaber.getType(), added, thrownLightSaber.getUUID());
                level.playSound(null, thrownLightSaber, sound.value(), SoundSource.PLAYERS, 1.0F, 1.0F);
                if (!player.hasInfiniteMaterials()) {
                    player.getInventory().removeItem(stack);
                }
            } else {
                this.consumeActionEnergy(stack);
            }
        }

        player.awardStat(Stats.ITEM_USED.get(this));
        if (spinStrength > 0.0F) {
            float yaw = player.getYRot();
            float pitch = player.getXRot();
            float x = -Mth.sin(yaw * ((float) Math.PI / 180.0F)) * Mth.cos(pitch * ((float) Math.PI / 180.0F));
            float y = -Mth.sin(pitch * ((float) Math.PI / 180.0F));
            float z = Mth.cos(yaw * ((float) Math.PI / 180.0F)) * Mth.cos(pitch * ((float) Math.PI / 180.0F));
            float scale = Mth.sqrt(x * x + y * y + z * z);
            x *= spinStrength / scale;
            y *= spinStrength / scale;
            z *= spinStrength / scale;
            player.push(x, y, z);
            player.startAutoSpinAttack(20, 8.0F, stack);
            if (player.onGround()) {
                player.move(MoverType.SELF, new Vec3(0.0D, 1.1999999F, 0.0D));
            }

            level.playSound(null, player, sound.value(), SoundSource.PLAYERS, 1.0F, 1.0F);
        }
    }

    protected int getThrowThresholdTime(ItemStack stack) {
        return Math.max(4, THROW_THRESHOLD_TIME - this.getSpeedCardCount(stack) * 2);
    }

    private void fireLightBlade(Level level, LivingEntity attacker, ItemStack stack) {
        if (this.getSaberEnergyCardCount(stack) <= 0) {
            return;
        }

        float damage = getLightBladeDamage(stack);
        if (damage <= 0.0F) {
            return;
        }

        LightBladeChargeEntity lightBlade = new LightBladeChargeEntity(
                level,
                attacker,
                getLightBladeColor(level, stack),
                damage);
        lightBlade.setWeaponStack(stack);
        lightBlade.setPos(attacker.getX(), attacker.getEyeY() - 0.15D, attacker.getZ());
        lightBlade.shootFromRotation(attacker, attacker.getXRot(), attacker.getYRot(), 0.0F, LIGHT_BLADE_SPEED, 0.0F);
        level.addFreshEntity(lightBlade);
    }

    private boolean canFireLightBlade(ItemStack stack) {
        return isLightBladeEmitter(stack) && this.getSaberEnergyCardCount(stack) > 0 && this.getAECurrentPower(stack) >= this.getActionEnergyCost(stack) * 2.0D;
    }

    private static boolean isLightBladeEmitter(ItemStack stack) {
        return stack.is(DEItems.DATA_LIGHT_SABER.get()) || stack.is(DEItems.DATA_SANCTIFIER.get());
    }

    @Override
    public boolean hasDataFlowCellSupport(ItemStack stack) {
        return stack.is(DEItems.DATA_CRYSTAL_SWORD.get()) && ConditionalDataFlowCellItem.super.hasDataFlowCellSupport(stack);
    }

    public static float getLightBladeDamage(ItemStack stack) {
        return Math.max(0.0F, (float) (getPanelAttackDamage(stack) * LIGHT_BLADE_DAMAGE_RATIO));
    }

    private static int getLightBladeColor(Level level, ItemStack stack) {
        if (stack.is(DEItems.DATA_SANCTIFIER.get())) {
            return LightSaberColorData.getSanctifierAnimatedColor(level.getGameTime());
        }
        return LightSaberColorData.getBladeColor(stack);
    }

    public static double getPanelAttackDamage(ItemStack stack) {
        final double playerBaseDamage = 1.0D;
        final double[] addValue = { 0.0D };
        final double[] addMultipliedBase = { 0.0D };
        final double[] addMultipliedTotal = { 0.0D };

        stack.forEachModifier(EquipmentSlot.MAINHAND,
                (Holder<Attribute> attribute, AttributeModifier modifier) -> {
                    if (!attribute.equals(Attributes.ATTACK_DAMAGE)) {
                        return;
                    }

                    if (modifier.operation() == Operation.ADD_VALUE) {
                        addValue[0] += modifier.amount();
                    } else if (modifier.operation() == Operation.ADD_MULTIPLIED_BASE) {
                        addMultipliedBase[0] += modifier.amount();
                    } else if (modifier.operation() == Operation.ADD_MULTIPLIED_TOTAL) {
                        addMultipliedTotal[0] += modifier.amount();
                    }
                });

        double panelDamage = playerBaseDamage + addValue[0];
        panelDamage += playerBaseDamage * addMultipliedBase[0];
        panelDamage *= 1.0D + addMultipliedTotal[0];
        return Math.max(0.0D, panelDamage);
    }

    private void tryApplyDataDisorder(ItemStack stack, LivingEntity target, LivingEntity attacker) {
        if (this.getSaberEnergyCardCount(stack) <= 0) {
            return;
        }

        if (!(attacker instanceof Player player) || player.getAttackStrengthScale(0.5F) < 1.0F) {
            return;
        }

        if (stack.is(DEItems.DATA_CRYSTAL_SWORD.get())) {
            this.tryApplyDataCrystalSwordDisorder(stack, target, attacker);
            return;
        }

        if (stack.is(DEItems.DATA_LIGHT_SABER.get())) {
            DataDisorderEffectLogic.applyOrBurst(target, DATA_DISORDER_DURATION_TICKS, attacker,
                    (float) getPanelAttackDamage(stack));
        }
    }

    private void tryApplyDataCrystalSwordDisorder(ItemStack stack, LivingEntity target, LivingEntity attacker) {
        var cellInventory = StorageCells.getCellInventory(stack, null);
        if (cellInventory == null) {
            return;
        }

        long extracted = cellInventory.extract(DataFlowKey.of(), SWORD_DATA_DISORDER_DATA_FLOW_COST,
                Actionable.MODULATE, IActionSource.empty());
        if (extracted < SWORD_DATA_DISORDER_DATA_FLOW_COST) {
            return;
        }

        DataDisorderEffectLogic.applyOrBurst(target, DATA_DISORDER_DURATION_TICKS, attacker,
                (float) getPanelAttackDamage(stack));
    }

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

    @Override
    public Projectile asProjectile(Level level, Position pos, ItemStack stack, Direction direction) {
        if (!this.throwable) {
            throw new IllegalStateException("Non-throwable powered sword cannot be converted to a projectile");
        }
        ThrownLightSaberEntity thrownLightSaber = new ThrownLightSaberEntity(
                level,
                pos.x(),
                pos.y(),
                pos.z(),
                stack.copyWithCount(1));
        thrownLightSaber.pickup = Pickup.ALLOWED;
        return thrownLightSaber;
    }
}

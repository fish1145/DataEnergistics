package com.fish_dan_.data_energistics.entity.projectile;

import com.fish_dan_.data_energistics.entity.projectile.cannon.CannonShot;
import com.fish_dan_.data_energistics.item.powered.MatterConvergingCrossbowMode;
import com.fish_dan_.data_energistics.registry.DEEntities;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.entity.IEntityWithComplexSpawn;

/** Fast physical round: explicit spawn velocity avoids vanilla's 3.9-block velocity packet clamp. */
public final class RailRoundEntity extends MatterConvergingBoltEntity implements IEntityWithComplexSpawn {

    public RailRoundEntity(EntityType<? extends RailRoundEntity> type, Level level) {
        super(type, level);
    }

    public RailRoundEntity(Level level, LivingEntity owner, ItemStack ammunition) {
        this(DEEntities.RAIL_ROUND.get(), level);
        this.setOwner(owner);
        this.setItem(ammunition);
    }

    @Override
    public void writeSpawnData(RegistryFriendlyByteBuf buffer) {
        ItemStack.STREAM_CODEC.encode(buffer, this.getItem());
        Vec3 velocity = this.getDeltaMovement();
        buffer.writeDouble(velocity.x);
        buffer.writeDouble(velocity.y);
        buffer.writeDouble(velocity.z);
    }

    @Override
    public void readSpawnData(RegistryFriendlyByteBuf buffer) {
        this.setItem(ItemStack.STREAM_CODEC.decode(buffer));
        Vec3 velocity = new Vec3(buffer.readDouble(), buffer.readDouble(), buffer.readDouble());
        if (!Double.isFinite(velocity.x) || !Double.isFinite(velocity.y) || !Double.isFinite(velocity.z))
            throw new IllegalArgumentException("Invalid rail spawn velocity");
        this.configureCannonShot(new CannonShot(MatterConvergingCrossbowMode.RAIL, 1, 0));
        this.setDeltaMovement(velocity);
    }
}

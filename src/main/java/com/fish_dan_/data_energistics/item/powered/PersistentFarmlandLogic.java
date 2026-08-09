package com.fish_dan_.data_energistics.item.powered;

import com.fish_dan_.data_energistics.world.PersistentFarmlandSavedData;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.FarmBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.common.FarmlandWaterManager;
import net.neoforged.neoforge.common.ticket.AABBTicket;
import net.neoforged.neoforge.event.tick.LevelTickEvent;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.WeakHashMap;

public final class PersistentFarmlandLogic {

    private final Map<ServerLevel, Map<Long, AABBTicket>> waterTickets = new WeakHashMap<>();

    @SubscribeEvent
    public void onLevelTick(LevelTickEvent.Post event) {
        if (!(event.getLevel() instanceof ServerLevel level)) {
            return;
        }

        PersistentFarmlandSavedData data = PersistentFarmlandSavedData.get(level);
        Map<Long, AABBTicket> levelTickets = this.waterTickets.computeIfAbsent(level, ignored -> new HashMap<>());

        for (long packedPos : data.getPositions()) {
            BlockPos pos = BlockPos.of(packedPos);
            BlockState state = level.getBlockState(pos);
            if (!(state.getBlock() instanceof FarmBlock) || !state.hasProperty(FarmBlock.MOISTURE)) {
                data.remove(pos);
                AABBTicket ticket = levelTickets.remove(packedPos);
                if (ticket != null) {
                    ticket.invalidate();
                }
                continue;
            }

            AABBTicket ticket = levelTickets.get(packedPos);
            if (ticket == null || !ticket.isValid()) {
                if (ticket != null) {
                    ticket.invalidate();
                }
                levelTickets.put(packedPos, createWaterTicket(level, pos));
            }

            if (state.getValue(FarmBlock.MOISTURE) != FarmBlock.MAX_MOISTURE) {
                level.setBlock(pos, state.setValue(FarmBlock.MOISTURE, FarmBlock.MAX_MOISTURE), 3);
            }
        }

        Iterator<Map.Entry<Long, AABBTicket>> iterator = levelTickets.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<Long, AABBTicket> entry = iterator.next();
            if (data.getPositions().contains(entry.getKey())) {
                continue;
            }
            entry.getValue().invalidate();
            iterator.remove();
        }
    }

    private static AABBTicket createWaterTicket(ServerLevel level, BlockPos pos) {
        return FarmlandWaterManager.addAABBTicket(level,
                new AABB(pos.getX(), pos.getY(), pos.getZ(), pos.getX() + 1.0D, pos.getY() + 1.0D, pos.getZ() + 1.0D));
    }
}

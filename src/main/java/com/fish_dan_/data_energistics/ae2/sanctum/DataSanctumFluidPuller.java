package com.fish_dan_.data_energistics.ae2.sanctum;

import com.fish_dan_.data_energistics.Data_Energistics;

import appeng.api.config.Actionable;
import appeng.api.networking.security.IActionSource;
import appeng.api.stacks.AEFluidKey;

import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;

import org.apache.logging.log4j.Logger;

/**
 * Transfers one accepted fluid batch from an external handler into a Data Sanctum return inventory.
 */
public final class DataSanctumFluidPuller {

    private static final Logger LOGGER = Data_Energistics.LOGGER;

    private DataSanctumFluidPuller() {}

    /**
     * Scans tanks in order and transfers the first fluid identity accepted by the return inventory.
     */
    public static boolean pullFirstAccepted(IFluidHandler handler,
                                            DataSanctumReturnInventory returnInventory,
                                            IActionSource actionSource,
                                            int maxAmount) {
        for (int tank = 0; tank < handler.getTanks(); tank++) {
            FluidStack candidate = handler.getFluidInTank(tank);
            if (candidate.isEmpty()) {
                continue;
            }

            FluidStack request = candidate.copy();
            request.setAmount(Math.min(candidate.getAmount(), maxAmount));
            FluidStack simulated = handler.drain(request, IFluidHandler.FluidAction.SIMULATE);
            if (simulated.isEmpty() || !FluidStack.isSameFluidSameComponents(simulated, candidate)) {
                continue;
            }

            AEFluidKey key = AEFluidKey.of(simulated);
            if (key == null) {
                continue;
            }

            long canBuffer = returnInventory.insert(key, simulated.getAmount(), Actionable.SIMULATE, actionSource);
            if (canBuffer <= 0) {
                continue;
            }

            request.setAmount((int) Math.min(canBuffer, simulated.getAmount()));
            FluidStack extracted = handler.drain(request, IFluidHandler.FluidAction.EXECUTE);
            if (extracted.isEmpty()) {
                continue;
            }
            if (!FluidStack.isSameFluidSameComponents(extracted, simulated)) {
                if (!refill(handler, extracted)) {
                    return false;
                }
                continue;
            }

            long buffered = returnInventory.insert(key, extracted.getAmount(), Actionable.MODULATE, actionSource);
            if (buffered < extracted.getAmount()) {
                FluidStack leftover = extracted.copy();
                leftover.setAmount(extracted.getAmount() - (int) Math.min(buffered, extracted.getAmount()));
                if (!refill(handler, leftover)) {
                    return false;
                }
            }
            if (buffered > 0) {
                return true;
            }
        }

        return false;
    }

    private static boolean refill(IFluidHandler handler, FluidStack fluid) {
        FluidStack remaining = fluid.copy();
        while (!remaining.isEmpty()) {
            int refilled = handler.fill(remaining, IFluidHandler.FluidAction.EXECUTE);
            if (refilled <= 0) {
                LOGGER.error("Data Sanctum fluid pull could not return {} mB of {} after a failed transfer",
                        remaining.getAmount(), remaining.getFluidHolder());
                return false;
            }
            remaining.setAmount(remaining.getAmount() - refilled);
        }
        return true;
    }
}

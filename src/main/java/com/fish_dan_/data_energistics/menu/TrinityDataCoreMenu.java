package com.fish_dan_.data_energistics.menu;

import com.fish_dan_.data_energistics.registry.ModMenus;

import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.world.entity.player.Inventory;

import appeng.api.stacks.GenericStack;
import appeng.menu.AEBaseMenu;
import appeng.menu.guisync.GuiSync;
import appeng.menu.guisync.PacketWritable;
import org.jetbrains.annotations.Nullable;

public class TrinityDataCoreMenu extends AEBaseMenu {

    private static final String NO_FAILURE = "";

    @Nullable
    private final TrinityDataCoreMenuHost host;

    @GuiSync(930)
    public boolean online;
    @GuiSync(931)
    public boolean structureFormed;
    @GuiSync(932)
    public int matchedBlockCount;
    @GuiSync(933)
    public int patternBufferCount;
    @GuiSync(934)
    public String lastFailureReason = NO_FAILURE;
    @GuiSync(935)
    public String lastFailurePosition = NO_FAILURE;
    @GuiSync(936)
    public int busyCraftingCpuCount;
    @GuiSync(937)
    public SyncedCraftingTarget craftingTarget = SyncedCraftingTarget.EMPTY;
    @GuiSync(938)
    public int storedTypeCount;
    @GuiSync(939)
    public String storedAmountText = "0";
    @GuiSync(940)
    public int cpuPartitionCount;
    @GuiSync(941)
    public int busyCpuPartitionCount;
    @GuiSync(942)
    public long cpuStorageBytes;
    @GuiSync(943)
    public int cpuCoProcessors;
    @GuiSync(944)
    public String storedTypeCapacityText = "0";
    @GuiSync(945)
    public String storedAmountCapacityText = "0";
    @GuiSync(946)
    public boolean cpuStructureFormed;
    @GuiSync(947)
    public int cpuStructureMatchedBlockCount;
    @GuiSync(948)
    public String cpuLastFailureReason = NO_FAILURE;
    @GuiSync(949)
    public String cpuLastFailurePosition = NO_FAILURE;
    @GuiSync(950)
    public boolean craftingStructureFormed;
    @GuiSync(951)
    public int craftingStructureMatchedBlockCount;
    @GuiSync(952)
    public int craftingPatternCoreCount;
    @GuiSync(953)
    public int craftingPatternCapacity;
    @GuiSync(954)
    public String craftingLastFailureReason = NO_FAILURE;
    @GuiSync(955)
    public String craftingLastFailurePosition = NO_FAILURE;

    public TrinityDataCoreMenu(int id, Inventory playerInventory, @Nullable TrinityDataCoreMenuHost host) {
        super(ModMenus.TRINITY_DATA_CORE.get(), id, playerInventory, host);
        this.host = host;
        createPlayerInventorySlots(playerInventory);
    }

    @Override
    public void broadcastChanges() {
        if (this.host == null) {
            clearState();
        } else {
            this.online = this.host.isOnline();
            this.structureFormed = this.host.isStructureFormed();
            this.matchedBlockCount = this.host.getMatchedBlockCount();
            this.patternBufferCount = this.host.getPatternBufferCount();
            this.cpuStructureFormed = this.host.isCpuStructureFormed();
            this.cpuStructureMatchedBlockCount = this.host.getCpuStructureMatchedBlockCount();
            this.cpuLastFailureReason = this.host.getCpuLastFailureReason();
            this.cpuLastFailurePosition = formatFailurePosition(this.host.getCpuLastFailurePosition());
            this.craftingStructureFormed = this.host.isCraftingStructureFormed();
            this.craftingStructureMatchedBlockCount = this.host.getCraftingStructureMatchedBlockCount();
            this.craftingPatternCoreCount = this.host.getCraftingPatternCoreCount();
            this.craftingPatternCapacity = this.host.getCraftingPatternCapacity();
            this.craftingLastFailureReason = this.host.getCraftingLastFailureReason();
            this.craftingLastFailurePosition = formatFailurePosition(this.host.getCraftingLastFailurePosition());
            this.lastFailureReason = this.host.getLastFailureReason();
            this.lastFailurePosition = formatFailurePosition(this.host.getLastFailurePosition());

            TrinityDataCoreCraftingStatus craftingStatus = this.host.getCraftingStatus();
            this.busyCraftingCpuCount = craftingStatus.busyCpuCount();
            this.craftingTarget = craftingStatus.hasTarget() ? new SyncedCraftingTarget(craftingStatus.target()) : SyncedCraftingTarget.EMPTY;
            this.storedTypeCount = this.host.getStoredTypeCount();
            this.storedAmountText = this.host.getStoredAmountText();
            this.storedTypeCapacityText = this.host.getStoredTypeCapacityText();
            this.storedAmountCapacityText = this.host.getStoredAmountCapacityText();
            this.cpuPartitionCount = this.host.getCpuPartitionCount();
            this.busyCpuPartitionCount = this.host.getBusyCpuPartitionCount();
            this.cpuStorageBytes = this.host.getCpuStorageBytes();
            this.cpuCoProcessors = this.host.getCpuCoProcessors();
        }

        super.broadcastChanges();
    }

    public @Nullable GenericStack getCraftingTarget() {
        return this.craftingTarget.target();
    }

    public @Nullable TrinityDataCoreMenuHost getHost() {
        return this.host;
    }

    public boolean hasCraftingTarget() {
        return this.craftingTarget.hasTarget();
    }

    private void clearState() {
        this.online = false;
        this.structureFormed = false;
        this.matchedBlockCount = 0;
        this.patternBufferCount = 0;
        this.cpuStructureFormed = false;
        this.cpuStructureMatchedBlockCount = 0;
        this.cpuLastFailureReason = NO_FAILURE;
        this.cpuLastFailurePosition = NO_FAILURE;
        this.craftingStructureFormed = false;
        this.craftingStructureMatchedBlockCount = 0;
        this.craftingPatternCoreCount = 0;
        this.craftingPatternCapacity = 0;
        this.craftingLastFailureReason = NO_FAILURE;
        this.craftingLastFailurePosition = NO_FAILURE;
        this.lastFailureReason = NO_FAILURE;
        this.lastFailurePosition = NO_FAILURE;
        this.busyCraftingCpuCount = 0;
        this.craftingTarget = SyncedCraftingTarget.EMPTY;
        this.storedTypeCount = 0;
        this.storedAmountText = "0";
        this.storedTypeCapacityText = "0";
        this.storedAmountCapacityText = "0";
        this.cpuPartitionCount = 0;
        this.busyCpuPartitionCount = 0;
        this.cpuStorageBytes = 0L;
        this.cpuCoProcessors = 0;
    }

    private static String formatFailurePosition(@Nullable BlockPos pos) {
        if (pos == null) {
            return NO_FAILURE;
        }
        return pos.getX() + ", " + pos.getY() + ", " + pos.getZ();
    }

    public record SyncedCraftingTarget(@Nullable GenericStack target) implements PacketWritable {

        public static final SyncedCraftingTarget EMPTY = new SyncedCraftingTarget((GenericStack) null);

        public SyncedCraftingTarget {
            if (!hasTarget(target)) {
                target = null;
            }
        }

        public SyncedCraftingTarget(RegistryFriendlyByteBuf data) {
            this(GenericStack.readBuffer(data));
        }

        @Override
        public void writeToPacket(RegistryFriendlyByteBuf data) {
            GenericStack.writeBuffer(this.target, data);
        }

        public boolean hasTarget() {
            return hasTarget(this.target);
        }

        private static boolean hasTarget(@Nullable GenericStack stack) {
            return stack != null && stack.amount() > 0;
        }
    }
}

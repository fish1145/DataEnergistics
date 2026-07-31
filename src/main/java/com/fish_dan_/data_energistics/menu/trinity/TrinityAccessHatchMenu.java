package com.fish_dan_.data_energistics.menu.trinity;

import com.fish_dan_.data_energistics.Data_Energistics;
import com.fish_dan_.data_energistics.common.trinity.TrinityHostedActionStatus;
import com.fish_dan_.data_energistics.common.trinity.TrinityHostedActionTicket;
import com.fish_dan_.data_energistics.registry.ModMenus;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.MenuType;

import appeng.helpers.InventoryAction;
import appeng.helpers.patternprovider.PatternContainer;
import appeng.menu.guisync.GuiSync;
import appeng.menu.implementations.PatternAccessTermMenu;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * Pattern-access terminal menu scoped to one lease-holding Trinity ME access hatch.
 *
 * <p>
 * AE2 continues to provide its bounded pattern inventory protocol while this menu adds two independently acknowledged
 * refund actions for the data core currently managed by the hatch.
 * </p>
 */
public class TrinityAccessHatchMenu extends PatternAccessTermMenu {

    private static final String ACTION_REFUND_PATTERNS = "dataEnergistics$refundPatterns";
    private static final String ACTION_REFUND_RETAINED_ITEMS = "dataEnergistics$refundRetainedItems";

    private final TrinityAccessHatchMenuHost host;

    @GuiSync(801)
    public long refundPatternsRevision;
    @GuiSync(802)
    public int refundPatternsStatus = TrinityHostedActionStatus.REJECTED.networkId();
    @GuiSync(803)
    public long refundRetainedItemsRevision;
    @GuiSync(804)
    public int refundRetainedItemsStatus = TrinityHostedActionStatus.REJECTED.networkId();

    private long clientObservedRefundPatternsRevision;
    private long clientObservedRefundRetainedItemsRevision;
    private long nextRefundPatternsSequence = 1L;
    private long nextRefundRetainedItemsSequence = 1L;
    private long lastAcceptedRefundPatternsSequence;
    private long lastAcceptedRefundRetainedItemsSequence;
    private boolean refundPatternsPending;
    private boolean refundRetainedItemsPending;
    @Nullable
    private TrinityHostedActionStatus refundPatternsResult;
    @Nullable
    private TrinityHostedActionStatus refundRetainedItemsResult;

    /**
     * Creates the registered access-hatch menu with the standard AE2 player inventory binding.
     *
     * @param id              active container identifier
     * @param playerInventory inventory of the player opening the hatch
     * @param host            exact hatch selected by the menu locator
     */
    public TrinityAccessHatchMenu(int id, Inventory playerInventory, TrinityAccessHatchMenuHost host) {
        this(ModMenus.TRINITY_ACCESS_HATCH.get(), id, playerInventory, host, true);
    }

    /**
     * Creates an access-hatch menu for registered variants that need to control player inventory binding.
     *
     * @param menuType        registered menu type
     * @param id              active container identifier
     * @param playerInventory inventory of the player opening the hatch
     * @param host            exact hatch selected by the menu locator
     * @param bindInventory   whether AE2 should create the player's inventory slots
     */
    public TrinityAccessHatchMenu(MenuType<?> menuType,
                                  int id,
                                  Inventory playerInventory,
                                  TrinityAccessHatchMenuHost host,
                                  boolean bindInventory) {
        super(menuType, id, playerInventory, requireHost(host), bindInventory);
        this.host = host;
        this.registerClientAction(
                ACTION_REFUND_PATTERNS,
                Long.class,
                sequence -> executeRefund(RefundTarget.PATTERNS, sequence));
        this.registerClientAction(
                ACTION_REFUND_RETAINED_ITEMS,
                Long.class,
                sequence -> executeRefund(RefundTarget.RETAINED_ITEMS, sequence));
    }

    /**
     * Keeps the menu attached only while the original hatch block entity and player route remain current.
     */
    @Override
    public boolean stillValid(Player player) {
        return super.stillValid(player) && this.host.isAccessHatchMenuValid(player);
    }

    /**
     * Called by the AE2 visibility mixin to reject every grid pattern container not owned by this menu's hatch.
     *
     * @param container candidate discovered by AE2's grid-wide scan
     * @return whether the exact candidate belongs to this access hatch
     */
    public boolean isManagedPatternContainer(PatternContainer container) {
        return this.host.isManagedPatternContainer(container);
    }

    /**
     * Rejects delayed or forged slot actions after the player, hatch block or lease has left this menu's route.
     */
    @Override
    public void doAction(ServerPlayer player, InventoryAction action, int slot, long id) {
        if (canManagePatterns(player)) {
            super.doAction(player, action, slot, id);
        }
    }

    /**
     * Applies the same live-route validation before AE2 inserts a player pattern into any selected partition.
     */
    @Override
    public void quickMovePattern(ServerPlayer player, int clickedSlot, List<Long> allowedPatternContainers) {
        if (canManagePatterns(player)) {
            super.quickMovePattern(player, clickedSlot, allowedPatternContainers);
        }
    }

    /**
     * Sends one installed-pattern refund request unless that same action is already awaiting a response.
     *
     * @return whether the request was accepted for transport
     */
    public boolean requestRefundPatterns() {
        captureSynchronizedResults();
        if (!canRequestRefund() || this.refundPatternsPending) {
            return false;
        }
        long sequence = nextClientSequence(RefundTarget.PATTERNS);
        if (sequence < 0L) {
            this.refundPatternsResult = TrinityHostedActionStatus.REJECTED;
            return false;
        }
        this.refundPatternsPending = true;
        this.refundPatternsResult = null;
        return sendRefundRequest(ACTION_REFUND_PATTERNS, RefundTarget.PATTERNS, sequence);
    }

    /**
     * Sends one retained-item refund request unless that same action is already awaiting a response.
     *
     * @return whether the request was accepted for transport
     */
    public boolean requestRefundRetainedItems() {
        captureSynchronizedResults();
        if (!canRequestRefund() || this.refundRetainedItemsPending) {
            return false;
        }
        long sequence = nextClientSequence(RefundTarget.RETAINED_ITEMS);
        if (sequence < 0L) {
            this.refundRetainedItemsResult = TrinityHostedActionStatus.REJECTED;
            return false;
        }
        this.refundRetainedItemsPending = true;
        this.refundRetainedItemsResult = null;
        return sendRefundRequest(ACTION_REFUND_RETAINED_ITEMS, RefundTarget.RETAINED_ITEMS, sequence);
    }

    /**
     * Reports whether the installed-pattern refund independently awaits its next revision.
     */
    public boolean isRefundPatternsPending() {
        captureSynchronizedResults();
        return this.refundPatternsPending;
    }

    /**
     * Reports whether the retained-item refund independently awaits its next revision.
     */
    public boolean isRefundRetainedItemsPending() {
        captureSynchronizedResults();
        return this.refundRetainedItemsPending;
    }

    /**
     * Consumes the newest installed-pattern refund outcome exactly once on the client.
     *
     * @return completed result, or {@code null} when none is ready
     */
    @Nullable
    public TrinityHostedActionStatus consumeRefundPatternsResult() {
        captureSynchronizedResults();
        TrinityHostedActionStatus result = this.refundPatternsResult;
        this.refundPatternsResult = null;
        return result;
    }

    /**
     * Consumes the newest retained-item refund outcome exactly once on the client.
     *
     * @return completed result, or {@code null} when none is ready
     */
    @Nullable
    public TrinityHostedActionStatus consumeRefundRetainedItemsResult() {
        captureSynchronizedResults();
        TrinityHostedActionStatus result = this.refundRetainedItemsResult;
        this.refundRetainedItemsResult = null;
        return result;
    }

    private static TrinityAccessHatchMenuHost requireHost(@Nullable TrinityAccessHatchMenuHost host) {
        if (host == null) {
            throw new IllegalArgumentException("Trinity access hatch menu requires a host");
        }
        return host;
    }

    private boolean canRequestRefund() {
        return this.isClientSide() && this.getPlayer().containerMenu == this;
    }

    private boolean canManagePatterns(ServerPlayer player) {
        return player.containerMenu == this &&
                this.stillValid(player) &&
                this.host.isAccessHatchManagementAvailable(player);
    }

    private boolean sendRefundRequest(String actionName, RefundTarget target, long sequence) {
        try {
            this.sendClientAction(actionName, sequence);
            return true;
        } catch (RuntimeException exception) {
            setClientTransportFailure(target);
            Data_Energistics.LOGGER.error(
                    "Failed to send Trinity access hatch refund request for player {}, menu {}, target {}",
                    this.getPlayer().getName().getString(),
                    this.containerId,
                    target,
                    exception);
            return false;
        }
    }

    private void setClientTransportFailure(RefundTarget target) {
        if (target == RefundTarget.PATTERNS) {
            this.refundPatternsPending = false;
            this.refundPatternsResult = TrinityHostedActionStatus.INTERNAL_ERROR;
            return;
        }
        this.refundRetainedItemsPending = false;
        this.refundRetainedItemsResult = TrinityHostedActionStatus.INTERNAL_ERROR;
    }

    private void executeRefund(RefundTarget target, @Nullable Long sequence) {
        if (sequence == null || !claimServerSequence(target, sequence)) {
            return;
        }
        TrinityHostedActionStatus status;
        Player player = this.getPlayer();
        try {
            if (!(player instanceof ServerPlayer) ||
                    player.containerMenu != this ||
                    !this.stillValid(player) ||
                    !this.host.isAccessHatchManagementAvailable(player)) {
                status = TrinityHostedActionStatus.REJECTED;
            } else {
                status = switch (target) {
                    case PATTERNS -> this.host.refundPatterns(player);
                    case RETAINED_ITEMS -> this.host.refundRetainedItems(player);
                };
                if (status == null) {
                    throw new IllegalStateException("Trinity access hatch refund returned no action status");
                }
            }
        } catch (RuntimeException exception) {
            status = TrinityHostedActionStatus.INTERNAL_ERROR;
            Data_Energistics.LOGGER.error(
                    "Failed to execute Trinity access hatch refund for player {}, menu {}, host {}, target {}",
                    player.getName().getString(),
                    this.containerId,
                    this.host,
                    target,
                    exception);
        }
        publishRefundResult(target, status);
    }

    private long nextClientSequence(RefundTarget target) {
        long sequence = target == RefundTarget.PATTERNS ?
                this.nextRefundPatternsSequence : this.nextRefundRetainedItemsSequence;
        if (sequence > TrinityHostedActionTicket.MAX_SEQUENCE) {
            return -1L;
        }
        if (target == RefundTarget.PATTERNS) {
            this.nextRefundPatternsSequence++;
        } else {
            this.nextRefundRetainedItemsSequence++;
        }
        return sequence;
    }

    private boolean claimServerSequence(RefundTarget target, long sequence) {
        if (sequence < 1L || sequence > TrinityHostedActionTicket.MAX_SEQUENCE) {
            return false;
        }
        if (target == RefundTarget.PATTERNS) {
            if (sequence <= this.lastAcceptedRefundPatternsSequence) {
                return false;
            }
            this.lastAcceptedRefundPatternsSequence = sequence;
            return true;
        }
        if (sequence <= this.lastAcceptedRefundRetainedItemsSequence) {
            return false;
        }
        this.lastAcceptedRefundRetainedItemsSequence = sequence;
        return true;
    }

    private void publishRefundResult(RefundTarget target, TrinityHostedActionStatus status) {
        if (target == RefundTarget.PATTERNS) {
            this.refundPatternsStatus = status.networkId();
            this.refundPatternsRevision++;
            return;
        }
        this.refundRetainedItemsStatus = status.networkId();
        this.refundRetainedItemsRevision++;
    }

    private void captureSynchronizedResults() {
        if (!this.isClientSide()) {
            return;
        }
        if (this.refundPatternsRevision != this.clientObservedRefundPatternsRevision) {
            this.clientObservedRefundPatternsRevision = this.refundPatternsRevision;
            this.refundPatternsPending = false;
            this.refundPatternsResult = TrinityHostedActionStatus.fromNetworkId(this.refundPatternsStatus);
        }
        if (this.refundRetainedItemsRevision != this.clientObservedRefundRetainedItemsRevision) {
            this.clientObservedRefundRetainedItemsRevision = this.refundRetainedItemsRevision;
            this.refundRetainedItemsPending = false;
            this.refundRetainedItemsResult = TrinityHostedActionStatus.fromNetworkId(this.refundRetainedItemsStatus);
        }
    }

    private enum RefundTarget {
        PATTERNS,
        RETAINED_ITEMS
    }
}

package xiao.hua.client.screen;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import xiao.hua.roles.VengeanceAgentComponent;

import java.util.UUID;

public class LensViewScreenHandler extends AbstractContainerMenu {
    private final Container displayInventory;
    public final Player viewer;
    private final UUID targetPlayerUuid;
    private ServerPlayer liveTargetPlayer;

    public static final int ROWS = 4;
    public static final int COLUMNS = 9;
    public static final int SLOT_COUNT = ROWS * COLUMNS;

    public LensViewScreenHandler(int syncId, Inventory playerInventory, ServerPlayer targetPlayer) {
        super(HuaScreenHandlers.LENS_VIEW_SCREEN_HANDLER, syncId);
        this.viewer = playerInventory.player;
        this.targetPlayerUuid = targetPlayer.getUUID();
        this.liveTargetPlayer = targetPlayer;
        this.displayInventory = new SimpleContainer(SLOT_COUNT);
        for (int i = 0; i < Math.min(SLOT_COUNT, targetPlayer.getInventory().getContainerSize()); i++)
            this.displayInventory.setItem(i, targetPlayer.getInventory().getItem(i).copy());
        for (int row = 0; row < ROWS; row++) {
            for (int col = 0; col < COLUMNS; col++) {
                int slotIndex = row * COLUMNS + col;
                int x = 8 + col * 18;
                int y = 18 + row * 18;
                this.addSlot(new ReadOnlySlot(this.displayInventory, slotIndex, x, y));
            }
        }
    }

    public LensViewScreenHandler(int syncId, Inventory playerInventory, UUID targetUuid) {
        super(HuaScreenHandlers.LENS_VIEW_SCREEN_HANDLER, syncId);
        this.viewer = playerInventory.player;
        this.targetPlayerUuid = targetUuid;
        this.displayInventory = new SimpleContainer(SLOT_COUNT);
        for (int i = 0; i < SLOT_COUNT; i++)
            this.displayInventory.setItem(i, ItemStack.EMPTY);
        for (int row = 0; row < ROWS; row++) {
            for (int col = 0; col < COLUMNS; col++) {
                int slotIndex = row * COLUMNS + col;
                int x = 8 + col * 18;
                int y = 18 + row * 18;
                this.addSlot(new ReadOnlySlot(this.displayInventory, slotIndex, x, y));
            }
        }
    }

    @Override
    public ItemStack quickMoveStack(Player player, int slot) {
        return ItemStack.EMPTY;
    }

    @Override
    public boolean stillValid(Player player) {
        if (player != this.viewer) return false;
        VengeanceAgentComponent component = VengeanceAgentComponent.getKey().maybeGet(this.viewer).orElse(null);
        return component != null && component.isInspecting();
    }

    @Override
    public void broadcastChanges() {
        if (this.liveTargetPlayer != null && !this.liveTargetPlayer.isRemoved()) {
            for (int i = 0; i < Math.min(SLOT_COUNT, this.liveTargetPlayer.getInventory().getContainerSize()); i++) {
                ItemStack stack = this.liveTargetPlayer.getInventory().getItem(i).copy();
                if (!ItemStack.matches(stack, this.displayInventory.getItem(i))) {
                    this.displayInventory.setItem(i, stack);
                }
            }
        }
        super.broadcastChanges();
    }

    @Override
    public void removed(Player player) {
        super.removed(player);
        VengeanceAgentComponent component = VengeanceAgentComponent.getKey().maybeGet(this.viewer).orElse(null);
        if (component != null)
            component.stopInspecting();
    }

    public UUID getTargetPlayerUuid() {
        return this.targetPlayerUuid;
    }

    public Container getDisplayInventory() {
        return this.displayInventory;
    }

    private static class ReadOnlySlot extends Slot {
        public ReadOnlySlot(Container inventory, int index, int x, int y) {
            super(inventory, index, x, y);
        }

        @Override
        public boolean mayPlace(ItemStack stack) {
            return false;
        }

        @Override
        public boolean mayPickup(Player player) {
            return false;
        }

        @Override
        public ItemStack remove(int amount) {
            return ItemStack.EMPTY;
        }
    }
}

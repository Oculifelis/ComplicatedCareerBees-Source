package dev.oculifelis.careerbees.menu;

import dev.oculifelis.careerbees.Config;
import dev.oculifelis.careerbees.item.BeeGunItem;
import dev.oculifelis.careerbees.item.Chambers;
import dev.oculifelis.careerbees.registry.ModComponents;
import dev.oculifelis.careerbees.registry.ModItems;
import dev.oculifelis.careerbees.registry.ModMenus;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/**
 * Loads the Bee-Bee Gun the player is holding: six bee chambers and a slot that empties honey bottles into the
 * tank. Every change is written straight back onto the gun stack.
 */
public class GunMenu extends AbstractContainerMenu {
    public static final int[][] CHAMBER_POS = {{57, 20}, {76, 30}, {76, 50}, {57, 60}, {38, 50}, {38, 30}};
    public static final int BOTTLE_X = 130, BOTTLE_Y = 62, GAUGE_X = 151, GAUGE_Y = 20, GAUGE_W = 16, GAUGE_H = 58;
    public static final int INV_X = 8, INV_Y = 96, HOTBAR_Y = 154;
    private static final int BOTTLE = Chambers.SIZE, INV_START = BOTTLE + 1, HOTBAR_START = INV_START + 27, END = HOTBAR_START + 9;

    private final Player player;
    private final int gunSlot;
    private final SimpleContainer box = new SimpleContainer(Chambers.SIZE + 1) {
        @Override
        public void setChanged() {
            super.setChanged();
            changed();
        }
    };
    private boolean loading;

    public GunMenu(int windowId, Inventory inv, int gunSlot) {
        super(ModMenus.GUN.get(), windowId);
        this.player = inv.player;
        this.gunSlot = gunSlot;
        loading = true;
        Chambers c = BeeGunItem.chambers(gun());
        for (int i = 0; i < Chambers.SIZE; i++) box.setItem(i, c.get(i));
        loading = false;
        for (int i = 0; i < Chambers.SIZE; i++) {
            addSlot(new Slot(box, i, CHAMBER_POS[i][0], CHAMBER_POS[i][1]) {
                @Override
                public boolean mayPlace(ItemStack stack) {
                    return BeeGunItem.loadable(stack);
                }
            });
        }
        addSlot(new Slot(box, BOTTLE, BOTTLE_X, BOTTLE_Y) {
            @Override
            public boolean mayPlace(ItemStack stack) {
                return stack.is(Items.HONEY_BOTTLE);
            }
        });
        for (int row = 0; row < 3; row++)
            for (int col = 0; col < 9; col++) addSlot(new Slot(inv, col + row * 9 + 9, INV_X + col * 18, INV_Y + row * 18));
        for (int col = 0; col < 9; col++) addSlot(new Slot(inv, col, INV_X + col * 18, HOTBAR_Y));
    }

    public static GunMenu fromNetwork(int windowId, Inventory inv, RegistryFriendlyByteBuf data) {
        return new GunMenu(windowId, inv, data.readVarInt());
    }

    public ItemStack gun() {
        return player.getInventory().getItem(gunSlot);
    }

    public int gunSlot() {
        return gunSlot;
    }

    private void changed() {
        if (loading) return;
        ItemStack gun = gun();
        if (!gun.is(ModItems.BEE_GUN.get())) return;
        Chambers c = Chambers.EMPTY;
        for (int i = 0; i < Chambers.SIZE; i++) c = c.with(i, box.getItem(i));
        if (!c.equals(BeeGunItem.chambers(gun))) gun.set(ModComponents.CHAMBERS.get(), c);
        if (player.level().isClientSide()) return;
        ItemStack bottles = box.getItem(BOTTLE);
        int per = Config.server(Config.GUN_PER_BOTTLE), cap = BeeGunItem.capacity();
        int honey = BeeGunItem.honey(gun), used = 0;
        while (!bottles.isEmpty() && bottles.is(Items.HONEY_BOTTLE) && honey + per <= cap) {
            bottles.shrink(1);
            honey += per;
            used++;
        }
        if (used > 0) {
            gun.set(ModComponents.HONEY.get(), honey);
            player.getInventory().placeItemBackInInventory(new ItemStack(Items.GLASS_BOTTLE, used));
            loading = true;
            box.setItem(BOTTLE, bottles.isEmpty() ? ItemStack.EMPTY : bottles);
            loading = false;
        }
    }

    @Override
    public void clicked(int slotId, int button, ContainerInput input, Player player) {
        if (slotId - HOTBAR_START == gunSlot) return; // the gun being loaded stays put
        if (input == ContainerInput.SWAP && (button == gunSlot || (gunSlot == BeeGunItem.OFFHAND && button == 40))) return;
        super.clicked(slotId, button, input, player);
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        Slot slot = slots.get(index);
        if (!slot.hasItem() || index - HOTBAR_START == gunSlot) return ItemStack.EMPTY;
        ItemStack stack = slot.getItem(), copy = stack.copy();
        if (index < INV_START) {
            if (!moveItemStackTo(stack, INV_START, END, true)) return ItemStack.EMPTY;
        } else if (stack.is(Items.HONEY_BOTTLE)) {
            if (!moveItemStackTo(stack, BOTTLE, BOTTLE + 1, false)) return ItemStack.EMPTY;
        } else if (BeeGunItem.loadable(stack)) {
            if (!moveItemStackTo(stack, 0, Chambers.SIZE, false)) return ItemStack.EMPTY;
        } else return ItemStack.EMPTY;
        if (stack.isEmpty()) slot.setByPlayer(ItemStack.EMPTY);
        else slot.setChanged();
        return copy;
    }

    @Override
    public void removed(Player player) {
        super.removed(player);
        // A bottle left in the slot goes back to the player.
        ItemStack left = box.removeItemNoUpdate(BOTTLE);
        if (!left.isEmpty() && !player.level().isClientSide()) player.getInventory().placeItemBackInInventory(left);
    }

    @Override
    public boolean stillValid(Player player) {
        return gun().is(ModItems.BEE_GUN.get());
    }
}

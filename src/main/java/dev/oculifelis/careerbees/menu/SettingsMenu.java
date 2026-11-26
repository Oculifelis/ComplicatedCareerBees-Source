package dev.oculifelis.careerbees.menu;

import dev.oculifelis.careerbees.effect.FilterSettings;
import dev.oculifelis.careerbees.registry.ModComponents;
import dev.oculifelis.careerbees.registry.ModItems;
import dev.oculifelis.careerbees.registry.ModMenus;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

import java.util.function.UnaryOperator;

/**
 * Edits the filter on the Settings Frame the player is holding. The only real slots are the player's inventory;
 * the filter item is a ghost that copies whatever is clicked onto it. All edits change the frame stack itself.
 */
public class SettingsMenu extends AbstractContainerMenu {
    public static final int MODE = 0, IGNORE_COMPONENTS = 1, IGNORE_DAMAGE = 2, VOID_EXCESS = 3, GHOST = 4;
    public static final int INV_X = 17, INV_Y = 137, HOTBAR_Y = 195;

    private final Player player;
    private final int frameSlot;

    public SettingsMenu(int windowId, Inventory inv, int frameSlot) {
        super(ModMenus.SETTINGS.get(), windowId);
        this.player = inv.player;
        this.frameSlot = frameSlot;
        for (int row = 0; row < 3; row++)
            for (int col = 0; col < 9; col++) addSlot(new Slot(inv, col + row * 9 + 9, INV_X + col * 18, INV_Y + row * 18));
        for (int col = 0; col < 9; col++) addSlot(new Slot(inv, col, INV_X + col * 18, HOTBAR_Y));
    }

    public static SettingsMenu fromNetwork(int windowId, Inventory inv) {
        return new SettingsMenu(windowId, inv, inv.getSelectedSlot());
    }

    public ItemStack frame() {
        return player.getInventory().getItem(frameSlot);
    }

    public FilterSettings settings() {
        return frame().getOrDefault(ModComponents.FILTER.get(), FilterSettings.DEFAULT);
    }

    public void edit(UnaryOperator<FilterSettings> change) {
        ItemStack frame = frame();
        if (frame.is(ModItems.SETTINGS_FRAME.get())) frame.set(ModComponents.FILTER.get(), change.apply(settings()));
    }

    @Override
    public boolean clickMenuButton(Player player, int id) {
        switch (id) {
            case MODE -> edit(s -> s.withTagMode(!s.tagMode()));
            case IGNORE_COMPONENTS -> edit(s -> s.withIgnoreComponents(!s.ignoreComponents()));
            case IGNORE_DAMAGE -> edit(s -> s.withIgnoreDamage(!s.ignoreDamage()));
            case VOID_EXCESS -> edit(s -> s.withVoidExcess(!s.voidExcess()));
            case GHOST -> edit(s -> s.withItem(getCarried()));
            default -> { return false; }
        }
        return true;
    }

    @Override
    public void clicked(int slotId, int button, ContainerInput input, Player player) {
        if (slotId - 27 == frameSlot) return; // the frame being edited stays put
        super.clicked(slotId, button, input, player);
    }

    /** Shift-click copies the item into the filter instead of moving it. */
    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        Slot slot = slots.get(index);
        if (slot.hasItem() && index - 27 != frameSlot) {
            ItemStack stack = slot.getItem();
            edit(s -> s.withItem(stack));
        }
        return ItemStack.EMPTY;
    }

    @Override
    public boolean stillValid(Player player) {
        return frame().is(ModItems.SETTINGS_FRAME.get());
    }
}

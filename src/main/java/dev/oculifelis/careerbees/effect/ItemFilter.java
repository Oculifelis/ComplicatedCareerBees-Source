package dev.oculifelis.careerbees.effect;

import com.accbdd.complicated_bees.block.entity.BaseBeeHousing;
import dev.oculifelis.careerbees.registry.ModComponents;
import dev.oculifelis.careerbees.registry.ModItems;
import net.minecraft.world.entity.decoration.ItemFrame;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.AABB;

/**
 * Item filter for effects that handle items.
 * A Settings Frame in any of the housing's frame slots decides first. Without one, an item frame hung on the
 * housing limits them to the framed item. With neither, everything passes.
 */
public final class ItemFilter {
    public static boolean passes(CareerEffect.Ctx ctx, ItemStack stack) {
        FilterSettings settings = settings(ctx);
        if (settings != null) return settings.matches(stack);
        ItemStack want = framed(ctx);
        return want.isEmpty() || stack.is(want.getItem());
    }

    /** Whether the Settings Frame says to destroy items the housing has no room for. */
    public static boolean voidExcess(CareerEffect.Ctx ctx) {
        FilterSettings settings = settings(ctx);
        return settings != null && settings.voidExcess();
    }

    /** Whether the housing still has output waiting to be moved out (it makes nothing new until that is gone). */
    public static boolean outputFull(CareerEffect.Ctx ctx) {
        return ctx.be() instanceof BaseBeeHousing housing && !housing.getOutputBuffer().empty();
    }

    /** The filter of the first Settings Frame in the housing, or null if there is none. */
    public static FilterSettings settings(CareerEffect.Ctx ctx) {
        if (!(ctx.be() instanceof BaseBeeHousing housing)) return null;
        for (ItemStack frame : housing.getActiveFrames()) {
            if (frame.is(ModItems.SETTINGS_FRAME.get())) return frame.getOrDefault(ModComponents.FILTER.get(), FilterSettings.DEFAULT);
        }
        return null;
    }

    /** The item in a frame hung on the housing block, or empty. */
    public static ItemStack framed(CareerEffect.Ctx ctx) {
        for (ItemFrame f : ctx.level().getEntitiesOfClass(ItemFrame.class, new AABB(ctx.pos()).inflate(1))) {
            if (f.getPos().relative(f.getDirection().getOpposite()).equals(ctx.pos()) && !f.getItem().isEmpty()) return f.getItem();
        }
        return ItemStack.EMPTY;
    }

    private ItemFilter() {}
}

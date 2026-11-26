package dev.oculifelis.careerbees.effect;

import dev.oculifelis.careerbees.block.PedestalBlockEntity;
import dev.oculifelis.careerbees.util.Bees;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Works on items sitting on flower pedestals in the territory.
 * Each fire picks one valid pedestal at random and changes its item once.
 * An item is released (automation may pull it) once this effect would not change it again.
 */
public abstract class PedestalEffect extends CareerEffect {
    public static final List<PedestalEffect> ALL = new CopyOnWriteArrayList<>();
    private static final DustParticleOptions YELLOW = new DustParticleOptions(0xFFE040, 1f);

    public enum Pulse {YELLOW, FIRE}

    private final Pulse pulse;

    Pulse pulseKind() {
        return pulse;
    }

    protected PedestalEffect(String id, int interval, double chance, Pulse pulse) {
        super(id, interval, chance);
        this.pulse = pulse;
        ALL.add(this);
    }

    /** Whether this effect works on that kind of item at all. */
    public abstract boolean accepts(Level level, ItemStack stack);

    /** The changed item, or null when nothing changes. */
    @Nullable
    public abstract ItemStack change(Ctx ctx, ItemStack stack);

    /** How a player would usually bring this item, for recipe viewers. Must work on the client. */
    public ItemStack sample(ItemStack stack) {
        return stack;
    }

    /** The item once this effect is done with it, for recipe viewers. Must work on the client; null when it can't be shown. */
    @Nullable
    public ItemStack preview(ItemStack stack) {
        return null;
    }

    /** True once one more change would do nothing. */
    public boolean finished(Level level, ItemStack stack) {
        return false;
    }

    public final boolean workable(Level level, ItemStack stack) {
        return !stack.isEmpty() && accepts(level, stack) && !finished(level, stack);
    }

    @Override
    protected void fire(Ctx ctx) {
        Identifier species = Bees.speciesId(ctx.queen(), true);
        List<PedestalBlockEntity> found = new ArrayList<>();
        for (BlockEntity be : LifeEffects.blockEntities(ctx.level(), territory(ctx))) {
            if (be instanceof PedestalBlockEntity p && p.usableBy(species) && workable(ctx.level(), p.getItem())
                    && ItemFilter.passes(ctx, p.getItem())) found.add(p);
        }
        if (found.isEmpty()) return;
        PedestalBlockEntity p = found.get(ctx.random().nextInt(found.size()));
        ItemStack next = change(ctx, p.getItem());
        if (next == null) return;
        p.setItem(next);
        pulse(ctx.level(), p.getBlockPos(), pulse);
    }

    public static void pulse(ServerLevel level, BlockPos pos, Pulse pulse) {
        double x = pos.getX() + 0.5, y = pos.getY() + 1.1, z = pos.getZ() + 0.5;
        if (pulse == Pulse.FIRE) {
            level.sendParticles(ParticleTypes.SMOKE, x, y, z, 10, 0.2, 0.2, 0.2, 0.01);
            level.sendParticles(ParticleTypes.FLAME, x, y, z, 10, 0.2, 0.2, 0.2, 0.01);
        } else {
            level.sendParticles(YELLOW, x, y, z, 10, 0.25, 0.25, 0.25, 0);
        }
    }
}

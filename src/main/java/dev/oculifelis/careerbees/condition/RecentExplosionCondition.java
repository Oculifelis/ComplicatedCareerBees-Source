package dev.oculifelis.careerbees.condition;

import com.accbdd.complicated_bees.recipe.mutation.condition.MutationCondition;
import dev.oculifelis.careerbees.CareerBees;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;

import java.util.ArrayDeque;
import java.util.Deque;

/** Passes when an explosion went off within 30 blocks (Manhattan) in the last 250 ticks, same dimension. */
public class RecentExplosionCondition extends MutationCondition {
    public static final String ID = "recent_explosion";
    private static final int RANGE = 30;
    private static final long WINDOW = 250;
    private static final Deque<Blast> RECENT = new ArrayDeque<>();

    public static synchronized void record(Level level, BlockPos pos) {
        long now = level.getGameTime();
        RECENT.addLast(new Blast(level.dimension(), pos.immutable(), now));
        while (RECENT.size() > 256) RECENT.removeFirst();
    }

    @Override
    public Identifier getID() {
        return CareerBees.id(ID);
    }

    @Override
    public synchronized boolean check(Level level, BlockPos pos) {
        long now = level.getGameTime();
        RECENT.removeIf(b -> b.dim == level.dimension() && now - b.time > WINDOW);
        for (Blast b : RECENT) {
            if (b.dim == level.dimension() && now - b.time <= WINDOW && b.pos.distManhattan(pos) <= RANGE) return true;
        }
        return false;
    }

    @Override
    public Component getDescription() {
        return Component.translatable("gui.careerbees.mutations.recent_explosion");
    }

    @Override
    public CompoundTag serialize() {
        return new CompoundTag();
    }

    @Override
    public RecentExplosionCondition deserialize(CompoundTag tag) {
        return new RecentExplosionCondition();
    }

    private record Blast(ResourceKey<Level> dim, BlockPos pos, long time) {}
}

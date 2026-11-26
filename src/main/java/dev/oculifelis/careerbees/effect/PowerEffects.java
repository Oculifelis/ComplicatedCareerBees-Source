package dev.oculifelis.careerbees.effect;

import com.accbdd.complicated_bees.block.entity.BaseGeneratorBlockEntity;
import com.accbdd.complicated_bees.block.entity.FurnaceGeneratorBlockEntity;
import com.accbdd.complicated_bees.port.PortCapabilities;
import dev.oculifelis.careerbees.CareerBees;
import dev.oculifelis.careerbees.Config;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.neoforged.neoforge.energy.IEnergyStorage;

import java.lang.reflect.Field;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.WeakHashMap;

/** Effects that feed machines or speed up blocks. */
public final class PowerEffects {
    public static final TagKey<BlockEntityType<?>> NO_ACCELERATION = TagKey.create(Registries.BLOCK_ENTITY_TYPE, CareerBees.id("no_acceleration"));

    public static final class Energy extends CareerEffect {
        public Energy() { super("energy", 0, 1); }

        @Override
        protected void fire(Ctx ctx) {
            int left = (int) Math.ceil(Config.ENERGY_PER_TICK.get() * ctx.speed() * ctx.speed());
            BlockPos pos = ctx.pos();
            for (Direction d : Direction.values()) {
                if (left <= 0) return;
                BlockPos n = pos.relative(d);
                IEnergyStorage e = PortCapabilities.find(ctx.level(), PortCapabilities.EnergyStorage.BLOCK, n, ctx.level().getBlockState(n), ctx.level().getBlockEntity(n), d.getOpposite());
                if (e != null && e.canReceive()) left -= e.receiveEnergy(left, false);
            }
        }
    }

    /** Winds one furnace generator in the territory. Its capability refuses input, so the stored energy is reached directly (Q11). */
    public static final class Clockwinding extends CareerEffect {
        private static Field energyField;
        private final Map<Object, Target> cache = new WeakHashMap<>();

        public Clockwinding() { super("clockwinding", 0, 1); }

        @Override
        protected void fire(Ctx ctx) {
            long now = ctx.level().getGameTime();
            Target t = cache.get(ctx.anchor());
            if (t == null || now >= t.rescanAt) {
                t = new Target(find(ctx), now + 400);
                cache.put(ctx.anchor(), t);
            }
            if (t.pos == null) return;
            if (!(ctx.level().getBlockEntity(t.pos) instanceof FurnaceGeneratorBlockEntity gen)) {
                cache.remove(ctx.anchor());
                return;
            }
            int amount = (int) Math.round(Math.min(2 + 4 * ctx.speed(), 8) / 8.0 * FurnaceGeneratorBlockEntity.BASE_GENERATE);
            net.neoforged.neoforge.energy.EnergyStorage store = storage(gen);
            if (store != null && store.receiveEnergy(amount, false) > 0) gen.setChanged();
        }

        private static BlockPos find(Ctx ctx) {
            AABB box = territory(ctx);
            return LifeEffects.blockEntities(ctx.level(), box).stream()
                    .filter(be -> be instanceof FurnaceGeneratorBlockEntity)
                    .map(BlockEntity::getBlockPos)
                    .min(Comparator.<BlockPos>comparingInt(p -> p.getX()).thenComparingInt(p -> p.getZ()).thenComparingInt(p -> p.getY()))
                    .orElse(null);
        }

        static net.neoforged.neoforge.energy.EnergyStorage storage(BaseGeneratorBlockEntity gen) {
            try {
                if (energyField == null) {
                    energyField = BaseGeneratorBlockEntity.class.getDeclaredField("energy");
                    energyField.setAccessible(true);
                }
                return (net.neoforged.neoforge.energy.EnergyStorage) energyField.get(gen);
            } catch (ReflectiveOperationException e) {
                return null;
            }
        }

        private record Target(BlockPos pos, long rescanAt) {}
    }

    public static final class Temporal extends CareerEffect {
        public Temporal() { super("temporal", 0, 1); }

        @Override
        protected void fire(Ctx ctx) {
            ServerLevel level = ctx.level();
            if (level.getGameTime() % 20 != 0) return;
            int[] r = ctx.radii();
            BlockPos o = ctx.pos();
            for (BlockPos p : BlockPos.betweenClosed(o.offset(-r[0], -r[1], -r[0]), o.offset(r[0], r[1], r[0]))) {
                if (!level.isInWorldBounds(p) || !level.hasChunkAt(p)) continue;
                BlockState s = level.getBlockState(p);
                if (s.hasBlockEntity() && !p.equals(o)) {
                    BlockEntity be = level.getBlockEntity(p);
                    if (be != null && !BuiltInRegistries.BLOCK_ENTITY_TYPE.wrapAsHolder(be.getType()).is(NO_ACCELERATION)) Boosts.add(level, p.immutable());
                }
                if (s.isRandomlyTicking()) s.randomTick(level, p, level.getRandom());
            }
        }
    }

    /** Ticking block entities sped up by temporal bees: 4 extra ticks per world tick for 40 ticks, never stacked. */
    public static final class Boosts {
        private static final Map<ServerLevel, Map<BlockPos, Integer>> ACTIVE = new WeakHashMap<>();

        static void add(ServerLevel level, BlockPos pos) {
            ACTIVE.computeIfAbsent(level, l -> new HashMap<>()).putIfAbsent(pos, 40);
        }

        @SuppressWarnings({"unchecked", "rawtypes"})
        public static void tick(ServerLevel level) {
            Map<BlockPos, Integer> map = ACTIVE.get(level);
            if (map == null || map.isEmpty()) return;
            Iterator<Map.Entry<BlockPos, Integer>> it = map.entrySet().iterator();
            while (it.hasNext()) {
                Map.Entry<BlockPos, Integer> e = it.next();
                BlockEntity be = level.getBlockEntity(e.getKey());
                BlockState state = level.getBlockState(e.getKey());
                BlockEntityTicker ticker = be == null || be.isRemoved() ? null : state.getTicker(level, (BlockEntityType) be.getType());
                if (ticker == null || e.getValue() <= 1) {
                    it.remove();
                    if (ticker == null) continue;
                } else {
                    e.setValue(e.getValue() - 1);
                }
                for (int i = 0; i < 4; i++) ticker.tick(level, e.getKey(), state, be);
            }
        }
    }

    private PowerEffects() {}
}

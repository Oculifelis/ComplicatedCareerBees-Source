package dev.oculifelis.careerbees.effect;

import dev.oculifelis.careerbees.Config;
import dev.oculifelis.careerbees.item.BarkItem;
import dev.oculifelis.careerbees.registry.ModItems;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Explosion;
import net.minecraft.world.level.ExplosionDamageCalculator;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseFireBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.material.PushReaction;
import net.neoforged.neoforge.common.Tags;

import java.util.List;
import java.util.Map;

/** Effects that change blocks in the territory. */
public final class WorldEffects {

    public static final class Burning extends CareerEffect {
        public Burning() { super("burning", 4, 0.01); }

        @Override
        protected void fire(Ctx ctx) {
            if (!griefing(ctx)) return;
            ServerLevel level = ctx.level();
            for (int a = 0; a < Config.WORLD_ATTEMPTS.get(); a++) {
                for (BlockPos p : randomColumn(ctx)) {
                    if (!level.isInWorldBounds(p) || !level.isEmptyBlock(p)) continue;
                    if (burnable(level, p)) {
                        level.setBlockAndUpdate(p, BaseFireBlock.getState(level, p));
                        return;
                    }
                }
            }
        }

        static boolean burnable(ServerLevel level, BlockPos p) {
            BlockPos below = p.below();
            if (level.getBlockState(below).isFaceSturdy(level, below, Direction.UP)) return true;
            for (Direction d : Direction.values()) {
                BlockPos n = p.relative(d);
                if (level.getBlockState(n).isFlammable(level, n, d.getOpposite())) return true;
            }
            return false;
        }
    }

    public static final class Mason extends CareerEffect {
        public Mason() { super("mason", 10, 1); }

        @Override
        protected void fire(Ctx ctx) {
            ServerLevel level = ctx.level();
            for (int a = 0; a < Config.WORLD_ATTEMPTS.get(); a++) {
                for (BlockPos p : randomColumn(ctx)) {
                    if (!level.isInWorldBounds(p)) continue;
                    Block next = CareerDataMaps.masonry(level.getBlockState(p).getBlock());
                    if (next != null) {
                        level.setBlockAndUpdate(p, next.defaultBlockState());
                        return;
                    }
                }
            }
        }
    }

    public static final class Lumber extends CareerEffect {
        public Lumber() { super("lumber", 200, 1); }

        @Override
        protected void fire(Ctx ctx) {
            ServerLevel level = ctx.level();
            for (int a = 0; a < Config.WORLD_ATTEMPTS.get(); a++) {
                for (BlockPos p : randomColumn(ctx)) {
                    if (!level.isInWorldBounds(p)) continue;
                    BlockState s = level.getBlockState(p);
                    if (!s.is(BlockTags.LOGS) || !isTree(level, p)) continue;
                    int count = 1 + ctx.random().nextInt(1 + ctx.random().nextInt(8));
                    output(ctx, BarkItem.of(ModItems.BARK.get(), s.getBlock(), count));
                    return;
                }
            }
        }

        static boolean isTree(ServerLevel level, BlockPos log) {
            BlockPos.MutableBlockPos m = log.mutable();
            while (level.isInWorldBounds(m) && level.getBlockState(m).is(BlockTags.LOGS)) m.move(Direction.UP);
            return level.getBlockState(m).is(BlockTags.LEAVES);
        }
    }

    public static final class Teleposition extends CareerEffect {
        public Teleposition() { super("teleposition", 0, 1); }

        @Override
        protected void fire(Ctx ctx) {
            if (!griefing(ctx)) return;
            ServerLevel level = ctx.level();
            BlockPos a = randomPos(ctx), b = randomPos(ctx);
            if (!level.isInWorldBounds(a) || !level.isInWorldBounds(b)) return;
            BlockState sa = level.getBlockState(a), sb = level.getBlockState(b);
            if (sa == sb || !movable(level, a, sa) || !movable(level, b, sb)) return;
            if (sa.getDestroySpeed(level, a) != sb.getDestroySpeed(level, b)) return;
            if (sa.getSoundType(level, a, null) != sb.getSoundType(level, b, null)) return;
            level.setBlock(a, sb, Block.UPDATE_ALL);
            level.setBlock(b, sa, Block.UPDATE_ALL);
        }

        static boolean movable(ServerLevel level, BlockPos p, BlockState s) {
            return !s.isAir() && !s.hasBlockEntity() && s.isSolidRender() && s.canOcclude()
                    && s.getPistonPushReaction() == PushReaction.NORMAL && s.getDestroySpeed(level, p) >= 0;
        }
    }

    public static final class Blast extends CareerEffect {
        private static final ExplosionDamageCalculator SPARE_CONTAINERS = new ExplosionDamageCalculator() {
            @Override
            public boolean shouldBlockExplode(Explosion explosion, BlockGetter level, BlockPos pos, BlockState state, float power) {
                return !state.hasBlockEntity() && super.shouldBlockExplode(explosion, level, pos, state, power);
            }
        };

        public Blast() { super("explosion", 20, 0.05); }

        @Override
        protected void fire(Ctx ctx) {
            int radius = 2 + ctx.random().nextInt(Config.EXPLOSION_MAX_RADIUS.get() - 1);
            BlockPos p = ctx.pos();
            ctx.level().explode(null, null, SPARE_CONTAINERS, p.getX() + 0.5, p.getY() + 0.5, p.getZ() + 0.5, radius, false,
                    griefing(ctx) ? Level.ExplosionInteraction.BLOCK : Level.ExplosionInteraction.NONE);
        }
    }

    public static final class Mining extends CareerEffect {
        public enum Tool { PLAIN, FORTUNE, SILK }

        private final Tool tool;

        public Mining(String id, Tool tool) {
            super(id, 40, 1);
            this.tool = tool;
        }

        @Override
        protected void fire(Ctx ctx) {
            ServerLevel level = ctx.level();
            int[] r = ctx.radii();
            BlockPos o = ctx.pos();
            for (int a = 0; a < Config.MINING_ATTEMPTS.get(); a++) {
                int x = o.getX() + ctx.random().nextInt(2 * r[0] + 1) - r[0];
                int z = o.getZ() + ctx.random().nextInt(2 * r[0] + 1) - r[0];
                if (!level.hasChunkAt(new BlockPos(x, o.getY(), z))) continue;
                int top = Math.min(level.getMaxY(), level.getHeight(Heightmap.Types.WORLD_SURFACE, x, z) + 16);
                int bottom = level.getMinY();
                if (top <= bottom) continue;
                BlockPos p = new BlockPos(x, bottom + ctx.random().nextInt(top - bottom), z);
                BlockState s = level.getBlockState(p);
                if (!s.is(Tags.Blocks.ORES)) continue;
                List<ItemStack> drops = Block.getDrops(s, level, p, level.getBlockEntity(p), null, tool(level));
                drops.forEach(d -> output(ctx, d));
                level.setBlock(p, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
                return;
            }
        }

        private ItemStack tool(ServerLevel level) {
            ItemStack pick = new ItemStack(Items.NETHERITE_PICKAXE);
            var lookup = level.registryAccess().lookupOrThrow(Registries.ENCHANTMENT);
            if (tool == Tool.FORTUNE) pick.enchant(lookup.getOrThrow(Enchantments.FORTUNE), 3);
            if (tool == Tool.SILK) pick.enchant(lookup.getOrThrow(Enchantments.SILK_TOUCH), 1);
            return pick;
        }
    }

    public static final class Pickup extends CareerEffect {
        public Pickup() { super("pickup", 20, 1); }

        @Override
        protected void fire(Ctx ctx) {
            // Output still waiting from last time means the housing is full: leave items on the ground, or void them.
            boolean full = ItemFilter.outputFull(ctx);
            for (ItemEntity item : ctx.level().getEntitiesOfClass(ItemEntity.class, territory(ctx))) {
                if (!item.isAlive() || !ItemFilter.passes(ctx, item.getItem())) continue;
                if (full) {
                    if (ItemFilter.voidExcess(ctx)) item.discard();
                    continue;
                }
                output(ctx, item.getItem().copy());
                item.discard();
            }
        }
    }

    private WorldEffects() {}
}

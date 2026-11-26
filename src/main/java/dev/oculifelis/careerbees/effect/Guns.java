package dev.oculifelis.careerbees.effect;

import com.accbdd.complicated_bees.bees.GeneticHelper;
import com.accbdd.complicated_bees.bees.effect.IBeeEffect;
import com.accbdd.complicated_bees.bees.gene.GeneEffect;
import com.accbdd.complicated_bees.bees.gene.GeneProductivity;
import com.accbdd.complicated_bees.bees.gene.GeneTerritory;
import com.accbdd.complicated_bees.bees.gene.enums.EnumProductivity;
import com.accbdd.complicated_bees.block.entity.FurnaceGeneratorBlockEntity;
import com.accbdd.complicated_bees.port.PortCapabilities;
import dev.oculifelis.careerbees.Config;
import dev.oculifelis.careerbees.item.BarkItem;
import dev.oculifelis.careerbees.registry.ModItems;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.EntityTypeTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.decoration.painting.Painting;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.monster.zombie.ZombieVillager;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.BaseFireBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.LootTable;
import net.minecraft.world.level.storage.loot.parameters.LootContextParamSets;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.common.util.FakePlayerFactory;
import net.neoforged.neoforge.energy.IEnergyStorage;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/**
 * What a swarm fired from the Bee-Bee Gun does with each career effect. A swarm latches onto the first thing its
 * effect accepts, waits out the cooldown, acts once on it and dies. Effects with no entry here can't be fired.
 */
public final class Guns {
    /** What a swarm latched onto: one of entity, item or block is set. */
    public record Target(@Nullable LivingEntity entity, @Nullable ItemEntity item, @Nullable BlockPos block, Direction face) {}

    public interface Gun {
        default boolean entity(ServerLevel level, LivingEntity e) { return false; }
        default boolean item(ServerLevel level, ItemEntity e) { return false; }
        default boolean block(ServerLevel level, BlockPos pos, BlockState state, Direction face) { return false; }

        /** Ticks the swarm waits on its target before acting. */
        default int cooldown(CareerEffect effect, double speed, int lifespan, RandomSource random) {
            return defaultCooldown(effect, speed, random);
        }

        void act(CareerEffect effect, CareerEffect.Ctx ctx, Target t);
    }

    private static final Map<String, Gun> GUNS = new HashMap<>();

    /** Interval over speed, with the chance re-rolled until it succeeds. */
    public static int defaultCooldown(CareerEffect effect, double speed, RandomSource random) {
        int interval = Math.max(1, effect.interval());
        int tries = 1;
        double chance = effect.chance();
        if (chance > 0 && chance < 1) while (tries < 1000 && random.nextDouble() >= chance) tries++;
        return (int) Math.max(1, Math.min(6000, Math.round(tries * interval / Math.max(0.05, speed))));
    }

    @Nullable
    public static CareerEffect effect(ItemStack queen) {
        Object v = GeneticHelper.getGeneValue(queen, GeneEffect.ID, true);
        return v instanceof CareerEffect c ? c : null;
    }

    @Nullable
    public static Gun gun(@Nullable IBeeEffect effect) {
        if (!(effect instanceof CareerEffect c) || !Config.effectEnabled(c.id())) return null;
        if (c instanceof PedestalEffect p) return pedestal(p);
        return GUNS.get(c.id());
    }

    /** Productivity gene alone: housing mods don't reach a swarm. */
    public static double speed(ItemStack queen) {
        return ((EnumProductivity) GeneticHelper.getGeneValue(queen, GeneProductivity.ID, true)).value;
    }

    public static int[] radii(ItemStack queen) {
        int[] r = (int[]) GeneticHelper.getGeneValue(queen, GeneTerritory.ID, true);
        return new int[]{r[0], r[1]};
    }

    private static boolean griefing(ServerLevel level) {
        return Config.MOB_GRIEFING.get() && level.getGameRules().get(net.minecraft.world.level.gamerules.GameRules.MOB_GRIEFING);
    }

    private static boolean enemy(LivingEntity e) {
        return e instanceof Mob && e instanceof Enemy && e.isAlive();
    }

    private static Gun pedestal(PedestalEffect p) {
        return new Gun() {
            @Override
            public boolean item(ServerLevel level, ItemEntity e) {
                return p.workable(level, e.getItem());
            }

            @Override
            public void act(CareerEffect effect, CareerEffect.Ctx ctx, Target t) {
                ItemEntity e = t.item();
                if (e == null || !e.isAlive() || !p.workable(ctx.level(), e.getItem())) return;
                ItemStack next = p.change(ctx, e.getItem().copy());
                if (next == null) return;
                e.setItem(next);
                PedestalEffect.pulse(ctx.level(), BlockPos.containing(e.position()).below(), p.pulseKind());
            }
        };
    }

    private static void register(String id, Gun gun) {
        GUNS.put(id, gun);
    }

    /** One-shot entity gun. */
    private interface OnEntity extends Gun {
        boolean accepts(ServerLevel level, LivingEntity e);

        void hit(CareerEffect.Ctx ctx, LivingEntity e);

        @Override
        default boolean entity(ServerLevel level, LivingEntity e) { return accepts(level, e); }

        @Override
        default void act(CareerEffect effect, CareerEffect.Ctx ctx, Target t) {
            LivingEntity e = t.entity();
            if (e != null && e.isAlive() && accepts(ctx.level(), e)) hit(ctx, e);
        }
    }

    /** One-shot block gun. */
    private interface OnBlock extends Gun {
        boolean accepts(ServerLevel level, BlockPos pos, BlockState state, Direction face);

        void hit(CareerEffect.Ctx ctx, BlockPos pos, BlockState state, Direction face);

        @Override
        default boolean block(ServerLevel level, BlockPos pos, BlockState state, Direction face) { return accepts(level, pos, state, face); }

        @Override
        default void act(CareerEffect effect, CareerEffect.Ctx ctx, Target t) {
            BlockPos p = t.block();
            if (p == null) return;
            BlockState s = ctx.level().getBlockState(p);
            if (accepts(ctx.level(), p, s, t.face())) hit(ctx, p, s, t.face());
        }
    }

    private static void entity(String id, java.util.function.BiPredicate<ServerLevel, LivingEntity> accepts,
                               java.util.function.BiConsumer<CareerEffect.Ctx, LivingEntity> hit) {
        register(id, new OnEntity() {
            public boolean accepts(ServerLevel level, LivingEntity e) { return accepts.test(level, e); }
            public void hit(CareerEffect.Ctx ctx, LivingEntity e) { hit.accept(ctx, e); }
        });
    }

    private interface BlockTest { boolean test(ServerLevel level, BlockPos pos, BlockState state, Direction face); }

    private interface BlockHit { void hit(CareerEffect.Ctx ctx, BlockPos pos, BlockState state, Direction face); }

    private static void block(String id, BlockTest accepts, BlockHit hit) {
        register(id, new OnBlock() {
            public boolean accepts(ServerLevel level, BlockPos pos, BlockState state, Direction face) { return accepts.test(level, pos, state, face); }
            public void hit(CareerEffect.Ctx ctx, BlockPos pos, BlockState state, Direction face) { hit.hit(ctx, pos, state, face); }
        });
    }

    private static FakePlayer fake(ServerLevel level) {
        return FakePlayerFactory.getMinecraft(level);
    }

    static {
        entity("healing", (l, e) -> true, (ctx, e) -> {
            e.heal(0.5f);
            e.addEffect(new MobEffectInstance(MobEffects.REGENERATION, 80, 0));
        });
        entity("curative", (l, e) -> true, (ctx, e) -> {
            if (e instanceof ZombieVillager zv) {
                if (zv.isConverting()) zv.hurtServer(ctx.level(), ctx.level().damageSources().generic(), 1f);
                else LifeEffects.Curative.cure(zv, Math.round((3600 + ctx.random().nextInt(2401)) * zv.getMaxHealth() / 20f));
            } else if (e.getType().builtInRegistryHolder().is(EntityTypeTags.UNDEAD)) {
                e.addEffect(new MobEffectInstance(MobEffects.POISON, 80, 0));
            } else {
                e.heal(0.1f);
                e.getActiveEffects().stream().filter(i -> !i.getEffect().value().isBeneficial()).map(MobEffectInstance::getEffect).toList().forEach(e::removeEffect);
            }
        });
        entity("electrify", (l, e) -> true, (ctx, e) -> {
            boolean wasBurning = e.isOnFire();
            e.hurtServer(ctx.level(), ctx.level().damageSources().lightningBolt(), 5f / 50f);
            if (!wasBurning) e.clearFire();
        });
        entity("husbandry", (l, e) -> e instanceof Animal a && a.getAge() == 0 && a.canFallInLove() && !a.isInLove(),
                (ctx, e) -> ((Animal) e).setInLove(null));
        entity("butcher", (l, e) -> e instanceof Animal a && !a.isBaby() && othersAround(l, a) >= 2,
                (ctx, e) -> e.hurtServer(ctx.level(), ctx.level().damageSources().generic(), 100f));
        register("police", new OnEntity() {
            public boolean accepts(ServerLevel level, LivingEntity e) { return enemy(e); }

            public void hit(CareerEffect.Ctx ctx, LivingEntity e) {
                DamageSource src = ctx.level().damageSources().playerAttack(fake(ctx.level()));
                if (e.isInvulnerableTo(ctx.level(), src)) return;
                float health = e.getHealth();
                e.hurtServer(ctx.level(), src, health < 0.01f ? 1000f : Math.min(2f, health - 0.005f));
            }

            public int cooldown(CareerEffect effect, double speed, int lifespan, RandomSource random) { return 200; }
        });
        entity("taxation", (l, e) -> enemy(e), (ctx, e) -> {
            Mob mob = (Mob) e;
            ServerLevel level = ctx.level();
            FakePlayer fp = fake(level);
            DamageSource src = level.damageSources().playerAttack(fp);
            if (mob.isInvulnerableTo(level, src)) return;
            mob.getLootTable().ifPresent(key -> {
                LootTable table = level.getServer().reloadableRegistries().getLootTable(key);
                LootParams params = new LootParams.Builder(level)
                        .withParameter(LootContextParams.THIS_ENTITY, mob)
                        .withParameter(LootContextParams.ORIGIN, mob.position())
                        .withParameter(LootContextParams.DAMAGE_SOURCE, src)
                        .withParameter(LootContextParams.ATTACKING_ENTITY, fp)
                        .withParameter(LootContextParams.LAST_DAMAGE_PLAYER, fp)
                        .create(LootContextParamSets.ENTITY);
                table.getRandomItems(params, s -> CareerEffect.output(ctx, s));
            });
        });
        entity("thief", (l, e) -> e instanceof Player p && !p.isSpectator() && !p.isCreative(), (ctx, e) -> {
            Player p = (Player) e;
            Inventory inv = p.getInventory();
            for (int i = 0; i < 20; i++) {
                int slot = ctx.random().nextInt(inv.getContainerSize());
                ItemStack s = inv.getItem(slot);
                if (s.isEmpty()) continue;
                ItemStack taken = s.split(1);
                inv.setChanged();
                CareerEffect.output(ctx, taken);
                LifeEffects.Thief.tell(ctx, p, taken);
                return;
            }
        });

        block("burning", (l, p, s, f) -> griefing(l) && !s.isAir() && l.isEmptyBlock(p.above()) && WorldEffects.Burning.burnable(l, p.above()),
                (ctx, p, s, f) -> ctx.level().setBlockAndUpdate(p.above(), BaseFireBlock.getState(ctx.level(), p.above())));
        block("mason", (l, p, s, f) -> CareerDataMaps.masonry(s.getBlock()) != null,
                (ctx, p, s, f) -> {
                    Block next = CareerDataMaps.masonry(s.getBlock());
                    if (next != null) ctx.level().setBlockAndUpdate(p, next.defaultBlockState());
                });
        block("lumber", (l, p, s, f) -> s.is(BlockTags.LOGS) && WorldEffects.Lumber.isTree(l, p), (ctx, p, s, f) -> {
            int count = 1 + ctx.random().nextInt(1 + ctx.random().nextInt(8));
            CareerEffect.output(ctx, BarkItem.of(ModItems.BARK.get(), s.getBlock(), count));
        });
        block("painting", (l, p, s, f) -> griefing(l) && f.getAxis().isHorizontal() && s.isSolidRender()
                        && SocialEffects.Artist.paintable(s.getSoundType()) && l.getBlockState(p.relative(f)).isAir(),
                (ctx, p, s, f) -> {
                    Optional<Painting> painting = Painting.create(ctx.level(), p.relative(f), f);
                    if (painting.isEmpty() || !painting.get().survives()) return;
                    ctx.level().addFreshEntity(painting.get());
                    painting.get().playPlacementSound();
                });
        block("teleposition", (l, p, s, f) -> griefing(l) && WorldEffects.Teleposition.movable(l, p, s), (ctx, a, sa, f) -> {
            ServerLevel level = ctx.level();
            for (int i = 0; i < Math.max(8, Config.WORLD_ATTEMPTS.get() * 4); i++) {
                BlockPos b = CareerEffect.randomPos(ctx);
                if (b.equals(a) || !level.isInWorldBounds(b)) continue;
                BlockState sb = level.getBlockState(b);
                if (sa == sb || !WorldEffects.Teleposition.movable(level, b, sb)) continue;
                if (sa.getDestroySpeed(level, a) != sb.getDestroySpeed(level, b)) continue;
                if (sa.getSoundType(level, a, null) != sb.getSoundType(level, b, null)) continue;
                level.setBlock(a, sb, Block.UPDATE_ALL);
                level.setBlock(b, sa, Block.UPDATE_ALL);
                return;
            }
        });
        register("energy", new OnBlock() {
            public boolean accepts(ServerLevel level, BlockPos pos, BlockState state, Direction face) {
                IEnergyStorage e = energy(level, pos, state, face);
                return e != null && e.canReceive();
            }

            public void hit(CareerEffect.Ctx ctx, BlockPos pos, BlockState state, Direction face) {
                IEnergyStorage e = energy(ctx.level(), pos, state, face);
                if (e == null) return;
                long amount = (long) Math.ceil(Config.ENERGY_PER_TICK.get() * ctx.speed() * ctx.speed()) * waited(ctx);
                e.receiveEnergy((int) Math.min(Integer.MAX_VALUE, amount), false);
            }

            public int cooldown(CareerEffect effect, double speed, int lifespan, RandomSource random) {
                return Math.min(120, lifespan) * 12;
            }
        });
        register("clockwinding", new OnBlock() {
            public boolean accepts(ServerLevel level, BlockPos pos, BlockState state, Direction face) {
                return level.getBlockEntity(pos) instanceof FurnaceGeneratorBlockEntity;
            }

            public void hit(CareerEffect.Ctx ctx, BlockPos pos, BlockState state, Direction face) {
                if (!(ctx.level().getBlockEntity(pos) instanceof FurnaceGeneratorBlockEntity gen)) return;
                net.neoforged.neoforge.energy.EnergyStorage store = PowerEffects.Clockwinding.storage(gen);
                long per = Math.round(Math.min(2 + 4 * ctx.speed(), 8) / 8.0 * FurnaceGeneratorBlockEntity.BASE_GENERATE);
                if (store != null && store.receiveEnergy((int) Math.min(Integer.MAX_VALUE, per * waited(ctx)), false) > 0) gen.setChanged();
            }

            public int cooldown(CareerEffect effect, double speed, int lifespan, RandomSource random) { return 200; }
        });
        register("temporal", new OnBlock() {
            public boolean accepts(ServerLevel level, BlockPos pos, BlockState state, Direction face) {
                if (state.isRandomlyTicking()) return true;
                BlockEntity be = state.hasBlockEntity() ? level.getBlockEntity(pos) : null;
                return be != null && !BuiltInRegistries.BLOCK_ENTITY_TYPE.wrapAsHolder(be.getType()).is(PowerEffects.NO_ACCELERATION);
            }

            public void hit(CareerEffect.Ctx ctx, BlockPos pos, BlockState state, Direction face) {
                BlockEntity be = state.hasBlockEntity() ? ctx.level().getBlockEntity(pos) : null;
                if (be != null && !BuiltInRegistries.BLOCK_ENTITY_TYPE.wrapAsHolder(be.getType()).is(PowerEffects.NO_ACCELERATION))
                    PowerEffects.Boosts.add(ctx.level(), pos.immutable());
                if (state.isRandomlyTicking()) state.randomTick(ctx.level(), pos, ctx.random());
            }

            public int cooldown(CareerEffect effect, double speed, int lifespan, RandomSource random) {
                return (int) Math.max(1, Math.round(20 / Math.max(0.05, speed)));
            }
        });
    }

    /** How long the swarm sat on its target; energy guns deliver that many ticks' worth at once. */
    private static long waited(CareerEffect.Ctx ctx) {
        return ctx.anchor() instanceof Waiting w ? Math.max(1, w.waited()) : 1;
    }

    /** Implemented by the swarm so energy guns know how long it waited. */
    public interface Waiting {
        int waited();
    }

    @Nullable
    private static IEnergyStorage energy(ServerLevel level, BlockPos pos, BlockState state, Direction face) {
        return PortCapabilities.find(level, PortCapabilities.EnergyStorage.BLOCK, pos, state, level.getBlockEntity(pos), face);
    }

    /** Adults of the same type around a target, inside a default territory box. */
    private static long othersAround(ServerLevel level, Animal a) {
        AABB box = new AABB(a.blockPosition()).inflate(9, 6, 9);
        return level.getEntitiesOfClass(Animal.class, box, o -> o != a && !o.isBaby() && o.getType() == a.getType()).size();
    }

    /** Where drops and particles go for a target. */
    public static Vec3 point(Target t) {
        if (t.entity() != null) return t.entity().position().add(0, t.entity().getBbHeight() / 2, 0);
        if (t.item() != null) return t.item().position();
        return Vec3.atCenterOf(t.block()).add(Vec3.atLowerCornerOf(t.face().getUnitVec3i()).scale(0.5));
    }

    private Guns() {}
}

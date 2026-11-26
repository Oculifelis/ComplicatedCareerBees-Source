package dev.oculifelis.careerbees.effect;

import com.accbdd.complicated_bees.bees.GeneticHelper;
import com.accbdd.complicated_bees.bees.IBeeHousing;
import com.accbdd.complicated_bees.bees.gene.GeneLifespan;
import com.accbdd.complicated_bees.bees.gene.enums.EnumLifespan;
import com.accbdd.complicated_bees.item.BeeItem;
import com.accbdd.complicated_bees.item.QueenItem;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.EntityTypeTags;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.monster.Creeper;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.monster.zombie.ZombieVillager;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.LootTable;
import net.minecraft.world.level.storage.loot.parameters.LootContextParamSets;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraft.world.phys.AABB;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.common.util.FakePlayerFactory;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

/** Effects that act on living things in the territory. */
public final class LifeEffects {
    public static final String BOMB_TAG = "careerbees_bomb";

    public static final class Healing extends CareerEffect {
        public Healing() { super("healing", 10, 1); }

        @Override
        protected void fire(Ctx ctx) {
            for (LivingEntity e : entities(ctx, LivingEntity.class)) {
                e.heal(0.5f);
                e.addEffect(new MobEffectInstance(MobEffects.REGENERATION, 80, 0));
            }
        }
    }

    public static final class Curative extends CareerEffect {
        private static Method startConverting;

        public Curative() { super("curative", 10, 1); }

        @Override
        protected void fire(Ctx ctx) {
            for (LivingEntity e : entities(ctx, LivingEntity.class)) {
                if (e instanceof ZombieVillager zv) {
                    if (!zv.isConverting()) {
                        int time = 3600 + ctx.random().nextInt(2401);
                        cure(zv, Math.round(time * zv.getMaxHealth() / 20f));
                    }
                } else if (e.getType().builtInRegistryHolder().is(EntityTypeTags.UNDEAD)) {
                    e.addEffect(new MobEffectInstance(MobEffects.POISON, 80, 0));
                } else {
                    e.heal(0.1f);
                    e.getActiveEffects().stream().filter(i -> !i.getEffect().value().isBeneficial()).map(MobEffectInstance::getEffect).toList().forEach(e::removeEffect);
                }
            }
        }

        static void cure(ZombieVillager zv, int time) {
            try {
                if (startConverting == null) {
                    startConverting = ZombieVillager.class.getDeclaredMethod("startConverting", UUID.class, int.class);
                    startConverting.setAccessible(true);
                }
                startConverting.invoke(zv, null, time);
            } catch (ReflectiveOperationException ignored) {
            }
        }
    }

    public static final class Electrify extends CareerEffect {
        public Electrify() { super("electrify", 5, 0.1); }

        @Override
        protected void fire(Ctx ctx) {
            List<LivingEntity> list = entities(ctx, LivingEntity.class);
            if (list.isEmpty()) return;
            LivingEntity target = list.get(ctx.random().nextInt(list.size()));
            boolean wasBurning = target.isOnFire();
            target.hurtServer(ctx.level(), ctx.level().damageSources().lightningBolt(), 5f / 50f);
            if (!wasBurning) target.clearFire();
        }
    }

    public static final class Husbandry extends CareerEffect {
        public Husbandry() { super("husbandry", 20, 1); }

        @Override
        protected void fire(Ctx ctx) {
            List<Animal> ready = new ArrayList<>();
            for (Animal a : entities(ctx, Animal.class)) {
                if (a.getAge() == 0 && a.canFallInLove() && !a.isInLove()) ready.add(a);
            }
            if (ready.isEmpty() || ready.size() > 16) return;
            ready.get(ctx.random().nextInt(ready.size())).setInLove(null);
        }
    }

    public static final class Butcher extends CareerEffect {
        public Butcher() { super("butcher", 20, 1); }

        @Override
        protected void fire(Ctx ctx) {
            List<Animal> adults = new ArrayList<>();
            for (Animal a : entities(ctx, Animal.class)) if (!a.isBaby()) adults.add(a);
            if (adults.size() < 2) return;
            adults.sort(Comparator.<Animal>comparingInt(a -> -Math.abs(a.getAge()))
                    .thenComparing(Animal::isInLove)
                    .thenComparingDouble(LivingEntity::getHealth));
            for (Animal a : adults) {
                long same = adults.stream().filter(o -> o != a && o.getType() == a.getType()).count();
                if (same >= 2) {
                    a.hurtServer(ctx.level(), ctx.level().damageSources().generic(), 100f);
                    return;
                }
            }
        }
    }

    public static final class Taxation extends CareerEffect {
        public Taxation() { super("taxation", 100, 1); }

        @Override
        protected void fire(Ctx ctx) {
            List<Mob> mobs = new ArrayList<>();
            for (Mob m : entities(ctx, Mob.class)) if (m instanceof Enemy) mobs.add(m);
            if (mobs.isEmpty()) return;
            Mob mob = mobs.get(ctx.random().nextInt(mobs.size()));
            ServerLevel level = ctx.level();
            FakePlayer fp = FakePlayerFactory.getMinecraft(level);
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
                table.getRandomItems(params, s -> output(ctx, s));
            });
        }
    }

    public static final class CreeperSpawn extends CareerEffect {
        private static Field radiusField;

        public CreeperSpawn() { super("creeper", 2, 0.2); }

        @Override
        protected void fire(Ctx ctx) {
            ServerLevel level = ctx.level();
            if (level.getEntitiesOfClass(Creeper.class, territory(ctx)).size() > dev.oculifelis.careerbees.Config.CREEPER_CAP.get()) return;
            int[] r = ctx.radii();
            BlockPos o = ctx.pos();
            double x = o.getX() + ctx.random().nextInt(2 * r[0] + 1) - r[0] + 0.5;
            double y = o.getY() + ctx.random().nextInt(3) - 1;
            double z = o.getZ() + ctx.random().nextInt(2 * r[0] + 1) - r[0] + 0.5;
            Creeper creeper = dev.oculifelis.careerbees.compat.Mc.CREEPER.create(level, EntitySpawnReason.MOB_SUMMONED);
            if (creeper == null) return;
            creeper.setPos(x, y, z);
            if (!level.noCollision(creeper)) return;
            MobEffectInstance wither = new MobEffectInstance(MobEffects.WITHER, 1000, 0);
            creeper.addEffect(wither);
            creeper.addTag(BOMB_TAG);
            setRadius(creeper, 5);
            level.addFreshEntity(creeper);
        }

        private static void setRadius(Creeper c, int radius) {
            try {
                if (radiusField == null) {
                    radiusField = Creeper.class.getDeclaredField("explosionRadius");
                    radiusField.setAccessible(true);
                }
                radiusField.setInt(c, radius);
            } catch (ReflectiveOperationException ignored) {
            }
        }
    }

    public static final class Assassin extends CareerEffect {
        public Assassin() { super("assassin", 10, 1); }

        @Override
        protected void fire(Ctx ctx) {
            Object own = GeneticHelper.getSpecies(ctx.queen(), true);
            for (BlockEntity other : blockEntities(ctx.level(), territory(ctx))) {
                if (other == ctx.be() || !(other instanceof IBeeHousing housing)) continue;
                ItemStack queen = housing.getBeeItemHandler().getStackInSlot(0);
                if (!(queen.getItem() instanceof QueenItem)) continue;
                if (own.equals(GeneticHelper.getSpecies(queen, true))) continue;
                EnumLifespan life = (EnumLifespan) GeneticHelper.getGeneValue(queen, GeneLifespan.ID, true);
                if (BeeItem.getAge(queen) >= life.value) continue;
                BeeItem.setAge(queen, life.value);
                other.setChanged();
                return;
            }
        }
    }

    public static final class Thief extends CareerEffect {
        public Thief() { super("thief", 10, 1); }

        @Override
        protected void fire(Ctx ctx) {
            for (Player p : entities(ctx, Player.class)) {
                if (ctx.random().nextBoolean()) continue;
                Inventory inv = p.getInventory();
                for (int i = 0; i < 5; i++) {
                    int slot = ctx.random().nextInt(inv.getContainerSize());
                    ItemStack s = inv.getItem(slot);
                    if (s.isEmpty()) continue;
                    ItemStack taken = s.split(1);
                    inv.setChanged();
                    output(ctx, taken);
                    tell(ctx, p, taken);
                    break;
                }
            }
        }

        static void tell(Ctx ctx, Player p, ItemStack taken) {
            if (!(p instanceof ServerPlayer sp)) return;
            UUID owner = ctx.be() instanceof IBeeHousing h ? h.getOwner() : null;
            Component msg;
            if (owner != null && owner.equals(p.getUUID())) {
                msg = Component.translatable("message.careerbees.thief.self", taken.getHoverName());
            } else {
                ServerPlayer op = owner == null ? null : ctx.level().getServer().getPlayerList().getPlayer(owner);
                msg = op != null
                        ? Component.translatable("message.careerbees.thief.owner", op.getDisplayName(), taken.getHoverName())
                        : Component.translatable("message.careerbees.thief.anon", taken.getHoverName());
            }
            sp.sendOverlayMessage(msg);
        }
    }

    /** Block entities in the loaded chunks overlapping a box. */
    public static List<BlockEntity> blockEntities(ServerLevel level, AABB box) {
        List<BlockEntity> out = new ArrayList<>();
        int minCx = ((int) Math.floor(box.minX)) >> 4, maxCx = ((int) Math.floor(box.maxX)) >> 4;
        int minCz = ((int) Math.floor(box.minZ)) >> 4, maxCz = ((int) Math.floor(box.maxZ)) >> 4;
        for (int cx = minCx; cx <= maxCx; cx++) {
            for (int cz = minCz; cz <= maxCz; cz++) {
                if (!level.hasChunk(cx, cz)) continue;
                for (BlockEntity be : level.getChunk(cx, cz).getBlockEntities().values()) {
                    BlockPos p = be.getBlockPos();
                    if (box.contains(p.getX() + 0.5, p.getY() + 0.5, p.getZ() + 0.5)) out.add(be);
                }
            }
        }
        return out;
    }

    private LifeEffects() {}
}

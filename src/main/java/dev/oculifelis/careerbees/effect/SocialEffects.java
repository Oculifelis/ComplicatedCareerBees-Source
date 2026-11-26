package dev.oculifelis.careerbees.effect;

import com.accbdd.complicated_bees.bees.GeneticHelper;
import com.accbdd.complicated_bees.bees.Genome;
import dev.oculifelis.careerbees.Config;
import dev.oculifelis.careerbees.item.ReportItem;
import dev.oculifelis.careerbees.registry.ModComponents;
import dev.oculifelis.careerbees.registry.ModItems;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.protocol.game.ClientboundSoundPacket;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.decoration.painting.Painting;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.common.util.FakePlayerFactory;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/** Police, artist and jazz bees. */
public final class SocialEffects {

    public static final class Police extends CareerEffect {
        /** Mobs the police are about to kill, so their drops can be routed to the housing. */
        static final Map<UUID, Arrest> ARRESTS = new ConcurrentHashMap<>();

        record Arrest(ResourceKey<Level> dimension, BlockPos housing, ItemStack queen, long time) {}

        public Police() { super("police", 10, 1); }

        @Override
        protected void fire(Ctx ctx) {
            List<Mob> mobs = new ArrayList<>();
            for (Mob m : entities(ctx, Mob.class)) if (m instanceof Enemy && m.isAlive()) mobs.add(m);
            if (mobs.isEmpty()) return;
            Mob mob = mobs.get(ctx.random().nextInt(mobs.size()));
            ServerLevel level = ctx.level();
            FakePlayer fp = FakePlayerFactory.getMinecraft(level);
            DamageSource src = level.damageSources().playerAttack(fp);
            if (mob.isInvulnerableTo(level, src)) return;
            float health = mob.getHealth();
            if (health < 0.01f) {
                ARRESTS.put(mob.getUUID(), new Arrest(level.dimension(), ctx.pos(), ctx.queen().copy(), level.getGameTime()));
                mob.hurtServer(level, src, 1000f);
                ARRESTS.remove(mob.getUUID());
            } else {
                mob.hurtServer(level, src, Math.min(2f, health - 0.005f));
            }
        }

        /** Moves an arrested mob's drops into the housing with an incident report. True when it took them. */
        public static boolean takeDrops(Mob mob, Collection<ItemEntity> drops) {
            Arrest a = ARRESTS.remove(mob.getUUID());
            if (a == null || !(mob.level() instanceof ServerLevel level) || !level.dimension().equals(a.dimension())) return false;
            BlockEntity be = level.getBlockEntity(a.housing());
            if (!(be instanceof com.accbdd.complicated_bees.bees.IBeeHousing)) return false;
            Ctx ctx = new Ctx(level, be, a.queen(), 1, level.getRandom());
            List<ItemStack> taken = new ArrayList<>();
            for (ItemEntity e : drops) {
                ItemStack s = e.getItem();
                if (!ItemFilter.passes(ctx, s)) {
                    level.addFreshEntity(e);
                    continue;
                }
                taken.add(s.copy());
                output(ctx, s.copy());
            }
            output(ctx, report(level, mob, a.queen(), taken));
            return true;
        }

        public static ItemStack report(ServerLevel level, Mob mob, ItemStack queen, List<ItemStack> items) {
            long dayTime = level.getOverworldClockTime();
            long day = dayTime / 24000 + 1;
            int minutes = (int) (((dayTime + 6000) % 24000) * 1440 / 24000);
            String time = String.format("%02d:%02d", minutes / 60, minutes % 60);
            Genome g = GeneticHelper.getGenome(queen);
            int officer = 1000 + Math.floorMod(g == null ? 0 : g.primary().hashCode(), 9000);
            ItemStack out = new ItemStack(ModItems.REPORT.get());
            out.set(ModComponents.REPORT.get(), new ReportItem.Report(mob.getDisplayName().getString(), day, time,
                    level.dimension().identifier().toString(), List.copyOf(items), officer));
            return out;
        }
    }

    public static final class Artist extends CareerEffect {
        static final Direction[] SIDES = {Direction.NORTH, Direction.SOUTH, Direction.EAST, Direction.WEST};

        public Artist() { super("painting", 600, 1); }

        @Override
        protected void fire(Ctx ctx) {
            if (!griefing(ctx)) return;
            ServerLevel level = ctx.level();
            for (int a = 0; a < Config.WORLD_ATTEMPTS.get(); a++) {
                BlockPos pos = randomPos(ctx);
                BlockState wall = level.getBlockState(pos);
                if (!wall.isSolidRender() || !paintable(wall.getSoundType())) continue;
                Direction side = SIDES[ctx.random().nextInt(SIDES.length)];
                BlockPos front = pos.relative(side);
                if (!level.getBlockState(front).isAir()) continue;
                Optional<Painting> painting = Painting.create(level, front, side);
                if (painting.isEmpty() || !painting.get().survives()) continue;
                level.addFreshEntity(painting.get());
                painting.get().playPlacementSound();
                return;
            }
        }

        static boolean paintable(SoundType sound) {
            return sound == SoundType.STONE || sound == SoundType.METAL || sound == SoundType.WOOD;
        }
    }

    /**
     * Guitar notes around the housing. The spec calls this client only; it is sent from the server
     * as plain sound packets so it needs no client code and every nearby player hears the same tune.
     */
    public static final class Jazz extends CareerEffect {
        public Jazz() { super("jazz", 20, 0.3); }

        @Override
        protected void fire(Ctx ctx) {
            ServerLevel level = ctx.level();
            BlockPos spot = randomPos(ctx);
            int note = ctx.random().nextInt(25);
            float pitch = (float) Math.pow(2, (note - 12) / 12.0);
            long seed = ctx.random().nextLong();
            Vec3 home = Vec3.atCenterOf(ctx.pos());
            double x = spot.getX() + 0.5, y = spot.getY() + 0.5, z = spot.getZ() + 0.5;
            for (ServerPlayer p : level.players()) {
                double dist = p.position().distanceTo(home);
                if (dist > 64) continue;
                p.connection.send(new ClientboundSoundPacket(SoundEvents.NOTE_BLOCK_GUITAR, SoundSource.RECORDS, x, y, z, volume(dist), pitch, seed));
            }
            level.sendParticles(ParticleTypes.NOTE, x, y + 0.7, z, 0, note / 24.0, 0, 0, 1);
        }

        /** 3 at the housing, times 2/3 per 1.3x step of distance, up to 10 steps. */
        public static float volume(double dist) {
            int steps = dist <= 1 ? 0 : (int) Math.floor(Math.log(dist) / Math.log(1.3));
            steps = Math.max(0, Math.min(10, steps));
            return (float) (3 * Math.pow(2.0 / 3.0, steps));
        }
    }

    private SocialEffects() {}
}

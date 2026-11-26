package dev.oculifelis.careerbees.entity;

import com.accbdd.complicated_bees.bees.GeneticHelper;
import com.accbdd.complicated_bees.bees.Species;
import com.accbdd.complicated_bees.bees.gene.GeneLifespan;
import com.accbdd.complicated_bees.bees.gene.enums.EnumLifespan;
import com.accbdd.complicated_bees.registry.GeneRegistration;
import dev.oculifelis.careerbees.effect.CareerEffect;
import dev.oculifelis.careerbees.effect.Guns;
import dev.oculifelis.careerbees.registry.ModEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ColorParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.Optional;

/**
 * A puff of bees fired from the Bee-Bee Gun. It flies straight, latches onto the first thing its queen's effect can
 * work on, sits there for the effect's cooldown, acts once and is gone. Rain and water kill it unless the queen is
 * weatherproof. Never saved.
 */
public class BeeSwarm extends Projectile implements Guns.Waiting {
    private static final EntityDataAccessor<Integer> COLOR = SynchedEntityData.defineId(BeeSwarm.class, EntityDataSerializers.INT);
    private static final int MAX_LIFE = 400;

    private ItemStack queen = ItemStack.EMPTY;
    @Nullable private CareerEffect effect;
    @Nullable private Guns.Gun gun;
    private int life, maxLife = 100, wait, waited;
    private boolean weatherproof;
    @Nullable private Guns.Target target;

    public BeeSwarm(EntityType<? extends BeeSwarm> type, Level level) {
        super(type, level);
        noPhysics = true;
    }

    public static BeeSwarm fire(ServerLevel level, Player shooter, ItemStack queen, Guns.Gun gun, CareerEffect effect) {
        BeeSwarm s = new BeeSwarm(ModEntities.SWARM.get(), level);
        s.setOwner(shooter);
        s.queen = queen.copyWithCount(1);
        s.gun = gun;
        s.effect = effect;
        Object life = GeneticHelper.getGeneValue(queen, GeneLifespan.ID, true);
        int lifespan = life instanceof EnumLifespan l ? l.value : 35;
        s.maxLife = (int) Math.min(2L * lifespan, MAX_LIFE);
        Object wp = GeneticHelper.getGeneValue(queen, GeneRegistration.WEATHERPROOF.getId(), true);
        s.weatherproof = Boolean.TRUE.equals(wp);
        Species species = GeneticHelper.getSpecies(queen, true);
        if (species != null) s.entityData.set(COLOR, 0xFF000000 | species.getColor());
        s.setPos(shooter.getX(), shooter.getEyeY() - 0.1, shooter.getZ());
        return s;
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        builder.define(COLOR, 0xFFFFC21A);
    }

    /** Ticks it may fly without a target: 2 x lifespan, capped for immortal bees. */
    public int maxLife() {
        return maxLife;
    }

    @Override
    public int waited() {
        return waited;
    }

    @Override
    public boolean hurtServer(ServerLevel level, DamageSource source, float amount) {
        return false;
    }

    @Override
    public boolean isPickable() {
        return false;
    }

    @Override
    public void tick() {
        super.tick();
        if (level().isClientSide()) {
            particles();
            return;
        }
        ServerLevel level = (ServerLevel) level();
        if (queen.isEmpty() || gun == null || effect == null || ++life > maxLife) {
            discard();
            return;
        }
        if (!weatherproof && (isInWater() || level.isRainingAt(blockPosition()))) {
            discard();
            return;
        }
        if (target != null) {
            latched(level);
            return;
        }
        fly(level);
    }

    private void particles() {
        // Particles right at the shooter's eyes fill their whole view, so the swarm shows once it is a few blocks out.
        Entity owner = getOwner();
        if (owner != null && distanceToSqr(owner.getEyePosition()) < 6.25) return;
        int c = entityData.get(COLOR);
        for (int i = 0; i < 3; i++) {
            level().addParticle(ColorParticleOption.create(ParticleTypes.ENTITY_EFFECT, c),
                    getX() + (random.nextDouble() - 0.5) * 0.5, getY() + (random.nextDouble() - 0.5) * 0.5, getZ() + (random.nextDouble() - 0.5) * 0.5, 0, 0, 0);
        }
    }

    private boolean blocks(LivingEntity e) {
        if (e == this.getOwner() && !hasLeftOwner()) return false;
        if (!canHitEntity(e) || !e.isAlive()) return false;
        if (e instanceof Player p) {
            if (p.isSpectator()) return false;
            String id = effect.id();
            if (CareerEffect.protectedApiarist(p) && !id.equals("healing") && !id.equals("curative")) return false;
        }
        return gun.entity((ServerLevel) level(), e);
    }

    private boolean hasLeftOwner() {
        Entity owner = getOwner();
        return owner == null || tickCount > 4 || !owner.getBoundingBox().inflate(1).contains(position());
    }

    private void fly(ServerLevel level) {
        Vec3 from = position(), motion = getDeltaMovement(), to = from.add(motion);
        BlockHitResult bh = level.clip(new ClipContext(from, to, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, this));
        Vec3 end = bh.getType() == HitResult.Type.MISS ? to : bh.getLocation();
        AABB sweep = new AABB(from, end).inflate(1.0);

        double best = Double.MAX_VALUE;
        Guns.Target hit = null;
        for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, sweep, this::blocks)) {
            Optional<Vec3> p = e.getBoundingBox().inflate(0.3).clip(from, end);
            if (e.getBoundingBox().inflate(0.3).contains(from)) p = Optional.of(from);
            if (p.isPresent() && from.distanceToSqr(p.get()) < best) {
                best = from.distanceToSqr(p.get());
                hit = new Guns.Target(e, null, null, Direction.UP);
            }
        }
        for (ItemEntity e : level.getEntitiesOfClass(ItemEntity.class, sweep, e -> e.isAlive() && gun.item(level, e))) {
            Optional<Vec3> p = e.getBoundingBox().inflate(0.3).clip(from, end);
            if (e.getBoundingBox().inflate(0.3).contains(from)) p = Optional.of(from);
            if (p.isPresent() && from.distanceToSqr(p.get()) < best) {
                best = from.distanceToSqr(p.get());
                hit = new Guns.Target(null, e, null, Direction.UP);
            }
        }
        if (hit == null && bh.getType() == HitResult.Type.BLOCK) {
            BlockPos pos = bh.getBlockPos();
            BlockState state = level.getBlockState(pos);
            if (gun.block(level, pos, state, bh.getDirection())) hit = new Guns.Target(null, null, pos.immutable(), bh.getDirection());
            else {
                discard();
                return;
            }
        }
        if (hit != null) {
            latch(level, hit);
            return;
        }
        setPos(end);
        // Keep the server's facing in step with the flight so tracking sends a sensible rotation.
        updateRotation();
    }

    private void latch(ServerLevel level, Guns.Target t) {
        target = t;
        setDeltaMovement(Vec3.ZERO);
        Object life = GeneticHelper.getGeneValue(queen, GeneLifespan.ID, true);
        int lifespan = life instanceof EnumLifespan l ? l.value : 35;
        wait = gun.cooldown(effect, Guns.speed(queen), lifespan, random);
        waited = wait;
        // Latched swarms stay until they act, however long that takes.
        maxLife = Integer.MAX_VALUE;
        setPos(Guns.point(t));
    }

    private boolean valid(ServerLevel level) {
        Guns.Target t = target;
        if (t.entity() != null) return t.entity().isAlive() && gun.entity(level, t.entity());
        if (t.item() != null) return t.item().isAlive() && gun.item(level, t.item());
        return gun.block(level, t.block(), level.getBlockState(t.block()), t.face());
    }

    private void latched(ServerLevel level) {
        if (!valid(level)) {
            discard();
            return;
        }
        setPos(Guns.point(target));
        if (--wait > 0) return;
        Guns.Target t = target;
        BlockPos pos = BlockPos.containing(Guns.point(t));
        CareerEffect.Ctx ctx = new CareerEffect.Ctx(level, null, queen, Guns.speed(queen), random, pos, Guns.radii(queen), this);
        gun.act(effect, ctx, t);
        discard();
    }

    @Override
    protected boolean canHitEntity(Entity e) {
        return e != this && !(e instanceof BeeSwarm) && super.canHitEntity(e);
    }

    @Override
    public boolean shouldBeSaved() {
        return false;
    }
}

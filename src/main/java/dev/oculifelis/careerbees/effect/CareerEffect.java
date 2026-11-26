package dev.oculifelis.careerbees.effect;

import com.accbdd.complicated_bees.bees.BeeHousingModifier;
import com.accbdd.complicated_bees.bees.GeneticHelper;
import com.accbdd.complicated_bees.bees.IBeeHousing;
import com.accbdd.complicated_bees.bees.effect.BeeEffect;
import com.accbdd.complicated_bees.bees.gene.GeneProductivity;
import com.accbdd.complicated_bees.bees.gene.GeneTerritory;
import com.accbdd.complicated_bees.bees.gene.enums.EnumProductivity;
import com.accbdd.complicated_bees.config.ServerConfig;
import com.accbdd.complicated_bees.port.PortCapabilities;
import dev.oculifelis.careerbees.CareerBees;
import dev.oculifelis.careerbees.Config;
import dev.oculifelis.careerbees.registry.ModAttachments;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.TagKey;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.items.IItemHandler;

import java.util.ArrayList;
import java.util.List;

/**
 * Shared base for every career effect.
 * Fires when ticks x speed reaches the interval, then rolls the chance.
 * An interval of 0 or less runs every tick with no throttle.
 */
public abstract class CareerEffect extends BeeEffect {
    public static final TagKey<Item> APIARIST_ARMOR = TagKey.create(Registries.ITEM, CareerBees.id("apiarist_armor"));
    private static final EquipmentSlot[] ARMOR = {EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET};

    protected final String id;
    private final int defaultInterval;
    private final double defaultChance;
    private int timingGeneration = -1;
    private int interval;
    private double chance;

    protected CareerEffect(String id, int interval, double chance) {
        this.id = id;
        this.defaultInterval = interval;
        this.defaultChance = chance;
        this.interval = interval;
        this.chance = chance;
    }

    /** Re-reads careerbees:effect_timing only after data maps reload; the Java numbers are the fallback. */
    private void refreshTiming() {
        int gen = CareerDataMaps.generation();
        if (gen == timingGeneration) return;
        CareerDataMaps.Timing t = CareerDataMaps.timing(this);
        interval = t == null ? defaultInterval : t.interval().orElse(defaultInterval);
        chance = t == null ? defaultChance : t.chance().orElse(defaultChance);
        timingGeneration = gen;
    }

    public String id() {
        return id;
    }

    @Override
    public final void runEffect(BlockEntity be, ItemStack queen, int cycleProgress) {
        if (!(be.getLevel() instanceof ServerLevel level)) return;
        if (!Config.effectEnabled(id)) return;
        double speed = speed(be, queen);
        if (speed <= 0) return;
        Ctx ctx = new Ctx(level, be, queen, speed, level.getRandom());
        refreshTiming();
        if (interval <= 0) {
            fire(ctx);
            return;
        }
        int ticks = be.getData(ModAttachments.EFFECT_TICKS) + 1;
        if (ticks * speed >= interval) {
            be.setData(ModAttachments.EFFECT_TICKS, 0);
            if (chance >= 1 || ctx.random.nextDouble() < chance) fire(ctx);
        } else {
            be.setData(ModAttachments.EFFECT_TICKS, ticks);
        }
    }

    protected abstract void fire(Ctx ctx);

    /** Productivity gene times every non-zero housing productivity mod, capped by the Complicated Bees config. */
    public static double speed(BlockEntity be, ItemStack queen) {
        double speed = ((EnumProductivity) GeneticHelper.getGeneValue(queen, GeneProductivity.ID, true)).value;
        if (be instanceof IBeeHousing housing) {
            for (BeeHousingModifier m : housing.getHousingModifiers()) {
                float mod = m.getProductivityMod();
                if (mod != 0) speed *= mod;
            }
        }
        double cap = ServerConfig.SERVER_CONFIG == null ? 50000 : ServerConfig.SERVER_CONFIG.productivityCap.get();
        return Math.min(speed, cap);
    }

    /** Territory half-extents {horizontal, vertical} after territory mods. */
    public static int[] radii(BlockEntity be, ItemStack queen) {
        int[] base = (int[]) GeneticHelper.getGeneValue(queen, GeneTerritory.ID, true);
        float mod = 1f;
        if (be instanceof IBeeHousing housing) {
            for (BeeHousingModifier m : housing.getHousingModifiers()) mod *= m.getTerritoryMod();
        }
        return new int[]{Math.round(base[0] * mod), Math.round(base[1] * mod)};
    }

    public static AABB territory(BlockEntity be, ItemStack queen) {
        int[] r = radii(be, queen);
        Vec3 c = Vec3.atCenterOf(be.getBlockPos());
        return new AABB(c.x - r[0], c.y - r[1], c.z - r[0], c.x + r[0], c.y + r[1], c.z + r[0]);
    }

    /** The context's territory: around the housing, or around a swarm fired from the Bee-Bee Gun. */
    public static AABB territory(Ctx ctx) {
        int[] r = ctx.radii();
        Vec3 c = Vec3.atCenterOf(ctx.pos());
        return new AABB(c.x - r[0], c.y - r[1], c.z - r[0], c.x + r[0], c.y + r[1], c.z + r[0]);
    }

    /** Ticks between fires at speed 1, from careerbees:effect_timing or the built-in default. */
    public int interval() {
        refreshTiming();
        return interval;
    }

    /** Chance each fire goes off, from careerbees:effect_timing or the built-in default. */
    public double chance() {
        refreshTiming();
        return chance;
    }

    public int defaultInterval() {
        return defaultInterval;
    }

    public double defaultChance() {
        return defaultChance;
    }

    /** Runs the effect once with a ready-made context (the gun's swarms use this). */
    public final void fireNow(Ctx ctx) {
        if (Config.effectEnabled(id)) fire(ctx);
    }

    /** World-changing effects need both our config switch and the mobGriefing game rule. */
    public static boolean griefing(Ctx ctx) {
        return Config.MOB_GRIEFING.get() && ctx.level.getGameRules().get(net.minecraft.world.level.gamerules.GameRules.MOB_GRIEFING);
    }

    public static final TagKey<Item> BEE_ARMOR = TagKey.create(Registries.ITEM, CareerBees.id("bee_armor"));

    /** One piece of our bee armour protects; Complicated Bees' own apiarist set has to be worn in full. */
    public static boolean protectedApiarist(Player player) {
        boolean full = true;
        for (EquipmentSlot slot : ARMOR) {
            ItemStack worn = player.getItemBySlot(slot);
            if (worn.is(BEE_ARMOR)) return true;
            if (!worn.is(APIARIST_ARMOR)) full = false;
        }
        return full;
    }

    /** Entities of a type inside the territory, minus players in full apiarist armour. */
    public static <T extends Entity> List<T> entities(Ctx ctx, Class<T> type) {
        List<T> out = new ArrayList<>();
        for (T e : ctx.level.getEntitiesOfClass(type, territory(ctx))) {
            if (e instanceof Player p && (protectedApiarist(p) || p.isSpectator())) continue;
            out.add(e);
        }
        return out;
    }

    /** A random position in the territory columns, scanning y from the bottom of the territory to the top. */
    public static List<BlockPos> randomColumn(Ctx ctx) {
        int[] r = ctx.radii;
        BlockPos o = ctx.pos;
        int x = o.getX() + ctx.random.nextInt(2 * r[0] + 1) - r[0];
        int z = o.getZ() + ctx.random.nextInt(2 * r[0] + 1) - r[0];
        List<BlockPos> col = new ArrayList<>();
        for (int y = o.getY() - r[1]; y <= o.getY() + r[1]; y++) col.add(new BlockPos(x, y, z));
        return col;
    }

    /** Uniform random position inside the territory box. */
    public static BlockPos randomPos(Ctx ctx) {
        int[] r = ctx.radii;
        return ctx.pos.offset(
                ctx.random.nextInt(2 * r[0] + 1) - r[0],
                ctx.random.nextInt(2 * r[1] + 1) - r[1],
                ctx.random.nextInt(2 * r[0] + 1) - r[0]);
    }

    /** Puts a stack into the housing's output buffer, the same path its own products take. */
    public static void output(Ctx ctx, ItemStack stack) {
        if (stack.isEmpty()) return;
        if (ctx.be instanceof IBeeHousing housing) housing.addToOutput(stack);
        else net.minecraft.world.level.block.Block.popResource(ctx.level, ctx.pos, stack);
    }

    /** Item handlers on the six neighbours of the housing. */
    public static List<IItemHandler> adjacentInventories(Ctx ctx) {
        List<IItemHandler> out = new ArrayList<>();
        BlockPos pos = ctx.pos;
        for (Direction d : Direction.values()) {
            BlockPos n = pos.relative(d);
            IItemHandler h = PortCapabilities.find(ctx.level, PortCapabilities.ItemHandler.BLOCK, n, ctx.level.getBlockState(n), ctx.level.getBlockEntity(n), d.getOpposite());
            if (h != null) out.add(h);
        }
        return out;
    }

    /**
     * Everything an effect run needs. be is the housing, or null for a swarm from the Bee-Bee Gun;
     * pos and radii give the territory, anchor keys per-source caches.
     */
    public record Ctx(ServerLevel level, BlockEntity be, ItemStack queen, double speed, RandomSource random,
                      BlockPos pos, int[] radii, Object anchor) {
        public Ctx(ServerLevel level, BlockEntity be, ItemStack queen, double speed, RandomSource random) {
            this(level, be, queen, speed, random, be.getBlockPos(), CareerEffect.radii(be, queen), be);
        }
    }
}

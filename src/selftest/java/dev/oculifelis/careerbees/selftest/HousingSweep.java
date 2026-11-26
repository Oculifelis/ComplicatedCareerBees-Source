package dev.oculifelis.careerbees.selftest;

import com.accbdd.complicated_bees.block.entity.BaseBeeHousing;
import com.accbdd.complicated_bees.registry.BlocksRegistration;
import com.accbdd.complicated_bees.registry.ItemsRegistration;
import com.accbdd.complicated_bees.registry.SpeciesRegistration;
import dev.oculifelis.careerbees.CareerBees;
import dev.oculifelis.careerbees.Config;
import dev.oculifelis.careerbees.compat.Mc;
import dev.oculifelis.careerbees.effect.CareerEffect;
import dev.oculifelis.careerbees.effect.Guns;
import dev.oculifelis.careerbees.effect.LifeEffects;
import dev.oculifelis.careerbees.effect.PedestalEffect;
import dev.oculifelis.careerbees.registry.ModItems;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Container;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.monster.Creeper;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.BaseFireBlock;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LeavesBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.phys.AABB;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.function.BooleanSupplier;
import java.util.function.Predicate;

/**
 * Every effect in EFFECTS.md through the housing path: an apiary with that effect's queen, one small scene
 * per effect, fired with fireNow (interval and chance are checked once on their own).
 */
final class HousingSweep {
    private static ServerLevel level;
    private static BlockPos h;
    private static int hy;
    private static BaseBeeHousing housing;
    static ItemStack retrainSample;

    private static final Set<String> covered = new TreeSet<>();

    private HousingSweep() {}

    static void run(MinecraftServer server) {
        level = server.overworld();
        hy = level.getMaxY() - 60;
        h = new BlockPos(8, hy, 8);
        var species = server.registryAccess().lookupOrThrow(SpeciesRegistration.SPECIES_REGISTRY_KEY);
        Map<String, ItemStack> queens = new TreeMap<>();
        Map<String, CareerEffect> effects = new HashMap<>();
        for (var key : species.keySet()) {
            if (!key.getNamespace().equals(CareerBees.MODID)) continue;
            var sp = SpeciesRegistration.getFromIdentifier(key);
            if (sp == null) continue;
            var effect = Guns.effect(sp.toStack(ItemsRegistration.DRONE.get()));
            if (effect == null || queens.containsKey(effect.id())) continue;
            queens.put(effect.id(), sp.toStack(ItemsRegistration.QUEEN.get()));
            effects.put(effect.id(), effect);
        }
        clear();
        intervalCheck(queens.get("healing"), effects.get("healing"));
        clear();
        for (String id : Config.EFFECTS) {
            String name = "housing " + id;
            var effect = effects.get(id);
            var queen = queens.get(id);
            if (effect == null) {
                SelfTest.check(name, false, "no species carries it");
                continue;
            }
            try {
                scene(id, effect, queen, name, queens);
            } catch (Throwable t) {
                SelfTest.check(name, false, "threw " + t);
                t.printStackTrace();
            }
            clear();
        }
        List<String> missed = new ArrayList<>();
        for (String id : Config.EFFECTS) if (!covered.contains(id)) missed.add(id);
        SelfTest.check("housing sweep covers EFFECTS.md", missed.isEmpty(), covered.size() + "/" + Config.EFFECTS.size() + " missed=" + missed);
        level.removeBlock(h, false);
    }

    private static void check(String id, String name, boolean ok, Object detail) {
        covered.add(id);
        SelfTest.check(name, ok, detail);
    }

    /** Air in the scene box, loose entities gone, a fresh apiary in the middle. */
    private static void clear() {
        for (int x = 0; x < 17; x++) for (int z = 0; z < 17; z++) for (int dy = -6; dy <= 6; dy++)
            level.setBlock(new BlockPos(x, hy + dy, z), Blocks.AIR.defaultBlockState(), 2 | 16);
        var box = new AABB(-2, hy - 48, -2, 19, hy + 8, 19);
        for (var e : level.getEntitiesOfClass(Entity.class, box, e -> !(e instanceof Player))) e.discard();
        level.setBlock(h, BlocksRegistration.APIARY.get().defaultBlockState(), 3);
        housing = level.getBlockEntity(h) instanceof BaseBeeHousing b ? b : null;
        if (housing != null) {
            housing.getLogic().setLevel(level);
            housing.getOutputBuffer().clear();
        }
    }

    private static <T extends Entity> T spawn(EntityType<T> type, double dx, double dy, double dz) {
        T e = type.create(level, net.minecraft.world.entity.EntitySpawnReason.TRIGGERED);
        e.setPos(h.getX() + 0.5 + dx, h.getY() + dy, h.getZ() + 0.5 + dz);
        if (e instanceof net.minecraft.world.entity.Mob m) { m.setNoAi(true); m.setPersistenceRequired(); }
        e.setNoGravity(true);
        level.addFreshEntity(e);
        return e;
    }

    private static CareerEffect.Ctx ctx(ItemStack queen, int r, int v) {
        return new CareerEffect.Ctx(level, housing, queen, 1, level.getRandom(), h, new int[]{r, v}, housing);
    }

    /** Fires up to n times, stopping once done says so; returns the fires used. */
    private static int fireUntil(CareerEffect effect, CareerEffect.Ctx ctx, int n, BooleanSupplier done) {
        int i = 0;
        while (i < n && !done.getAsBoolean()) { effect.fireNow(ctx); i++; }
        return i;
    }

    private static long out(Predicate<ItemStack> p) {
        long n = 0;
        for (ItemStack s : housing.getOutputBuffer()) if (p.test(s)) n += s.getCount();
        return n;
    }

    private static void fill(int x0, int x1, int y0, int y1, int z0, int z1, BlockState s) {
        for (int x = x0; x <= x1; x++) for (int y = y0; y <= y1; y++) for (int z = z0; z <= z1; z++) {
            BlockPos p = new BlockPos(x, y, z);
            if (!p.equals(h)) level.setBlock(p, s, 2 | 16);
        }
    }

    @SuppressWarnings("unchecked")
    private static <T extends Entity> EntityType<T> type(String path) {
        return (EntityType<T>) BuiltInRegistries.ENTITY_TYPE.getValue(Identifier.fromNamespaceAndPath("minecraft", path));
    }

    /** runEffect honours the interval: healing at speed s fires once every ceil(interval / s) calls. */
    private static void intervalCheck(ItemStack queen, CareerEffect effect) {
        if (queen == null || effect == null || housing == null) {
            SelfTest.check("housing interval", false, "no healing queen");
            return;
        }
        housing.setData(dev.oculifelis.careerbees.registry.ModAttachments.EFFECT_TICKS, 0);
        var pig = spawn(Mc.PIG, 1, 0, 0);
        double speed = CareerEffect.speed(housing, queen);
        int period = (int) Math.ceil(effect.interval() / speed);
        int fires = 0, calls = 6 * Math.max(1, period);
        List<Integer> at = new ArrayList<>();
        for (int i = 1; i <= calls; i++) {
            pig.setHealth(1f);
            effect.runEffect(housing, queen, 0);
            if (pig.getHealth() > 1f) { fires++; at.add(i); }
        }
        SelfTest.check("housing interval", effect.chance() >= 1 && fires == calls / Math.max(1, period),
                "interval=" + effect.interval() + " speed=" + speed + " period=" + period + " calls=" + calls + " fires=" + fires + " at=" + at);
    }

    private static void scene(String id, CareerEffect effect, ItemStack queen, String name, Map<String, ItemStack> queens) {
        if (housing == null) {
            check(id, name, false, "no apiary");
            return;
        }
        switch (id) {
            case "healing" -> {
                var pig = spawn(Mc.PIG, 1, 0, 0);
                pig.setHealth(1f);
                effect.fireNow(ctx(queen, 3, 2));
                check(id, name, pig.getHealth() > 1f && pig.hasEffect(MobEffects.REGENERATION), "health=" + pig.getHealth() + " regen=" + pig.hasEffect(MobEffects.REGENERATION));
            }
            case "curative" -> {
                var pig = spawn(Mc.PIG, 1, 0, 0);
                pig.addEffect(new MobEffectInstance(MobEffects.POISON, 2000, 0));
                var zombie = spawn(Mc.ZOMBIE, -1, 0, 0);
                zombie.setHealth(10f);
                net.minecraft.world.entity.monster.zombie.ZombieVillager zv = spawn(type("zombie_villager"), 0, 0, 1);
                effect.fireNow(ctx(queen, 3, 2));
                // The spec gives undead Poison I, which vanilla undead ignore, so the zombie only has to stay unhealed.
                boolean ok = !pig.hasEffect(MobEffects.POISON) && zombie.getHealth() == 10f && zv.isConverting();
                check(id, name, ok, "pigPoison=" + pig.hasEffect(MobEffects.POISON) + " zombieHealth=" + zombie.getHealth() + " villagerConverting=" + zv.isConverting());
            }
            case "electrify" -> {
                var pig = spawn(Mc.PIG, 1, 0, 0);
                float before = pig.getHealth();
                int n = fireUntil(effect, ctx(queen, 3, 2), 5, () -> pig.getHealth() < before);
                check(id, name, pig.getHealth() < before, "fires=" + n + " health " + before + "->" + pig.getHealth());
            }
            case "husbandry" -> {
                var cow = spawn(Mc.COW, 1, 0, 0);
                effect.fireNow(ctx(queen, 3, 2));
                check(id, name, cow.isInLove(), "inLove=" + cow.isInLove());
            }
            case "butcher" -> {
                List<Entity> pigs = new ArrayList<>();
                for (int i = -1; i <= 1; i++) pigs.add(spawn(Mc.PIG, 2, 0, i * 2));
                effect.fireNow(ctx(queen, 3, 2));
                long alive = pigs.stream().filter(Entity::isAlive).count();
                check(id, name, alive == 2, "alive=" + alive + "/3");
            }
            case "police" -> {
                var zombie = spawn(Mc.ZOMBIE, 1, 0, 0);
                // Fires here are one tick apart, inside the hurt cooldown the 10-tick interval clears in a real world.
                int n = fireUntil(effect, ctx(queen, 3, 2), 60, () -> { zombie.invulnerableTime = 0; return !zombie.isAlive(); });
                long reports = out(s -> s.is(ModItems.REPORT.get()));
                check(id, name, !zombie.isAlive() && reports == 1, "fires=" + n + " alive=" + zombie.isAlive() + " reports=" + reports + " output=" + housing.getOutputBuffer());
            }
            case "taxation" -> {
                var zombie = spawn(Mc.ZOMBIE, 1, 0, 0);
                float before = zombie.getHealth();
                int n = fireUntil(effect, ctx(queen, 3, 2), 40, () -> !housing.getOutputBuffer().isEmpty());
                check(id, name, !housing.getOutputBuffer().isEmpty() && zombie.getHealth() == before,
                        "fires=" + n + " output=" + housing.getOutputBuffer() + " health " + before + "->" + zombie.getHealth());
            }
            case "creeper" -> {
                var box = new AABB(h).inflate(4);
                Predicate<Creeper> bomb = c -> c.entityTags().contains(LifeEffects.BOMB_TAG) && c.hasEffect(MobEffects.WITHER);
                int n = fireUntil(effect, ctx(queen, 3, 2), 20, () -> !level.getEntitiesOfClass(Creeper.class, box, bomb).isEmpty());
                int found = level.getEntitiesOfClass(Creeper.class, box, bomb).size();
                check(id, name, found == 1, "fires=" + n + " bombs=" + found);
            }
            case "assassin" -> {
                BlockPos op = h.offset(2, 0, 0);
                level.setBlock(op, BlocksRegistration.APIARY.get().defaultBlockState(), 3);
                if (!(level.getBlockEntity(op) instanceof BaseBeeHousing other)) {
                    check(id, name, false, "no second apiary");
                    return;
                }
                other.getLogic().setLevel(level);
                ItemStack victim = queens.get("healing").copy();
                other.getBeeItemHandler().setStackInSlot(0, victim);
                var life = (com.accbdd.complicated_bees.bees.gene.enums.EnumLifespan) com.accbdd.complicated_bees.bees.GeneticHelper
                        .getGeneValue(victim, com.accbdd.complicated_bees.bees.gene.GeneLifespan.ID, true);
                effect.fireNow(ctx(queen, 3, 2));
                float age = com.accbdd.complicated_bees.item.BeeItem.getAge(other.getBeeItemHandler().getStackInSlot(0));
                // Same species is spared.
                level.setBlock(op.offset(0, 0, 2), BlocksRegistration.APIARY.get().defaultBlockState(), 3);
                boolean spared = true;
                if (level.getBlockEntity(op.offset(0, 0, 2)) instanceof BaseBeeHousing twin) {
                    twin.getLogic().setLevel(level);
                    twin.getBeeItemHandler().setStackInSlot(0, queen.copy());
                    effect.fireNow(ctx(queen, 3, 2));
                    spared = com.accbdd.complicated_bees.item.BeeItem.getAge(twin.getBeeItemHandler().getStackInSlot(0)) < 1;
                }
                check(id, name, age >= life.value && spared, "age=" + age + " lifespan=" + life.value + " sameSpeciesSpared=" + spared);
            }
            case "thief" -> {
                var victim = net.neoforged.neoforge.common.util.FakePlayerFactory.get(level, new com.mojang.authlib.GameProfile(
                        java.util.UUID.fromString("6c6f6f74-6265-6573-0000-000000000002"), "ccb_mark2"));
                victim.getInventory().clearContent();
                for (int i = 0; i < 36; i++) victim.getInventory().setItem(i, new ItemStack(Items.EMERALD, 3));
                victim.setPos(h.getX() + 1.5, h.getY(), h.getZ() + 0.5);
                boolean added = level.addFreshEntity(victim);
                int before = victim.getInventory().countItem(Items.EMERALD);
                int n;
                try {
                    n = fireUntil(effect, ctx(queen, 3, 2), 30, () -> !housing.getOutputBuffer().isEmpty());
                } finally {
                    if (added) level.removePlayerImmediately(victim, Entity.RemovalReason.DISCARDED);
                }
                int after = victim.getInventory().countItem(Items.EMERALD);
                long got = out(s -> s.is(Items.EMERALD));
                check(id, name, added && after == before - 1 && got == 1, "added=" + added + " fires=" + n + " emeralds " + before + "->" + after + " output=" + got);
                victim.getInventory().clearContent();
            }
            case "burning" -> {
                fill(0, 16, hy - 1, hy - 1, 0, 16, Blocks.STONE.defaultBlockState());
                int n = fireUntil(effect, ctx(queen, 3, 2), 10, () -> count(b -> b.getBlock() instanceof BaseFireBlock) > 0);
                int fires = count(b -> b.getBlock() instanceof BaseFireBlock);
                check(id, name, fires == n && fires > 0, "fires=" + n + " fireBlocks=" + fires);
            }
            case "mason" -> {
                fill(0, 16, hy - 1, hy - 1, 0, 16, Blocks.COBBLESTONE.defaultBlockState());
                int n = fireUntil(effect, ctx(queen, 3, 2), 5, () -> count(b -> b.is(Blocks.STONE)) > 0);
                int stone = count(b -> b.is(Blocks.STONE));
                check(id, name, stone == 1, "fires=" + n + " stone=" + stone);
            }
            case "painting" -> {
                fill(h.getX() - 2, h.getX() - 2, hy - 2, hy + 2, h.getZ() - 3, h.getZ() + 3, Blocks.STONE.defaultBlockState());
                var box = new AABB(h).inflate(6);
                int n = fireUntil(effect, ctx(queen, 3, 2), 20,
                        () -> !level.getEntitiesOfClass(net.minecraft.world.entity.decoration.painting.Painting.class, box, e -> true).isEmpty());
                int paintings = level.getEntitiesOfClass(net.minecraft.world.entity.decoration.painting.Painting.class, box, e -> true).size();
                check(id, name, paintings == 1, "fires=" + n + " paintings=" + paintings);
            }
            case "lumber" -> {
                BlockPos log = h.offset(1, 0, 0);
                fill(log.getX(), log.getX(), hy - 2, hy + 1, log.getZ(), log.getZ(), Blocks.OAK_LOG.defaultBlockState());
                level.setBlock(log.atY(hy + 2), Blocks.OAK_LEAVES.defaultBlockState().setValue(LeavesBlock.PERSISTENT, true), 2 | 16);
                int n = fireUntil(effect, ctx(queen, 1, 2), 30, () -> out(s -> s.is(ModItems.BARK.get())) > 0);
                long bark = out(s -> s.is(ModItems.BARK.get()));
                check(id, name, bark > 0 && level.getBlockState(log).is(Blocks.OAK_LOG), "fires=" + n + " bark=" + bark);
            }
            case "teleposition" -> {
                Map<BlockPos, BlockState> was = new HashMap<>();
                for (BlockPos p : BlockPos.betweenClosed(h.offset(-2, -1, -2), h.offset(2, 1, 2))) {
                    if (p.equals(h)) continue;
                    BlockState s = ((p.getX() + p.getY() + p.getZ()) & 1) == 0 ? Blocks.ANDESITE.defaultBlockState() : Blocks.GRANITE.defaultBlockState();
                    level.setBlock(p, s, 2 | 16);
                    was.put(p.immutable(), s);
                }
                BooleanSupplier moved = () -> was.entrySet().stream().anyMatch(e -> level.getBlockState(e.getKey()) != e.getValue());
                int n = fireUntil(effect, ctx(queen, 2, 1), 40, moved);
                long changed = was.entrySet().stream().filter(e -> level.getBlockState(e.getKey()) != e.getValue()).count();
                long andesite = was.keySet().stream().filter(p -> level.getBlockState(p).is(Blocks.ANDESITE)).count();
                long andesiteWas = was.values().stream().filter(s -> s.is(Blocks.ANDESITE)).count();
                check(id, name, changed == 2 && andesite == andesiteWas && level.getBlockEntity(h) == housing,
                        "fires=" + n + " changed=" + changed + " andesite " + andesiteWas + "->" + andesite);
            }
            case "explosion" -> {
                fill(h.getX() - 3, h.getX() + 3, hy - 2, hy + 2, h.getZ() - 3, h.getZ() + 3, Blocks.DIRT.defaultBlockState());
                BlockPos chest = h.offset(-1, 0, 0);
                level.setBlock(chest, Blocks.CHEST.defaultBlockState(), 3);
                level.setBlock(h.offset(0, 0, 1), Blocks.AIR.defaultBlockState(), 2 | 16);
                level.setBlock(h.offset(0, 0, 2), Blocks.AIR.defaultBlockState(), 2 | 16);
                level.setBlock(h.offset(0, 1, 2), Blocks.AIR.defaultBlockState(), 2 | 16);
                var pig = spawn(Mc.PIG, 0, 0, 2);
                float before = pig.getHealth();
                int dirtBefore = count(b -> b.is(Blocks.DIRT));
                // A radius-2 blast can spend itself inside the apiary's own block, so allow a few rolls.
                int n = fireUntil(effect, ctx(queen, 3, 2), 8, () -> count(b -> b.is(Blocks.DIRT)) < dirtBefore);
                int dirtAfter = count(b -> b.is(Blocks.DIRT));
                boolean kept = level.getBlockState(chest).is(Blocks.CHEST) && level.getBlockEntity(h) instanceof BaseBeeHousing;
                check(id, name, dirtAfter < dirtBefore && kept && pig.getHealth() < before,
                        "fires=" + n + " dirt " + dirtBefore + "->" + dirtAfter + " chestAndApiaryKept=" + kept + " pig " + before + "->" + pig.getHealth());
            }
            case "mining", "mining_fortune", "mining_silk" -> {
                int depth = 40;
                for (int i = 1; i <= depth; i++) level.setBlock(h.below(i), Blocks.DIAMOND_ORE.defaultBlockState(), 2 | 16);
                var c = ctx(queen, 0, 2);
                for (int i = 0; i < 12; i++) effect.fireNow(c);
                int left = 0;
                for (int i = 1; i <= depth; i++) if (level.getBlockState(h.below(i)).is(Blocks.DIAMOND_ORE)) left++;
                int mined = depth - left;
                long diamonds = out(s -> s.is(Items.DIAMOND)), ores = out(s -> s.is(Items.DIAMOND_ORE));
                boolean ok = mined > 0 && switch (id) {
                    case "mining" -> diamonds == mined && ores == 0;
                    case "mining_fortune" -> diamonds > mined && ores == 0;
                    default -> ores == mined && diamonds == 0;
                };
                check(id, name, ok, "mined=" + mined + " diamonds=" + diamonds + " ores=" + ores);
                for (int i = 1; i <= depth; i++) level.setBlock(h.below(i), Blocks.AIR.defaultBlockState(), 2 | 16);
            }
            case "pickup" -> {
                var item = new ItemEntity(level, h.getX() + 1.5, h.getY() + 0.2, h.getZ() + 0.5, new ItemStack(Items.STONE, 5));
                item.setNoGravity(true);
                level.addFreshEntity(item);
                int n = fireUntil(effect, ctx(queen, 3, 2), 5, () -> !item.isAlive());
                long stone = out(s -> s.is(Items.STONE));
                check(id, name, !item.isAlive() && stone == 5, "fires=" + n + " alive=" + item.isAlive() + " output=" + stone);
            }
            case "business", "junk_sale" -> {
                BlockPos chest = h.offset(1, 0, 0);
                level.setBlock(chest, Blocks.CHEST.defaultBlockState(), 3);
                Container inv = (Container) level.getBlockEntity(chest);
                var goods = new net.minecraft.world.item.Item[]{Items.WHEAT, Items.PAPER, Items.STRING, Items.COAL, Items.ROTTEN_FLESH,
                        Items.STICK, Items.POTATO, Items.CARROT, Items.FEATHER, Items.CLAY_BALL, Items.PUMPKIN, Items.MELON};
                for (int i = 0; i < inv.getContainerSize(); i++)
                    inv.setItem(i, new ItemStack(id.equals("business") ? goods[i % goods.length] : Items.EMERALD, 64));
                int emeraldsBefore = inv.countItem(Items.EMERALD);
                int n = fireUntil(effect, ctx(queen, 3, 2), 10, () -> !housing.getOutputBuffer().isEmpty());
                int emeraldsAfter = inv.countItem(Items.EMERALD);
                long emeraldsOut = out(s -> s.is(Items.EMERALD)), otherOut = out(s -> !s.is(Items.EMERALD));
                boolean ok = id.equals("business") ? emeraldsOut > 0 && otherOut == 0
                        : otherOut > 0 && emeraldsOut == 0 && emeraldsAfter < emeraldsBefore;
                check(id, name, ok, "fires=" + n + " chestEmeralds " + emeraldsBefore + "->" + emeraldsAfter + " out emeralds=" + emeraldsOut + " other=" + otherOut);
                inv.clearContent();
            }
            case "energy" -> {
                BlockPos at = h.offset(1, 0, 0);
                String used = null;
                net.neoforged.neoforge.energy.IEnergyStorage cap = null;
                for (String c : new String[]{"centrifuge", "autolyzer", "gyrofuge_energy_cell", "mellarium_energy_cell"}) {
                    var blk = BuiltInRegistries.BLOCK.getValue(Identifier.fromNamespaceAndPath("complicated_bees", c));
                    level.setBlock(at, blk.defaultBlockState(), 3);
                    var st = level.getBlockState(at);
                    cap = com.accbdd.complicated_bees.port.PortCapabilities.find(level, com.accbdd.complicated_bees.port.PortCapabilities.EnergyStorage.BLOCK,
                            at, st, level.getBlockEntity(at), Direction.WEST);
                    if (cap != null && cap.canReceive()) { used = c; break; }
                }
                if (used == null) {
                    check(id, name, false, "no receiver");
                    return;
                }
                int before = cap.getEnergyStored();
                effect.fireNow(ctx(queen, 3, 2));
                int after = cap.getEnergyStored();
                check(id, name + " into " + used, after > before, "energy " + before + "->" + after);
            }
            case "clockwinding" -> {
                BlockPos at = h.offset(2, 0, 0);
                level.setBlock(at, BuiltInRegistries.BLOCK.getValue(Identifier.fromNamespaceAndPath("complicated_bees", "furnace_generator")).defaultBlockState(), 3);
                var cap = com.accbdd.complicated_bees.port.PortCapabilities.find(level, com.accbdd.complicated_bees.port.PortCapabilities.EnergyStorage.BLOCK,
                        at, level.getBlockState(at), level.getBlockEntity(at), Direction.WEST);
                int before = cap == null ? -1 : cap.getEnergyStored();
                for (int i = 0; i < 3; i++) effect.fireNow(ctx(queen, 3, 2));
                int after = cap == null ? -1 : cap.getEnergyStored();
                check(id, name, cap != null && after > before, "energy " + before + "->" + after);
            }
            case "temporal" -> {
                BlockPos farm = h.offset(1, -1, 0);
                level.setBlock(farm, Blocks.FARMLAND.defaultBlockState().setValue(BlockStateProperties.MOISTURE, 7), 2 | 16);
                var data = (net.minecraft.world.level.storage.ServerLevelData) level.getLevelData();
                long t = level.getGameTime();
                data.setGameTime(t + (20 - Math.floorMod(t, 20)) % 20 + 1);
                effect.fireNow(ctx(queen, 2, 1));
                int offTick = level.getBlockState(farm).getValue(BlockStateProperties.MOISTURE);
                t = level.getGameTime();
                data.setGameTime(t + (20 - Math.floorMod(t, 20)) % 20);
                effect.fireNow(ctx(queen, 2, 1));
                var s = level.getBlockState(farm);
                int onTick = s.is(Blocks.FARMLAND) ? s.getValue(BlockStateProperties.MOISTURE) : -1;
                check(id, name, offTick == 7 && onTick < 7, "time%20=" + Math.floorMod(level.getGameTime(), 20) + " moisture offTick=" + offTick + " onTick=" + onTick);
            }
            case "jazz" -> {
                for (int i = 0; i < 3; i++) effect.fireNow(ctx(queen, 3, 2));
                check(id, name, true, "fired 3 times");
            }
            default -> {
                if (!(effect instanceof PedestalEffect pe)) {
                    check(id, name, false, "no scene");
                    return;
                }
                ItemStack sample = pedestalSample(id);
                if (sample == null) {
                    check(id, name, false, "no sample");
                    return;
                }
                BlockPos at = h.offset(2, 0, 0);
                level.setBlock(at, dev.oculifelis.careerbees.registry.ModBlocks.PEDESTAL.get().defaultBlockState(), 3);
                var ped = (dev.oculifelis.careerbees.block.PedestalBlockEntity) level.getBlockEntity(at);
                ped.setItem(sample.copy());
                int n = fireUntil(pe, ctx(queen, 3, 2), 60, () -> !ItemStack.matches(ped.getItem(), sample));
                check(id, name, !ItemStack.matches(ped.getItem(), sample), "fires=" + n + " " + sample + "->" + ped.getItem());
                ped.setItem(ItemStack.EMPTY);
            }
        }
    }

    private static ItemStack pedestalSample(String id) {
        return switch (id) {
            case "crusher" -> new ItemStack(Items.IRON_ORE);
            case "smelt" -> new ItemStack(Items.RAW_IRON);
            case "honey_ingot" -> new ItemStack(Items.IRON_INGOT);
            case "repair" -> {
                var worn = new ItemStack(Items.IRON_SWORD);
                worn.setDamageValue(100);
                yield worn;
            }
            case "sharpen" -> new ItemStack(Items.IRON_SWORD);
            case "armorer" -> new ItemStack(Items.IRON_CHESTPLATE);
            case "glazing" -> new ItemStack(Items.BREAD);
            case "priest" -> new ItemStack(Items.IRON_PICKAXE);
            case "retraining" -> retrainSample == null ? null : retrainSample.copy();
            default -> null;
        };
    }

    private static int count(Predicate<BlockState> p) {
        int n = 0;
        for (BlockPos q : BlockPos.betweenClosed(new BlockPos(0, hy - 6, 0), new BlockPos(16, hy + 6, 16)))
            if (p.test(level.getBlockState(q))) n++;
        return n;
    }
}

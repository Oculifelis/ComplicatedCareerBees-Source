package dev.oculifelis.careerbees.selftest;

import com.accbdd.complicated_bees.registry.BeeEffectRegistration;
import com.accbdd.complicated_bees.registry.SpeciesRegistration;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.oculifelis.careerbees.Config;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.MinecraftServer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.event.server.ServerStartedEvent;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/** Checks registries and data on a dedicated server, writes selftest-report.txt and stops the server. */
@Mod("careerbees_selftest")
@EventBusSubscriber(modid = "careerbees_selftest")
public final class SelfTest {
    private static final String NS = "careerbees";
    static final List<String> lines = new ArrayList<>();
    private static int fails;

    static void check(String name, boolean ok, Object detail) {
        if (!ok) fails++;
        lines.add((ok ? "PASS " : "FAIL ") + name + " " + detail);
    }

    /** The pedestal, trade, police and jazz pieces, run against the overworld. */
    private static void careerChecks(MinecraftServer server) {
        net.minecraft.server.level.ServerLevel level = server.overworld();
        net.minecraft.core.BlockPos pos = new net.minecraft.core.BlockPos(0, level.getMaxY() - 2, 0);
        level.setBlock(pos, dev.oculifelis.careerbees.registry.ModBlocks.PEDESTAL.get().defaultBlockState(), 3);
        var be = level.getBlockEntity(pos);
        check("pedestal block entity", be instanceof dev.oculifelis.careerbees.block.PedestalBlockEntity, be);
        var cap = level.getCapability(com.accbdd.complicated_bees.port.PortCapabilities.ItemHandler.BLOCK, pos, null);
        check("pedestal item handler", cap != null, cap);
        check("pedestal loot table", server.reloadableRegistries().lookup().lookup(net.minecraft.core.registries.Registries.LOOT_TABLE)
                .flatMap(r -> r.get(net.minecraft.resources.ResourceKey.create(net.minecraft.core.registries.Registries.LOOT_TABLE,
                        dev.oculifelis.careerbees.CareerBees.id("blocks/flower_pedestal")))).isPresent(), "");
        var ctx = new dev.oculifelis.careerbees.effect.CareerEffect.Ctx(level, be, net.minecraft.world.item.ItemStack.EMPTY, 1, level.getRandom());
        java.util.Map<String, net.minecraft.world.item.ItemStack> samples = new java.util.LinkedHashMap<>();
        samples.put("crusher", new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.IRON_ORE));
        samples.put("smelt", new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.RAW_IRON));
        samples.put("honey_ingot", new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.IRON_INGOT));
        var worn = new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.IRON_SWORD);
        worn.setDamageValue(100);
        samples.put("repair", worn);
        samples.put("sharpen", new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.IRON_SWORD));
        samples.put("armorer", new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.IRON_CHESTPLATE));
        samples.put("glazing", new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.BREAD));
        samples.put("priest", new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.IRON_PICKAXE));
        for (var e : samples.entrySet()) {
            var eff = dev.oculifelis.careerbees.effect.PedestalEffect.ALL.stream().filter(x -> x.id().equals(e.getKey())).findFirst().orElse(null);
            if (eff == null) { check("pedestal " + e.getKey(), false, "not registered"); continue; }
            boolean acc = eff.accepts(level, e.getValue());
            net.minecraft.world.item.ItemStack out = null;
            for (int i = 0; i < 20 && out == null && acc; i++) out = eff.change(ctx, e.getValue().copy());
            boolean changed = out != null && !net.minecraft.world.item.ItemStack.isSameItemSameComponents(out, e.getValue());
            check("pedestal " + e.getKey(), acc && changed, e.getValue() + " -> " + out + (out == null ? "" : " " + out.getComponentsPatch()));
        }
        var offers = dev.oculifelis.careerbees.effect.TradeEffects.offers(level, pos);
        long sells = offers.stream().filter(o -> o.getResult().is(net.minecraft.world.item.Items.EMERALD) && !o.getCostA().is(net.minecraft.world.item.Items.EMERALD)).count();
        long buys = offers.stream().filter(o -> o.getCostA().is(net.minecraft.world.item.Items.EMERALD) && o.getItemCostB().isEmpty() && !o.getResult().is(net.minecraft.world.item.Items.EMERALD)).count();
        check("trade offers", sells > 10 && buys > 10, "all=" + offers.size() + " business=" + sells + " junk=" + buys);
        var zombie = new net.minecraft.world.entity.monster.zombie.Zombie(level);
        var report = dev.oculifelis.careerbees.effect.SocialEffects.Police.report(level, zombie,
                net.minecraft.world.item.ItemStack.EMPTY, java.util.List.of(new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.ROTTEN_FLESH)));
        var r = report.get(dev.oculifelis.careerbees.registry.ModComponents.REPORT.get());
        check("police report", r != null && r.items().size() == 1, r);
        float v0 = dev.oculifelis.careerbees.effect.SocialEffects.Jazz.volume(1), v64 = dev.oculifelis.careerbees.effect.SocialEffects.Jazz.volume(64);
        check("jazz volume", v0 == 3f && v64 < 0.1f, v0 + " " + v64);
        var helmet = new net.minecraft.world.item.ItemStack(dev.oculifelis.careerbees.registry.ModItems.BEE_HELMET.get());
        var chest = new net.minecraft.world.item.ItemStack(dev.oculifelis.careerbees.registry.ModItems.BEE_CHESTPLATE.get());
        var fake = net.neoforged.neoforge.common.util.FakePlayerFactory.getMinecraft(level);
        boolean bare = dev.oculifelis.careerbees.effect.CareerEffect.protectedApiarist(fake);
        fake.setItemSlot(net.minecraft.world.entity.EquipmentSlot.HEAD, helmet);
        boolean one = dev.oculifelis.careerbees.effect.CareerEffect.protectedApiarist(fake);
        fake.setItemSlot(net.minecraft.world.entity.EquipmentSlot.HEAD, net.minecraft.world.item.ItemStack.EMPTY);
        var eq = chest.get(net.minecraft.core.component.DataComponents.EQUIPPABLE);
        check("bee armour", !bare && one && helmet.is(dev.oculifelis.careerbees.effect.CareerEffect.APIARIST_ARMOR)
                && eq != null && eq.slot() == net.minecraft.world.entity.EquipmentSlot.CHEST && chest.getMaxDamage() == 33 * 16,
                "bare=" + bare + " one=" + one + " equippable=" + eq + " durability=" + chest.getMaxDamage());
        level.removeBlock(pos, false);
    }

    /** CBU frame and upgrade stats from data maps (port.7 on); the self test mod's own data overrides the plain frame. */
    private static void dataMapChecks() {
        try {
            Class.forName("com.accbdd.complicated_bees.bees.ItemModifierMaps");
        } catch (ClassNotFoundException e) {
            lines.add("SKIP cbu data maps (CBU jar before port.7)");
            return;
        }
        var reg = net.minecraft.core.registries.BuiltInRegistries.ITEM;
        var waxed = (com.accbdd.complicated_bees.item.FrameItem) reg.getValue(net.minecraft.resources.Identifier.fromNamespaceAndPath("complicated_bees", "waxed_frame"));
        var plain = (com.accbdd.complicated_bees.item.FrameItem) reg.getValue(net.minecraft.resources.Identifier.fromNamespaceAndPath("complicated_bees", "frame"));
        var endenic = (com.accbdd.complicated_bees.item.UpgradeItem) reg.getValue(net.minecraft.resources.Identifier.fromNamespaceAndPath("complicated_bees", "endenic_upgrade"));
        check("cbu frame data map", waxed.getModifier().getProductivityMod() == 1.5f, waxed.getModifier().getProductivityMod());
        check("cbu frame data override", plain.getModifier().getProductivityMod() == 3.0f, plain.getModifier().getProductivityMod());
        check("cbu upgrade data map", endenic.getEfficiencyMod() == 4f, endenic.getEfficiencyMod());
    }

    /** Port.6 gene values, the bark recipe and the active frames' per-cycle hook. */
    private static void frameChecks(MinecraftServer server) {
        net.minecraft.server.level.ServerLevel level = server.overworld();
        var robot = com.accbdd.complicated_bees.registry.SpeciesRegistration.getFromIdentifier(dev.oculifelis.careerbees.CareerBees.id("robot"));
        var temporal = com.accbdd.complicated_bees.registry.SpeciesRegistration.getFromIdentifier(dev.oculifelis.careerbees.CareerBees.id("acceleration"));
        var queenItem = com.accbdd.complicated_bees.registry.ItemsRegistration.QUEEN.get();
        Object prod = robot == null ? null : com.accbdd.complicated_bees.bees.GeneticHelper.getGeneValue(robot.toStack(queenItem), com.accbdd.complicated_bees.bees.gene.GeneProductivity.ID, true);
        check("gene robotic", prod == com.accbdd.complicated_bees.bees.gene.enums.EnumProductivity.ROBOTIC, prod);
        Object life = temporal == null ? null : com.accbdd.complicated_bees.bees.GeneticHelper.getGeneValue(temporal.toStack(queenItem), com.accbdd.complicated_bees.bees.gene.GeneLifespan.ID, true);
        check("gene immortal", life == com.accbdd.complicated_bees.bees.gene.enums.EnumLifespan.IMMORTAL, life);
        var boreal = com.accbdd.complicated_bees.registry.SpeciesRegistration.getFromIdentifier(net.minecraft.resources.Identifier.fromNamespaceAndPath("complicated_bees", "boreal"));
        int[] wide = boreal == null ? null : (int[]) com.accbdd.complicated_bees.bees.GeneticHelper.getGeneValue(boreal.toStack(queenItem), com.accbdd.complicated_bees.bees.gene.GeneTerritory.ID, true);
        check("gene territory from json (CB boreal)", wide != null && wide[0] != 4, wide == null ? null : java.util.Arrays.toString(wide));
        for (String placed : new String[]{"student", "yente"}) {
            var sp = com.accbdd.complicated_bees.registry.SpeciesRegistration.getFromIdentifier(dev.oculifelis.careerbees.CareerBees.id(placed));
            int[] terr = sp == null ? null : (int[]) com.accbdd.complicated_bees.bees.GeneticHelper.getGeneValue(sp.toStack(queenItem), com.accbdd.complicated_bees.bees.gene.GeneTerritory.ID, true);
            check("gene agoraphobic " + placed, terr != null && terr[0] == 1 && terr[1] == 1, terr == null ? null : java.util.Arrays.toString(terr));
        }
        var bensen = com.accbdd.complicated_bees.registry.SpeciesRegistration.getFromIdentifier(dev.oculifelis.careerbees.CareerBees.id("bensen"));
        var rainbow = com.accbdd.complicated_bees.registry.SpeciesRegistration.getFromIdentifier(dev.oculifelis.careerbees.CareerBees.id("rainbow"));
        check("species flags", bensen != null && bensen.isSecret() && !bensen.isRainbow() && rainbow != null && rainbow.isRainbow() && !rainbow.isSecret() && !robot.isSecret(),
                "bensen secret=" + (bensen != null && bensen.isSecret()) + " rainbow=" + (rainbow != null && rainbow.isRainbow()));

        var bark = dev.oculifelis.careerbees.registry.ModItems.BARK.get();
        var birch = dev.oculifelis.careerbees.item.BarkItem.of(bark, net.minecraft.world.level.block.Blocks.BIRCH_LOG, 1);
        var oak = dev.oculifelis.careerbees.item.BarkItem.of(bark, net.minecraft.world.level.block.Blocks.OAK_LOG, 1);
        var plain = new net.minecraft.world.item.ItemStack(bark);
        var rm = server.getRecipeManager();
        java.util.function.Function<java.util.List<net.minecraft.world.item.ItemStack>, net.minecraft.world.item.ItemStack> craft = items -> rm
                .getRecipeFor(net.minecraft.world.item.crafting.RecipeType.CRAFTING, net.minecraft.world.item.crafting.CraftingInput.of(2, 2, items), level)
                .map(h -> h.value().assemble(net.minecraft.world.item.crafting.CraftingInput.of(2, 2, items))).orElse(net.minecraft.world.item.ItemStack.EMPTY);
        var same = craft.apply(java.util.List.of(birch, birch, birch, birch));
        var mixed = craft.apply(java.util.List.of(birch, birch, birch, oak));
        var untyped = craft.apply(java.util.List.of(plain, plain, plain, plain));
        check("bark recipe", same.is(net.minecraft.world.item.Items.BIRCH_LOG) && mixed.isEmpty() && untyped.isEmpty(),
                "same=" + same + " mixed=" + mixed + " untyped=" + untyped);

        net.minecraft.core.BlockPos pos = new net.minecraft.core.BlockPos(4, level.getMaxY() - 2, 4);
        level.setBlock(pos, com.accbdd.complicated_bees.registry.BlocksRegistration.APIARY.get().defaultBlockState(), 3);
        if (level.getBlockEntity(pos) instanceof com.accbdd.complicated_bees.block.entity.BaseBeeHousing housing) {
            var queen = robot == null ? net.minecraft.world.item.ItemStack.EMPTY : robot.toStack(queenItem);
            var frame = new net.minecraft.world.item.ItemStack(dev.oculifelis.careerbees.registry.ModItems.PHEROMONE_FRAME.get());
            var item = (com.accbdd.complicated_bees.item.FrameItem) frame.getItem();
            for (int i = 0; i < 200 && housing.getOutputBuffer().isEmpty(); i++) item.onBeeCycle(housing, frame, queen);
            var vial = housing.getOutputBuffer().isEmpty() ? null : housing.getOutputBuffer().peek();
            check("pheromone frame", vial != null && vial.is(dev.oculifelis.careerbees.registry.ModItems.PHEROMONES.get()),
                    vial == null ? "none" : vial + " " + vial.getComponentsPatch());
            var zombie = new net.minecraft.world.entity.animal.pig.Pig(dev.oculifelis.careerbees.compat.Mc.PIG, level);
            zombie.setPos(pos.getX() + 1.5, pos.getY(), pos.getZ() + 0.5);
            zombie.setNoAi(true);
            level.addFreshEntity(zombie);
            var poison = new net.minecraft.world.item.ItemStack(dev.oculifelis.careerbees.registry.ModItems.POISON_FRAME.get());
            ((com.accbdd.complicated_bees.item.FrameItem) poison.getItem()).onBeeCycle(housing, poison, queen);
            check("poison frame", zombie.hasEffect(net.minecraft.world.effect.MobEffects.POISON), zombie.getActiveEffects());
            zombie.discard();
            filterChecks(level, pos, housing, queen);
        } else {
            check("apiary block entity", false, level.getBlockEntity(pos));
        }
        level.removeBlock(pos, false);
    }

    private static void filterChecks(net.minecraft.server.level.ServerLevel level, net.minecraft.core.BlockPos pos,
                                     com.accbdd.complicated_bees.block.entity.BaseBeeHousing housing, net.minecraft.world.item.ItemStack queen) {
        var F = dev.oculifelis.careerbees.effect.FilterSettings.DEFAULT;
        var sword = new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.IRON_SWORD);
        var hurt = sword.copy();
        hurt.setDamageValue(40);
        var named = sword.copy();
        named.set(net.minecraft.core.component.DataComponents.CUSTOM_NAME, net.minecraft.network.chat.Component.literal("x"));
        var oak = new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.OAK_LOG);
        var stone = new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.STONE);
        var exact = F.withItem(sword).withIgnoreComponents(false);
        boolean ok = F.matches(stone) && F.withItem(sword).matches(named) && !F.withItem(sword).matches(stone)
                && exact.matches(sword) && !exact.matches(hurt) && !exact.matches(named)
                && exact.withIgnoreDamage(true).matches(hurt) && !exact.withIgnoreDamage(true).matches(named);
        var logs = F.withTagMode(true).withTag("#minecraft:logs");
        boolean tags = logs.matches(oak) && !logs.matches(stone) && F.withTagMode(true).matches(stone)
                && !F.withTagMode(true).withTag("Not A Tag!").matches(oak);
        check("filter settings", ok && tags, "item=" + ok + " tag=" + tags);

        var frame = new net.minecraft.world.item.ItemStack(dev.oculifelis.careerbees.registry.ModItems.SETTINGS_FRAME.get());
        frame.set(dev.oculifelis.careerbees.registry.ModComponents.FILTER.get(), F.withItem(stone));
        housing.getLogic().setLevel(level); // normally set on the first tick
        housing.getFrameItems().setStackInSlot(0, frame);
        housing.getOutputBuffer().clear();
        var ctx = new dev.oculifelis.careerbees.effect.CareerEffect.Ctx(level, housing, queen, 1, level.getRandom());
        var dirt = new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.DIRT);
        check("settings frame filter", dev.oculifelis.careerbees.effect.ItemFilter.passes(ctx, stone)
                && !dev.oculifelis.careerbees.effect.ItemFilter.passes(ctx, dirt), dev.oculifelis.careerbees.effect.ItemFilter.settings(ctx));

        var pickup = new dev.oculifelis.careerbees.effect.WorldEffects.Pickup();
        java.util.function.Function<net.minecraft.world.item.ItemStack, net.minecraft.world.entity.item.ItemEntity> drop = st -> {
            var e = new net.minecraft.world.entity.item.ItemEntity(level, pos.getX() + 0.5, pos.getY() + 1.2, pos.getZ() + 0.5, st.copy());
            e.setNoGravity(true);
            e.setDeltaMovement(net.minecraft.world.phys.Vec3.ZERO);
            level.addFreshEntity(e);
            return e;
        };
        var s1 = drop.apply(stone);
        var d1 = drop.apply(dirt);
        for (int i = 0; i < 40 && s1.isAlive(); i++) pickup.runEffect(housing, queen, 0);
        boolean took = !s1.isAlive() && d1.isAlive() && housing.getOutputBuffer().size() == 1;
        var s2 = drop.apply(stone);
        for (int i = 0; i < 40; i++) pickup.runEffect(housing, queen, 0);
        boolean kept = s2.isAlive() && housing.getOutputBuffer().size() == 1;
        frame.set(dev.oculifelis.careerbees.registry.ModComponents.FILTER.get(), F.withItem(stone).withVoidExcess(true));
        for (int i = 0; i < 40 && s2.isAlive(); i++) pickup.runEffect(housing, queen, 0);
        boolean voided = !s2.isAlive() && housing.getOutputBuffer().size() == 1;
        check("pickup filter", took && kept && voided, "took=" + took + " kept=" + kept + " voided=" + voided
                + " buffer=" + housing.getOutputBuffer().size());
        d1.discard();
        s2.discard();
        housing.getFrameItems().setStackInSlot(0, net.minecraft.world.item.ItemStack.EMPTY);
        housing.getOutputBuffer().clear();
    }

    private static void gunChecks(MinecraftServer server) {
        var level = server.overworld();
        server.getCommands().performPrefixedCommand(server.createCommandSourceStack().withSuppressedOutput(), "weather clear");
        var doctor = com.accbdd.complicated_bees.registry.SpeciesRegistration.getFromIdentifier(dev.oculifelis.careerbees.CareerBees.id("doctor"));
        if (doctor == null) { check("bee gun species", false, "no doctor"); return; }
        var drone = doctor.toStack(com.accbdd.complicated_bees.registry.ItemsRegistration.DRONE.get());
        drone.setCount(4);
        check("bee gun loadable", dev.oculifelis.careerbees.item.BeeGunItem.loadable(drone), drone);
        var player = net.neoforged.neoforge.common.util.FakePlayerFactory.getMinecraft(level);
        var gun = new net.minecraft.world.item.ItemStack(dev.oculifelis.careerbees.registry.ModItems.BEE_GUN.get());
        player.getInventory().setItem(0, gun);
        var menu = new dev.oculifelis.careerbees.menu.GunMenu(1, player.getInventory(), 0);
        menu.getSlot(0).set(drone.copy());
        menu.getSlot(6).set(new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.HONEY_BOTTLE, 2));
        var held = player.getInventory().getItem(0);
        int honey = dev.oculifelis.careerbees.item.BeeGunItem.honey(held);
        int per = Config.server(Config.GUN_PER_BOTTLE);
        var loaded = dev.oculifelis.careerbees.item.BeeGunItem.chambers(held).get(0);
        check("bee gun menu", honey == 2 * per && loaded.getCount() == 4 && menu.getSlot(6).getItem().isEmpty(),
                "honey=" + honey + " chamber=" + loaded + " bottleSlot=" + menu.getSlot(6).getItem());
        menu.removed(player);

        var queen = dev.oculifelis.careerbees.item.BeeGunItem.queen(drone);
        var effect = dev.oculifelis.careerbees.effect.Guns.effect(drone);
        var g = dev.oculifelis.careerbees.effect.Guns.gun(effect);
        net.minecraft.core.BlockPos pos = new net.minecraft.core.BlockPos(2, level.getMaxY() - 8, 10);
        var pig = new net.minecraft.world.entity.animal.pig.Pig(dev.oculifelis.careerbees.compat.Mc.PIG, level);
        pig.setPos(pos.getX() + 4.5, pos.getY(), pos.getZ() + 0.5);
        pig.setNoAi(true);
        pig.setNoGravity(true);
        boolean pigAdded = level.addFreshEntity(pig);
        pig.setHealth(1f);
        player.setPos(pos.getX() + 0.5, pos.getY() - 1.0, pos.getZ() + 0.5);
        var swarm = dev.oculifelis.careerbees.entity.BeeSwarm.fire(level, player, queen, g, effect);
        swarm.setPos(pos.getX() + 0.5, pig.getY() + 0.4, pos.getZ() + 0.5);
        swarm.shoot(1, 0, 0, 0.6f, 0f);
        level.addFreshEntity(swarm);
        int ticks = 0;
        while (!swarm.isRemoved() && ticks < 20000) { swarm.tick(); ticks++; }
        check("bee gun swarm", queen.isEmpty() == false && g != null && pig.getHealth() > 1f && pigAdded,
                "gun=" + (g != null) + " pigAdded=" + pigAdded + " ticks=" + ticks + " pigHealth=" + pig.getHealth() + " regen=" + pig.hasEffect(net.minecraft.world.effect.MobEffects.REGENERATION));
        pig.discard();
        player.getInventory().setItem(0, net.minecraft.world.item.ItemStack.EMPTY);
        gunSweep(server);
    }

    private static int sweepY;

    /** Air and loose entities back in the test lane. */
    private static void clearLane(net.minecraft.server.level.ServerLevel level) {
        int y = sweepY;
        for (int x = 0; x < 16; x++) for (int z = 0; z < 16; z++) for (int dy = -6; dy <= 4; dy++)
            level.setBlock(new net.minecraft.core.BlockPos(x, y + dy, z), net.minecraft.world.level.block.Blocks.AIR.defaultBlockState(), 2 | 16);
        var box = new net.minecraft.world.phys.AABB(-2, y - 8, -2, 18, y + 6, 18);
        for (var e : level.getEntitiesOfClass(net.minecraft.world.entity.Entity.class, box, e -> !(e instanceof net.minecraft.world.entity.player.Player))) e.discard();
    }

    private static <T extends net.minecraft.world.entity.Entity> T spawn(net.minecraft.server.level.ServerLevel level, net.minecraft.world.entity.EntityType<T> type, double x, double y, double z) {
        T e = type.create(level, net.minecraft.world.entity.EntitySpawnReason.TRIGGERED);
        e.setPos(x, y, z);
        if (e instanceof net.minecraft.world.entity.Mob m) { m.setNoAi(true); m.setPersistenceRequired(); }
        e.setNoGravity(true);
        level.addFreshEntity(e);
        return e;
    }

    /** Fires one swarm east along the lane at height y and ticks it out; returns the ticks it lived. */
    private static int fire(net.minecraft.server.level.ServerLevel level, net.minecraft.world.entity.player.Player player, net.minecraft.world.item.ItemStack queen,
                            dev.oculifelis.careerbees.effect.Guns.Gun g, dev.oculifelis.careerbees.effect.CareerEffect effect, double y) {
        player.setPos(-3.5, sweepY - 1.0, 8.5);
        var swarm = dev.oculifelis.careerbees.entity.BeeSwarm.fire(level, player, queen, g, effect);
        swarm.setPos(1.5, y, 8.5);
        swarm.shoot(1, 0, 0, 0.6f, 0f);
        level.addFreshEntity(swarm);
        int ticks = 0;
        while (!swarm.isRemoved() && ticks < 20000) { swarm.tick(); ticks++; }
        return ticks;
    }

    private static long itemsInLane(net.minecraft.server.level.ServerLevel level, java.util.function.Predicate<net.minecraft.world.item.ItemStack> p) {
        var box = new net.minecraft.world.phys.AABB(-2, sweepY - 8, -2, 18, sweepY + 6, 18);
        return level.getEntitiesOfClass(net.minecraft.world.entity.item.ItemEntity.class, box, e -> p.test(e.getItem())).stream().mapToLong(e -> e.getItem().getCount()).sum();
    }

    /** Spec numbers (careerbees-spec ITEMS.md / EFFECTS.md): honey, fire cooldown, launch speed, swarm life, gun cooldowns. */
    private static void gunNumbers(net.minecraft.server.level.ServerLevel level, java.util.Map<String, net.minecraft.world.item.ItemStack> queens,
                                   java.util.Map<String, dev.oculifelis.careerbees.effect.CareerEffect> effects) {
        check("gun numbers honey", dev.oculifelis.careerbees.Config.GUN_CAPACITY.getDefault() == 1000
                && dev.oculifelis.careerbees.Config.GUN_PER_BOTTLE.getDefault() == 250 && dev.oculifelis.careerbees.Config.GUN_PER_SHOT.getDefault() == 2
                && dev.oculifelis.careerbees.item.BeeGunItem.COOLDOWN == 10,
                "cap=" + dev.oculifelis.careerbees.Config.GUN_CAPACITY.getDefault() + " bottle=" + dev.oculifelis.careerbees.Config.GUN_PER_BOTTLE.getDefault()
                + " shot=" + dev.oculifelis.careerbees.Config.GUN_PER_SHOT.getDefault() + " cooldown=" + dev.oculifelis.careerbees.item.BeeGunItem.COOLDOWN);
        // interval, chance per EFFECTS.md; 0 = every tick.
        java.util.Map<String, double[]> spec = new java.util.TreeMap<>();
        for (String[] r : new String[][]{{"healing","10","1"},{"curative","10","1"},{"burning","4","0.01"},{"electrify","5","0.1"},{"husbandry","20","1"},
                {"butcher","20","1"},{"taxation","100","1"},{"thief","10","1"},{"crusher","400","1"},{"smelt","20","1"},{"honey_ingot","200","1"},
                {"repair","20","0.2"},{"sharpen","20","0.2"},{"armorer","16","0.2"},{"glazing","30","1"},{"retraining","16","1"},{"priest","100","1"},
                {"mason","10","1"},{"painting","600","1"},{"lumber","200","1"},{"teleposition","0","1"},{"police","10","1"},{"energy","0","1"},
                {"clockwinding","0","1"},{"temporal","0","1"}})
            spec.put(r[0], new double[]{Double.parseDouble(r[1]), Double.parseDouble(r[2])});
        List<String> bad = new ArrayList<>();
        for (String id : spec.keySet()) if (!effects.containsKey(id)) bad.add(id + ":not a gun effect");
        for (String id : effects.keySet()) if (!spec.containsKey(id)) bad.add(id + ":not in spec");
        var random = net.minecraft.util.RandomSource.create(7);
        for (var e : effects.entrySet()) {
            double[] want = spec.get(e.getKey());
            if (want == null) continue;
            var effect = e.getValue();
            var g = dev.oculifelis.careerbees.effect.Guns.gun(effect);
            int interval = Math.max(0, effect.interval());
            if (want[0] == 0 ? interval > 1 : interval != (int) want[0]) bad.add(e.getKey() + ":interval " + effect.interval());
            if (Math.abs(effect.chance() - want[1]) > 1e-9) bad.add(e.getKey() + ":chance " + effect.chance());
            for (double speed : new double[]{0.5, 1, 2}) {
                for (int lifespan : new int[]{35, 200}) {
                    int got = g.cooldown(effect, speed, lifespan, random);
                    long base = Math.round(Math.max(1, want[0]) / speed);
                    boolean ok = switch (e.getKey()) {
                        case "police", "clockwinding" -> got == 200;
                        case "energy" -> got == Math.min(120, lifespan) * 12;
                        case "temporal" -> got == Math.round(20 / speed);
                        default -> want[1] >= 1 ? got == Math.max(1, base)
                                : speed != 1 || (got >= want[0] && got % (int) want[0] == 0);
                    };
                    if (!ok) bad.add(e.getKey() + ":cooldown s" + speed + " l" + lifespan + "=" + got);
                }
            }
        }
        check("gun numbers cooldowns", bad.isEmpty(), effects.size() + " effects " + bad);
        // Launch speed and swarm life from one queen.
        var queen = queens.values().iterator().next();
        int[] r = dev.oculifelis.careerbees.effect.Guns.radii(queen);
        Object life = com.accbdd.complicated_bees.bees.GeneticHelper.getGeneValue(queen, com.accbdd.complicated_bees.bees.gene.GeneLifespan.ID, true);
        int lifespan = life instanceof com.accbdd.complicated_bees.bees.gene.enums.EnumLifespan l ? l.value : 35;
        var player = net.neoforged.neoforge.common.util.FakePlayerFactory.getMinecraft(level);
        var first = effects.get(queens.keySet().iterator().next());
        var swarm = dev.oculifelis.careerbees.entity.BeeSwarm.fire(level, player, queen, dev.oculifelis.careerbees.effect.Guns.gun(first), first);
        check("gun numbers swarm life", swarm.maxLife() == Math.min(2L * lifespan, 400), "lifespan=" + lifespan + " maxLife=" + swarm.maxLife());
        swarm.discard();
        check("gun numbers launch", dev.oculifelis.careerbees.item.BeeGunItem.power(r) == (float) Math.max(0.25, (r[0] * 2 + r[1]) * 0.3 / 24.0),
                "radii=" + r[0] + "," + r[1] + " power=" + dev.oculifelis.careerbees.item.BeeGunItem.power(r));
    }

    /** One swarm per gun effect at a fitting target; each must act once. */
    private static void gunSweep(MinecraftServer server) {
        var level = server.overworld();
        sweepY = level.getMaxY() - 24;
        int y = sweepY;
        var player = net.neoforged.neoforge.common.util.FakePlayerFactory.getMinecraft(level);
        var species = server.registryAccess().lookupOrThrow(SpeciesRegistration.SPECIES_REGISTRY_KEY);
        java.util.Map<String, net.minecraft.world.item.ItemStack> queens = new java.util.TreeMap<>();
        java.util.Map<String, dev.oculifelis.careerbees.effect.CareerEffect> effects = new java.util.HashMap<>();
        List<String> noGun = new ArrayList<>();
        for (var key : species.keySet()) {
            if (!key.getNamespace().equals(NS)) continue;
            var sp = SpeciesRegistration.getFromIdentifier(key);
            if (sp == null) continue;
            var drone = sp.toStack(com.accbdd.complicated_bees.registry.ItemsRegistration.DRONE.get());
            var effect = dev.oculifelis.careerbees.effect.Guns.effect(drone);
            if (effect == null) continue;
            if (dev.oculifelis.careerbees.effect.Guns.gun(effect) == null) { if (!noGun.contains(effect.id())) noGun.add(effect.id()); continue; }
            if (queens.containsKey(effect.id())) continue;
            queens.put(effect.id(), dev.oculifelis.careerbees.item.BeeGunItem.queen(drone));
            effects.put(effect.id(), effect);
        }
        lines.add("INFO gun sweep: " + queens.size() + " gun effects " + queens.keySet() + ", no gun " + noGun);
        gunNumbers(level, queens, effects);
        java.util.Map<String, net.minecraft.world.item.ItemStack> samples = new java.util.HashMap<>();
        samples.put("crusher", new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.IRON_ORE));
        samples.put("smelt", new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.RAW_IRON));
        samples.put("honey_ingot", new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.IRON_INGOT));
        var worn = new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.IRON_SWORD);
        worn.setDamageValue(100);
        samples.put("repair", worn);
        samples.put("sharpen", new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.IRON_SWORD));
        samples.put("armorer", new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.IRON_CHESTPLATE));
        samples.put("glazing", new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.BREAD));
        samples.put("priest", new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.IRON_PICKAXE));
        // Retraining: a drone carrying some other species' effect gene, to be trained back to its own.
        {
            net.minecraft.world.item.ItemStack base = null;
            com.accbdd.complicated_bees.bees.gene.IGene<?> foreign = null;
            for (var key : species.keySet()) {
                if (!key.getNamespace().equals(NS)) continue;
                var sp = SpeciesRegistration.getFromIdentifier(key);
                if (sp == null || sp.getDefaultChromosome() == null) continue;
                var gene = sp.getDefaultChromosome().getGene(com.accbdd.complicated_bees.bees.gene.GeneEffect.ID);
                if (gene == null) continue;
                if (base == null) { base = sp.toStack(com.accbdd.complicated_bees.registry.ItemsRegistration.DRONE.get()); foreign = gene; continue; }
                if (gene.get() != foreign.get()) { foreign = gene; break; }
            }
            var g = base == null ? null : com.accbdd.complicated_bees.bees.GeneticHelper.getGenome(base);
            if (g != null) {
                com.accbdd.complicated_bees.bees.GeneticHelper.setGenome(base, new com.accbdd.complicated_bees.bees.Genome(
                        g.primary().copy().setGene(com.accbdd.complicated_bees.bees.gene.GeneEffect.ID, foreign),
                        g.secondary().copy().setGene(com.accbdd.complicated_bees.bees.gene.GeneEffect.ID, foreign)));
                samples.put("retraining", base);
                HousingSweep.retrainSample = base.copy();
            }
        }
        var target = new net.minecraft.core.BlockPos(5, y, 8);
        clearLane(level);
        for (var en : queens.entrySet()) {
            String id = en.getKey();
            var queen = en.getValue();
            var effect = effects.get(id);
            var g = dev.oculifelis.careerbees.effect.Guns.gun(effect);
            String name = "gun sweep " + id;
            try {
                int ticks;
                switch (id) {
                    case "healing", "curative", "electrify" -> {
                        var pig = spawn(level, dev.oculifelis.careerbees.compat.Mc.PIG, 5.5, y, 8.5);
                        if (id.equals("healing")) pig.setHealth(1f);
                        if (id.equals("curative")) pig.addEffect(new net.minecraft.world.effect.MobEffectInstance(net.minecraft.world.effect.MobEffects.POISON, 2000, 0));
                        float before = pig.getHealth();
                        ticks = fire(level, player, queen, g, effect, y + 0.4);
                        boolean ok = switch (id) {
                            case "healing" -> pig.getHealth() > before;
                            case "curative" -> !pig.hasEffect(net.minecraft.world.effect.MobEffects.POISON);
                            default -> pig.getHealth() < before;
                        };
                        check(name, ok, "ticks=" + ticks + " health " + before + "->" + pig.getHealth());
                    }
                    case "husbandry" -> {
                        var cow = spawn(level, dev.oculifelis.careerbees.compat.Mc.COW, 5.5, y, 8.5);
                        ticks = fire(level, player, queen, g, effect, y + 0.4);
                        check(name, cow.isInLove(), "ticks=" + ticks + " inLove=" + cow.isInLove());
                    }
                    case "butcher" -> {
                        var pig = spawn(level, dev.oculifelis.careerbees.compat.Mc.PIG, 5.5, y, 8.5);
                        spawn(level, dev.oculifelis.careerbees.compat.Mc.PIG, 8.5, y, 12.5);
                        spawn(level, dev.oculifelis.careerbees.compat.Mc.PIG, 8.5, y, 4.5);
                        ticks = fire(level, player, queen, g, effect, y + 0.4);
                        check(name, !pig.isAlive(), "ticks=" + ticks + " alive=" + pig.isAlive());
                    }
                    case "police", "taxation" -> {
                        int tries = 0;
                        boolean ok = false;
                        String detail = "";
                        while (!ok && tries < 6) {
                            tries++;
                            var z = spawn(level, dev.oculifelis.careerbees.compat.Mc.ZOMBIE, 5.5, y, 8.5);
                            float before = z.getHealth();
                            ticks = fire(level, player, queen, g, effect, y + 0.9);
                            long drops = itemsInLane(level, s -> true);
                            ok = id.equals("police") ? z.getHealth() < before : drops > 0;
                            detail = "ticks=" + ticks + " health " + before + "->" + z.getHealth() + " drops=" + drops + " tries=" + tries;
                            clearLane(level);
                        }
                        check(name, ok, detail);
                    }
                    case "thief" -> {
                        var victim = net.neoforged.neoforge.common.util.FakePlayerFactory.get(level, new com.mojang.authlib.GameProfile(java.util.UUID.fromString("6c6f6f74-6265-6573-0000-000000000001"), "ccb_mark"));
                        victim.getInventory().clearContent();
                        for (int i = 0; i < victim.getInventory().getContainerSize(); i++)
                            victim.getInventory().setItem(i, new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.EMERALD, 3));
                        int before = victim.getInventory().countItem(net.minecraft.world.item.Items.EMERALD);
                        boolean accepts = g.entity(level, victim);
                        var ctx = new dev.oculifelis.careerbees.effect.CareerEffect.Ctx(level, null, queen, dev.oculifelis.careerbees.effect.Guns.speed(queen), level.getRandom(),
                                target, dev.oculifelis.careerbees.effect.Guns.radii(queen), victim);
                        g.act(effect, ctx, new dev.oculifelis.careerbees.effect.Guns.Target(victim, null, null, net.minecraft.core.Direction.UP));
                        int after = victim.getInventory().countItem(net.minecraft.world.item.Items.EMERALD);
                        long dropped = itemsInLane(level, s -> s.is(net.minecraft.world.item.Items.EMERALD));
                        check(name, accepts && after == before - 1 && dropped == 1, "direct accepts=" + accepts + " emeralds " + before + "->" + after + " dropped=" + dropped);
                        victim.getInventory().clearContent();
                    }
                    case "burning" -> {
                        level.setBlock(target, net.minecraft.world.level.block.Blocks.STONE.defaultBlockState(), 3);
                        ticks = fire(level, player, queen, g, effect, y + 0.5);
                        var above = level.getBlockState(target.above());
                        check(name, above.getBlock() instanceof net.minecraft.world.level.block.BaseFireBlock, "ticks=" + ticks + " above=" + above);
                    }
                    case "mason" -> {
                        level.setBlock(target, net.minecraft.world.level.block.Blocks.COBBLESTONE.defaultBlockState(), 3);
                        ticks = fire(level, player, queen, g, effect, y + 0.5);
                        var s = level.getBlockState(target);
                        check(name, s.is(net.minecraft.world.level.block.Blocks.STONE), "ticks=" + ticks + " block=" + s);
                    }
                    case "lumber" -> {
                        for (int dy = 0; dy < 3; dy++) level.setBlock(target.above(dy), net.minecraft.world.level.block.Blocks.OAK_LOG.defaultBlockState(), 3);
                        level.setBlock(target.above(3), net.minecraft.world.level.block.Blocks.OAK_LEAVES.defaultBlockState()
                                .setValue(net.minecraft.world.level.block.LeavesBlock.PERSISTENT, true), 3);
                        ticks = fire(level, player, queen, g, effect, y + 0.5);
                        long bark = itemsInLane(level, s -> s.is(dev.oculifelis.careerbees.registry.ModItems.BARK.get()));
                        check(name, bark > 0 && level.getBlockState(target).is(net.minecraft.world.level.block.Blocks.OAK_LOG), "ticks=" + ticks + " bark=" + bark);
                    }
                    case "painting" -> {
                        for (int dy = -2; dy <= 3; dy++) for (int dz = -3; dz <= 3; dz++)
                            level.setBlock(target.offset(0, dy, dz), net.minecraft.world.level.block.Blocks.STONE.defaultBlockState(), 3);
                        ticks = fire(level, player, queen, g, effect, y + 0.5);
                        var box = new net.minecraft.world.phys.AABB(-2, y - 8, -2, 18, y + 6, 18);
                        int paintings = level.getEntitiesOfClass(net.minecraft.world.entity.decoration.painting.Painting.class, box, e -> true).size();
                        check(name, paintings == 1, "ticks=" + ticks + " paintings=" + paintings);
                    }
                    case "teleposition" -> {
                        // Andesite all round the target in reach of a small territory; the flight row stays open.
                        for (int x = 0; x < 16; x++) for (int z = 0; z < 16; z++) for (int dy = -2; dy <= 2; dy++)
                            if (!(z == 8 && dy == 0 && x < 5))
                                level.setBlock(new net.minecraft.core.BlockPos(x, y + dy, z), net.minecraft.world.level.block.Blocks.ANDESITE.defaultBlockState(), 2 | 16);
                        level.setBlock(target, net.minecraft.world.level.block.Blocks.STONE.defaultBlockState(), 3);
                        ticks = fire(level, player, queen, g, effect, y + 0.5);
                        var s = level.getBlockState(target);
                        check(name, s.is(net.minecraft.world.level.block.Blocks.ANDESITE), "ticks=" + ticks + " block=" + s);
                    }
                    case "clockwinding", "energy" -> {
                        String[] candidates = id.equals("clockwinding") ? new String[]{"furnace_generator"}
                                : new String[]{"centrifuge", "autolyzer", "gyrofuge_energy_cell", "mellarium_energy_cell", "furnace_generator"};
                        net.minecraft.world.level.block.state.BlockState st = null;
                        net.neoforged.neoforge.energy.IEnergyStorage cap = null;
                        boolean accepts = false;
                        String used = "";
                        for (String c : candidates) {
                            var blk = net.minecraft.core.registries.BuiltInRegistries.BLOCK.getValue(net.minecraft.resources.Identifier.fromNamespaceAndPath("complicated_bees", c));
                            level.setBlock(target, blk.defaultBlockState(), 3);
                            st = level.getBlockState(target);
                            cap = com.accbdd.complicated_bees.port.PortCapabilities.find(level, com.accbdd.complicated_bees.port.PortCapabilities.EnergyStorage.BLOCK, target, st, level.getBlockEntity(target), net.minecraft.core.Direction.WEST);
                            accepts = g.block(level, target, st, net.minecraft.core.Direction.WEST);
                            used = c;
                            if (accepts) break;
                        }
                        if (!accepts) {
                            lines.add("SKIP " + name + " no receiver among " + String.join(",", candidates));
                            break;
                        }
                        name += " on " + used;
                        int before = cap == null ? -1 : cap.getEnergyStored();
                        ticks = fire(level, player, queen, g, effect, y + 0.5);
                        int after = cap == null ? -1 : cap.getEnergyStored();
                        check(name, after > before, "ticks=" + ticks + " energy " + before + "->" + after);
                    }
                    case "temporal" -> {
                        level.setBlock(target, net.minecraft.world.level.block.Blocks.FARMLAND.defaultBlockState().setValue(net.minecraft.world.level.block.state.properties.BlockStateProperties.MOISTURE, 7), 3);
                        ticks = fire(level, player, queen, g, effect, y + 0.5);
                        var s = level.getBlockState(target);
                        boolean ok = !s.is(net.minecraft.world.level.block.Blocks.FARMLAND) || s.getValue(net.minecraft.world.level.block.state.properties.BlockStateProperties.MOISTURE) < 7;
                        check(name, ok, "ticks=" + ticks + " block=" + s);
                    }
                    default -> {
                        var sample = samples.get(id);
                        if (!(effect instanceof dev.oculifelis.careerbees.effect.PedestalEffect) || sample == null) {
                            lines.add("SKIP " + name + " no target set up");
                            break;
                        }
                        var item = new net.minecraft.world.entity.item.ItemEntity(level, 5.5, y, 8.5, sample.copy());
                        item.setNoGravity(true);
                        item.setDeltaMovement(net.minecraft.world.phys.Vec3.ZERO);
                        item.setNeverPickUp();
                        level.addFreshEntity(item);
                        ticks = fire(level, player, queen, g, effect, y + 0.15);
                        boolean changed = !net.minecraft.world.item.ItemStack.matches(item.getItem(), sample);
                        check(name, changed, "ticks=" + ticks + " " + sample + "->" + item.getItem());
                    }
                }
            } catch (Throwable t) {
                check(name, false, "threw " + t);
                t.printStackTrace();
            }
            clearLane(level);
        }
    }

    /** Every gene each species file writes in default_chromosome must read back as written (a bad read falls back silently). */
    private static void geneAudit(MinecraftServer server) {
        var files = server.getResourceManager().listResources("complicated_bees/species", id -> id.getPath().endsWith(".json"));
        java.util.Map<String, List<String>> bad = new java.util.TreeMap<>();
        java.util.Map<String, Integer> genes = new java.util.TreeMap<>();
        for (var e : files.entrySet()) {
            var file = e.getKey();
            String name = file.getPath().substring("complicated_bees/species/".length(), file.getPath().length() - 5);
            var id = net.minecraft.resources.Identifier.fromNamespaceAndPath(file.getNamespace(), name);
            List<String> out = bad.computeIfAbsent(file.getNamespace(), k -> new ArrayList<>());
            try (var in = e.getValue().openAsReader()) {
                JsonObject json = JsonParser.parseReader(in).getAsJsonObject();
                if (!json.has("default_chromosome")) continue;
                var sp = SpeciesRegistration.getFromIdentifier(id);
                if (sp == null) { out.add(id + " not registered"); continue; }
                net.minecraft.nbt.CompoundTag loaded = sp.getDefaultChromosome().serialize();
                for (var g : json.getAsJsonObject("default_chromosome").entrySet()) {
                    // The species gene is rebuilt from the file's own id and top-level "dominant", not from this entry.
                    if (g.getKey().equals("complicated_bees:species")) continue;
                    genes.merge(file.getNamespace(), 1, Integer::sum);
                    var nbt = loaded.get(g.getKey());
                    if (!(nbt instanceof net.minecraft.nbt.CompoundTag gene)) { out.add(id + " " + g.getKey() + " missing"); continue; }
                    for (var f : g.getValue().getAsJsonObject().entrySet()) {
                        String why = sameValue(f.getValue(), gene.get(f.getKey()));
                        if (why != null) out.add(id + " " + g.getKey() + "." + f.getKey() + " json=" + f.getValue() + " read=" + why);
                    }
                }
            } catch (Exception ex) {
                out.add(id + " " + ex);
            }
        }
        for (var ns : bad.keySet()) {
            List<String> out = bad.get(ns);
            check("json genes read back (" + ns + ")", out.isEmpty(), "genes=" + genes.getOrDefault(ns, 0) + " bad=" + out.size() + " " + out.subList(0, Math.min(out.size(), 25)));
        }
    }

    /** null when the json value and the loaded tag agree, else what was read. */
    private static String sameValue(com.google.gson.JsonElement j, net.minecraft.nbt.Tag t) {
        if (t == null) return "absent";
        if (j.isJsonArray()) {
            var a = j.getAsJsonArray();
            double[] got;
            if (t instanceof net.minecraft.nbt.IntArrayTag ia) got = java.util.Arrays.stream(ia.getAsIntArray()).asDoubleStream().toArray();
            else if (t instanceof net.minecraft.nbt.ListTag l) { got = new double[l.size()]; for (int i = 0; i < l.size(); i++) got[i] = l.get(i).asDouble().orElse(Double.NaN); }
            else return t.toString();
            if (got.length != a.size()) return t.toString();
            for (int i = 0; i < got.length; i++) if (got[i] != a.get(i).getAsDouble()) return t.toString();
            return null;
        }
        if (!j.isJsonPrimitive()) return null;
        var p = j.getAsJsonPrimitive();
        if (p.isString()) {
            String got = t.asString().orElse(null);
            return got != null && got.equalsIgnoreCase(p.getAsString()) ? null : t.toString();
        }
        double want = p.isBoolean() ? (p.getAsBoolean() ? 1 : 0) : p.getAsDouble();
        double got = t.asDouble().orElse(Double.NaN);
        return got == want ? null : t.toString();
    }

    @SubscribeEvent
    public static void started(ServerStartedEvent event) {
        MinecraftServer server = event.getServer();
        try {
            var species = server.registryAccess().lookupOrThrow(SpeciesRegistration.SPECIES_REGISTRY_KEY);
            long sp = species.keySet().stream().filter(k -> k.getNamespace().equals(NS)).count();
            check("species", sp == 44, sp);

            var effects = server.registryAccess().lookupOrThrow(BeeEffectRegistration.BEE_REGISTRY_KEY);
            List<String> missing = Config.EFFECTS.stream().filter(e -> !effects.containsKey(dev.oculifelis.careerbees.CareerBees.id(e))).toList();
            check("effects", missing.isEmpty(), Config.EFFECTS.size() + " missing=" + missing);

            var masonCobble = dev.oculifelis.careerbees.effect.CareerDataMaps.masonry(net.minecraft.world.level.block.Blocks.COBBLESTONE);
            check("masonry data map cobblestone", masonCobble == net.minecraft.world.level.block.Blocks.STONE, masonCobble);
            var masonDirt = dev.oculifelis.careerbees.effect.CareerDataMaps.masonry(net.minecraft.world.level.block.Blocks.DIRT);
            check("masonry data map dirt has none", masonDirt == null, masonDirt);
            check("honey ingot input tag", new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.IRON_INGOT)
                    .is(dev.oculifelis.careerbees.effect.CareerDataMaps.HONEY_INGOT_INPUT), "iron_ingot");
            check("trade currency tag", new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.EMERALD)
                    .is(dev.oculifelis.careerbees.effect.CareerDataMaps.TRADE_CURRENCY), "emerald");
            var effReg = net.minecraft.core.registries.BuiltInRegistries.REGISTRY.getValue(
                    com.accbdd.complicated_bees.registry.BeeEffectRegistration.BEE_REGISTRY_KEY.identifier());
            Object healing = effReg == null ? null : effReg.getValue(net.minecraft.resources.Identifier.fromNamespaceAndPath(NS, "healing"));
            Object jazz = effReg == null ? null : effReg.getValue(net.minecraft.resources.Identifier.fromNamespaceAndPath(NS, "jazz"));
            if (healing instanceof dev.oculifelis.careerbees.effect.CareerEffect h) {
                var t = dev.oculifelis.careerbees.effect.CareerDataMaps.timing(h);
                check("effect timing shipped healing", t != null && t.interval().orElse(-1) == 10 && h.interval() == 10, t);
            } else check("effect timing shipped healing", false, healing);
            if (jazz instanceof dev.oculifelis.careerbees.effect.CareerEffect j) {
                check("effect timing pack override jazz", j.interval() == 33 && j.defaultInterval() == 20 && j.chance() == 0.3,
                        "interval " + j.interval() + " chance " + j.chance());
            } else check("effect timing pack override jazz", false, jazz);

            var files = server.getResourceManager().listResources("recipe", id -> id.getNamespace().equals(NS) && id.getPath().endsWith(".json"));
            List<String> loaded = new ArrayList<>();
            for (var h : server.getRecipeManager().getRecipes()) {
                String id = h.id().identifier().toString();
                if (id.startsWith(NS + ":")) loaded.add(id);
            }
            long mutations = loaded.stream().filter(s -> s.startsWith(NS + ":mutation/")).count();
            check("mutations", mutations == 46, mutations);
            List<String> notLoaded = new ArrayList<>();
            for (var id : files.keySet()) {
                String p = id.getPath();
                String rid = NS + ":" + p.substring("recipe/".length(), p.length() - 5);
                if (!loaded.contains(rid)) notLoaded.add(rid);
            }
            check("recipes", notLoaded.isEmpty(), "files=" + files.size() + " loaded=" + loaded.size() + " notLoaded=" + notLoaded);

            JsonObject lang;
            try (var in = Files.newBufferedReader(Path.of("../../../src/datagen/resources/assets/careerbees/lang/en_us.json"), StandardCharsets.UTF_8)) {
                lang = JsonParser.parseReader(in).getAsJsonObject();
            }
            List<String> noName = new ArrayList<>();
            BuiltInRegistries.ITEM.keySet().stream().filter(k -> k.getNamespace().equals(NS)).forEach(k -> {
                String key = BuiltInRegistries.ITEM.getValue(k).getDescriptionId();
                if (!lang.has(key)) noName.add(key);
            });
            species.keySet().stream().filter(k -> k.getNamespace().equals(NS)).forEach(k -> {
                if (!lang.has("species.complicated_bees." + k)) noName.add("species " + k);
            });
            for (String e : Config.EFFECTS) {
                if (!lang.has("effect.complicated_bees.careerbees:" + e)) noName.add("effect " + e);
            }
            check("lang", noName.isEmpty(), noName);
            careerChecks(server);
            frameChecks(server);
            dataMapChecks();
            geneAudit(server);
            gunChecks(server);
            HousingSweep.run(server);
        } catch (Throwable t) {
            fails++;
            lines.add("FAIL suite " + t);
            t.printStackTrace();
        } finally {
            lines.add("SELFTEST DONE failed=" + fails);
            try {
                Files.write(Path.of("selftest-report.txt"), lines);
            } catch (Exception e) {
                e.printStackTrace();
            }
            lines.forEach(System.out::println);
            server.halt(false);
        }
    }
}

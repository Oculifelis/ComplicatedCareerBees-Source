package dev.oculifelis.careerbees;

import net.neoforged.neoforge.common.ModConfigSpec;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public final class Config {
    public static final ModConfigSpec COMMON_SPEC;
    public static final ModConfigSpec SERVER_SPEC;

    public static final Map<String, ModConfigSpec.BooleanValue> FRAME_ENABLED = new HashMap<>();
    public static final Map<String, ModConfigSpec.BooleanValue> EFFECT_ENABLED = new HashMap<>();
    public static ModConfigSpec.BooleanValue MOB_GRIEFING;
    public static ModConfigSpec.IntValue WORLD_ATTEMPTS;
    public static ModConfigSpec.IntValue MINING_ATTEMPTS;
    public static ModConfigSpec.IntValue CREEPER_CAP;
    public static ModConfigSpec.IntValue ENERGY_PER_TICK;
    public static ModConfigSpec.IntValue GUN_CAPACITY, GUN_PER_SHOT, GUN_PER_BOTTLE;
    public static ModConfigSpec.IntValue EXPLOSION_MAX_RADIUS;
    public static ModConfigSpec.DoubleValue PHEROMONE_CHANCE;
    public static ModConfigSpec.BooleanValue PEDESTAL_PARTICLES;

    public static final List<String> FRAMES = List.of("mutation_frame", "pheromone_frame", "poison_frame", "eternal_frame", "settings_frame");
    public static final List<String> EFFECTS = List.of(
            "healing", "curative", "burning", "electrify", "husbandry", "butcher", "police", "taxation", "creeper",
            "assassin", "thief", "crusher", "smelt", "honey_ingot", "repair", "sharpen", "armorer", "glazing",
            "retraining", "priest", "mason", "painting", "lumber", "teleposition", "explosion", "mining",
            "mining_fortune", "mining_silk", "pickup", "business", "junk_sale", "energy", "clockwinding", "temporal", "jazz");

    static {
        ModConfigSpec.Builder c = new ModConfigSpec.Builder();
        c.comment("Turn individual items on or off.").push("frames");
        for (String f : FRAMES) FRAME_ENABLED.put(f, c.define(f, true));
        c.pop();
        COMMON_SPEC = c.build();

        ModConfigSpec.Builder s = new ModConfigSpec.Builder();
        s.push("effects");
        s.comment("Turn individual bee effects on or off.").push("enabled");
        for (String e : EFFECTS) EFFECT_ENABLED.put(e, s.define(e, true));
        s.pop();
        MOB_GRIEFING = s.comment("Effects that change the world also follow the mobGriefing game rule.").define("mobGriefing", true);
        WORLD_ATTEMPTS = s.comment("Block attempts per fire for world effects.").defineInRange("worldAttempts", 40, 1, 1000);
        MINING_ATTEMPTS = s.comment("Block attempts per fire for the miner bees.").defineInRange("miningAttempts", 80, 1, 2000);
        CREEPER_CAP = s.comment("Creepers allowed in a territory before the N.C.A. bee stops spawning them.").defineInRange("creeperCap", 20, 0, 500);
        ENERGY_PER_TICK = s.comment("Base FE per tick for the Energy bee, multiplied by speed squared.").defineInRange("energyPerTick", 400, 0, 1_000_000);
        EXPLOSION_MAX_RADIUS = s.comment("Largest blast radius of the Mad Scientist bee (smallest is 2).").defineInRange("explosionMaxRadius", 4, 2, 8);
        s.pop();
        s.push("frames");
        PHEROMONE_CHANCE = s.comment("Chance per bee cycle that a Pheromone Frame makes a vial.").defineInRange("pheromoneChance", 0.25, 0, 1);
        s.pop();
        s.push("pedestal");
        PEDESTAL_PARTICLES = s.define("particles", true);
        s.pop();
        s.push("gun");
        GUN_CAPACITY = s.comment("Honey the Bee-Bee Gun holds.").defineInRange("honeyCapacity", 1000, 1, 1_000_000);
        GUN_PER_SHOT = s.comment("Honey one shot costs.").defineInRange("honeyPerShot", 2, 0, 1_000_000);
        GUN_PER_BOTTLE = s.comment("Honey one Honey Bottle adds.").defineInRange("unitsPerBottle", 250, 1, 1_000_000);
        s.pop();
        SERVER_SPEC = s.build();
    }

    /** A server value, or its default when the server config isn't loaded (the client in multiplayer). */
    public static int server(ModConfigSpec.IntValue v) {
        return SERVER_SPEC.isLoaded() ? v.get() : v.getDefault();
    }

    public static boolean effectEnabled(String id) {
        ModConfigSpec.BooleanValue v = EFFECT_ENABLED.get(id);
        return v == null || !SERVER_SPEC.isLoaded() || v.get();
    }

    private Config() {}
}

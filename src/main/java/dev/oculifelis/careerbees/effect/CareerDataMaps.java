package dev.oculifelis.careerbees.effect;

import com.accbdd.complicated_bees.bees.effect.IBeeEffect;
import com.accbdd.complicated_bees.registry.BeeEffectRegistration;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import dev.oculifelis.careerbees.CareerBees;
import java.util.Optional;
import javax.annotation.Nullable;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.registries.datamaps.DataMapsUpdatedEvent;
import net.neoforged.neoforge.registries.datamaps.DataMapType;
import net.neoforged.neoforge.registries.datamaps.RegisterDataMapTypesEvent;

/**
 * Career effect tables that datapacks can change: {@code data/careerbees/data_maps/block/masonry.json} (what the Mason
 * turns each block into), the item tag {@code careerbees:honey_ingot_input} (what the Honey Ingot pedestal accepts) and
 * the item tag {@code careerbees:trade_currency} (what counts as money in villager trades) and
 * {@code data/careerbees/data_maps/complicated_bees/bee_effect/effect_timing.json} (how often each career effect fires).
 * Target blocks are ids looked up when used, so an entry for a mod that is not installed does nothing.
 */
public final class CareerDataMaps {
    /** What the Mason turns a block into. */
    public record Masonry(String result) {
        public static final Codec<Masonry> CODEC = RecordCodecBuilder.create(i -> i.group(
                Codec.STRING.fieldOf("result").forGetter(Masonry::result)
        ).apply(i, Masonry::new));

        public Optional<Block> block() {
            Identifier id = Identifier.tryParse(result);
            return id == null ? Optional.empty() : BuiltInRegistries.BLOCK.getOptional(id);
        }
    }

    public static final DataMapType<Block, Masonry> MASONRY = DataMapType.builder(
            CareerBees.id("masonry"), Registries.BLOCK, Masonry.CODEC).synced(Masonry.CODEC, false).build();

    public static final TagKey<Item> HONEY_INGOT_INPUT = TagKey.create(Registries.ITEM, CareerBees.id("honey_ingot_input"));

    /** Items the Business and Junk Sale bees treat as money in villager trades. */
    public static final TagKey<Item> TRADE_CURRENCY = TagKey.create(Registries.ITEM, CareerBees.id("trade_currency"));

    /** How often a career effect fires: ticks between fires at speed 1, and the chance each fire goes off. Either may be left out. */
    public record Timing(Optional<Integer> interval, Optional<Double> chance) {
        public static final Codec<Timing> CODEC = RecordCodecBuilder.create(i -> i.group(
                Codec.INT.optionalFieldOf("interval").forGetter(Timing::interval),
                Codec.doubleRange(0, 1).optionalFieldOf("chance").forGetter(Timing::chance)
        ).apply(i, Timing::new));
    }

    public static final DataMapType<IBeeEffect, Timing> EFFECT_TIMING = DataMapType.builder(
            CareerBees.id("effect_timing"), BeeEffectRegistration.BEE_REGISTRY_KEY, Timing.CODEC).build();

    private static volatile int generation;

    /** Goes up each time data maps reload, so effects know to re-read their timing. */
    public static int generation() {
        return generation;
    }

    /** The timing entry for an effect, or null when the map has none (or the registry is not up yet). */
    @Nullable
    @SuppressWarnings("unchecked")
    public static Timing timing(IBeeEffect effect) {
        Registry<IBeeEffect> registry = (Registry<IBeeEffect>) BuiltInRegistries.REGISTRY.getValue(BeeEffectRegistration.BEE_REGISTRY_KEY.identifier());
        if (registry == null) return null;
        return registry.getResourceKey(effect).map(k -> registry.getData(EFFECT_TIMING, k)).orElse(null);
    }

    /** The block the Mason makes from this one, or null. */
    @Nullable
    public static Block masonry(Block block) {
        Masonry m = block.builtInRegistryHolder().getData(MASONRY);
        return m == null ? null : m.block().orElse(null);
    }

    public static void init(IEventBus bus) {
        bus.addListener((RegisterDataMapTypesEvent event) -> {
            event.register(MASONRY);
            event.register(EFFECT_TIMING);
        });
        NeoForge.EVENT_BUS.addListener((DataMapsUpdatedEvent event) -> generation++);
    }

    private CareerDataMaps() {
    }
}

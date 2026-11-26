package dev.oculifelis.careerbees.client;

import com.accbdd.complicated_bees.bees.Species;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import dev.oculifelis.careerbees.item.PheromonesItem;
import dev.oculifelis.careerbees.util.Bees;
import net.minecraft.client.color.item.ItemTintSource;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.resources.Identifier;
import net.minecraft.util.ARGB;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

/** Tints a pheromone vial with its species: index 0 is the bee colour, 1 the nest colour. */
public record SpeciesTint(int index) implements ItemTintSource {
    public static final MapCodec<SpeciesTint> CODEC = Codec.INT.optionalFieldOf("index", 0).xmap(SpeciesTint::new, SpeciesTint::index);

    @Override
    public MapCodec<SpeciesTint> type() {
        return CODEC;
    }

    @Override
    public int calculate(ItemStack stack, @Nullable ClientLevel level, @Nullable LivingEntity owner) {
        Identifier id = PheromonesItem.species(stack);
        Species s = id == null ? null : Bees.species(id);
        if (s == null) return -1;
        int c = index == 0 ? s.getColor() : s.getNestColor();
        if (c == -1) c = s.getColor();
        return ARGB.opaque(c);
    }
}

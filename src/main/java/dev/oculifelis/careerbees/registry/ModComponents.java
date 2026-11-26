package dev.oculifelis.careerbees.registry;

import com.mojang.serialization.Codec;
import dev.oculifelis.careerbees.CareerBees;
import dev.oculifelis.careerbees.effect.FilterSettings;
import dev.oculifelis.careerbees.item.Chambers;
import dev.oculifelis.careerbees.item.ReportItem;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.resources.Identifier;
import net.neoforged.neoforge.registries.DeferredRegister;

import java.util.function.Supplier;

public final class ModComponents {
    public static final DeferredRegister.DataComponents REGISTER = DeferredRegister.createDataComponents(Registries.DATA_COMPONENT_TYPE, CareerBees.MODID);

    /** Log block a piece of Tree Bark came from. */
    public static final Supplier<DataComponentType<Identifier>> LOG = REGISTER.registerComponentType("log",
            b -> b.persistent(Identifier.CODEC).networkSynchronized(Identifier.STREAM_CODEC));
    /** Species a Vial of Pheromones belongs to. */
    public static final Supplier<DataComponentType<Identifier>> SPECIES = REGISTER.registerComponentType("species",
            b -> b.persistent(Identifier.CODEC).networkSynchronized(Identifier.STREAM_CODEC));

    /** Extra attack damage from the sharpening bees, up to 5. */
    public static final Supplier<DataComponentType<Float>> SHARPEN = REGISTER.registerComponentType("sharpen",
            b -> b.persistent(Codec.FLOAT).networkSynchronized(ByteBufCodecs.FLOAT));
    /** Fraction of the piece's own armour added by the armorer bees, up to 1. */
    public static final Supplier<DataComponentType<Float>> ARMOR_BONUS = REGISTER.registerComponentType("armor_bonus",
            b -> b.persistent(Codec.FLOAT).networkSynchronized(ByteBufCodecs.FLOAT));
    /** Absorption points a glazed food gives when eaten. */
    public static final Supplier<DataComponentType<Integer>> GLAZE = REGISTER.registerComponentType("glaze",
            b -> b.persistent(Codec.INT).networkSynchronized(ByteBufCodecs.VAR_INT));
    public static final Supplier<DataComponentType<ReportItem.Report>> REPORT = REGISTER.registerComponentType("report",
            b -> b.persistent(ReportItem.Report.CODEC).networkSynchronized(ReportItem.Report.STREAM_CODEC));

    /** What a Settings Frame lets through. */
    public static final Supplier<DataComponentType<FilterSettings>> FILTER = REGISTER.registerComponentType("filter",
            b -> b.persistent(FilterSettings.CODEC).networkSynchronized(FilterSettings.STREAM_CODEC));

    /** Bees loaded in a Bee-Bee Gun. */
    public static final Supplier<DataComponentType<Chambers>> CHAMBERS = REGISTER.registerComponentType("chambers",
            b -> b.persistent(Chambers.CODEC).networkSynchronized(Chambers.STREAM_CODEC));
    /** Honey in a Bee-Bee Gun's tank. */
    public static final Supplier<DataComponentType<Integer>> HONEY = REGISTER.registerComponentType("honey",
            b -> b.persistent(Codec.INT).networkSynchronized(ByteBufCodecs.VAR_INT));
    /** The chamber a Bee-Bee Gun fires from next, 0 to 5. */
    public static final Supplier<DataComponentType<Integer>> CHAMBER = REGISTER.registerComponentType("chamber",
            b -> b.persistent(Codec.INT).networkSynchronized(ByteBufCodecs.VAR_INT));

    private ModComponents() {}
}

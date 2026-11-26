package dev.oculifelis.careerbees.item;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import dev.oculifelis.careerbees.registry.ModComponents;
import net.minecraft.ChatFormatting;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;

import java.util.List;
import java.util.function.Consumer;

/** Police Incident Report: what the police bees confiscated, from whom, and who signed it. */
public class ReportItem extends Item {
    private static final String[] OFFICERS = {"Bea", "Beetrice", "Beeckett", "Debeeorah", "Melisandbee", "Phoebee", "Beeanca",
            "Abbee", "Beetty", "Rubee", "Beeritany", "Debee", "Kolbee", "Sabee", "Tabee", "Beeja", "Beetrix", "Rebeeca",
            "Beetriss", "Beetrissa", "Beetrise", "Beetriz", "Beeauty", "Beeast", "Darbee"};

    public ReportItem(Properties props) {
        super(props);
    }

    public static String officerName(int number) {
        return OFFICERS[Math.floorMod(number, OFFICERS.length)];
    }

    @Override
    @SuppressWarnings("deprecation")
    public void appendHoverText(ItemStack stack, TooltipContext ctx, TooltipDisplay display, Consumer<Component> out, TooltipFlag flag) {
        Report r = stack.get(ModComponents.REPORT.get());
        if (r == null) return;
        out.accept(Component.translatable("tooltip.careerbees.report.when", r.day(), r.time(), r.dimension()).withStyle(ChatFormatting.GRAY));
        out.accept(Component.translatable("tooltip.careerbees.report.subject", r.subject()).withStyle(ChatFormatting.GRAY));
        if (!r.items().isEmpty()) {
            out.accept(Component.translatable("tooltip.careerbees.report.confiscated").withStyle(ChatFormatting.GRAY));
            for (ItemStack s : r.items()) out.accept(Component.literal("  " + s.getCount() + " x ").append(s.getHoverName()).withStyle(ChatFormatting.DARK_GRAY));
        }
        out.accept(Component.translatable("tooltip.careerbees.report.signed", officerName(r.officer()), r.officer()).withStyle(ChatFormatting.ITALIC, ChatFormatting.GRAY));
    }

    public record Report(String subject, long day, String time, String dimension, List<ItemStack> items, int officer) {
        public static final Codec<Report> CODEC = RecordCodecBuilder.create(i -> i.group(
                Codec.STRING.fieldOf("subject").forGetter(Report::subject),
                Codec.LONG.fieldOf("day").forGetter(Report::day),
                Codec.STRING.fieldOf("time").forGetter(Report::time),
                Codec.STRING.fieldOf("dimension").forGetter(Report::dimension),
                ItemStack.CODEC.listOf().optionalFieldOf("items", List.of()).forGetter(Report::items),
                Codec.INT.fieldOf("officer").forGetter(Report::officer)
        ).apply(i, Report::new));
        public static final StreamCodec<RegistryFriendlyByteBuf, Report> STREAM_CODEC = StreamCodec.composite(
                ByteBufCodecs.STRING_UTF8, Report::subject,
                ByteBufCodecs.VAR_LONG, Report::day,
                ByteBufCodecs.STRING_UTF8, Report::time,
                ByteBufCodecs.STRING_UTF8, Report::dimension,
                ItemStack.OPTIONAL_LIST_STREAM_CODEC, Report::items,
                ByteBufCodecs.VAR_INT, Report::officer,
                Report::new);
    }
}

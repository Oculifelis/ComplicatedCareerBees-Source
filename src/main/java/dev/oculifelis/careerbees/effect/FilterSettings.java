package dev.oculifelis.careerbees.effect;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.Identifier;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

import java.util.Objects;

/**
 * What a Settings Frame lets through: one item (optionally ignoring components or damage) or an item tag.
 * An empty item or blank tag lets everything through. Void excess only matters to the pickup effect.
 */
public record FilterSettings(boolean tagMode, ItemStack item, boolean ignoreComponents, boolean ignoreDamage, String tag, boolean voidExcess) {
    public static final int MAX_TAG = 128;
    public static final FilterSettings DEFAULT = new FilterSettings(false, ItemStack.EMPTY, true, false, "", false);

    public static final Codec<FilterSettings> CODEC = RecordCodecBuilder.create(i -> i.group(
            Codec.BOOL.optionalFieldOf("tag_mode", false).forGetter(FilterSettings::tagMode),
            ItemStack.OPTIONAL_CODEC.optionalFieldOf("item", ItemStack.EMPTY).forGetter(FilterSettings::item),
            Codec.BOOL.optionalFieldOf("ignore_components", true).forGetter(FilterSettings::ignoreComponents),
            Codec.BOOL.optionalFieldOf("ignore_damage", false).forGetter(FilterSettings::ignoreDamage),
            Codec.string(0, MAX_TAG).optionalFieldOf("tag", "").forGetter(FilterSettings::tag),
            Codec.BOOL.optionalFieldOf("void_excess", false).forGetter(FilterSettings::voidExcess)
    ).apply(i, FilterSettings::new));

    public static final StreamCodec<RegistryFriendlyByteBuf, FilterSettings> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.BOOL, FilterSettings::tagMode,
            ItemStack.OPTIONAL_STREAM_CODEC, FilterSettings::item,
            ByteBufCodecs.BOOL, FilterSettings::ignoreComponents,
            ByteBufCodecs.BOOL, FilterSettings::ignoreDamage,
            ByteBufCodecs.stringUtf8(MAX_TAG), FilterSettings::tag,
            ByteBufCodecs.BOOL, FilterSettings::voidExcess,
            FilterSettings::new);

    public boolean matches(ItemStack stack) {
        if (tagMode) {
            TagKey<Item> key = tagKey();
            if (tag.isBlank()) return true;
            return key != null && stack.is(key);
        }
        if (item.isEmpty()) return true;
        if (!stack.is(item.getItem())) return false;
        if (ignoreComponents) return true;
        if (!ignoreDamage) return Objects.equals(stack.getComponentsPatch(), item.getComponentsPatch());
        ItemStack a = stack.copy(), b = item.copy();
        a.remove(DataComponents.DAMAGE);
        b.remove(DataComponents.DAMAGE);
        return Objects.equals(a.getComponentsPatch(), b.getComponentsPatch());
    }

    /** The tag this filter names, or null if the text is not a valid id. A leading # is allowed. */
    public TagKey<Item> tagKey() {
        String t = tag.strip();
        if (t.startsWith("#")) t = t.substring(1);
        Identifier id = Identifier.tryParse(t);
        return id == null || t.isEmpty() ? null : TagKey.create(Registries.ITEM, id);
    }

    public FilterSettings withTagMode(boolean v) { return new FilterSettings(v, item, ignoreComponents, ignoreDamage, tag, voidExcess); }
    public FilterSettings withItem(ItemStack v) { return new FilterSettings(tagMode, v.copyWithCount(1), ignoreComponents, ignoreDamage, tag, voidExcess); }
    public FilterSettings withIgnoreComponents(boolean v) { return new FilterSettings(tagMode, item, v, ignoreDamage, tag, voidExcess); }
    public FilterSettings withIgnoreDamage(boolean v) { return new FilterSettings(tagMode, item, ignoreComponents, v, tag, voidExcess); }
    public FilterSettings withTag(String v) { return new FilterSettings(tagMode, item, ignoreComponents, ignoreDamage, v.length() > MAX_TAG ? v.substring(0, MAX_TAG) : v, voidExcess); }
    public FilterSettings withVoidExcess(boolean v) { return new FilterSettings(tagMode, item, ignoreComponents, ignoreDamage, tag, v); }

    @Override
    public boolean equals(Object o) {
        return o instanceof FilterSettings f && tagMode == f.tagMode && ignoreComponents == f.ignoreComponents
                && ignoreDamage == f.ignoreDamage && voidExcess == f.voidExcess && tag.equals(f.tag)
                && ItemStack.isSameItemSameComponents(item, f.item) && item.isEmpty() == f.item.isEmpty();
    }

    @Override
    public int hashCode() {
        return Objects.hash(tagMode, ignoreComponents, ignoreDamage, voidExcess, tag, item.isEmpty() ? null : item.getItem());
    }
}

package dev.oculifelis.careerbees.registry;

import dev.oculifelis.careerbees.item.BeeGunItem;
import com.accbdd.complicated_bees.bees.BeeHousingModifier;
import com.accbdd.complicated_bees.item.FrameItem;
import dev.oculifelis.careerbees.CareerBees;
import dev.oculifelis.careerbees.Config;
import dev.oculifelis.careerbees.item.BarkItem;
import dev.oculifelis.careerbees.item.NoteFrameItem;
import dev.oculifelis.careerbees.item.ActiveFrameItem;
import dev.oculifelis.careerbees.item.PheromonesItem;
import dev.oculifelis.careerbees.item.ReportItem;
import dev.oculifelis.careerbees.item.SettingsFrameItem;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.item.equipment.ArmorMaterial;
import net.minecraft.world.item.equipment.ArmorType;
import net.minecraft.world.item.equipment.EquipmentAssets;
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.Map;
import java.util.List;

public final class ModItems {
    public static final DeferredRegister.Items REGISTER = DeferredRegister.createItems(CareerBees.MODID);
    public static final List<DeferredItem<? extends Item>> TAB_ORDER = new ArrayList<>();

    /** With Metallurgy present its dusts take over and ours are not registered. */
    public static final boolean OWN_DUSTS = !ModList.get().isLoaded("metallurgy");

    public static final DeferredItem<FrameItem> MUTATION_FRAME = add(REGISTER.registerItem("mutation_frame",
            p -> new FrameItem(p.durability(20), new BeeHousingModifier.Builder().mutation(10f).lifespan(0.5f).build(), Config.FRAME_ENABLED.get("mutation_frame"))));
    public static final DeferredItem<FrameItem> PHEROMONE_FRAME = add(REGISTER.registerItem("pheromone_frame",
            p -> new ActiveFrameItem.Pheromone(p.durability(80), new BeeHousingModifier.Builder().build(), Config.FRAME_ENABLED.get("pheromone_frame"))));
    public static final DeferredItem<FrameItem> POISON_FRAME = add(REGISTER.registerItem("poison_frame",
            p -> new ActiveFrameItem.Poison(p.durability(20), new BeeHousingModifier.Builder().lifespan(0.1f).build(), Config.FRAME_ENABLED.get("poison_frame"))));
    public static final DeferredItem<FrameItem> ETERNAL_FRAME = add(REGISTER.registerItem("eternal_frame",
            p -> new NoteFrameItem(p.stacksTo(1).rarity(Rarity.EPIC), new BeeHousingModifier.Builder().lifespan(1_000_000f).productivity(0f).build(),
                    Config.FRAME_ENABLED.get("eternal_frame"), "tooltip.careerbees.eternal_frame.age", "tooltip.careerbees.eternal_frame.production")));
    public static final DeferredItem<FrameItem> SETTINGS_FRAME = add(REGISTER.registerItem("settings_frame",
            p -> new SettingsFrameItem(p.stacksTo(1), Config.FRAME_ENABLED.get("settings_frame"))));

    public static final DeferredItem<Item> HONEYCOMB_INGOT = add(REGISTER.registerSimpleItem("honeycomb_ingot"));
    public static final DeferredItem<Item> YANG = add(REGISTER.registerItem("yang", p -> new Item(p.rarity(Rarity.RARE))));
    public static final DeferredItem<Item> YING = add(REGISTER.registerItem("ying", p -> new Item(p.rarity(Rarity.RARE))));
    public static final DeferredItem<Item> TRIQUETRA = add(REGISTER.registerItem("triquetra", p -> new Item(p.stacksTo(1).rarity(Rarity.RARE))));
    public static final DeferredItem<BarkItem> BARK = add(REGISTER.registerItem("bark", BarkItem::new));
    public static final DeferredItem<PheromonesItem> PHEROMONES = add(REGISTER.registerItem("pheromones", PheromonesItem::new));
    public static final DeferredItem<ReportItem> REPORT = add(REGISTER.registerItem("report", p -> new ReportItem(p.stacksTo(1))));
    public static final DeferredItem<BeeGunItem> BEE_GUN = add(REGISTER.registerItem("bee_gun", BeeGunItem::new));

    /** Bee armour: iron-like durability, diamond-like protection, repaired with Honey-Comb Ingots. */
    public static final ArmorMaterial BEE_ARMOR = new ArmorMaterial(33, new EnumMap<>(Map.of(
            ArmorType.HELMET, 4, ArmorType.CHESTPLATE, 9, ArmorType.LEGGINGS, 7, ArmorType.BOOTS, 4, ArmorType.BODY, 9)),
            64, SoundEvents.ARMOR_EQUIP_IRON, 2f, 0f,
            TagKey.create(Registries.ITEM, Identifier.fromNamespaceAndPath("c", "ingots/honeycomb")),
            ResourceKey.create(EquipmentAssets.ROOT_ID, CareerBees.id("bee")));
    public static final DeferredItem<Item> BEE_HELMET = armor("bee_helmet", ArmorType.HELMET);
    public static final DeferredItem<Item> BEE_CHESTPLATE = armor("bee_chestplate", ArmorType.CHESTPLATE);
    public static final DeferredItem<Item> BEE_LEGGINGS = armor("bee_leggings", ArmorType.LEGGINGS);
    public static final DeferredItem<Item> BEE_BOOTS = armor("bee_boots", ArmorType.BOOTS);

    public static final DeferredItem<Item> IRON_DUST = dust("iron_dust");
    public static final DeferredItem<Item> GOLD_DUST = dust("gold_dust");
    public static final DeferredItem<Item> COPPER_DUST = dust("copper_dust");
    public static final DeferredItem<Item> TIN_DUST = dust("tin_dust");
    public static final DeferredItem<Item> BRONZE_DUST = dust("bronze_dust");

    private static DeferredItem<Item> armor(String id, ArmorType type) {
        return add(REGISTER.registerItem(id, Item::new, p -> p.humanoidArmor(BEE_ARMOR, type)));
    }

    private static DeferredItem<Item> dust(String id) {
        return OWN_DUSTS ? add(REGISTER.registerSimpleItem(id)) : null;
    }

    public static DeferredItem<BlockItem> addBlock(DeferredBlock<?> block) {
        return add(REGISTER.registerSimpleBlockItem(block));
    }

    private static <T extends Item> DeferredItem<T> add(DeferredItem<T> item) {
        TAB_ORDER.add(item);
        return item;
    }

    private ModItems() {}
}

package dev.oculifelis.careerbees.effect;

import com.accbdd.complicated_bees.bees.GeneticHelper;
import com.accbdd.complicated_bees.bees.Genome;
import com.accbdd.complicated_bees.bees.Species;
import com.accbdd.complicated_bees.bees.gene.GeneEffect;
import com.accbdd.complicated_bees.bees.gene.IGene;
import com.accbdd.complicated_bees.item.DroneItem;
import com.accbdd.complicated_bees.item.PrincessItem;
import dev.oculifelis.careerbees.CareerBees;
import dev.oculifelis.careerbees.registry.ModComponents;
import dev.oculifelis.careerbees.registry.ModItems;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderSet;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.EnchantmentTags;
import net.minecraft.tags.ItemTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.food.FoodProperties;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.ItemAttributeModifiers;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.item.crafting.SingleRecipeInput;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.equipment.Equippable;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.EnchantingTableBlock;
import org.jetbrains.annotations.Nullable;

import java.util.Optional;

/** The flower pedestal effects. */
public final class PedestalEffects {

    public static final class Crusher extends PedestalEffect {
        public Crusher() { super("crusher", 400, 1, Pulse.YELLOW); }

        @Override
        public boolean accepts(Level level, ItemStack stack) {
            return dust(stack) != null;
        }

        @Override
        public ItemStack change(Ctx ctx, ItemStack stack) {
            Item dust = dust(stack);
            return dust == null ? null : new ItemStack(dust, 2 + ctx.random().nextInt(3));
        }

        @Override
        public ItemStack preview(ItemStack stack) {
            Item dust = dust(stack);
            return dust == null ? null : new ItemStack(dust, 2);
        }

        /** A dust for this ore: from the ore's own mod, then ours, then any. */
        @Nullable
        static Item dust(ItemStack ore) {
            for (TagKey<Item> tag : ore.typeHolder().tags().toList()) {
                if (!tag.location().getNamespace().equals("c") || !tag.location().getPath().startsWith("ores/")) continue;
                String metal = tag.location().getPath().substring(5);
                if (metal.isEmpty() || metal.contains("/")) continue;
                TagKey<Item> dustTag = TagKey.create(Registries.ITEM, Identifier.fromNamespaceAndPath("c", "dusts/" + metal));
                Optional<HolderSet.Named<Item>> set = BuiltInRegistries.ITEM.get(dustTag);
                if (set.isEmpty() || set.get().size() == 0) continue;
                String own = BuiltInRegistries.ITEM.getKey(ore.getItem()).getNamespace();
                Item ours = null, any = null;
                for (Holder<Item> h : set.get()) {
                    String ns = BuiltInRegistries.ITEM.getKey(h.value()).getNamespace();
                    if (ns.equals(own)) return h.value();
                    if (ours == null && ns.equals(CareerBees.MODID)) ours = h.value();
                    if (any == null) any = h.value();
                }
                return ours != null ? ours : any;
            }
            return null;
        }
    }

    public static final class Smelt extends PedestalEffect {
        public Smelt() { super("smelt", 20, 1, Pulse.FIRE); }

        @Override
        public boolean accepts(Level level, ItemStack stack) {
            return !result(level, stack).isEmpty();
        }

        @Override
        public ItemStack change(Ctx ctx, ItemStack stack) {
            ItemStack out = result(ctx.level(), stack);
            return out.isEmpty() ? null : out.copyWithCount(out.getCount() * stack.getCount());
        }

        static ItemStack result(Level level, ItemStack stack) {
            if (!(level instanceof ServerLevel sl)) return ItemStack.EMPTY;
            SingleRecipeInput in = new SingleRecipeInput(stack);
            return sl.recipeAccess().getRecipeFor(RecipeType.SMELTING, in, sl)
                    .map(r -> r.value().assemble(in)).orElse(ItemStack.EMPTY);
        }
    }

    public static final class HoneyIngot extends PedestalEffect {
        public HoneyIngot() { super("honey_ingot", 200, 1, Pulse.YELLOW); }

        @Override
        public boolean accepts(Level level, ItemStack stack) {
            return stack.is(CareerDataMaps.HONEY_INGOT_INPUT);
        }

        @Override
        public ItemStack change(Ctx ctx, ItemStack stack) {
            return new ItemStack(ModItems.HONEYCOMB_INGOT.get(), stack.getCount());
        }

        @Override
        public ItemStack preview(ItemStack stack) {
            return new ItemStack(ModItems.HONEYCOMB_INGOT.get(), stack.getCount());
        }
    }

    public static final class Repair extends PedestalEffect {
        public Repair() { super("repair", 20, 0.2, Pulse.YELLOW); }

        @Override
        public boolean accepts(Level level, ItemStack stack) {
            return stack.isDamageableItem();
        }

        @Override
        public boolean finished(Level level, ItemStack stack) {
            return !stack.isDamaged();
        }

        @Override
        public ItemStack change(Ctx ctx, ItemStack stack) {
            ItemStack out = stack.copy();
            out.setDamageValue(stack.getDamageValue() - 1);
            return out;
        }

        @Override
        public ItemStack sample(ItemStack stack) {
            ItemStack out = stack.copy();
            out.setDamageValue(stack.getMaxDamage() / 2);
            return out;
        }

        @Override
        public ItemStack preview(ItemStack stack) {
            ItemStack out = stack.copy();
            out.setDamageValue(0);
            return out;
        }
    }

    public static final class Sharpen extends PedestalEffect {
        public static final float MAX = 5f;

        public Sharpen() { super("sharpen", 20, 0.2, Pulse.YELLOW); }

        @Override
        public boolean accepts(Level level, ItemStack stack) {
            if (stack.is(ItemTags.SWORDS)) return true;
            boolean[] found = {false};
            stack.getOrDefault(DataComponents.ATTRIBUTE_MODIFIERS, ItemAttributeModifiers.EMPTY)
                    .forEach(EquipmentSlot.MAINHAND, (attr, mod) -> { if (attr.is(Attributes.ATTACK_DAMAGE)) found[0] = true; });
            return found[0];
        }

        @Override
        public boolean finished(Level level, ItemStack stack) {
            return stack.getOrDefault(ModComponents.SHARPEN.get(), 0f) >= MAX;
        }

        @Override
        public ItemStack change(Ctx ctx, ItemStack stack) {
            float old = stack.getOrDefault(ModComponents.SHARPEN.get(), 0f);
            float next = Math.min(MAX, (float) Math.ceil(old * 100 + 8 * (MAX - old)) / 100f);
            ItemStack out = stack.copy();
            out.set(ModComponents.SHARPEN.get(), next);
            return out;
        }

        @Override
        public ItemStack preview(ItemStack stack) {
            ItemStack out = stack.copy();
            out.set(ModComponents.SHARPEN.get(), MAX);
            return out;
        }
    }

    public static final class Armorer extends PedestalEffect {
        public Armorer() { super("armorer", 16, 0.2, Pulse.YELLOW); }

        @Override
        public boolean accepts(Level level, ItemStack stack) {
            return baseArmor(stack) > 0;
        }

        @Override
        public boolean finished(Level level, ItemStack stack) {
            return stack.getOrDefault(ModComponents.ARMOR_BONUS.get(), 0f) >= 1f;
        }

        @Override
        public ItemStack change(Ctx ctx, ItemStack stack) {
            float old = stack.getOrDefault(ModComponents.ARMOR_BONUS.get(), 0f);
            float next = Math.min(1f, (float) Math.ceil(old * 1000 + 50 * (1 - old)) / 1000f);
            ItemStack out = stack.copy();
            out.set(ModComponents.ARMOR_BONUS.get(), next);
            return out;
        }

        @Override
        public ItemStack preview(ItemStack stack) {
            ItemStack out = stack.copy();
            out.set(ModComponents.ARMOR_BONUS.get(), 1f);
            return out;
        }

        /** Armour the piece gives in its own slot, before bee bonuses. */
        public static double baseArmor(ItemStack stack) {
            Equippable eq = stack.get(DataComponents.EQUIPPABLE);
            if (eq == null) return 0;
            double[] total = {0};
            stack.getOrDefault(DataComponents.ATTRIBUTE_MODIFIERS, ItemAttributeModifiers.EMPTY)
                    .forEach(eq.slot(), (attr, mod) -> { if (attr.is(Attributes.ARMOR)) total[0] += mod.amount(); });
            return total[0];
        }
    }

    public static final class Glazing extends PedestalEffect {
        public Glazing() { super("glazing", 30, 1, Pulse.YELLOW); }

        @Override
        public boolean accepts(Level level, ItemStack stack) {
            return stack.has(DataComponents.FOOD) && cap(stack) > 0;
        }

        @Override
        public boolean finished(Level level, ItemStack stack) {
            return stack.getOrDefault(ModComponents.GLAZE.get(), 0) + 1 > cap(stack);
        }

        @Override
        public ItemStack change(Ctx ctx, ItemStack stack) {
            ItemStack out = stack.copy();
            out.set(ModComponents.GLAZE.get(), stack.getOrDefault(ModComponents.GLAZE.get(), 0) + 1);
            return out;
        }

        @Override
        public ItemStack preview(ItemStack stack) {
            ItemStack out = stack.copy();
            out.set(ModComponents.GLAZE.get(), cap(stack));
            return out;
        }

        /** Highest glaze a food can take: min(20, round(25 x nutrition x saturation modifier / 2) / 4). */
        public static int cap(ItemStack stack) {
            FoodProperties food = stack.get(DataComponents.FOOD);
            if (food == null) return 0;
            // saturation is stored as nutrition x modifier x 2, so nutrition x modifier / 2 = saturation / 4
            return Math.min(20, Math.round(25f * food.saturation() / 4f) / 4);
        }
    }

    public static final class Retraining extends PedestalEffect {
        public Retraining() { super("retraining", 16, 1, Pulse.YELLOW); }

        @Override
        public boolean accepts(Level level, ItemStack stack) {
            return (stack.getItem() instanceof DroneItem || stack.getItem() instanceof PrincessItem) && target(stack) != null;
        }

        @Override
        public boolean finished(Level level, ItemStack stack) {
            IGene<?> want = target(stack);
            if (want == null) return true;
            Genome g = GeneticHelper.getGenome(stack);
            return g != null && same(g.primary().getGene(GeneEffect.ID), want) && same(g.secondary().getGene(GeneEffect.ID), want);
        }

        @Override
        public ItemStack change(Ctx ctx, ItemStack stack) {
            return preview(stack);
        }

        @Override
        public ItemStack preview(ItemStack stack) {
            IGene<?> want = target(stack);
            Genome g = GeneticHelper.getGenome(stack);
            if (want == null || g == null) return null;
            ItemStack out = stack.copy();
            GeneticHelper.setGenome(out, new Genome(g.primary().copy().setGene(GeneEffect.ID, want), g.secondary().copy().setGene(GeneEffect.ID, want)));
            return out;
        }

        @Nullable
        static IGene<?> target(ItemStack bee) {
            Species s = GeneticHelper.getSpecies(bee, true);
            if (s == null || s == Species.INVALID || s.getDefaultChromosome() == null) return null;
            return s.getDefaultChromosome().getGene(GeneEffect.ID);
        }

        static boolean same(@Nullable IGene<?> a, IGene<?> b) {
            return a != null && a.get() == b.get();
        }
    }

    public static final class Priest extends PedestalEffect {
        public Priest() { super("priest", 100, 1, Pulse.YELLOW); }

        @Override
        public boolean accepts(Level level, ItemStack stack) {
            return stack.isDamageableItem() && stack.has(DataComponents.ENCHANTABLE);
        }

        @Override
        public boolean finished(Level level, ItemStack stack) {
            return stack.isEnchanted();
        }

        @Override
        public ItemStack change(Ctx ctx, ItemStack stack) {
            ServerLevel level = ctx.level();
            int shelves = shelves(level, ctx.pos());
            int cost = EnchantmentHelper.getEnchantmentCost(ctx.random(), ctx.random().nextInt(3), shelves, stack);
            if (cost <= 0) return null;
            Optional<HolderSet<Enchantment>> pool = level.registryAccess().lookupOrThrow(Registries.ENCHANTMENT)
                    .get(EnchantmentTags.IN_ENCHANTING_TABLE).map(s -> (HolderSet<Enchantment>) s);
            ItemStack out = EnchantmentHelper.enchantItem(ctx.random(), stack.copy(), cost, level.registryAccess(), pool);
            return out.isEnchanted() ? out : null;
        }

        static int shelves(ServerLevel level, BlockPos center) {
            int n = 0;
            for (BlockPos off : EnchantingTableBlock.BOOKSHELF_OFFSETS) {
                if (EnchantingTableBlock.isValidBookShelf(level, center, off)) n++;
            }
            return Math.min(15, n);
        }
    }

    private PedestalEffects() {}
}

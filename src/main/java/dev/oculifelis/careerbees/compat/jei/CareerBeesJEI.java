package dev.oculifelis.careerbees.compat.jei;

import com.accbdd.complicated_bees.bees.Species;
import com.accbdd.complicated_bees.registry.ItemsRegistration;
import dev.oculifelis.careerbees.CareerBees;
import dev.oculifelis.careerbees.effect.PedestalEffect;
import dev.oculifelis.careerbees.effect.PedestalEffects;
import dev.oculifelis.careerbees.item.BarkItem;
import dev.oculifelis.careerbees.registry.ModBlocks;
import dev.oculifelis.careerbees.registry.ModItems;
import dev.oculifelis.careerbees.util.Bees;
import mezz.jei.api.IModPlugin;
import mezz.jei.api.JeiPlugin;
import mezz.jei.api.constants.RecipeTypes;
import mezz.jei.api.gui.builder.IRecipeSlotBuilder;
import mezz.jei.api.gui.builder.IRecipeLayoutBuilder;
import mezz.jei.api.gui.widgets.IRecipeExtrasBuilder;
import mezz.jei.api.helpers.IGuiHelper;
import mezz.jei.api.recipe.IFocusGroup;
import mezz.jei.api.recipe.RecipeIngredientRole;
import mezz.jei.api.recipe.category.AbstractRecipeCategory;
import mezz.jei.api.recipe.types.IRecipeType;
import mezz.jei.api.registration.IRecipeCatalystRegistration;
import mezz.jei.api.registration.IRecipeCategoryRegistration;
import mezz.jei.api.registration.IRecipeRegistration;
import net.minecraft.client.Minecraft;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.FormattedText;
import net.minecraft.resources.Identifier;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;

import java.util.ArrayList;
import java.util.List;

/** JEI pages for the flower pedestal effects and the bark to log recipe. */
@JeiPlugin
public class CareerBeesJEI implements IModPlugin {
    /**
     * One pedestal effect: the bees that carry it, the items it works on and, at the same index,
     * each item once the effect is done with it (empty when the result can't be shown ahead of time).
     */
    public record PedestalPage(PedestalEffect effect, List<ItemStack> bees, List<ItemStack> inputs, List<ItemStack> outputs) {}

    static final IRecipeType<PedestalPage> PEDESTAL = IRecipeType.create(CareerBees.MODID, "pedestal", PedestalPage.class);
    static final IRecipeType<Block> BARK = IRecipeType.create(CareerBees.MODID, "bark_to_log", Block.class);

    @Override
    public Identifier getPluginUid() {
        return CareerBees.id("jei");
    }

    @Override
    public void registerCategories(IRecipeCategoryRegistration registration) {
        IGuiHelper gui = registration.getJeiHelpers().getGuiHelper();
        registration.addRecipeCategories(new PedestalCategory(gui), new BarkCategory(gui));
    }

    @Override
    public void registerRecipes(IRecipeRegistration registration) {
        List<PedestalPage> pages = new ArrayList<>();
        Registry<Species> species = Bees.registry();
        Level level = Minecraft.getInstance().level;
        List<ItemStack> candidates = candidates(species);
        for (PedestalEffect effect : PedestalEffect.ALL) {
            List<ItemStack> bees = new ArrayList<>();
            if (species != null) {
                for (Species s : species) {
                    if (Bees.defaultEffect(species.getKey(s)) == effect) bees.add(s.toStack(ItemsRegistration.QUEEN.get()));
                }
            }
            if (bees.isEmpty()) continue;
            pages.add(page(effect, bees, candidates, level));
        }
        registration.addRecipes(PEDESTAL, pages);
        List<Block> logs = new ArrayList<>();
        BuiltInRegistries.BLOCK.getTagOrEmpty(BlockTags.LOGS).forEach(h -> {
            if (h.value().asItem() != Items.AIR) logs.add(h.value());
        });
        registration.addRecipes(BARK, logs);

        registration.addIngredientInfo(ModItems.PHEROMONES.get(), Component.translatable("jei.careerbees.info.pheromones"));
        registration.addIngredientInfo(ModBlocks.PEDESTAL_ADVANCED_ITEM.get(), Component.translatable("jei.careerbees.info.pedestal_advanced"));
        registration.addIngredientInfo(ModItems.REPORT.get(), Component.translatable("jei.careerbees.info.report"));
        registration.addIngredientInfo(ModItems.BEE_GUN.get(), Component.translatable("jei.careerbees.info.bee_gun"));
    }

    @Override
    public void registerRecipeCatalysts(IRecipeCatalystRegistration registration) {
        registration.addCraftingStation(PEDESTAL, ModBlocks.PEDESTAL_ITEM.get(), ModBlocks.PEDESTAL_ADVANCED_ITEM.get());
        registration.addCraftingStation(BARK, Items.CRAFTING_TABLE);
        // smelting recipes live on the server only, so the Smelter's page has no item list; the pedestal joins the furnace pages instead
        registration.addCraftingStation(RecipeTypes.SMELTING, ModBlocks.PEDESTAL_ITEM.get(), ModBlocks.PEDESTAL_ADVANCED_ITEM.get());
    }

    /** Every item's default stack, plus a drone and a princess of each species for retraining. */
    static List<ItemStack> candidates(Registry<Species> species) {
        List<ItemStack> out = new ArrayList<>();
        for (Item item : BuiltInRegistries.ITEM) {
            ItemStack stack = new ItemStack(item);
            if (!stack.isEmpty()) out.add(stack);
        }
        if (species != null) {
            for (Species s : species) {
                out.add(s.toStack(ItemsRegistration.DRONE.get()));
                out.add(s.toStack(ItemsRegistration.PRINCESS.get()));
            }
        }
        return out;
    }

    /** The items an effect works on, each paired with its finished form when the effect can show one. */
    static PedestalPage page(PedestalEffect effect, List<ItemStack> bees, List<ItemStack> candidates, Level level) {
        List<ItemStack> inputs = new ArrayList<>(), outputs = new ArrayList<>();
        if (level != null) {
            for (ItemStack stack : candidates) {
                try {
                    if (!effect.accepts(level, stack)) continue;
                    ItemStack in = effect.sample(stack);
                    ItemStack out = effect.preview(in);
                    inputs.add(in);
                    outputs.add(out == null ? ItemStack.EMPTY : out);
                } catch (RuntimeException ignored) {
                    // an item the effect can't judge on the client is left out
                }
            }
        }
        if (outputs.stream().allMatch(ItemStack::isEmpty)) return new PedestalPage(effect, bees, inputs, List.of());
        List<ItemStack> ins = new ArrayList<>(), outs = new ArrayList<>();
        for (int i = 0; i < inputs.size(); i++) {
            if (outputs.get(i).isEmpty()) continue;
            ins.add(inputs.get(i));
            outs.add(outputs.get(i));
        }
        return new PedestalPage(effect, bees, ins, outs);
    }

    static final class PedestalCategory extends AbstractRecipeCategory<PedestalPage> {
        PedestalCategory(IGuiHelper gui) {
            super(PEDESTAL, Component.translatable("gui.careerbees.jei.pedestal"), gui.createDrawableItemLike(ModBlocks.PEDESTAL_ITEM.get()), 150, 78);
        }

        @Override
        public void setRecipe(IRecipeLayoutBuilder builder, PedestalPage page, IFocusGroup focuses) {
            builder.addSlot(RecipeIngredientRole.CRAFTING_STATION, 1, 1).setStandardSlotBackground().addItemStacks(page.bees());
            builder.addSlot(RecipeIngredientRole.RENDER_ONLY, 1, 23).addItemStack(new ItemStack(ModBlocks.PEDESTAL_ITEM.get()));
            if (page.inputs().isEmpty()) return;
            IRecipeSlotBuilder in = builder.addSlot(RecipeIngredientRole.INPUT, 25, 59).setStandardSlotBackground().addItemStacks(page.inputs());
            if (page.outputs().isEmpty()) return;
            IRecipeSlotBuilder out = builder.addSlot(RecipeIngredientRole.OUTPUT, 79, 59).setOutputSlotBackground().addItemStacks(page.outputs());
            builder.createFocusLink(in, out);
        }

        @Override
        public void createRecipeExtras(IRecipeExtrasBuilder builder, PedestalPage page, IFocusGroup focuses) {
            String key = "effect.complicated_bees." + CareerBees.MODID + ":" + page.effect().id();
            List<FormattedText> text = new ArrayList<>(List.of(Component.translatable(key), Component.translatable(key + ".desc")));
            if (page.inputs().isEmpty() && page.effect() instanceof PedestalEffects.Smelt) text.add(Component.translatable("gui.careerbees.jei.pedestal.no_inputs"));
            builder.addText(text, 124, page.inputs().isEmpty() ? 76 : 54).setPosition(24, 2).setColor(0xFF404040);
            if (!page.outputs().isEmpty()) builder.addRecipeArrow().setPosition(49, 60);
        }
    }

    static final class BarkCategory extends AbstractRecipeCategory<Block> {
        private final IGuiHelper gui;

        BarkCategory(IGuiHelper gui) {
            super(BARK, Component.translatable("gui.careerbees.jei.bark"), gui.createDrawableItemLike(ModItems.BARK.get()), 82, 38);
            this.gui = gui;
        }

        @Override
        public void setRecipe(IRecipeLayoutBuilder builder, Block log, IFocusGroup focuses) {
            ItemStack bark = BarkItem.of(ModItems.BARK.get(), log, 1);
            for (int i = 0; i < 4; i++) {
                builder.addSlot(RecipeIngredientRole.INPUT, 1 + (i % 2) * 18, 1 + (i / 2) * 18).setStandardSlotBackground().addItemStack(bark);
            }
            builder.addSlot(RecipeIngredientRole.OUTPUT, 61, 10).setOutputSlotBackground().addItemStack(new ItemStack(log.asItem()));
        }

        @Override
        public void createRecipeExtras(IRecipeExtrasBuilder builder, Block log, IFocusGroup focuses) {
            builder.addRecipeArrow().setPosition(39, 10);
        }
    }
}

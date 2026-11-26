package dev.oculifelis.careerbees.registry;

import dev.oculifelis.careerbees.CareerBees;
import dev.oculifelis.careerbees.recipe.BarkLogRecipe;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.neoforged.neoforge.registries.DeferredRegister;

import java.util.function.Supplier;

public final class ModRecipes {
    public static final DeferredRegister<RecipeSerializer<?>> SERIALIZERS = DeferredRegister.create(Registries.RECIPE_SERIALIZER, CareerBees.MODID);

    public static final Supplier<RecipeSerializer<BarkLogRecipe>> BARK_TO_LOG = SERIALIZERS.register("bark_to_log", () -> BarkLogRecipe.SERIALIZER);

    private ModRecipes() {}
}

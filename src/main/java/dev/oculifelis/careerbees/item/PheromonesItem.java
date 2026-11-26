package dev.oculifelis.careerbees.item;

import com.accbdd.complicated_bees.bees.GeneticHelper;
import com.accbdd.complicated_bees.bees.Species;
import dev.oculifelis.careerbees.registry.ModComponents;
import dev.oculifelis.careerbees.util.Bees;
import net.minecraft.core.Registry;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

/** Vial of Pheromones: remembers one species, binds a Flower Pedestal to it. */
public class PheromonesItem extends Item {
    public PheromonesItem(Properties props) {
        super(props);
    }

    public static ItemStack of(Item vial, Identifier species) {
        ItemStack stack = new ItemStack(vial);
        stack.set(ModComponents.SPECIES.get(), species);
        return stack;
    }

    @Nullable
    public static Identifier species(ItemStack stack) {
        return stack.get(ModComponents.SPECIES.get());
    }

    @Override
    public Component getName(ItemStack stack) {
        Species s = Bees.species(species(stack));
        if (s == null) return super.getName(stack);
        return Component.translatable("item.careerbees.pheromones.of", GeneticHelper.getTranslationKey(s));
    }

    /** One vial per species. */
    public void fillTab(CreativeModeTab.Output out) {
        Registry<Species> r = Bees.registry();
        if (r == null) return;
        r.keySet().stream().sorted().forEach(id -> out.accept(of(this, id)));
    }
}

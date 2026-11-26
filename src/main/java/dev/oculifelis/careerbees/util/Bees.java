package dev.oculifelis.careerbees.util;

import com.accbdd.complicated_bees.bees.GeneticHelper;
import com.accbdd.complicated_bees.bees.Species;
import com.accbdd.complicated_bees.bees.effect.IBeeEffect;
import com.accbdd.complicated_bees.bees.gene.GeneEffect;
import com.accbdd.complicated_bees.bees.gene.IGene;
import com.accbdd.complicated_bees.registry.SpeciesRegistration;
import net.minecraft.core.Registry;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

/** Small lookups into Complicated Bees' species registry. */
public final class Bees {
    @Nullable
    public static Registry<Species> registry() {
        try {
            return GeneticHelper.getRegistryAccess().lookupOrThrow(SpeciesRegistration.SPECIES_REGISTRY_KEY);
        } catch (RuntimeException e) {
            return null;
        }
    }

    @Nullable
    public static Species species(@Nullable Identifier id) {
        Registry<Species> r = registry();
        return r == null || id == null ? null : r.getValue(id);
    }

    @Nullable
    public static Identifier speciesId(ItemStack bee, boolean primary) {
        Registry<Species> r = registry();
        Species s = GeneticHelper.getSpecies(bee, primary);
        return r == null || s == null || s == Species.INVALID ? null : r.getKey(s);
    }

    /** The effect a species' bees carry by default, or null. */
    @Nullable
    public static IBeeEffect defaultEffect(@Nullable Identifier speciesId) {
        Species s = species(speciesId);
        if (s == null || s.getDefaultChromosome() == null) return null;
        IGene<?> gene = s.getDefaultChromosome().getGene(GeneEffect.ID);
        return gene != null && gene.get() instanceof IBeeEffect e ? e : null;
    }

    private Bees() {}
}

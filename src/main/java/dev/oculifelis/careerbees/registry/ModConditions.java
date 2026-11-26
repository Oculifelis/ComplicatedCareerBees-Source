package dev.oculifelis.careerbees.registry;

import com.accbdd.complicated_bees.recipe.mutation.condition.IMutationCondition;
import com.accbdd.complicated_bees.registry.MutationRegistration;
import dev.oculifelis.careerbees.CareerBees;
import dev.oculifelis.careerbees.condition.RecentExplosionCondition;
import net.neoforged.neoforge.registries.DeferredRegister;

import java.util.function.Supplier;

public final class ModConditions {
    public static final DeferredRegister<IMutationCondition> REGISTER = DeferredRegister.create(MutationRegistration.MUTATION_CONDITION_KEY, CareerBees.MODID);

    public static final Supplier<IMutationCondition> RECENT_EXPLOSION = REGISTER.register(RecentExplosionCondition.ID, RecentExplosionCondition::new);

    private ModConditions() {}
}
